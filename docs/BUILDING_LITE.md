# Building and testing the experimental Lite runtime

The regular Android/JNI source namespace stays `com.koinos.teleno`; the Android
installation ID is independently `com.koinos.teleno.lite`. Preserve its existing
signing certificate to upgrade the foundation installation. Never copy a funds
wallet key into a build or node directory.

## Locked sources

`native/lite-engine.json` locks the native patch and every changed source file.
The base is `koinos/teleno@2c11229267cff2f9ee39ed8e56ab70af391d85ce`.
`tools/prepare-teleno.py --source /checkout/teleno --output /new/prepared-lite`
exports that exact base, applies the recorded Android CMake hook and Lite patch,
and verifies the resulting Lite source hashes. It never changes the input tree.

A full cross-build uses `tools/build-engine.py` and the pinned dependency prefixes
described in `docs/BUILDING.md`. Its input now must have the Lite patch applied.
The original protobuf ownership and Fizzy runtime checks remain in force.

## Relinking the released native materials

A faster qualified route rebuilds only the block-store and RocksDB storage-owner
objects, then relinks all original inputs. Public `BlockStore`/`NodeConfig` object
layouts and virtual interfaces remain unchanged. The database stores its explicit
Lite policy; an existing full archive is never automatically pruned. The storage
owner registers its existing durable-state handle, and the post-accept callback
reads that committed revision rather than trusting a peer height.

Use the native materials ZIP from the standard repository's 0.3.7 release:
https://github.com/therexdev/teleno-android-node/releases/tag/v0.3.7
The bundle's `link.json` pins all 171 original input hashes and source versions.
Use official Android NDK r27d (`27.3.13750724`). The qualification dependency
workspace (`--deps`) has these header source locations:

| Location | Source |
|---|---|
| `rocksdb-8.8.1-source` | facebook/rocksdb v8.8.1, archive SHA1 `ef3e82eb750013c9fec5220911213609613776d7` |
| `protobuf-owned-source` | koinos/protobuf e1b1477875a8b022903b548eb144f2c7bf4d9561, archive SHA256 `824eca40d5a9b7e1090961e84efe7b90fba818156c32d92e447f7daf61522318`, with the pinned Teleno ownership patch |
| `proto-cpp-source` | koinos/koinos-proto-cpp v2.6.0, archive SHA1 `1c83a0f32089775b489d7846de6efefe5095da14` |
| `component-test/exception` | koinos-exception-cpp v1.0.2, archive SHA1 `e7cf9e149268ee78b1b0c342eccd40ce9354a3ad` |
| `component-test/log` | koinos-log-cpp v2.0.1, archive SHA1 `44505fceff05bddb374f15fe328b6681147513d8` |
| `component-test/util` | koinos-util-cpp v1.0.2, archive SHA1 `06e08703417187e689e16bd36e74ac564e2b6366` |
| `component-test/crypto` | koinos-crypto-cpp v1.0.2 (test tools), archive SHA1 `3b459f321d106e587dc94b13b7f5be230395e183` |
| `component-test/include/nlohmann/json.hpp` | nlohmann/json v3.11.2 single header |
| `host-engine/hunter/_Base/*/*/*/Install/include/boost` | Boost 1.83, source archive SHA1 `75b1f569134401d178ad2aaf97a2993898dd7ee3` |

These are header sources for the original binary ABIs, not substitutions for its
linked runtimes. Do not use a different RocksDB/protobuf version for Android
relinking. The helper host component test used newer distribution runtimes; the
end-to-end ARM64 tests used the actual pinned Android runtimes.

```bash
python3 tools/build-lite-relink.py --source /prepared-lite --deps /workspace/deps \
  --ndk /android-ndk-r27d --materials /teleno-android-native-relink \
  --output /new/lite-arm64 --test-tools
```

The tool checks original input hashes, locked changed sources and NDK version.
It creates a separate bundle with exactly two intentionally replaced archives,
then records their hashes and the resulting engine hash. It does not qualify it.
The qualification engine SHA256 is
`36dae70b7fa3bbba95140010341d0d92424666c0c0fad9d40746525a548bbbd6`.
The relocatable builder reproduced this exact hash.

## Snapshot export

Install `rocksdict==0.3.29` and protobuf 4.25+ in a tool-only Python environment.
`tools/lite_snapshot.py` reads an immutable, manifest-verified checkpoint and
exports to a new directory. It copies all current state and compares complete
length-prefixed key/value hashes, validates genesis/chain/header identity and
recent receipt-root continuity, reconstructs the recent skip-list, and writes
persistent retention metadata. It creates a private read-only source view because
rocksdict itself writes a configuration file even on read-only opens.

