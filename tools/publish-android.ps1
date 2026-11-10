# Builds the release APK and puts it on the download page (downloads/android/).
# Usage:  powershell -File tools\publish-android.ps1 [-Bump]
#   -Bump  increments versionCode and the minor part of versionName first.
# Then commit + push; the Pages workflow publishes it at <site>/android/.
param([switch]$Bump)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$gradleFile = Join-Path $root 'app\build.gradle.kts'

if ($Bump) {
    $g = Get-Content $gradleFile -Raw
    $code = [int]([regex]::Match($g, 'versionCode = (\d+)').Groups[1].Value) + 1
    $name = [regex]::Match($g, 'versionName = "(\d+)\.(\d+)"')
    $newName = "$($name.Groups[1].Value).$([int]$name.Groups[2].Value + 1)"
    $g = $g -replace 'versionCode = \d+', "versionCode = $code" -replace 'versionName = "[^"]+"', "versionName = `"$newName`""
    Set-Content -Path $gradleFile -Value $g -NoNewline -Encoding utf8
}

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
& (Join-Path $root 'gradlew.bat') -p $root assembleRelease --console=plain -q
if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }

$g = Get-Content $gradleFile -Raw
$versionName = [regex]::Match($g, 'versionName = "([^"]+)"').Groups[1].Value
$versionCode = [int]([regex]::Match($g, 'versionCode = (\d+)').Groups[1].Value)

$out = Join-Path $root 'downloads\android'
$apk = Join-Path $out 'workout.apk'
Copy-Item (Join-Path $root 'app\build\outputs\apk\release\app-release.apk') $apk -Force
$info = [ordered]@{
    versionName = $versionName
    versionCode = $versionCode
    date        = (Get-Date -Format 'yyyy-MM-dd')
    bytes       = (Get-Item $apk).Length
}
$info | ConvertTo-Json | Set-Content -Path (Join-Path $out 'version.json') -Encoding ascii
"Published Workout $versionName ($versionCode), $([math]::Round($info.bytes / 1MB, 1)) MB -> downloads/android/"
