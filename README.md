# Teleno Node Lite 0.2.2 — experimental compact miner

An independent Android application (`com.koinos.teleno.lite`) for local Koinos
validation and mining. It can be installed alongside standard Teleno Node. It
uses separate app storage, encrypted wallet/key aliases, notifications and ports
(RPC 28085, P2P 28888). The standard app and its branch are unchanged.

Version 0.2.2 separates actual chain syncing from reconnecting peers and delayed
local status. A newer native connection event can supersede the startup peer
count while the next numeric report is pending. Recovery now accounts for
measured native progress during local RPC delays. Install over your existing
Lite app and keep its chain, wallet state and signing keys. A current chain does
not need another restore. The checkpoint and native engine are unchanged.

## Setup

1. Connect the producer account to KOIN Vault. An existing valid connection is reused.
2. Use existing VHP, or review a KOIN-to-VHP burn and the resulting bounded PoB
   allowance in Vault. Register the device's block-signing key. These are explicit
   wallet approvals; the funds key never enters Lite. Burn review can occur while
   the node runs.
3. Restore the compact checkpoint included in the APK. It needs no additional
   network download or Wi-Fi. Every copied file is checked against its pinned
   SHA-256. Existing chain data is retained during an explicit replacement.
4. Tap **Start node** on Setup or the dashboard. The observer starts, catches up,
   checks local readiness and the reviewed producer permission, then starts PoB.

Charging is optional. Foreground execution, wake locks and recovery are inherited
from the existing app. Android/OEM process limits, lack of connectivity, severe
heat and low storage can still interrupt a phone; this is not a 100% uptime claim.
Use Setup → **Allow unrestricted background running** if Android still applies
battery optimization. A four-second-old local block with a zero-peer report
indicates current chain data and a connection/status check, not a failed restore.

## Storage

The bundled Mainnet checkpoint is **60,592,409 bytes** (about 61 MB), at durable
revision 41,258,090. The signed APK is about 71 MB. Keep a **2 GiB free-space
reserve** for chain growth and compaction. These are current measurements, not a
permanent storage cap.

The checkpoint retains all current consensus state and 4,097 recent block
records. The native engine prunes older block bodies/receipts after durable
finality, preserves positional ancestor navigation, and atomically advances its
retention floor. Optional account/transaction/contract-history indexes are off.
Wallet history can use the existing labelled public read source.

This is a trusted Foundation checkpoint bootstrap, not historical replay or a
proof of all past consensus. The phone validates subsequent blocks locally. Old
block ranges are unavailable from Lite; it is not an archival node. The snapshot
exporter creates a new database and does not convert or delete a full archive.

Ongoing peer traffic has no fixed total size. Mobile/metered use requires the
existing per-run warning and consent. The bundled restore itself works offline.
The current compact checkpoint is Mainnet-only; Harbinger compact restore is not
provided in this build.

## Validation and limits

The exact ARM64 engine ran under QEMU with Android libc. It matched a reference
node through 150 submitted, signed private-chain blocks, pruned old data, restored
and caught up from a compact checkpoint, then produced two federated test blocks.
The reference accepted them with matching state roots through height 152. A
further durable restart passed. The same engine restored the Mainnet checkpoint
with its expected chain ID and state root, resolved KOIN and executed a local
KOIN contract read. Native retention, corrupt-index atomicity, fork retention,
snapshot failure checks, wallet fixtures and Android UI tests also passed.

Physical-device installation/co-installation, Android Keystore behavior,
foreground endurance, live P2P catch-up, real Vault approvals and **Mainnet PoB
block production remain untested**. `productionQualified` remains false. This
APK explicitly enables experimental production for phone testing. No live wallet
transaction was sent as part of these tests.

See [the sync-status validation record](evidence/lite-0.2.2/VALIDATION.md),
[the restore-fix validation record](evidence/lite-0.2.1/VALIDATION.md) and
[build instructions](docs/BUILDING_LITE.md). The 0.1.0 foundation preview can still
be built by omitting engine/snapshot inputs; it remains unable to start a node.
