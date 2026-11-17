#!/usr/bin/env python3
"""Final Teleno ARM64 build using already-built, pinned Android dependency prefixes.

Run prepare-teleno.py first. This never marks the engine production-qualified.
"""
import argparse
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
for name in ('source', 'koinos-prefix', 'aux-prefix', 'native-deps', 'build'):
    p.add_argument('--' + name, type=Path, required=True)
p.add_argument('--cmake', default='cmake')
p.add_argument('--jobs', type=int, default=4)
a = p.parse_args()
if a.jobs < 1 or a.jobs > 32:
    p.error('Choose 1–32 parallel jobs.')
source, koinos, aux, deps, build = [getattr(a, x).resolve() for x in ('source', 'koinos_prefix', 'aux_prefix', 'native_deps', 'build')]
meta = json.loads((source / 'ANDROID_BUILD_SOURCE.json').read_text())
if meta['upstreamCommit'] != '2c11229267cff2f9ee39ed8e56ab70af391d85ce':
    raise SystemExit('Expected the pinned source prepared by prepare-teleno.py.')
if not os.environ.get('TELENO_ANDROID_NDK'):
    raise SystemExit('Set TELENO_ANDROID_NDK to r27d.')
thirdparty = deps / 'cpp-libp2p-thirdparty-include'
ssh = deps / 'libssh-static-0.12.0'
zstd = deps / 'zstd-static-1.5.7'
rocks = deps / 'rocksdb-zstd-8.8.1'
p2p = deps / 'cpp-libp2p-koinos'
gmp = deps / 'gmp-static-6.3.0/lib/libgmp.a'
for required in (
    koinos / 'lib/cmake/koinos_proto/koinos_proto-config.cmake',
    koinos / 'lib/cmake/koinos_state_db/koinos_state_db-config.cmake',
    aux / 'lib/cmake/soralog/soralogConfig.cmake',
    ssh / 'lib/libssh.a', zstd / 'lib/libzstd.a', rocks / 'lib/librocksdb.a',
    p2p / 'lib/cmake/libp2p/libp2pConfig.cmake', gmp, thirdparty / 'qtils'
):
    if not required.exists():
        raise SystemExit('Android dependency is not ready: ' + str(required))
flags = '-Wno-error=deprecated-declarations -DBOOST_STACKTRACE_GNU_SOURCE_NOT_REQUIRED -DBOOST_ASIO_USE_TS_EXECUTOR_AS_DEFAULT'
flags += ' -I' + str(koinos / 'include') + ' -I' + str(thirdparty)
values = {
    'CMAKE_TOOLCHAIN_FILE': ROOT / 'native/cmake/android-arm64.cmake',
    'CMAKE_BUILD_TYPE': 'Release', 'CMAKE_POSITION_INDEPENDENT_CODE': 'ON',
    'CMAKE_EXPORT_COMPILE_COMMANDS': 'ON',
    'CMAKE_FIND_ROOT_PATH_MODE_PACKAGE': 'BOTH',
    'CMAKE_FIND_ROOT_PATH_MODE_INCLUDE': 'BOTH',
    'CMAKE_FIND_USE_PACKAGE_REGISTRY': 'OFF',
    'CMAKE_FIND_USE_SYSTEM_PACKAGE_REGISTRY': 'OFF',
    'KOINOS_BUILD_TESTS': 'OFF', 'KOINOS_ENABLE_LIBP2P': 'ON',
    'TELENO_ANDROID_SUPPORT_DIR': ROOT / 'native',
    'TELENO_ANDROID_TELENO_SOURCE': source,
    'TELENO_GIT_COMMIT_OVERRIDE': meta['upstreamCommit'],
    'CMAKE_PROJECT_INCLUDE': ROOT / 'native/AndroidKoinosPrelude.cmake',
    'OPENSSL_ROOT_DIR': koinos, 'OPENSSL_INCLUDE_DIR': koinos / 'include',
    'OPENSSL_CRYPTO_LIBRARY': koinos / 'lib/libcrypto.a',
    'OPENSSL_SSL_LIBRARY': koinos / 'lib/libssl.a',
    'ZLIB_INCLUDE_DIR': koinos / 'include', 'ZLIB_LIBRARY': koinos / 'lib/libz.a',
    'yaml-cpp_DIR': koinos / 'lib/cmake/yaml-cpp',
    'CPP_LIBP2P_THIRDPARTY_INCLUDE_DIR': thirdparty,
    'CMAKE_CXX_FLAGS': flags, 'CMAKE_RUNTIME_OUTPUT_DIRECTORY': build,
    'CMAKE_PREFIX_PATH': ';'.join(map(str, [source / 'cmake/shims', ssh, p2p, koinos, aux])),
    'LIBSSH_ROOT': ssh, 'RocksDB_DIR': rocks / 'lib/cmake/rocksdb',
    'zstd_INCLUDE_DIRS': zstd / 'include', 'zstd_LIBRARIES': zstd / 'lib/libzstd.a',
    'GMP_LIBRARY': gmp,
}
subprocess.run([a.cmake, '-S', str(source), '-B', str(build), *[f'-D{k}={v}' for k, v in values.items()]], check=True)
subprocess.run([a.cmake, '--build', str(build), '--target', 'teleno_node', 'koinos_private_testnet_keygen', '--parallel', str(a.jobs)], check=True)
print('Native build finished. Android execution and production qualification are still required.')
