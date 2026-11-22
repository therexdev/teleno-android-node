#!/usr/bin/env python3
"""Start the unchanged ARM64 engine on LiteRestoreTest's actual APK-restored files.

Uses an owned copy, loopback RPC and disabled P2P/production. QEMU's Android
runtime setup is documented in docs/BUILDING_LITE.md. No wallet is used.
"""
import argparse
import base64
import json
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import time
import urllib.request

p=argparse.ArgumentParser(description=__doc__)
for name in ('node','qemu','sysroot','restored','output'):p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args()
if a.output.exists():p.error('Use a new owned output directory.')
if not (a.restored/'.backup-just-restored').is_file():p.error('Provide a fresh LiteRestoreTest output including its observer-first marker.')
a.output.mkdir(parents=True);base=a.output.resolve()/'node';shutil.copytree(a.restored,base)
with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
config=base/'config.yml'
config.write_text(f'''global:
  log-level: info
chain:
  verify-blocks: true
jsonrpc:
  listen: 127.0.0.1:{port}
  jobs: 2
features:
  chain: true
  block_store: true
  mempool: true
  jsonrpc: true
  p2p: false
  grpc: false
  block_producer: false
  contract_meta_store: false
  transaction_store: false
  account_history: false
''')
cmd=[str(a.qemu.resolve()),'-L',str(a.sysroot.resolve()),'-E','LD_LIBRARY_PATH=/apex/com.android.runtime/lib64/bionic',str(a.node.resolve()),'--basedir',str(base),'--config',str(config),'--disable','p2p','block_producer','grpc']
preflight=subprocess.run([*cmd,'--preflight','--preflight-json'],capture_output=True,text=True,timeout=30)
(a.output/'preflight.json').write_text(preflight.stdout)
assert preflight.returncode==0 and json.loads(preflight.stdout)['no_known_blockers'],preflight.stdout+preflight.stderr
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
def rpc(method,params=None):
    req=urllib.request.Request(f'http://127.0.0.1:{port}',data=json.dumps({'jsonrpc':'2.0','id':1,'method':method,'params':params or {}}).encode(),headers={'Content-Type':'application/json'})
    with opener.open(req,timeout=5) as response:result=json.load(response)
    assert 'error' not in result,result
    return result['result']
def decode(value):return bytes.fromhex(value[2:]) if value.startswith('0x') else base64.urlsafe_b64decode(value+'='*(-len(value)%4))
with (a.output/'node.log').open('w') as log:
    process=subprocess.Popen(cmd,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL)
    try:
        deadline=time.monotonic()+40
        while time.monotonic()<deadline:
            assert process.poll() is None,'Node exited before readiness'
            try:head=rpc('chain.get_head_info');break
            except (OSError,TimeoutError):time.sleep(.1)
        else:raise RuntimeError('Native observer readiness timed out')
        chain=rpc('chain.get_chain_id')
        assert decode(chain['chain_id']).hex()=='1220592bf18654fd07fdf5d500cde3e8402ecf7f81fa5dde8f14527b08bba8805f48'
        assert int(head['head_topology']['height'])==41258090
        assert decode(head['head_state_merkle_root']).hex()=='122084544b4436efca00025168d69cf66b31e9ef9b9bd1fc02f1719a6556d2309d98'
        token=rpc('chain.read_contract',{'contract_id':'19GYjDBVXU7keLbYvMLazsGQn3GTWHjHkK','entry_point':int('ee80fd2f',16),'args':''})
        assert decode(token['result'])==b'\x08\x08'
        assert (base/'.backup-observer-recovery').exists() and not (base/'.backup-just-restored').exists()
        result={'java_apk_restore_native_startup_passed':True,'preflight_passed':True,'observer_recovery_hold_active':True,'chain_id':chain,'head':head,'koin_decimals':token,'physical_android_tested':False}
        (a.output/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
    finally:
        if process.poll() is None:process.send_signal(signal.SIGTERM)
        try:process.wait(timeout=15)
        except subprocess.TimeoutExpired:process.kill();process.wait()
