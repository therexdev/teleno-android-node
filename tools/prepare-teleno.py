#!/usr/bin/env python3
"""Export the pinned node to a new owned build tree and add Android CMake support.

The original checkout stays unchanged. Teleno's archive, ownership-patch and
runtime hash checks remain in force. This does not compile or qualify a node.
"""
import argparse
import hashlib
import io
import json
import subprocess
import tarfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMMIT = '2c11229267cff2f9ee39ed8e56ab70af391d85ce'
p = argparse.ArgumentParser()
p.add_argument('--source', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
source, output = a.source.resolve(), a.output.resolve()
if output.exists():
    raise SystemExit('Output must be a new directory; existing source is never overwritten.')
actual = subprocess.check_output(['git', '-C', str(source), 'rev-parse', COMMIT + '^{commit}'], text=True).strip()
if actual != COMMIT:
    raise SystemExit('Teleno source pin is unavailable.')
archive = subprocess.check_output(['git', '-C', str(source), 'archive', '--format=tar', COMMIT])
output.mkdir(parents=True)
with tarfile.open(fileobj=io.BytesIO(archive)) as files:
    files.extractall(output, filter='data')
recipe = output / 'cmake/TelenoProtobuf.cmake'
before = recipe.read_bytes()
anchor = 'FetchContent_GetProperties(${_content} SOURCE_DIR _source BINARY_DIR _binary)\n'
text = before.decode()
if text.count(anchor) != 1:
    raise SystemExit('Unexpected pinned protobuf recipe.')
hook = '''if(ANDROID)
  if(NOT TELENO_ANDROID_SUPPORT_DIR)
    message(FATAL_ERROR "Set TELENO_ANDROID_SUPPORT_DIR to this Android project's native directory")
  endif()
  set(TELENO_ANDROID_PROTOBUF_SOURCE "${_source}")
  include("${TELENO_ANDROID_SUPPORT_DIR}/ApplyProtobufAndroidBuild.cmake")
endif()
'''
recipe.write_text(text.replace(anchor, anchor + hook))
manifest = {
    'upstreamCommit': COMMIT,
    'archiveSha256': hashlib.sha256(archive).hexdigest(),
    'changedFiles': [{
        'path': 'cmake/TelenoProtobuf.cmake',
        'beforeSha256': hashlib.sha256(before).hexdigest(),
        'afterSha256': hashlib.sha256(recipe.read_bytes()).hexdigest(),
        'reason': 'Apply recorded Android CMake-only fix after original ownership patch; preserve all runtime checks.'
    }],
    'runtimeQualified': False,
}
(output / 'ANDROID_BUILD_SOURCE.json').write_text(json.dumps(manifest, indent=2) + '\n')
print(json.dumps(manifest, indent=2))
