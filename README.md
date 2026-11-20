# Teleno Node

Run a Koinos node directly on an Android device. Teleno Node bundles the full
native Teleno engine with a dashboard, guided setup, KOIN Vault connection,
chain snapshot downloads and reward history. Docker, WSL and root are not required.

## Download

Published download: [Teleno Node 0.3.8 APK](https://raw.githubusercontent.com/therexdev/teleno-android-node/main/releases/0.3.8/Teleno-Node-0.3.8.apk).

**Prepared update: 0.3.11 (build 19)** allows the node and miner to run on battery, and includes mobile-data downloads,
existing-chain recovery and clearer setup alongside the mining recovery and wallet burns from
prepared version 0.3.9. Its signed APK, source and checksums are supplied separately
in ChatGPT while physical-device validation is pending. The source changes are in
[draft PR #1](https://github.com/therexdev/teleno-android-node/pull/1); a 0.3.11
binary release has not been published to GitHub.

Requires **Android 9 or newer and ARM64**. Stop the node and install the update
over the existing app; do not uninstall or clear its data. The package remains
`com.koinos.teleno`, uses version code **19** and retains the same signing certificate.

## What changed in 0.3.11

- Run the observer and block producer on battery. Charging is optional; unplugging
  no longer pauses mining or blocks automatic recovery.

- Use an existing downloaded chain as the normal next step. A new snapshot is optional.
- Find and reconnect complete chain data in the app's phone or SD-card locations.
- Label backup deletion as optional and protect the active chain during cleanup/discard.
- Show exact snapshot size and estimated remaining data before explicitly approving
  a mobile or metered download. Wi-Fi-only transfers cannot silently change networks.
- Ask separately before ongoing mobile-data node synchronization or mining.
- Keep automatic mining recovery, Silent Mode and explicit KOIN-to-VHP wallet
  reviews from prepared 0.3.9.

See [0.3.11 setup and recovery instructions](releases/0.3.11/RELEASE_NOTES.md).
The unchanged native engine can be rebuilt from the
[0.3.7 native materials](https://github.com/therexdev/teleno-android-node/releases/download/v0.3.7/Teleno-Node-0.3.7-native-materials.zip).

## Use the app

1. In **Setup**, choose the network and storage location. Reuse an existing chain if present.
2. Use the saved chain, find an existing database, or review an optional snapshot download.
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

Release validation covers 41 Robolectric tests, package/version checks, APK
signature verification, 16 KiB alignment and unchanged native engine hashes.
No physical Android device, overnight connection test or live burn transaction was available.
See [validation details](releases/0.3.11/validation.json).

Publisher certificate SHA-256:

```text
05db99a3f860721985cf7999e3503c6f652b3104a4e86750bcdb0553a00e71e1
```
