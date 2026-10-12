#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
shopt -s nullglob
jars=(build/libs/*.jar)
valid=()
for j in "${jars[@]}"; do
  [[ "$j" == *-sources.jar ]] && continue
  [[ "$j" == *-dev.jar ]] && continue
  valid+=("$j")
done
[[ "${#valid[@]}" -eq 1 ]] || { echo "FAIL: expected 1 unobfuscated Fabric build jar, got ${#valid[@]}: ${valid[*]}" >&2; exit 1; }
jar="${valid[0]}"
jar tf "$jar" | grep -qx 'fabric.mod.json'
jar tf "$jar" | grep -qx 'dev/kondalabs/gaslight/GaslightNativeMod.class'
! jar tf "$jar" | grep -Eq '^net/(minecraft|fabricmc)/'
python3 - "$jar" <<'PY'
import sys,json,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    m=json.loads(z.read('fabric.mod.json'))
assert m['id']=='gaslight_native',m
assert m['depends']['minecraft']=='=26.1.2',m
assert m['depends']['java']=='>=25',m
assert m['version']!='${version}',m
print('Validated genuine metadata:',m['id'],m['version'],m['depends']['minecraft'])
PY
sha256sum "$jar"
python3 scripts/verify_build.py "$jar"
python3 scripts/test_verify_build.py "$jar"
echo "PASS: Fabric 26.1+ unobfuscated production jar inspection: $jar"
