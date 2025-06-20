$msg=$args[0]
$activeBranch = git rev-parse --abbrev-ref HEAD

$switchToBranch = 'forge-1.20.1'

if($activeBranch -eq "forge-1.20.1") {
    $switchToBranch = 'neoforge-1.21.1'
}

$ErrorActionPreference = 'Stop'


git stash --keep-index
git commit -m $msg
$hash = git rev-parse HEAD

git push
git checkout $switchToBranch
git cherry-pick $hash
git push

git checkout $activeBranch
git stash pop