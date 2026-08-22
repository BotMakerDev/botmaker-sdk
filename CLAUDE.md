# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The **BotMaker SDK** is the runtime library that user bots compile against. The sibling
**botmaker-studio** app (`../botmaker-studio`) generates user projects that depend on this SDK and
call its public `com.botmaker.sdk.api.*` facades. The SDK itself depends on **botmaker-shared**
(`../botmaker-shared`, cross-platform native window plumbing).

## Planning

For large changes, write the plan to a dedicated plan file before starting implementation, so work
can be resumed if a session is interrupted.

**Always update `ROADMAP.md` whenever you add a feature or refactor code** — append a dated entry
under "Done" (and add/adjust "Deferred / next" items as needed). It is the running history future
sessions rely on to understand what changed and what's intentionally left for later.

## Commands

```bash
mvn compile        # Build
mvn test           # Run tests (JUnit Jupiter)
mvn install        # Install to the local Maven repo (umbrella coordinate); for a generated bot to pick
                   #   up local changes use ./dev-install.sh instead (see Publishing › Local dev)
```

There are no `main`-method entry points. The module is a library: everything under `src/main` is
reachable from a generated bot, and everything that verifies it is JUnit under `src/test`. The five
manual harnesses that used to sit in `internal/` (and a hello-world `Main` that shipped in the
published jar) were deleted in 2026-07 — they were unreferenced, untested, and on every bot's
classpath. A new diagnostic goes in `src/test` with the JUnit the rest of the module uses.

## Publishing

The SDK is consumed by the **bot projects that Studio generates** (not by Studio itself), via JitPack as
`com.github.LiQiyeDev:botmaker-sdk:<tag>`. JitPack builds each git tag on demand and serves it under that
`com.github.LiQiyeDev` coordinate regardless of this pom's `groupId`/`version` (so the pom `version` is
cosmetic). **The maintainer owns the SDK → JitPack publish — don't push or publish the SDK yourself;**
releases are cut from the umbrella with `../release.sh`.

### Local dev (test SDK changes without pushing a tag)

> The old `dev-install.sh` script was removed and is **no longer needed**: this module's pom `groupId` is now
> `com.github.LiQiyeDev` (matching the coordinate JitPack serves), so a plain `mvn install` already lands the
> SDK at the coordinate a generated bot resolves. The whole `local-SNAPSHOT` / `-Dbotmaker.shared.version`
> dance the old script automated is obsolete.

A generated bot pins `com.github.LiQiyeDev:botmaker-sdk:<version>`, and the local `~/.m2` is checked before
JitPack. To make a bot pick up your local SDK changes, install both shared and the SDK to `~/.m2` at
`0.0.0-SNAPSHOT` in one shot from the umbrella root:

```bash
mvn -pl botmaker-sdk -am install     # builds+installs shared (-am) then the SDK, both at 0.0.0-SNAPSHOT
```

`-am` ("also make") builds the SDK's reactor dependency `shared` first, so both land at `0.0.0-SNAPSHOT`
(the version every consumer defaults to via `${botmaker.shared.version}`), and the installed SDK depends on
that local shared. Re-run it after each SDK edit; a bot pinned to `0.0.0-SNAPSHOT` resolves the freshly
installed jar on its next classpath resolve.

You never type the version into Studio: it auto-lists locally-installed `*-SNAPSHOT` SDK builds (newest
first) at the top of the SDK version dropdown (New Project and Project ▸ Manage Libraries), labeled
`(local build)` and **preselected**, so a bot created in a dev-run Studio is pinned to `0.0.0-SNAPSHOT`
automatically. This affordance is gated on `AppVersion.isDevBuild()` (true only when there's no jar
manifest, i.e. an IDE/`javafx:run` launch), so packaged/released builds never surface your `~/.m2` snapshots
and their users only ever pick real released versions.

## Code Style

Prefer **functional OOP**: minimize mutable class fields to avoid state-related bugs. Favor immutable
values (`record`s like `MatchResult`, `RawMatch`, `Point`/`Rect`/`Size`) and pure transformations;
pass dependencies in via parameters rather than holding mutable fields or static/singleton state.
Keep side effects (screen capture, native library loading, process launching) at the edges. The
static facades (`ImageFinder`, `ImageClicker`, `ScreenCapture`, …) are stateless dispatchers.

## Architecture

### Public API vs internal plumbing

