# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The **BotMaker SDK** is the runtime library that user bots compile against, **and plugin #1** (`SdkPlugin`).
The sibling **botmaker-studio** app (`../botmaker-studio`) loads it as a plugin off an open project's
classpath and never depends on it. The SDK depends on **botmaker-shared** (`../botmaker-shared`,
cross-platform native window plumbing), **botmaker-session**, the contract (`botmaker-studio-api`, at
`compile` so a bot has `@Param`/`@Managed`), **botmaker-plugin-basics** and, for its plugin half only,
**botmaker-plugin-toolkit**.

This file states what is true now. How it got here — the generated sources, `activities.json`, `Wire`,
`capture.json`, the seeds, the codecs, and every dated section this file carried until 2026-09-28 — is in
`../docs/refactor/31-umbrella-history.md` (*SDK*), and the old text itself is `git show 8d2cfe4:CLAUDE.md` in
this repository. Search there before re-deriving a decision.

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
mvn install        # Install to ~/.m2 at the coordinate a bot resolves (see Local dev)
```

There are no `main`-method entry points. The module is a library: everything under `src/main` is
reachable from a generated bot, and everything that verifies it is JUnit under `src/test`. A new diagnostic
goes in `src/test` with the JUnit the rest of the module uses.

## Publishing

The SDK is consumed by **bot projects** (not by Studio), via JitPack as
`com.github.LiQiyeDev:botmaker-sdk:<tag>`. JitPack builds each git tag on demand and serves it under that
coordinate regardless of this pom's `groupId`/`version` (so the pom `version` is cosmetic). **The maintainer
owns the SDK → JitPack publish — don't push or publish the SDK yourself;** releases are cut from the
umbrella with `../release.sh`. The whole `CHANGELOG.md` is copied into the jar as
`META-INF/botmaker/whats-new.md`.

### Local dev (test SDK changes without pushing a tag)

A bot pins `com.github.LiQiyeDev:botmaker-sdk:<version>`, and `~/.m2` is checked before JitPack. Install
the SDK and what it builds on at `0.0.0-SNAPSHOT` from the umbrella root:

```bash
mvn -pl botmaker-sdk -am install     # shared, session, contract, toolkit, basics, then the SDK
```

Re-run it after each SDK edit; a bot pinned to `0.0.0-SNAPSHOT` resolves the fresh jar on its next
classpath resolve, and Studio's **Project ▸ Plugins & Libraries ▸ Reload plugins** re-opens the plugin loader over it. A dev-run
Studio (`AppVersion.isDevBuild()`, no jar manifest) lists local `*-SNAPSHOT` SDK builds first in its version
dropdowns, labelled `(local build)`; a packaged Studio never shows them.

## Code Style

Prefer **functional OOP**: minimize mutable class fields to avoid state-related bugs. Favor immutable
values (`record`s like `MatchResult`, `RawMatch`, `Point`/`Rect`/`Size`) and pure transformations;
pass dependencies in via parameters rather than holding mutable fields or static/singleton state.

**The three geometry types are records of `int`s, and both halves of that are deliberate.** They are
immutable, so `equals` is value equality and a getter hands back its field rather than a defensive copy —
don't reintroduce one. They are `int` because every producer is a pixel and every consumer is an input event
the native layer delivers at a whole pixel. **A fraction is rounded where it is created, never carried** —
`Rect.center`, `MatchResult.center`, `Pixel`'s centre of mass, `Mouse.drag`'s interpolation.
Keep side effects (screen capture, native library loading, process launching) at the edges. The
static facades (`ImageFinder`, `ImageClicker`, …) are stateless dispatchers.

## Architecture

### Public API vs internal plumbing

- **The line between the two is one question: can a bot *write the name down*?** A type it can only ever
  *receive* — from a factory, as an event, as a return value — belongs in `internal`, however public its
  methods are. The `CaptureSource` implementations (`Desktop`, `Monitor`, `NamedWindow`, `SessionSource`)
  only ever arrive from `CaptureSource.desktop()/monitor()/window()` and `Source.current()`, and the
  observation stack (`Bots`, `BotObserver`, `Surface`, `ClickEvent`, `MatchEvent`, `SwipeEvent`) only ever
  reached `internal.observe.IpcObserver`, so all of them are `internal`. **The palette mirrors this**: a
  class that leaves `api` leaves the palette.

- **The palette is discovered, never listed.** `@Palette` on an `api` class = **offered** (its own menu
  entry). A value type carries **no** annotation: what an offered call takes or returns from this jar is
  catalogued by reach (the recognition set — imports, "does `Point` mean ours or `java.awt`'s"), and plumbing
  no offered call reaches (`Debug`, `Watchdog`, `Session`) is not catalogued at all (2026-09-30; type-level
  `@Hidden` and `order` are gone, facades are alphabetical). `@Hidden` is for a member. The host finds every
  `@Palette` class in this jar (`botmaker-plugin-host`'s `Palettes`, since
  `SdkPlugin` declares no catalog); members are discovered in the class file's own declaration order
  (`SourceOrder`, alphabetical on any failure). **Constructors are not catalogued**: a palette entry inserts
  a *call*.

- **Recording is the host's; the SDK annotates.** `@Records(Gesture, rank)` on a public static `api` method
  (`Mouse.click`, `Keyboard.type/tap/combo`, `Wait.time`, `ImageClicker.click` at rank 10, `ImageWaiter.waitFor`
  for `AWAIT`, …) says which call writes a gesture. The host fills parameters by type; the one type it cannot
  fill is `plugin/types/PictureAt`, a `RecordedValue` (the project picture under the click). This module
  writes no Java for a recording.

- **No `api` signature may name a type a bot cannot write**: not `internal` (`PluginLayersTest`, by
  reflection), and not `botmaker-shared` or OpenCV, which are freely breakable. `ImageTemplate.getMat()`
  (`org.opencv.core.Mat`) is package-private for this reason, a window handle is reached through
  **`internal.capture.WindowBacked`** (`WindowBacked.of(source)`), an emulator source is
  `CaptureSource.emulator(name)`, and OCR lives here rather than in shared (`api.text`; `internal.ocr` the
  engine). **`../docs/refactor/22-api-audit.md` is the record** of the shared audit.

- **`com.botmaker.sdk.api.*` is what a bot writes, in packages named by what a bot does** (2026-10-01):
  `api.bot`, `api.flow`, `api.capture`, `api.input` (`Mouse`, `MouseButton`, `Keyboard`, `Key`, `Combo`,
  `KeySequence`), `api.time` (`Wait`, `Time`), `api.vision`, `api.text` (OCR), `api.geometry` (`Point`,
  `Rect`, `Size`), `api.sound`, `api.console` (`Debug`, `BotMaker`) — `PluginLayersTest` holds the list.
  **The `api` root holds no classes**. Plumbing the plugin half needs and a bot never names is public in
  `internal`: `internal.launch` (`Game`, `Target`, `LaunchTarget`), `internal.emulator`, `internal.bot`
  (`Session`, `Watchdog`, `BotStuckException`), `internal.flow.Flows`, `internal.capture.Window`.

- **The package tree is `api` / `internal` / `plugin`, and nothing else** (`../docs/refactor/34-plugin-package-tree.md`,
  enforced by `plugin/PluginLayersTest`). `api` and `internal` are what a bot links: no JavaFX, no toolkit, no
  `com.botmaker.sdk.plugin` name. `plugin/` is the Studio half: `SdkPlugin` (the declaration),
  `SdkToolbarItems`, `types/` (`SdkTypes`, `FlowTypes`, `CaptureTypes`, `SettingsTypes`, `PictureAt`),
  `editors/`, and one package per feature — `flow`, `pictures`, `screen`, `source`, `settings`, `pilot`
  (+ `pilot/ui`), `emulator`, `launch`, `setup`. **No class under `plugin/` reads or writes Java text**: an
  editor reads the value the host hands it and hands one back.

- **`api.*` breaks freely (2026-10-01, the maintainer's ruling).** Studio's refactor-safe upgrade drops a
  statement whose method is gone, so the never-delete rule, its japicmp gate and the "2.0.0 is the one
  sanctioned break" wording are retired. Delete outright; no `@Deprecated` is owed.

- **A rename may carry `@ReplacedBy`** (contract's `com.botmaker.plugin.api.meta`), a courtesy Studio's
  upgrade follows: targets `fqn`, `fqn#member` or `fqn#<init>`; an empty value is an explicit "nothing takes
  my place"; several values are a split, with a parallel `whens()` sentence per candidate; `note` is shown
  verbatim; `behaviourChanged = true` forces a review mark and needs a `note`. **`ApiPointersTest`** is the gate
  (four rules: every deprecated element has a pointer, every target resolves, a behaviour change has its
  note, a split says when). It is not a coverage rule. `first(…)` filters with `directOnly()`, because these
  annotations annotate each other.

