#!/usr/bin/env bash
# Deliberate fail-closed human/audit gate. Passing offline tests is not a release.
set -euo pipefail
cd "$(dirname "$0")/.."
bash run_offline_checks.sh
bash scripts/check_adapter_syntax.sh
python3 - <<'PY'
from pathlib import Path
p=Path('src/main/java/dev/kondalabs/gaslight/Phase3Engine.java').read_text()
a=Path('src/main/java/dev/kondalabs/gaslight/GaslightNativeMod.java').read_text()
assert 'default Optional<Phase3Publication.Transaction> stagePublication(List<Passage> facets){return Optional.empty();}' in p
assert 'Phase3Publication.publish(passages,host::stagePublication)' in p
assert 'Phase3SafetyGate.check(facets,1,4000,host::inspectChunk)' in p
assert 'default boolean authoritativeCamera(NativeWorldEngine.Actor player){return false;}' in p
assert 'authoritativeCamera(NativeWorldEngine.Actor actor){return false;}' in a
assert 'phase3AllowPhysical=false' in a and 'phase3DynamicMode=false' in a
assert 'journal.begin(passages)' in p and 'journal.settle(actor.id(),epochs)' in p
assert 'Phase3GenerationGeometry.separated(passages,old.facets,8)' in p
assert 'Phase3Visibility.hidden(camera,q.exit(),cfg.safetyMargin())' in p
assert 'private boolean safeToRetire(List<Passage> facets)' in p
assert 'Phase3RecoveryJournal.FileStore' in a
assert 'Phase3PreparedCatalog.load(' in a
assert 'requireDetailedTerrainProfiles(){return true;}' in a
assert 'return Phase3DestinationProfile.capture(index->' in a
assert 'Phase3DestinationSelector.candidates(' in p
assert 'new HashMap<>()' in p and 'terrainAudit(best,bestDx/16,bestDz/16' in p
assert 'class FileStore implements Store' in Path('src/main/java/dev/kondalabs/gaslight/Phase3RecoveryJournal.java').read_text()
assert 'Geometry.chunksTouched(segment,0,budget)' in Path('src/main/java/dev/kondalabs/gaslight/Phase3TerrainMatcher.java').read_text()
print('PASS: source-level fail-closed safety invariants')
PY
echo 'NO-GO: real Java25 Fabric build, client 3D renderer, authoritative camera, atomic compositor and in-game crossing unverified.' >&2
exit 42
