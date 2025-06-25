$activeBranch = git rev-parse --abbrev-ref HEAD

$gradleProperties = Get-Content -Path "gradle.properties" | Where-Object { $_ -match "^mod_version=" }
$modVersion = ($gradleProperties -split "=")[1].Trim()
Write-Host "Mod version from gradle.properties: $modVersion"


$switchToBranch = 'forge-1.20.1'

if($activeBranch -eq "forge-1.20.1") {
    $switchToBranch = 'neoforge-1.21.1'
}

$ErrorActionPreference = 'Stop'

if (-not (Test-Path "both_loaders")) {
    mkdir  "both_loaders"
}

.\gradlew clean build
Move-Item "./build/libs/*.jar" "both_loaders/mc_talking_$modVersion-$activeBranch.jar" -Force

git checkout $switchToBranch
.\gradlew clean build
Move-Item "./build/libs/*.jar" "both_loaders/mc_talking_$modVersion-$switchToBranch.jar" -Force

git checkout $activeBranch
Move-Item "both_loaders" "build/both_loaders" -Force