### What the API holds

- `api.vision` — `ImageFinder` (find + `exists` + the lambda control flow `whileExists`/`ifExists`/
  `untilExists`), `ImageClicker`, `ImageWaiter`, `MatchResult`, `ImageTemplate`, `ImageTemplateGroup`,
  `Pixel`, `Vision` (the last image and colour match). `api.text` is OCR: `Text` (with `Text.lastMatch()`),
  `TextMatch`, `TextResult`, `OcrOptions`, `OcrLanguage`. `Precision` is `Pixel`'s knobs as one value (`EXACT`/`TIGHT`/`DEFAULT`/`LOOSE`, a validating
  `of(…)`, withers): a type because ΔE has no obvious scale and the pixel count is an *area* routinely misread
  as a width, and because an editor is claimed by **type**, never by a method and an argument index.
- `api.bot` — `Bot.run(Home::goHome, Sdk.class)` (installs every `@Managed` value it is handed and walks the
  flow, `internal/flow/FlowWalker`), `Activities` (`outcome(@OutcomeName String)`/`next()` — a body is
  `public static Outcome body()`, and `FlowWalker.current()` says which activity runs; `ActivityContext` was
  deleted 2026-09-30), `Outcome`, and **`BotSettings`**, the runtime tuning
  as an immutable value: `@Managed("settings")` in `Sdk.java`, read as `BotSettings.current()`, changed for a
  while with `BotSettings.use(…)`, edited in ⚙ Bot Settings (`plugin/settings/BotSettingsWindow`).
