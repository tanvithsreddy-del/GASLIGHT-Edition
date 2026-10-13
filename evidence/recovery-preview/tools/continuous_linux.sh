#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME=/var/tmp/gaslight-phase1-01a12efa/toolchains/jdk-25+36
export GRADLE_USER_HOME=/var/tmp/gaslight-phase1-01a12efa/gradle-home
export PATH="$JAVA_HOME/bin:$PATH"
root=$(mktemp -d /var/tmp/gaslight-clean-continuous-01a12efa-XXXXXX)
echo "FRESH_CHECKOUT=$root"
mkdir -p "$root"
tar -xf /mnt/c/Users/Tanvith/Documents/Codex/2026-10-12/you-are-taking-over-gaslight-minecraft/outputs/continuous-build/current-source.tar -C "$root"
cd "$root/ACTIVE_PROJECT/provider-adapters/fabric-1.21.1"
bash gradlew --no-daemon --max-workers=2 clean build --console=plain
