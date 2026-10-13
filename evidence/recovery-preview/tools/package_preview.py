import pathlib,shutil,hashlib,json,zipfile,difflib,xml.etree.ElementTree as ET,html
base=pathlib.Path(__file__).resolve().parent.parent
native=base/'work/handoff/GASLIGHT_CODEX_HANDOFF/ACTIVE_PROJECT'
project=native/'provider-adapters/fabric-1.21.1'
out=base/'outputs/continuous-build'
evidence=out/'evidence'
package=base/'outputs/gaslight-recovery-preview'
package.mkdir(exist_ok=True)
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def copy(p,relative):
    q=package/relative;q.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,q);return q
for folder in ['build/libs','build/devlibs']:
    for p in (project/folder).glob('*.jar'):copy(p,'artifacts/'+p.name)
copy(project/'deps/immersive-portals-6.0.6-mc1.21.1-fabric.jar','dependencies/immersive-portals-6.0.6-mc1.21.1-fabric.jar')
fabric=pathlib.Path.home()/'.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.109.0+1.21.1'
for p in fabric.rglob('*.jar'):copy(p,'dependencies/'+p.name)
for p in evidence.rglob('*'):
    if p.is_file() and p.suffix not in {'.class'}:copy(p,'evidence/'+p.relative_to(evidence).as_posix())
for p in (project/'build/test-results/test').glob('*.xml'):copy(p,'evidence/windows-unit-results/'+p.name)
screens=project/'run-client/screenshots'
for p in screens.glob('2026-10-13*.png'):copy(p,'evidence/screenshots/'+p.name)
for name in ['continuous_build.py','continuous_control.py','continuous_linux.py','continuous_linux.sh','phase2.py','package_preview.py']:copy(base/'work'/name,'evidence/tools/'+name)
for p in [project/'LICENSE',project/'THIRD_PARTY_NOTICES.md',out/'REUSE.md']:copy(p,p.name)
jarinfo=[]
for p in (package/'artifacts').glob('*.jar'):
    with zipfile.ZipFile(p) as z:
        names=z.namelist();info=dict(file=p.name,sha256=digest(p),bytes=p.stat().st_size,names=names)
        assert not any('/probe/' in n for n in names),'test driver leaked into production JAR'
        if 'fabric.mod.json' in names:info['metadata']=json.loads(z.read('fabric.mod.json'))
        if 'META-INF/MANIFEST.MF' in names:info['manifest']=z.read('META-INF/MANIFEST.MF').decode()
        classes=[n for n in names if n.endswith('.class') and n.startswith('dev/')]
        if classes:info['class_major_versions']=sorted(set(int.from_bytes(z.read(n)[6:8],'big') for n in classes));assert info['class_major_versions']==[69]
        for n in names:
            if n.startswith('META-INF/jars/') and n.endswith('.jar'):
                import io
                with zipfile.ZipFile(io.BytesIO(z.read(n))) as nested:
                    for k in nested.namelist():
                        if ('license' in k.lower() or 'notice' in k.lower()) and not k.endswith('/'):
                            q=package/'licenses'/pathlib.Path(n).name/pathlib.Path(k).name;q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(nested.read(k))
        jarinfo.append(info)
(package/'evidence/jar-verification.json').write_text(json.dumps(jarinfo,indent=2))
tests=[]
for p in (project/'build/test-results/test').glob('TEST-*.xml'):
    r=ET.parse(p).getroot();tests.append(dict(file=p.name,**r.attrib))
(package/'evidence/unit-summary.json').write_text(json.dumps(tests,indent=2))
diff=[];changes=[];added=removed=0
baseline=out/'baseline'
for p in sorted(project.rglob('*')):
    rel=p.relative_to(project)
    if not p.is_file() or any(x in {'build','.gradle','deps','run','run-server','run-client','run-artifacts','__pycache__'} for x in rel.parts):continue
    old=baseline/rel
    if old.exists() and old.read_bytes()==p.read_bytes():continue
    try:
        d=list(difflib.unified_diff(old.read_text().splitlines(True) if old.exists() else [],p.read_text().splitlines(True),fromfile='a/'+rel.as_posix(),tofile='b/'+rel.as_posix()))
        diff.extend(d);a=sum(s.startswith('+') and not s.startswith('+++') for s in d);r=sum(s.startswith('-') and not s.startswith('---') for s in d)
        production=rel.parts[:3]==('src','main','java')
        if production:added+=a;removed+=r
        changes.append(dict(file=rel.as_posix(),added=a,removed=r,production=production))
    except UnicodeError:changes.append(dict(file=rel.as_posix(),binary=True))
