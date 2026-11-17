#!/usr/bin/env python3
"""Verify every source file in the vendored archive against pinned upstream.
Usage: python3 tools/verify-vendored-secp.py /path/to/original-upstream.tar.gz
The sole permitted difference is the recorded host-generator CMake fix.
"""
from pathlib import Path
import hashlib,json,sys,tarfile
root=Path(__file__).resolve().parents[1]
meta=json.loads((root/'native/vendor/SOURCES.json').read_text())
upstream=Path(sys.argv[1]); patched=root/'native/vendor/secp256k1-vrf-android.tar.gz'
assert hashlib.sha256(upstream.read_bytes()).hexdigest()==meta['upstreamSha256']
assert hashlib.sha1(upstream.read_bytes()).hexdigest()==meta['upstreamSha1']
assert hashlib.sha256(patched.read_bytes()).hexdigest()==meta['patchedArchiveSha256']
assert hashlib.sha256((root/meta['patch']).read_bytes()).hexdigest()==meta['patchSha256']
old=b'\ttarget_compile_definitions(gen_context PRIVATE HAVE_CONFIG_H)'
new=b'''\t# Cross builds expose gen_context as an imported host executable.
\t# Its native sub-build applies this definition to the actual compiled target.
\tif(NOT CMAKE_CROSSCOMPILING)
\t\ttarget_compile_definitions(gen_context PRIVATE HAVE_CONFIG_H)
\tendif()'''
changed=0; files=0
with tarfile.open(upstream) as a,tarfile.open(patched) as b:
 x={m.name:m for m in a}; y={m.name:m for m in b}; assert x.keys()==y.keys()
 for name,entry in x.items():
  other=y[name]; assert (entry.type,entry.linkname,entry.mode)==(other.type,other.linkname,other.mode)
  if not entry.isfile(): continue
  one=a.extractfile(entry).read(); two=b.extractfile(other).read(); files+=1
  if name.endswith('/CMakeLists.txt') and name.count('/')==1:
   assert one.count(old)==1 and one.replace(old,new)==two; changed+=1
  else: assert one==two, name
assert changed==1
print(f'PASS: {files} source files verified; only the CMake host-generator definition handling differs. Cryptography is unchanged.')
