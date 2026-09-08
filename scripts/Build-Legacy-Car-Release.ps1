param(
    [Parameter(Mandatory = $true)][string]$Gradle,
    [Parameter(Mandatory = $true)][string]$BuildToolsPath,
    [string]$InitScript
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingRoot = Join-Path $projectRoot '.signing'
$keyStore = Join-Path $signingRoot 'fan-ai-studio-legacy-car.p12'
$encryptedPassword = Join-Path $signingRoot 'password.dpapi'
$apkSigner = Join-Path $BuildToolsPath 'apksigner.bat'
$zipAlign = Join-Path $BuildToolsPath 'zipalign.exe'
$aapt = Join-Path $BuildToolsPath 'aapt.exe'

foreach ($required in @($keyStore, $encryptedPassword, $apkSigner, $zipAlign, $aapt)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required file missing: $required" }
}

$outputDir = Join-Path $projectRoot 'app/build/outputs/fan-ai-studio'
$null = New-Item -ItemType Directory -Path $outputDir -Force
$password = (Get-Content -LiteralPath $encryptedPassword -Raw).Trim() | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
$oldPasswordEnvironment = [Environment]::GetEnvironmentVariable('YINYAN_LEGACY_STORE_PASSWORD', 'Process')

Push-Location $projectRoot
try {
    $gradleArgs = @()
    if ($InitScript) { $gradleArgs += @('-I', $InitScript) }
    $gradleArgs += @(
        '-PyinyanMinSdk=19',
        '-PyinyanTargetSdk=23',
        '-PyinyanReleaseMinify=true',
        ':app:testReleaseUnitTest',
        ':app:lintRelease',
        ':app:assembleRelease'
    )
    & $Gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Gradle failed for the legacy-car release' }

    $unsignedApk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release-unsigned.apk'
    $metadata = Get-Content -LiteralPath (Join-Path $projectRoot 'app/build/outputs/apk/release/output-metadata.json') -Raw | ConvertFrom-Json
    $versionName = $metadata.elements[0].versionName
    if ($versionName -notmatch '^[0-9A-Za-z._-]+$') { throw 'Unsafe version name for APK filename' }
    $signedApk = Join-Path $outputDir "yinyan-$versionName-legacy-car-api19-release.apk"

    & $zipAlign -c -p 4 $unsignedApk
    if ($LASTEXITCODE -ne 0) { throw 'Unsigned APK alignment check failed' }

    try {
        $env:YINYAN_LEGACY_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
        # The known-good car APK uses SHA-1 JAR digests and v1/v2 signing. Passing
        # min API 14 selects that old JAR algorithm while the manifest remains API 21.
        & $apkSigner sign --ks $keyStore --ks-key-alias yinyan-legacy-car `
            --ks-pass env:YINYAN_LEGACY_STORE_PASSWORD --key-pass env:YINYAN_LEGACY_STORE_PASSWORD `
            --min-sdk-version 14 --v1-signing-enabled true --v2-signing-enabled true `
            --v3-signing-enabled false --v4-signing-enabled false --out $signedApk $unsignedApk
        if ($LASTEXITCODE -ne 0) { throw 'Legacy APK signing failed' }
    } finally {
        [Environment]::SetEnvironmentVariable('YINYAN_LEGACY_STORE_PASSWORD', $oldPasswordEnvironment, 'Process')
    }

    $verification = @(& $apkSigner verify --verbose --print-certs --min-sdk-version 14 $signedApk 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Legacy APK signature verification failed' }
    if (-not ($verification -match 'certificate DN:.*Fan AI Studio')) { throw 'Unexpected legacy signing certificate' }
    if (-not ($verification -match 'Verified using v1 scheme .*true')) { throw 'Missing legacy v1 signature' }
    if (-not ($verification -match 'Verified using v2 scheme .*true')) { throw 'Missing v2 signature' }
    if ($verification -match 'Verified using v3 scheme .*true') { throw 'Unexpected v3 signature in legacy APK' }

    $badging = @(& $aapt dump badging $signedApk)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read legacy APK manifest' }
    if ($badging -notcontains "sdkVersion:'19'") { throw 'Wrong legacy minimum SDK' }
    if ($badging -notcontains "targetSdkVersion:'23'") { throw 'Wrong legacy target SDK' }
    if ($badging -match '^application-debuggable') { throw 'Legacy Release APK unexpectedly debuggable' }

    $verification | Where-Object { $_ -match '^Verifies|^Verified using|^Signer #1 certificate (DN|SHA-256)' }
    $badging | Where-Object { $_ -match '^package:|^sdkVersion:|^targetSdkVersion:|^application-label:' }
    Get-FileHash -LiteralPath $signedApk -Algorithm SHA256
} finally {
    Pop-Location
    [Environment]::SetEnvironmentVariable('YINYAN_LEGACY_STORE_PASSWORD', $oldPasswordEnvironment, 'Process')
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $password.Dispose()
}
