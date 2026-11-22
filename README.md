# Teleno Node

Run a Koinos node directly on an Android device. Teleno Node bundles the full
native Teleno engine with a dashboard, guided setup, KOIN Vault connection,
chain snapshot downloads and reward history. Docker, WSL and root are not required.

## Download

Published download: [Teleno Node 0.3.8 APK](https://raw.githubusercontent.com/therexdev/teleno-android-node/main/releases/0.3.8/Teleno-Node-0.3.8.apk).

**Prepared update: 0.3.12 (build 20)** streamlines setup into Connect wallet →
Burn KOIN to VHP → Restore/download chain → Start node. Home's Start node uses the
same complete startup flow: prepare/register a signing key when needed, catch up,
and enable mining automatically after local checks. Burns and optional exact VHP
allowances work while mining and before the chain is downloaded. Battery operation,
mobile-data consent, existing-chain recovery and automatic mining recovery remain
included.

The signed APK, matching source and checksums are supplied separately in ChatGPT.
Source and release metadata are in [draft PR #1](https://github.com/therexdev/teleno-android-node/pull/1);
the 0.3.12 binary is not published to GitHub. Physical-device validation is pending.

Requires **Android 9 or newer and ARM64**. Stop the node and install the update
over the existing app; do not uninstall or clear its data. Package
`com.koinos.teleno`, version code **20**, retains the same signing certificate.

## Use the app

1. **Connect wallet.** Approve the connection in KOIN Vault. Existing accounts
   and network choices are kept; new installations select Mainnet.
2. **Burn KOIN to VHP.** Review the exact amount and optionally approve a fixed VHP
   production allowance in the same wallet transaction. Skip burning if you already
   have VHP and use the production-permission controls as needed.
3. **Restore or download chain.** Reuse saved data or find an intact database.
   Optional snapshot downloads show their size before Wi-Fi/mobile-data consent.
4. **Start node** in Setup or Home. The app handles signing-key setup/registration
   when needed, syncs, and enables mining after fresh local checks. Stop cancels
   startup and automatic recovery. Charging is optional.

Wallet transaction approvals remain explicit. Vault connections currently expire
30 minutes after creation, so reconnect for a new wallet transaction after expiry.
The saved producer and device signing key can keep mining without a live wallet
connection. Burning and renewing VHP permission do not stop the running producer;
revoking permission requires stopping it. Extra signing-key, device and observer
controls are expandable in Setup.

See [0.3.12 release notes](releases/0.3.12/RELEASE_NOTES.md).
The unchanged native engine can be rebuilt from the
[0.3.7 native materials](https://github.com/therexdev/teleno-android-node/releases/download/v0.3.7/Teleno-Node-0.3.7-native-materials.zip).

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
