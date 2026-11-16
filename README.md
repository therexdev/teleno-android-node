# Teleno Node

Run a Koinos node directly on an Android device. Teleno Node bundles the full
native Teleno engine with a dashboard, guided setup, KOIN Vault connection,
chain snapshot downloads and reward history. Docker, WSL and root are not required.

## Download

**[Download Teleno Node 0.3.7 APK](https://github.com/therexdev/teleno-android-node/releases/download/v0.3.7/Teleno-Node-0.3.7.apk)**

[Release notes and all files](https://github.com/therexdev/teleno-android-node/releases/tag/v0.3.7)
include the APK, app source ZIP, native source/relink materials and SHA-256 checksums.

Requires **Android 9 or newer and an ARM64 device**. Chain data requires substantial
free storage; Setup shows the space required for the selected snapshot. Removable
storage is offered when Android exposes a suitable app-specific SD-card location.

To update, stop the node and install the APK over the existing app. Version 0.3.7
uses package `com.koinos.teleno`, version code **15** and the same signing
certificate as the earlier installers. Keep the existing app and its data.

## What changed in 0.3.7

- The installed app is named **Teleno Node** and displays the plain version **0.3.7**.
- Setup uses **Review and start block production** wording.
- Fixed a wallet-initialization timing issue that could interrupt the screen refresh.
- The compact dashboard, portrait bottom menu and landscape left menu are retained.

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
the app does not automatically burn KOIN or transfer funds.

**Home** shows time/date, KOIN, VHP, mana, block height, peers, local blocks this run,
device readings and recent finalized rewards. Its only action is Start/Stop.
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

Release validation covers 13 Robolectric UI tests, package/version checks,
v1/v2/v3 APK signatures, 16 KiB alignment and unchanged native engine hashes.
No physical Android device was connected for this release; the label change does
not establish production qualification. See [validation details](releases/0.3.7/validation.json).

Publisher certificate SHA-256:

```text
05db99a3f860721985cf7999e3503c6f652b3104a4e86750bcdb0553a00e71e1
```
