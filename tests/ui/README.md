# Native Android UI smoke tests

These tests run the actual native Android view code with Robolectric 4.16,
Android 13 / API 33 and native graphics. The host compile/runtime Android JAR is API 36 to resolve newer Robolectric shadow signatures; the test sandbox stays on API 33. They are not physical-device tests.
The balance/reward fixtures use a synthetic address and invented amounts; no
wallet is connected and no transaction is signed. The actual MainActivity test
loads without packaged native assets, so its setup screenshot intentionally
shows an unavailable engine and test-environment metadata.

Coverage: portrait bottom navigation, landscape left rail, enlarged text, tab
visibility and selection across rotation, actual setup controls, persistent SQLite history, network isolation
and exact token amounts. The production-health test checks measured activity,
the account used at launch, the health dialog and clipboard report, and the
no-progress state. A separate warning test verifies VHP-burn classification,
grouped errors and a clipboard report without raw exception text. Permission
reviews retain their exact amounts and cancellation path. Screenshot-protection
checks keep normal UI capturable and private key import/export protected.

Twenty-six tests run in total. Compact dashboard checks cover 360×640 portrait,
960×540 Retroid landscape and 640×360 landscape with system-bar insets, alongside
412×915 portrait, 915×412 landscape and 360×800 at 1.3× text scale. They verify
non-scrolling Home, the Start/Stop and Silent Mode actions, KOIN above VHP, all nonempty
text inside its allocated height, child bounds, navigation placement, longer
amounts and two reward entries. The render harness dispatches pre-draw after
layout so right-aligned single-line text receives Android's normal scroll
position adjustment. A lifecycle test checks that running animation starts,
pauses off Home/in background, resumes, and stops when the node stops.

PNGs in `evidence/ui` are test fixtures, not live user account screenshots.
Animation is paused for deterministic captures. The actual Setup test also
confirms that the production-health report remains available there.

Use Java 17+ and Maven 3.9.9 or compatible. Build the APK (unsigned is sufficient) first so
`build/apk/classes` contains the current app classes. Resolve the pinned UI test
dependencies into a local directory:

```bash
mvn -f tests/ui/pom.xml dependency:copy-dependencies \
  -DoutputDirectory=/absolute/path/ui-deps
python3 tools/test-ui.py --deps /absolute/path/ui-deps \
  --ecj /absolute/path/ecj-3.40.0.jar
```

Maven needs access to Maven Central and Google's Maven repository. Robolectric
downloads the matching instrumented Android SDK on its first run. Configure any
required network proxy locally via Maven settings and Java system properties;
no environment credentials belong in this source package. Dependencies and test
classes remain outside the APK. The runner extracts AndroidX AAR class files to
`build/ui-tests` and writes screenshots to `evidence/ui` by default.

Silent Mode coverage checks screen-awake flags, brightness restoration (including
an existing lower brightness), pause/resume, tap and Back dismissal, rotation,
destroy cleanup, and unchanged node run counters. Render fixtures cover Retroid
landscape, small landscape, and portrait with larger text; movement bounds are
sampled over seven simulated days and the color change is checked separately.
These are simulated checks, not an overnight device soak test.

Run-reward tests use exact integer amounts larger than JavaScript's safe integer
range, more rewards than the dashboard feed, duplicate history reads, unrelated
accounts/networks, excluded non-reward records, and run start/stop boundaries.

Recovery tests cover sticky restart, Stop cancellation, cancelling a start before
it can save recovery intent, changed-network cancellation, native exit recovery,
Wi-Fi loss without process destruction and exact burn-review cancellation.
The host Java tests separately cover watchdog deadlines/backoff, exact burn ABI,
zero balances, mana, stale reviews, irreversible confirmation and unknown-delivery
recovery without duplicate wallet submission.
