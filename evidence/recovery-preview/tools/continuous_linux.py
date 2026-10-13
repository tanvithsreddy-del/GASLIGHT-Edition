import pathlib,sys,tarfile
import phase2
phase2.out=phase2.base/'outputs/continuous-build/evidence'
p=phase2.project/'provider-adapters/fabric-1.21.1'
with tarfile.open(phase2.base/'outputs/continuous-build/current-source.tar','w') as t:
    for f in sorted(p.rglob('*')):
        if f.is_file() and not any(x in {'build','.gradle','run','run-server','run-client','run-artifacts','__pycache__'} for x in f.relative_to(p).parts):t.add(f,arcname='ACTIVE_PROJECT/provider-adapters/fabric-1.21.1/'+f.relative_to(p).as_posix())
    for name in ['Geometry','NativePortal','StructureEnvelope','Phase3RecoveryJournal','ProtectedChunkRegions']:
        f=phase2.project/'src/main/java/dev/kondalabs/gaslight'/f'{name}.java';t.add(f,arcname='ACTIVE_PROJECT/src/main/java/dev/kondalabs/gaslight/'+f.name)
script='/mnt/c/Users/Tanvith/Documents/Codex/2026-10-12/you-are-taking-over-gaslight-minecraft/work/continuous_linux.sh'
raise SystemExit(phase2.run('fresh-linux-build-and-sqlite-tests',['wsl','-d','Ubuntu','--','bash',script],phase2.base))
