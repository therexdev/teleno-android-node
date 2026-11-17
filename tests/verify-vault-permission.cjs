// Run with NODE_PATH pointing to koilib, argv[2] a checked-out koin-vault repo,
// and argv[3] the JSON vectors emitted by PermissionTests. Does not call a network.
const { Contract } = require('koilib');
const { isDeepStrictEqual } = require('node:util');
const fs = require('node:fs'), path = require('node:path');
const vault = path.resolve(process.argv[2]);
const { reviewProducer, CONTRACTS } = require(path.join(vault, 'tools/dapp-producer.js'));
const fixtures = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
const abi = require(path.join(vault, 'abi/token-abi.json'));
delete abi.koilib_types.nested.koinos.nested.btype;
delete abi.koilib_types.nested.koinos.nested._btype;
(async () => {
  const c = new Contract({ id: CONTRACTS.vhp, abi }), rows = [];
  for (const f of fixtures) {
    const r = await c.functions.approve({ owner: f.owner, spender: CONTRACTS.pob, value: f.amount }, { onlyOperation: true });
    if (!isDeepStrictEqual(r.operation, f.operation)) throw Error('Canonical mismatch: ' + f.amount);
    const review = await reviewProducer([f.operation], f.owner, 'mainnet');
    rows.push({ amount: f.amount, title: review.title, canonical: true });
  }
  let rejected = 0;
  const f = fixtures[2];
  for (const mutate of [
    op => { op.call_contract.contract_id = f.owner; },
    op => { op.call_contract.entry_point = 0; },
    op => { op.call_contract.args = Buffer.concat([Buffer.from(op.call_contract.args, 'base64url'), Buffer.from([0])]).toString('base64url'); }
  ]) {
    const op = JSON.parse(JSON.stringify(f.operation)); mutate(op);
    let failed = false; try { await reviewProducer([op], f.owner, 'mainnet'); } catch { failed = true; }
    if (!failed) throw Error('Unexpected acceptance of malformed operation'); rejected++;
  }
  for (const [owner, network] of [[CONTRACTS.pob, 'mainnet'], [f.owner, 'harbinger']]) {
    let failed = false; try { await reviewProducer([f.operation], owner, network); } catch { failed = true; }
    if (!failed) throw Error('Unexpected acceptance of changed context'); rejected++;
  }
  const unlimited = await c.functions.approve({ owner: f.owner, spender: CONTRACTS.pob, value: '18446744073709551615' }, { onlyOperation: true });
  let failed = false; try { await reviewProducer([unlimited.operation], f.owner, 'mainnet'); } catch { failed = true; }
  if (!failed) throw Error('Unexpected unlimited approval'); rejected++;
  console.log(JSON.stringify({ koilib: require('koilib/package.json').version, vectors: rows, rejected_mutations: rejected, live_signing: false }, null, 2));
})().catch(e => { console.error(e); process.exit(1); });
