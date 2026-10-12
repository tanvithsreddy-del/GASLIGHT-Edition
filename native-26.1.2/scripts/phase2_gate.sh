#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./run_offline_checks.sh
# Under no circumstances certify this Phase 2 build without a genuine 26.1.2
# Minecraft client runtime and real remote-world renderer/physics evidence.
echo 'NO-GO: real Fabric build, remote 3D compositor and Minecraft crossing remain unverified.' >&2
exit 42
