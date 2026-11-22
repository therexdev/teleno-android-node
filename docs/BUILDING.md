> For Lite 0.2.0, start with [BUILDING_LITE.md](BUILDING_LITE.md). The recipe below documents the inherited full cross-build dependencies and the earlier engine-free foundation. Engine/snapshot/evidence inputs are now accepted together by the Lite APK builder.

# Building Teleno Node Lite

Build in the separate Lite checkout, not the standard app checkout. Use Java 17+,
Python 3, Android SDK platform 36/build-tools 36.0.0, and ECJ 3.40.0. The existing
native materials supply `libteleno_keys.so` and `teleno_runner` for ARM64:
[standard native materials](https://github.com/therexdev/teleno-android-node/releases/download/v0.3.7/Teleno-Node-0.3.7-native-materials.zip).

Prepare an aligned unsigned preview:

```bash
python3 tools/build-apk.py --sdk /path/to/sdk --ecj /path/to/ecj.jar \
  --bridge /path/to/native-bridge --unsigned
```

Or use `--keystore /private/path/to/existing-signing.keystore` instead of
`--unsigned` to sign, with the inherited signing configuration. Keep the key
outside the checkout. Preserve this Lite publisher identity on future updates.
Output: `build/Teleno-Node-Lite-0.1.0.apk` when signed.

The preview builder rejects `--engine` and `--enable-production`; a full node
binary is not a compact-storage implementation. The release manifest package is
`com.koinos.teleno.lite`, version code 1, label `Teleno Node Lite`. Its Java/JNI
namespace remains `com.koinos.teleno`; AAPT receives `--custom-package` accordingly.
Do not change the namespace without rebuilding the JNI bridge and tests.

Verify isolation against a read-only standard checkout:

```bash
python3 tools/verify-lite-isolation.py --standard-root /path/to/standard \
  --sdk /path/to/sdk --apk build/Teleno-Node-Lite-0.1.0.apk
```

Use SDK `apksigner verify --verbose --print-certs` and `zipalign -c -P 16 4`
for release signature/alignment verification. Physical co-installation, independent
uninstall/data handling, and future concurrent mining with different producers
must also be tested on Android before a production release.