- **`com.botmaker.sdk.api.*`** is the API generated bots compile against. It is under a **compatibility
  convention** — real semver, and a removal announced by one full minor marked
  `@Deprecated(since = "x.y.z", forRemoval = true)` whose Javadoc `@deprecated` line names the replacement.
  A member added after 1.1.0 carries `@since`; the 1.1.0 surface itself carries none, because comparing two
  published jars already yields the exact per-version added/removed set and 818 identical tags would not.
  The full picture is **`../docs/refactor/21-api-compat.md`**.

  **Convention, not enforcement, and that is a deliberate 2026-08-22 decision.** There was a gate here:
  japicmp against the previously published jar, `ApiRulesCheck` in `src/api-check/java`, an `api-check`
  profile, and `release.sh` refusing a version number the diff did not justify. All of it is deleted. It
  existed to protect a repair model where a break was carried across by **pointing one member at another**
  — the old `fix` kinds — and there, a wrong or missing declaration produced a bot that compiled and
  behaved differently, so the declarations had to be checkable. Studio no longer does that: it replaces a
  call to a member that is gone with a **default value of the type it used to return** and marks the
  enclosing function `@NeedsReview`. Nothing is declared, so nothing can be forgotten, and there is nothing
  for a gate to verify. What it costs is stated plainly in `release.sh`: nothing now refuses a breaking
  change released as a patch.

  **Two things on this side still matter to an upgrade, and both are read by Studio, not by CI.**

  **`@ApiId` is how a rename survives.** A jar diff sees `ImageClicker` go and `IClicker` arrive; it cannot
  see they are the same class, and read as a removal that is hundreds of deleted statements in someone's
  bot. Every public `api.*` type carries `@ApiId("kebab-case")` (`api/ApiId.java`, `CLASS` retention, read
  from the jar by the ClassGraph scan Studio already runs). Both releases spell the id the same way, so the
  pairing is a fact. **Rename the class freely; keep the id.** The one rule: an id names a *role* and is
  **retired when the role disappears, never re-pointed** at a different class — its absence from the newer
  jar is exactly the "this is not coming back" signal. Reusing one is survivable rather than catastrophic,
  because an id pairs the **type name only** and every member is still checked individually, so an id kept
  across a redesign degrades to defaults-and-review rather than a silently wrong rewrite.

  **`src/main/resources/META-INF/botmaker/migrations.json` is renames only** — `schema` 2,
  `{"versions": {"<v>": [{"from": ..., "to": ...}]}}` — and exists for what the ids cannot reach: anything
  renamed relative to a release predating them (v1.0.26 carries none), and a pairing you want to state by
  hand. The version keys are still there even though nothing replays any more: Studio composes every
  version in `(from, to]` ascending into one map, because a bot jumping 1.x → 3.0 spells a twice-renamed
  member the way 1.x did and neither entry matches that alone. The file's own `_readme` is the authority.

  The API contains:
  - `api.vision` — `ImageFinder` (find + `exists` + the lambda control-flow `whileExists`/`ifExists`
    /`untilExists`), `ImageClicker`, `ImageWaiter`, `MatchResult`, `ImageTemplate`.
  - `api.vision.Precision` — `Pixel`'s precision knobs as one value type rather than a bare
    `double`/`int`. A record with named constants (`Precision.EXACT`/`TIGHT`/`DEFAULT`/`LOOSE`) and a
    validating `of(...)`. It is a type because the numbers are unreadable alone — ΔE has no obvious scale,
    and the pixel count is an *area* routinely misread as a width — and because it lets Studio dispatch its
    editor by **type** instead of by a `(method, argIndex)` table that would silently stop firing whenever
    `Pixel` gains an overload. (It was two types, `Tolerance` and `MinPixels`, until they were merged; this
    entry named them long after they were gone.)
  - `api.BotSettings` — the bot's runtime tuning (delays, confidence, compare margin, retry budget, real
    input), seeded from the project's `botmaker-project.properties` on first read. Was `api.vision.ClickConfig`.
  - `api.capture.Screen` (`capture()`), `api.interaction.Mouse`/`Wait`, `api.core.Direction`,
    geometry `api.Point`/`Rect`/`Size`.
  - `api.BotMaker` — console IO. `readX()` prints a SOH-wrapped `BM-INPUT:<type>` marker to stdout
    before blocking on stdin; Studio detects/strips it to show a modal input prompt. Changing that
    marker on one side without the other breaks input prompts.
- **`com.botmaker.sdk.internal.*`** is plumbing, free to rework — and now nearly empty, because most of what
  was in it was not SDK-specific: `opencv`, `capture` (desktop backends), `launch` and the emulator transport
  all live in **shared**, where Studio can reach them too. What is left is genuinely SDK-shaped:
  - `internal/observe/IpcObserver` — it *implements* `api.observe.BotObserver` and consumes
    `MatchEvent`/`ClickEvent`/`Surface`/`Bots`; it is the adapter from SDK observer callbacks onto shared's
    already-shared telemetry wire (`shared.ipc.TelemetryClient`). Moving it would move the SDK types with it.
  - `internal/config/ProjectDefaults` — a thin typed accessor mapping shared's `ProjectProperties` (which owns
    the file, the key names and the parsing) onto `CaptureSource`/`Size`.
  (The manual harnesses that were also left behind here — `internal/Main`, `capture/CaptureTest`,
  `capture/ImageDisplay`, `capture/linux/LinuxControllerTest`, `opencv/OpencvTest` — have since been
  deleted; they were dev tools nothing referenced.)

