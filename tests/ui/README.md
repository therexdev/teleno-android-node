# Lite foundation validation

The default UI suite is `LiteUiTest.java`: Robolectric 4.16, API 33, native graphics.
The inherited `UiSmokeTest.java` is retained as reference for the future compact
runtime; its full-node/recovery expectations are not the foundation preview policy.

```bash
python3 tools/test-ui.py --deps /path/to/robolectric-dependencies \
  --ecj /path/to/ecj.jar --output /path/to/test-output
```

The suite verifies Lite branding/status, all four setup anchors, Home and Setup
start gates, blocked snapshot/observer/discovery entry points, service rejection
of startup/download/recovery/maintenance, cancellation of stale run intent,
separate private key/session paths, exclusion of a standard app database from
Lite discovery, distinct ports, the preview disclosure in burn reviews, and a
separate Vault pairing name with unchanged protocol 2.

`tools/verify-lite-isolation.py` checks source and compiled APK identities,
fully qualified components, lack of a shared UID, distinct icon and ports,
separate storage/crypto names and exclusion of the standard engine. The actual
APK signature and 16 KiB alignment are checked separately. Host wallet/burn
fixtures still cover exact amounts, current-funds limits, expiry, chain checks,
durable saved requests and no automatic resend; no live transaction is sent.

Android simulation and manifest checks are not a physical-device co-installation,
Android Keystore UID-isolation test or compact-mining endurance test.
