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
