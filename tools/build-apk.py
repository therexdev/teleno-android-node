#!/usr/bin/env python3
"""Build a self-contained Android APK using official SDK tools and ECJ.
This Lite foundation omits the standard engine and blocks compact mining until
its runtime and snapshot are qualified.
"""
import argparse, hashlib, json, os, pathlib, shutil, struct, subprocess, zipfile
import xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[1]

def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)

def elf(path, executable):
    data = path.read_bytes()
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01': raise ValueError(f'{path.name}: expected ELF64 little endian')
    kind, machine = struct.unpack_from('<HH', data, 16)
    if machine != 183 or kind != 3: raise ValueError(f'{path.name}: expected ARM64 PIE/shared ELF')
    phoff = struct.unpack_from('<Q', data, 32)[0]
    entsize, count = struct.unpack_from('<HH', data, 54)
    if entsize != 56 or phoff + count * entsize > len(data): raise ValueError('Invalid ELF program headers')
    interpreter = None
    for i in range(count):
        ty, flags, off, va, pa, size, memsize, align = struct.unpack_from('<IIQQQQQQ', data, phoff + i * entsize)
        if off + size > len(data): raise ValueError('Invalid ELF segment')
        if ty == 1 and align < 16384: raise ValueError(f'{path.name}: LOAD segment is not 16 KiB compatible')
        if ty == 3: interpreter = data[off:off+size].rstrip(b'\0')
    if executable and interpreter != b'/system/bin/linker64': raise ValueError(f'{path.name}: not an Android executable')
    return hashlib.sha256(data).hexdigest()

p = argparse.ArgumentParser()
p.add_argument('--sdk', type=pathlib.Path, required=True)
p.add_argument('--ecj', type=pathlib.Path, required=True)
p.add_argument('--bridge', type=pathlib.Path, required=True)
p.add_argument('--engine', type=pathlib.Path)
p.add_argument('--diagnostic', action='store_true')
p.add_argument('--enable-production', '--experimental-production', dest='enable_production', action='store_true', help='Enable block production with the existing review and readiness checks')
p.add_argument('--keystore', type=pathlib.Path, help='Existing APK signing keystore, password android; never included in the APK/source ZIP')
p.add_argument('--unsigned', action='store_true', help='Build an aligned package for later signing; cannot be installed')
a = p.parse_args()
if a.engine or a.diagnostic: p.error('Lite foundation preview does not bundle the standard node engine. Omit --engine and --diagnostic.')
if a.unsigned and a.keystore: p.error('Choose unsigned preparation or an existing keystore, not both.')
if not a.unsigned and (a.keystore is None or not a.keystore.is_file()): p.error('The existing signing keystore is required. A new key cannot update the installed app. Use --unsigned to prepare the package without changing its signing identity.')
if a.enable_production: p.error('Lite preview has no qualified compact mining engine. Production cannot be enabled in this build.')
sdk = a.sdk.resolve(); a.ecj = a.ecj.resolve(); a.bridge = a.bridge.resolve(); a.keystore = a.keystore.resolve() if a.keystore else None
if a.engine: a.engine = a.engine.resolve()
if hashlib.sha256(a.ecj.read_bytes()).hexdigest() != '05cc22a24e7982970f63a405fc6c820bc80b806f27f3c5a6236fc475f8f7152b': raise SystemExit('Expected verified ECJ 3.40.0 compiler.')
# Official build-tools r36 archive uses android-16 as its internal directory name.
bt = next((sdk/'build-tools'/x for x in ('36.0.0', 'android-16') if (sdk/'build-tools'/x/'aapt2').is_file()), None)
if bt is None: raise SystemExit('Install Android SDK build-tools 36.0.0.')
jar = sdk/'platforms/android-36/android.jar'
if not jar.is_file(): raise SystemExit('Install Android SDK platform 36.')
b = ROOT/'build/apk'; b.mkdir(parents=True, exist_ok=True)
for name in ('classes','generated','dex','assets'):
    target=b/name
    if target.exists(): shutil.rmtree(target)
    target.mkdir()
