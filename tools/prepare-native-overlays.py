#!/usr/bin/env python3
"""Prepare hash-verified source archives with narrow Android build fixes.
Inputs are original upstream archives from Teleno's recipes, not arbitrary trees.
"""
import argparse, hashlib, tarfile, io, gzip, difflib, json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--rocksdb',type=Path,required=True);p.add_argument('--protobuf',type=Path,required=True);a=p.parse_args()
def rocks(name,data):
    if name=='env/io_posix.cc':
        old=b'#include <sys/types.h>\n#ifdef OS_LINUX';new=b'#include <sys/types.h>\n#ifdef OS_ANDROID\n#include <sys/statfs.h>\n#endif\n#ifdef OS_LINUX'
        assert data.count(old)==1;return data.replace(old,new)
    return data

def proto(name,data):
    if name in ('cmake/libprotobuf.cmake','cmake/libprotobuf-lite.cmake'):
        target='libprotobuf-lite' if 'lite' in name else 'libprotobuf'
        text=data.decode();text=text.replace(f'target_link_libraries({target} ${{CMAKE_THREAD_LIBS_INIT}})',f'target_link_libraries({target} PUBLIC ${{CMAKE_THREAD_LIBS_INIT}})')
        text=text.replace(f'target_link_libraries({target} atomic)',f'target_link_libraries({target} PUBLIC atomic)').replace(f'target_link_libraries({target} log)',f'target_link_libraries({target} PUBLIC log)')
        block=f'if(ANDROID)\n  find_package(android_log REQUIRED)\n  target_link_libraries({target} PUBLIC android_log::android_log)\nendif()'
        assert text.count(block)==1;text=text.replace(block,'# Android logging is linked above through the NDK system log library.')
        return text.encode()
    if name=='cmake/protobuf-config.cmake.in':
        old=b'if(ANDROID)\n  find_package(android_log REQUIRED)\nendif()';assert data.count(old)==1
        return data.replace(old,b'# Android log is an NDK system library, not a downstream CMake package.')
    return data

def make(label,path,sha1,sha256,url,transform,expected):
    raw=path.read_bytes();assert hashlib.sha1(raw).hexdigest()==sha1
    if sha256:assert hashlib.sha256(raw).hexdigest()==sha256
    buffer=io.BytesIO();diff=[];changed=[];files=0
    with tarfile.open(path) as src,tarfile.open(fileobj=buffer,mode='w',format=tarfile.PAX_FORMAT) as dest:
        for m in src:
            name=m.name.split('/',1)[1] if '/' in m.name else ''
            data=src.extractfile(m).read() if m.isfile() else None
            if data is not None:
                files+=1;after=transform(name,data)
                if after!=data:
                    changed.append(name)
                    diff.extend(difflib.unified_diff(data.decode().splitlines(True),after.decode().splitlines(True),fromfile='a/'+name,tofile='b/'+name))
                data=after;m.size=len(data)
            m.uid=m.gid=0;m.uname=m.gname='';m.mtime=0;m.pax_headers={}
            dest.addfile(m,io.BytesIO(data) if data is not None else None)
    assert set(changed)==set(expected)
    out=ROOT/f'native/vendor/{label}-android.tar.gz';out.write_bytes(gzip.compress(buffer.getvalue(),mtime=0))
    patch=ROOT/f'native/patches/{label}-android-build.patch';patch.write_text(''.join(diff))
    meta={'upstream':url,'upstreamSha1':sha1,'upstreamSha256':hashlib.sha256(raw).hexdigest(),'patchedArchiveSha1':hashlib.sha1(out.read_bytes()).hexdigest(),'patchedArchiveSha256':hashlib.sha256(out.read_bytes()).hexdigest(),'patch':str(patch.relative_to(ROOT)),'patchSha256':hashlib.sha256(patch.read_bytes()).hexdigest(),'sourceFileCount':files,'changedFiles':changed}
    (ROOT/f'native/vendor/{label}-SOURCES.json').write_text(json.dumps(meta,indent=2)+'\n');print(json.dumps(meta))
make('rocksdb',a.rocksdb,'ef3e82eb750013c9fec5220911213609613776d7',None,'https://github.com/facebook/rocksdb/archive/v8.8.1.tar.gz',rocks,['env/io_posix.cc'])
make('protobuf',a.protobuf,'5796707a98eec15ffb3ad86ff50e8eec5fa65e68','824eca40d5a9b7e1090961e84efe7b90fba818156c32d92e447f7daf61522318','https://github.com/koinos/protobuf/archive/e1b1477875a8b022903b548eb144f2c7bf4d9561.tar.gz',proto,['cmake/libprotobuf.cmake','cmake/libprotobuf-lite.cmake','cmake/protobuf-config.cmake.in'])
