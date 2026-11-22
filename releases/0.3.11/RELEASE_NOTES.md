# Teleno Node 0.3.11 — run on battery

Package `com.koinos.teleno`, build 19. Stop the node and install over the current
app. The package and publisher signing identity remain unchanged.

- The observer and block producer can start and continue running without a charger.
- Unplugging the device no longer pauses production or prevents automatic recovery.
- A missing battery-status broadcast no longer blocks production.
- Setup and the production review explicitly say charging is optional.
- Overheating, low-memory, storage and network checks still apply. The battery
  status display still reports whether the device is plugged in or on battery.

The mobile-data consent, saved-chain recovery, Silent Mode, automatic mining
recovery and wallet burn/approval features from prepared 0.3.10 remain included.
There is no new battery threshold, automatic wallet action or data reset.

Validation: 41 Robolectric Android simulation tests, including startup and repeat
producer/observer environment checks while unplugged, plug/unplug transitions,
missing battery broadcasts and retained thermal/storage protection. The APK's
signature, version and 16 KiB alignment are checked; native engine/assets are
unchanged. Physical-device battery operation and overnight mining have not been
verified here.
