$ErrorActionPreference = 'Stop'

git checkout forge-1.20.1
./gradlew clean publishAll

git checkout neoforge-1.21.1
./gradlew clean publishAll