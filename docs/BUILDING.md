# Building Teleno Node

The published APK is built with the SDK-tools path below. The repository contains
all Android app, JNI bridge, supervisor, dependency overlays and test source.
The companion [native materials ZIP](https://github.com/therexdev/teleno-android-node/releases/download/v0.3.7/Teleno-Node-0.3.7-native-materials.zip)
contains the exact native binaries, pinned source archives, link inputs, licenses
and instructions for replacing statically linked libraries.

## Build with the supplied native engine

Extract the native materials ZIP. With SDK platform 36, build-tools 36.0.0,
Java 17+, Python 3 and ECJ 3.40.0 installed, run from the repository root:

```sh
python3 tools/build-apk.py \
  --sdk /absolute/path/android-sdk \
  --ecj /absolute/path/ecj-3.40.0.jar \
  --bridge /absolute/path/teleno-android-native-relink/bin \
  --engine /absolute/path/teleno-android-native-relink/bin/teleno_node \
  --enable-production \
  --unsigned
```

Output: `build/Teleno-Node-0.3.11-unsigned.apk`. Sign your own build with
`apksigner sign --ks /private/path/app-signing.keystore --out Teleno-Node.apk build/Teleno-Node-0.3.11-unsigned.apk`.
Then run `apksigner verify --verbose Teleno-Node.apk` and
`zipalign -c -P 16 4 Teleno-Node.apk` using the SDK build-tools.
Only an APK signed with the existing installation's certificate can update it.
The project's signing private key is not distributed.

The helper's direct `--keystore` path preserves the original publisher key's
alias/password convention; arbitrary keys should use the unsigned path and
`apksigner` directly. The helper never creates a new key silently.

## Tests

Build the APK first, then follow [the UI test instructions](../tests/ui/README.md).
`tools/test-host.sh` runs the native/Java host tests with the Android NDK and ECJ
paths supplied in `TELENO_ANDROID_NDK` and `TELENO_ECJ`.

The 0.3.11 APK passes 41 Robolectric UI/service/storage tests and APK identity, signature, native
payload and alignment checks. No physical Android device was connected for this
release. The screensaver does not change engine qualification or
bypass the existing production-readiness and wallet-approval checks.

## Build inputs

| Input | Pin |
| --- | --- |
| Teleno | `2c11229267cff2f9ee39ed8e56ab70af391d85ce` / 1.3.0 |
| Android NDK | r27d / `27.3.13750724` |
| ABI / API / STL | arm64-v8a / android-28 / c++_static |
| SDK platform / build tools | 36 / 36.0.0 |
| CMake tested | 3.31.6 |
| Koinos CMake | `12cc060400781497140ad549a094f2d0680503e0` / v1.0.9 |
| Fizzy | `b9bf7feaa8009a3d4f4bdd49245a5cd55d122055` plus upstream Teleno ownership patch |
| Host protoc | Koinos `e1b1477875a8b022903b548eb144f2c7bf4d9561` |
| Java compiler used | Eclipse ECJ 3.40.0 |
| Android Studio project | AGP 8.13.2, Gradle 8.13, JDK 17+ |

NDK r28c fails on the pinned Fizzy use of `std::char_traits<unsigned char>`;
r27d builds it with Teleno's existing `-Wno-error=deprecated-declarations` flag.
No consensus execution behavior or upstream verified source hashes were bypassed.
The Android build patches are isolated in `native/patches/` and the upstream
Teleno checkout is unchanged.

## Build the APK

The reproducible SDK-tools build does not require Gradle. Install the official
SDK/NDK and obtain ECJ 3.40.0 from Maven Central:

`https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar`

Expected ECJ SHA256:
`05cc22a24e7982970f63a405fc6c820bc80b806f27f3c5a6236fc475f8f7152b`.
The build helper enforces it. Native bridge building needs an Android ARM64
OpenSSL static prefix, not host Linux libraries.

```sh
export TELENO_ANDROID_NDK=/absolute/path/to/android-ndk-r27d
cmake -S native/bridge -B build/native-bridge \
  -DCMAKE_TOOLCHAIN_FILE="$PWD/native/cmake/android-arm64.cmake" \
  -DCMAKE_BUILD_TYPE=Release \
  -DOPENSSL_INCLUDE_DIR=/absolute/android-openssl/include \
  -DOPENSSL_CRYPTO_LIBRARY=/absolute/android-openssl/lib/libcrypto.a
cmake --build build/native-bridge --parallel 2
python3 tools/build-apk.py \
  --sdk /absolute/path/to/android-sdk \
  --ecj /absolute/path/to/ecj-3.40.0.jar \
  --bridge build/native-bridge \
  --engine /absolute/path/to/android/teleno_node \
  --enable-production \
  --keystore /private/path/app-signing.keystore
```

The helper compiles resources and Java, dexes, packages the ARM64 bridge, checks
ELF ABI/interpreter/16 KiB alignment, signs with the supplied certificate, and
verifies the APK signature and ZIP alignment. It requires an existing keystore
and never includes or generates one. Preserve that same identity for updates.
With `--unsigned` instead of `--keystore`, it writes an aligned, non-installable
`Teleno-Node-0.3.11-unsigned.apk` for later signing.

The supplied native relink bundle includes the Android engine used in this APK.
A Linux/glibc ARM64 binary is rejected. The resulting APK still leaves production
unqualified. Omit `--enable-production` to build observer-only; the flag
requires a complete engine and cannot be combined with diagnostic mode.
Use `--diagnostic` instead of `--engine` only to build a
clearly labeled preview without a node engine. The Gradle
project is provided for Android Studio; the SDK-tools build is the path tested
in this environment. Gradle does not download or build the node engine.

## Native port reproduction

The focused VM build uses the original hash-verified Teleno recipe:

```sh
cmake -S native/probe -B build/fizzy \
  -DTELENO_SOURCE=/absolute/path/to/pinned-teleno \
  -DCMAKE_TOOLCHAIN_FILE="$PWD/native/cmake/android-arm64.cmake" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_CXX_FLAGS=-Wno-error=deprecated-declarations
cmake --build build/fizzy --target teleno_fizzy_build --parallel 2
```

`native/dependencies/CMakeLists.txt` contains the full pinned Hunter dependency
request used for the cross-build. Use a separate project-owned `HUNTER_ROOT`,
create a `TELENO_ANDROID_OWNED` marker in that empty root, and never point it at
an existing desktop Hunter cache. The native dependency project applies the
hash-checked Hunter helper patch after Hunter initializes.
The Hunter patch preserves already-prefixed Android `-l:libunwind.a` linker
arguments. The libssh patch handles unavailable Android glob flags and uses the
POSIX owner-write permission bit. The GMP patch supplies the Android cross host;
GMP also requires the NDK CC/CXX/AR/RANLIB and a host `m4`.
The vendored secp256k1-vrf archive fixes one CMake cross-compilation bug: compiler
definitions are applied only to the actual host generator target, not its
imported cross-build reference. `native/vendor/SOURCES.json` records both
upstream and patched hashes; `tools/verify-vendored-secp.py` verifies that the
other 104 source files are byte-identical. Cryptographic code and static
precomputation remain enabled and unchanged. RocksDB's overlay adds the missing
Android `statfs` header. Protobuf's overlay changes three CMake files to use
consistent public linkage and the NDK system log library; it does not change
wire formats, public headers or serialization code. `tools/prepare-native-overlays.py`
checks the original archive hashes before making those overlays. Their manifests
record every changed file and both source hashes. The Teleno-specific protobuf
ownership patch must still be applied separately by the original runtime recipe.

`tools/prepare-teleno.py` exports the exact upstream commit to a new owned tree.
Its only Teleno change adds `native/ApplyProtobufAndroidBuild.cmake` after the
original ownership patch; all upstream runtime/header verification remains.
The Android helper checks all three CMake input/output hashes and is idempotent.
`tools/prepare-libp2p.py` exports the pinned peer library and applies Teleno's
original compatibility and listener-lifetime patches. All 840 files in its
prepared tree matched the successfully built Android peer library source.

Cross compilation also needs two **host** executables: the pinned Koinos `protoc`
and `grpc_cpp_plugin`. `native/host-grpc-plugin/CMakeLists.txt` builds the latter
from the hash-verified gRPC archive against the pinned host protobuf libraries.
Put both host build directories on `PATH` for the Koinos dependency build.
The Android protobuf package enables `protobuf_BUILD_LIBPROTOC=ON` so gRPC's
target-side compiler tools can link, while generation executes the host tools.
For the auxiliary peer-library build, use the recorded CMP0125/CMP0126 settings
to preserve the NDK compiler, and apply the owned Hunter linker helper through
`CMAKE_PROJECT_INCLUDE`. The final peer build instead uses Teleno's original
Koinos compatibility prelude, the Koinos OpenSSL/Boost/protobuf prefixes, and an
auxiliary include copy excluding `google/` and `openssl/`.

`tools/build-engine.py` contains the final configure/build invocation. It requires
the completed Android Koinos SDK and auxiliary prefixes, the prepared Teleno
source, and the libssh/GMP/zstd/RocksDB/cpp-libp2p installations. It does not
download a substitute binary or mark the result production-qualified. Validation for the published APK is recorded in `releases/0.3.11/validation.json`.
The complete Koinos SDK, cpp-libp2p, patched protobuf/RocksDB runtime, Teleno node
and private-testnet key generator have now built successfully for Android ARM64.
`native/AndroidKoinosPrelude.cmake` maps soralog's separate desktop `pthread`
library dependency to CMake's Android thread target; Android provides threading
through its C library. No node C++ runtime source was changed for this mapping.

## Native relink materials

`teleno-android-native-relink.zip` is a companion to this source package. It
contains the engine, 171 actual object/archive link inputs with debug information
removed, the recorded link arguments and input hashes, dependency notices, and
GMP/libssh source and build materials. `relink.py` successfully relinked the
engine using NDK r27d. This preserves the ability to replace static libraries
without rebuilding every dependency. Read the bundle's README before modifying
an input; modified inputs require an explicit command-line option.

To package a modified engine, use `tools/build-apk.py --engine` as above with
your own signing keystore. It regenerates the native integrity hashes.
Signing private keys and wallet data are excluded from both source and relink
archives. Keep your signing identity separately if you want Android to accept
future APK updates without uninstalling the app. Uninstalling removes its
private chain data and locally stored signing keys.

