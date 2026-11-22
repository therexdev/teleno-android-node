# Teleno Node 0.3.12 — four-step setup and burns while mining

Package `com.koinos.teleno`, build 20. Stop the node and install over the current
app. Keep the app installed and preserve its data. The publisher signing identity
and native node engine are unchanged.

1. **Connect wallet.** Choose the producer wallet in KOIN Vault and approve the
   connection. Saved accounts are reused. New installations default to Mainnet;
   existing network choices are preserved.
2. **Burn KOIN to VHP.** Review an exact burn and optionally include an exact VHP
   production allowance in the same atomic wallet transaction. This works before
   downloading the chain and while the same producer is mining. Existing VHP
   holders can skip burning and use the permission controls.
3. **Restore or download chain.** Reuse existing app-owned data, reconnect a
   database, or review a snapshot download. Mobile/metered data still requires a
   separate size/charges warning and explicit consent.
4. **Start node.** Setup and Home use the same startup flow. It reuses the saved
   signing key, or guides creation and explicit Vault registration when needed.
   After the start review, Teleno syncs through an observer and enables mining
   automatically after fresh local chain, peer, VHP, allowance and key checks.
   Stop node cancels this run and automatic recovery. Charging is optional.

Signing-key backup/import, manual accounts, connection details, device settings
and observer-only operation remain available under expandable controls.

Burning no longer requires switching to an observer or stopping production.
VHP consumption, allowance consumption and KOIN rewards during a review do not
invalidate an otherwise funded burn. Current KOIN/mana and the review's account,
contract and expiry are still checked immediately before submission. The exact
reviewed burn and optional VHP allowance are never silently increased. The shown
post-burn VHP balance is an estimate while mining continues.

Vault's current server gives connections a 30-minute lifetime. An unexpired
connection is reused across actions and app restarts. Reconnect after expiry or
wallet-side disconnect to request a new wallet transaction; each transaction
still needs approval in Vault. Mining uses the saved producer and device signing
key and does not require a live Vault session. Reconnecting while mining is now
available and does not change the running producer. Revoking production
permission still requires stopping production.

Saved wallet requests and transaction IDs remain durable and are never resent
automatically. Burn confirmation checks the exact irreversible transaction, so
later mining balance changes do not imply a failed burn.

Validation covers Android UI simulations, exact transaction encoding, mutable
mining balances, current-funds limits, expiry, account/contract changes, durable
requests, reconnection and startup intents. No live wallet transaction was
submitted. Physical-device first-time onboarding and overnight mining still
need tester validation; continuous Android uptime is not guaranteed.
