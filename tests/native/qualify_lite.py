#!/usr/bin/env python3
import sys,pathlib,json,time,subprocess,socket,urllib.request,base64,hashlib,signal,shutil,re,os
from google.protobuf import json_format
from rocksdict import Rdict,Options,AccessType
import argparse,types
parser=argparse.ArgumentParser(description='Compare pruned ARM64 state with the reference node and test isolated federated production. Never uses a live peer network or wallet.')
parser.add_argument('--workspace',type=pathlib.Path,required=True)
parser.add_argument('--output',type=pathlib.Path,required=True)
parser.add_argument('--engine-source',type=pathlib.Path,required=True)
args=parser.parse_args();workspace=args.workspace.resolve();root=workspace/'deps/lite-probe';out=args.output.resolve();out.mkdir(parents=True,exist_ok=False)
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[2]/'tools'));import lite_snapshot as lite
chain_pb2=types.SimpleNamespace(genesis_data=lite.PROTO['genesis'],resource_limit_data=lite.PROTO['resources'],max_account_resources=lite.PROTO['max_resources'],compute_bandwidth_registry=lite.PROTO['registry'])
protocol_pb2=types.SimpleNamespace(block_header=lite.PROTO['header'])
key=dict(line.split('=',1) for line in (root/'private-testnet-key.txt').read_text().splitlines());assert key['seed']=='teleno-private-testnet-producer'
def unb64(v):return bytes.fromhex(v[2:]) if v.startswith('0x') else base64.urlsafe_b64decode(v+'='*(-len(v)%4))
def b64(v):return base64.urlsafe_b64encode(v).decode()
gen=chain_pb2.genesis_data()
def entry(name,value):
 e=gen.entries.add();e.space.system=True;e.key=lite.multihash(('object_key::'+name).encode());e.value=value
entry('genesis_key',unb64(key['address_base64']))
entry('resource_limit_data',chain_pb2.resource_limit_data(disk_storage_cost=10,disk_storage_limit=409600,network_bandwidth_cost=5,network_bandwidth_limit=1048576,compute_bandwidth_cost=1,compute_bandwidth_limit=100000000).SerializeToString())
entry('max_account_resources',chain_pb2.max_account_resources(value=10000000).SerializeToString())
s=(args.engine_source/'tests/chain/controller_delta_test.cpp').read_text();s=s[s.index('thunk_compute = {'):s.index('chain::compute_bandwidth_registry registry;')];registry=chain_pb2.compute_bandwidth_registry()
for name,cost in re.findall(r'\{ "([a-z0-9_]+)", ([0-9\']+) \}',s):registry.entries.add(name=name,compute=int(cost.replace("'",'')))
entry('compute_bandwidth_registry',registry.SerializeToString());entry('block_hash_code',b'\x12')
qemu=[str(workspace/'deps/debian/sysroot/usr/bin/qemu-aarch64'),'-L',str(workspace/'deps/android-runtime/root'),'-E','LD_LIBRARY_PATH=/apex/com.android.runtime/lib64/bionic']
arm=qemu+[str(workspace/'deps/lite-arm64/teleno_node')];baseline=[str(root/'baseline/teleno-node-1.3.0/bin/teleno_node')];opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
processes=[]
class Node:
 def __init__(self,name,command,existing=None,produce=False):
  self.name=name;self.path=out/name
  if existing:shutil.copytree(existing,self.path)
  else:
   self.path.mkdir();(self.path/'chain').mkdir();(self.path/'chain/genesis_data.json').write_text(json_format.MessageToJson(gen))
  with socket.socket() as sk:sk.bind(('127.0.0.1',0));self.port=sk.getsockname()[1]
  self.command=command;self.config(produce)
 def config(self,produce=False):
  self.cfg=self.path/'config.yml';self.cfg.write_text(f'''global:
  log-level: info
  fork-algorithm: pob
chain:
  verify-blocks: true
jsonrpc:
  listen: 127.0.0.1:{self.port}
  jobs: 2
rocksdb:
  block-cache-mb: 16
features:
  chain: true
  block_store: true
  mempool: true
  jsonrpc: true
  p2p: false
  grpc: false
  block_producer: {str(produce).lower()}
  contract_meta_store: false
  transaction_store: false
  account_history: false
''')
  if produce:
   (self.path/'block_producer').mkdir(exist_ok=True);(self.path/'block_producer/private.key').write_text(key['wif']);os.chmod(self.path/'block_producer/private.key',0o600)
   with self.cfg.open('a') as f:f.write('block_producer:\n  algorithm: federated\n  gossip-production: false\n  private-key-file: '+json.dumps(str(self.path/'block_producer/private.key'))+'\n')
 def rpc(self,method,params={}):
  req=urllib.request.Request(f'http://127.0.0.1:{self.port}',data=json.dumps({'jsonrpc':'2.0','id':1,'method':method,'params':params}).encode(),headers={'Content-Type':'application/json'})
  with opener.open(req,timeout=25) as f:x=json.load(f)
  if 'error'in x:raise RuntimeError(method+': '+str(x['error']))
  return x['result']
 def start(self):
  self.log=(out/(self.name+'.log')).open('a');self.proc=subprocess.Popen([*self.command,'--basedir',str(self.path),'--config',str(self.cfg)],stdout=self.log,stderr=subprocess.STDOUT);processes.append(self)
  until=time.monotonic()+60
  while time.monotonic()<until:
   if self.proc.poll()is not None:raise RuntimeError(self.name+' exited '+str(self.proc.returncode))
   try:self.rpc('chain.get_head_info');return
   except (OSError,TimeoutError):time.sleep(.15)
  raise RuntimeError('readiness timeout '+self.name)
 def stop(self):
  if self.proc.poll()is None:self.proc.send_signal(signal.SIGTERM)
  try:self.proc.wait(timeout=20)
  except subprocess.TimeoutExpired:self.proc.kill();self.proc.wait()
  self.log.close()
 def head(self):return self.rpc('chain.get_head_info')
