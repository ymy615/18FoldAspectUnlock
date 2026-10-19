[CmdletBinding()]
param(
    [string]$SdkRoot = '',
    [string]$JavaRoot = '',
    [string]$Platform = 'android-37.0',
    [string]$BuildToolsVersion = '36.0.0'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Build tool failed ($LASTEXITCODE): $([System.IO.Path]::GetFileName($Executable))"
    }
}

function Assert-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required file not found: $Path"
    }
}

if (-not $SdkRoot) {
    if ($env:ANDROID_SDK_ROOT) { $SdkRoot = $env:ANDROID_SDK_ROOT }
    elseif ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME }
    else { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}
if (-not $JavaRoot) {
    if ($env:JAVA_HOME) { $JavaRoot = $env:JAVA_HOME }
    else { $JavaRoot = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }
}
$sdk = [System.IO.Path]::GetFullPath($SdkRoot)
$jdk = [System.IO.Path]::GetFullPath($JavaRoot)
$buildTools = Join-Path $sdk "build-tools\$BuildToolsVersion"
$androidJar = Join-Path $sdk "platforms\$Platform\android.jar"
$apiJar = Join-Path $projectRoot 'libs\api-82.jar'
$manifest = Join-Path $projectRoot 'app\src\main\AndroidManifest.xml'
$entrySource = Join-Path $projectRoot 'app\src\main\java\io\github\foldaspect\unlock\AspectUnlock.java'
$javac = Join-Path $jdk 'bin\javac.exe'
$jar = Join-Path $jdk 'bin\jar.exe'
$java = Join-Path $jdk 'bin\java.exe'
$keytool = Join-Path $jdk 'bin\keytool.exe'
$aapt2 = Join-Path $buildTools 'aapt2.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$d8Jar = Join-Path $buildTools 'lib\d8.jar'
$apksignerJar = Join-Path $buildTools 'lib\apksigner.jar'
if (-not (Test-Path -LiteralPath $apiJar -PathType Leaf)) {
    & (Join-Path $projectRoot 'setup-dependencies.ps1')
}
foreach ($required in @($androidJar, $apiJar, $manifest, $entrySource, $javac, $jar, $java, $keytool, $aapt2, $zipalign, $d8Jar, $apksignerJar)) {
    Assert-File $required
}
$expectedApiHash = 'F48C635F1C7469FDEC0E00AD2EA0B7A6B2F5B55065784A35B7CA3A84615E8E25'
if ((Get-FileHash -LiteralPath $apiJar -Algorithm SHA256).Hash -ne $expectedApiHash) {
    throw 'The Xposed API 82 dependency differs from the recorded publisher download.'
}

[xml]$manifestXml = Get-Content -LiteralPath $manifest -Raw
$androidNamespace = 'http://schemas.android.com/apk/res/android'
$versionName = $manifestXml.manifest.GetAttribute('versionName', $androidNamespace)
$minSdk = $manifestXml.manifest.'uses-sdk'.GetAttribute('minSdkVersion', $androidNamespace)
if ($versionName -notmatch '^[0-9A-Za-z._-]+$') { throw 'Invalid APK version name.' }
if ($minSdk -notmatch '^\d+$') { throw 'Invalid minimum SDK version.' }

# Each build gets a separate directory, so no recursive deletion is necessary.
$runName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$runRoot = Join-Path $projectRoot "build\$runName"
$classes = Join-Path $runRoot 'classes'
$generated = Join-Path $runRoot 'generated'
$dexRoot = Join-Path $runRoot 'dex'
$dist = Join-Path $projectRoot 'dist'
$local = Join-Path $projectRoot '.local'
foreach ($directory in @($classes, $generated, $dexRoot, $dist, $local)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}
$resourcesZip = Join-Path $runRoot 'resources.zip'
$resourcesApk = Join-Path $runRoot 'resources.apk'
$classesJar = Join-Path $runRoot 'classes.jar'
$unsignedApk = Join-Path $runRoot 'unsigned.apk'
$alignedApk = Join-Path $runRoot 'aligned.apk'
$signedApk = Join-Path $runRoot 'signed.apk'
$outputApk = Join-Path $dist "FoldAspectUnlock-$versionName.apk"

Write-Host 'Compiling Android resources...'
Invoke-Checked $aapt2 @('compile', '--dir', (Join-Path $projectRoot 'app\src\main\res'), '-o', $resourcesZip)
Invoke-Checked $aapt2 @('link', '-o', $resourcesApk, '--manifest', $manifest, '-I', $androidJar, '-A', (Join-Path $projectRoot 'app\src\main\assets'), '--java', $generated, $resourcesZip)

Write-Host 'Compiling module Java sources...'
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\main\java'), $generated -Filter '*.java' -File -Recurse | Sort-Object FullName)
$sourceList = Join-Path $runRoot 'sources.txt'
$sourceLines = @($sourceFiles | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' })
[System.IO.File]::WriteAllLines($sourceList, [string[]]$sourceLines, $utf8)
Invoke-Checked $javac @('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options', '-classpath', "$androidJar;$apiJar", '-d', $classes, "@$sourceList")
Invoke-Checked $jar @('--create', '--file', $classesJar, '-C', $classes, '.')
Invoke-Checked $java @('-cp', $d8Jar, 'com.android.tools.r8.D8', '--release', '--min-api', $minSdk, '--lib', $androidJar, '--classpath', $apiJar, '--output', $dexRoot, $classesJar)

Copy-Item -LiteralPath $resourcesApk -Destination $unsignedApk
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open($unsignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $dexFiles = @(Get-ChildItem -LiteralPath $dexRoot -Filter '*.dex' -File)
    if ($dexFiles.Count -eq 0) { throw 'D8 did not produce any DEX files.' }
    foreach ($dexFile in $dexFiles) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $dexFile.FullName, $dexFile.Name, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $archive.Dispose()
}

Write-Host 'Signing APK with the local project key...'
$keyStore = Join-Path $local 'release.p12'
$passwordFile = Join-Path $local 'keystore.password'
if (-not (Test-Path -LiteralPath $keyStore)) {
    if (-not (Test-Path -LiteralPath $passwordFile)) {
        $randomBytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        [System.IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($randomBytes), $utf8)
    }
    Invoke-Checked $keytool @('-genkeypair', '-keystore', $keyStore, '-storetype', 'PKCS12', '-storepass:file', $passwordFile, '-keypass:file', $passwordFile, '-alias', 'fold-aspect-unlock', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000', '-dname', 'CN=Fold Aspect Unlock, OU=Local Android Modules, O=Local Development', '-noprompt')
}
Assert-File $passwordFile
Invoke-Checked $zipalign @('-f', '-p', '4', $unsignedApk, $alignedApk)
Invoke-Checked $java @('-jar', $apksignerJar, 'sign', '--ks', $keyStore, '--ks-key-alias', 'fold-aspect-unlock', '--ks-pass', "file:$passwordFile", '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', $signedApk, $alignedApk)
Invoke-Checked $java @('-jar', $apksignerJar, 'verify', '--verbose', '--print-certs', $signedApk)
Invoke-Checked $zipalign @('-c', '-p', '4', $signedApk)
Copy-Item -LiteralPath $signedApk -Destination $outputApk -Force
$apkHash = (Get-FileHash -LiteralPath $outputApk -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText("$outputApk.sha256", "$apkHash  $([System.IO.Path]::GetFileName($outputApk))`n", $utf8)
Write-Host "APK: $outputApk"
Write-Host "SHA-256: $apkHash"
