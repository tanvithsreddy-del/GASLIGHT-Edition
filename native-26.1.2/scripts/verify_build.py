#!/usr/bin/env python3
"""Inspect an actual Fabric build; never substitute generated Minecraft APIs."""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile


def verify(path, root):
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)):
            raise ValueError('duplicate JAR entries')
        if jar.testzip() is not None:
            raise ValueError('JAR CRC failure')
        if any(n.startswith(('net/minecraft/', 'net/fabricmc/')) for n in names):
            raise ValueError('bundled Minecraft/Fabric API classes')
        metadata = json.loads(jar.read('fabric.mod.json'))
        if metadata['id'] != 'gaslight_native' or metadata['version'] == '${version}':
            raise ValueError('unexpanded or wrong mod metadata')
        if metadata['depends']['minecraft'] != '=26.1.2' or metadata['depends']['java'] != '>=25':
            raise ValueError('wrong Minecraft/Java target')
        if metadata['entrypoints'] != {
            'main': ['dev.kondalabs.gaslight.GaslightNativeMod'],
            'client': ['dev.kondalabs.gaslight.client.GaslightPreviewClient'],
        }:
            raise ValueError('wrong entrypoints')
        count = 0
        for source_set in ('main', 'client'):
            for source in (root / 'src' / source_set / 'java').rglob('*.java'):
                entry = source.relative_to(root / 'src' / source_set / 'java').with_suffix('.class').as_posix()
                if entry not in names:
                    raise ValueError('missing production class: ' + entry)
                count += 1
        classes = [n for n in names if n.endswith('.class')]
        for entry in classes:
            magic, minor, major = struct.unpack('>IHH', jar.read(entry)[:8])
            if magic != 0xCAFEBABE or major != 69 or minor != 0:
                raise ValueError('not Java 25 non-preview bytecode: ' + entry)
        for source in (root / 'src' / 'test' / 'java').rglob('*.java'):
            entry = source.relative_to(root / 'src' / 'test' / 'java').with_suffix('.class').as_posix()
            if entry in names:
                raise ValueError('test class packaged: ' + entry)
        return {'production_source_files': count, 'class_entries': len(classes),
                'metadata': metadata, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


if __name__ == '__main__':
    print(json.dumps(verify(Path(sys.argv[1]), Path(__file__).resolve().parents[1]), indent=2))
