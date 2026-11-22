# Lite 0.2.0 validation

The standard app source and APK are unchanged. Both app IDs/ports/data aliases
are distinct; physical co-installation has not been exercised.

- Mainnet export: 925,260 live chain-state entries copied byte for byte; five
  durable metadata entries unchanged. 4,097 retained block records. See the
  compact manifest for exact hashes, size and checkpoint identity.
- Actual ARM64 engine: QEMU AArch64 with AOSP Android libc, stderr-only log shim,
  unused libandroid shim. Restored the Mainnet checkpoint with matching chain ID,
  revision and state root. Resolved KOIN and executed its decimals contract read.
- Isolated native production: reference and Lite state roots matched for 150
  submitted signed blocks. Lite pruned through floor 26 at durable revision 90.
  Compact restore and catch-up passed. The native federated producer created
  blocks 151 and 152; reference validation and durable restart matched.
- Native component checks: rolling retention, ancestor boundaries, restart,
  recent forks, state preservation, committed-finality callback and atomic
  rejection of corrupt pruning indexes. This helper host build used RocksDB
  9.10/protobuf 3.21; end-to-end ARM64 used original 8.8.1/owned 3.17 runtimes.
- Snapshot tests: five real-RocksDB tests, including wrong network, corruption,
  missing SST, unverified WAL, preserved source and bounded history.
- UI: seven foundation-policy tests and seven enabled-runtime tests, including
  all four setup anchors, local restore copy, pinned manifest/path checks and
  burn review while the running producer remains active.
- Existing host fixtures: wallet protocol, exact burn/allowance, recovery,
  private-stdin key handling, native ownership, TCP handoff and storage safety.
  No live wallet approval or transaction was submitted.

Not established: real Android installation/Keystore/co-installation, background
endurance, live P2P catch-up, KOIN Vault passkey/deep-link behavior on a phone,
Mainnet PoB production or a permanent storage cap. This is an experimental phone
test build, not a production-qualified release. The private federated test does
not demonstrate Mainnet PoB consensus.
