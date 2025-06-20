$ErrorActionPreference = 'Stop'

# Parse command line arguments for changelog file or default to CHANGELOG.md
param (
    [string]$ChangelogFile = "CHANGELOG.md"
)

# Verify the changelog file exists
if (-not (Test-Path $ChangelogFile)) {
    Write-Error "Changelog file '$ChangelogFile' not found!"
    exit 1
}

# Read the mod version from gradle.properties
$gradleProperties = Get-Content -Path "gradle.properties" | Where-Object { $_ -match "^mod_version=" }
$modVersion = ($gradleProperties -split "=")[1].Trim()
Write-Host "Mod version from gradle.properties: $modVersion"

# Extract the latest version changelog content from the file
$changelogContent = Get-Content $ChangelogFile -Raw
$latestChangelog = ""
$latestChangelogVersion = ""

if ($changelogContent -match '## \[(\d+\.\d+\.\d+)\].*?\r?\n(.*?)(?=\r?\n## \[|$)') {
    $latestChangelogVersion = $matches[1]
    $latestChangelog = $matches[2].Trim()
    Write-Host "Latest changelog version: $latestChangelogVersion"
    
    # Check if the versions match
    if ($latestChangelogVersion -ne $modVersion) {
        Write-Error "Version mismatch! Changelog version ($latestChangelogVersion) does not match mod_version in gradle.properties ($modVersion)"
        exit 1
    }
    
    Write-Host "Version check passed: Changelog version matches mod_version"
} else {
    Write-Warning "Could not extract latest changelog version and content from '$ChangelogFile'. Using full file contents."
    $latestChangelog = $changelogContent
}

# Create a temporary changelog file for Gradle
$tempChangelogFile = [System.IO.Path]::GetTempFileName()
$latestChangelog | Out-File -FilePath $tempChangelogFile -Encoding utf8

Write-Host "Publishing with changelog from '$ChangelogFile'"
Write-Host "Temporary changelog file created at: $tempChangelogFile"

git checkout forge-1.20.1
./gradlew clean publishAll -PchangelogFile="$tempChangelogFile"

git checkout neoforge-1.21.1
./gradlew clean publishAll -PchangelogFile="$tempChangelogFile"

# Clean up the temporary file
Remove-Item $tempChangelogFile
Write-Host "Temporary changelog file removed"