### OpenCV / native loading

The native library is `org.openpnp:opencv` (self-contained — bundles the OS native and loads it via
`nu.pattern.OpenCV.loadLocally()`). **All loading goes through shared's single idempotent loader
`com.botmaker.shared.opencv.OpenCvNative.ensureLoaded()`.** It is invoked from a `static {}` block on the
classes that first touch an `org.opencv` type — `ImageTemplate` (which owns the image `Mat`) and shared's
`OpencvManager` — so every find/match path loads the native before any Mat is created, independent of JVM
class-link order. Do not rely on scattered per-class blocks elsewhere; a class that links an OpenCV type
without a guaranteed-loaded path is how "opencv not loaded" errors return. `ImageFinder.find` deliberately
does **not** catch `Error`s (e.g. `UnsatisfiedLinkError`), so a genuine load failure surfaces instead of
masquerading as "not found".

**The matching engines live in shared** (`shared.opencv`: `OpencvManager`, `ColorMatcher`,
`ResolutionScaler`), because Studio's Magic Wand matches at edit time exactly as a bot does at run time — and
because it collapses three independent copies of the OpenCV loader into one. They work directly on
`org.opencv.core.Mat` and return the raw `RawMatch`/`RawColorMatch` records (plain ints + score, no OpenCV
types); **mapping those onto the public `MatchResult`/`ColorMatch` is the SDK's job**, in `api.vision`. The
OpenCV `Mat` still lives in `ImageTemplate` — the old `Template`/`InternalMatch`/`MatType` wrapper layer has
been collapsed.

Because shared cannot see `api.Size`, the matcher takes the authored resolution as a `java.awt.Dimension`.
`ImageTemplate.authoredSize()` is the single conversion point; the vision call sites go through it rather
than each converting.

### Screen capture

`com.botmaker.shared.capture.ScreenCapture` is the **single** desktop-capture facade, and it now lives in
shared beside per-window capture — the platform knowledge is the same either way, and Studio's picker wants
the same grab. `api.capture.Screen`/`Desktop`/`Monitor` and every `NativeController.captureDesktop()` route
through it; there is one `getVirtualScreenBounds()` (the AWT all-monitor union). See
`../botmaker-shared/CLAUDE.md` for the backend selection (`RobotCapture` vs `SpectacleCapture`) and the
Wayland notes.

(A Swing `ImageDisplay` preview window used to live here for the `internal` dev harnesses; it went with
them. A JFrame was never something the JavaFX Studio would consume.)

### Mouse clicks & the Wayland input limitation

`api.interaction.Mouse.click` routes through `NativeControllerFactory.get()` (Windows → `Clicker`/
`User32 PostMessage`; Linux → `LinuxController` XTest, with an AWT `Robot` fallback). Match
coordinates are absolute: `ImageFinder` adds `Screen.captureOrigin()` (the virtual-screen
origin) so clicks are correct even when a monitor sits left/above the primary.

On Linux the click warps the real cursor, then restores it. **Restore is X11-only:** under native
Wayland the JVM is an **XWayland** client that can *write* the pointer (warp + click work) but
**cannot read the global cursor position** (XQueryPointer / AWT `MouseInfo` return a stale constant
when the cursor isn't over our surface — and the bot has no window). `LinuxController` therefore
skips the restore when `WAYLAND_DISPLAY` is set, leaving the cursor on the target. The Wayland-correct
"click without disturbing the cursor" path is the xdg-desktop-portal **RemoteDesktop** (libei/
PipeWire) interface — deferred; see `ROADMAP.md`.

## Android emulator (`api.emulator`)

The emulator **capability** — the dadb transport (`AdbDevice`) and product discovery (`Platforms`,
`BlueStacks`/`LdPlayer`, `WindowsRegistry`, `EmulatorInstance`) — lives in **shared**
(`com.botmaker.shared.emulator`), because both the SDK (connect at runtime) and Studio (list instances in the
picker) need it, and so a future Studio capture-picker can preview an emulator screen. dadb therefore comes in
transitively via shared — it is **not** a direct SDK dependency.

The SDK owns only the bot-facing facade `api.emulator`: **`Emulator implements CaptureSource`** (wraps a shared
`AdbDevice`; `origin()` is `(0,0)` so a match's coords are already emulator pixels and the whole vision/click
stack works unchanged; `click(Point)` → `adb input tap`) and **`Emulators`** (static discovery over shared's
`Platforms`: `list`/`first`/`named`/`connect`, plus `use()`/`use(String)` connect-and-set-`Source` shorthands).
