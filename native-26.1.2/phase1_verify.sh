#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
echo '[GASLIGHT] Phase 1: Java/Fabric binary build gate'
if ! command -v javac >/dev/null; then echo 'BLOCKED: JDK javac not found' >&2; exit 20; fi
javac -version
major="$(javac -version 2>&1 | sed -E 's/.* ([0-9]+)(\..*)?$/\1/')"
if [[ "$major" != '25' ]]; then echo 'BLOCKED: JDK 25 required for Minecraft 26.1.2' >&2; exit 21; fi
[[ -f ./gradlew && -f gradle/wrapper/gradle-wrapper.jar ]] || { echo 'BLOCKED: pinned Gradle wrapper missing' >&2; exit 22; }
bash ./gradlew --version
# Network/dependency failures abort. Offline or test stub JARs are never accepted.
bash ./gradlew --no-daemon clean build --stacktrace
bash scripts/check_jar.sh