- `api.flow` — `Flow`, `Flow.activity(Collect::body, …)` (an activity's work is a **method reference**, so a
  rename is a compile error naming `Sdk.java`), `FlowLayout` (the editor's card positions, which a run
  ignores), `ActivityBody`; `internal.flow.Flows.enabled(name)` reads an activity's switch.
- `api.capture` — `CaptureSource` (`desktop()`, `monitor(i)`, `window(title)`, `emulator(name)`, `region(…)`;
  `capture()` and `origin()` go together), `Source.current()` (what `Bot.run` installed, or the whole desktop).
- `api.input` (`Mouse`, `Keyboard`, …), `api.time` (`Wait`, `Time`), `api.sound`, `api.geometry`.
- `api.console.BotMaker` — console IO. `readX()` prints a SOH-wrapped `BM-INPUT:<type>` marker to stdout before
  blocking on stdin; Studio detects/strips it to show a modal input prompt. Changing that marker on one side
  without the other breaks input prompts.
- **Api parameters that want an editor carry an annotation**: `api.bot.@ActivityName`, `@OutcomeName`, and
  `@Setting(label, prompt, unit, min, max, step, fallback)` on the `BotSettings` withers;
  `internal.emulator.@EmulatorName` on `Emulators`, whose device picker no offered call reaches any more
  (kept, flagged 2026-10-01). The launch annotations and their game grids went on 2026-10-01. `SdkEditors.ALL`
  is `SlotEditor.onParameter(X.class).draw(…)`, and `SettingsEditors` reads `@Setting`.

### What `internal` holds

Plumbing, free to rework — and small, because most of what is not SDK-specific (desktop capture backends,
launch, the emulator transport, OpenCV matching) lives in **shared**, where Studio can reach it too:

- `internal/observe` — the observation stack and `IpcObserver`, the adapter from observer callbacks onto
  shared's telemetry wire (`shared.ipc.TelemetryClient`).
