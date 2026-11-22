# Teleno Node Lite 0.2.1

Fixes compact restore failing to open the first SST file on a fresh installation.
The database directory is now created before copying. Restore failures now point
to the actual Restore compact checkpoint control and explain the offline retry.

Install over your existing Lite app. Do not uninstall or clear data. Tap Restore
compact checkpoint again, then Start node. Wallet connection data and signing
keys are preserved. The standard Teleno app is unchanged.

The bundled checkpoint and ARM64 engine are unchanged from 0.2.0. Full APK-asset
restore, cancellation/preservation/retry, native observer handoff and UI checks
are covered in the matching validation record. The app remains experimental;
this is not a claim of physical-device or Mainnet PoB qualification.
