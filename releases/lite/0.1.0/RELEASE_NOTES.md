# Teleno Node Lite 0.1.0 — separate-app foundation

This is a separate Android application, not an update to Teleno Node.

- App ID: `com.koinos.teleno.lite`; version code 1; Android 9+, ARM64.
- Launcher: **Teleno Node Lite**, with a blue T/Lite icon.
- Independent app-private data, signing-key alias, encrypted Vault-session alias
  and chain-directory name. No shared UID or cross-app database provider.
- Separate RPC/P2P ports reserved as 28085/28888.
- KOIN Vault pairing identifies the app as Teleno Node Lite.
- Based on the 0.3.12 four-step setup, dashboard and explicit wallet review flows.

**Compact mining is not available in this foundation preview.** The APK excludes
the standard node engine. Node startup, observer sync, standard snapshot downloads,
maintenance and saved-run recovery are blocked. Start node explains this status.
Burn reviews make clear that burning does not enable Lite mining. Wallet requests
remain explicit and are never automatically resent.

Install Lite as a new app alongside Teleno Node. Do not uninstall, clear or
replace the standard app. Lite does not import its database, signing keys or
wallet sessions. Existing standard-app source, APK and working branch remain
unchanged. Future Lite updates must keep this package and publisher certificate.

Validation: 7 Robolectric API 33 Android simulation tests, 280 host wallet/burn/
permission assertions, source/APK identity and isolation checks, APK signature and
16 KiB alignment. No physical-device co-installation or live wallet transaction
was tested. No compact mining/storage or uptime claim is made.

The next stage is engine storage measurement, safe pruning with retained ancestor
metadata, a compact bootstrap snapshot and reference-node/device validation.
See `docs/LITE_STORAGE_DESIGN.md` for the implementation and acceptance criteria.