- `internal/config/ProjectDefaults` — what a running bot was told from outside its code: the
  `botmaker.launch.target` run property (set per machine through `Runs.setProperty`,
  `plugin/settings/LaunchTargetValue`) and the session half of its `BotSettings`. No file is read.
- `internal/ocr/{OcrEngine,OcrNative,OcrPreprocessor}` — the Tesseract stack behind `api.vision.Text`.
  `OcrNative` extracts the bundled `tessdata` and delegates the OpenCV load to shared's
  `OpenCvNative.ensureLoaded()`. **The Tess4J / lept4j / bytedeco pins move together** — a mismatch throws an
  undefined-symbol `UnsatisfiedLinkError` on the `getWords` path only, at a bot's runtime and never at build
  time. `pom.xml`'s property block has the version table and `OcrEngineNativeTest` is the only guard; read
  both before bumping anything in that stack.
- `internal/flow`, `internal/bot`, `internal/capture` (the `CaptureSource` implementations, `RegionSource`,
  `CurrentSource`, `WindowBacked`), `internal/session`, `internal/vision` (`TemplateNames`, the `img:`
  prefix), `internal/trace`, `internal/sound`.
- `internal/trace` — `Trace` (collapsed runs, located lines) and `TraceSources`: **a debug line never writes its
  own `[Name]`**. `Debug.log("…")` is printed and traced under the calling top-level class's simple name, and
  only that (`@TraceSource` deleted 2026-09-30). Only a class whose lines need two names (`BotSettings`,
  `FlowWalker`) spells one. **A line that only echoes a public call is not written**: Studio's trace agent
  writes one per call a bot makes into an offered class, so `Mouse`, `Wait` and `Keyboard` write none of their
  own; a line adds what the call's arguments and result cannot say (the match score, the window keys went to,
  a collapsed run). `Debug.error`/`Diag.error` print and trace whatever the debug switch says.
  `../docs/refactor/40-run-trace.md`.

## The plugin half

### A plugin's values are Java this plugin ships

