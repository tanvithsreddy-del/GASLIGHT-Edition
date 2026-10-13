import pathlib,time,json,sys
import phase2
p=phase2.project/'provider-adapters/fabric-1.21.1'
out=phase2.base/'outputs/continuous-build/evidence'
def request(side,command,timeout=30):
    folder=p/('run-client' if side=='client' else 'run-server')
    pattern='genuine-runClientProof*' if side=='client' else 'genuine-runServerProof*'
    log=max(out.glob(pattern+'.log'),key=lambda f:f.stat().st_mtime)
    offset=log.stat().st_size
    (folder/f'probe-{side}.txt').write_text(command)
    started=time.monotonic();answer=''
    while time.monotonic()-started<timeout:
        with log.open(encoding='utf-8',errors='replace') as f:f.seek(offset);lines=f.read().splitlines()
        answers=[s for s in lines if 'GASLIGHT_A_' in s and (command in s or command.startswith('quit') and 'QUIT' in s or command.startswith('stop') and 'STOP' in s)]
        if answers:answer=answers[-1];break
        time.sleep(.1)
    r=dict(side=side,command=command,elapsed=time.monotonic()-started,response=answer)
    with (out/'controls.jsonl').open('a') as f:f.write(json.dumps(r)+'\n')
    print(json.dumps(r),flush=True)
    if not answer or 'FAIL ' in answer:raise RuntimeError('Control failed: '+str(r))
    return answer
if __name__=='__main__':request(sys.argv[1],' '.join(sys.argv[2:]))
