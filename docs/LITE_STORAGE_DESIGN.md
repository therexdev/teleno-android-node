# Teleno Node Lite: compact local mining

The goal is an independently installed Android app that validates and produces
Koinos blocks on the phone with KOIN Vault approvals and substantially less
retained storage. The miner remains local. This is not remote mining or a public
RPC dashboard presented as a miner.

## Foundation delivered in 0.1.0

The application ID is `com.koinos.teleno.lite`, distinct from standard
`com.koinos.teleno`. It uses a distinct launcher icon, Vault app name, encrypted
key/session aliases, chain directory name and RPC/P2P ports 28085/28888. Android
assigns app-private storage independently. No shared UID, cross-app data provider,
standard-app migration or shared chain directory is configured.

The Java/JNI namespace remains `com.koinos.teleno` to reuse the native bridge
without modifying its ABI. Fully qualified Android components and AAPT's custom
resource namespace make this independent of the Android installation ID.

This preview supports the wallet UI, explicit Vault pairing and reviewed wallet
requests. It bundles the existing native bridge and supervisor, but no standard
node engine. Mining, observer startup, restore/download, chain maintenance and
recovery are blocked at the service boundary. The UI states that compact mining
is unavailable. A burn review explicitly says a burn does not enable Lite mining.
The build rejects both a supplied full engine and production enablement.

Standard APKs, source and the existing working branch remain unchanged. Both
packages can be installed together; physical-device co-installation has not been
tested in this environment. A shared producer must never be run twice.

## Engine work required

The inherited engine source pin is
`koinos/teleno@2c11229267cff2f9ee39ed8e56ab70af391d85ce`.
`RocksDBManager::column_family_stats()` reports per-family SST and live data
estimates. Measure `chain_state`, `chain_metadata`, `blocks`, `block_meta`,
`transaction_index` and `account_history` on a representative Mainnet database.
SST totals, temporary compaction space and steady-state growth must all be
included. The full snapshot's aggregate size is not a compact-state size estimate.

The block store embeds ancestor skip-list metadata in block records. Adding a
block consults historical ancestors and rejects an insertion if required records
are missing. Deleting old records wholesale would therefore break normal block
storage. A pruning design must preserve or replace that metadata, not just delete
old files or disable the block store.

Retain current consensus state, recent block bodies/receipts, required ancestor
metadata and all material needed by the durable state/indexer and supported fork
recovery. Derive the retention boundary from consensus finality and actual read
dependencies. Disable optional history indexing for new Lite databases; removing
previously populated indexes is a separate checked migration.

Create a versioned Lite snapshot format and dedicated publication channel.
Do not download the standard snapshot and prune it afterward on a small phone.
Restore must check network identity, snapshot identity/checksums, state consistency,
storage requirements and observer progress before any block production. Clarify
the trust/checkpoint assumptions of snapshot bootstrapping.

P2P must honestly handle unavailable old block ranges and still discover/follow
the chain. Missing old history cannot be mistaken for a corrupt current state.
The node must remain able to validate new blocks and build/sign correct blocks.
Wallet/reward history can use labelled public RPC reads; production validation
must continue to use verified local state.

## Acceptance before enabling compact mining

1. Measure database size and peak restore/compaction space on a fresh compact
   bootstrap, including continued growth. Do not promise a fixed GB target yet.
2. Compare state roots and block acceptance with the reference node at the same
   chain checkpoints, before and after pruning.
3. Test block insertion across ancestor skip-list boundaries after old payloads
   are pruned, fork/reorg handling, and a restart during interrupted pruning.
4. Test restore, observer catch-up and durable restart without the removed history.
5. Exercise block production on an isolated test network with matching registered
   device key and explicit wallet approvals; never auto-burn or auto-resend.
6. Test both Android packages installed together, isolated private/SD data,
   independent sessions, notifications and Stop controls. Use different producers
   for concurrent mining tests.

Only after those checks should a Lite runtime/snapshot be enabled. Engine metadata
alone is not proof of qualification. The standard app must not be changed to make
the Lite prototype pass.
