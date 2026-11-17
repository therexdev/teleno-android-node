#!/usr/bin/env python3
"""Preserve the actual node link inputs in a relocatable, inspectable bundle.

Debug information is removed from copies only. The original build is untouched.
The bundle permits replacing static libraries and relinking the native engine.
"""
import argparse
import hashlib
import json
import shlex
import shutil
import subprocess
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--build', type=Path, required=True)
p.add_argument('--ndk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
build, ndk, out = a.build.resolve(), a.ndk.resolve(), a.output.resolve()
if out.exists(): raise SystemExit('Use a new output directory.')
if not (build / 'teleno_node').is_file(): raise SystemExit('The final node build must have passed.')
out.mkdir(parents=True); (out / 'inputs').mkdir()
args = shlex.split((build / 'src/CMakeFiles/teleno_node.dir/link.txt').read_text())
records, rewritten, copied = [], [], {}
skip = False
for i, arg in enumerate(args):
    if skip: skip = False; continue
    if arg == '-o': rewritten += ['-o', '@OUTPUT@']; skip = True; continue
    if arg == '-Xlinker' and i + 1 < len(args) and args[i + 1].startswith('--dependency-file='):
        skip = True; continue
    # Header paths and preprocessor defines do not participate in this object-only link.
    if arg.startswith(('-I', '-D')): continue
    if arg.endswith(('.o', '.a')):
        source = Path(arg); source = (source if source.is_absolute() else build / 'src' / source).resolve()
        if source not in copied:
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            name = digest[:16] + '-' + source.name
            target = out / 'inputs' / name; shutil.copy2(source, target)
            subprocess.run([str(ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'), '--strip-debug', str(target)], check=True)
            copied[source] = 'inputs/' + name
            records.append({'file': copied[source], 'sourceName': source.name,
                            'originalSha256': digest, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                            'bytes': target.stat().st_size})
        rewritten.append('@BUNDLE@/' + copied[source])
    else:
        rewritten.append(arg.replace(str(ndk), '@NDK@'))
manifest = {'abi': 'arm64-v8a', 'api': 28, 'ndk': '27.3.13750724',
            'telenoCommit': '2c11229267cff2f9ee39ed8e56ab70af391d85ce',
            'argv': rewritten, 'inputs': records, 'productionQualified': False}
(out / 'link.json').write_text(json.dumps(manifest, indent=2) + '\n')
(out / 'relink.py').write_text('''#!/usr/bin/env python3
import argparse, hashlib, json, subprocess
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--ndk',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--allow-modified-inputs',action='store_true',help='Allow intentional replacement of a static library or object')
a=p.parse_args();root=Path(__file__).resolve().parent;ndk=a.ndk.resolve()
if '27.3.13750724' not in (ndk/'source.properties').read_text():raise SystemExit('Use NDK r27d.')
data=json.loads((root/'link.json').read_text())
for item in data['inputs']:
    path=root/item['file']
    if not path.is_file():raise SystemExit('Missing input: '+item['file'])
    if not a.allow_modified_inputs and hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']:raise SystemExit('Changed input: '+item['file']+'; use --allow-modified-inputs for an intentional library replacement.')
output=a.output.resolve();output.parent.mkdir(parents=True,exist_ok=True)
args=[x.replace('@BUNDLE@',str(root)).replace('@NDK@',str(ndk)).replace('@OUTPUT@',str(output)) for x in data['argv']]
subprocess.run(args,check=True,cwd=root)
print('Relinked Android engine:',output)
print('SHA256:',hashlib.sha256(output.read_bytes()).hexdigest())
print('Android execution and production qualification remain required.')
''')
print(json.dumps({'inputs': len(records), 'bytes': sum(x['bytes'] for x in records), 'output': str(out)}))
