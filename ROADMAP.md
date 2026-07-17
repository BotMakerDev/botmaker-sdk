# ROADMAP

A running history of features and refactors for future Claude Code sessions. **Append here whenever
you add a feature or refactor** (this is required — see `CLAUDE.md` › Planning).

Format: newest first. Each dated entry has a **Done** list (what shipped) and, when relevant, updates
to **Deferred / next** (intentionally left for later, with enough context to pick up cold).

---

## 2026-07-18 — `Text` OCR facade (`api.vision.Text`)

**Done**
- **New `api.vision.Text` facade — on-screen text recognition, shaped exactly like `Pixel`.** Per-call
  `CaptureSource`, region via `source.region(...)`, results in **absolute** screen coords, parked in
  `VisionContext`, no-source overloads use `Source.current()`. The heavy lifting lives in
  `botmaker-shared`'s new `com.botmaker.shared.ocr.OcrEngine` (OpenCV preprocessing + Tesseract), so Studio
  can reuse OCR later without depending on the SDK. Surface:
  - `read(source[, opts])` — all recognized text as one string.
  - `find(needle, source)` (case-insensitive substring), `findExact`, `findMatching` (regex) — booleans that
    store the hit in `VisionContext.getLastTextMatch()`.
  - `findAll(needle, source)` / `readAll(source)` — counts, list in `VisionContext.getLastTextMatchList()`.
  - `waitFor` / `waitForGone` — poll loops identical to `Pixel`'s. Every call accepts an `OcrOptions` overload
    (languages, PSM, upscale, binarize, char whitelist). Default options read whole **lines** so multi-word
    phrases (`"Game Over"`) match by substring.
- **`api.vision.TextMatch`** — result type mirroring `ColorMatch`/`MatchResult` (package-private ctor,
  `notFound()` sentinel, `null` accessors when not found): `getText`, `getBounds` (absolute `Rect`),
  `getCenter`, `getConfidence`.
- **`api.vision.VisionContext`** — added a **separate** thread-local text slot (`lastTextMatch` /
  `lastTextMatchList` + getters/`ifLastTextMatch`/`clearLastTextMatch`), not shared with the template or
  colour slots, since bots interleave all three.
- **Bridge** maps shared `TextResult` (source-local box) → `TextMatch` (absolute via `source.origin()`),
  exactly like `Pixel.map(...)`. A genuine native-load failure surfaces as an `Error` (not caught), matching
  the rest of the vision layer.
- **Tests** — `TextTest` drives `Text` against a fixed-origin `CaptureSource` stub (rendered text) and
  asserts recognition, `VisionContext` storage, and absolute-coordinate mapping.

**Deferred / next**
- **Studio palette** — register `Text` in `palette/SdkApi.FACADE_CLASSES` and add palette blocks (a new
  facade needs registering; its methods are then auto-discovered). Not started.

---

## 2026-07-16 — `Game.kill` + name-based `Activity` control

**Done**
- **`Game.kill(name)` / `Game.isRunning(name)`** — cross-platform process control by executable name
  (Windows `taskkill`/`tasklist`, Linux/mac `pkill`/`pgrep`), best-effort and never throwing on "no such
  process". **Why:** the Firestone restart routine does `Process, Close, Firestone.exe` → relaunch, which the
  launch-only `Game` couldn't reproduce. Complements the existing window-based `isRunning(CaptureSource)`.
- **`Activity` self-registers by name; static `Activity.disable(name)`/`enable(name)`/`setEnabled(name,bool)`.**
  The constructor registers `this` in a static name→instance map, so any code can toggle an activity by name
  without a reference (one activity disabling another, or `GoHome`/`Startup` toggling one). Unknown name →
  stderr warning + no-op (never crashes a running bot). Instance `active()/setEnabled/enable/disable` unchanged.
  Package-private `clearRegistry()` for test isolation. **Why:** the Studio "disable this activity" self-call
  only worked inside an Activity and only self-targeted; the block now emits `Activity.disable("Name")` with a
  picker (see `../botmaker-studio/ROADMAP.md`).
- Tests: `GameTest` (kill/isRunning validation + no-throw on a bogus name); `ActivityTest` (static
  disable/enable by name toggles only that activity; unknown name warns + no-ops).

## 2026-07-16 — `Bot.stop()`: let the bot end cleanly

**Done**
- **New `Bot.stop()` ends the bot** — throws a **private nested** `BotStoppedException` (extends
  `RuntimeException`) which both `supervise` overloads catch *before* the `BotStuckException`/`RuntimeException`
  catches, log `[Bot] Stopped by request.` and **return** (not recover). The 3-arg cold-start try also honours
  it (a `stop()` during Startup/GoHome ends the bot before the loop). **Why:** `supervise`'s `while (true)` had
  no exit — once a bot disabled every activity, the loop spun forever with nothing to do ("the bot can't end").
  Exception (not a boolean body return) so `stop()` unwinds cleanly from arbitrarily deep in an activity's
  `run()`; nested-private so the only public surface is `Bot.stop()` (users never see/throw the exception),
  mirroring how `BotStuckException` is caught internally. `Activity.execute()` only catches `BotStuckException`,
  so the stop propagates through it untouched.
