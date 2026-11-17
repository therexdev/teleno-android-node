#!/usr/bin/env python3
"""Export pinned cpp-libp2p, apply Teleno's two patches, and set Android build inputs."""
import argparse
import hashlib
import io
import json
import subprocess
import tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PIN = 'c03bf7c44c4fd30a390f3ef93bd1eab482e686a6'
TELENO = '2c11229267cff2f9ee39ed8e56ab70af391d85ce'
p = argparse.ArgumentParser()
p.add_argument('--source', type=Path, required=True)
p.add_argument('--teleno', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
output = a.output.resolve()
if output.exists():
    raise SystemExit('Output must be new; existing source is preserved.')
archive = subprocess.check_output(['git', '-C', str(a.source), 'archive', '--format=tar', PIN])
output.mkdir(parents=True)
with tarfile.open(fileobj=io.BytesIO(archive)) as files:
    files.extractall(output, filter='data')
patches = {}
for name in ('cpp-libp2p-koinos.patch', 'cpp-libp2p-listener-lifetime.patch'):
    patch = subprocess.check_output(['git', '-C', str(a.teleno), 'show', TELENO + ':cmake/' + name])
    patches[name] = hashlib.sha256(patch).hexdigest()
    subprocess.run(['git', 'apply', '-'], input=patch, cwd=output, check=True)
config = output / 'cmake/Hunter/config.cmake'
text = config.read_text()
old = 'soralog\n    VERSION 0.2.5\n'
assert text.count(old) == 1
text = text.replace(old, old + '    CONFIGURATION_TYPES Release\n    CMAKE_ARGS\n      CMAKE_POLICY_DEFAULT_CMP0125=NEW\n      CMAKE_POLICY_DEFAULT_CMP0126=NEW\n')
meta = json.loads((ROOT / 'native/vendor/protobuf-SOURCES.json').read_text())
proto = ROOT / 'native/vendor/protobuf-android.tar.gz'
assert hashlib.sha256(proto.read_bytes()).hexdigest() == meta['patchedArchiveSha256']
assert text.count(meta['upstream']) == 1 and text.count(meta['upstreamSha1']) == 1
text = text.replace(meta['upstream'], proto.as_uri()).replace(meta['upstreamSha1'], meta['patchedArchiveSha1'])
text = text.replace('hunter_config(Protobuf\n', 'hunter_config(Protobuf\n   CONFIGURATION_TYPES Release\n')
config.write_text(text)
record = {'upstreamCommit': PIN, 'telenoCommit': TELENO,
          'archiveSha256': hashlib.sha256(archive).hexdigest(),
          'upstreamTelenoPatches': patches,
          'androidChanges': 'Hunter protobuf CMake overlay; Release soralog; preserve NDK compiler under CMP0125/CMP0126.',
          'hunterConfigSha256': hashlib.sha256(config.read_bytes()).hexdigest()}
(output / 'ANDROID_BUILD_SOURCE.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record, indent=2))