(package/'evidence/source.diff').write_text(''.join(diff))
(package/'evidence/modified-files.json').write_text(json.dumps(dict(files=changes,production_added=added,production_removed=removed,production_changed=added+removed),indent=2))
source=package/'GASLIGHT_SOURCE.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(native.rglob('*')):
        rel=p.relative_to(native)
        if p.is_file() and not any(x in {'build','.gradle','run','run-server','run-client','run-artifacts','__pycache__'} or x.startswith('run-') for x in rel.parts):z.write(p,'ACTIVE_PROJECT/'+rel.as_posix())
report=(out/'REPORT.md').read_text() if (out/'REPORT.md').exists() else 'REPORT_PENDING'
report+='\n\nSource archive SHA-256: `'+digest(source)+'`.\nProduction diff: '+str(added)+' added / '+str(removed)+' removed / '+str(added+removed)+' changed lines.\n'
(package/'REPORT.md').write_text(report)
(package/'README.md').write_text('''# GASLIGHT recovery preview — Minecraft 1.21.1 Fabric, Java 25

Full GASLIGHT: NO-GO. This package does not make houses or terrain disappear when
you look away. It contains a genuine fixed-portal development preview, saved-chunk
preparation, recovery improvements, source, raw evidence and precise limitations.

`artifacts/*-dev.jar` is the named development JAR. `artifacts/*-recovery-preview.jar`
is the remapped normal-loader artifact. See REPORT.md for which exact hashes were
run in game. Install Fabric Loader 0.19.5 and the two original dependency JARs under
dependencies for a normal modpack. The production mod stays inactive without its
exact disposable-world marker; this is a development preview, not normal automatic
GASLIGHT gameplay. Never install the integration driver in an existing world.

To reproduce the disposable development test, extract GASLIGHT_SOURCE.zip, enter
ACTIVE_PROJECT/provider-adapters/fabric-1.21.1, read README.md, run setup_deps.py and
build with JDK 25. Create a fresh fixture with prepare_disposable_test.py using a new
two-digit suffix. Accept Minecraft's EULA yourself before starting a new environment.
Run the server with `gradlew.bat --no-daemon --max-workers=2 runServerProof
-PgaslightPackaged -PgaslightSqlite`, then run the client with
`gradlew.bat --no-daemon --max-workers=2 runClientProof -PgaslightPackaged`.
The existing probe-client.txt command `connect-demo` connects the graphical client.
All worlds must be newly created disposable fixtures. No Minecraft game binaries
are redistributed here. Dependencies and downloads require internet access.
''')
reference=(base/'work/gaslight-github/docs/DESIGN.md').read_text()+'\n\n'+report+'\n\n'+(out/'REUSE.md').read_text()
(package/'GASLIGHT_PRIVATE_REFERENCE.html').write_text('<!doctype html><meta charset="utf-8"><title>GASLIGHT private development reference</title><style>body{max-width:1000px;margin:40px auto;font:16px/1.5 system-ui}pre{white-space:pre-wrap}</style><h1>GASLIGHT — private reference</h1><p>Development evidence. Full automatic experience remains NO-GO.</p><pre>'+html.escape(reference)+'</pre>')
hashes={p.relative_to(package).as_posix():digest(p) for p in sorted(package.rglob('*')) if p.is_file() and p.name!='SHA256SUMS.json'}
(package/'SHA256SUMS.json').write_text(json.dumps(hashes,indent=2))
archive=base/'outputs/GASLIGHT_RECOVERY_PREVIEW.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(package.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(package).as_posix())
(base/'outputs/GASLIGHT_RECOVERY_PREVIEW_SHA256.txt').write_text(digest(archive)+'  '+archive.name+'\n')
print(json.dumps(dict(package=str(archive),bytes=archive.stat().st_size,sha256=digest(archive),source_sha256=digest(source),tests=tests,production_added=added,production_removed=removed),indent=2))
