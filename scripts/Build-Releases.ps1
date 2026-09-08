param(
    [Parameter(Mandatory = $true)][string]$Gradle,
    [Parameter(Mandatory = $true)][string]$BuildToolsPath,
    [string]$InitScript
)

$ErrorActionPreference = 'Stop'
$releaseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingRoot = Join-Path $releaseRoot '.signing'
$keyStore = Join-Path $signingRoot 'fan-ai-studio-release.p12'
$encryptedPassword = Join-Path $signingRoot 'password.dpapi'
$apkSigner = Join-Path $BuildToolsPath 'apksigner.bat'
$zipAlign = Join-Path $BuildToolsPath 'zipalign.exe'
$aapt = Join-Path $BuildToolsPath 'aapt.exe'
foreach ($required in @($keyStore, $encryptedPassword, $apkSigner, $zipAlign, $aapt)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required file missing: $required" }
}

$outputDir = Join-Path $releaseRoot 'app/build/outputs/fan-ai-studio'
$null = New-Item -ItemType Directory -Path $outputDir -Force
$password = (Get-Content -LiteralPath $encryptedPassword -Raw).Trim() | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
$oldPasswordEnvironment = [Environment]::GetEnvironmentVariable('YINYAN_RELEASE_STORE_PASSWORD', 'Process')
Push-Location $releaseRoot
try {
    # Keep the password out of process arguments, Gradle daemons and build logs.
    $builds = @(
        @{ Name = 'standard-android7'; MinSdk = 24 },
        @{ Name = 'compat-android5.1'; MinSdk = 22 }
    )
    foreach ($build in $builds) {
        $gradleArgs = @()
        if ($InitScript) { $gradleArgs += @('-I', $InitScript) }
        $gradleArgs += @("-PyinyanMinSdk=$($build.MinSdk)", ':app:testReleaseUnitTest', ':app:lintRelease', ':app:assembleRelease')
        & $Gradle @gradleArgs
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed for $($build.Name)" }

        $unsignedApk = Join-Path $releaseRoot 'app/build/outputs/apk/release/app-release-unsigned.apk'
        $metadata = Get-Content -LiteralPath (Join-Path $releaseRoot 'app/build/outputs/apk/release/output-metadata.json') -Raw | ConvertFrom-Json
        $versionName = $metadata.elements[0].versionName
        if ($versionName -notmatch '^[0-9A-Za-z._-]+$') { throw 'Unsafe version name for APK filename' }
        $signedApk = Join-Path $outputDir "yinyan-$versionName-$($build.Name)-release.apk"
        & $zipAlign -c -p 4 $unsignedApk
        if ($LASTEXITCODE -ne 0) { throw 'Unsigned APK alignment check failed' }

        try {
            $env:YINYAN_RELEASE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
            & $apkSigner sign --ks $keyStore --ks-key-alias yinyan-release `
                --ks-pass env:YINYAN_RELEASE_STORE_PASSWORD --key-pass env:YINYAN_RELEASE_STORE_PASSWORD `
                --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
                --v4-signing-enabled false --out $signedApk $unsignedApk
            if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
        } finally {
            [Environment]::SetEnvironmentVariable('YINYAN_RELEASE_STORE_PASSWORD', $oldPasswordEnvironment, 'Process')
        }

        # Verify from API 22 even for the API 24 APK: otherwise apksigner skips
        # v1 verification and reports false because API 24+ can use v2 instead.
        $verification = @(& $apkSigner verify --verbose --print-certs --min-sdk-version 22 $signedApk 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
        $verification | Where-Object { $_ -match '^Verifies|^Verified using|^Signer #1 certificate (DN|SHA-256)' }
        if (-not ($verification -match 'certificate DN:.*Fan AI Studio')) { throw 'Unexpected signing certificate' }
        if (-not ($verification -match 'Verified using v1 scheme .*true')) { throw 'Missing Android 5.1-compatible v1 signature' }
        $badging = @(& $aapt dump badging $signedApk)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read APK manifest' }
        if ($badging -notcontains "sdkVersion:'$($build.MinSdk)'") { throw 'Wrong minimum SDK in signed APK' }
        if ($badging -match '^application-debuggable') { throw 'Release APK unexpectedly debuggable' }
        $badging | Where-Object { $_ -match '^package:|^sdkVersion:|^targetSdkVersion:|^application-label:' }
        Get-FileHash -LiteralPath $signedApk -Algorithm SHA256
    }
} finally {
    Pop-Location
    [Environment]::SetEnvironmentVariable('YINYAN_RELEASE_STORE_PASSWORD', $oldPasswordEnvironment, 'Process')
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $password.Dispose()
}