- Studio pairs this with an auto-`Bot.stop()` in the generated `GameLoop` (registry non-empty + no active
  activity) and a "Stop This Bot" palette block — see `../botmaker-studio/ROADMAP.md`.
- Tests: `BotTest` — `stop()` breaks the loop and `supervise` returns without recovering; a `stop()` during
  cold start ends the bot before the first body pass.

## 2026-07-16 — Cold-start launch sequence + runtime activity enable/disable + launch diagnostics

**Done**
- **`Bot.supervise(body, goHome, startGame)` now runs the start-up sequence once at launch** — `startGame()`
  then `goHome()` — *before* the first loop pass, reusing one shared `recovery` runnable. A cold-start failure
  routes through that same recovery instead of aborting the bot. **Why:** the 3-arg supervisor only ran
  GoHome/Startup *after* the loop threw, so "launch the game in Startup" never fired on a normal run (the
  reported bug: prints in Startup were silent while the activity `run()` printed fine). The 2-arg
  `supervise(body, recovery)` is unchanged.
- **`Activity` gained a runtime enable override** — nullable `enabledOverride` plus `setEnabled(boolean)`,
  `enable()`, `disable()`, and `active()` (the *effective* state: override if set, else the configured
  `isEnabled()`). The macro loop should consult `active()`, so a mid-run `disable()` actually stops the
  activity next pass (the reported "can't turn an activity off → GameLoop runs forever"). `isEnabled()`'s
  javadoc reworded to "configured default"; it stays the `Activities.<FLAG>` wiring.
- **`Game.launch`/`launchSteam` now log the exact command/URI they invoke** (`[Game] launch: …`,
  `[Game] launchSteam <id> → <uri>` + opener result) so a silent "nothing happened" launch becomes
  diagnosable in the Studio console. Behavior is otherwise identical.
- Tests: `BotTest` now asserts cold-start order (SHB / SHBHS / SHSB); new `ActivityTest` covers
  `active()`/`setEnabled`/`enable`/`disable`.
- **Release note:** generated bots pinned to a *released* SDK don't get cold-start or `active()` until this
  ships; the Studio GameLoop template emits `activity.active()`, which needs this SDK. Local
  `0.0.0-SNAPSHOT` dev runs pick it up immediately. Cut with `../release.sh` (shared unchanged → sdk → studio).

---

## 2026-07-15 — `Activity` names itself (no-arg constructor)

**Done**
- **`api/bot/Activity` gained `protected Activity()`**, defaulting `name` to `getClass().getSimpleName()`.
  Additive — `Activity(String)` stays for a name that shouldn't track the class name, and `name` stays final.
- **Why:** the only constructor was `Activity(String name)`, so every Studio-generated activity subclass had to
  carry a `public Mining() { super("Mining"); }` that restated the class name and asked the bot author for
  nothing. The Studio now generates a stub with `run()` and `isEnabled()` and no constructor at all; the
  generated `ActivityRegistry`'s `new Mining()` binds this inherited ctor.
- **Release note:** generated bots pinned to a *released* SDK can't use the constructor-less stub until this
  ships. Local `0.0.0-SNAPSHOT` dev runs pick it up immediately. Cut with `../release.sh` when ready.

---

## 2026-07-15 — Pixel colour detection (new `Pixel` facade)

**Done**
- **`api/vision/Pixel`** — the colour counterpart to `ImageFinder`, following the same conventions
  (`CaptureSource` per call, region-as-`source.region(...)`, absolute coords via `source.origin()`, results
  parked in `VisionContext`, no-source overloads use `Source.current()`). Surface: `colorAt`, `matchesAt`,
  `distance`, `find`, `findAll`, `findInRange`, `coverage`, `waitFor`, `waitForGone`.
- **Two precisions are separate knobs, deliberately.** `tolerance` is *colour* precision only — a **CIELAB
  ΔE76** distance (constants `EXACT`/`TIGHT`/`DEFAULT_TOLERANCE`/`LOOSE` = 0/5/12/25). *Location* precision is
  the searched region plus `minPixels`, the smallest connected blob that counts (kills stray anti-aliased
  pixels). Coupling the two into one knob is what makes Studio's old magic wand unusable; don't repeat it.
- **`api/vision/ColorMatch`** — mirrors `MatchResult` (package-private ctors, `notFound()` sentinel, null
  accessors when not found). Exposes `getCenter()` as the **centroid**, not the bbox centre — an L-shaped
  blob's bbox centre can lie outside the blob.