def same(a,b):
 x,y=a.head(),b.head();assert x['head_topology']['id']==y['head_topology']['id'],(x,y);assert unb64(x['head_state_merkle_root'])==unb64(y['head_state_merkle_root']),(x,y)
try:
 ref=Node('reference',baseline);ref.start()
 seed=Node('lite-seed',arm);seed.start();seed.stop()
 d=Rdict(str(seed.path/'db'),lite.options());cf=d.get_column_family('block_meta');cf[b'lite.retained_from']=b'1';cf[b'lite.retention_blocks']=b'64';cf.close();d.close()
 seed.start();same(seed,ref)
 siglog=(out/'signer.log').open('w');signer=subprocess.Popen(qemu+[str(workspace/'deps/lite-arm64/test_block_signer')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=siglog,text=True)
 blocks={}
 for h in range(1,151):
  head=ref.head();hdr=protocol_pb2.block_header(previous=unb64(head['head_topology']['id']),height=h,timestamp=int(time.time()*1000),previous_state_merkle_root=unb64(head['head_state_merkle_root']),transaction_merkle_root=lite.multihash(b''),signer=unb64(key['address_base64']))
  signer.stdin.write(hdr.SerializeToString().hex()+'\n');signer.stdin.flush();line=signer.stdout.readline();parts=line.split();assert len(parts)==2,line;bid,sig=parts
  body={'id':bid,'header':{'previous':'0x'+hdr.previous.hex(),'height':str(h),'timestamp':str(hdr.timestamp),'previous_state_merkle_root':b64(hdr.previous_state_merkle_root),'transaction_merkle_root':b64(hdr.transaction_merkle_root),'signer':key['address']},'signature':b64(bytes.fromhex(sig[2:]))}
  blocks[h]=body
  for n in (ref,seed):n.rpc('chain.submit_block',{'block':body})
  same(ref,seed)
  if h%25==0:print('matching consensus roots through',h,flush=True)
 signer.stdin.close();signer.wait(timeout=10);siglog.close();seed.stop()
 d=Rdict(str(seed.path/'db'),lite.options(),access_type=AccessType.read_only());cf=d.get_column_family('block_meta');floor=int(cf[b'lite.retained_from']);cf.close();state=d.get_column_family('chain_metadata');revision=int.from_bytes(state[b'revision'],'big');identity={'identity_status':'checkpoint_verified','chain_id':'0x'+lite.multihash(gen.SerializeToString()).hex(),'state_revision':revision,'state_id':'0x'+state[b'id'].hex(),'state_merkle_root':'0x'+state[b'merkle_root'].hex()};state.close();d.close();assert floor>1,(floor,revision)
 # Publish only verified checkpoint inputs; the original node is stopped.
 cat=out/'files.json';cat.write_text(json.dumps({'files':[{'path':p.relative_to(seed.path).as_posix(),'size_bytes':p.stat().st_size,'sha256':lite.digest(p)} for p in sorted(seed.path.rglob('*')) if p.is_file()]}));man=out/'manifest.json';man.write_text(json.dumps({'backup_id':'private-tested-checkpoint','source':identity}))
 compact=out/'compact';lite.export(seed.path,cat,man,compact,64,'chain/genesis_data.json')
 restored=Node('restored-lite',arm,compact);restored.start();assert int(restored.head()['head_topology']['height'])==revision
 for h in range(revision+1,151):restored.rpc('chain.submit_block',{'block':blocks[h]})
 same(ref,restored);restored.stop();restored.config(True);restored.start();before=150;until=time.monotonic()+25
 while time.monotonic()<until:
  h=int(restored.head()['head_topology']['height'])
  if h>=before+2:break
  time.sleep(.5)
 else:raise RuntimeError('native federated producer did not advance')
 latest=restored.head();response=restored.rpc('block_store.get_blocks_by_height',{'head_block_id':latest['head_topology']['id'],'ancestor_start_height':151,'num_blocks':h-150,'return_block':True,'return_receipt':True})
 for item in response['block_items']:ref.rpc('chain.submit_block',{'block':item['block']})
 same(ref,restored);restored.stop();restored.config(False);restored.start();same(ref,restored)
 report={'engine_sha256':lite.digest(workspace/'deps/lite-arm64/teleno_node'),'profile':'lite-v1','emulated_arm64_restore':True,'matching_reference_roots_through':h,'actual_pruned_from':floor,'checkpoint_revision':revision,'compact_restore_and_catchup':True,'isolated_federated_production':True,'durable_restart':True,'physical_android_device_tested':False,'mainnet_pob_production_tested':False,'runtime':'QEMU AArch64, AOSP Android libc, stderr-only liblog shim; unused libandroid shim; no peer network'}
 (out/'result.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2),flush=True)
finally:
 for n in reversed(processes):
  try:n.stop()
  except Exception:pass