`../docs/refactor/33-plugin-java.md`. A project gets two files from its template, in
`src/main/java/<bot package>/plugins/sdk/`: **`Sdk.java`** — `@Managed("flow")` returning a `Flow`,
`@Managed("capture")` a `CaptureSource`, `@Managed("settings")` a `BotSettings`, `@Managed("flow.layout")` a
`FlowLayout` — and **`Pictures.java`**, `@Managed("pictures")` on the type, one `ImageTemplate` constant per
picture. They are the user's: the host rewrites the expression a `@Managed` method returns and nothing else,
a body that is not exactly `return <expr>;` is read-only with a reason, and Studio locks the whole file on the
canvas while this plugin is loaded (its values change in this plugin's windows). A project without them gets
them from the host on bind, from the `ManagedValue`s `SdkValues` declares.

**Nothing is stored anywhere else.** There is no `activities.json`, `capture.json`, `capture.source`, `Wire`,
`ProjectData`, parameters JSON or generated source, and **no migration, by rule**: a project written before
the flow was Java reads as having no flow, and nothing deletes anyone's old file. What one computer launches
is a run property; what a bot was tested on is its gallery entry.

### Declaration

- **`SdkPlugin` is one declaration** on the contract's `DeclaredPlugin`:
  `StudioPlugin.id(ID).named(NAME).types(…).parts(…).editors(…).values(…).toolbar(…).recorded(…)` — suppliers,
  so constructing it links nothing (`SdkPluginHeadlessTest`: a headless host constructs it too, and
  `javafx-controls`, the toolkit, Javalin and ZXing are `optional` here). It adds only `projectClosing()`,
  which releases the Remote Pilot.
- **A type is declared once, in `plugin/types/SdkTypes`**, as a contract `PluginType.value(X.class)` whose
  steps ask for the fresh value, the editor (`() -> X::editor`) and the Java (`writtenAs(Owner::factory,
  X::part, …)`, `writtenAsRecord()`, `writtenAsEach`, `writtenAsConstant()`, or `filledBy(Vision::lastMatch)`
  for a result nobody edits). A part never picked on its own is `ComponentType.part(X.class).writtenAs(…)` in
  `SdkTypes.PARTS`; a `Flow` is five parts (`FlowTypes`), a `CaptureSource` six calls (`CaptureTypes`).
  Adding a type is one constant there and nothing else.
- **Factories are method references, with two string exceptions that stay**:
  - `CaptureTypes`' `REGION` and `REGION_CHAIN` name `CaptureSource.region` through `Ref.member`, because the static
    `region(src, r)` and the instance `src.region(r)` share a name and an arity and javac cannot reference
    either, and both are what a bot writes — a new-named factory would still leave this one.
  - `FlowTypes`' activity body is the text `Collect::body`, written through the host's own source-leaf path
    (Studio's `ValueWriter.ofClass`), which parses it into a tree; renames already follow bindings in the real
    `Sdk.java`. A contract `MethodName` type was considered and declined (2026-09-28): one contract type,
    grammar changes and a flow-editor rewrite to save about forty lines.
  Neither is to be proposed for removal again without new facts.
- **Toolbar buttons are `SdkToolbarItems`**: one `ToolbarItem.id(…)` constant per button, each pressing its
  feature's own `open(ActionContext)` (`RemotePilotUi.open`, `CaptureTemplates.open`, `SourcePicker.choose`,
  `CaptureValue.pointHere`, …). Nothing holds a project in a static: a feature takes `StudioServices` from
  the context it is handed.

### Editors

- **An editor reads the value and hands one back.** `ctx.value(T.class)`, `ctx.set(value)`; a value the host
  could not read is *shown* (`Slots.sourceOr`) and never overwritten. A run of pictures is `SlotRun.Element`s
  (`Element.of`).
- **Two rules for any row over a run**: an element it cannot read is kept as it stands, because every write
  hands back the whole list; and *Remove* is **disabled** at `run.minimum()`, with the reason in its label.
- **A list that moves is read when the dropdown opens, never when the block is drawn**, and the box stays
  typeable (`Editors.choiceSlot`) — the activity and outcome pickers read `FlowValue.read(ctx.services())`.
- **Every screen pick asks where first**: Point, Rect, Size and the eyedropper go through
  `plugin/source/SurfaceMenu` (the bot's source, another window or screen, the whole desktop) and a frozen
  frame (`plugin/screen/FrameShotSource`), and `plugin/screen/PickSpace` decides from the call whether the
  numbers are relative to that surface or desktop pixels. The Precision dialog draws its matches on that frame
  (`MatchOverlay`), reads its target colour from the call (`SlotContext.argumentValue`) and learns ΔE from pins
  (`ToleranceTeacher`).

### Pixels, windows and pictures

- **This plugin grabs its own pixels**, through `botmaker-shared`; the contract has no capture service.
  `plugin/screen/EditorFrame` has two `grabAsync` overloads and the second is not the first with a flag: an
  editor samples the target *as it is*, while a capture raises the window first so what it saves is what is
  on screen (at whatever size it is — the matcher rescales against each picture's sidecar). A blank per-window
  grab on Wayland falls through to a desktop capture cropped to the window. `EditorFrame.Failure` tells *no
  target* from *the grab came back blank*, because they send a user to two different places.
- **The whole desktop is read through shared's `ScreenCapture`**, the grab a bot uses too (Robot, or under
  Wayland the first installed of Spectacle, grim, gnome-screenshot). There is no second desktop grab here.
- **One overlay, `plugin/screen/ScreenOverlay`**: a rubber band, a point lens and a colour lens over a frozen
  frame. Each shows a hint, previews the exact numbers it will write (through the slot's `PickSpace`), and
  cancels on Esc or a right-click; over a frame it is promoted above fullscreen windows (`OverlayStage`).
- **`OverlayStage` is here, not in the toolkit**: the raise is shared's
  (`NativeControllerFactory.promoteOverlayAboveFullscreen`), and the toolkit names no BotMaker upstream but the
  contract. The capture surfaces are deliberately ownerless, so a user can minimise the editor and keep
  capturing, and opt out of theming with the toolkit's `Styles.UNTHEMED`.
- **The pictures are this plugin's folder** (`plugin/pictures/TemplateLibrary`, keyed on
  `StudioServices.resourcesDir()`, with `TagCatalog` and `TemplateManifest`), and **their names are
  `Pictures` constants changed by binding**: `TemplateUses` maps a file to its constant and calls the
  contract's `PluginValues` open-set operations on `SdkValues.PICTURES` (`uses`, `add`, `rename`, `repoint`,
  `remove`); Studio resolves the constant, compiles each change as the whole bot and refuses one that breaks
  it. A path literal a user wrote is not rewritten. Naming a capture (`TemplateNaming`, one class for one crop
  and a batch) refuses blank, taken and reserved names with one wording.
- **The Remote Pilot is this plugin's feature** (`plugin/pilot`, the web client under
  `src/main/resources/pilot/`): a toolbar button opens it and `projectClosing()` releases its port and nested
  display. Telemetry crosses as `TelemetryFrame` bytes (`Runs.onTelemetry`), decoded with the shared codec the
  bot encoded it with — **strings and bytes cross the contract, shapes do not**. It needed nothing added to
  `StudioServices`, which is the test for the next feature that wants to move here.

