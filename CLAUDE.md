# Foundry (repo: Founder-s)

Project memory for Claude Code. It covers what Foundry is, how it is built, what the owner has decided and where work
stands. Keep it current: when something ships or a decision changes, update "Where things stand" and the decision log.

## What this is

Foundry is a browser built around search: "the future of search engines, with the browser as the base". The owner adds
features one at a time on top of a working skeleton.

| Part | Where | State |
|---|---|---|
| Android browser (the focus) | `android/` | A fork of Firefox for Android (Fenix on GeckoView), kept as patches over a pinned Firefox revision. GitHub Actions builds it into an installable APK. |
| Desktop prototype (Electron) | `src/`, `test/`, `README.md` | The first skeleton: an Electron browser with an on-device search engine. Paused since the move to Gecko. |

The project moved to Gecko because the owner wants Firefox extensions to work. Android comes first.

### Feature list (from the owner's Notion page "Foundry browser")

- **Bottom UI on mobile:** major controls within thumb reach, swipe gestures. Done: bottom toolbar and the gesture
  system below.
- **Multiple results for a search ("service tabs"):** spec in `docs/design/service-tabs.md` with a sketch in
  `docs/references/`. Not built yet.
- **Result priority:** ranking by a small ML model (SLM). Not started.
- **Built on Gecko:** done.
- **Scroll on tabs (up and down):** part of service tabs (confirmed by the owner with the sketch). Each card is a live,
  interactive page: up and down scrolls the page, and left and right swipes between the cards in that row. Sideways
  swipes must reach the cards even when the page under the finger could scroll sideways itself.
- **Floating address bar (laptop only):** a right-click or double-click anywhere on a page opens the usual menu with an
  empty URL box above it, which always opens a new tab. This is for the desktop prototype, not Android.

The Notion page's tagline is "Built for builders". Its Feature list database is the owner's source of truth for
features; read it with the Notion connector (search for "Foundry browser").

Settled decisions: the name is **Foundry**. Service tabs load each service's real web page, with no APIs, so the user's
existing logins work.

## Product thinking (owner's instruction: think like an owner and investor)

Steer the work as someone who owns and is investing in Foundry: be critical, put the core value first, and make changes
accordingly. Say so plainly when something is a poor use of time. Ask before big direction changes.

- **The bet:** one search, many sources, with the user's own logins in each and no API cost. A neutral place to compare
  AIs and search side by side, which single-AI browsers (Comet, Arc Search, Opera, Chrome with Gemini) won't be.
- **Core first:** service tabs is what makes Foundry worth installing. Gesture tuning waits unless something is broken.
- **Performance budget:** a search must not load every service. Cards load only when landed on; peeking neighbours show
  a placeholder. Measure memory, data and time-to-first-card on a mid-range phone before adding services.
