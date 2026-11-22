# Teleno Node 0.3.10 — mobile data and existing-chain setup

Package `com.koinos.teleno`, version code 18. Same publisher certificate and
native engine as 0.3.8/0.3.9. Stop the node and install over the current app.
Do not uninstall or clear app data.

## Existing chain data comes first

The previous setup flow recommended a snapshot whenever the node was stopped,
even when a chain had already been downloaded. Its previous-copy cleanup action
could also look like a prerequisite for starting the node. These confusing code
paths were reproduced; the tester's actual phone files were not available here.

- A recognized saved chain now offers **Use existing chain · start observer**.
  The next step explains that another snapshot or backup deletion is unnecessary.
- **Find existing chain data** searches this app's phone storage and mounted
  app-specific SD-card locations. It offers complete, marked database generations
  and the original database without changing files during the search.
- Selecting a discovered chain repairs its storage selection and links under the
  node ownership lock. It does not download a snapshot or delete database files.
- Missing links for an otherwise intact selected generation can be repaired on start.
- Backup deletion is explicitly optional. It refuses an unreadable active chain
  and checks canonical paths before deleting a separate backup.
- A completed restore's leftover download record is reconciled rather than
  prompting for a second full download. Discarding a transfer never deletes a
  published, complete chain even if the saved storage selection was lost.
- Support reports now include chain-selection and manifest-presence details.

### What the tester should try first

1. Install this APK over the existing app, preserving its data.
2. In Setup step 2, use the saved chain if recognized. Otherwise tap
   **Find existing chain data**, choose the correct phone/SD copy and confirm.
3. Start the observer and let it catch up. Production still requires the normal checks.
4. If the chain is not found, keep all existing folders and copy the Production
   health report before considering another download. This recovery can reconnect
   intact app-owned data; it cannot reconstruct files already deleted or removed
   by uninstalling/clearing app data.

Replacing a working chain with a newer snapshot is now an explicit Advanced
choice, separate from normal startup. Backup cleanup remains optional for startup;
repeated snapshot replacement still requires resolving the previous backup first.

## Download over mobile or metered data

Snapshot review shows the full size in GB, GiB and bytes. A resumed transfer also
shows its estimated remaining size. Mobile, metered Wi-Fi and unknown-cost network
connections require a second explicit approval before bulk downloading begins,
even if the cellular plan is reported as unmetered.

The warning explains data allowances, possible carrier/roaming charges, throttling,
fair-use limits and additional retry/metadata traffic. Cancel starts no bulk
transfer. Verified files and partial portions remain reusable when resuming.
Wi-Fi-only requests bind their HTTPS connections to the checked Android network,
so a lost Wi-Fi connection cannot silently move that socket onto mobile data.

Download approval is scoped to the reviewed snapshot. It does not authorize
ongoing node traffic. After a mobile download completes, the chain is kept ready
without automatically starting the observer on mobile data. Starting the observer
on mobile prompts separately for ongoing synchronization and peer traffic. That
permission lasts for the user-started run and its automatic recovery; Stop ends it.

The mining recovery and KOIN/VHP wallet features from prepared version 0.3.9 are
included. No automatic wallet burn or approval has been added.

## Validation

- 39 Robolectric Android UI/service/storage tests passed, including new recovery,
  mobile consent, network-selection, cleanup and completed-download scenarios.
- 96 host assertions passed across snapshot policy, download integrity/retry,
  data warnings, resume estimates and active-chain protection.
- APK signature, identity, unchanged native payload and 16 KiB alignment checked.
- No physical tester phone, carrier download, overnight mining or live wallet
  transaction was available. Storage integration uses real host files and links
  with simulated ownership and directory-flush Android calls; it does not qualify
  physical Android durability. See `validation.json`.
