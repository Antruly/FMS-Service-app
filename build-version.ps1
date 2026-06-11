# Auto-increment patch version before building
# Version format: major.minor.patch (e.g. 2.3.1)
# versionCode = major * 10000 + minor * 100 + patch

$gradleFile = "app\build.gradle"
$content = Get-Content $gradleFile -Raw

# Extract current versionName
$match = [regex]::Match($content, 'versionName\s+"(\d+)\.(\d+)\.(\d+)"')
if (-not $match.Success) {
    Write-Host "ERROR: Cannot parse versionName"
    exit 1
}
$major = [int]$match.Groups[1].Value
$minor = [int]$match.Groups[2].Value
$patch = [int]$match.Groups[3].Value

$oldVersion = "$major.$minor.$patch"

# Increment patch
$newPatch = $patch + 1
$newVersion = "$major.$minor.$newPatch"
$newVersionCode = $major * 10000 + $minor * 100 + $newPatch

Write-Host "Version: $oldVersion -> $newVersion (code: $newVersionCode)"

# Update versionName
$content = $content -replace 'versionName\s+"[^"]*"', "versionName `"$newVersion`""
# Update versionCode
$content = $content -replace 'versionCode\s+\d+', "versionCode $newVersionCode"

[System.IO.File]::WriteAllText((Resolve-Path $gradleFile), $content)
Write-Host "Updated $gradleFile"
Write-Host "New version: $newVersion (code: $newVersionCode)"
