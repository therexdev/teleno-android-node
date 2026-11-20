# Teleno Node 0.3.9 — recovery and wallet burns

Android package `com.koinos.teleno`, build 17. Same signing certificate and unchanged
native engine as 0.3.8. Install over the existing application to retain chain data,
wallet pairing and device signing key.

## Mining reliability

0.3.8 stopped production after three failed local RPC reads, or a temporary
readiness/device check, and explicitly disabled automatic restarts. Silent Mode
kept the display awake but did not change that behavior. These are confirmed code
paths; the exact cause on a particular device still requires its diagnostics.

- A disconnected unmetered network keeps the native process open while it reconnects.
- Sustained local RPC failure (3 minutes), stalled production chain checks (5 minutes),
  stalled native eligibility counters (5 minutes), a stuck observer (10 minutes),
  and native process exit trigger clean recovery with retries capped at 60 seconds.
- Recovery starts an observer, verifies current chain progress and peers, then
  rechecks the reviewed account, signing key registration, VHP and required
  production allowance before resuming the same producer.
- A user-started run is saved for Android sticky-service recovery, device reboot
  and package replacement. **Stop node cancels recovery immediately.**
- CPU and Wi-Fi locks support continuous operation. Setup includes a shortcut
  to Android's battery-optimization exemption and reports its current status.
- Setup and the copied Production health report retain the last interruption,
  native exit code and available Android process-exit reason. The production
  reward run window survives automatic restarts.
- A changed network, producer account or signing key cancels recovery. No
  wallet transaction is ever part of automatic recovery.

Android force-stop, device shutdown, manufacturer battery policies, excessive
heat, insufficient memory or unavailable storage can still interrupt operation.
Resource issues wait for recovery; they are not bypassed. Absolute uptime and
reward frequency are not guaranteed. Silent Mode remains optional.

### Update and run

1. Stop the current node and install this APK over 0.3.8. Do not uninstall or clear data.
2. Open Setup → **Allow unrestricted background running**. Allow the Android
   exemption; on devices with additional battery controls, allow background activity.
3. Keep power connected, storage available and the device cool on unmetered Wi-Fi.
4. Start the observer, wait until up to date, then review and start production.
   That review now explicitly includes automatic recovery until you press Stop.
5. Use Silent Mode if desired. If mining interrupts again, copy the Production
   health report; it includes the saved reason.

## Burn KOIN and approve VHP using the separate wallet

In Mainnet Setup step 3, **Burn KOIN → VHP** prepares a transaction for KOIN Vault.
Stop production, start an up-to-date observer and connect the selected producer
wallet before reviewing a burn.

1. Enter an exact KOIN amount, with up to eight decimal places. The maximum
   respects the balance and current mana, reserving 1 KOIN of mana like the desktop plugin.
2. Choose whether to approve the resulting VHP balance for production.
3. Review the wallet, official contracts, exact burn, same-wallet VHP receipt,
   and replacement PoB allowance.
4. Approve in KOIN Vault. Burning KOIN is irreversible.
5. Return and **Check saved KOIN burn**. Confirmation requires the exact
   transaction in irreversible account history and matching KOIN/VHP changes.
6. In step 5, check VHP permission, then review and start production.

The combined transaction contains an exact KOIN allowance to PoB, the same-wallet
PoB burn, and optionally an exact VHP allowance equal to existing VHP plus the burn.
The VHP allowance replaces the remaining allowance; it does not add to it.
Standalone VHP approval/revocation remains available in step 5. There is no
unlimited approval, automatic burn, funds-key import, or automatic resend.
Pending/uncertain requests remain saved across restarts. Closing an expired
request does not cancel an already submitted transaction; check the wallet first.

## Validation boundary

Validation details are in `validation.json`. Host and simulated Android tests do
not establish overnight reliability on physical hardware. No live wallet transaction
was submitted during development. Retroid overnight and real-wallet approval
validation remain required before describing this update as device-qualified.