- **Release readiness (required before anyone else installs it; not started, needs the owner's go-ahead):**
  - Turn off or replace Mozilla telemetry (Glean), crash reporting and Mozilla API keys.
  - Use Foundry's own `applicationId` instead of `org.mozilla.fenix.debug`, and a release signing key.
  - Check MPL-2.0 and Mozilla trademark obligations, and Firefox Sync and add-on server terms.
- **Security debt:** the fork is pinned to one Firefox revision. Plan a regular update to new Firefox releases, and keep
  patches small so updates stay cheap.
- **Platform risk:** AI sites may rate-limit or bot-check auto-submitted queries (`?q=`). Check on real devices, and
  fall back to opening the AI with the question for the user to send.
- **Learning:** ranking ("Result priority") needs signal. Plan opt-in, private, on-device metrics (such as which cards
  get opened) rather than server telemetry.

## Where things stand (2026-11-19)

- **All work goes straight to `main`.** No feature branches and no PRs (the owner's rule, 2026-11-19). Commit and push
  to `main`; every push that touches `android/**` builds the nightly APK. The old
  `claude/browser-search-engine-skeleton-j5ayor` branch was fast-forwarded into `main` and is retired.
- Build `efb44fa` crashed on every launch ("Foundry needs to restart"): the crash recorder read its file on the main
  thread during startup, and StrictMode killed the app. The next build fixes this by reading in the background. It
  also contains the fix for the crash on switching to homepage tabs, the current brow rules and the card that follows
  the thumb. The owner's feedback on those is pending.
- **Direction confirmed by the owner (2026-11-19):**
  - Keep the Firefox fork, and improve the code: fixing bugs and restructuring files and folders is allowed.
  - Then build the **service tabs** results screen. This is the first new feature; it will be patch `0007`.
  - Development now happens locally on the owner's Mac, with an emulator and their phone (see "Local builds on the
    owner's Mac"). CI stays the source of the nightly APK.
- **Restructure and fixes (2026-11-19, on the Mac):**
  - Foundry's own new files moved out of the patches into `android/src/` (see below). The patches went from about
    2,100 lines to about 600, and now only edit Firefox's files.
  - Fixed: a back or forward swipe started during the previous swipe's 200 ms settle lost its underlay and showed a
    blank page.
  - Fixed: the back or forward snapshot now waits for the history index to change, not the URL, so same-URL entries
    don't wait for the full timeout.
  - Fixed: a cancelled tab switcher animation no longer runs its end action (switching tabs or opening the tray).
  - Fixed: a snapshot lookup for a history entry that doesn't exist no longer returns a stale bitmap.
- Open questions for the owner:
  - Tab switching lights the destination's brow (left for the previous tab), to match back/forward. The owner has not
    confirmed this. Flipping it is one line: `destination` in `FoundryBrowsGuide.update`.
  - The tabs-tray landing spot (`FoundryTabSwitcher.trayCell`) is an estimate of Fenix's grid. Check it against a
    screenshot.

## Android: how the fork works

Firefox itself is never committed. `android/firefox/` is a gitignored working checkout.

- `android/FIREFOX_REVISION`: the pinned `mozilla-firefox/firefox` commit (Firefox 158.0a1).
- `android/patches/NNNN-*.patch`: our **edits to Firefox's own files**, applied in order with `git apply`. Keep them
  small: a call site, a default, a string.
- `android/src/`: **Foundry's own source files**, as plain files at their path in the Firefox tree (for example
  `android/src/mobile/android/fenix/app/src/main/java/org/mozilla/fenix/foundry/FoundryCrashLog.kt`). `apply.sh` copies
  them over the checkout. Edit them here directly; there is no patch to regenerate.
- `android/overlay/`: whole files copied over the checkout. These are brand drawables generated by
  `android/branding/generate.py` (needs fontTools; brand blue `#2B50E0`, DejaVu Sans Bold). Regenerate them; don't
  hand-edit.
- `android/mozconfig`: artifact build (prebuilt Gecko, aarch64), objdir `objdir-foundry`.
- `android/signing/foundry-debug.keystore`: a fixed debug key, public on purpose (see its README), so every build
  installs over the last.
- `android/scripts/`:
  - `fetch.sh`: shallow fetch of the pinned revision.
  - `apply.sh`: resets and cleans the checkout, applies the patches, copies `src/`, the overlay and mozconfig. **This wipes
    edits in the checkout.**
  - `build.sh`: bootstrap, `./mach build`, then `./mach gradle fenix:assembleDebug`.

Which Firefox files each patch edits, and which of Foundry's own files in `android/src/` belong to that feature. Paths are under `mobile/android/fenix/app/src/main/` unless they say otherwise; `java/...` is `java/org/mozilla/fenix`.

| Patch | Files | Change |
|---|---|---|
| 0001 branding-app-name | `res/values/static_strings.xml` | App name "Foundry" |
| 0002 bottom-toolbar-by-default | `java/.../utils/Settings.kt` | `shouldUseBottomToolbar` defaults to true |
| 0003 toolbar-swipe-gestures | `java/.../browser/BrowserFragment.kt`, `java/.../home/HomeFragment.kt`, `java/.../home/ui/HomeSwipeIntegration.kt` (own files: `java/.../components/toolbar/gestures/Foundry*.kt`) | The gesture system |
| 0004 foundry-name-and-logo | `res/values/strings.xml`, `res/values/styles.xml`, `res/values-night/styles.xml`, `res/values-night-v27/styles.xml` | English strings say Foundry instead of Firefox, except Mozilla-service, legal, sync and privacy strings; logo and wordmark attributes |
| 0005 taller-address-bar | android-components `compose/browser-toolbar/.../ui/FullDisplayToolbar.kt` and `BrowserEditToolbar.kt`; `res/values/dimens.xml` | 84 dp toolbar with a 60 dp pill |
| 0006 foundry-crash-log | `java/.../FenixApplication.kt`, `java/.../HomeActivity.kt` (own file: `java/.../foundry/FoundryCrashLog.kt`) | Records crashes and shows them with a Copy button on next launch |
| 0007 service-tabs | `res/navigation/nav_graph.xml`, `java/.../search/BrowserToolbarSearchMiddleware.kt`, `java/.../search/FenixSearchMiddleware.kt` (own files: `java/.../foundry/results/*`) | Searches open the Results screen (service tabs) |

### Making a change

1. Get a patched checkout. Run `android/scripts/fetch.sh` once per machine (about 2 min, about 1 GB). If the blobless
   fetch stalls, run `git fetch --depth 1 origin <rev>` inside `android/firefox` without `--filter`. Then run
   `android/scripts/apply.sh`.
2. Edit Foundry's own files in `android/src/` (then re-run `apply.sh`, or copy them over), and Firefox's files inside
   `android/firefox/`. Mozilla's own `CLAUDE.md` and `AGENTS.md` apply in there.
   `searchfox-cli` is usually not installed, so use `rg` limited to narrow paths.
3. If you changed Firefox's files, regenerate the patch that owns each one, from inside `android/firefox`. New files
   never go in a patch; put them in `android/src/`.
   ```bash
   g=mobile/android/fenix/app/src/main/java/org/mozilla/fenix
   git diff HEAD -- $g/browser/BrowserFragment.kt $g/home/HomeFragment.kt \
     $g/home/ui/HomeSwipeIntegration.kt > ../patches/0003-toolbar-swipe-gestures.patch
   ```
   Every Firefox file belongs to exactly one patch (see the table). A new area of work gets a new numbered patch.
4. Check that every patch still applies to an unmodified Firefox:
   ```bash
   tmp=$(mktemp); export GIT_INDEX_FILE=$tmp; git read-tree HEAD
   for p in ../patches/*.patch; do git apply --cached "$p" || echo "FAIL $p"; done
   unset GIT_INDEX_FILE; rm -f "$tmp"
   ```
5. Check it compiles with `./mach gradle fenix:compileDebugKotlin`, if a local build environment exists (see below).
   Otherwise CI is the compile check.
6. Commit only `android/patches`, `android/src`, `android/overlay`, the scripts and so on (never `android/firefox`),
   then push to `main`. CI builds the APK.

### CI and the APK

- `.github/workflows/android.yml` runs on pushes and PRs that touch `android/**` or the workflow. It fetches, applies,
  installs the fixed key, builds and uploads an artifact. On pushes to `main` it also publishes `foundry-arm64.apk` to the
  `nightly` release, replacing the old file. A newer push cancels a running build. A build takes 20 to 30 minutes.
- The owner downloads from this link, which needs no login:
  https://github.com/freakofntur/Founder-s/releases/download/nightly/foundry-arm64.apk
- Check the run for the current commit:
  `gh api "repos/freakofntur/Founder-s/actions/runs?head_sha=$(git rev-parse HEAD)&per_page=1" --jq '.workflow_runs[0] | [.id,.status,.conclusion]'`.
  Get a job's logs with `gh api repos/freakofntur/Founder-s/actions/jobs/<job-id>/logs`.
- Before telling the owner a build is ready:
  - Confirm the asset's `Last-Modified` is later than the push:
    `curl -sIL <link> | grep -i last-modified`.
  - Ideally, download the APK into an empty directory and grep its `classes*.dex` for a string from the change.
- The package (`org.mozilla.fenix.debug`) and signing key never change, so a new build installs over the old one with
  no uninstall.

### Local builds on the owner's Mac (Apple Silicon)

The owner also runs Claude Code on their Mac, with the repo at `~/AndroidProjects/Founder-s`. Everything is installed
in the home folder, with no Homebrew and no sudo:

- **Tools:**
  - JDK 21: `~/dev-tools/jdk`.
  - Android SDK: `~/Library/Android/sdk`, with platform-tools, platform 36, build-tools 36, the emulator and an
    `android-36` `google_apis` arm64 system image.
  - `gh`: `~/dev-tools/bin/gh`, logged in as `freakofntur`, and set up as git's credential helper.
  - The paths are exported in `~/.zshrc`.
- **Network:** the network is IPv6-only (Jio NAT64). The Mac shows `192.0.0.2`, and the phone shows `192.0.0.4`, which
  can't be reached.
  - `~/.gradle/gradle.properties` sets `java.net.preferIPv6Addresses=true` and runs the Kotlin compiler in-process,
    because the compile daemon can't connect.
  - The Gradle wrapper's download fails here, so use a local Gradle (`~/dev-tools/bin/gradle`) for non-Firefox
    projects.
  - `mach` replaces `JAVA_TOOL_OPTIONS`, so the Gradle wrapper inside `mach build` tries IPv4 and times out
    downloading its Gradle distribution. After a Firefox update that changes the Gradle version, fetch it once by hand:
    `cd android/firefox && JAVA_HOME=$(ls -d ~/.mozbuild/jdk/jdk-17*/Contents/Home) JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true ./gradlew --version`.
  - The blobless fetch in `fetch.sh` stalls on this network. Use a full shallow fetch instead:
    `git -C android/firefox fetch --depth 1 origin $(cat android/FIREFOX_REVISION) && git -C android/firefox checkout -f FETCH_HEAD`.
    Do this in a fresh `git init` directory. A checkout left over from a blobless fetch keeps `promisor` and
    `partialclonefilter` in its config, and goes on fetching blob by blob.
- **Devices:**
  - **Emulator:** the AVD is `Pixel` (Pixel 8, API 36). Start it with `~/dev-tools/bin/emu`; it shows up as
    `emulator-5554`. Use `-gpu swiftshader_indirect`: with `-gpu host`, Gecko's web content draws black. Launch
    Foundry with `am start -n org.mozilla.fenix.debug/org.mozilla.fenix.HomeActivity`; `monkey` can pick the debug
    build's LeakCanary launcher instead.
  - **Owner's phone:** a Nothing Phone (2) on wireless adb, because the USB cable is unreliable. Run
    `~/dev-tools/phone-connect.sh` before every install.
    - If it reports that the phone isn't connected, stop and ask the owner for the port shown on the phone's Wireless
      debugging screen, then run `phone-connect.sh <port>`.
    - The port changes whenever wireless debugging restarts.
    - The phone's IPv6 address is cached in `~/dev-tools/.phone-ip`.
  - With two devices attached, pass `adb -s <serial>`.
- **Fast loop (use this):** `android/scripts/dev.sh`. It syncs `android/src/` with checksums, so unchanged files keep
  their timestamps, and re-applies patches only when one changed. It runs bootstrap and `mach build` only the first
  time, then `./mach gradle fenix:installDebug` onto every connected device and launches Foundry. Pass `--no-install`
  to only build.
- **Gradle memory:** `~/.gradle/gradle.properties` overrides the project's settings, so it must keep Firefox's
  `-Xmx7g` heap. A 3 GB heap there made the Foundry build crawl.
- **Full build, install and launch** (slow; the same steps as CI):
  1. Build: `android/scripts/build.sh`. The APK lands under
     `android/firefox/objdir-foundry/gradle/build/mobile/android/fenix/app/outputs/apk`.
  2. Install with `adb -s <serial> install -r <apk>`.
  3. Launch with `adb -s <serial> shell monkey -p org.mozilla.fenix.debug -c android.intent.category.LAUNCHER 1`.
- **Crash logs:** read them straight from the device with `adb -s <serial> logcat -d -b crash`.

### Local builds in a Claude Code cloud sandbox

Fresh containers have no `android/firefox` and no `~/.mozbuild`. A local compile needs
`./mach --no-interactive bootstrap --application-choice mobile_android_artifact_mode` and then `./mach build`. These
sandbox-only workarounds are not in the repo:

- **Proxy for Gradle:** mach overrides `JAVA_TOOL_OPTIONS`, which drops the proxy settings.
  - Put proxy `systemProp.http(s).proxyHost` and `proxyPort` lines, and the same `-D` flags in `org.gradle.jvmargs`,
    into `~/.gradle/gradle.properties`.
  - The port changes every session. Update it with
    `P=${HTTPS_PROXY##*:}; sed -i -E "s/(proxyPort=)[0-9]+/\1$P/g" ~/.gradle/gradle.properties`.
- **Maven Central rate limits (HTTP 429):**
  - Add a `~/.gradle/init.d/` script that points Maven Central at
    `https://maven-central.storage-download.googleapis.com/maven2/`.
  - Run `export MOZCONFIG=$PWD/mozconfig` and
    `export GRADLE_MAVEN_REPOSITORIES="https://maven.mozilla.org/maven2/,https://maven.google.com/,https://maven-central.storage-download.googleapis.com/maven2/,https://plugins.gradle.org/m2/"`,
    then `./mach configure`.
- **TLS errors (PKIX):** import the proxy's CA certificate into the `cacerts` of the JDK that mach installs.
- **Stale "plugin not found" errors:** delete `~/.gradle/caches/modules-2/metadata-*`, `.gradle/configuration-cache`
  and `.gradle/mach-environment-cache`, then run `./gradlew --stop`.
- **Running builds:** run one Gradle build at a time. Send its output to a file and grep the file.
- **No device testing:** there is no KVM, so the emulator cannot run. The owner tests on their phone.
- **Low disk space:** delete `~/.mozbuild/android-device`, the system images and the emulator.

## Code conventions (Android)

- Kotlin, with warnings treated as errors. Follow Firefox's `AGENTS.md` and keep comments to a minimum: KDoc on
  classes, non-obvious parameters and constants, saying why rather than what.
- Gesture code lives in `org.mozilla.fenix.components.toolbar.gestures`, in files prefixed `Foundry`. Foundry-only
  infrastructure goes in `org.mozilla.fenix.foundry`.
- Distances are in dp (`*_DP` constants multiplied by density), like Android's own gesture thresholds. Constants live
  in a private companion object, each with a one-line KDoc.
- Keep edits to upstream files small, such as a call site or a default, and put the logic in Foundry files. Small
  patches survive Firefox updates.
- Commit messages: a subject like `Android: <what changed>`, and a body that explains why.

## Gesture system (current behaviour)

All gestures start on the bottom address bar (or the navigation bar). One handler, `FoundryToolbarGesturesHandler`,
owns them in the browser (`BrowserFragment.setupToolbarSwipeBehavior`) and on the home screen (`HomeSwipeIntegration`).

The direction is decided after 12 dp of movement (`DECIDE_DP`):

- Within 20° of horizontal (`SIDEWAYS_MAX_ANGLE`): back or forward.
- Steeper, moving away from the toolbar: the tab switcher. The exception is a swipe that starts in the system gesture
  area at the bottom of the screen, which belongs to Android's home gesture.

### The limit: `FoundrySwipeLimit` (the "eyebrows")

- Two arcs over the touch point, each the upper half of a circle beside it. Together they reach 176 dp sideways and
  dip to 94 dp straight up between them.
- Released under the arcs, nothing happens. Released past them, the action runs.
- Momentum counts: the release point is projected 0.15 s ahead using the finger's velocity.
- `isToTheSide`: a release past the limit with |dx| ≥ 64 dp counts as going to a side (the neighbouring tab). Anything
  else counts as going up the middle (the tabs tray).

### The guide: `FoundryBrowsGuide`, drawn on the window's decor view

- **Look:** shaded eyebrows.
  - A darker brow band whose top edge is exactly the limit, thick in the middle and tapering to a thin tail.
  - A lighter gradient shade covers the area under the brows.
  - Night mode uses a light shade.
  - The brows grow in from their tails when a swipe starts (220 ms). A haptic tick plays on crossing the limit.
- **Which brow reacts:** only the brow on the destination's side. Moving right brings in what is to the left (the
  previous page or the previous tab), so the left brow reacts.
