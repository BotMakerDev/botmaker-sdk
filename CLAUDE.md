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

**The three geometry types are records of `int`s, and both halves of that are deliberate.** `Point`, `Rect`
and `Size` were OpenCV `org.opencv.core.*` clones until 2026-08-23 — mutable public `double` fields,
`set(double[])`, `clone()` — none of which anything used, while the missing `equals` on `Point` and `Rect`
made `p1.equals(p2)` an identity comparison in every bot that tried it. They are `int` because every producer
is a pixel and every consumer is an input event the native layer delivers at a whole pixel; the old `double`
was cast straight back at fourteen call sites. **A fraction is rounded where it is created, never carried** —
`Rect.center`, `MatchResult.center`, `Pixel`'s centre of mass, `Mouse.drag`'s interpolation. Because
they are immutable, a getter hands back its field rather than a defensive copy; don't reintroduce one.
Keep side effects (screen capture, native library loading, process launching) at the edges. The
static facades (`ImageFinder`, `ImageClicker`, `ScreenCapture`, …) are stateless dispatchers.

## Architecture

### Public API vs internal plumbing

- **The line between the two is one question: can a bot *write the name down*?** A type it can only ever
  *receive* — from a factory, as an event, as a return value — belongs in `internal`, however public its
  methods are. That rule was applied in 1.1.0 and moved eleven classes out: the `CaptureSource`
  implementations (`Desktop`, `Monitor`, `NamedWindow`, `SessionSource`), which only ever arrive from
  `CaptureSource.desktop()/monitor()/window()` and `Source.current()` — all of which declare the *interface*
  as their return type — and the whole observation stack (`Bots`, `BotObserver`, `Surface`, `ClickEvent`,
  `MatchEvent`, `SwipeEvent`), whose only consumer was ever `internal.observe.IpcObserver`. `Screen` was
  deleted outright: no callers, and not even a `CaptureSource`. **Studio's `palette/SdkType` is the mirror**
  of this decision, not a second one — a class that leaves `api` leaves that enum, which is how the palette
  stops offering it.

