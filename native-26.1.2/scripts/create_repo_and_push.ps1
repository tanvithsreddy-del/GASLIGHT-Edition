# Run inside extracted GASLIGHT project. Requires Git and GitHub CLI (`gh auth login`).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$repo = 'tanvithsreddy-del/GASLIGHT'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Install https://cli.github.com/' }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Install https://git-scm.com/' }
gh auth status
if ($LASTEXITCODE -ne 0) { throw 'Please authenticate first using gh auth login' }
gh repo view $repo 2>$null
if ($LASTEXITCODE -eq 0) { throw "Repository already exists: $repo. Refusing to overwrite." }
if (-not (Test-Path .git)) { git init -b main; if ($LASTEXITCODE -ne 0) { throw 'git init failed' } }
git add .gitignore .github src scripts build.gradle settings.gradle gradle.properties LICENSE README.md 106_CHECK_GATE.json RELEASE_SCORE.json TEST_EVIDENCE.md PHASE1_STATUS.md GITHUB_REPOSITORY_STATUS.md OFFLINE_CORE_TESTS.log PHASE1_BUILD_ATTEMPT.log build_offline.sh run_offline_checks.sh phase1_verify.sh
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git -c user.name='GASLIGHT build setup' -c user.email='actions@users.noreply.github.com' commit -m 'Initialize GASLIGHT 26.1.2 Phase 1 build gate'
if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
gh repo create $repo --private --source=. --remote=origin --push
if ($LASTEXITCODE -ne 0) { throw 'Repo create/push failed' }
Write-Output "Published: https://github.com/$repo"