## OpenCV / native loading

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
`ResolutionScaler`), because an editor matches at edit time exactly as a bot does at run time. They work on
`org.opencv.core.Mat` and return the raw `RawMatch`/`RawColorMatch` records (plain ints + score, no OpenCV
types); **mapping those onto the public `MatchResult`/`ColorMatch` is the SDK's job**, in `api.vision`.
Because shared cannot see `api.geometry.Size`, the matcher takes the authored resolution as a
`java.awt.Dimension`; `ImageTemplate.authoredSize()` is the single conversion point.

## Screen capture

`com.botmaker.shared.capture.ScreenCapture` is the **single** desktop-capture facade, in shared beside
per-window capture. The desktop and monitor sources (`internal/capture/Desktop`, `Monitor`) route through
it; there is one `getVirtualScreenBounds()` (the AWT all-monitor union). A match's coordinates are in its
`CaptureSource`'s pixels, and `origin()` places them on the desktop. See `../botmaker-shared/CLAUDE.md` for
the backend selection (`RobotCapture` vs `SpectacleCapture`) and the Wayland notes; `../docs/display-pipeline.md`
before touching `api/capture` or `internal/session`.

### Mouse clicks & the Wayland input limitation

`api.input.Mouse.click` routes through `NativeControllerFactory.get()` (Windows → `Clicker`/
`User32 PostMessage`; Linux → `LinuxController` XTest, with an AWT `Robot` fallback).

On Linux the click warps the real cursor, then restores it. **Restore is X11-only:** under native
Wayland the JVM is an **XWayland** client that can *write* the pointer (warp + click work) but
**cannot read the global cursor position** (XQueryPointer / AWT `MouseInfo` return a stale constant
when the cursor isn't over our surface — and the bot has no window). `LinuxController` therefore
skips the restore when `WAYLAND_DISPLAY` is set, leaving the cursor on the target. The Wayland-correct
"click without disturbing the cursor" path is the xdg-desktop-portal **RemoteDesktop** (libei/
PipeWire) interface — deferred; see `ROADMAP.md`.

## Android emulator (`internal.emulator`, since 2026-10-01; a bot writes `CaptureSource.emulator(name)`)

The emulator **capability** — the dadb transport (`AdbDevice`) and product discovery (`Platforms`,
`BlueStacks`/`LdPlayer`, `WindowsRegistry`, `EmulatorInstance`) — lives in **shared**
(`com.botmaker.shared.emulator`), because both the SDK (connect at runtime) and the plugin's picker need it.
dadb therefore comes in transitively via shared — it is **not** a direct SDK dependency.

The SDK owns only the facade `internal.emulator`: **`Emulator implements CaptureSource`** (wraps a shared
`AdbDevice`; `origin()` is `(0,0)` so a match's coords are already emulator pixels and the whole vision/click
stack works unchanged; `click(Point)` → `adb input tap`) and **`Emulators`** (static discovery over shared's
`Platforms`: `list`/`first`/`named`/`connect`, plus `use()`/`use(String)` connect-and-set-`Source` shorthands).