- **A second rule, from the 1.1.0 method audit: no `api` signature may name a type the SDK does not version.**
  `botmaker-shared` and OpenCV are *freely breakable* by design while `api.*` is under contract, so a public
  `api` method returning one of their types promises a spelling nobody keeps — and no gate on either side can
  see it break. `ImageTemplate.getMat()` (`org.opencv.core.Mat`) is package-private for this reason, and
  `targetWindow()` (shared's `GenericWindow`) left `CaptureSource`/`Window` for
  **`internal.capture.WindowBacked`**, which `Window`, `NamedWindow`, `SessionSource` and `RegionSource`
  implement and `Keyboard` reaches via `WindowBacked.of(source)`. **The last knowingly-open leak closed in
  1.2.0**: `Text`'s nine `shared.ocr.OcrOptions` overloads were a real feature with no `api`-owned
  replacement, so rather than remove them the whole of `com.botmaker.shared.ocr` moved *here* — `OcrOptions`,
  `OcrLanguage` and `TextResult` into `api.vision` under contract, `OcrEngine`/`OcrNative`/`OcrPreprocessor`
  into `internal.ocr` where a bot can only receive their results. shared had exactly one consumer of that
  package (this facade), so the move cost nothing and dissolved the leak instead of working around it.
  **`docs/refactor/22-api-audit.md` is the record** of that audit: every verdict, the near-misses and why they
  were near-misses, and the additions it deliberately deferred.

- **`com.botmaker.sdk.api.*`** is the API generated bots compile against, and every class in it sits in a
  sub-package that says what it is: `api.geometry` (`Point`, `Rect`, `Size`, `Direction`), `api.meta` (the
  three pointer annotations), `api.bot`, `api.capture`, `api.emulator`, `api.interaction`, `api.launch`,
  `api.util` (`Time`, `BotMaker`, `Debug`), `api.vision`. **The `api` root holds no classes** — it was a
  junk drawer of annotations, geometry and five facades until 1.1.0, and a name landing there again means
  somebody skipped the question above. It is under a **compatibility convention** — real semver, and a removal announced by one full minor marked
  `@Deprecated(since = "x.y.z", forRemoval = true)` whose Javadoc `@deprecated` line names the replacement.
  A member added after 1.1.0 carries `@since`; the 1.1.0 surface itself carries none, because comparing two
  published jars already yields the exact per-version added/removed set and 818 identical tags would not.
  The full picture is **`../docs/refactor/21-api-compat.md`**.

  **Convention, not enforcement, and that is a deliberate 2026-08-22 decision.** There was a gate here:
  japicmp against the previously published jar, `ApiRulesCheck` in `src/api-check/java`, an `api-check`
  profile, and `release.sh` refusing a version number the diff did not justify. All of it is deleted. It
  existed to protect a repair model where a break was carried across by **pointing one member at another**
  — the old `fix` kinds — and there, a wrong or missing declaration produced a bot that compiled and
  behaved differently, so the declarations had to be checkable. What it costs is stated plainly in
  `release.sh`: nothing now refuses a breaking change released as a patch, and nothing sizes the bump.

  **What carries a rename is a pointer written at both ends — `@ReplacedBy` and `@Replaces`** (2026-08-23;
  they replaced `@ApiId` and `META-INF/botmaker/migrations.json`, both deleted). A jar diff sees
  `ImageClicker#click` go and `IClicker#tap` arrive and cannot see that one became the other; read as a
  removal, that is hundreds of calls replaced by default values in someone's bot. So the SDK says it, at
  both ends, because a bot being upgraded holds only two jars:

  - **`@ReplacedBy`** on the deprecated element, read out of the bot's **own** jar — the bot still spells the
    element the old way, so that is where the forward pointer has to be. Each target is `fqn`, `fqn#member`
    or `fqn#<init>`, no arity (it sits on one overload). **An empty value is an explicit "nothing takes my
    place"**, not an omission — which is why it is *required* on every deprecated public element.
  - **`@Replaces`** on the survivor, read out of the **target** jar — each entry
    `fqn[#member][(arity)]@<version>`, the version being the **last release the old spelling existed in**.
    This is the only place the answer survives once the deprecated element is finally deleted. Entries
    accumulate and are never pruned. It carries **`note()` and `behaviourChanged()` too**, duplicating the
    forward end's, because the two are read out of different jars and only one survives: a bot upgrading
    *through* the deprecation release reads the forward pair, a bot that skipped it has only this one. The
    forward note wins where both exist; the flag is a logical OR. The **arity is optional and exists only
    here** — the forward end sits on one overload already, while this end may name an overload that is
    already deleted and so has nothing left to count.

  **`@ReplacedBy.value()` is a `String[]`, and that is the split.** One old member can become two, and
  *which* one a given call meant is a property of **that call**, not of the member — `Mouse.scroll(int)`,
  whose sign decides `scrollUp` from `scrollDown`, is the worked example, and no annotation can know a sign.
  So the SDK does not resolve a split, it **offers** one: the targets in preference order (first preferred)
  plus a parallel **`whens()`** carrying one sentence per candidate (*"when notches is positive"*), and
  Studio puts the choice to the user once per call site. `@ReplacedBy("…#tap")` is unchanged in source and
  in bytecode — a single value is already a one-element array — so the ordinary one-target pointer is the
  degenerate case of all of it. On the back edge a split surfaces as **two survivors claiming one
  `name@version`**, which is legal exactly when the claimed element's own `@ReplacedBy` names precisely
  those two; that is checkable inside one build, and it is the only place a split still exists once the old
  member is deleted.

  Either half alone resolves one hop; **composed, they resolve a chain** — `a`→`b` in 2.0 and `b`→`c` in 3.0
  land a bot still spelling it `a` on `c`, with the 2.0 jar never fetched. Write both halves **in the release
  that makes the change**, while both ends are still compilable: that is what lets the gate below verify the
  link from a single build. A pointer is an ordinary annotation — correct a wrong one in a later release.

  **`ApiPointersTest` is the gate, and it is not the one that was deleted.** One offline ClassGraph scan of
  `target/classes`, run by CI on every build and by `release.sh check_api_pointers`. Five rules, each wrong
  at every version: every deprecated `api.*` element carries a pointer; **every** target resolves; **every**
  target carries the matching back-edge; no two survivors claim the same `name@version` *without the claimed
  element declaring them as a split*; every entry is well-formed, arity included. Rule 6 is opt-in —
  `-Dbotmaker.api.maxVersion` — because only the release caller knows the version being cut. **It is not a
  coverage rule**: an uncovered break is a supported outcome (default value plus review mark), and these
  five only ask that a link somebody *did* declare is complete. Rule 11 is the split's own: a pointer naming
  two or more candidates needs a non-blank `whens()` for each, since a menu of bare member names is not a
  choice anybody can make, and a blank target may not be mixed in with real ones.

  **`api-surface.txt` was the second gate, and it was deleted on 2026-08-25 — a deliberate reversal of the
  decision recorded here the day before, and the second one this month.** It was a committed, generated file
  at the module root holding the *previous* release's public `api.*` surface, one sorted line per element
  (`type#member(paramTypes):returnType [deprecated] [since=…]`), which `ApiSurfaceTest` diffed this build
  against: an element that left had to have carried `[deprecated]` (the announced window), an element in both
  kept the exact `@Since` it had, and a new element had to carry one. It went with `ApiSurfaceTest`,
  `release.sh`'s `check_api_surface` / `refresh_api_surface` and `--allow-removal`
  (`-Dbotmaker.api.allowUndeprecatedRemoval`).

  Why: it was a **second, hand-maintained record of what the SDK offers**, kept in a text file beside the
  code, and the inversion is about to build the real one — an explicit per-version catalog in
  `api.authoring`, written in code, keyed by `SdkVersion`, which answers *what did 1.2 offer* rather than
  only *what did the last build offer*. Two records of the same fact is exactly the shape this teardown
  keeps removing. The catalog subsumes the gate, and until it exists the window is a **convention** again:
  `@Deprecated(since, forRemoval = true)` one full minor ahead, with a pointer, still the rule — nothing
  mechanically refuses an undeprecated removal.

  **What survives is the pointer machinery**, which is the half that carries a rename: `@ReplacedBy`,
  `@Replaces`, `ApiPointersTest` and `ApiPointerProcessor`, all unchanged.

  **`ApiPointerProcessor` is the same rules again, at the line rather than at the class** (2026-08-24). Three
  of `ApiPointersTest`'s rules — a deprecated element carries a pointer, each target it names exists, each
  target points back — are decidable **from one element**, so they also run as `javac` errors during the main
  compile: red in the IDE, on the annotation, while it is still being typed. The other rules cannot follow.
  A processor sees elements one at a time and never the surface at once, and rule 4 (no *undeclared* double
  claim) is a question about every `@Replaces` in the API, while rules 6–7 need the version only the release
  caller knows. So the test stays the gate — it is what CI and `release.sh` read, and **if the two ever
  disagree the test is right** — and this is ergonomics: one mistake fails the build twice, on purpose.

  It lives in a **second source root, `src/apt/java`**, compiled by its own `maven-compiler-plugin` execution
  at `process-sources` with `-proc:none` (it must exist before the API it checks, and must not be run over
  itself). Its output goes to `target/apt-classes` — never `target/classes` — and reaches the main compile
  only as `-processorpath`, which is why no `maven-jar-plugin` `<excludes>` is needed to keep it out of the
  jar: the classes are never in the packaged directory at all. `annotationProcessorPaths` cannot express this
  (it takes artifact coordinates, not a directory this build just made). Because it is compiled first, the
  processor **cannot reference `ReplacedBy.class`**; it matches annotations by FQN string and reads
  `AnnotationMirror`s by hand, which is what keeps the dependency one-way. If the two-pass build ever proves
  fragile on JitPack, delete both executions and `src/apt` — nothing downstream depends on them, and the
  surface file plus the test are the load-bearing halves.

  **Three more annotations sit beside the pointer pair, all `@Retention(CLASS)`, all read from the jar by the
  same scan** (2026-08-23). Each records something that is cheap while both ends of a move still exist and
  impossible afterwards:

  - **`@ReplacedBy(note = "…")`** — the author's own sentence, shown to the user **verbatim**. The pointer
    says *what*; nothing else can say *why*. (It is what `migrations.json`'s deleted `summary` used to be.)
  - **`@ReplacedBy(behaviourChanged = true)`** — the replacement *does something different*. This is the one
    gap the redirect model cannot see: Studio takes a pointer by comparing **shapes**, so "same shape,
    different meaning" is exactly a silent, successful rename, and the bot compiles and misbehaves. Setting it
    forces a review mark on every redirected site, with the note as its text — hence rule 8: `true` with a
    blank `note` is refused, since a mark that says nothing costs a hand review and answers nothing.
  - **`@Since("1.2.0")`** — the release an element first shipped in, so the upgrade dialog can group additions
    by version instead of one flat alphabetical diff. **The pre-1.1.0 surface deliberately carries none** and
    never will: the value is unrecoverable after the fact, and a guessed one asserts something false about a
    release the user cannot check. Rule 7 checks only the shape (and, at release time, that it is not dated
    ahead of the version being cut) — **absence is never an error**.
  **Two more lived here and were deleted on 2026-08-25 — `@Scaffolding` and `@Palette`.** Both existed to let
  **two repositories agree about something neither could read in the other**, and the maintainer's decision is
  to remove the disagreement rather than manage it: the SDK becomes the generator and the palette, so there is
  no second author left to inform. Worth knowing they existed, because both left visible holes:

  - **`@Scaffolding`** said *Studio writes this element into the files it generates*, so renaming it broke
    bots that never mentioned it. `ApiPointersTest` rule 9 refused a `@Deprecated` `@Scaffolding` element with
    an empty `@ReplacedBy`, and `ScaffoldTemplatesTest` read the templates' own constant pools to require the
    annotation on every `com.botmaker.sdk.*` member they reached. (It had been reconciled through a committed
    `scaffolding-surface.txt` written by Studio's own test, until the scaffold moved into
    `src/templates/java` on 2026-08-24 and made the file unnecessary — the same argument, one step further
    along, is why the annotation itself is gone.) Both rules went with it.
  - **`@Palette`** said *Studio offers this in its block palette* — a strict, per-overload whitelist, where
    **hiding was not deprecating**: an unannotated method stayed public, supported and under contract, simply
    not proposed. It was the answer to the wall the method audit kept hitting
    (`docs/refactor/22-api-audit.md` §3, §5), where a method could only leave the menu by leaving the API.
    Deleting it **widens Studio's menus** — `SdkSurfaceService` treats a jar with no `Palette` class as
    uncurated and offers everything public — until the SDK serves the palette itself. That is a known interim
    cost, not a regression. The maintainer's per-facade curation *prose* survives in each facade's Javadoc
    (*"Curated for the palette: …"*); the per-member verdicts lived only in the annotations and are gone.

  Both were public `api.meta` types removed after 1.1.0 shipped, i.e. an undeprecated removal, taken
  deliberately via `release.sh --allow-removal` with `api-surface.txt` regenerated in the same commit — the
  last use of a switch and a file that were themselves deleted hours later (above). Neither was a name a bot
  could write down.

## The scaffold templates are gone (2026-08-25)

`src/templates/java` — nine files the SDK compiled and shipped as *text* under `botmaker-templates/` for
Studio to fill — is **deleted**, with `@Template`, `apt/TemplateProcessor`, the generated `manifest.txt` and
`ScaffoldTemplatesTest`. The pom is back to two compiler passes: `compile-processor` (`src/apt/java` →
`target/apt-classes`, `-proc:none`) and the main compile that runs `ApiPointerProcessor`. Passes 3 and 4 went
together — pass 4 existed only to undo pass 3's `projectArtifact.setFile(target/template-classes)`.

This is the second half of the same reversal: the templates existed so **two repositories could co-author one
file**, the SDK owning its frame and Studio splicing the fences. The inversion removes the second author —
the SDK becomes the generator outright (`api.authoring`, inversion Phase 2) — so a text-and-fences protocol
between them has nothing left to mediate.

**The interim cost is real and is the point:** with nothing to fill, **Studio cannot generate a project or
save an Activity Flow** until Phase 2 lands. Both paths refuse by name. Do not re-add a template as a way
around that; the emitters belong in this module.

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
  - `internal/ocr/{OcrEngine,OcrNative,OcrPreprocessor}` — the Tesseract stack behind `api.vision.Text`,
    moved here from `com.botmaker.shared.ocr` in 1.2.0. It is internal because a bot only ever *receives*
    a `TextResult`; it never names the engine. `OcrNative` extracts the bundled `tessdata` and delegates the
    OpenCV load to shared's `OpenCvNative.ensureLoaded()`. **The Tess4J / lept4j / bytedeco pins move
    together** — a mismatch throws an undefined-symbol `UnsatisfiedLinkError` on the `getWords` path only, at
    a bot's runtime and never at build time. `pom.xml`'s property block has the version table and
    `OcrEngineNativeTest` is the only guard; read both before bumping anything in that stack.
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
