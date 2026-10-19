[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$dependencyDirectory = Join-Path $PSScriptRoot 'libs'
$dependencyPath = Join-Path $dependencyDirectory 'api-82.jar'
$expectedHash = 'F48C635F1C7469FDEC0E00AD2EA0B7A6B2F5B55065784A35B7CA3A84615E8E25'
$sourceUri = 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar'
if (Test-Path -LiteralPath $dependencyPath -PathType Leaf) {
    if ((Get-FileHash -LiteralPath $dependencyPath -Algorithm SHA256).Hash -ne $expectedHash) {
        throw 'Existing Xposed API dependency failed SHA-256 verification.'
    }
    Write-Host 'Xposed API 82 already verified.'
    return
}
New-Item -ItemType Directory -Path $dependencyDirectory -Force | Out-Null
$temporaryPath = Join-Path $dependencyDirectory ('api-82-' + [Guid]::NewGuid().ToString('N') + '.tmp')
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $sourceUri -OutFile $temporaryPath -UseBasicParsing
    if ((Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash -ne $expectedHash) {
        throw 'Downloaded Xposed API dependency failed SHA-256 verification.'
    }
    Move-Item -LiteralPath $temporaryPath -Destination $dependencyPath
    Write-Host 'Xposed API 82 downloaded and verified.'
} finally {
    if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath }
}