```bash
python3 tools/lite_snapshot.py --source /checkpoint \
  --files /foundation-files.json --manifest /foundation-manifest.json \
  --output /new/compact-checkpoint --retained-blocks 4096
python3 tests/lite_snapshot_test.py -v
```

Selective source views may omit old block SSTs; every supplied file is fully
SHA-256 verified and all state iterators verify checksums and final status. A
missing state SST must fail, never become an empty state copy. The advisory
`lite_sst_inventory.py` range measurement is not restore qualification. Full
source hashes are checked again before the new completed export is published.

## Native and Android testing

The native patch adds the CMake/CTest target `koinos_lite_retention_test`. It covers
rolling pruning, power-of-two boundaries, recent forks, restart, state
preservation, malformed policy and failed-prune atomicity. The deterministic
`tests/native/lite_block_signer.cpp` and upstream private-testnet keygen are test
helpers only. Their known seed must never fund a public-network account.

`tests/native/qualify_lite.py --workspace /qualification-workspace \
 --engine-source /prepared-lite --output /new/results` repeats reference-root,
restore/catch-up, pruning and federated-production checks. It expects the documented
`deps/lite-arm64` engine/test helpers, `deps/lite-probe` reference binary and test
key-info file, QEMU AArch64 under `deps/debian/sysroot`, and Android runtime under
`deps/android-runtime/root`. The test has no P2P network or wallet connection.
The tested QEMU environment used AOSP Android libc, stderr-only liblog, and an
unused libandroid shim; it is not an Android installation or endurance test.

## Build the APK

Use official SDK platform/build-tools 36, verified ECJ 3.40.0, the existing native
keys bridge/supervisor and the existing app signing key. The APK builder requires
native test evidence bound to the engine SHA256. Experimental production also
requires successful isolated native block production in that evidence.

```bash
python3 tools/build-apk.py --sdk /android-sdk --ecj /ecj.jar --bridge /native-bin \
  --engine /lite-arm64/teleno_node --lite-snapshot /compact-checkpoint \
  --lite-validation /native-results/result.json --enable-production \
  --keystore /private/existing-signing.keystore
python3 tools/test-ui.py --deps /robolectric-jars --ecj /ecj.jar \
  --test-class LiteRuntimeUiTest --output /test-output
python3 tools/verify-lite-isolation.py --standard-root /standard-app \
  --sdk /android-sdk --apk build/Teleno-Node-Lite-0.2.2.apk
```

The snapshot's exact files and genesis are verified again before bundling. The
APK generates an enabled `LiteRuntime` bound to its manifest hash; the checked-in
fallback class remains disabled. Omitting all engine/snapshot/evidence inputs
builds a foundation preview with blocked startup. Gradle alone uses that disabled
fallback. The 0.2.0 runtime has not been physically installed or tested with real
Vault approvals or Mainnet PoB production; qualification remains false.

The signed APK itself carries the exact tested engine, bridge, supervisor and
checkpoint. To recover them for a source rebuild, first verify the APK's supplied
checksum/signature, then run:

```bash
python3 tools/extract-lite-runtime.py --apk /Teleno-Node-Lite-0.2.2.apk \
  --output /new/extracted-runtime
```

Use `extracted-runtime/teleno_node`, `extracted-runtime/bridge` and
`extracted-runtime/snapshot` with the APK build command above, and the matching
`evidence/lite-0.2.0/native-production.json`. The extractor checks contained file
sizes/hashes; it does not independently establish the publisher's identity.

## Restore regression tests added in 0.2.1

`LiteRestoreTest` executes the real restore using assets loaded from the built
APK, with real host files and ownership locks. Only Android directory-fsync/lock
entry points are shadowed. Run it with `--test-class LiteRestoreTest` and a fresh
`--output` directory. It verifies every copied file and activation, then covers
cancellation, preserved previous data/wallet sentinels, cleanup and a successful
retry. The test also exports its actual restored database and observer-first
markers for `tests/native/check_java_restore.py`. That helper starts an isolated
ARM64 observer from a copy, checks native preflight, Mainnet state identity, the
observer recovery hold and a local KOIN contract read. Physical-device operation
remains separate. See `evidence/lite-0.2.1/VALIDATION.md`.

## Sync status and recovery regression tests added in 0.2.2

`SyncStatusTests` replays sanitized chain/peer timing without a wallet or network.
It covers startup counts versus newer native gate events, event expiry, malformed
metrics, measured native progress and the RPC recovery deadline. Existing chain
identity, timestamp, advancement and finality requirements remain enforced.
`SyncStatusUiTest` verifies the enabled-runtime dashboard for current-chain peer
loss, pending peer counts, real eligibility activity, RPC delays and historical
sync. Run it with `tools/test-ui.py --test-class SyncStatusUiTest` using the same
dependency and compiler arguments above. These fixture tests do not establish
phone endurance, live peer stability or Mainnet PoB production.
