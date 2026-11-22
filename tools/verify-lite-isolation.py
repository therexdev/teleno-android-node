#!/usr/bin/env python3
"""Check the side-by-side package boundary without installing or changing apps."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--standard-root',type=Path,required=True)
p.add_argument('--sdk',type=Path,required=True)
p.add_argument('--apk',type=Path,required=True)
args=p.parse_args()
android='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
standard=ET.parse(args.standard_root/'app/src/main/AndroidManifest.xml').getroot()
assert manifest.attrib['package']=='com.koinos.teleno.lite'
assert standard.attrib['package']=='com.koinos.teleno'
assert android+'sharedUserId' not in manifest.attrib
app=manifest.find('application')
assert app.attrib[android+'label']=='Teleno Node Lite'
assert app.attrib[android+'allowBackup']=='false'
assert app.attrib[android+'icon']!=standard.find('application').attrib[android+'icon']
for tag in ['activity','receiver','service']:
 for component in app.findall(tag):
  assert component.attrib[android+'name'].startswith('com.koinos.teleno.')
  if tag!='activity':assert component.attrib[android+'exported']=='false'
gradle=(root/'app/build.gradle').read_text()
assert "applicationId 'com.koinos.teleno.lite'" in gradle
# Java/JNI namespace stays stable; Android install identity is the manifest ID.
assert "namespace 'com.koinos.teleno'" in gradle
ports=[]
for checkout in [args.standard_root,root]:
 source=(checkout/'app/src/main/java/com/koinos/teleno/NodeConfig.java').read_text()
 ports.append({name:int(re.search(name+r' = (\d+);',source).group(1)) for name in ['RPC_PORT','P2P_PORT']})
assert not set(ports[0].values())&set(ports[1].values())
for filename,token in [('KeyVault.java','teleno-lite-block-signer-v1-'),('VaultStore.java','teleno-lite-koin-vault-session-v1'),('NodeStorage.java','teleno-lite-chain')]:
 source=(root/'app/src/main/java/com/koinos/teleno'/filename).read_text();assert token in source
 assert token not in (args.standard_root/'app/src/main/java/com/koinos/teleno'/filename).read_text()
bt=next(args.sdk/'build-tools'/d for d in ['36.0.0','android-16'] if (args.sdk/'build-tools'/d/'aapt2').exists())
badging=subprocess.check_output([str(bt/'aapt2'),'dump','badging',str(args.apk)],text=True)
assert "name='com.koinos.teleno.lite' versionCode='1' versionName='0.1.0'" in badging
assert "application-label:'Teleno Node Lite'" in badging
assert "launchable-activity: name='com.koinos.teleno.MainActivity'" in badging
with zipfile.ZipFile(args.apk) as archive:
 assert 'lib/arm64-v8a/libteleno_node.so' not in archive.namelist()
 info=json.loads(archive.read('assets/engine.json'))
 assert info['applicationId']=='com.koinos.teleno.lite'
 assert not any(info[k] for k in ['available','productionQualified','experimentalProductionEnabled','liteStorageReady'])
print(json.dumps({'package':manifest.attrib['package'],'standard_package':standard.attrib['package'],'lite_ports':ports[1],'separate_identity':True,'standard_engine_bundled':False,'compact_mining_ready':False,'device_installation_tested':False},indent=2))