- **Back or forward:** that brow fills with brand blue `#2B50E0` from its outer tail inwards, keeping pace with how far
  the finger has gone, like a loading bar. It fades once the finger is well past the limit.
- **Tab switcher:** the brows take on colour gradually, from 50% of the way to the limit until the limit. Both colour
  while heading up the middle (the tray). The colour shifts to only the destination's brow as the finger moves from
  24 dp to 64 dp sideways.

### Back and forward (like Safari on the Mac)

- The page (`swipeRefresh` in the browser, the home content on the home screen) moves 1:1 with the finger.
- An underlay behind it shows the snapshot of the previous or next history entry (`FoundryPageSnapshots`), with 0.3
  parallax.
- If there is nowhere to go, the page only gives a rubber-band pull (0.12).
- On commit:
  - The page animates off and `goBack` or `goForward` runs.
  - The snapshot stays on top until the new page has loaded (1.5 s timeout), then fades out.
- Never show white: a missing snapshot shows `FoundryPageSnapshots.placeholder`, the home background with the Foundry
  logo.

### Tab switcher: `FoundryTabSwitcher`, in the style of Android's recent apps

- **Dragging:**
  - The page shrinks into a card whose bottom edge stays under the thumb.
  - Shrinking starts after 16 dp and reaches 0.55 scale over 220 dp.
  - Up to four neighbouring tabs on each side sit beside the card, and moving sideways slides through them.
  - Titles show above the cards, over a dark scrim.
