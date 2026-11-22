# Lite 0.2.1 restore fix

The complete Android restore reproduced the 0.2.0 failure before the change:
opening the first `db/000009.sst` failed because the database directory did not
exist. The `plain()` helper validates a path's type; it does not create folders.

0.2.1 creates and validates the database directory before copying and checks
output file types. The service now describes a local restore/retry and no longer
claims a resumable network download or retained partial files.

The restore integration uses actual assets from the built APK, real host files
and real exclusive file locks under Robolectric API 33. Android ownership entry
points and directory fsync are shadowed. Tests verify every database file's size
and SHA-256, storage activation and Lite marker. Cancellation preserves the
previous database, wallet/signing sentinels and selection; inactive partial data
is removed and a fresh retry succeeds. The prior database remains backed up.

The actual Java-restored data and observer-first markers are then copied into
an isolated QEMU Android ARM64 engine test. Native startup preflight, Mainnet
chain ID, height/root, the observer recovery hold and a local KOIN contract read
are checked. P2P and block production are disabled during that handoff check.
The enabled-runtime UI suite also passes.

The native engine and compact checkpoint are byte-identical to 0.2.0; its isolated
152-block reference/production results remain applicable. See the 0.2.0 validation
record for that separate test. No real wallet transaction is sent. Physical phone,
live P2P/Vault and Mainnet PoB validation remain outstanding.
