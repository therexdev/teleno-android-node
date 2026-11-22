# Lite 0.2.2 sync status and recovery

The sanitized diagnostic case has a four-second-old head, recent local RPC,
zero reported peers and an open native production gate after a recent restart.
The first eligibility counter sample alone does not establish whether mining is
advancing. It requires another sample. No tester account or raw device report is
included in this evidence.

## Findings and change

The dashboard previously mapped every readiness failure to “Catching up to the
chain.” Native numeric peer metrics are sampled every 60 seconds, whereas native
gossip-gate events can arrive between those reports. An older startup zero could
therefore hide newer connection evidence. Neither finding establishes the cause
of a persistent real peer disconnection or the tester's prior local RPC outage.

The new readiness states separate chain progress from peer and RPC checks. An
exact, trusted info-level gate-open event can supersede an older count for up to
90 seconds, subject to all chain checks. A newer numeric zero, closed/disabled
gate, stale event or malformed metric cannot create this pending-count hint.
The numeric count is retained rather than replaced with an invented number.

Recovery now distinguishes a current chain from other readiness failures. The
RPC watchdog accepts only increasing native head/eligibility counters measured
on the monotonic clock; unchanged, reset, stale or future progress cannot extend
its deadline indefinitely. Chain identity, fresh RPC/head, observed advancement,
valid finality, signing-key/VHP checks and the native production gate remain.
The existing five-minute eligibility-stall recovery is unchanged.

## Checks performed for this version

- Thirteen selected Java suites: 612 assertions, including 29 new sync-state,
  real native-header parsing, peer-event ordering/expiry and recovery assertions.
  The existing suites include wallet wire fixtures, bounded VHP permissions,
  burn review, recovery, mobile consent, data preservation and real TCP handoff.
  This is not a rerun of host JNI/supervisor or native-engine qualification.
- Robolectric API 33 native graphics: four sync-status dashboard tests and seven
  enabled-runtime onboarding/dashboard tests.
- Full built-APK asset restore: two integration tests verify every restored file,
  activation, cancellation, preservation of existing data/wallet sentinels and
  successful retry. Android ownership/directory-fsync entry points are shadowed;
  this does not simulate Android process lifetime or hardware storage.
- The signed APK retains Lite's separate package and update certificate, version
  code 4, 16 KiB library alignment and the exact previous native runtime/snapshot.
  See `apk-verification.json` and `apk-signature.txt` for the byte identities.

The native engine and checkpoint are byte-identical to 0.2.1/0.2.0. Previous
isolated native evidence remains in those versioned directories; those native
experiments were not rerun for this Java change. Physical-phone installation,
endurance, live peer reconnection, real Vault approvals and Mainnet PoB block
production are not validated by this test run. No live wallet transaction was
sent and no test enabled a real Mainnet producer.