- **Limit on dragging:** the card cannot be pulled off the top of the screen. It stops under the status bar, with room
  for its title. Don't make it stop at its tabs-tray spot instead: that pinned the cards of later tabs to the bottom of
  the screen.
- **Outcomes (decided by the handler from the brows):**
  - Released under the brows: the page is restored.
  - Past a side brow: the neighbouring tab opens full screen.
  - Past the middle: the card shrinks into its estimated cell in the tabs-tray grid, then the tray opens. The estimate
    is 2 columns of 4:5 cells under an 88 dp header.
- **Card images:**
  - The current tab is captured fresh.
  - Other tabs use cached snapshots, falling back to Fenix thumbnails loaded at 0.6 of the screen size.

### Snapshots: `FoundryPageSnapshots`

- A 48 MB LRU cache of half-size bitmaps, keyed by tab id and history index and checked against that entry's URL.
- Taken 400 ms after each page finishes loading in the browser, and when a swipe starts.
- Skipped for home tabs and for engine views that are not attached, shown and laid out.

### Other UI changes

- The toolbar is at the bottom by default.
- The toolbar is 84 dp tall with a 60 dp pill, so its top is roughly level with the keyboard's space bar.
- Foundry name and logo: a white F on a blue rounded square.

## Decision log (confirmed by the owner; don't regress)

