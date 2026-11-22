# Teleno Node Lite 0.2.2

An up-to-date chain no longer changes to “catching up” solely because a peer
report is zero or local status is delayed. The dashboard identifies peer
reconnection, missing status, block-time checks and actual recent-block syncing.

A newer native connection event now takes precedence over an older startup peer
count until a fresh numeric report arrives. The screen shows “Live” while that
count is pending; the health report includes the last numeric count and its age.
Newer zero counts and expired connection evidence still block readiness.

The current-chain watchdog no longer treats peer telemetry alone as chain
failure. Local RPC recovery gives a progressing engine time to respond, using
increases in native block height or eligibility counters. Repeated unchanged
counters do not postpone recovery indefinitely. Actual stalled production still
uses the existing recovery path.

Install over your existing Lite app. Keep its current chain, wallet connection
and signing keys; do not uninstall, clear data or restore a current checkpoint
again. If needed, use Setup → Allow unrestricted background running. The standard
Teleno app, native engine and bundled checkpoint are unchanged.

The Java regression suites, dashboard tests and full bundled-restore tests pass.
These checks do not demonstrate physical-phone endurance, live peer stability or
Mainnet PoB block production. Experimental production remains explicitly enabled
and `productionQualified` remains false. No live wallet transaction was submitted.