- **`internal/opencv/ColorMatcher`** + **`RawColorMatch`** (OpenCV-free, crosses internal/api like `RawMatch`).
  Pipeline: BGR → **float** Lab → per-pixel ΔE → threshold → `connectedComponentsWithStats` → `minPixels`
  filter → largest-first. The float conversion matters: converting an 8-bit image via `COLOR_BGR2Lab` gives L
  rescaled to 0..255 and a/b offset by 128, so the distances would not be ΔE.
- **`VisionContext`** gains `getLastColorMatch` / `getLastColorMatchList` / `lastColorMatchFound` /
  `clearLastColorMatch` / `ifLastColorMatch`. Colour results use their own thread-locals rather than sharing
  the template slots — bots interleave the two and would otherwise clobber each other.
- Studio: `SdkApi.FACADE_CLASSES` gains `Pixel` (a new *facade* needs registering; new *methods* on an
  existing facade do not — ClassGraph discovers those at runtime).

---

## 2026-07-14 — Bot lifecycle: supervisor + watchdog + Activity (new `api.bot`)

**Done**
- **New package `com.botmaker.sdk.api.bot`** with the runtime primitives a game bot needs:
  - **`BotStuckException`** (first custom exception in `api.*`) — unchecked; thrown by the watchdog, caught
    by the supervisor.
  - **`Watchdog`** — stuck detector. Piggybacks on existing `api.observe` match telemetry: while enabled it
    installs a `BotObserver` that counts consecutive identical match *signatures* (`templateId + coarse
    location`, or `"miss"`). The observer only counts (never throws — `ImageFinder.findInternal` swallows
    `Exception`); the throw is deterministic at `checkpoint()` once `repeats >= ClickConfig.MAX_RETRY_ATTEMPTS`.
    `progress()`/`reset()` clear the per-thread counter.
  - **`Bot.supervise(body, recovery)`** / `supervise(body, goHome, startGame)` — the outer restart loop:
    runs `body` forever, catches `BotStuckException`/`RuntimeException`, resets the watchdog, runs recovery.
  - **`Activity`** — abstract base a bot author subclasses per game task: `isEnabled()`/`run()` abstract,
    `before()`/`after()`/`onStuck()` overridable no-ops, `final execute()` orchestrates them. Studio
    generates one subclass per activity and a registry of instances.