Oldest first.

1. **Basics:**
   - The toolbar is at the bottom by default.
   - A sideways swipe on the address bar means back or forward, not switching tabs.
   - A swipe up shows the tabs, and up plus sideways changes tab.
   - No Firefox branding: use "Foundry".
2. **Feel of the tab gesture:** it should feel like Android's recent apps with gesture navigation. The card shrinks
   under the thumb, and lifting high gives a tab overview like Chrome's.
3. **Basic feel:**
   - Momentum matters: a flick released early still counts.
   - No white pages.
   - Thresholds are in dp, like Android's, with some room before "up" starts.
   - Back and forward work like Safari on the Mac: the page follows the swipe and reveals the other page.
4. The sideways zone was narrowed to 20°.
5. **The visible limit:**
   - Wedges were tried and rejected ("the fade isn't hitting right").
   - Then an arc like cute anime eyes.
   - Now shaded eyebrows: the dark brow's top edge is the limit, with a lighter shade under it.
   - The limit must match the drawing exactly.
6. The address bar is taller: its top is level with the keyboard's space bar.
7. A flatter, hand-drawn brow shape was tried. The owner preferred the arch: keep the arch and keep the sizes.
8. **Brow feedback:**
   - Back and forward fill gradually like a loading bar, tied to the drag distance.
   - Changing tab lights up that side.
   - The colour must shift gradually from both brows to one side.
