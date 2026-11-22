# Compact local mining design

Lite retains current consensus state and recent block bodies/receipts on the
phone. It validates new blocks and performs local block production. It does not
present a remote RPC dashboard as a miner. Android identity, private keys,
sessions, notifications, chain storage and listening ports are isolated from the
standard application.

The native base is `koinos/teleno@2c11229267cff2f9ee39ed8e56ab70af391d85ce`.
The public Foundation checkpoint measured 30,924,289,067 bytes of block SSTs and
54,385,168 bytes of state SSTs. Those SST measurements include older versions;
they are not live-state size guarantees. The exported live compact database,
recent history and genesis total about 61 MB. Its exact manifest is recorded in
`evidence/lite-0.2.0/compact-snapshot-manifest.json`.

## Policy and pruning

`block_meta/lite.retained_from` and `block_meta/lite.retention_blocks` explicitly
identify a Lite database. They are validated and reloaded on startup. Ordinary
full archives have neither key and are never automatically converted. Enabling
Lite on a populated full archive is rejected; export to a separate database.

Every new block and its ordered height index are written atomically. After
acceptance, the storage owner supplies the already committed `chain_metadata`
revision. The retention floor is that durable height minus the configured window
(4,096 for the bundled checkpoint). Each pruning transaction advances at most
128 heights, deleting old indexed block records and updating the floor in one
synchronous cross-CF WriteBatch. A malformed record/index or iterator failure
aborts before any deletion commits. Consensus-state CFs are never pruned by this
policy. Recent forks remain available; forks below durable consensus finality
are outside the supported recovery horizon.

Ancestor records contain positional power-of-two skip pointers. Slots below the
floor are preserved as empty values; supported reads at or above the floor never
select those slots. Queries for removed historical heights return unavailable
history. New blocks can still build their recent ancestor navigation, including
at powers of two. Native tests exercise this through height 512 and real native
consensus comparison exercises a boundary at height 128 after pruning.

The implementation preserves the public C++ object layouts and virtual ABI for
relinking the published Android materials. The storage owner registers its
existing durable metadata handle and unregisters before closing it. No separate
configuration flag can accidentally disable a restored database's Lite policy.

## Export and restore

The offline exporter validates source hashes, genesis-derived chain ID, durable
state/header/ID, recent block header hashes, parent continuity and receipt roots.
It copies all current consensus state byte for byte, verifies complete key/value
hashes, drops optional history indexes and reconstructs recent skip pointers.
The exported archived head is the durable state root. An immutable source view
prevents tool metadata writes from touching the checkpoint. An incomplete export
is removed and never published as complete.

The signed experimental APK includes its checkpoint and pins the manifest hash.
Restore copies into a new inactive storage generation, checks every file, writes
an explicit app-side Lite marker, then activates through the existing journaled
storage switch. Prior chain data is retained until the new observer advances and
the user explicitly chooses cleanup. Once activation journaling begins, recovery
owns the stage. Startup requires a restored Lite marker and cannot silently sync
an unbounded full archive from genesis.

The compact restore is local and needs no additional network or Wi-Fi. Ongoing
peer traffic still requires mobile/metered consent. The current bundled snapshot
is Mainnet-only. Publishing a separate downloadable rolling compact snapshot
channel remains future work; no full snapshot is downloaded as a fallback.

## Validation boundary

See `evidence/lite-0.2.0/VALIDATION.md` for exact results. The ARM64 engine restored
Mainnet state and executed a KOIN contract read. An isolated private federated
chain matched reference roots before/after pruning, compact restore, catch-up,
production and restart. This establishes an experimental native test build.

Physical Android execution, Keystore isolation/co-installation, live P2P peer
acquisition/catch-up, Vault passkey/deep-link approvals, sustained thermal and
background behavior, long-run storage growth and Mainnet PoB production remain
required for production qualification. The Android package explicitly retains
`productionQualified=false`; experimental production is separately enabled.
