# Teleno Node 0.3.8 — Silent Mode

## Install

Stop the node and wait for Stopped. Install Teleno-Node-0.3.8.apk over the existing
app, then reopen and start the node as usual. Do not uninstall. The package is
com.koinos.teleno, version code 16, with the same publisher certificate as 0.3.7.
Android 9+ and ARM64 are required.

## Use Silent Mode

On Home, tap Silent Mode. The screen becomes pure black with a dim outlined box
that drifts continuously and slowly changes color. It shows the device's local
time, latest finalized KOIN reward and its date/time, Rewards this run (KOIN total
and count), and a brief node status. Tap anywhere to return to Home.

While Silent Mode is visible, the app keeps the screen awake, hides the system
bars and caps its own window brightness at 5% (retaining any already lower
brightness). It restores the original window settings on exit or backgrounding.
Rotation retains the mode; returning from another app resumes it. It does not
change Android sound settings. It does not start, stop, or restart the node.

Use this instead of pressing the power button when leaving the device running.
Manually locking the device or leaving the app puts it back under Android's
background restrictions. A screen-on mode cannot guarantee network availability
or override a device shutdown. No physical Retroid or overnight test was available.

## Reward meaning

Latest reward comes from the selected wallet/network's irreversible chain history.
Rewards this run sums all cached finalized wallet rewards whose block timestamps
fall between this producer run's start and stop; the current run has no stop bound.
Starting production resets the run. Entering or leaving Silent Mode, rotating,
and backgrounding do not reset it. Stopped-run totals can still gain delayed
confirmations for blocks timestamped during that run. Starting an observer clears
the production-run view. Exact integer arithmetic preserves all eight decimals.

Rewards are wallet-based; another producer using the same wallet can contribute.
These are confirmed rewards, not local block attempts or unconfirmed earnings.
History is refreshed approximately once a minute while the app is in the foreground.
Saved records are retained if a history read fails. Missing older history must be
loaded from History; the total reflects the confirmed records currently cached.

## Verification

20 Robolectric UI/SQLite tests passed. Native engine and packaged asset bytes
match 0.3.7. APK signatures, certificate continuity, version identity, and 16 KiB
ZIP alignment were checked. Screenshot fixtures use synthetic accounts and amounts.
No wallet transaction was submitted. Device production qualification is unchanged.

Android references:
- https://developer.android.com/develop/background-work/background-tasks/awake/screen-on
- https://developer.android.com/develop/ui/views/layout/immersive
- https://developer.android.com/training/monitoring-device-state/doze-standby
