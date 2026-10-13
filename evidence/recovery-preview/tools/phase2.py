import datetime,difflib,hashlib,json,os,pathlib,shutil,subprocess,sys,time,tarfile
base=pathlib.Path(__file__).resolve().parent.parent
project=base/'work/handoff/GASLIGHT_CODEX_HANDOFF/ACTIVE_PROJECT'
baseline=base/'work/phase2-baseline'
out=base/'outputs/phase2/evidence';out.mkdir(parents=True,exist_ok=True)
env=dict(os.environ)
env['JAVA_HOME']=r'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
env['PATH']=str(base/'work/bin')+os.pathsep+r'C:\Program Files\Git\bin'+os.pathsep+env['JAVA_HOME']+'/bin'+os.pathsep+env['PATH']
env['GASLIGHT_BASH']=r'C:\Program Files\Git\bin\bash.exe'
env['GASLIGHT_MUTATION_LOG_DIR']=str(out/'windows-mutations')
bash=env['GASLIGHT_BASH']
def run(name,args,cwd=project):
    log=out/(name+'.log');start=time.monotonic()
    attempt=1
    while log.exists():
        attempt+=1;log=out/(name+'-attempt-'+str(attempt)+'.log')
    with log.open('w',encoding='utf-8') as f:
        f.write('COMMAND: '+json.dumps(args)+'\nCWD: '+str(cwd)+'\nUTC: '+datetime.datetime.now(datetime.timezone.utc).isoformat()+'\n');f.flush()
        p=subprocess.Popen(args,cwd=cwd,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        for line in iter(p.stdout.readline,b''):f.write(line.decode('utf-8',errors='replace'));f.flush()
        p.wait();elapsed=round(time.monotonic()-start,3)
        f.write(f'\nEXIT_CODE: {p.returncode}\nELAPSED_SECONDS: {elapsed}\n')
    record=dict(name=name,command=args,cwd=str(cwd),exit_code=p.returncode,elapsed_seconds=elapsed)
    with (out/'commands.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(record)+'\n')
    print(json.dumps(record),flush=True);print(log.read_text(encoding='utf-8')[-3300:],flush=True)
    return p.returncode
if __name__=='__main__':
    mode=sys.argv[1]
    if mode=='windows-baseline':
        if run('windows-baseline',[bash,'run_offline_checks.sh']):sys.exit(1)
        sys.exit(run('windows-mutations',[sys.executable,'scripts/phase4_mutation_probe.py']))
    elif mode=='windows-recovery':sys.exit(run('windows-recovery',['java','-Xmx512m','-cp','build/core-test-classes','dev.kondalabs.gaslight.RecoveryJournalTests']))
    elif mode=='windows-build':sys.exit(run('windows-build',[str(project/'gradlew.bat'),'--no-daemon','--max-workers=2','clean','build','dependencyManifest','--stacktrace','--console=plain']))
    elif mode=='copy-linux':
        archive=base/'work/phase2-current.tar'
        with tarfile.open(archive,'w') as tar:
            for p in sorted(project.rglob('*')):
                rel=p.relative_to(project)
                if p.is_file() and not any(x in {'build','.gradle','run','__pycache__'} for x in rel.parts):tar.add(p,arcname=rel.as_posix())
        command='mkdir -p /var/tmp/gaslight-phase2-01a12efa/project && tar -xf /mnt/c/Users/Tanvith/Documents/Codex/2026-10-12/you-are-taking-over-gaslight-minecraft/work/phase2-current.tar -C /var/tmp/gaslight-phase2-01a12efa/project'
        sys.exit(run('linux-source-copy',['wsl','-d','Ubuntu','--','bash','-c',command]))
    elif mode in {'linux-baseline','linux-recovery','linux-build','linux-smoke'}:
        sys.exit(run(mode,['wsl','-d','Ubuntu','--','bash','/mnt/c/Users/Tanvith/Documents/Codex/2026-10-12/you-are-taking-over-gaslight-minecraft/work/phase2_linux.sh',mode]))
    elif mode=='diff':
        result=[];changes=[]
        for p in sorted(project.rglob('*')):
            rel=p.relative_to(project)
            if not p.is_file() or any(x in {'build','.gradle','run','__pycache__'} for x in rel.parts):continue
            old=baseline/rel
            if old.exists() and old.read_bytes()==p.read_bytes():continue
            changes.append(rel.as_posix())
            try:result.extend(difflib.unified_diff(old.read_text().splitlines(keepends=True) if old.exists() else [],p.read_text().splitlines(keepends=True),fromfile='a/'+rel.as_posix(),tofile='b/'+rel.as_posix()))
            except UnicodeError:result.append('Binary: '+rel.as_posix()+'\n')
        (out/'phase2.diff').write_text(''.join(result));(out/'modified-files.json').write_text(json.dumps(changes,indent=2));print(changes)
