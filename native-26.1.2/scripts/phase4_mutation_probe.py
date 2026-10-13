#!/usr/bin/env python3
"""Kill critical safety mutants in temporary source copies. Never edit original."""
import shutil, subprocess, tempfile, pathlib, sys, os
root=pathlib.Path(__file__).resolve().parents[1]
bash_executable=os.environ.get('GASLIGHT_BASH') or shutil.which('bash')
if not bash_executable:
    raise SystemExit('Bash executable unavailable')
variants=[
    ('chunk_boundary_omission','src/main/java/dev/kondalabs/gaslight/Geometry.java',
     'Math.nextDown(minX/16.0)','minX/16.0','touching an exact chunk face','dev.kondalabs.gaslight.Phase4ValidationTests'),
    ('camera_pitch_basis_regression','src/main/java/dev/kondalabs/gaslight/Geometry.java',
     '-Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),Math.cos(yaw)*Math.sin(pitch)',
     'Math.sin(yaw)*Math.sin(pitch),Math.cos(pitch),-Math.cos(yaw)*Math.sin(pitch)',
     'valid yaw/pitch witness','dev.kondalabs.gaslight.Phase4ValidationTests'),
    ('lost_journal_cas','src/main/java/dev/kondalabs/gaslight/Phase3RecoveryJournal.java',
     'if(!Arrays.equals(actual,expectedDiskBytes))','if(false)',
     'stale concurrent journal writer','dev.kondalabs.gaslight.Phase4ValidationTests'),
    ('nonfinite_chunk_geometry','src/main/java/dev/kondalabs/gaslight/Geometry.java',
     'requireFinite(seg.a());requireFinite(seg.b());',
     '// deliberately omitted finite-coordinate validation',
     'nonfinite segment X veto','dev.kondalabs.gaslight.Phase4FinalSafetyTests'),
    ('blocked_headroom_compatibility','src/main/java/dev/kondalabs/gaslight/Phase3DestinationProfile.java',
     'if(!a.twoBlockClear()||!b.twoBlockClear())',
     'if(false)',
     'repeated headroom mismatch veto','dev.kondalabs.gaslight.Phase3DestinationTests'),
    ('swallowed_camera_host_failure','src/main/java/dev/kondalabs/gaslight/Phase3Engine.java',
     'disabled=true;\n            if(owner!=null)walker.forget(owner);\n            refuse("QUARANTINED: planner/host failure:',
     'disabled=false;\n            if(owner!=null)walker.forget(owner);\n            refuse("planner refused:',
     'unexpected callback must quarantine','dev.kondalabs.gaslight.Phase4HostIsolationTests'),
]
failed=[]
for name,fn,original,replacement,expected,test_class in variants:
    with tempfile.TemporaryDirectory(prefix='gaslight-phase4-mutant-') as tmp:
        dest=pathlib.Path(tmp)/'project'
        shutil.copytree(root,dest,ignore=shutil.ignore_patterns('build','*.log','SHA256SUMS.txt','.gradle','provider-adapters','run-*','deps','.git'))
        path=dest/fn
        content=path.read_text()
        if original not in content:
            failed.append((name,'mutation anchor unavailable'));continue
        path.write_text(content.replace(original,replacement,1))
        result=subprocess.run([bash_executable,'run_offline_checks.sh'],cwd=dest,capture_output=True,text=True,timeout=90, env={**os.environ,'GASLIGHT_RUN_ONLY':test_class})
        output=result.stdout+result.stderr
        if os.environ.get('GASLIGHT_MUTATION_LOG_DIR'):
            logs=pathlib.Path(os.environ['GASLIGHT_MUTATION_LOG_DIR']);logs.mkdir(parents=True,exist_ok=True)
            (logs/(name+'.log')).write_text(f'COMMAND: {bash_executable} run_offline_checks.sh\nSUITE: {test_class}\n'+output+f'\nEXIT_CODE: {result.returncode}\n')
        caught=result.returncode!=0 and expected in output
        print(f"MUTATION,{name},detected={caught},exit={result.returncode},expected_message={expected}",flush=True)
        if not caught:failed.append((name,output[-500:]))
if failed:
    print('MUTATION_GATE_FAILED:',failed,file=sys.stderr);sys.exit(1)
print(f'MUTATION_GATE_PASS: {len(variants)} independent safety regressions detected')
