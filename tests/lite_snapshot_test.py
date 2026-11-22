#!/usr/bin/env python3
"""Real RocksDB export checks, including corruption and incomplete state."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('lite_snapshot', ROOT / 'tools/lite_snapshot.py')
lite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(lite)
from google.protobuf import json_format
from rocksdict import Rdict, AccessType


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.src = self.root / 'source'
        self.src.mkdir()
        self.out = self.root / 'compact'
        self.catalog = self.root / 'files.json'
        self.manifest = self.root / 'manifest.json'
        genesis = lite.PROTO['genesis']()
        entry = genesis.entries.add()
        entry.space.system = True
        entry.key = b'fixture'
        entry.value = b'test genesis, not a public chain'
        (self.src / 'chain').mkdir()
        (self.src / 'chain/genesis_data.json').write_text(json_format.MessageToJson(genesis))
        chain_id = lite.multihash(genesis.SerializeToString())
        db = Rdict(str(self.src / 'db'), lite.options(True), {name: lite.options(True) for name in lite.CF_NAMES - {'default'}})
        self.state = {b'consensus-object': b'do not change this value'}
        state, meta, storage, blocks = [db.get_column_family(n) for n in ('chain_state', 'chain_metadata', 'storage_metadata', 'blocks')]
        key = lite.PROTO['key']()
        key.space.system = True
        key.key = lite.multihash(b'object_key::chain_id')
        self.state[key.SerializeToString()] = chain_id
        for key, value in self.state.items():
            state[key] = value
        previous = lite.multihash(b'genesis')
        for height in range(1, 151):
            r = lite.PROTO['record']()
            r.block.header.height = height
            r.block.header.previous = previous
            r.block.header.timestamp = height * 5000
            r.block.header.previous_state_merkle_root = lite.multihash(str(height - 1).encode())
            r.block_id = lite.multihash(r.block.header.SerializeToString())
            r.block.id = r.block_id
            r.block_height = height
            r.receipt.id = r.block_id
            r.receipt.height = height
            r.receipt.state_merkle_root = lite.multihash(str(height).encode())
            blocks[r.block_id] = r.SerializeToString()
            previous = r.block_id
        meta[b'revision'] = (150).to_bytes(8, 'big')
        meta[b'id'] = r.block_id
        meta[b'merkle_root'] = r.receipt.state_merkle_root
        meta[b'block_header'] = r.block.header.SerializeToString()
        storage[b'layout.version'] = b'1'
        storage[b'layout.chain_storage'] = b'unified'
        for cf in (state, meta, storage, blocks):
            cf.close()
        db.flush(); db.close()
        (self.src / 'db/rocksdict-config.json').unlink()
        for p in (self.src / 'db').glob('LOG*'):
            p.unlink()
        (self.src / 'db/LOCK').unlink(missing_ok=True)
        self.update_catalog()
        self.manifest.write_text(json.dumps({'backup_id': 'fixture-150', 'source': {
            'identity_status': 'checkpoint_verified', 'chain_id': '0x' + chain_id.hex(),
            'state_revision': 150, 'state_id': '0x' + r.block_id.hex(),
            'state_merkle_root': '0x' + r.receipt.state_merkle_root.hex()}}))

    def tearDown(self):
        self.tmp.cleanup()

    def update_catalog(self):
        self.catalog.write_text(json.dumps({'files': [
            {'path': p.relative_to(self.src).as_posix(), 'size_bytes': p.stat().st_size, 'sha256': lite.digest(p)}
            for p in sorted(self.src.rglob('*')) if p.is_file()]}))

    def export(self):
        with contextlib.redirect_stdout(io.StringIO()):
            lite.export(self.src, self.catalog, self.manifest, self.out, 64, 'chain/genesis_data.json')

    def test_roundtrip_state_and_bounded_history(self):
        before = {p.relative_to(self.src): lite.digest(p) for p in self.src.rglob('*') if p.is_file()}
        self.export()
        after = {p.relative_to(self.src): lite.digest(p) for p in self.src.rglob('*') if p.is_file()}
        self.assertEqual(before, after)
        manifest = json.loads((self.out / 'lite-manifest.json').read_text())
        self.assertEqual(manifest['retained_from'], 86)
        self.assertEqual(manifest['retained_blocks'], 65)
        db = Rdict(str(self.out / 'db'), lite.options(), access_type=AccessType.read_only())
        for name in ('account_history', 'transaction_index', 'contract_meta'):
            cf = db.get_column_family(name)
            self.assertEqual(list(lite.checked_items(cf)), [])
            cf.close()
        cf = db.get_column_family('chain_state')
        self.assertEqual(dict(lite.checked_items(cf)), self.state)
        cf.close()
        blocks = db.get_column_family('blocks')
        records = [lite.PROTO['record'].FromString(v) for _, v in lite.checked_items(blocks)]
        self.assertEqual(sorted(r.block_height for r in records), list(range(86, 151)))
        at128 = next(r for r in records if r.block_height == 128)
        self.assertEqual(len(at128.previous_block_ids), 8)
        self.assertEqual(at128.previous_block_ids[-1], b'')
        blocks.close(); db.close()

    def test_wrong_network_fails_without_output(self):
        data = json.loads(self.manifest.read_text())
        data['source']['chain_id'] = lite.MAINNET
        self.manifest.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, 'network'):
            self.export()
        self.assertFalse(self.out.exists())

    def test_corrupt_input_is_rejected_before_open(self):
        p = self.src / 'chain/genesis_data.json'
        p.write_bytes(p.read_bytes() + b' ')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.export()
        self.assertFalse(self.out.exists())

    def test_missing_sst_never_becomes_empty_state(self):
        for p in (self.src / 'db').glob('*.sst'):
            p.unlink()
        with self.assertRaises(Exception):
            self.export()
        self.assertFalse(self.out.exists())

    def test_unverified_wal_and_existing_output_rejected(self):
        (self.src / 'db/000001.log').write_bytes(b'not in catalog')
        with self.assertRaisesRegex(ValueError, 'unverified'):
            self.export()
        (self.src / 'db/000001.log').unlink()
        self.out.mkdir()
        with self.assertRaisesRegex(ValueError, 'output exists'):
            self.export()


if __name__ == '__main__':
    unittest.main()
