#!/usr/bin/env bash
# Publish GASLIGHT Phase 1 to a dedicated private repository using GitHub CLI.
# Run from anywhere; this script only modifies the unzipped project folder and the new GASLIGHT repo.
set -euo pipefail
cd "$(dirname "$0")/.."
owner=tanvithsreddy-del
repo="$owner/GASLIGHT"
command -v gh >/dev/null || { echo 'Install GitHub CLI: https://cli.github.com/'; exit 2; }
command -v git >/dev/null || { echo 'Install Git: https://git-scm.com/'; exit 2; }
gh auth status >/dev/null
if gh repo view "$repo" >/dev/null 2>&1; then
  echo "Repository already exists. Refusing to overwrite or push automatically: $repo" >&2
  exit 3
fi
if [[ ! -d .git ]]; then git init -b main; fi
# Ensure only expected source, scripts and reports are committed.
git add .gitignore .github src scripts build.gradle settings.gradle gradle.properties LICENSE README.md     106_CHECK_GATE.json RELEASE_SCORE.json TEST_EVIDENCE.md PHASE1_STATUS.md GITHUB_REPOSITORY_STATUS.md     OFFLINE_CORE_TESTS.log PHASE1_BUILD_ATTEMPT.log build_offline.sh run_offline_checks.sh phase1_verify.sh
git diff --cached --quiet && { echo 'No files to commit'; exit 4; }
git -c user.name='GASLIGHT build setup' -c user.email='actions@users.noreply.github.com' commit -m 'Initialize GASLIGHT 26.1.2 Phase 1 build gate'
gh repo create "$repo" --private --source=. --remote=origin --push
echo "Repository created and pushed: https://github.com/$repo"
echo 'A push to main triggers the build workflow.'
