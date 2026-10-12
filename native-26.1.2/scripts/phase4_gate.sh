#!/usr/bin/env bash
# Offline verification and an intentionally strict release gate.
# A test-passing source tree still IS NOT a Minecraft-tested JAR.
set -euo pipefail
cd "$(dirname "$0")/.."
bash run_offline_checks.sh
bash scripts/check_adapter_syntax.sh
python3 scripts/phase4_mutation_probe.py
python3 - <<'PY'
from pathlib import Path
p=Path('src/main/java/dev/kondalabs/gaslight/GaslightNativeMod.java').read_text()
r=Path('src/main/java/dev/kondalabs/gaslight/Phase3RecoveryJournal.java').read_text()
e=Path('src/main/java/dev/kondalabs/gaslight/Phase3Engine.java').read_text()
assert 'phase3AllowPhysical=false' in p and 'phase3DynamicMode=false' in p
assert 'authoritativeCamera(NativeWorldEngine.Actor actor){return false;}' in p
assert 'if(!Arrays.equals(actual,expectedDiskBytes))' in r
assert 'FileLock lock=guardChannel.tryLock()' in r
assert 'Phase3Publication.publish(passages,host::stagePublication)' in e
assert 'if(!host.authoritativeCamera(actor))' in e
print('PASS: static fail-closed host contract')
PY
echo 'OFFLINE VALIDATION PASSED; RELEASE NO-GO: Java25 Minecraft/Fabric launch, GPU renderer, in-game crossing and save tests unverified.' >&2
exit 42
