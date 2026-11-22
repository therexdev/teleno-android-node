#!/usr/bin/env python3
"""Extract the tested runtime/checkpoint from a trusted signed Lite APK for rebuilds."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--apk',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
if a.output.exists():p.error('Choose a new output directory.')
with zipfile.ZipFile(a.apk) as z:
    info=json.loads(z.read('assets/engine.json'))
    if info.get('applicationId')!='com.koinos.teleno.lite' or info.get('engineProfile')!='lite-v1':p.error('Expected a compact Lite runtime APK.')
    snapshot=json.loads(z.read('assets/compact/lite-manifest.json'))
    if snapshot.get('format')!='teleno-lite-state-snapshot':p.error('Expected a compact snapshot.')
    rows=snapshot['files']
    if not 1<=len(rows)<=1024 or sum(r['size_bytes'] for r in rows)>1024**3:p.error('Invalid compact inventory size.')
    paths=set()
    for row in rows:
        path=row['path']
        if path in paths or not (path=='genesis_data.json' or re.fullmatch(r'db/(CURRENT|IDENTITY|MANIFEST-[0-9]+|OPTIONS-[0-9]+|[0-9]+\.(sst|log)|rocksdict-config\.json)',path)):p.error('Invalid compact path.')
        paths.add(path)
        entry=z.getinfo('assets/compact/'+path)
        if entry.file_size!=row['size_bytes']:p.error('Compact file size mismatch.')
    a.output.mkdir(parents=True)
    for row in rows:
        data=z.read('assets/compact/'+row['path'])
        if hashlib.sha256(data).hexdigest()!=row['sha256']:raise ValueError('Compact checksum mismatch: '+row['path'])
        target=a.output/'snapshot'/row['path'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    (a.output/'snapshot/lite-manifest.json').write_bytes(z.read('assets/compact/lite-manifest.json'))
    for name,target in [('libteleno_node.so','teleno_node'),('libteleno_runner.so','bridge/teleno_runner'),('libteleno_keys.so','bridge/libteleno_keys.so')]:
        entry=z.getinfo('lib/arm64-v8a/'+name)
        if entry.file_size>200*1024*1024:raise ValueError('Oversize native library')
        data=z.read(entry)
        if hashlib.sha256(data).hexdigest()!=info['sha256'][name]:raise ValueError('Native checksum mismatch')
        path=a.output/target;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);path.chmod(0o755)
print(a.output.resolve())
