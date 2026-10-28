[CmdletBinding()]
param([string]$FirmwarePolicy = '', [string]$JavaRoot = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $JavaRoot) {
    if ($env:JAVA_HOME) { $JavaRoot = $env:JAVA_HOME }
    else { $JavaRoot = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
}
$classes = Join-Path $PSScriptRoot ('build\policy-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes -Force | Out-Null
& (Join-Path $JavaRoot 'bin\javac.exe') -encoding UTF-8 -d $classes `
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\UnlockPolicy.java') `
    (Join-Path $PSScriptRoot 'tests\PolicyRegression.java')
if ($LASTEXITCODE -ne 0) { throw 'Policy test compilation failed.' }
$testArguments = @('-cp', $classes, 'PolicyRegression')
if ($FirmwarePolicy) { $testArguments += (Resolve-Path -LiteralPath $FirmwarePolicy).Path }
& (Join-Path $JavaRoot 'bin\java.exe') @testArguments
if ($LASTEXITCODE -ne 0) { throw 'Policy regression failed.' }

# Compile the real hook and policy against the minimal host-only API fixtures.
# These fixtures are outside app/src and are never inputs to build.ps1.
$hostClasses = Join-Path $classes 'host'
$fixtureClasses = Join-Path $classes 'fixtures'
New-Item -ItemType Directory -Path $hostClasses, $fixtureClasses -Force | Out-Null
$hostSources = @(
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\AspectUnlock.java'),
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\UnlockPolicy.java'),
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\StatusBarInsetFix.java'),
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\StatusBarAppearance.java'),
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\StatusBarStylePolicy.java'),
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\WidthModePolicy.java'),
    (Join-Path $PSScriptRoot 'app\src\main\java\io\github\foldaspect\unlock\WidthModeController.java')
)
$hostSources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests\host-src\main') -Recurse -Filter '*.java' -File |
    Sort-Object FullName | ForEach-Object { $_.FullName })
$sourceList = Join-Path $classes 'host-sources.txt'
$sourceLines = @($hostSources | ForEach-Object { '"' + $_.Replace('\', '/') + '"' })
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($sourceList, [string[]]$sourceLines, $utf8)
& (Join-Path $JavaRoot 'bin\javac.exe') -encoding UTF-8 -d $hostClasses "@$sourceList"
if ($LASTEXITCODE -ne 0) { throw 'Host hook test compilation failed.' }

foreach ($fixture in @('missing-method', 'wrong-return', 'insets-wrong-field')) {
    $target = Join-Path $fixtureClasses $fixture
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    $fixtureSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "tests\host-src\fixtures\$fixture") -Recurse -Filter '*.java' -File |
        Sort-Object FullName | ForEach-Object { $_.FullName })
    & (Join-Path $JavaRoot 'bin\javac.exe') -encoding UTF-8 -cp $hostClasses -d $target @fixtureSources
    if ($LASTEXITCODE -ne 0) { throw "Host fixture compilation failed: $fixture" }
}

$scenarios = @(
    'exact-install',
    'environment-guards',
    'missing-class',
    'missing-method',
    'wrong-return',
    'rollback-retry',
    'fold-unfold-policy',
    'package-uid-protection',
    'status-bar-policy'
)
foreach ($scenario in $scenarios) {
    # Separate JVMs prevent AspectUnlock.installed leaking between scenarios.
    & (Join-Path $JavaRoot 'bin\java.exe') -cp $hostClasses host.HookRegression $scenario $fixtureClasses
    if ($LASTEXITCODE -ne 0) { throw "Host hook regression failed: $scenario" }
}
$insetScenarios = @(
    'inset-dimensions',
    'inset-context-guards',
    'inset-complex-metadata',
    'inset-copy-repeat',
    'inset-exception-preservation',
    'inset-preflight'
)
foreach ($scenario in $insetScenarios) {
    & (Join-Path $JavaRoot 'bin\java.exe') -cp $hostClasses io.github.foldaspect.unlock.StatusBarInsetRegression $scenario $fixtureClasses
    if ($LASTEXITCODE -ne 0) { throw "Host inset regression failed: $scenario" }
}
$appearanceScenarios = @(
    'appearance-native-flags',
    'appearance-context-exit',
    'appearance-exception-preservation',
    'letterbox-context-restore',
    'letterbox-exception-preservation'
)
foreach ($scenario in $appearanceScenarios) {
    & (Join-Path $JavaRoot 'bin\java.exe') -cp $hostClasses io.github.foldaspect.unlock.StatusBarAppearanceRegression $scenario $fixtureClasses
    if ($LASTEXITCODE -ne 0) { throw "Host appearance regression failed: $scenario" }
}
$styleScenarios = @('style-height-policy', 'style-request-gates')
foreach ($scenario in $styleScenarios) {
    & (Join-Path $JavaRoot 'bin\java.exe') -cp $hostClasses io.github.foldaspect.unlock.StatusBarStyleRegression $scenario
    if ($LASTEXITCODE -ne 0) { throw "Host style policy regression failed: $scenario" }
}
$widthScenarios = @(
    'width-policy-geometry',
    'width-native-wide',
    'width-context-optouts',
    'width-endpoint-persistence',
    'width-registration-failures',
    'width-refresh-targets',
    'width-refresh-failures'
)
foreach ($scenario in $widthScenarios) {
    & (Join-Path $JavaRoot 'bin\java.exe') -cp $hostClasses io.github.foldaspect.unlock.WidthModeRegression $scenario (Join-Path $PSScriptRoot 'app\src\main\AndroidManifest.xml')
    if ($LASTEXITCODE -ne 0) { throw "Host width regression failed: $scenario" }
}
Write-Host "PASS $($scenarios.Count + $insetScenarios.Count + $appearanceScenarios.Count + $styleScenarios.Count + $widthScenarios.Count) isolated host scenarios."
Write-Host 'Host fold/status-bar models do not establish ART hook activation, native fold dispatch, app rendering or physical-device behavior.'
