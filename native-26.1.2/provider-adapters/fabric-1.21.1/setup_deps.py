"""Fetch only the hash-pinned provider and extract its unchanged development dependencies."""
import hashlib,json,pathlib,urllib.request,zipfile
root=pathlib.Path(__file__).resolve().parent
lock=json.loads((root/'provider.lock.json').read_text())
deps=root/'deps';deps.mkdir(exist_ok=True)
provider=deps/'immersive-portals-6.0.6-mc1.21.1-fabric.jar'
if provider.is_symlink() or deps.is_symlink():raise RuntimeError('linked dependency path refused')
if not provider.exists():
    data=urllib.request.urlopen(lock['url'],timeout=60).read()
    if hashlib.sha512(data).hexdigest()!=lock['sha512']:raise RuntimeError('provider download hash mismatch')
    provider.write_bytes(data)
if hashlib.sha512(provider.read_bytes()).hexdigest()!=lock['sha512']:raise RuntimeError('provider hash mismatch')
for item in lock['nested_dependencies']:
    name=item['file']
    if pathlib.Path(name).name!=name:raise RuntimeError('invalid pinned filename')
    parent=deps/item.get('parent',provider.name)
    with zipfile.ZipFile(parent) as archive:
        matches=[n for n in archive.namelist() if n.startswith('META-INF/jars/') and n.endswith('/'+name)]
        if len(matches)!=1:raise RuntimeError('missing or ambiguous nested dependency: '+name)
        data=archive.read(matches[0])
    if hashlib.sha256(data).hexdigest()!=item['sha256']:raise RuntimeError('nested dependency hash mismatch: '+name)
    target=deps/name
    if target.is_symlink():raise RuntimeError('linked dependency file refused')
    target.write_bytes(data)
    print(name,item['sha256'])
print('PINNED_PROVIDER_DEPENDENCIES_VERIFIED',hashlib.sha256(provider.read_bytes()).hexdigest())
