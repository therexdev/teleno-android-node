# Third-party material

The bundled network genesis/configuration assets come from Koinos Teleno and the
KAI Teleno integration. Teleno is MIT licensed; its notice is included in
`app/src/main/assets/licenses/teleno.txt`.

The APK statically links OpenSSL 3.0.12 into `libteleno_keys.so`.
OpenSSL's Apache-2.0 license is included in
`app/src/main/assets/licenses/openssl.txt` and in the APK assets.
Source: https://github.com/openssl/openssl/tree/openssl-3.0.12

The Teleno Node APK also contains the full Teleno node. Its static
dependencies include the Koinos SDK, Fizzy, cpp-libp2p, RocksDB, protobuf, gRPC,
OpenSSL, Boost, GMP, libssh, zstd, zlib, ethash, secp256k1 libraries, abseil,
c-ares, re2, yaml-cpp, nlohmann JSON, fmt, soralog, qtils and hat-trie. Original
dependency notices and Android runtime notices are included in
`app/src/main/assets/licenses/` and are readable from the app's Open-source
notices button.

GMP 6.3.0 is included under its LGPLv3 option and libssh 0.12.0 under LGPLv2.1.
The companion `Teleno-Node-0.3.7-native-materials.zip` contains their original source
archives, the complete libssh patch used for this build, build configuration,
all 171 object/archive inputs needed to relink Teleno, and the relink script.
GMP's source is unchanged; its Android cross-compilation invocation is recorded.
The bundle supports replacing these libraries and rebuilding an APK using the
app source and your own signing key. Nothing in the app's native integrity
checks prevents generating fresh hashes for a modified build. No restriction
on reverse engineering for debugging such library modifications is imposed.

Upstream source archives are pinned by the Teleno/Koinos recipes. The Android
patches and their provenance are included in this source package. The native
relink materials are build inputs, not an Android SDK or device qualification.
No signing private key, wallet data or recovery phrase is included in either
archive.
