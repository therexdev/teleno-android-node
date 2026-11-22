#!/usr/bin/env python3
"""Offline, non-destructive compact checkpoint exporter (not a phone downloader).

Requires rocksdict==0.3.29 and protobuf>=4.25. Source must be an immutable
checkpoint, never a running node's database. Publication remains a separate step.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import tempfile

from google.protobuf import descriptor_pb2, descriptor_pool, message_factory, json_format
from rocksdict import Rdict, Options, AccessType, ReadOptions, BlockBasedOptions, DBCompressionType

CF_NAMES = {'default', 'blocks', 'block_meta', 'contract_meta', 'transaction_index',
            'account_history', 'chain_state', 'chain_metadata', 'storage_metadata'}
COPY_CFS = ('chain_state', 'chain_metadata')
FLOOR_KEY = b'lite.retained_from'
HEIGHT_PREFIX = b'lite.height/'
MAINNET = '0x1220592bf18654fd07fdf5d500cde3e8402ecf7f81fa5dde8f14527b08bba8805f48'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def proto_types():
    data = base64.b64decode((Path(__file__).parent / 'lite_proto/koinos.desc.b64').read_bytes(), validate=False)
    pool = descriptor_pool.DescriptorPool()
    for file in descriptor_pb2.FileDescriptorSet.FromString(data).file:
        pool.Add(file)
    return {name: message_factory.GetMessageClass(pool.FindMessageTypeByName(qualified))
            for name, qualified in {
                'record': 'koinos.block_store.block_record',
                'header': 'koinos.protocol.block_header',
                'topology': 'koinos.block_topology',
                'genesis': 'koinos.chain.genesis_data',
                'key': 'koinos.chain.database_key',
                'resources': 'koinos.chain.resource_limit_data',
                'max_resources': 'koinos.chain.max_account_resources',
                'registry': 'koinos.chain.compute_bandwidth_registry',
            }.items()}


PROTO = proto_types()


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def multihash(data):
    return b'\x12\x20' + hashlib.sha256(data).digest()


def parse_id(text):
    require(isinstance(text, str) and len(text) == 70 and text.startswith('0x1220'), 'invalid chain identity')
    return bytes.fromhex(text[2:])


def options(create=False, partial=False):
    opt = Options(raw_mode=True)
    opt.create_if_missing(create)
    opt.create_missing_column_families(create)
    opt.set_max_open_files(64)
    opt.set_max_background_jobs(2)
    opt.set_write_buffer_size(8 * 1024 * 1024)
    # A selective offline source may omit old blocks, but every state read and
    # iteration below verifies checksums and explicitly checks iterator status.
    opt.set_paranoid_checks(not partial)
    table = BlockBasedOptions()
    table.set_format_version(5)  # readable by the pinned RocksDB 8.8.1 engine
    opt.set_block_based_table_factory(table)
    opt.set_compression_type(DBCompressionType.zstd())
    return opt


def checked_items(cf):
    reads = ReadOptions()
    reads.set_verify_checksums(True)
    it = cf.iter(reads)
    it.seek_to_first()
    while it.valid():
        yield it.key(), it.value()
        it.next()
    it.status()  # missing SST / corrupt state must not look like end-of-data


def cf_hash(cf):
    h = hashlib.sha256()
    count = 0
    for key, value in checked_items(cf):
        h.update(struct.pack('>QQ', len(key), len(value)))
        h.update(key)
        h.update(value)
        count += 1
    return {'entries': count, 'sha256_length_prefixed_kv': h.hexdigest()}


def verified_inputs(source, catalog):
    require(not source.is_symlink() and source.is_dir(), 'source must be a checkpoint directory')
    rows = json.loads(catalog.read_text())['files']
    seen, used = set(), []
    for row in rows:
        rel = Path(row['path'])
        require(not rel.is_absolute() and '..' not in rel.parts and row['path'] not in seen, 'unsafe catalog path')
        seen.add(row['path'])
        p = source / rel
        if not p.exists():
            require(rel.suffix == '.sst', 'checkpoint runtime/metadata file missing')
            continue
        require(p.is_file() and not p.is_symlink(), 'checkpoint symlink or special file')
        require(p.stat().st_size == row['size_bytes'] and digest(p) == row['sha256'], 'source checksum mismatch: ' + row['path'])
        used.append(row)
    require('genesis_data.json' in seen or any('genesis' in r['path'] for r in used), 'genesis missing from catalog')
    # Reject unverified DB files, especially an extra WAL that could change state.
    actual = {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}
    require(actual <= {r['path'] for r in used}, 'unverified files in checkpoint')
    return used


def identity(db, source_identity, genesis_path):
    state = db.get_column_family('chain_state')
    meta = db.get_column_family('chain_metadata')
    storage = db.get_column_family('storage_metadata')
    try:
        revision = int.from_bytes(meta[b'revision'], 'big')
        require(len(meta[b'revision']) == 8 and revision > 0, 'invalid durable revision')
        header = PROTO['header'].FromString(meta[b'block_header'])
        state_id, root = meta[b'id'], meta[b'merkle_root']
        key = PROTO['key']()
        key.space.system = True
        key.key = multihash(b'object_key::chain_id')
        chain_id = state[key.SerializeToString()]
        genesis = json_format.Parse(genesis_path.read_text(), PROTO['genesis']())
        require(chain_id == multihash(genesis.SerializeToString()), 'genesis does not match state chain ID')
        require(chain_id == parse_id(source_identity['chain_id']), 'network does not match source manifest')
        require(storage[b'layout.version'] == b'1' and storage[b'layout.chain_storage'] == b'unified', 'unsupported storage layout')
        require(header.height == revision and multihash(header.SerializeToString()) == state_id, 'state header/ID mismatch')
        require(revision == source_identity['state_revision'] and state_id == parse_id(source_identity['state_id'])
                and root == parse_id(source_identity['state_merkle_root']), 'durable state does not match manifest')
        return revision, state_id, root, header, chain_id
    finally:
        state.close(); meta.close(); storage.close()


def recent_records(db, revision, state_id, root, retention):
    blocks = db.get_column_family('blocks')
    floor = max(1, revision - retention)
    records, key, newer = [], state_id, None
    try:
        for height in range(revision, floor - 1, -1):
            r = PROTO['record'].FromString(blocks[key])
            require(r.block_id == key and r.block.id == key and r.block_height == height
                    and r.block.header.height == height and multihash(r.block.header.SerializeToString()) == key,
                    'retained block identity mismatch')
            require(r.HasField('receipt') and r.receipt.id == key and r.receipt.height == height,
                    'retained receipt identity mismatch')
            require(r.receipt.state_merkle_root == (root if newer is None else newer.block.header.previous_state_merkle_root),
                    'retained receipt root does not match checkpoint/header')
            records.append(r)
            newer, key = r, r.block.header.previous
    finally:
        blocks.close()
    by_height = {r.block_height: r.block_id for r in records}
    for r in records:
        del r.previous_block_ids[:]
        if r.block_height == 1:
            r.previous_block_ids.append(r.block.header.previous)
        else:
            step = 1
            while True:
                ancestor = r.block_height - step
                r.previous_block_ids.append(by_height[ancestor] if ancestor >= floor else b'')
                if r.block_height % (2 * step):
                    break
                step *= 2
    return floor, records


def export(source, catalog, manifest_path, output, retention, genesis_name):
    require(64 <= retention <= 1_000_000, 'retention must be 64–1000000 blocks')
    require(not output.exists(), 'output exists; export to a new directory')
    source = source.resolve()
    require(source not in output.resolve().parents, 'output cannot be inside source checkpoint')
    require(not Path(genesis_name).is_absolute() and '..' not in Path(genesis_name).parts, 'unsafe genesis path')
    source_manifest = json.loads(manifest_path.read_text())
    source_identity = source_manifest['source']
    require(source_identity.get('identity_status') == 'checkpoint_verified', 'requires a checkpoint-verified source')
    used = verified_inputs(source, catalog)
    require(genesis_name in {r['path'] for r in used} and (source / genesis_name).stat().st_size <= 32 * 1024 * 1024,
            'genesis must be a verified checkpoint file, at most 32 MiB')
    # rocksdict writes its own config even on a read-only open. Give it a private
    # view, hard-linking immutable SSTs and copying small metadata, so it never
    # opens the actual source directory.
    view = tempfile.TemporaryDirectory(prefix='lite-source-view-', dir=output.parent)
    view_path = Path(view.name)
    for row in used:
        src, dst = source / row['path'], view_path / row['path']
        dst.parent.mkdir(parents=True, exist_ok=True)
        if src.suffix == '.sst':
            os.link(src, dst)
        else:
            shutil.copyfile(src, dst)
    opt = options(partial=True)
    require(set(Rdict.list_cf(str(view_path / 'db'), opt)) == CF_NAMES, 'unexpected column-family layout')
    original = Rdict(str(view_path / 'db'), opt, access_type=AccessType.read_only())
    work = Path(tempfile.mkdtemp(prefix=output.name + '.partial-', dir=output.parent))
    target = None
    try:
        revision, state_id, root, header, chain_id = identity(original, source_identity, source / genesis_name)
        floor, records = recent_records(original, revision, state_id, root, retention)
        target = Rdict(str(work / 'db'), options(True), {name: options(True) for name in CF_NAMES - {'default'}})
        hashes = {}
        for name in COPY_CFS:
            src, dst = original.get_column_family(name), target.get_column_family(name)
            try:
                for key, value in checked_items(src):
                    dst[key] = value
                hashes[name] = cf_hash(src)
                require(cf_hash(dst) == hashes[name], 'state copy verification failed: ' + name)
            finally:
                src.close(); dst.close()
        blocks, meta, storage = [target.get_column_family(n) for n in ('blocks', 'block_meta', 'storage_metadata')]
        try:
            for r in reversed(records):
                blocks[r.block_id] = r.SerializeToString()
                meta[HEIGHT_PREFIX + r.block_height.to_bytes(8, 'big') + r.block_id] = r.block_id
            topo = PROTO['topology'](id=state_id, height=revision, previous=header.previous)
            meta[b'\x01'] = topo.SerializeToString()
            meta[FLOOR_KEY] = str(floor).encode()
            meta[b'lite.retention_blocks'] = str(retention).encode()
            storage[b'layout.version'] = b'1'
            storage[b'layout.chain_storage'] = b'unified'
            storage[b'lite.snapshot.version'] = b'1'
            storage[b'lite.source.backup_id'] = source_manifest['backup_id'].encode()
        finally:
            blocks.close(); meta.close(); storage.close()
        target.flush()
        target.close(); target = None
        shutil.copyfile(source / genesis_name, work / 'genesis_data.json')
        # No source node config, producer identity, peers, logs or wallet material.
        files = [{'path': p.relative_to(work).as_posix(), 'size_bytes': p.stat().st_size, 'sha256': digest(p)}
                 for p in sorted(work.rglob('*')) if p.is_file() and p.name not in ('LOCK', 'LOG') and not p.name.startswith('LOG.old')]
        for p in work.rglob('*'):
            if p.is_file() and (p.name in ('LOCK', 'LOG') or p.name.startswith('LOG.old')):
                p.unlink()
        result = {'version': 1, 'format': 'teleno-lite-state-snapshot', 'engine_profile': 'lite-v1',
                  'source_backup_id': source_manifest['backup_id'], 'source_manifest_sha256': digest(manifest_path),
                  'chain_id': '0x' + chain_id.hex(), 'state_revision': revision,
                  'state_id': '0x' + state_id.hex(), 'state_merkle_root': '0x' + root.hex(),
                  'retained_from': floor, 'retained_blocks': len(records), 'retention_target': retention,
                  'state_copy': hashes, 'source_files_verified': used, 'files': files,
                  'download_bytes': sum(r['size_bytes'] for r in files),
                  'trust': 'Trusted checkpoint bootstrap; not a replay or proof of historical consensus.',
                  'qualification': 'export-verified; native restore/catch-up/production tests required',
                  'producer_enabled': False}
        (work / 'lite-manifest.json').write_text(json.dumps(result, indent=2) + '\n')
        # Recheck the source after all reads, before publishing a completed result.
        require(verified_inputs(source, catalog) == used, 'source changed during export')
        os.rename(work, output)
        print(json.dumps({k: result[k] for k in ('format', 'state_revision', 'retained_from', 'retained_blocks', 'download_bytes', 'state_copy')}, indent=2))
    except BaseException:
        if target is not None:
            target.close()
        shutil.rmtree(work)
        raise
    finally:
        original.close()
        view.cleanup()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--retained-blocks', type=int, default=4096)
    parser.add_argument('--genesis', default='chain/genesis_data.json')
    args = parser.parse_args()
    export(args.source, args.files, args.manifest, args.output, args.retained_blocks, args.genesis)


if __name__ == '__main__':
    main()
