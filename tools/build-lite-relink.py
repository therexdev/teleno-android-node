#!/usr/bin/env python3
"""Rebuild the Lite storage objects and relink the pinned Android materials.

Header sources use the documented qualification workspace layout (--deps).
No original library, existing build directory, or signing key is overwritten.
This produces a build record, not a claim of runtime qualification.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
for name in ('source', 'deps', 'ndk', 'materials', 'output'):
    parser.add_argument('--' + name, type=Path, required=True)
parser.add_argument('--test-tools',action='store_true',help='Also link deterministic private-network keygen and block signer')
args = parser.parse_args()
source, deps, ndk, materials, output = [getattr(args, n).resolve() for n in ('source', 'deps', 'ndk', 'materials', 'output')]
if output.exists():
    parser.error('Output must be a new directory.')
if '27.3.13750724' not in (ndk / 'source.properties').read_text():
    parser.error('Use the pinned Android NDK r27d.')
lock = json.loads((ROOT / 'native/lite-engine.json').read_text())
for rel, expected in lock['sourceSha256'].items():
    if hashlib.sha256((source / rel).read_bytes()).hexdigest() != expected:
        parser.error('Lite source does not match its lock: ' + rel)
original = json.loads((materials / 'link.json').read_text())
if original['telenoCommit'] != lock['upstreamCommit'] or original['ndk'] != '27.3.13750724':
    parser.error('Native materials do not match the pinned engine/toolchain.')
for row in original['inputs']:
    if hashlib.sha256((materials / row['file']).read_bytes()).hexdigest() != row['sha256']:
        parser.error('Original native input checksum failed: ' + row['file'])
boost = list((deps / 'host-engine/hunter/_Base').glob('*/*/*/Install/include'))
if len(boost) != 1:
    parser.error('Supply the qualification dependency layout, including Boost 1.83 headers.')
includes = [source / 'src', output, deps / 'rocksdb-8.8.1-source/include',
            deps / 'protobuf-owned-source/src', deps / 'proto-cpp-source/include',
            deps / 'component-test/exception/include', deps / 'component-test/log/include',
            deps / 'component-test/util/include', deps / 'component-test/include', boost[0]]
for directory in includes[2:]:
    if not directory.is_dir():
        parser.error('Missing header source: ' + str(directory))
output.mkdir(parents=True)
llvm = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin'
(output / 'git_version.h').write_text('#pragma once\n#define KOINOS_NODE_VERSION "1.3.0"\n#define KOINOS_NODE_RELEASE_TAG "teleno-node-v1.3.0"\n#define KOINOS_NODE_GIT_COMMIT "2c11229267cf"\n#define KOINOS_NODE_BUILD_VERSION "1.3.0+2c11229267cf-lite.1"\n')
command = [str(llvm / 'aarch64-linux-android28-clang++'), '-std=c++20', '-O3', '-fPIC',
           '-ffunction-sections', '-fdata-sections', '-funwind-tables', '-fstack-protector-strong',
           '-DBOOST_STACKTRACE_GNU_SOURCE_NOT_REQUIRED', '-Wno-error=deprecated-declarations',
           *['-I' + str(path) for path in includes]]
for rel in ('block_store/block_store.cpp', 'storage/rocksdb_manager.cpp'):
    obj = output / (Path(rel).name + '.o')
    subprocess.run([*command, '-c', str(source / 'src' / rel), '-o', str(obj)], check=True)
bundle = output / 'relink'
shutil.copytree(materials, bundle)
replaced = []
for library, obj in [('libkoinos_storage_lib.a', 'rocksdb_manager.cpp.o'),
                     ('libkoinos_block_store_lib.a', 'block_store.cpp.o')]:
    row = next(r for r in original['inputs'] if r['sourceName'] == library)
    subprocess.run([str(llvm / 'llvm-ar'), 'r', str(bundle / row['file']), str(output / obj)], check=True)
    replaced.append({'file': row['file'], 'sha256': hashlib.sha256((bundle / row['file']).read_bytes()).hexdigest()})
subprocess.run(['python3', str(bundle / 'relink.py'), '--ndk', str(ndk), '--output',
                str(output / 'teleno_node'), '--allow-modified-inputs'], check=True)
if args.test_tools:
    test_command=command+['-I'+str(deps/'component-test/crypto/include'),
                          '-I'+str(materials/'build-support/openssl/include')]
    main_input=str(bundle/next(row['file'] for row in original['inputs'] if row['sourceName']=='main.cpp.o'))
    for name,code in [('private_testnet_keygen',source/'tools/private_testnet_keygen.cpp'),
                      ('test_block_signer',ROOT/'tests/native/lite_block_signer.cpp')]:
        obj=output/(name+'.o')
        subprocess.run([*test_command,'-c',str(code),'-o',str(obj)],check=True)
        link=[v.replace('@BUNDLE@',str(bundle)).replace('@NDK@',str(ndk)).replace('@OUTPUT@',str(output/name)) for v in original['argv']]
        subprocess.run([str(obj) if v==main_input else v for v in link],check=True)
record = {'profile': 'lite-v1', 'upstreamCommit': lock['upstreamCommit'],
          'lite_patch_sha256': lock['patchSha256'], 'replaced_inputs': replaced,
          'engine_sha256': hashlib.sha256((output / 'teleno_node').read_bytes()).hexdigest(),
          'runtimeQualified': False}
(output / 'lite-build.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record, indent=2))