9. Back lights the **left** brow. The owner said the right brow glowing during back was wrong. Tabs follow the same
   destination rule; the owner has yet to confirm that.
10. The tab card must not leave the screen when dragged up. Releasing into tab view animates the card into its place
    there, like Chrome.

## Lessons (bugs we have hit)

- `Store.flowScoped(owner)` returns a scope that **you** must cancel; it is not tied to the lifecycle. Use
  `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { store.flow()... } }`.
- Never touch `binding` after a suspension point: `BaseBrowserFragment._binding` is set to null in `onDestroyView`. This
  crashed the app on every switch to a homepage tab.
- Selecting an `about:home` tab makes `BrowserFragment` navigate to `HomeFragment`, which destroys its views almost at
  once.
- Capturing a hidden or zero-size GeckoView can fail. Only capture views that are attached, shown and have a size.
- Debug builds run StrictMode with "penalty death" until the first fragment resumes, so main-thread disk I/O during
  startup kills the app ("Foundry needs to restart" on every launch). Even `context.filesDir` counts: it checks that
  the directory exists. Do the I/O on `Dispatchers.IO`, or wrap it in `StrictMode.allowThreadDiskWrites()` or
  `components.strictMode.allowViolation`. This covers `HomeActivity.onCreate`/`onResume` and `FenixApplication`.