- **`ClickConfig.MAX_RETRY_ATTEMPTS`** is now genuinely live (the watchdog's threshold); javadoc updated.
- Tests: `api/bot/WatchdogTest` (counter → checkpoint throws / resets) and `api/bot/BotTest` (supervisor
  recovery ordering) — standalone, no OpenCV/screen.
- Studio mirror: added `Bot`, `Watchdog` to `palette/SdkApi.FACADE_CLASSES` (`Activity` is a base class, not
  a static facade, so it's intentionally not listed).

## 2026-07-14 — Transparent-background templates: alpha-as-mask matching

**Done**
- **`ImageTemplate.getMat()` loads with `Imgcodecs.IMREAD_UNCHANGED`** so a transparent PNG keeps its alpha
  channel (4-channel BGRA); opaque PNGs still load as 3-channel BGR.
- **`OpencvManager` uses the alpha channel as a match mask.** New `extractAlphaMask` + `runMatch` helpers:
  when a template carries alpha, transparent pixels are ignored via `TM_CCORR_NORMED` with a mask (the
  reliably mask-supporting normed method); opaque templates keep `TM_CCOEFF_NORMED`. Threaded through
  `matchScaled`, `findMultipleMatches`, and `scoreAround`. This makes Studio's new "Capture object"
  (transparent-background) templates actually match regardless of the scene behind the object.
  Note: masked scores use CCORR_NORMED, so the 0.8 default threshold reads slightly differently for
  transparent templates. New `MaskedMatchTest` covers the path.

---

## 2026-07-11 — Game window-detection takes `CaptureSource`; add `ImageFinder.findAnyCompare`/`findAllCompare`

**Done**
- **`Game` window-detection now uses `CaptureSource`, not a bare window-title `String`** (so the Studio
  offers the visual capture-source picker instead of a free-text field, matching the vision API):
  `isRunning(CaptureSource)`, `waitForLaunch(CaptureSource, long)`,
  `launchIfNotRunning(String executablePath, CaptureSource, String... args)`,
  `launchSteamIfNotRunning(String appId, CaptureSource)`,
  `launchAndWait(String executablePath, CaptureSource, long timeout, String... args)`. The executable path
  and Steam appId are now the **first** parameter. Still window-based (no process detection yet).
- **Capture-layer presence**: new `CaptureSource.isPresent()` (default `true`; desktop/monitor are always
  present). New lazy `NamedWindow` source — `CaptureSource.window(title)` now returns it instead of eagerly
  resolving + falling back to `desktop()`. It re-resolves `Window.find(title)` on every use, so a
  window source survives the window not existing yet (before launch) and reports `isPresent()` correctly;
  `Game`'s detection delegates to it. Also makes ordinary vision matching more robust (re-binds a moved/
  reopened window per frame). `GameRunningTest` updated to drive `CaptureSource.window(...)`.
- **New compare finders** on `ImageFinder`: `findAnyCompare(good, bad[, source][, margin])` → `boolean`
  (first good template, in order, that beats every bad by the margin) and
  `findAllCompare(...)` → `int` (every winning good location; stored in `VisionContext` last-match-list).
  Mirror the existing `findCompare` overload set; new private `compareAny`/`compareAll` + shared
  `beatsAllBads` helper (which `compare` was refactored to reuse). `ImageClicker.clickCompare*` untouched.

## 2026-07-11 — Stamp the jar manifest with a build identifier

**Done**
- `pom.xml` now runs `maven-jar-plugin` with `addDefaultImplementationEntries` + a `Build-Time`
  manifest entry (`${maven.build.timestamp}`, ISO-8601). The pom `version` is cosmetic (`0.0.0-SNAPSHOT`
  locally; JitPack overrides it with the tag), so `Build-Time` is the signal that distinguishes local
  rebuilds that all reuse the same `botmaker-sdk-0.0.0-SNAPSHOT.jar` file name. Studio reads it at project
  open to report which SDK build the editor indexed — closes the "no way to tell which SDK a bot loaded" gap
  behind the stale-editor-cache bug fixed in the Studio ROADMAP (same date).

## 2026-07-11 — Fix `steam://` launch opening a blank browser page

**Done**
- `UriLauncher.open` no longer routes custom protocol schemes through `Desktop.browse`. On Windows
  `Desktop.browse("steam://…")` handed the URI to the default *browser* (blank page) instead of Steam. Now
  only `http`/`https`/`file` URLs use `Desktop.browse`; everything else goes straight to the OS protocol
  handler. The Windows native opener switched from `rundll32 url.dll,FileProtocolHandler` to `explorer.exe`
  (ShellExecute — the reliable way to invoke a registered protocol handler). `Game.launchSteam`'s CLI
  fallback is unchanged.

## 2026-07-10 — Compare API trim, click Any/All Compare, Game running-detection, per-template resolution

**Done**
- **Trimmed the Compare surface.** Removed every `clickCompare`/`findCompare` overload that took a solo
  `ImageTemplate` (`ImageClicker`, `ImageFinder`); the `ImageTemplateGroup` overloads remain the single
  Compare shape. This is an intentional breaking API change (no bot consumes the SDK yet).
- **New `ImageClicker.clickAnyCompare` / `clickAllCompare`** over `ImageTemplateGroup` (with
  `CaptureSource` + `margin` overloads). `clickAnyCompare` clicks the first good in group order that beats
  the bad set; `clickAllCompare` clicks every winning location and returns the count. Backed by new private
  `compareAnyInternal` / `compareAllInternal` (+ shared `beatsAll` neighbour-scoring helper).
- **`Game` running-detection & wait** (window-title based, via `NativeControllerFactory.get()
  .getAllWindows()`): `isRunning(title)`, `waitForLaunch(title, timeoutMs)` (~250ms poll),
  `launchIfNotRunning(...)`, `launchSteamIfNotRunning(...)`, `launchAndWait(...)`. Avoids relaunching an
  already-running game and lets a bot block until the game window appears. `launch`/`launchSteam` unchanged.
- **Per-template capture resolution.** Resolution-independent matching now prefers each template's own
  authored resolution over the project-wide `ProjectDefaults.defaultResolution()`. `ImageTemplate
  .captureResolution()` lazily reads `captureWidth`/`captureHeight` from a `<name>.json` sidecar (written by
  Studio; best-effort, null when absent). Threaded through `ResolutionScaler.primaryScale(live, authored)`
  and new authored-aware overloads of `OpencvManager.findBest`/`findBestMatch`/`findMultipleMatches`;
  templates without a sidecar keep the previous project-wide behaviour.

## 2026-07-10 — Verify `Game.launchSteam`

**Done**
- Reviewed `api/launch/Game.launchSteam(int)` → delegates to `launchSteam(String)`, which opens
  `steam://rungameid/<id>` via `UriLauncher` (Desktop.browse → `xdg-open`/`open`/`rundll32` fallback) and
  then falls back to the `steam -applaunch <id>` CLI. URI + fallback are correct; kept the numeric overload
  as a documented convenience (no signature change).
- Added `api/launch/GameTest` pinning the reject-empty-input contract for `launch` and `launchSteam`
  (String + numeric overload) — deliberately does not perform a real launch (no process/Steam spawned in CI).

## 2026-07-09 — API cleanup: global Source, concrete capture sources, Mouse ergonomics, resolution-independent matching

**Done**
- **Global ambient capture source.** New `api.capture.Source` holds a mutable global `current()`
  source; `Source.set(CaptureSource)` overrides it until changed. Every no-source overload in
  `ImageFinder`/`ImageClicker`/`ImageWaiter` now resolves through `Source.current()` instead of the
  inlined `CaptureSource.desktop()`. `current()` initialises lazily to the **project default source**
  (`internal.config.ProjectDefaults`, read from the classpath resource `/botmaker-project.properties`
  that Studio bakes into a bot), falling back to the whole `Desktop` when unset.
- **Concrete capture-source hierarchy.** Added `api.capture.Desktop` and `api.capture.Monitor`
  implementing `CaptureSource` (replacing the anonymous inner classes `Screen.asSource()` /
  `Screen.monitorSource(int)`, now removed). `CaptureSource.desktop()`/`monitor(int)` construct these;
  `Screen` remains the low-level static desktop-capture utility.
- **Mouse ergonomics.** Added `down(MouseButton, Point)` (move-then-press), `drag(Point, Point, long
  durationMs)` (timed interpolated drag), `move(int, int)` (merged with `move(Point)`; `moveTo(int,int)`
  removed), and `scrollUp(int)`/`scrollDown(int)` helpers with a clearer `scroll(int notches)` sign doc.
- **Vision facade aligned.** Removed `find(ImageTemplateGroup ...)` overloads (find is single-template);
  added `findAny(ImageTemplateGroup ...)` and `findAll(ImageTemplateGroup ...)`; removed
  `findBest(ImageTemplate ...)` (redundant — `find` already returns the best single-template match),
  keeping `findBest(ImageTemplateGroup ...)`. `ImageClicker.clickBest(ImageTemplate)` now delegates to
  `find`.
- **Resolution-independent template matching.** `internal.opencv.ResolutionScaler` derives a primary
  scale = `liveCaptureSize / projectDefaultResolution` (from `ProjectDefaults.defaultResolution()`;
  `1.0` when unset or implausible). `OpencvManager` resizes the template by that scale before matching:
  `findBest` (single scale for near-miss telemetry), `findBestMatch` (primary scale, then a small
  fallback pyramid ±10–20% **only on a threshold miss**, early-out on a hit), and `findMultipleMatches`
  (primary scale). Pre-existing pixel-exact behaviour is preserved when no default resolution is set.

**Deferred / next**
- **Studio side of resolution independence:** UI to set the project default capture resolution and to
  write `capture.width`/`capture.height` (+ `capture.source`) into `/botmaker-project.properties` of a
  generated bot. Until Studio writes it, the SDK falls back to native scale (no behaviour change).

## 2026-07-08 — Report best score on a template-match miss + SNAPSHOT shared pin

**Done**
- **Confidence is no longer always `0` on a miss.** `OpencvManager` split into `findBest(...)` (returns the top
  `TM_CCOEFF_NORMED` peak regardless of threshold; `null` only when the template can't fit) and
  `findBestMatch(...,threshold)` (unchanged gate, delegates to `findBest`). `ImageFinder.find` emits
  `MatchResult.miss(bestScore)` on a below-threshold miss so the telemetry Match carries the real near-miss
  confidence (e.g. `0.77`) — explaining "detected half the time" as a score straddling the threshold. The public
  find contract is unchanged (`isFound()` still `false`, click points still `null`); no telemetry wire change.
- **`botmaker.shared.version` committed value is now `0.0.0-SNAPSHOT`** (was a pinned tag). The real shared tag
  is resolved and injected at build time by `jitpack.yml` (`-Dbotmaker.shared.version=<newest v* tag>`); the
  committed pom is never edited by `release.sh`.

**Deferred / next**
- Consider surfacing the near-miss best score on the public API (today it's telemetry-only via `MatchResult.miss`).

## 2026-07-08 — CaptureSource redesign: three kinds, region-as-modifier, full method coverage

**Done**
- **`CaptureSource` is now exactly one of three, on one class:** `CaptureSource.desktop()` (whole virtual
  desktop, replaces `screen()`), `CaptureSource.monitor(int)` (a single screen, replaces `Screen.at(int)` as
  the public factory), `CaptureSource.window(String)`. `Screen.at` → internal `Screen.monitorSource(int)`
  backing `monitor(int)`. **Breaking** (sanctioned — early dev): callers of `CaptureSource.screen()` /
  `Screen.at(i)` move to `desktop()` / `monitor(i)`.
- **Region is a modifier on a source, not a separate parameter:** `source.region(Rect)` / `region(x,y,w,h)`
  returns a sub-source that actually **crops** `capture()` to that rect (in the source's own pixel space) and
  shifts `origin()` — so it both restricts the search area (fixes the old offset-only `region` that never
  cropped) and keeps absolute click coords. Regions compose.
- **Vision facades collapsed to `(template, CaptureSource[, double confidence])`.** Dropped every bare `Rect
  region` overload across `ImageFinder`/`ImageClicker`/`ImageWaiter`; each op now has a whole-desktop default
  + a `CaptureSource` form + optional trailing confidence. Uniform source coverage by construction — notably
  **`ImageClicker.clickCompare` gained CaptureSource overloads** (previously zero, though `findCompare` had
  them), plus the missing `ImageWaiter`/`findAny` forms.
- **`Mouse.click(CaptureSource src, int x, int y)`** — plain click at `src.origin() + (x,y)`: a fixed point
  inside a window/monitor/region, monitor-independent.
- **Observability preserved without a region param:** added `CaptureSource.base()` / `subRegion()` hooks;
  `ImageFinder` emits `MatchEvent(Surface.of(source.base()), source.subRegion(), result)` so overlays still
  know the window/screen + searched sub-rect.
- Supersedes the prior additive-overload entry below (which piled Rect+source overloads on); those Rect
  overloads are now gone in favour of `source.region(...)`.

**Deferred / next**
- Studio-side visual rubber-band region selection (interim: numeric x/y/w/h entry in the capture chooser).

## 2026-07-08 — Full CaptureSource overload coverage + `CaptureSource.window`

**Done**
- **`CaptureSource.window(String titleSubstring)`** — new static factory returning the first window whose
  title contains the substring as a `CaptureSource`, or the whole `screen()` if none matches (unwraps the
  `Window.find` `Optional` in one call). Studio now emits this inline instead of a generated `BotConfig`
  helper (the `BotConfig.java` sidecar was dropped Studio-side).
- **`ImageFinder` — CaptureSource-targeted overloads for the whole family**: `findAll`, `findAny`, group
  `find`, `findBest` (single + group), `findCompare` (single/varargs/group), `exists`/`notExists`/`existsAny`
  /`existsAll` (single + group), and the `if`/`while`/`untilExists*` lambda control-flow (single + group).
  Previously only `find`/`findAll` accepted a source. All additive, routed through the existing source-aware
  cores (`find(t,source,region,conf)`, `findAll(...)`, `compare(...)`), so coordinates stay absolute.
- **`ImageClicker` / `ImageWaiter`** — matching source overloads for `click`/`clickBest`/`clickAny`/`clickAll`
  and `waitFor`/`waitUntilGone`/`waitAndClick`, so a targeted window/monitor can drive click + wait blocks too.
- No existing public signature changed. Released bots need this SDK version to use the new source overloads;
  Studio's capture-source picker now attaches to every `CaptureSource`/`Window` parameter across these.

## 2026-07-08 — Telemetry carries the bot source line

**Done**
- `internal/observe/IpcObserver` now stamps each `Match`/`Click` telemetry event with the bot's source line
  (`botLine()` walks the current stack for the first non-`com.botmaker.*`/non-JDK frame — the user's bot
  class). Uses the shared wire v2 field (see `../botmaker-shared/ROADMAP.md`). Enables the Studio's
  running-block highlight on a plain run. Additive; a bot with no Studio attached still registers no observer.

## 2026-07-07 — Per-monitor CaptureSource (`Screen.at`)

**Done**
- **`Screen.at(int index)`** — a new public `CaptureSource` for a single monitor (0-based index into the OS
  screen-device list), so a bot can match against just one screen on a multi-monitor desktop instead of the
  whole virtual desktop (`CaptureSource.screen()` / `Screen.asSource()`). `origin()` is the monitor's
  top-left in virtual-screen space, so in-image matches convert to absolute clickable coordinates as usual.
- **`internal/capture/ScreenCapture`** gained `monitorBounds(int)` (AWT device bounds, falls back to the
  virtual desktop for an out-of-range index) and `captureMonitor(int)` (crops the single `captureDesktop()`
  grab to those bounds — one backend selection, no second capture path).
- Consumed by the Studio's generated `BotConfig.screen(index)` helper (capture-source picker → block code).
  Additive only; no existing public signature changed. Released bots need this SDK version for a
  specific-screen block; local dev picks it up via `./dev-install.sh`.

## 2026-07-07 — Observer SPI + Studio telemetry bridge

**Done**
- **New public, Studio-agnostic observability SPI** (`com.botmaker.sdk.api.observe`): `BotObserver`
  (default no-op `onMatch`/`onClick`), event records `MatchEvent`/`ClickEvent`, a `Surface` target
  (window title+bounds, or screen; `Surface.of(CaptureSource)` resolves it), and the `Bots` registry
  (copy-on-write `add/removeObserver`, `hasObservers`, `fireMatch`/`fireClick`). A first-class feature
  usable standalone — log actions, assert on them in tests, drive custom tooling. `Bots` is the one bit
  of static facade state (a deliberate, documented exception to the stateless-dispatcher style).
- **Emit sites** in the vision/interaction facades, each guarded by `Bots.hasObservers()` so a normal run
  builds/pays nothing: `ImageFinder.find(...)` (found + not-found), private `compare(...)`, `findAll(...)`
  (one event per match, or a not-found), and `ImageClicker.click`/`clickResult`/`clickAll` (left-click).
- **Internal, env-gated Studio bridge** `internal/observe/IpcObserver`: translates SDK events →
  `com.botmaker.shared.ipc.TelemetryEvent` and ships them via `TelemetryClient.fromEnvironment()`. It
  self-installs only when `BM_IPC_PORT` is set (Studio-launched). `Bots` loads it *by name* in a static
  block, so the public API keeps zero compile-time dependency on the bridge/socket — a bot never needs
  the Studio, and outside it no observer registers and no socket opens.
- Tests: `api/observe/BotsTest` (registration, fan-out, error isolation, `Surface`). Real end-to-end
  (find→click overlays) is validated with the Studio's stub bot.
- **`dev-install.sh` now routes a local SDK build to the local `botmaker-shared` build.** It rewrites
  `botmaker.shared.version` → `0.0.0-SNAPSHOT` in the temporary pom (restored on exit), alongside the
  existing groupId/version rewrites. Without this, once a release pins the property to a real tag (e.g.
  `v0.0.2`) a `local-SNAPSHOT` SDK build silently pulled shared from JitPack, so local shared changes were
  ignored. Studio also now auto-lists the installed `local-SNAPSHOT` in its version dropdown (see Studio
  ROADMAP), so there's nothing to type.

**Deferred / next**
- Optional raw `Mouse.click` (bare point, no template context) click events — left out for now.
- The SDK pom still pins `botmaker.shared.version` to the released tag; the umbrella `release.sh` bumps it
  to the new shared tag that carries the `ipc` package when this ships. For local dev build with
  `-Dbotmaker.shared.version=0.0.0-SNAPSHOT` after `botmaker-shared/dev-install.sh`.

## 2026-07-06 — Group/`Any`/`All` variants for the loop & existence helpers

**Done**
- **Lambda control-flow over an `ImageTemplateGroup`** (`ImageFinder`) — `whileExistsAny`/`ifExistsAny`
  hand the action the first visible match (`Consumer<MatchResult>`, first-match via `find(group)`);
  `whileExistsAll`/`ifExistsAll`/`untilExistsAny`/`untilExistsAll` take a `Runnable` ("all present" /
  "waiting" has no single meaningful match, mirroring `untilExists`). All are one-capture-per-check
  like the single-template originals.
- **Group/`All` existence booleans** — `exists(ImageTemplateGroup)` (any, first-match),
  `existsAll(ImageTemplate...)` and `existsAll(ImageTemplateGroup)` (every one visible; empty input is
  false), `notExists(ImageTemplateGroup)`. Complements the pre-existing `existsAny(...)`.
- **`findCompare(good, bad)` Javadoc** now documents `@param good`/`@param bad` (Studio surfaces these
  as argument labels + the "learn about it" description).
- All additive — no existing public signature changed. Tests: `ImageFinderGroupTest` (headless-safe
  `existsAll()` empty guard; screen-dependent paths stay in the manual `Main` harness).

**Deferred / next**
- `ImageWaiter.waitForAny/waitForAll/waitUntilGone` group overloads (nice-to-have; `ImageClicker`
  already covers group clicking).

## 2026-07-05 — Multi-template vision: `ImageTemplateGroup`, best-match, compare

**Done**
- **New value type `ImageTemplateGroup`** (`api.vision`) — immutable, non-empty, ordered wrapper
  around `List<ImageTemplate>` with `of(ImageTemplate...)` / `of(List)` factories and `toArray()`.
  Serves as the first-class multi-template value (Studio detects it as a special type → dedicated
  list picker).
- **`ImageFinder.find(ImageTemplateGroup)`** (+ region/confidence) — first-match over the group
  (delegates to `findAny`; keeps cheap short-circuit). Mirror `ImageClicker.click(group)`.
- **Re-introduced `findBest`/`clickBest`** (previously deleted 2026-07-03) with clearer semantics:
  exhaustive highest-score match. Overloads for a **single `ImageTemplate`** (returns the global
  argmax vs. `find`'s first-acceptable) and for an **`ImageTemplateGroup`** (best score across all
  templates).
- **`findCompare`/`clickCompare`** — a "good" template must out-score similar "bad" variants (other
  in-game states of the same element) **at the same location** by `ClickConfig.DEFAULT_COMPARE_MARGIN`
  (0.05). Overloads: `(good, bad)`, `(good, bad...)`, `(ImageTemplateGroup good, ImageTemplateGroup bad)`
  (+ region/margin). Single-capture: one screenshot, good located via `findBestMatch`, each bad
  re-scored in a padded window at good's location via new internal `OpencvManager.scoreAround(...)`.
- **Tests:** `ImageTemplateGroupTest` (guards/immutability), `ScoreAroundTest` (synthetic
  good-vs-distractor ranking, self-contained — no fixture image needed).

**Note:** the beta status let us add these to the public `api.vision` surface freely.

## 2026-07-03 — Vision API simplification

**Done**
- **Collapsed `api.vision` from 9 classes to 3 action classes** (+ the unchanged value/config types
  `MatchResult`/`ImageTemplate`/`ClickConfig`):
  - **`ImageFinder`** — now owns single-frame lookup *and* existence: `find`/`findAll`/`findAny`, the boolean
    `exists`/`notExists`/`existsAny` (moved from `ImageMatcher`), and lambda control-flow `whileExists` /
    `ifExists` (take `Consumer<MatchResult>` — one capture per check, hands the action the live match) and
    `untilExists` (takes `Runnable`, since no match exists while the template is absent).
  - **`ImageClicker`** — trimmed to `click`/`clickAny`/`clickAll`.
  - **`ImageWaiter`** — unchanged: `waitFor`/`waitUntilGone`/`waitAndClick`.
- **Deleted** `Vision` (+ `evaluate`/`snapshot`), `ImageState` (+ `ScreenState`), `ImageMatcher`, and the
  `…then…`/long-tail variants (`clickBest`, `clickFirst`, `clickUntilSuccess`, `clickWhileVisible`, `findBest`,
  `retryUntilFound`, `clickAndThen`, `clickThenWaitFor`, `waitForGoneThenClick`, `clickOrWaitAndClick`). The
  multi-template single-capture branch (`Vision.evaluate`/`ScreenState`) is replaced by the `whileExists`-style
  lambdas; deleted `VisionEvaluateTest`.
- **Callers updated:** `capture/CaptureSource` + `capture/Window` javadocs no longer reference
  `Vision`/`ImageState`; `Main` unaffected. Studio drops `ImageMatcher`/`ImageState` from its SDK-facade list.

## 2026-07-02 — Game launch API

**Done**
- **`api.launch.Game` facade.** New public static entry point exposed as Studio blocks:
  `Game.launch(String executablePath, String... args)` starts any executable via `ProcessBuilder`
  (detached), and `Game.launchSteam(String appId)` / `launchSteam(int)` hands a Steam appId to the
  local Steam client via the cross-platform `steam://rungameid/<appId>` URL, falling back to
  `steam -applaunch <appId>`. Launching a Steam game needs no login of ours — the installed, signed-in
  Steam client owns the session; we never touch credentials.
- **`internal/launch/UriLauncher`.** Small OS URL-handler opener (Desktop.browse → `xdg-open`/`open`/
  `rundll32`), mirroring the Studio's `util.BrowserLauncher` (duplicated because the SDK can't depend
  on the Studio). Used by `launchSteam` to invoke `steam://` URLs.

**Deferred / next**
- No "wait for game window" helper yet — the vision blocks (`ImageWaiter`) already poll for on-screen
  templates, which is how a test bot detects the game is up.

## 2026-06-30 — Linux click support

**Done**
- **Linux click works.** `api.interaction.Mouse.click` now routes through
  `internal/capture/core/NativeControllerFactory.get()` instead of calling the Windows-only
  `internal/capture/Clicker` (JNA `User32`) directly. This fixes
  `UnsatisfiedLinkError: Unable to load library 'user32'` on Linux, which surfaced whenever a click
  actually fired (i.e. when `ImageFinder` found the template on screen). Windows path is unchanged
  (factory → `WindowsController` → `Clicker` → `User32 PostMessage`).
- **Multi-monitor coordinates fixed.** Added `api.capture.Screen.captureOrigin()` (the virtual-screen
  origin from `ScreenCapture.getVirtualScreenBounds()`). `ImageFinder.find/findAll` and
  `ImageState.findWhichAreVisibleDetailed` now add this origin to match coordinates, so reported
  points are **absolute** screen coordinates. Previously they were image-local, so clicks landed off
  by the virtual origin whenever a monitor was placed left/above the primary (negative origin).
  Note: `ImageFinder.find/findAll` now return absolute coords (corrected contract).
- **Cursor save/restore on Linux click (X11 only).** `LinuxController.postLeftClickScreen` reads the
  pre-click pointer via X11 `XQueryPointer` (added the binding in `linux/X11.java`) against the
  default root window — same coordinate space as `XTestFakeMotionEvent` — and warps back after the
  click. Gated on `WAYLAND_DISPLAY == null`; skipped under Wayland (see below).

**Deferred / next**
- **Click without disturbing the cursor on native Wayland.** Under native Wayland the JVM is an
  XWayland client: it can *write* the pointer (warp/click via XTest) but **cannot read** the global
  cursor position, so save/restore is impossible there (it would teleport the cursor to a stale
  constant). Current interim: on Wayland we skip restore and leave the cursor on the click target.
  Proper fix: implement input injection via the **xdg-desktop-portal RemoteDesktop** interface
  (libei / PipeWire) — this is also what raises the one-time "allow control of pointer/keyboard"
  prompt. Alternative (weaker): window-relative motionless click via `XSendEvent`, but many apps
  ignore synthetic events and it can't reach native Wayland windows. Likely belongs alongside the
  capture backends as a new injection strategy.
- **GNOME/sway capture.** Add a portal/PipeWire `CaptureBackend` and wire into
  `CaptureBackend.select()` (noted in `CLAUDE.md` › Screen capture).
