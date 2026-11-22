# Teleno Node Lite

A separate Android application for developing a compact-storage, on-phone Koinos
miner with KOIN Vault approvals. It is based on Teleno Node 0.3.12 and is kept on
its own `lite/foundation-0.1.0` branch. The standard application and working branch
are unchanged.

## Current build: 0.1.0 foundation preview

This APK is installable alongside Teleno Node. It provides the inherited dashboard,
wallet connection and explicit wallet review flows. **Compact mining is not ready.**
Start, restore/download, maintenance and recovery paths are blocked in this preview.
It does not include the standard node engine or download the full chain database.
A wallet burn does not enable Lite mining; every wallet transaction still needs
explicit approval in Vault.

| Boundary | Teleno Node | Teleno Node Lite |
| --- | --- | --- |
| Android application ID | `com.koinos.teleno` | `com.koinos.teleno.lite` |
| Launcher label | Teleno Node | Teleno Node Lite |
| Icon | Green T | Blue T with Lite badge |
| RPC port | 18085 | 28085 |
| P2P port | 18888 | 28888 |
| Keys, sessions, preferences and app data | Standard app sandbox | Lite app sandbox |
| Chain directory name | `teleno-chain` | `teleno-lite-chain` |

The packages do not share a UID, chain database, signing key or encrypted Vault
session. Lite does not migrate or reconnect the standard app's files. Installing
or removing Lite does not replace or uninstall Teleno Node. The same signing
certificate may sign both packages; their application IDs remain independent.
Physical-device co-installation is still to be tested.

The Java/JNI namespace intentionally remains `com.koinos.teleno` for bridge ABI
compatibility. Android component names are fully qualified and the independent
installation identity is `com.koinos.teleno.lite`.

## Storage work

The native engine requires safe history pruning, preservation of required
ancestor metadata, a compact bootstrap snapshot, and reference-node validation.
A minimum storage requirement has not been measured. See the concrete
[compact-mining design and acceptance criteria](docs/LITE_STORAGE_DESIGN.md).

## Build and test

See [build instructions](docs/BUILDING.md) and [Lite validation](tests/ui/README.md).
The signing key is never included in source archives. Android 9+, ARM64.
Do not install historical standard APKs from inherited `releases/` directories
when testing Lite; only `releases/lite/0.1.0/` contains this app's release metadata.
