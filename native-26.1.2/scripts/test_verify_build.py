#!/usr/bin/env python3
"""Fault injection for packaging checks, using a genuinely compiled JAR input."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import warnings
import zipfile
from verify_build import verify

ROOT = Path(__file__).resolve().parents[1]
JAR = Path(sys.argv.pop(1))
MAIN = 'dev/kondalabs/gaslight/GaslightNativeMod.class'
CLIENT = 'dev/kondalabs/gaslight/client/GaslightPreviewClient.class'


class PackagingFailures(unittest.TestCase):
    def mutate(self, change):
        temporary = tempfile.TemporaryDirectory(prefix='gaslight-package-fault-')
        self.addCleanup(temporary.cleanup)
        target = Path(temporary.name) / 'fault.jar'
        with zipfile.ZipFile(JAR) as source:
            entries = {n: source.read(n) for n in source.namelist()}
        change(entries)
        with zipfile.ZipFile(target, 'w') as output:
            for name, data in entries.items():
                output.writestr(name, data)
        return target

    def metadata_fault(self, field, value):
        def change(entries):
            data = json.loads(entries['fabric.mod.json'])
            data['depends'][field] = value
            entries['fabric.mod.json'] = json.dumps(data).encode()
        return self.mutate(change)

    def test_authentic_artifact(self):
        self.assertGreater(verify(JAR, ROOT)['production_source_files'], 0)

    def test_wrong_minecraft(self):
        with self.assertRaisesRegex(ValueError, 'wrong Minecraft/Java target'):
            verify(self.metadata_fault('minecraft', '=26.3'), ROOT)

    def test_wrong_java(self):
        with self.assertRaisesRegex(ValueError, 'wrong Minecraft/Java target'):
            verify(self.metadata_fault('java', '>=21'), ROOT)

    def test_missing_client(self):
        with self.assertRaisesRegex(ValueError, 'missing production class'):
            verify(self.mutate(lambda e: e.pop(CLIENT)), ROOT)

    def test_fake_api_class(self):
        with self.assertRaisesRegex(ValueError, 'bundled Minecraft/Fabric API classes'):
            verify(self.mutate(lambda e: e.update({'net/minecraft/Fake.class': e[MAIN]})), ROOT)

    def test_old_bytecode(self):
        def change(e):
            e[MAIN] = e[MAIN][:6] + bytes([0, 65]) + e[MAIN][8:]
        with self.assertRaisesRegex(ValueError, 'not Java 25 non-preview bytecode'):
            verify(self.mutate(change), ROOT)

    def test_test_class_leak(self):
        with self.assertRaisesRegex(ValueError, 'test class packaged'):
            verify(self.mutate(lambda e: e.update({'dev/kondalabs/gaslight/NativeTests.class': e[MAIN]})), ROOT)

    def test_duplicate_entry(self):
        target = self.mutate(lambda e: None)
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(target, 'a') as output:
                output.writestr('fabric.mod.json', '{}')
        with self.assertRaisesRegex(ValueError, 'duplicate JAR entries'):
            verify(target, ROOT)


if __name__ == '__main__':
    unittest.main(verbosity=2)
