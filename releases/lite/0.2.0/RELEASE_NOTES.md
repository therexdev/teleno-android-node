# Teleno Node Lite 0.2.0 — experimental phone test

Install the signed `Teleno-Node-Lite-0.2.0.apk` alongside the standard Teleno app.
If upgrading Lite 0.1.0, install over it; do not uninstall or clear its data.
Android 9+, ARM64. Package `com.koinos.teleno.lite`, build 2.

The APK is 71.31 MB and includes a 60.59 MB compact Mainnet checkpoint. Restore
uses no extra data or Wi-Fi. Keep at least 2 GiB free after restoration. State
storage grows; this is not a permanent 61 MB storage limit.

Connect KOIN Vault → use existing VHP or review burn/allowance → restore the
compact checkpoint → Start node. Device signing-key registration remains an
explicit Vault approval when needed. Home and Setup share the complete startup
flow. Existing valid connections are reused; Vault session expiration can still
require reconnecting for a new wallet transaction. Burning/renewing VHP permission
can occur while mining. Stop node cancels the run and automatic recovery.
Charging is optional; background/battery settings and Android thermal/storage
limits still apply. The standard app's data and binary are unchanged.

Native ARM64 tests matched a reference node through 152 private-chain blocks,
including pruning, compact restore/catch-up, two actually produced federated
blocks and a durable restart. Mainnet compact restore and a local KOIN contract
read also passed. Wallet/storage/runner fixtures, 5 exporter tests and 14 UI tests
passed. The existing certificate and 16 KiB packaging checks were verified.

This is an experimental test build. Physical Android operation/co-installation,
live P2P catch-up, real Vault approvals and Mainnet PoB production are still
outstanding. The isolated federated production test does not demonstrate Mainnet
PoB mining. No live wallet transaction was sent during development.
