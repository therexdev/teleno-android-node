# Teleno Node

Run a Koinos node directly on an Android device. Teleno Node bundles the full
native Teleno engine with a dashboard, guided setup, KOIN Vault connection,
chain snapshot downloads and reward history. Docker, WSL and root are not required.

## Download

**Latest version: 0.3.9 (build 17), with automatic mining recovery and KOIN-to-VHP burns.**

- [Download Teleno Node 0.3.9 APK](https://raw.githubusercontent.com/therexdev/teleno-android-node/main/releases/0.3.9/Teleno-Node-0.3.9.apk)
- [Download the matching 0.3.9 source archive](https://raw.githubusercontent.com/therexdev/teleno-android-node/main/releases/0.3.9/Teleno-Node-0.3.9-source.zip)
- [SHA-256 checksums](releases/0.3.9/SHA256SUMS.txt)
- [0.3.9 release notes and setup instructions](releases/0.3.9/RELEASE_NOTES.md)

The APK and matching source are committed in this repository under
[`releases/0.3.9`](releases/0.3.9). The links above download version 0.3.9 directly.
The unchanged native engine can be rebuilt using the
[0.3.7 native materials](https://github.com/therexdev/teleno-android-node/releases/download/v0.3.7/Teleno-Node-0.3.7-native-materials.zip).

Requires **Android 9 or newer and an ARM64 device**. Chain data requires substantial
free storage; Setup shows the space required for the selected snapshot. Removable
storage is offered when Android exposes a suitable app-specific SD-card location.

To update, stop the node and install the APK over the existing app. Version 0.3.9
uses package `com.koinos.teleno`, version code **17** and the same signing
certificate as the earlier installers. Keep the existing app and its data.

## What changed in 0.3.9

- Recover a saved user-started run after temporary interruptions, native process
  exits, Android service recreation and device reboot. Stop node cancels recovery.
- Keep the process open during Wi-Fi loss; recover sustained RPC, chain and native
  eligibility-counter stalls through an observer with fresh production checks.
- Show the last interruption reason and Android background-running settings.
- Burn an exact KOIN amount to VHP in the selected KOIN Vault wallet and optionally
  approve its resulting VHP production balance in one explicitly reviewed transaction.
- Preserve pending burn requests without automatic resubmission; confirm the exact
  burn against irreversible history.
- Retain Silent Mode's moving, dim black-screen display from 0.3.8.

See the [release notes](releases/0.3.9/RELEASE_NOTES.md) for setup, recovery behavior
and the physical-device validation still needed.

## Use the app

1. In **Setup**, choose the network and storage location.
2. Download and restore a public chain snapshot, or start an observer to sync normally.
3. Connect KOIN Vault and choose the node wallet if you want to produce blocks.
4. Create or import the device's block-signing key, approve its registration in the
   wallet, and check registration after the observer catches up.
5. Check VHP production permission, review any required allowance in the wallet,
   then review and start block production.

An observer does not need a wallet. Mainnet wallet approvals remain in KOIN Vault.
The app checks chain readiness, account selection, key registration and production
permissions before starting. VHP allowances are explicit wallet-approved actions;
burns always require an explicit review and separate wallet approval.

**Home** shows time/date, KOIN, VHP, mana, block height, peers, local blocks this run,
device readings and recent finalized rewards. Its actions are Start/Stop and Silent Mode.
**Setup** contains detailed status and health reports. **History** contains rewards
and app actions. Local blocks this run and finalized rewards are separate measures.

## Source and builds

- [Build instructions](docs/BUILDING.md)
- [Android app source](app/src/main)
- [Native bridge, supervisor and Android dependency patches](native)
- [UI tests](tests/ui/README.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)

The native engine is based on upstream [Koinos Teleno](https://github.com/koinos/teleno)
commit `2c11229267cff2f9ee39ed8e56ab70af391d85ce`. The native materials release ZIP
includes pinned source archives, all 171 link inputs, licenses and instructions
for relinking with replacement libraries. APK-signing keys and wallet data are
not distributed.

Release validation covers 26 Robolectric tests, package/version checks, APK
signature verification, 16 KiB alignment and unchanged native engine hashes.
No physical Android device, overnight connection test or live burn transaction was available.
See [validation details](releases/0.3.9/validation.json).

Publisher certificate SHA-256:

```text
05db99a3f860721985cf7999e3503c6f652b3104a4e86750bcdb0553a00e71e1
```