shutil.copytree(ROOT/'app/src/main/assets', b/'assets', dirs_exist_ok=True)
artifacts = {'libteleno_keys.so': a.bridge/'libteleno_keys.so', 'libteleno_runner.so': a.bridge/'teleno_runner'}
if a.engine: artifacts['libteleno_node.so'] = a.engine
hashes = {name: elf(path, name != 'libteleno_keys.so') for name,path in artifacts.items()}
info = {'available':bool(a.engine), 'productionQualified':False, 'experimentalProductionEnabled':False, 'liteStorageReady':False, 'applicationId':'com.koinos.teleno.lite', 'abi':'arm64-v8a', 'sourceCommit':'2c11229267cff2f9ee39ed8e56ab70af391d85ce', 'sha256':hashes,
        'reason':'Lite preview: the compact mining engine and snapshot are not available yet.'}
(b/'assets/engine.json').write_text(json.dumps(info, indent=2)+'\n')
manifest = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
if ET.fromstring(manifest).attrib.get('package') != 'com.koinos.teleno.lite': raise SystemExit('Refusing to build Lite with another application ID.')
version = ET.fromstring(manifest).attrib['{http://schemas.android.com/apk/res/android}versionName']
if a.diagnostic:
    manifest = manifest.replace('android:versionName="'+version+'"', 'android:versionName="'+version+'-diagnostic"').replace('android:label="Teleno Node Lite"', 'android:label="Teleno Node Lite · Diagnostics"')
(b/'AndroidManifest.xml').write_text(manifest)
run(bt/'aapt2', 'compile', '--dir', ROOT/'app/src/main/res', '-o', b/'resources.zip')
run(bt/'aapt2', 'link', '-o', b/'unsigned.apk', '-I', jar, '--manifest', b/'AndroidManifest.xml', '--custom-package', 'com.koinos.teleno', '--java', b/'generated', '-A', b/'assets', b/'resources.zip')
sources = sorted((ROOT/'app/src/main/java').rglob('*.java')) + sorted((b/'generated').rglob('*.java'))
run('java', '-jar', a.ecj, '-1.8', '-nowarn', '-bootclasspath', str(jar)+os.pathsep+str(bt/'core-lambda-stubs.jar'), '-d', b/'classes', *sources)
with zipfile.ZipFile(b/'classes.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    for f in sorted((b/'classes').rglob('*.class')): z.write(f, f.relative_to(b/'classes').as_posix())
run(bt/'d8', '--release', '--min-api', '28', '--lib', jar, '--output', b/'dex', b/'classes.jar')
with zipfile.ZipFile(b/'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    for f in sorted((b/'dex').glob('*.dex')): z.write(f, f.name)
    for name, path in artifacts.items(): z.write(path, 'lib/arm64-v8a/'+name)
run(bt/'zipalign', '-f', '-P', '16', '4', b/'unsigned.apk', b/'aligned.apk')
out = ROOT/'build'/('Teleno-Node-Lite-'+version+('-diagnostic' if a.diagnostic else ''))
out = out.with_name(out.name + ('-unsigned.apk' if a.unsigned else '.apk'))
if a.unsigned:
    shutil.copyfile(b/'aligned.apk', out)
else:
    run(bt/'apksigner', 'sign', '--ks', a.keystore, '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', out, b/'aligned.apk')
    run(bt/'apksigner', 'verify', '--verbose', out)
run(bt/'zipalign', '-c', '-P', '16', '4', out)
print(json.dumps({'apk':str(out), 'signed':not a.unsigned, 'bytes':out.stat().st_size, 'sha256':hashlib.sha256(out.read_bytes()).hexdigest(), 'nodeEngineBundled':bool(a.engine), 'productionQualified':False, 'experimentalProductionEnabled':False, 'liteStorageReady':False, 'applicationId':'com.koinos.teleno.lite'},indent=2))
