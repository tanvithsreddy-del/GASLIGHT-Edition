import pathlib,sys
import phase2
phase2.out=phase2.base/'outputs/continuous-build/evidence'
phase2.out.mkdir(parents=True,exist_ok=True)
p=phase2.project/'provider-adapters/fabric-1.21.1'
args=[str(p/'gradlew.bat'),'--no-daemon','--max-workers=2','--console=plain']+sys.argv[1:]
raise SystemExit(phase2.run('genuine-'+('-'.join(sys.argv[1:])[:70]),args,p))