- `Animator.cancel()` also runs `doOnEnd` callbacks, so guard end actions.
- A new debug key on every CI run made phones reject updates with "package appears to be invalid". The committed
  keystore fixed this.
- `git diff` ignores untracked files. This no longer matters for patches, because new files live in `android/src/`.
- The release asset can lag behind the push, so always check `Last-Modified`.

## Working with the owner

- **Keep Notion in sync with the work, always.** The owner's Notion page "Foundry browser" (use the Notion connector)
  is the shared vision board. Claude may edit it freely: add, change or remove features, and add screenshots and
  inspiration. Whenever work happens, update it in the same session so it matches reality:
  - **Feature list:** set each feature's **Status** (Idea → Next → Building → Testing → Shipped) as work moves. Add new
    ideas, planned work and future features as rows straight away, even if they are only an idea.
  - **Feature pages:** describe what was built or planned in plain words, add emulator screenshots of new UI, and list
    open questions for the owner as checkboxes.
  - **"Claude's notes" page:** keep the roadmap and decisions current, and add a dated line to the progress log at the
    end of each session.
  - When this file's "Where things stand", "Next up" or decision log changes, make the matching change in Notion.
- The owner tests on an Android phone (a Nothing Phone 2) with dark theme and gesture navigation, and replies with
  screenshots, often drawn on. They don't use GitHub's interface, so always give plain instructions.
  - **In cloud sessions:** give the direct nightly APK link.
  - **On the owner's Mac:** install straight onto the emulator or phone (see "Local builds on the owner's Mac"), and
    send emulator screenshots.
- Keep replies simple and direct. On the Mac, keep the project memory's `work-log.md` up to date: add a short dated
  entry after each piece of work.
- After pushing an Android change:
  - Say what changed in plain words.
  - Once CI finishes, follow up with the link, after checking the asset was updated.
- For visual changes, send a small to-scale SVG preview while the build runs. The owner has found these useful.
- If the app crashes, ask the owner to reopen Foundry, tap **Copy** in the "Foundry closed unexpectedly" box and paste
  the text.
- Ask before changing direction on something big. Small tuning of constants can go ahead directly. Describe new values
  in screen terms ("about a third of the screen width").

## Next up (the owner's list)

- **Service tabs results screen** (spec: `docs/design/service-tabs.md`):
  - An upper row of small cards (U1, U2…) and a lower row of AI chat cards (L1, L2…), each ranked separately.
  - Tapping a card opens it full screen, and the results screen stays open as a tab.
  - A follow-up typed in an AI card goes to that AI only.
- **Two menus**, whose positions may change:
  - `>`: profile, saved and active tabs, reading lists.
  - `⋮`: a traditional browser menu.
- Result priority using a small ML model (SLM) for ranking.
- **Later:**
  - Asking several AIs at once.
  - Rebranding the other languages.
  - An icon picker.

## Desktop prototype (Electron)

Run `npm install && npm start`, and `npm test` for the `node:test` suites. `README.md` covers the architecture and the
extension points: search providers live in `src/search/providers`, and internal pages use `founder://`. It is not being
developed right now.
