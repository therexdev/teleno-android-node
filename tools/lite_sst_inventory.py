#!/usr/bin/env python3
"""Read only SST metadata ranges to estimate a Foundation snapshot's CF sizes.

This is an advisory measurement, not checksum verification or restore approval.
No SST is ingested and no local chain database is opened or changed.
"""
import argparse
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import struct
import urllib.request

def varint(data,pos=0):
    value=0
    for shift in range(0,70,7):
        if pos>=len(data):raise ValueError('truncated varint')
        b=data[pos];pos+=1;value|=(b&127)<<shift
        if b<128:return value,pos
    raise ValueError('oversize varint')

def entries(data):
    if len(data)<8:raise ValueError('short metadata block')
    restarts=struct.unpack_from('<I',data,len(data)-4)[0]
    end=len(data)-4-4*restarts
    if end<0:raise ValueError('invalid restart table')
    pos=0;prior=b'';out={}
    while pos<end:
        shared,pos=varint(data,pos);length,pos=varint(data,pos);size,pos=varint(data,pos)
        if shared>len(prior) or pos+length+size>end:raise ValueError('invalid metadata entry')
        key=prior[:shared]+data[pos:pos+length];pos+=length
        out[key]=data[pos:pos+size];pos+=size;prior=key
    return out

def read_range(url,offset,length,total):
    if offset<0 or length<=0 or length>262144 or offset+length>total:raise ValueError('unsafe metadata range')
    request=urllib.request.Request(url,headers={'Range':f'bytes={offset}-{offset+length-1}','Accept-Encoding':'identity'})
    with urllib.request.urlopen(request,timeout=30) as response:
        if response.status!=206 or response.headers.get('Content-Range')!=f'bytes {offset}-{offset+length-1}/{total}':raise ValueError('server did not honor exact range')
        data=response.read(length+1)
    if len(data)!=length:raise ValueError('wrong range length')
    return data

def inspect(base,row):
    digest=row['sha256'];size=row['size_bytes']
    if len(digest)!=64 or any(c not in '0123456789abcdef' for c in digest):raise ValueError('invalid object digest')
    url=base.rstrip('/')+f'/objects/sha256/{digest[:2]}/{digest[2:4]}/{digest}'
    footer=read_range(url,size-53,53,size)
    if footer[-8:]!=bytes.fromhex('f7cff485b741e288'):raise ValueError('unknown SST magic')
    version=struct.unpack_from('<I',footer,41)[0]
    if version>5:raise ValueError('unsupported SST format')
    offset,pos=varint(footer,1);length,pos=varint(footer,pos)
    block=read_range(url,offset,length+5,size)
    if block[-5]!=0:raise ValueError('compressed metaindex not supported')
    meta=entries(block[:-5]);handle=meta.get(b'rocksdb.properties')
    if handle is None:raise ValueError('missing SST properties')
    offset,pos=varint(handle);length,pos=varint(handle,pos)
    block=read_range(url,offset,length+5,size)
    if block[-5]!=0:raise ValueError('compressed properties not supported')
    props=entries(block[:-5]);name=props[b'rocksdb.column.family.name'].decode()
    def number(key):return varint(props[key])[0] if key in props else 0
    return {'path':row['path'],'sha256':digest,'bytes':size,'column_family':name,'entries':number(b'rocksdb.num.entries'),'raw_key_bytes':number(b'rocksdb.raw.key.size'),'raw_value_bytes':number(b'rocksdb.raw.value.size'),'format_version':version}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base',required=True)
    parser.add_argument('--files',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--workers',type=int,default=6)
    args=parser.parse_args()
    if args.base!='https://seed.koinosfoundation.org/backups/prodnet/teleno-bootstrap/':parser.error('Only the fixed Foundation Mainnet source is supported')
    if not 1<=args.workers<=8:parser.error('Use 1–8 workers')
    files=json.loads(args.files.read_text());rows=[r for r in files['files'] if r['path'].endswith('.sst')]
    result=[];by_cf=defaultdict(lambda:{'sst_files':0,'bytes':0,'entries':0,'raw_key_bytes':0,'raw_value_bytes':0})
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        for index,item in enumerate(pool.map(lambda r:inspect(args.base,r),rows),1):
            result.append(item);target=by_cf[item['column_family']];target['sst_files']+=1
            for key in ['bytes','entries','raw_key_bytes','raw_value_bytes']:target[key]+=item[key]
            if index%50==0:print(f'Inspected {index}/{len(rows)} SST metadata records',flush=True)
    report={'backup_id':files['backup_id'],'measurement':'unverified advisory SST metadata ranges; not a restore qualification','column_families':dict(by_cf),'sst_files':result}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report['column_families'],indent=2))

if __name__=='__main__':main()
