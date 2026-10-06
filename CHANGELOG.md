# Changelog

What each released version of `botmaker-sdk` gives you, in the terms a bot author cares about.

This file is **not** the ROADMAP. `ROADMAP.md` is the detailed engineering log — why a thing was built, what
was rejected, what it cost. This is the short answer to *"should I upgrade, and what changes for me?"*, a few
bullets per version, and it is read by two things besides you:

- **`release.sh` refuses to cut a version with no section here** (`check_changelog`, in the decide pass,
  before anything is tagged). If the top section still says `## [Unreleased]`, rename it to the version being
  cut and date it.
- **The whole file ships inside the jar** as `META-INF/botmaker/whats-new.md`, so Studio's *Project ▸ Upgrade
  SDK…* can lead with what a release **gives** you before it lists what it costs — read offline, out of the
  jar it already downloads to diff. Whole, not one section: a bot may jump several releases at once, so the
  jar must be able to answer every span ending at its own version.

Sections are `## [x.y.z] — YYYY-MM-DD`, newest first. Versions absent from this file predate it; see
`ROADMAP.md` for those.

## [Unreleased]

### Added

- **The overlay editor knows your bot.** In a Studio with the new overlay editor:
  - each activity's body is a chip to edit;
  - the panel opens over the window `Sdk.captureSource()` names, and ⇄ Change opens 🎯 Capture Source;
  - what the panel checks, cuts and shows Claude is that capture source's own frame — a bot narrowed to a
    region is checked in that region — not Studio's grab of the whole window. An emulator is still grabbed
    by Studio, as its window: grabbing it here would connect adb or start it;
  - a picture call says live whether its picture is on screen and where. For a click, it says where the click
    would land, and nothing clicks;
  - its Picture, Point and Flow tabs cut a picture straight into `Pictures`, insert a click at a point, and
    open 🔀 Activity Flow.
- **Claude can see and shape your bot from Studio.** The SDK offers it these tools:
  - **seeing:** `screenshot` (a part of it, shrunk, or with a labelled grid), `find_picture`, `which_screen`
    (every picture scored on one frame), `wait_for_picture`, `read_text`, `find_text`, `pixel_color` and
    `find_color`;
  - **pictures and places:** `list_pictures`, `crop_picture`, `show_picture`, `rename_picture`,
    `delete_picture` (refused while in use, unless its uses move to another picture), `replace_picture`,
    `save_point` and `save_region`;
  - **the flow:** `read_flow`, `add_activity`, `rename_activity`, `remove_activity`, `connect`, `disconnect`
    and `set_start`;
  - **where it looks and how it runs:** `get_capture_source`, `set_capture_source`, `list_windows`,
    `get_settings`, `set_setting`, `list_emulators`, `use_emulator`, `get_launch_target` and
    `set_launch_target` (this computer's launch target: a Steam, Epic, Heroic or Faugus game or an emulator app,
    by a plain id — never a command line or an executable, which only you set).

  Each one writes through the same path as the SDK's own windows. None clicks, types or starts anything.
- **Named spots and areas.** A bot can keep `Points` (`Points.CLAIM = new Point(412, 230)`, for
  `Mouse.click`) and `Regions` (`Regions.BAG = new Rect(…)`, for `.region(…)`) constants, as it keeps
  `Pictures`. Studio writes the two empty classes beside `Pictures.java`; Claude's `save_point` and
  `save_region` fill them.
- **▶ Try one statement.** `Bot.trial(body, Sdk.class)` installs your values as `Bot.run` does and runs only
  `body`, with no launch and no recovery. Studio calls it; a bot does not. `ImageFinder.bestMatch` answers the
  best place a picture sits at any score, for the overlay's probe. It is hidden from the palette.

- **Watch your bot over the desktop while it runs.** In a Studio with the run overlay, the bar names the
  activity your bot is in and what it just did there ("Collect · clicked · line 42"). The desktop gets a green
  box where it found a picture, with its confidence, and a dot where it clicked; each fades after a second
  and a half, and neither takes your clicks or the bot's. Boxes and dots are drawn for a bot that drives your
  own desktop only; one that drives an emulator or a private display still gets its line in the bar. Nothing
  changes in your bot's code.

### Changed

- Published as `com.github.BotMakerDev:botmaker-sdk` (was `com.github.LiQiyeDev`), and resolves shared,
  session, the contract, the toolkit and basics under the new groupId. A bot's pom names it as
  `com.github.BotMakerDev:botmaker-sdk`; tags already built under the old groupId still resolve under it.

- **The Remote Pilot's pause is Studio's pause** when Studio can pause a run, so pausing from the phone and
  from the run overlay is one pause. Under an older Studio the pilot pauses the bot itself, as before.

**Needs a Studio whose contract has the run overlay.** An older Studio refuses this SDK's plugin by name, in
Manage Plugins; your bot still builds and runs.

## [1.3.0] — 2026-10-05

### Changed — breaking

- **Activities and outcomes are constants of your bot, not strings.** Your project gets two classes beside
  `Sdk.java`: `Activities` (`public static final Activity COLLECT = Activity.named("Collect");`) and
  `Outcomes` (`public static final Outcome WON = Outcome.named("Won");`). A body returns `Outcomes.WON` or
  `Outcome.NEXT`, and the flow's steps, arrows, presets and start name the same constants. A misspelled
  outcome is now a compile error instead of a run that silently ends, and renaming an activity or outcome in
  the Activity Flow renames every use of it.
- `Activities.outcome("…")` and `Activities.next()` are gone: return the `Outcomes` constant, or
  `Outcome.NEXT`. `Outcome.of` is now `Outcome.named`.
- `Activities.enable/disable/active` are now `ActivitySwitch.enable/disable/active`, and take an `Activity`
  constant rather than a name.
- In `Sdk.flow()`, `Flow.activity(...)` takes the activity first (`Flow.activity(Activities.COLLECT,
  Collect::body, …)`), and `Flow.edge`, `Flow.preset` and the start take constants.

### Added

- Activity and outcome names are free text in the Activity Flow ("Bag full"); each becomes a constant
  (`Outcomes.BAG_FULL`) that the window adds, renames and removes for you. A constant your code still uses is
  kept, and the window says where it is used.
- An outcome is one constant: renaming it on one activity renames it on every activity that reports it.
- The outcome picker in a `return` ends with **+ New outcome…**, which makes the constant and adds it to that
  activity's outcomes.

### Changed

- **The plugin's windows follow your theme.** Remote Pilot, Project Setup, Bot Settings, the capture-source
  picker and the emulator dialogs drop their hard-coded greys, oranges and greens for the theme's text roles,
  so they read in the dark themes too. Overlays drawn over a live game keep their own dark look.
- **The plugin's windows reopen at the size you left them.** The Activity Flow, Resource Manager, Tags,
  picture picker, New activity, Project Setup, Bot Settings and capture-source windows are built by the
  toolkit's `Modals.window`, and come back at the size they last closed at in the session.
- **Background checks report their failures.** The emulator, phone, Remote Pilot, launch, text-reading and
  picture-matching windows do their slow work through the toolkit's `Async`; a launch or a Remote Pilot start
  that fails says why, as before, and a failed object-capture solve no longer leaves its spinner turning.
- **Nothing changes for a bot.** The plugin half declares `Pictures`, `Activities` and `Outcomes` as open sets
  of `ImageTemplate`, `Activity` and `Outcome` (the plugin contract's typed open sets), and changes them through
  the toolkit's `ManagedSet`, so a constant of the wrong kind cannot be added to one.

## [1.2.3] — 2026-10-01

### Fixed

- **1.2.2 never reached JitPack: this release is the one to install.** The build command lost a space
  (`-B-Dbotmaker.shared.version=…`), Maven refused it, and `com.github.LiQiyeDev:botmaker-sdk:v1.2.2` has no
  artifacts. Nothing in the library changed.

## [1.2.2] — 2026-10-01

### Added

- **`Chance`, random numbers for a bot** (`com.botmaker.sdk.api.random`): `between(min, max)` (a whole number,
  both ends included), `chance(probability)` (a coin flip weighted by `probability`), `pick(options…)` (one of
  some text) and `duration(min, max)` (a random length of time). There was no random in the SDK at all. A
  random pause stays `Wait.between(min, max)`.
- **`Ask`, a question for the person running the bot** (`com.botmaker.sdk.api.console`): `text`, `number`,
  `whole`, `yesNo` and `choice(prompt, options…)`. Under Studio it is a dialog sent over the run's telemetry
  channel; anywhere else the prompt is printed and the answer read from the console. An answer that does not
  read is asked again; a cancel throws `CancellationException`. Needs a Studio and `botmaker-shared` from the
  same release.

### Removed

- **`BotMaker`** (`print`, `readLine`, `readInt`, `readDouble`, `readBoolean`) and the `BM-INPUT` marker its
  reads printed on stdout: a question is `Ask`, and printing is `System.out.println`.
- **Plumbing a bot never writes left `api` for `internal`**: `Game`, `Target`, `LaunchTarget`, `Emulator`,
  `Emulators`, `EmulatorRef`, `EmulatorSource`, `Session`, `Watchdog`, `BotStuckException`, `Flows`, `Window`.
- **`Direction`**, which no call took, with its arrow-pad editor.
- **`Bot.start(…)`** (both forms) and **`StartMode`**: `Bot.run` is the entry point.
- **The launch annotations** (`@SteamAppId`, `@EpicAppName`, `@HeroicAppName`, `@FaugusGameId`,
  `@ProgramPath`, `@LaunchOption`) and their game-grid editors: nothing offered carried them.
- **Second spellings of the vision calls.** Each operation keeps two shapes, plain and `CaptureSource`
  (Studio's upgrade repairs a bot):
  - every per-call `double confidence` and compare `margin` overload of `ImageFinder`, `ImageClicker` and
    `ImageWaiter` — both are `BotSettings`' (`BotSettings.use(…)` to change them for a while);
  - `ImageClicker.click(template, source, confidence, delayMs)` — the pause is `BotSettings`' `foundDelay`;
  - `findAny`/`clickAny` over `ImageTemplate...` — pass an `ImageTemplateGroup`;
  - the whole-seconds `int` timeouts of `ImageWaiter` and the `long` milliseconds ones of `Pixel` and
    `Text` — a timeout is a `Duration`;
  - `Pixel.colorAt(x, y…)` and `matchesAt(x, y, …)` — take a `Point`; `Pixel.find(color)`,
    `find(color, source)` and `findInRange(low, high)`, which defaulted the `Precision` — pass one.
- **`@Setting`**, the annotation on `BotSettings`' setters that told Studio how to draw them. No bot wrote it;
  the ⚙ Bot Settings sliders read the same labels and ranges from the SDK plugin now.
- **`ImageTemplate`'s threshold** (`threshold()`, `setThreshold`, the two-argument constructor): no matcher
  read it and nothing set it; how sure a match must be is `BotSettings`' confidence.

### Changed

- **The packages are named by what a bot does** (imports move; Studio's upgrade repairs a bot):
  `api.interaction` is `api.input` (`Mouse`, `MouseButton`, `Keyboard`, `Key`, `Combo`, `KeySequence`);
  `Wait` and `Time` are `api.time`; `Debug` and `BotMaker` are `api.console` (was `api.util`); OCR —
  `Text`, `TextMatch`, `TextResult`, `OcrOptions`, `OcrLanguage` — is `api.text`; `ActivityBody` is
  `api.flow`.
- **The last text match is `Text.lastMatch()`** (and `lastMatchList`, `lastMatchFound`, `clearLastMatch`,
  `ifLastMatch`), no longer `Vision.lastTextMatch()` and its family.
- **An emulator source is `CaptureSource.emulator("name")`.**
- **`api.*` breaks freely**: the never-delete rule and its japicmp gate are retired.
- **The palette offers the calls a bot makes and nothing else.** Thirty-seven value and plumbing types lost
  `@Palette` + `@Hidden` (contract 0.4.0: `@Palette` means offered). The value types — `Point`,
  `MatchResult`, `ImageTemplate`, `Key` and the rest — are still recognised because an offered call takes or
  returns them; `Debug`, `Watchdog`, `Session`, `PopupGuard`, `Flows` and `BotMaker` no longer appear in
  Studio at all. Offered classes are listed alphabetically.
- **An activity body takes no argument**: `public static Outcome body()`, returning
  `Activities.outcome("BAG_FULL")` or `Activities.next()`. The flow knows which activity is running, so an
  undeclared outcome is still reported on the console, and Studio's outcome picker now lists only the
  outcomes of the activity whose body the call sits in (every outcome elsewhere). `ActivityBody.run()` has no
  parameter.
- **`Emulators`, `Game` and `Target` are no longer in the palette**: the run connects and launches the
  project's target itself. They stay public for a hand-written bot.
- **`Time` is ten members**: `now`, `today`, `currentTime`, `hour`, `minute`, `dayOfWeek`, `isBetween`,
  `isDay`, `isMonth`, `format`. It reads the machine's own zone.
- **Studio traces every call your bot makes into the SDK**, so `Mouse`, `Wait` and `Keyboard` no longer print
  a line of their own for each call. Run from Studio, the trace reads `[Mouse] click(…)  3 ms`.
- **A debug line is named after the class that wrote it, and only that**: `[ImageFinder]`, not `[Vision]`;
  `[PopupGuard]`, not `[Popup]`.
- **Errors show with debugging off.** `Debug.error` prints and traces whatever the debug switch says, an
  activity that throws logs its name and stack before the bot recovers, and a thread that dies of an
  uncaught exception says so. The run connects to Studio's trace as it starts, not at its first vision call.
- **A collapsed run's count is shown once**: `ore not found over 3.4s (×47)`, not `×47` in the text and again
  as the count.
- `Bot.run` and both `Bot.start` are `@Untraced`: they hold the whole run.

### Removed

Deleted outright rather than deprecated — nothing used them.

- `ActivityContext` (use `Activities.outcome`/`next`/`enable`/`disable`).
- `@TraceSource`: a line is traced under its class's own name, which the Trace tab filters by.
- `Images` (a picture named by a string; the `@Managed` `Pictures` constants replace it).
- `Emulator.platform()`, `EmulatorRef.platform()` and `Window.targetWindow()`, which named botmaker-shared
  types a bot cannot write down. `Window.capture()` stays for the vision layer and is `@Hidden`.
- `Time`: `second`, `millisecond`, `dayOfMonth`, `month`, `year`, `nowUtc`, `hourUtc`, `minuteUtc`,
  `secondUtc`, `millisecondUtc`, `now(ZoneId)`, `now(String)`, `getDefaultTimeZone`, both
  `setDefaultTimeZone`, `formatUtc`, `elapsedMillis`, `elapsedSeconds`, `isBetweenUtc`, `currentTimeMillis`,
  `nanoTime`.

## [1.2.1] — 2026-09-29

### Added

- **The Remote Pilot takes keys and text.** `{"cmd":"input","kind":"key","key":"ENTER"}` presses an `api.interaction.Key`
  by name and `{"kind":"text","text":…}` types (control characters dropped, 256 at most), on the route the last
  frame came from: a nested session's controller, an emulator's `input keyevent`/`input text`, or the host
  `:0`. **On `:0` a key is sent only while the focused window is the streamed frame** (each edge within
  16 px), since a key cannot be clamped to the frame the way a tap is and the pilot may be reachable over
  Funnel; otherwise the phone gets `{"type":"notice","text":…}` saying to tap the window first. On connect the
  server announces `{"type":"input","kinds":[…]}` so a phone shows its keyboard only when the host takes keys.
  Both new messages are in the shared wire corpus (`pilot-wire/wire-golden.json`, digest moved in both repos).
- **The bundled BotPilot client** (`src/main/resources/pilot/`) is rebuilt with pinch-zoom and the keyboard.

### Changed

- **An input of a kind this SDK does not know is logged** (`Pilot: ignoring input of unknown kind "…"`) and
  still ignored, where it was dropped without a word.

## [1.2.0] — 2026-09-29

### Added

- **The Remote Pilot says why a phone cannot connect, on both ends.** Over Tailscale, the pairing dialog shows
  each phone on the tailnet and whether Tailscale sees it online (`○ Pixel 10 — offline in Tailscale, last seen
  9 days ago`), with what to do on the phone. On the phone, *Can't reach this connection* now lists the steps
  for the way it was paired: Tailscale (connect, Always-on VPN, battery Unrestricted), Funnel, a quick tunnel
  (its address changes, scan again) or the local network (same Wi-Fi).
- **The Remote Pilot's pairing dialog lets you pick how the phone reaches it**, from four free ways:
  Tailscale (the default), Tailscale Funnel, a **Cloudflare quick tunnel** (a public `trycloudflare.com`
  address with no account and nothing on the phone; it needs `cloudflared` installed, and the address changes
  each time the pilot starts) and the local network. The choice is remembered. When the one you picked cannot
  start, the pilot falls back to Tailscale or the local network and says why; it never falls back to a public
  address. Behind Funnel or a tunnel the server listens on this computer only, and the token is still
  required.

- **A run started from Studio sends its debug output to Studio as structured lines**: level, source (the
  `[Vision]`, `[Game]`… it starts with), how many times a repeated line happened, and where on screen. The
  console output is unchanged.
- **`-Dbotmaker.debug=true|false` on the run wins over the `debug` in your Bot Settings.** The setting still
  decides for a run that does not say.
- **`Debug.log` names the class that wrote the line.** `Debug.log("hello")` in `Collect` prints
  `[Collect] hello`, so you never write the prefix yourself. Put `@TraceSource("Farming")` on a class to show
  another name, or start a message with `[Name]` to name one line.

- **Heroic and Faugus games are picked from a grid.** `launchHeroic` and `launchFaugus` carry
  `@HeroicAppName` and `@FaugusGameId`, so their argument opens the launcher's installed games with cover art,
  as Steam's and Epic's do.
- **Project Setup ▸ Launch target ▸ Choose…** lists the games Steam, Epic, Heroic and Faugus already have on
  this computer and makes the pick what a run launches (a typed `kind:id` works too). Nothing about how to
  start the game is stored; its launcher knows. Background mode can then start a PC game in its own display.
- **Every toolbar button opens through its feature's own `open(ActionContext)`**, listed in the plugin's
  `SdkToolbarItems` with an id constant each. The buttons, their order and what they open are unchanged.
- **Parameter annotations saying what a `String` or a number is for**: `@SteamAppId`, `@EpicAppName`,
  `@ProgramPath`, `@LaunchOption` (`api.launch`), `@EmulatorName` (`api.emulator`), `@ActivityName`,
  `@OutcomeName` and `@Setting(label, …, min, max, step)` (`api.bot`), on the launch calls, the emulator
  calls, `Activities`, `ActivityContext.outcome` and the `BotSettings` withers. Nothing reads them while a bot
  runs; Studio picks each argument's editor by them. `launchSteam(int)` carries none: the game grid writes a
  `String`.
- **BREAKING: the bot's settings are its own Java.** `BotSettings` is a value now, declared in `Sdk.java` as
  `@Managed("settings") public static BotSettings settings()` —
  `BotSettings.of(BotSettings.clicks(500, 200, true), BotSettings.vision(0.8, 0.05), BotSettings.input(false,
  InputBackend.AUTO), BotSettings.session(true, DisplayBackend.AUTO), 20, true)`, or `BotSettings.DEFAULTS` —
  installed by `Bot.run` before the first click. **⚙ Bot Settings** on the toolbar edits it. The static
  setters (`setFoundDelay`, `setDefaultConfidence`, `useRealInput`, `enableDebugMode`, …) are gone: read
  `BotSettings.current()`, and change one for a while with
  `BotSettings.use(BotSettings.current().confidence(0.9))`. A project without the method runs on the defaults.
- **BREAKING: nothing reads `botmaker-project.properties`.** What a bot launches is a fact about this
  computer: the `botmaker.launch.target` system property, which Studio passes to every run and keeps out of
  the project. Debug output and the private display come from the settings above.
- **The Activity Flow's card positions are Java too.** `Sdk.java` gains `@Managed("flow.layout") public
  static FlowLayout flowLayout()`, returning `FlowLayout.of(Map.ofEntries(Map.entry("Collect",
  FlowLayout.at(80, 80)), …), true)`, and 🔀 Activity Flow rewrites it when a card moves or is renamed, so a
  clone opens on its author's canvas. A run ignores it. `flow-layout.json` is no longer written or read; a
  project without the method opens on an arranged canvas and says its positions are not kept.
- **A combo can be held.** `Combo.of(Key.CTRL, Key.S).held(Duration.ofMillis(200))` presses every key, waits
  200 ms, then releases them in reverse; a game that misses a press shorter than a frame now sees it. It reads
  `Ctrl+S (hold 200 ms)`, and the combination window has a *Hold* field in milliseconds with 0, 50, 200 and
  1000 presets. `Combo` gains a `hold` component; `new Combo(keys)` and `Combo.of(…)` still mean no hold.
- **`KeySequence` and `Keyboard.sequence`.** Combos one after another, each followed by a wait —
  `KeySequence.of(KeySequence.step(Combo.of(Key.CTRL, Key.A), Duration.ofMillis(100)), …)`, read as
  `Ctrl+A → 100 ms → Ctrl+C`. `Keyboard.sequence(sequence)` and `Keyboard.sequence(source, sequence)` press
  it, and both are in the palette. Its editor is a row per step (the combination, the wait after it, ✕),
  dragged by ⠿ to reorder, with *Add step*.
- **`Direction.CENTER`**: the match closest to the centre of the frame, the ⊙ in the middle of the direction
  pad. Added after every other constant, so none moves.
- **Every picture picker is the Resource Manager's gallery** (tags, search, large tiles) with *Capture new…*:
  the pictures a capture saves come back selected, ready to confirm. A picture row's ＋ picks several at once.
- **`ImageTemplateGroup` and `CaptureSource` draw themselves.** A group is the picture row; a source is a pill
  opening the capture-source tiles — *Project default* writes `Source.current()`, and a narrowed source reopens
  with its tile selected and its region filled in.

### Changed

- **Finding the bot's line for a trace or telemetry event walks only as far as it needs.** `IpcObserver` took
  a full `Thread.getStackTrace()` for every match, click and swipe while a host watched; it now asks shared's
  `Diag.Callers`, which stops at the bot's first frame, as `TraceSources` does. What is found is unchanged.
- **Without Tailscale, the Remote Pilot no longer listens on every network interface.** It bound `0.0.0.0`,
  which also put it on any VPN, container bridge or other network the computer was on. The local network
  choice binds the computer's Wi-Fi or Ethernet address only, and says who else can reach it.
- **The Remote Pilot shows the bot's log.** On the phone, **📜 Log** opens a drawer with the run's debug lines.
  It has a level floor and a search, and a phone that connects mid-run gets the last 200 lines. The served
  pilot client is rebuilt with it.
- **⚙ Bot Settings no longer shows "Print the bot's debug trace".** Studio's 🐞 Debug button decides debug
  output now, for every plugin and not only the SDK. The value stays in your bot's settings and still applies
  when the bot runs outside Studio. `BotSettings.debug()` is deprecated, with a note saying so.
- **Each debug line says which class and method wrote it**, so Studio's Trace tab can hide one class or one
  method's lines. A lambda counts as the method it is written in.
- **Key, combination, key-step and capture-source pills** label an unread value through the toolkit's
  `Slots.sourceOr`. `SdkScreenPicks` no longer implements a colour pick (the toolkit's `ScreenPicks.color`,
  which nothing called, is deleted); the colour editor's own sampling is unchanged.
- **`SdkPlugin` is one declaration** on the contract's `DeclaredPlugin`:
  `StudioPlugin.id(ID).named(NAME).types(…).parts(…).editors(…).values(…).recorded(…)`. Its `build…`
  overrides and the inline part list are gone; `SdkTypes.PARTS` holds the parts.
- **Types, parts and values are declared by the contract's steps, factories by method reference.** No method
  is named by string except `CaptureSource.region`, which javac cannot reference. The build is derived by
  invoking the factory, so the hand-written builds and their fallbacks are gone: a flow, a setting or a
  capture source whose argument is of the wrong kind is shown as written rather than defaulted.
  `POINT_TYPE`/`RECT_TYPE`/`SIZE_TYPE` are `POINT`/`RECT`/`SIZE`; the private `seeded` helper is
  `MATCH_RESULT`, `MATCHES`, `COLOR_MATCH`, `TEXT_MATCH` (`filledBy(Vision::lastMatch)`); `PictureAt` is a
  `RecordedValue.of(…).at(PictureAt::find)` constant; `SdkValues` uses `ManagedValue.method(…)` and names the
  pictures class through `TemplateNames.CLASS_NAME`.
- **The plugin's types are declared with the toolkit's `Types`.** `SdkTypes`' nested `…Type` classes are
  constants (`IMAGE_TEMPLATE`, `PRECISION`, `POINT_TYPE`, `COMBO`, …); `Point`, `Rect`, `Size` and
  `Precision` are `Types.record`, so their parts are the records' own. The copied `method`/`constructor`/
  `parts` helpers and the `Fixed`/`Shape`/`EnumType`/`SeededType`/`Wither` bases in `SdkTypes`, `FlowTypes`,
  `SettingsTypes` and `CaptureTypes` are deleted. `SdkPlugin` overrides the toolkit's `buildComponentTypes`/
  `buildManagedValues`, and every plugin window takes its owner from `Modals.owner`. No behaviour changes.
- **The keyboard picker fits its labels and knows your layout.** Numpad caps read "7", "+", "Enter" (the chip
  and tooltip still say "Num 7"), two-word caps take two lines, and a long label shrinks its font instead of
  being cut to "N…". **QWERTY / AZERTY / QWERTZ** above the board moves the letters to where your keyboard has
  them and is remembered; the cap labelled A still writes `Key.A`, which your system presses as its A.
- **A combination's keys can be reordered**: drag a chip to where it should be pressed; ✕ takes it out. The
  keypad's arrows (Num Lock off) are recorded as the arrows.
- **A point, size or rectangle's menu lists where to pick directly**: *Edit values…*, then the bot's source,
  another window or screen, and the whole desktop — no second menu after the first. *Edit values…* has
  steppers, a drawing to scale, a rectangle's right and bottom, and takes a pasted `x, y`.
- **Clicking a Precision frame answers.** A crosshair marks the pixel, its colour shows beside the frame as a
  swatch with RGB, hex and ΔE from the target (matches or not), and the blob it belongs to is outlined — in
  any mode, where a click outside a pin mode did nothing. Pins keep their size at any zoom.
- **A key combination takes any keys, in order.** Click or press keys and each is added — two ordinary keys,
  a modifier anywhere — shown as chips beneath the keyboard, each removable, with Clear. It held modifiers plus
  one key before, and opened empty on any other combo.
- **The drawn keyboard grows with its window**, so every cap shows in a small window and the board fills a
  large one; the keys with no place on a US board wrap onto rows of their own instead of one long row.
- **Every type implements `EditableType`** (contract 0.3.0): `ImageTemplate`, `ImageTemplateGroup`,
  `CaptureSource`, `Precision`, `Point`, `Rect`, `Size`, `Direction`, `Key`, `MouseButton`, `Combo`, and the four
  vision results. A result (`MatchResult`, `Matches`, `ColorMatch`, `TextMatch`) is a pill in plain words —
  *Last picture match*, *Pictures found*, *Last colour match*, *Last text read* — with its Java in the
  tooltip: the bot fills it in, so there is nothing to set.
- **Each `@Managed` value is declared once** (`internal.bot.SdkValues`: `FLOW`, `FLOW_LAYOUT`, `CAPTURE`,
  `SETTINGS`, `PICTURES`, typed `ManagedValue<T>` constants). The plugin lists them, `Bot.run` claims them
  through the contract's `ManagedValues`, and the windows read and write them through the toolkit's
  `ManagedHandle`. `SdkPlugin.FLOW`/`CAPTURE`/…, `FlowValue.ID`/`LAYOUT_ID`, `CaptureValue.ID`/`open` and
  `BotSettingsWindow.ID` are gone. No SDK code names plugin-basics any more; the dependency stays for the JDK
  value editors.

### Fixed

- **A bot whose package starts with `com.botmaker.` now reports which of its lines is running.** Studio got
  no line from the worked template (`com.botmaker.gamebot`), because every class under `com.botmaker.` was
  skipped as SDK code. Only the SDK's own packages are skipped now. A debug line also carries the bot class
  that wrote it, so Studio's Trace tab can show the block.
- **Picking a point, a region or a colour off the screen** (Point, Rect and Size pills, the eyedropper,
  capturing a picture in 🖼 Manage Pictures):
  - A region dragged past the edge of the frame is clamped to it, instead of reporting negative or oversized
    numbers.
  - A point is the pixel the lens's crosshair boxes. It used to round to the neighbouring pixel half the time.
  - The overlay stays above a fullscreen game instead of opening behind it.
  - While dragging, a readout shows the region `x, y   w × h` that will be written; the point readout now also
    shows the desktop numbers when the call reads desktop pixels.
  - Every overlay says what to do in a corner, and a right-click cancels it as Esc does.
  - A failed grab says so, instead of doing nothing. On Wayland the message names the screenshot programs
    that work.
  - The overlay opens faster: the frame is no longer encoded to PNG and decoded again first.
  - "Picking on the whole desktop instead" messages read as two sentences.
- **🎯 Capture Source:**
  - A source narrowed to a region is read as that region of its window, screen or emulator. It used to read
    as the whole desktop everywhere in the editor: pixel editors, captures and the pilot all looked at the
    desktop instead.
  - Monitor tiles are numbered as the bot numbers monitors. "Screen 2" could write a bot that read screen 1
    when JavaFX and the OS listed monitors in different orders.
  - The window opens on the project's current source, region included, instead of on the whole desktop.
  - The region fields empty when another tile is clicked, instead of narrowing it with numbers meant for the
    last one. A double-click keeps a typed region.
  - **Draw…** beside the region fields grabs the selected source and lets you drag the region on it;
    **Whole** clears it. The status line names the region that was written.
  - The desktop and the monitor thumbnails come from one grab instead of two. A window listed twice under
    one title shows once. A tile whose preview failed says "No preview" instead of "…" for ever.
  - "Another window or screen…" from a pill's pick menu shows no region row, since it chooses only where to
    pick.
- **✂ Capture Templates:**
  - On a scaled (HiDPI) screen the drawing surface, the object cutter and the toolbar sit over the window
    instead of off to the right and too large. An emulator frame is no longer shrunk by the screen's scale.
  - A picture no longer risks catching the drawing surface's tint and control bar: the save waits for the
    surface to leave the screen before grabbing.
  - The naming dialog and warnings open above a fullscreen game instead of behind it, owned by the editor.
  - Oval pictures have a smooth edge instead of a stair-stepped one.
  - Capture many: Ctrl+Z removes the region drawn last.
  - Capture object: undoing the first box goes back to drawing a box instead of leaving nothing to refine.
  - The toolbar's size readout follows the window when it is resized between captures, and shows the size
    a picture records.
- **🖼 Manage Pictures:**
  - *Replace image ▸ Capture a new picture…* captures from the project's capture source, the way Capture
    Templates does, and records that surface's size. It used to crop the whole desktop and record the
    monitor's size, so the bot rescaled the new picture wrongly. The window comes back when the capture is
    cancelled; it used to stay minimised, and because it is modal it blocked Studio.
  - A picture named before names were lowercase (`Ore`) can be renamed to `ore`. The rename refused it as
    taken by itself.
  - A rename whose file move fails puts the `Pictures` constant back, so the bot still finds the picture.
  - The preview shows a replaced picture straight away.
  - A delete that stops part-way says why. The count used to replace the reason.
  - Missing files are found from the `Pictures` constants as well as from the tags. An untagged picture
    deleted in a file manager went unnoticed until a run failed to load it. *Forget them* says which
    constants stayed because something still uses them.
  - Importing a `.bmtemplates` archive files the pictures under their custom tags. The tags arrived
    undeclared, so every imported picture showed as Untagged.
  - Renaming a tag checks the name as you type, the same way a new tag does, and a change of case is
    allowed.
  - Typing in the search box no longer lists the folder and reads the flow on every keystroke.
- **Picture slots and picture rows:**
  - *Clear* is now *Use the placeholder picture*. It wrote `images/.png`, a file no project has, so the bot
    failed to load it at run time.
  - A slot holding something that is not a picture (a variable, a call) shows it as written instead of
    "Choose a picture…".
  - The gallery opens with the current picture selected. A picture out of view (another tag, a search) is
    brought into view, after a capture too.
  - Picking a picture that has no `Pictures` constant yet declares one, so the block reads `Pictures.ORE`
    and follows a later rename. It used to write the path. A recorded click on such a picture does the same.
  - A picture whose file is missing says so in the pill's tooltip.
- **The precision dialog:**
  - Pressing OK without touching the slider keeps a tolerance written by hand. `Precision.of(60)` became 40
    and `12.34` became 12.3.
  - The two counts keep a value above the spinner's usual ceiling instead of lowering it.
  - A pin dropped before there is a target colour is no longer drawn, since it teaches nothing.
  - Picking a new target colour clears the pins, which were measured from the old one.
  - *Target colour…* with no frame yet puts the frame it grabs under the matches too.
  - Only `Pixel`'s own `matchesAt`, `coverage` and `findInRange` hide a knob. Your own method of the same
    name is offered all three.
- **The colour editor:**
  - A colour it cannot read, such as a variable, is shown as written beside the swatch. The swatch alone
    showed its default white, a colour the bot does not use.
  - A colour picked with the eyedropper is written once, not twice.
  - The eyedropper's frozen frame cancels on a right-click too, like every other pick.
- **Input editors:**
  - A direction pad or mouse drawing that selects nothing, because the value is a variable or a call, shows
    the value as written under it.
  - A mouse button with no part on the drawing is selected when you press it on the strip.
  - A hold or wait field takes digits only. A typo in a combination's hold dropped the whole combination on
    OK, and a typo in a step's wait was ignored.
  - The keyboard window takes a key press as soon as it opens. The search box had the focus, so the key was
    typed there.
- **🔀 Activity Flow:**
  - Renaming an outcome keeps its wire. The card dropped the wire before the rename could carry it across.
  - Renaming an activity keeps it in the presets that switch it on.
  - Saving a preset selects the preset just saved, not the one two before it.
  - A saved preset may not be called Everything or Nothing.
  - Dragging from an outcome that already has a wire moves that wire to the card you drop it on; a drop
    anywhere else leaves it where it was. It used to refuse with "remove that wire first". A click on a port
    no longer wires the card to itself.
  - An activity name that differs from another only in case is refused where you type it, in the side panel
    and in the new-activity dialog. It was accepted, and then the flow could not be saved.
  - The go-home and popup ticks and the outcomes hint name what exists: the home method given to
    `Bot.run`, `PopupGuard`, and `ctx.outcome("…")`. They named `GoHome.run()`, `Popups.run()` and `run()`.
- **⚙ Bot Settings:**
  - A number typed into a field and followed straight by Save is kept. It was dropped unless you pressed
    Enter or left the field first.
  - Save with nothing changed writes nothing.
  - Esc cancels.
  - The snippet for a project with no `settings()` names both imports it needs.
- **📋 Project Setup:**
  - It no longer blocks the rest of Studio while open. It used to, which blocked the very toolbar buttons
    its rows sent you to.
  - The capture row has a **Choose…** button that opens 🎯 Capture Source, and the pictures row a
    **Capture…** button that opens ✂ Capture Templates. The checklist refreshes when either closes.
  - The launch target is optional and says so. Start the game from its own launcher (Faugus on Linux, Steam
    or Epic on Windows), or pick an emulator app, which sets it and lets ▶ Launch now start it.
  - A launch target this computer cannot read is no longer ticked ✓. The row names it and offers **Clear**.
  - Esc closes the window.
- **Emulators:**
  - A device slot holding a constant or a variable shows it as written instead of "Choose a device…".
  - Picking an app inside an emulator says on the status line that it also changed what this computer
    launches and where the bot captures from. Both used to change silently.
  - Closing the emulator picker stops a Start or Stop still waiting for the emulator. The wait used to run
    out its whole boot timeout, then open Waydroid diagnostics over whatever you had moved on to.
  - The picker's and Connect a phone's background checks no longer keep Studio running after it closes.
  - Waydroid diagnostics' "Upstream docs" link no longer freezes the dialog while the browser starts.
- **🎮 Remote Pilot:**
  - Pressing 🎮 again keeps the pilot running. Every press used to restart it, which dropped the paired
    phone and killed the game running in background mode.
  - Pressing it while the pairing dialog is open brings that dialog forward instead of opening a second one.
  - **Reset pairing token** now disconnects the phones connected with the old token. They used to stay
    connected, still watching and driving the bot, until they reconnected.
  - Leaving the project closes the pairing dialog, whose address stops working then.
  - The Funnel setup checklist no longer ticks the HTTPS-certificates step while highlighting it as the
    blocker.
  - Background mode's hint no longer points at a Run ▸ Launch Target… menu that does not exist.
  - Stopping the pilot turns off only its own HTTPS Funnel (`tailscale serve --https=443 off`). It ran
    `tailscale funnel reset`, which also removed anything else you served or funnelled through Tailscale.
- **Launch:** the unused launch-target picker and its game-grid dialog are deleted; nothing called them.
  `Target.current()` and `Target.set` are no longer offered in the palette, since no editor can fill either.
- **Vision results and the palette:**
  - A vision result's pill names its type ("Picture match", "Colour match", "Text found"). It said "Last
    picture match" for every call, `ImageFinder.find(…)` included.
  - A `Text` block with an options slot works when dropped from the palette. Studio filled that slot with
    `null`, which threw from `read` and made every search report "no text"; `null` options now mean the defaults.
  - **Text reading options have an editor.** An `OcrOptions` slot or `@Param` is a pill ("English, lines, ×2")
    opening a dialog: languages, whole lines or single words, only these characters (with *Digits only*), how
    much to enlarge, the clean-up, light text on a dark background, and **Try it**, which reads the bot's
    capture source with those settings. A dropped block starts on what `Text` reads with by default, and a
    chain you wrote (`OcrOptions.defaults().withUpscale(3.0)`) is read as its value.
  - `Time.now(ZoneId)` and `Time.setDefaultTimeZone(ZoneId)` take the `null` a dropped block is written with:
    `now` uses the default timezone, and `setDefaultTimeZone` goes back to the system one instead of throwing.
  - Waits and timeouts dropped from the palette wait. Each timeout call has a `Duration` shape that the palette
    now leads with — `ImageWaiter.waitFor`/`waitUntilGone`/`waitAndClick`, `Text.waitFor`/`waitForGone`,
    `Pixel.waitFor`/`waitForGone`, `Game.waitForLaunch`/`waitForDefaultSource`/`launchAndWait`,
    `Target.waitForLaunch` — and a new `Duration` starts at one second. The seconds and milliseconds shapes
    started at `0` and gave up at once; they stay, one step away in the same menu.
  - The Flow menu (`Flows`) is no longer offered. A dropped `use` was written `Flows.use(null)` and cleared the
    bot's flow; Bot ▸ Activities (`active`, `enable`, `disable`, with the activity picker) covers the rest.
- **Desktop picks work on GNOME and Sway under Wayland** wherever a bot's own capture does: the plugin reads
  the desktop through shared's capture, which now knows grim and gnome-screenshot as well as Spectacle.
- **Renaming a picture in 🖼 Manage Pictures no longer breaks the bot.** It renamed `Pictures.ORE` at every
  use and left the constant itself called `ORE`, so the bot stopped compiling. The constant and every use are
  renamed together now, and a rename that would not compile is refused before the file moves. Deleting a
  used picture points its uses elsewhere and then removes its constant; a static import of a picture is
  found too.
- **A captured or imported picture gets its `Pictures` constant**, so a block that picks it reads
  `Pictures.ORE` rather than the path. A path you typed in your own code is yours and is no longer rewritten.
- **Picking a colour on screen sets it.** A click on the frozen frame threw and changed nothing: closing the
  sampler forgot the pixel under the pointer before it was read.

### Removed

- **The SDK's own Duration editor.** Basics draws the JDK types; a Duration slot gets basics' picker (presets,
  spinners that carry, the length in words), so a slot no longer asks which of two editors to use. `Color`'s
  eyedropper stays, as an alternative to basics' swatch.

## [1.1.17] — 2026-09-27

### Added

- **`Sound.miaou()`** (`api.sound`, palette category *Sound*): meows like the Scratch cat and waits until it
  is done. The sound is synthesised, so nothing is recorded or bundled; on a machine with no audio output it
  plays nothing and says so once.
- **Every key a keyboard has.** `Key` gains the US punctuation keys (`BACKQUOTE` … `SLASH`), `HOME`, `END`,
  `PAGE_UP`, `PAGE_DOWN`, `INSERT`, `CAPS_LOCK`, `NUM_LOCK` and the numpad (`NUMPAD_0` … `NUMPAD_9`, the four
  operators, `NUMPAD_DECIMAL`, `NUMPAD_ENTER`), appended so no existing constant moves. Windows has no key code
  of its own for the numpad's Enter, so there `NUMPAD_ENTER` is `ENTER`. `Key.label()` is the text on the cap.
- **`Direction` diagonals**: `NORTH_EAST`, `NORTH_WEST`, `SOUTH_EAST`, `SOUTH_WEST`.
- **`Combo`**, keys pressed together as one value (`Combo.of(Key.CTRL, Key.S)`, read as `Ctrl+S`), and
  `Keyboard.combo(Combo)` / `combo(CaptureSource, Combo)`. Recording Ctrl+S writes it. `combo(Key...)` is kept
  and still works; the palette leads with the `Combo` shape.

### Changed

- **A `Key` is picked on a drawn keyboard**: click a cap, press the key, or search ("page", "num 5"). It was a
  dropdown of constant names. A **`Combo`** is picked on the same keyboard: Ctrl, Alt, Shift and Meta toggle
  beside one other key, or press the whole combination at once; it is written modifiers first.
- **A `MouseButton` is picked on a drawn mouse**: click the left or right button, the wheel or a side button, or
  click the strip under it with the button you mean. **The direction pad has its diagonals**, one square each.

- **Screen picks ask where to pick.** Point, Rect, Size and the colour eyedropper open a small menu — the bot's
  own source, another window or screen, or the whole desktop — and pick on a frozen frame of it. A value in a
  call that takes a capture source is written relative to the chosen surface, so it survives the window moving;
  one in a call that takes desktop pixels (`Mouse.click(Point)`) is written in desktop pixels. A Parameters row's
  pill says "in window". The eyedropper's magnifier and ΔE spread work on every surface.
- **Precision shows what it matches.** The dialog opens on a frozen frame (switchable to any window or screen;
  Ctrl+scroll zooms, middle-drag pans) with every pixel within ΔE of the target tinted and each blob boxed —
  solid when it is big enough, dashed grey when it is too small — and the coverage against `minCount` under it.
  The target is the `Color` passed beside the precision, read by the host; an eyedropper stands in where that
  cannot be read. The tolerance is shown as the darkest and lightest colours it accepts, and *Should match* /
  *Should not* pins set ΔE to the smallest value that takes every good pin, naming a bad one it cannot keep
  out. A taught ΔE keeps its tenth; a dragged one still lands on a whole number.

## [1.1.16] — 2026-09-26

No source changes since v1.1.15; re-released for updated upstream pins.

- **Fixed: the Activity Flow window can always be closed.** When the flow cannot be saved (the project has no
  `Sdk.java`, or its flow was written by hand), Close asks whether to close without saving. Before, the
  window refused to close. The message about a missing `Sdk.java` no longer promises that adding the SDK
  creates the file.
- **A project with no `Sdk.java` gets one.** Studio writes `plugins/sdk/Sdk.java` (an empty flow, the whole
  desktop as the capture source) and `plugins/sdk/Pictures.java` as soon as the SDK is in a project that has
  neither, so the Activity Flow window no longer carries a *Create Sdk.java* button. Add `Sdk.class` to
  `Bot.run(…)` in `main` yourself; Studio never edits `main`. Picking a capture source in a project without
  the file still creates it, instead of the pick going nowhere. Needs studio-api 0.3.0.

This is **2.0.0**, the one release that removes `api.*` elements. From it on, never-delete applies:
`api.*` only grows.

**Built against studio-api 0.3.0** (`TypeRef` asks by class, a slot's call is an `Executable`), so it needs
a Studio released with that contract. Nothing under `api.*` changes for it:

- **Call-site editors claim the resolved call, and type editors the class.** The Steam, Epic, launch,
  setting, emulator, activity and outcome editors match the method Studio resolved, so a namesake `Game` in
  another package is no longer claimed and `game.launch(…)` on a variable is. A launch flag starts at the
  overload's own varargs rather than at a per-name table. The `Duration` and `Color` editors match
  `java.time.Duration` and `java.awt.Color` by class; a bot class called `Duration` is not theirs. Every
  method name is checked against its class when the editors load (`CallSitesTest`).

### BREAKING

Every older way of writing an activity or a flow is gone. A bot's flow is the `Flow` value its
`plugins/sdk/Sdk.java` returns, and its `main` is `Bot.run(goHome, Sdk.class)`.

| Removed | Use instead |
|---|---|
| `api.bot.Activity<O>` (subclass per activity) | a `public static Outcome body(ActivityContext ctx)`, named in the flow as `Flow.activity(Collect::body, …)` |
| `Activity.enable/disable/setEnabled(String)` | `Activities.enable/disable/setEnabled(String)` |
| `Activities.define(name, ctx -> …)` | a body method, named in the flow by method reference |
| `api.flow.FlowGraph` (`load`, `run`, `walk`, `of`, `node`, `route`, `Node`, `Route`) | `Bot.run(goHome, Sdk.class)` walks the installed `Flow` |
| `FlowGraph.run(Main.class, goHome)` inside `Bot.start` | `Bot.run(goHome, Sdk.class)` |
| `api.flow.PopupCheck`, `api.flow.Recovery` | `Flow.Activity.popupCheck()` and `goHome()`, booleans on the activity |
| `Bot.run(Class<?> anchor, Runnable goHome, Class<?>... values)` | `Bot.run(Runnable goHome, Class<?>... values)`; the anchor found generated activity classes, which no longer exist |
| `com.botmaker.sdk.api.meta.{ReplacedBy, Replaces, Since}` | `com.botmaker.plugin.api.meta.ReplacedBy` |
| `BotSettings.defaultCaptureSource()` | `Source.current()` |

A pre-2026-08-29 project whose activities are generated `<package>.activities.<Name>` classes no longer
runs them. Move each `run()` into a body method and name it in the flow.

### Added

- **`Activities.enable`, `disable`, `setEnabled`**, beside `active`: switching the flow's activities on
  and off by name, which `ctx.disable()` also calls.
- **`Mouse.doubleClick`, `rightClick`, `middleClick(CaptureSource, x, y)` and
  `Mouse.drag(CaptureSource, Point, Point, durationMs)`**: the same gestures as the `Point` forms, relative to
  a capture source's top-left corner, so a recording made over a window replays wherever the window is.
- **Studio's recorder writes this SDK's calls.** Twelve methods carry `@Records` — `Mouse.click` and the four
  above, `scrollUp`/`scrollDown`, `Keyboard.type`/`tap`/`combo`, `Wait.time`, `ImageClicker.click(ImageTemplate)`
  and `ImageWaiter.waitFor(ImageTemplate, int)` — and a click on one of the project's pictures is recognised
  as that picture.
- **`CaptureSource.region(CaptureSource of, Rect sub)`**, the same narrowing as `of.region(sub)` written as
  one call. It is how Studio writes a picked region, since it writes a value as a factory call, never a
  chain.
- **Studio reads a capture source as a value.** `Source.current()`, `CaptureSource.desktop()`,
  `.monitor(i)`, `.window("t")`, `new EmulatorSource("n")` and `CaptureSource.region(…)` are each declared to
  the host, so the capture picker, the pilot and the editors' frame grab read the project's source without
  this plugin parsing it, and a recorded click writes `Mouse.click(Source.current(), x, y)` with its import.
  `ImageTemplateGroup.of(…)` is declared the same way.
- **Studio reads the chains you write by hand.** `CaptureSource.window("Game").region(r)` and
  `Precision.TIGHT.minArea(400)` (and `.tolerance(d)`, `.minCount(n)`) are declared to the host as
  instance-method factories (`CaptureTypes.REGION_CHAIN`, `SdkTypes.PRECISION_WITHERS`), so they draw as
  editable pills. Studio reads them and never writes them: an edit is written as
  `CaptureSource.region(source, rect)` or `new Precision(…)`.

### Changed

- **Every call a value is written as is looked up as a real method.** `FlowTypes` and `CaptureTypes`
  hand the host the `Method` (or, for an emulator, the constructor) that writes each value, and derive
  their parts from its parameters, so the two cannot disagree. A renamed factory now fails this plugin's
  own tests instead of a bot's build.
- **The plugin half moved to `com.botmaker.sdk.plugin`.** What was `com.botmaker.sdk.internal.plugin`,
  `com.botmaker.sdk.internal.authoring` and `com.botmaker.sdk.authoring` is now one `plugin` package tree
  (`types`, `editors`, `pictures`, `screen`, `source`, `pilot`, `flow`, …). None of it was under contract
  and a bot never needed it. `TemplateNames` moved to `internal.vision`. A bot that imported
  `com.botmaker.sdk.authoring.TemplateLibrary` must drop that import.
- **The duration editor edits a length and nothing else.** Its *Random range* toggle rewrote `Wait.time(x)`
  into `Wait.between(a, b)` through the call's Java text, which Studio no longer hands a plugin;
  `Wait.between` is in the palette. The editor reads and writes a `Duration` value, which Studio writes as
  `Duration.ofMillis(n)`.
- **The precision editor shows a hand-written wither chain as written** (`Precision.TIGHT.minArea(400)`)
  and opens its dialog on the defaults, rather than parsing the chain. Its preview no longer takes the colour
  from the call's `new Color(…)` argument; sample one from the game.

### Removed

- **⏺ Record Macro and ⏺ Record at cursor.** Recording is Studio's: its overlay HUD records and inserts at the
  cursor, writing the calls above.
- **`Flow.Edge.NEXT` and `Flow.Edge.DISABLED`**, the two outcomes every activity has without declaring them,
  plus `Edge.outcomeOrNext()` and `Edge.isDisabled()`. They were the constants of an editor-only record,
  `FlowEdgeModel`, which is gone: the flow editor now holds the same `Flow.Edge` a bot's `Sdk.java` writes.

### Fixed

- **The canvas's "managed value" notes and the 📋 Project Setup tooltip name the right place.** The flow's
  note pointed at "✂ Activity Flow" (the button is 🔀), the capture source's at "Project ▸ Settings" (it is
  🎯 Capture Source), and the tooltip still listed a reference resolution the checklist no longer has.
- **The Remote Pilot no longer drops a message for a window title with a newline or tab in it**, nor a
  match whose confidence was never computed. Both produced text the phone could not parse, and it
  discarded the message without a word. A missing confidence is now sent as `0`.
- **`Flow.limits(0, …)` means no step limit, as documented.** The walk treated `0` as "stop before the
  first activity".
- **The palette offers `Activities` and `Flows`.** Both carried `@Palette` but were missing from the
  plugin's hand-written class list. The list is gone: the host now catalogues every `@Palette` class in
  the SDK's jar, so an annotated class cannot be left out again.
- **An activity with no method yet is written as `ActivityBody.NONE` again**, and reads back as a card with
  no body. Since the codecs went, the flow wrote a blank body as nothing at all, which declined the whole
  flow — so a flow holding a freshly drawn card could not be saved.
- **Studio can read the flow at all.** The five records a `Flow` is written as (`Flow.of`,
  `Flow.activity`, `Flow.edge`, `Flow.preset`, `Flow.limits`) are handed to the host through the
  contract's new `componentTypes()`; they had no way to reach it.

### Added

- **`Bot.run(anchor, goHome, Sdk.class)` — the whole of a bot's `main`.** It installs every `@Managed` value
  the classes you name declare, then starts the flow. What it replaces is a hand-written `Sdk.install()`:
  one line per plugin, in a file you own, and a bot that lost that line ran with no flow and said nothing.
  Your bot still *names* each plugin's values class — javac checks that — and no longer says what to do with
  it.

  ```java
  public final class Gamebot extends Bot {

      public static void main(String[] args) {
          run(Gamebot.class, Gamebot::goHome, Sdk.class);
      }
  }
  ```

  `Sdk.install()` still works and is not deprecated: it is your file, and `Flows.use` and `Source.set` are
  unchanged.

### Changed

- **`Bot` is no longer `final`** and has a `protected` constructor, so `extends Bot` lets the entry point
  above read as one line. `Bot.run(…)` spelled in full does the same thing for a bot that does not extend
  it. Nothing that compiled before stops compiling.
- **The SDK no longer ships `Sdk.java` and `Pictures.java` for the host to copy in.** A new project gets
  them from the template it is created from. Adding the SDK to a project that has neither brings neither —
  the flow window offering to write one is owed and not in this release.
- **`SdkPlugin` no longer declares a parameter section**, and no longer answers a parameter row or a
  parameter edit. The contract surface is deleted: the SDK's group was the only one in existence, it declared
  no rows, and `botmaker-plugin-basics`' `ParameterStore.declare` had no caller — so what the Parameters
  window read out of this plugin was a pre-2026-09-17 project's JSON and nothing else. **Nothing changes for
  a bot**, and nothing is lost in the editor: a parameter is a `@Param` field in your own Java, and one this
  plugin wants for itself goes in `plugins/sdk/Sdk.java`, which the window already reads.
- **The plugin contract is a `compile` dependency**, so it reaches your bot rather than stopping at this
  jar. `@Param` and `@Managed` moved onto the contract and sit on your *own* fields and methods, so your bot
  needs that jar to compile at all; at `provided` it resolved no copy and failed on its own `@Param` line.
  Nothing about how a plugin links changes — `PluginLoader` is parent-first for `com.botmaker.plugin.api.**`,
  so a plugin still links the host's copy and there is one `Class` on both sides. **The one thing to know:**
  do not declare `botmaker-studio-api` yourself beside a plugin that brings it, or nearest-wins pins you to a
  contract version your plugin was never built against.
  **Your bot's imports change with it**: `com.botmaker.plugin.basics.params.Param` is now
  `com.botmaker.plugin.api.params.Param`, `…basics.managed.Managed` is `…api.managed.Managed`, and
  `@Param(min, max)` are numbers, so `min = "1"` becomes `min = 1`.
- **🎯 Capture Targets is 🎯 Capture Source, and it picks one thing.** A project kept a *list* of targets in
  `capture.json` with one marked default; that file is deleted and a project's capture source is the
  expression `Sdk.captureSource()` returns, which is one source. So the list manager is the picker it always
  opened. Nothing read the other entries.
- **Nothing is snapped to a reference resolution before a capture.** It came from `capture.json` too, and
  each picture already records the size it was authored at in its own sidecar — which is what the matcher
  rescales against. *Project Setup* has two required steps rather than three for the same reason.

- **Otherwise nothing changes for a bot.** The SDK's plugin half was recompiled against the plugin
  contract's new package layout (`com.botmaker.plugin.api.slot`, `.parameters`, `.toolbar`, `.source`,
  `.value`). No `api.*` type, method or behaviour changed — japicmp holds that — and a bot never writes a
  contract name down.

## [1.1.15] — 2026-09-23

This is **2.0.0**, the one release that removes `api.*` elements. From it on, never-delete applies:
`api.*` only grows.

### BREAKING

Every older way of writing an activity or a flow is gone. A bot's flow is the `Flow` value its
`plugins/sdk/Sdk.java` returns, and its `main` is `Bot.run(goHome, Sdk.class)`.

| Removed | Use instead |
|---|---|
| `api.bot.Activity<O>` (subclass per activity) | a `public static Outcome body(ActivityContext ctx)`, named in the flow as `Flow.activity(Collect::body, …)` |
| `Activity.enable/disable/setEnabled(String)` | `Activities.enable/disable/setEnabled(String)` |
| `Activities.define(name, ctx -> …)` | a body method, named in the flow by method reference |
| `api.flow.FlowGraph` (`load`, `run`, `walk`, `of`, `node`, `route`, `Node`, `Route`) | `Bot.run(goHome, Sdk.class)` walks the installed `Flow` |
| `FlowGraph.run(Main.class, goHome)` inside `Bot.start` | `Bot.run(goHome, Sdk.class)` |
| `api.flow.PopupCheck`, `api.flow.Recovery` | `Flow.Activity.popupCheck()` and `goHome()`, booleans on the activity |
| `Bot.run(Class<?> anchor, Runnable goHome, Class<?>... values)` | `Bot.run(Runnable goHome, Class<?>... values)`; the anchor found generated activity classes, which no longer exist |
| `com.botmaker.sdk.api.meta.{ReplacedBy, Replaces, Since}` | `com.botmaker.plugin.api.meta.ReplacedBy` |
| `BotSettings.defaultCaptureSource()` | `Source.current()` |

A pre-2026-08-29 project whose activities are generated `<package>.activities.<Name>` classes no longer
runs them. Move each `run()` into a body method and name it in the flow.

### Added

- **`Activities.enable`, `disable`, `setEnabled`**, beside `active`: switching the flow's activities on
  and off by name, which `ctx.disable()` also calls.
- **`Mouse.doubleClick`, `rightClick`, `middleClick(CaptureSource, x, y)` and
  `Mouse.drag(CaptureSource, Point, Point, durationMs)`**: the same gestures as the `Point` forms, relative to
  a capture source's top-left corner, so a recording made over a window replays wherever the window is.
- **Studio's recorder writes this SDK's calls.** Twelve methods carry `@Records` — `Mouse.click` and the four
  above, `scrollUp`/`scrollDown`, `Keyboard.type`/`tap`/`combo`, `Wait.time`, `ImageClicker.click(ImageTemplate)`
  and `ImageWaiter.waitFor(ImageTemplate, int)` — and a click on one of the project's pictures is recognised
  as that picture.
- **`CaptureSource.region(CaptureSource of, Rect sub)`**, the same narrowing as `of.region(sub)` written as
  one call. It is how Studio writes a picked region, since it writes a value as a factory call, never a
  chain.
- **Studio reads a capture source as a value.** `Source.current()`, `CaptureSource.desktop()`,
  `.monitor(i)`, `.window("t")`, `new EmulatorSource("n")` and `CaptureSource.region(…)` are each declared to
  the host, so the capture picker, the pilot and the editors' frame grab read the project's source without
  this plugin parsing it, and a recorded click writes `Mouse.click(Source.current(), x, y)` with its import.
  `ImageTemplateGroup.of(…)` is declared the same way.
- **Studio reads the chains you write by hand.** `CaptureSource.window("Game").region(r)` and
  `Precision.TIGHT.minArea(400)` (and `.tolerance(d)`, `.minCount(n)`) are declared to the host as
  instance-method factories (`CaptureTypes.REGION_CHAIN`, `SdkTypes.PRECISION_WITHERS`), so they draw as
  editable pills. Studio reads them and never writes them: an edit is written as
  `CaptureSource.region(source, rect)` or `new Precision(…)`.

### Changed

- **Every call a value is written as is looked up as a real method.** `FlowTypes` and `CaptureTypes`
  hand the host the `Method` (or, for an emulator, the constructor) that writes each value, and derive
  their parts from its parameters, so the two cannot disagree. A renamed factory now fails this plugin's
  own tests instead of a bot's build.
- **The plugin half moved to `com.botmaker.sdk.plugin`.** What was `com.botmaker.sdk.internal.plugin`,
  `com.botmaker.sdk.internal.authoring` and `com.botmaker.sdk.authoring` is now one `plugin` package tree
  (`types`, `editors`, `pictures`, `screen`, `source`, `pilot`, `flow`, …). None of it was under contract
  and a bot never needed it. `TemplateNames` moved to `internal.vision`. A bot that imported
  `com.botmaker.sdk.authoring.TemplateLibrary` must drop that import.
- **The duration editor edits a length and nothing else.** Its *Random range* toggle rewrote `Wait.time(x)`
  into `Wait.between(a, b)` through the call's Java text, which Studio no longer hands a plugin;
  `Wait.between` is in the palette. The editor reads and writes a `Duration` value, which Studio writes as
  `Duration.ofMillis(n)`.
- **The precision editor shows a hand-written wither chain as written** (`Precision.TIGHT.minArea(400)`)
  and opens its dialog on the defaults, rather than parsing the chain. Its preview no longer takes the colour
  from the call's `new Color(…)` argument; sample one from the game.

### Removed

- **⏺ Record Macro and ⏺ Record at cursor.** Recording is Studio's: its overlay HUD records and inserts at the
  cursor, writing the calls above.
- **`Flow.Edge.NEXT` and `Flow.Edge.DISABLED`**, the two outcomes every activity has without declaring them,
  plus `Edge.outcomeOrNext()` and `Edge.isDisabled()`. They were the constants of an editor-only record,
  `FlowEdgeModel`, which is gone: the flow editor now holds the same `Flow.Edge` a bot's `Sdk.java` writes.

### Fixed

- **The canvas's "managed value" notes and the 📋 Project Setup tooltip name the right place.** The flow's
  note pointed at "✂ Activity Flow" (the button is 🔀), the capture source's at "Project ▸ Settings" (it is
  🎯 Capture Source), and the tooltip still listed a reference resolution the checklist no longer has.
- **The Remote Pilot no longer drops a message for a window title with a newline or tab in it**, nor a
  match whose confidence was never computed. Both produced text the phone could not parse, and it
  discarded the message without a word. A missing confidence is now sent as `0`.
- **`Flow.limits(0, …)` means no step limit, as documented.** The walk treated `0` as "stop before the
  first activity".
- **The palette offers `Activities` and `Flows`.** Both carried `@Palette` but were missing from the
  plugin's hand-written class list. The list is gone: the host now catalogues every `@Palette` class in
  the SDK's jar, so an annotated class cannot be left out again.
- **An activity with no method yet is written as `ActivityBody.NONE` again**, and reads back as a card with
  no body. Since the codecs went, the flow wrote a blank body as nothing at all, which declined the whole
  flow — so a flow holding a freshly drawn card could not be saved.
- **Studio can read the flow at all.** The five records a `Flow` is written as (`Flow.of`,
  `Flow.activity`, `Flow.edge`, `Flow.preset`, `Flow.limits`) are handed to the host through the
  contract's new `componentTypes()`; they had no way to reach it.

### Added

- **`Bot.run(anchor, goHome, Sdk.class)` — the whole of a bot's `main`.** It installs every `@Managed` value
  the classes you name declare, then starts the flow. What it replaces is a hand-written `Sdk.install()`:
  one line per plugin, in a file you own, and a bot that lost that line ran with no flow and said nothing.
  Your bot still *names* each plugin's values class — javac checks that — and no longer says what to do with
  it.

  ```java
  public final class Gamebot extends Bot {

      public static void main(String[] args) {
          run(Gamebot.class, Gamebot::goHome, Sdk.class);
      }
  }
  ```

  `Sdk.install()` still works and is not deprecated: it is your file, and `Flows.use` and `Source.set` are
  unchanged.

### Changed

- **`Bot` is no longer `final`** and has a `protected` constructor, so `extends Bot` lets the entry point
  above read as one line. `Bot.run(…)` spelled in full does the same thing for a bot that does not extend
  it. Nothing that compiled before stops compiling.
- **The SDK no longer ships `Sdk.java` and `Pictures.java` for the host to copy in.** A new project gets
  them from the template it is created from. Adding the SDK to a project that has neither brings neither —
  the flow window offering to write one is owed and not in this release.
- **`SdkPlugin` no longer declares a parameter section**, and no longer answers a parameter row or a
  parameter edit. The contract surface is deleted: the SDK's group was the only one in existence, it declared
  no rows, and `botmaker-plugin-basics`' `ParameterStore.declare` had no caller — so what the Parameters
  window read out of this plugin was a pre-2026-09-17 project's JSON and nothing else. **Nothing changes for
  a bot**, and nothing is lost in the editor: a parameter is a `@Param` field in your own Java, and one this
  plugin wants for itself goes in `plugins/sdk/Sdk.java`, which the window already reads.
- **The plugin contract is a `compile` dependency**, so it reaches your bot rather than stopping at this
  jar. `@Param` and `@Managed` moved onto the contract and sit on your *own* fields and methods, so your bot
  needs that jar to compile at all; at `provided` it resolved no copy and failed on its own `@Param` line.
  Nothing about how a plugin links changes — `PluginLoader` is parent-first for `com.botmaker.plugin.api.**`,
  so a plugin still links the host's copy and there is one `Class` on both sides. **The one thing to know:**
  do not declare `botmaker-studio-api` yourself beside a plugin that brings it, or nearest-wins pins you to a
  contract version your plugin was never built against.
  **Your bot's imports change with it**: `com.botmaker.plugin.basics.params.Param` is now
  `com.botmaker.plugin.api.params.Param`, `…basics.managed.Managed` is `…api.managed.Managed`, and
  `@Param(min, max)` are numbers, so `min = "1"` becomes `min = 1`.
- **🎯 Capture Targets is 🎯 Capture Source, and it picks one thing.** A project kept a *list* of targets in
  `capture.json` with one marked default; that file is deleted and a project's capture source is the
  expression `Sdk.captureSource()` returns, which is one source. So the list manager is the picker it always
  opened. Nothing read the other entries.
- **Nothing is snapped to a reference resolution before a capture.** It came from `capture.json` too, and
  each picture already records the size it was authored at in its own sidecar — which is what the matcher
  rescales against. *Project Setup* has two required steps rather than three for the same reason.

- **Otherwise nothing changes for a bot.** The SDK's plugin half was recompiled against the plugin
  contract's new package layout (`com.botmaker.plugin.api.slot`, `.parameters`, `.toolbar`, `.source`,
  `.value`). No `api.*` type, method or behaviour changed — japicmp holds that — and a bot never writes a
  contract name down.

## [1.1.14] — 2026-09-21

### Changed

- **Nothing changes for a bot.** The SDK's plugin half was recompiled against the plugin contract's new
  package layout (`com.botmaker.plugin.api.slot`, `.parameters`, `.toolbar`, `.source`). No `api.*` type,
  method or behaviour changed, and a bot never writes a contract name down — this line exists so the version
  bump has a reason on it.

## [1.1.13] — 2026-09-21

No source changes since v1.1.12; re-released for updated upstream pins.

### Added

- **Your flow is a value in your own Java: `Flow`, `Flow.Activity`, `Flow.Edge`, `Flow.Limits`.** The SDK
  gives your project a file — `plugins/sdk/Sdk.java` — with a `@Managed("flow")` method that returns one,
  and BotMaker rewrites that one expression when you draw. Everything you add to the file survives.

- **An activity's body is a method reference: `Flow.activity(Collect::body, "Collect", …)`.** That is the
  point of the change. `Activities.define("Collect", …)` matched a string in a JSON file against a string in
  a Java call, so renaming or deleting the method compiled fine and the flow quietly took the `DISABLED`
  wire three screens into a run. `Collect::body` is resolved by javac, so the same rename **fails the
  build**, naming the file. The new type is `ActivityBody`, an ordinary functional interface.

- **`Flows.use(Flow)`** — the whole hand-off, one static call from the `install()` in the file you were
  given, which your `main` calls. No reflection, no service loader, no file.

- **The capture source is a value too**, `@Managed("capture")` in the same file, installed through the
  `Source.set` that has always existed. *Capture Targets ▸ Apply* and the overlay's "point the bot here"
  button both write it, beside the two files they already wrote.

- **`ActivityBody.NONE`** — the body of an activity you have drawn but not written yet. Drawing the flow
  first is an ordinary way to work, so the card has to be sayable: it is a node like any other, it does
  nothing, and the run takes the wire it would take for one switched off.

### Changed

- **✂ Activity Flow reads and writes `Sdk.flow()`, not `activities.json`.** Drawing changes one expression
  in your own source — one hunk in `git diff`, one entry in the project's history — and everything you wrote
  around it survives. A `flow()` body you wrote by hand is shown empty and **left alone**: the editor says
  so on its status line and refuses to save over it.

- **Where the cards sit is not in your source.** The graph is Java and committed; the positions go to
  `src/main/resources/plugins/com.botmaker/sdk/flow-layout.json`, which both templates gitignore, so
  dragging a node never shows up in a diff. A clone without the file opens on an auto-arranged canvas.

- **A flow carries its enable flags and its presets.** `Flow.Activity.enabled()` is part of what the bot
  does, so it is in the bot's own source; a saved preset is a named set of those flags, so it travels beside
  them rather than being lost the moment the project is cloned.

- **The side panel has a "Runs" box** — the method reference this card's work is written as. It is checked
  as you type against the same rule the file is read back with, so a value the editor accepts is never one
  it then shows read-only.

- **The flow no longer validates generated field names.** An activity's enable flag and a project's
  variables used to become fields of one generated class, so their names had to be unique identifiers in one
  namespace. Neither generates a field now, so javac is what has an opinion, and the editor checks only the
  names it is still the author of.

- **A bot runs from the flow it installed, not from `activities.json`.** `FlowGraph.load` and
  `FlowGraph.run` walk `Flows.installed()`. An activity whose body the flow names is run directly — the
  method reference javac resolved — and only one that names none is still looked for by the old convention
  at `<your package>.activities.<Name>`, so bots written the older way keep running. The old lookup is asked
  **only** about the activities that need it, so a bot whose bodies are all named starts silently rather
  than reporting, once per activity per start, that it has no body for one written three lines away.

- **`ActivityContext`'s constructor is public.** A body is a `public static Outcome body(ActivityContext
  ctx)` in your own file, so a plain JUnit test can call it: `assertEquals("BAG_FULL",
  Collect.body(new ActivityContext("Collect")).name())`. That is the thing the method-reference design
  buys, and it needed a constructor to be reachable.

- **`Flows.enabled(name)`** — whether the installed flow has an activity switched on. An activity the flow
  does not mention reads as **on**, which is the answer `Settings.enabled` gave for a name with no entry: a
  bot may define an activity that is not on the canvas at all.

- **`Activities.define(String, …)` is deprecated**, with a `@ReplacedBy` pointing at `Flow.activity`. It
  still works and still registers a body by name; the name is exactly the link the method reference
  replaces. Move the lambda into a `public static Outcome body(ActivityContext ctx)` and name that.

- **Your picture class says it is managed, instead of Studio guessing.** Until now Studio locked every
  `static final ImageTemplate` field it found anywhere, and treated a class of nothing but those as owned
  whole by *🖼 Manage Pictures*. So a bot that kept one picture beside ordinary code was locked out of that
  code, and a second class of pictures could not be told from the first. The class carries
  `@Managed("pictures")` now and only the class that carries it is refused; a picture constant you write
  yourself, anywhere else, is yours to edit. The SDK **ships that file** (`plugins/sdk/Pictures.java`), so
  the class is one this plugin owns rather than one it hoped you had: picture renames searched for
  `Templates.ORE` before, in bots whose author had called theirs something else, and found nothing.

### Removed

- **`activities.json`, and everything that read or wrote it.** `Authoring.readModel`, `writeModel`,
  `modelJson` and `readSchemaVersion`; `ProjectModel`, `FlowModel`, `FlowNodeModel`, `PresetModel`,
  `ActivityModel` and `VariableModel`; `internal.config.ProjectData`; `internal.config.SdkGrammar` and its
  `META-INF/services` registration; `AuthoringMixins` and `ValueJson`.

  **There is no migration, and that is deliberate** — a project written before this reads as having no
  flow, which is a state the editor can show and offer to fix, where a converter would be a second reader
  of a format nothing writes. Nothing deletes anyone's `activities.json`; it is simply not read.

  `Authoring.SCHEMA_FIELD` survives, because `capture.json` carries the same stamp.

### Added

- **The SDK's eight value types read their own Java back.** A picture, a precision, a point, a rectangle, a
  size and the three enums could all be *written* into a user's `@Param` field and none of them could be
  read, so the editor listed them read-only and could only ever offer to overwrite. Each inverse now sits in
  the same expression as the literal it undoes. They recognise **only** what the SDK emits — `Point.of(3,
  4)` and `Precision.TIGHT.minArea(400)` mean the same thing and are declined, because the author wrote
  those on purpose and the window shows them as written. An enum constant this build does not have is
  declined rather than parsed: `Direction.UP` would otherwise read back as `NORTH`.

- **Picture constants are managed by 🖼 Manage Pictures.** A `static final ImageTemplate` field is shown in
  the editor with its thumbnail and cannot be edited on the canvas, which says to use that window — the one
  that renames the file, the constant and every use of it together. A class of nothing but picture constants
  (the template's `Pictures`) is read-only as a whole.

### Changed

- **A duration, a colour and a precision are edited the same way everywhere.** Each of them drew one control
  in the Parameters window and a different one on a block — four boxes written on every keystroke against a
  pill committing on OK, a hex string against a constructor call, three stored numbers against the shortest
  exact Java. A value is written as Java in both places now, so there is one control and one spelling. Two
  consequences worth naming: a colour the editor cannot write back (`Color.RED`, a variable) leaves the
  swatch alone instead of showing white, and a duration in the Parameters window commits when you press OK
  rather than as you type.

- **`VariableModel`'s type is a `ValueForm`.** The component is called `form` and the stored file still calls
  it `type`, so every project on disk reads unchanged; `ValueJson` writes the `type`/`shape`/`list` object it
  always wrote and reads it back through plugin-basics' `StoredForms`. `VariableModel.of` and
  `LiteralWriter`'s four methods take a form too. The contract deleted `ValueChoice` and `ValueShape` on
  2026-09-20 — `api.*`'s never-delete rule does not reach these, which are `authoring` and `internal`.

- **`VariableModel.fromWire` and `listShapeOf` are gone, and so is the question they answered.** A stored
  `ANY_OF` meant tick boxes over the author's choices or a free list the user fills in, and which one showed
  only in whether any choices were written down — so reading a variable's type needed a sibling field. Both
  shapes emitted `List<T>`; a form says `List<T>` and the widget question is asked of the row's options,
  where it belongs. Jackson binds the canonical constructor again.

  One consequence is stated plainly because it is user-visible for one release: the shape written into
  `activities.json` can no longer say *a set was declared*, so a Studio built before this change draws a
  free list where it drew tick boxes. The values are the same either way, and it goes when the file does.

### Fixed

- **A picture loads whatever directory the bot was started from.** `new ImageTemplate("src/main/resources/
  images/collect.png")` only resolved against the working directory, so a bot run from an IDE rooted
  anywhere but the project, or from its own jar, failed with `Failed to load image template`. The path as
  written is still tried first; then the same picture on the classpath, where Maven packages it
  (`images/collect.png`). The picture's size sidecar is found the same way.
- **A picture that cannot be found says where it looked**: every location tried and the working directory,
  instead of one absolute path that was only ever the first guess.

## [1.1.12] — 2026-09-19

### Added

- **Picture constants are managed by 🖼 Manage Pictures.** A `static final ImageTemplate` field is shown in
  the editor with its thumbnail and cannot be edited on the canvas, which says to use that window — the one
  that renames the file, the constant and every use of it together. A class of nothing but picture constants
  (the template's `Pictures`) is read-only as a whole.

### Fixed

- **A picture loads whatever directory the bot was started from.** `new ImageTemplate("src/main/resources/
  images/collect.png")` only resolved against the working directory, so a bot run from an IDE rooted
  anywhere but the project, or from its own jar, failed with `Failed to load image template`. The path as
  written is still tried first; then the same picture on the classpath, where Maven packages it
  (`images/collect.png`). The picture's size sidecar is found the same way.
- **A picture that cannot be found says where it looked**: every location tried and the working directory,
  instead of one absolute path that was only ever the first guess.

## [1.1.11] — 2026-09-19

### Changed

- **The Remote Pilot's install QR code points at `BotMakerDev/botmaker-pilot`**, where the app's releases
  moved on 2026-09-18. The old address still redirects, so a code printed before this still works.

## [1.1.10] — 2026-09-18

No source changes since v1.1.9; re-released for updated upstream pins.

No source changes since v1.1.8; re-released for updated upstream pins.

### Changed

- **The six parameter categories are gone** — Timing, Targets, Vision, Input, Limits, Debug. They were this
  plugin's declaration of how a bot's settings are filed, and a bot's settings are its own Java now: a
  `@Param`'s `category` is free text, so the Parameters rail lists the categories *you* wrote and nothing
  else. A project that used one keeps it, because the category was always just text on the row; what
  changes is that the six no longer appear in an empty project, offering a filing system for parameters
  that do not exist yet.
- **`SdkPlugin` no longer stores declared parameter rows.** The contract's
  `parameterDeclared(ParameterDeclaration)` is gone (studio-api, 2026-09-17): a user parameter is a
  `@Param` field in the bot's own Java and the host writes it there. This plugin's own rows — an activity's
  enable flag — are unaffected, and it still answers `parameterRows` and `parameterEdited`. Nothing in
  `api.*` changed, so no bot is affected.

## [1.1.9] — 2026-09-18

No source changes since v1.1.8; re-released for updated upstream pins.

### Changed

- **The six parameter categories are gone** — Timing, Targets, Vision, Input, Limits, Debug. They were this
  plugin's declaration of how a bot's settings are filed, and a bot's settings are its own Java now: a
  `@Param`'s `category` is free text, so the Parameters rail lists the categories *you* wrote and nothing
  else. A project that used one keeps it, because the category was always just text on the row; what
  changes is that the six no longer appear in an empty project, offering a filing system for parameters
  that do not exist yet.
- **`SdkPlugin` no longer stores declared parameter rows.** The contract's
  `parameterDeclared(ParameterDeclaration)` is gone (studio-api, 2026-09-17): a user parameter is a
  `@Param` field in the bot's own Java and the host writes it there. This plugin's own rows — an activity's
  enable flag — are unaffected, and it still answers `parameterRows` and `parameterEdited`. Nothing in
  `api.*` changed, so no bot is affected.

## [1.1.8] — 2026-09-17

### Changed

- **The six parameter categories are gone** — Timing, Targets, Vision, Input, Limits, Debug. They were this
  plugin's declaration of how a bot's settings are filed, and a bot's settings are its own Java now: a
  `@Param`'s `category` is free text, so the Parameters rail lists the categories *you* wrote and nothing
  else. A project that used one keeps it, because the category was always just text on the row; what
  changes is that the six no longer appear in an empty project, offering a filing system for parameters
  that do not exist yet.
- **`SdkPlugin` no longer stores declared parameter rows.** The contract's
  `parameterDeclared(ParameterDeclaration)` is gone (studio-api, 2026-09-17): a user parameter is a
  `@Param` field in the bot's own Java and the host writes it there. This plugin's own rows — an activity's
  enable flag — are unaffected, and it still answers `parameterRows` and `parameterEdited`. Nothing in
  `api.*` changed, so no bot is affected.

## [1.1.7] — 2026-09-16

### Added

- **Three buttons on the overlay editor's own row** — the translucent HUD Studio draws over the game now
  carries this plugin's items, so the window you are looking at is the subject of all three. **⌖ Point bot
  here** makes that window the project's capture target, written to `capture.json` through the same path the
  Capture Targets dialog's Apply uses. **✂ Picture of this** opens the capture tool against that window
  whatever the project's target is — including a project that names none, which used to make the tool refuse
  to open — and changes no file. **⏺ Record at cursor** gives the recorder back what it lost when it became a
  plugin: an *Insert at cursor* button that places the recorded lines where you were working, instead of
  handing you text to paste.

- **The Activity Flow editor is this plugin's** — 🔀 Activity Flow on the toolbar, where Studio's own 🔀 Flow
  button used to be. The canvas, the cards, the outcome ports, the wiring rules, the auto-arrange, the undo
  arrows and the loop-safety fields all came across unchanged; what changed is who owns them. It reads and
  writes `activities.json` itself, so the editor and the running bot cannot disagree about what a flow is,
  and it is the only one of the three windows over this plugin's project data that moved: a parameter is a
  `ParameterRow` the host can draw, while a flow's nodes, edges and outcomes are this plugin's own
  vocabulary.

  Two consequences worth knowing. **Studio's Project ▸ Activity Flow menu entry and its New Activity button
  in the file explorer are gone** — the toolbar item is the one way in, and adding an activity is a button on
  the canvas, which is the only place that can also say where it sits and what it reports. And the window no
  longer remembers its size between openings, which is host state a plugin has no access to.

- **The Parameters window's rows are this plugin's, not the host's.** `SdkPlugin` implements the contract's
  parameter-data surface — `parameterRows(groupId)` and `parameterEdited(edit)`. Studio parsed the project
  file itself, which meant the host knew one plugin's storage format and no second plugin could have had
  parameters at all. A plugin with no project bound answers nothing, and a group id this plugin does not own
  answers nothing — a project's parameters can now be several plugins' at once. The value a host hands back
  is **canonicalised, clamped to any declared range and pruned to the options still on offer** before it is
  stored, and the row that comes back says what was actually stored — so a window renders the value the bot
  will get rather than the text somebody typed.

  **The declaration half landed the same day**: `parameterDeclared` takes the row the host wants and answers
  the row this plugin stored — an add, a rename, a retype, new choices, a range, a category, a note, a
  visibility change or a removal, all as one call. What each costs is `ParameterStore`'s rule, so the window
  that asks needs to know nothing about what a value type is.

  **Where they are stored changed the same day.** All three methods go through
  `botmaker-plugin-basics`' `ParameterStore`, over this plugin's own folder in the project —
  `src/main/resources/plugins/com.botmaker/sdk/parameters.json`. Storing parameters was plugin #1's
  privilege only because plugin #1 owned `activities.json`; it is an ordinary plugin's file now, and any
  plugin declares parameters the same way. **A project created before this reads as having none**: nothing
  converts the old `variables` array, and nothing deletes it either, so the data is on disk and a converter
  can be written later.

- **`Settings` replaces `Wire`, and two methods replace eighteen.** The type is an argument now:

  ```java
  Duration   rest  = Settings.load("restBetween", Duration.class);
  int        health = Settings.load("minHealth", int.class);
  List<Rect> zones = Settings.loadAll("zones", Rect.class);
  boolean    on    = Settings.enabled("Mining");
  ```

  `Wire.whole`, `Wire.duration`, `Wire.area` and the fifteen others could only ever read *this* plugin's
  value types — `Wire.one(String)`'s own javadoc called itself "the escape hatch for a type this class has no
  reader for". Passing the type in makes the set open: whichever plugin introduced a type ships a
  `com.botmaker.plugin.basics.store.ValueGrammar` that reads it, and every call site is the same shape. Both
  `int.class` and `Integer.class` resolve, so you write whichever your field is.

  Everything else is unchanged. A missing file, a missing name, a name declared as another type and text that
  will not parse all answer that type's own fallback, so **a bot still never fails to start because of its own
  configuration file**. One thing throws, and it is not about the file: a type no grammar on the classpath
  claims, which is a bot compiled against a plugin it does not run with.

  The parsers are the same parsers. `internal.config.SdkGrammar` wraps the `WireText` calls the editor's own
  codecs use, so the Parameters window and the running bot cannot disagree about what `"3s500ms"` means.

- **`Images.named("ore")` is `images/ore.png`** — `Wire.image` moved to `com.botmaker.sdk.api.vision`. It
  reads a *file*, where everything else on that class read a *variable*, and it was most of why `Wire` looked
  like it did too much. A picture is vision's business.

### Removed

- **`com.botmaker.sdk.api.config.Settings` and `com.botmaker.sdk.api.config.Wire` are deleted.** A bot reads
  its own parameters from **`com.botmaker.plugin.basics.store.Settings`**, which is where the work always
  happened — the SDK classes were a facade over it holding no logic, so every answer is the one you already
  get. The change you make is the import:

  ```java
  import com.botmaker.plugin.basics.store.Settings;      // was com.botmaker.sdk.api.config.Settings

  Settings.load("minHealth", int.class);                 // unchanged
  Settings.loadAll("zones", Rect.class);                 // unchanged
  Settings.enabled("Mining");                            // unchanged
  ```

  `Settings.one`, `Settings.many` and `Settings.names` were the facade's own and are
  `ProjectValues.current().one(…)` / `.many(…)` / `.variables()` in the same package. `Wire`'s eighteen typed
  readers were already replaced by `Settings.load(name, T.class)` and `Wire.image` by `Images.named(file)`;
  those replacements are unchanged and are what the calls become.

  **This breaks a bot that imports either class, and there is no migrator for a deleted type** — only a
  compile error naming it. It is a deliberate decision, taken while `api.*` was still allowed one last
  pre-policy removal: never-delete's baseline is `v1.2.0`, which is the release this deletion is in, so the
  rule begins from a surface that no longer offers them. Nothing is removed from `api.*` after it.

  **No menu offers a settings read until `botmaker-plugin-basics` catalogues its own `Settings`** — the SDK
  may not catalogue another plugin's API, which is the same rule that moved the nine JDK value types there.
  Write the call yourself in the meantime; it compiles and runs exactly as before.

### Changed

- **A bot reads its parameters through `botmaker-plugin-basics`**, and that is the package to import if you
  call `Settings` directly rather than through `com.botmaker.sdk.api.config.Settings`:
  `com.botmaker.plugin.basics.store.Settings`. It was `com.botmaker.plugin.toolkit.config.Settings` for part
  of one day and never shipped under that name. `SdkGrammar` now reads the SDK's own eight types and
  plugin-basics' `BasicsGrammar` reads the nine JDK ones — two grammars, indexed together off the classpath,
  which is what the mechanism was built for and had never been exercised while there was one plugin.

- **The nine JDK value types are `botmaker-plugin-basics`' now** — `TEXT`, `YES_NO`, `WHOLE_NUMBER`,
  `DECIMAL_NUMBER`, `CHARACTER`, `COLOR`, `DATE`, `TIME_OF_DAY`, `DURATION`. Nothing about a whole number or
  a time of day is about automating a game; they were registered here only because this SDK was written
  first, which made having a duration variable plugin #1's privilege. **Nothing changes for a bot or for a
  stored project**: the ids are the same, `WireText`'s nine readers and their two spellers are still here and
  still public — they delegate to `com.botmaker.plugin.basics.values.JdkText`, so there is one grammar rather
  than two — and `Settings.load("rest", Duration.class)` reads exactly what it read before. What changes is
  who registers them: this SDK now declares `botmaker-plugin-basics` as an ordinary `compile`-scope
  dependency, one plugin depending on another, and contributes the eight types that really are its own
  (`ImageTemplate`, `Precision`, `Point`, `Rect`, `Size`, `Direction`, `Key`, `MouseButton`). A bot's pom
  must **not** declare `botmaker-plugin-basics` itself: Maven's nearest-wins mediation would make that entry
  outrank the one this jar brings, and the bot would run a version this SDK was never built against.

- **`ProjectData` keeps the flow and delegates the rest.** The untyped store — a variable's stored text, an
  activity's flags — is `com.botmaker.plugin.basics.store.ProjectValues` now, so every plugin can read a bot's
  parameters and not only this one. Nothing a bot writes changes and no method was removed: `value`,
  `values`, `declares`, `variables`, `enabled`, `outcomes`, `activities`, `goHome`, `popupCheck` and
  `isEmpty` all still answer here, through the store. `ProjectData.use(…)` sets the shared seam, so a test
  stubbing the model here also stubs what `Settings` reads.

  What stayed is the half that knows what the file *means*: `start`, `placed`, `maxSteps`, `stepDelayMs`
  and `routes` are `FlowModel`'s and `FlowEdgeModel`'s rules, and reading them through a second set anywhere
  else is how the editor and a running bot would come to disagree about which activity runs first.

- **The seven authoring records are `com.botmaker.sdk.authoring` again** — `ProjectModel`, `ActivityModel`,
  `VariableModel`, `FlowModel`, `FlowNodeModel`, `FlowEdgeModel`, `PresetModel`. They spent a week in the
  plugin contract (2026-08-31 to 2026-09-07). Nothing a bot writes changes; they sit beside `SdkVersion`,
  `Authoring` and `WireText`, which is where they were before and where the file they describe is read.
  `AuthoringMixins` keeps the Jackson marks out of the records regardless of which module they live in.
- `AuthoringModelTest.aStoredAnyOfWithNoSetBehindItReadsAsAnOpenList` is restored. It calls
  `VariableModel.listShapeOf`, which is package-private; the move away made it unreachable and the move back
  makes it reachable. The rule it asserts never stopped running.

- **An `ImageTemplate` no longer loads the OpenCV native when it is constructed**, only when a matcher first
  asks for its pixels. Holding a template is now something a bot does without meaning to — the value grammar
  builds one as the fallback for an unreadable image variable — and a class-initialiser load would have made
  every bot that reads *any* setting extract and link the library. Nothing about matching changes.

- Nothing a bot can see. `WireText` grew the writers for the five types whose spellings the editor had kept
  privately (`spellColor`, `spellPrecision`, `spellPoint`, `spellSize`, `spellArea`), so that reading and
  writing one stored value is one grammar rather than two — the same reason `spellDuration` is there.
  `SdkPlugin` follows `Region` to its new home in the toolkit, and `WireText`'s class
  javadoc stops describing a generator that no longer exists — it claimed a value's text was parsed "at
  generation time" and written into source as `Duration.ofMillis(5400000L)`, which stopped being true when
  the inversion was reversed between 2026-08-29 and 2026-09-02. A bot reads its own text at run time.

## [1.1.6] — 2026-09-05

### Fixed

- **The palette, the slot editors and the toolbar buttons appear when you install the SDK through
  *Manage Plugins*.** Constructing the plugin needed JavaFX, and JavaFX is `optional` in this jar — which
  means *not transitive*, so it is on the SDK's own classpath and absent from yours. A project that added
  `botmaker-sdk` as a dependency therefore got:

  ```
  ServiceConfigurationError: Provider com.botmaker.sdk.plugin.SdkPlugin could not be instantiated
    Caused by: NoClassDefFoundError: javafx/scene/Node
  ```

  which the host catches — a classpath with no loadable plugin on it is an ordinary state — leaving an empty
  palette, no name recognition, no slot editors and one line on stderr. Nothing crashed and nothing failed to
  compile. The cause was one line in the constructor registering the screen picker with the toolkit; it now
  happens where the editors are built, which only a host that has JavaFX ever asks for.

  **Found by the plugin registry's own gate**, on this plugin's first real submission, because the gate
  resolves the *published* artifact exactly as a host does. `botmaker validate --coordinate
  com.github.LiQiyeDev:botmaker-sdk:v1.1.5` reproduces it; validating a working copy never could, since an
  `optional` dependency is present on the module's own classpath.

  The rule, now held by `SdkPluginHeadlessTest`: **constructing a plugin must not link an optional
  dependency.** A headless host — `botmaker validate`, `botmaker run`, the registry's CI — is a legitimate
  host, and it is the one that decides whether a plugin may be published at all.

## [1.1.5] — 2026-09-05

### Fixed

- **`1.1.4` still does not resolve — third time, third plugin, and this one is caught by a gate rather than
  by a tag.** `botmaker-session` and `botmaker-studio-api` did build this time. `botmaker-plugin-toolkit`
  did not: it pinned `flatten-maven-plugin` 1.6.0, which declares a Maven 3.6.3 prerequisite that JitPack's
  **Apache Maven 3.6.1** cannot satisfy, so it published nothing and this jar's pom named a toolkit that
  does not exist. The toolkit and `botmaker-plugin-host` now pin 1.4.1, exactly as this module always has.

  The SDK itself is unchanged from `1.1.3`. What changed outside it is that the umbrella's `release.sh`
  gained `check_jitpack_plugins`, which reads each pinned plugin's own `<prerequisites><maven>` and refuses
  the release while nothing has been pushed — three chains have now been burned on this one failure, each
  time on a different plugin, and each time invisible locally because a developer's Maven is new enough.

## [1.1.4] — 2026-09-04

### Fixed

- **`1.1.3` still does not resolve, and this is the release that fixes it.** The upstream repair it
  announced was pinning `maven-compiler-plugin` in the modules that lacked one — and the pin named 3.13.0,
  which the plugin's own Maven prerequisite (raised to 3.6.3 in 3.12.0) puts out of reach of JitPack's
  Maven. So `botmaker-session` and `botmaker-studio-api` failed to build again, one line later, and this
  jar's pom went on naming versions that do not exist. Every module now pins **3.11.0**, the version
  `botmaker-shared` has always used. Nothing in the SDK itself changed between `1.1.3` and this release.

## [1.1.3] — 2026-09-04

### Fixed

- **Installing the SDK through *Project ▸ Manage Plugins* now gives you a working palette.** It did not.
  The SDK is a library *and* Studio's plugin #1, and its plugin half extends `AbstractStudioPlugin` from
  `botmaker-plugin-toolkit` — which this pom declared `<optional>true</optional>`. `optional` means **not
  transitive**, so a project that added `botmaker-sdk` as a dependency got the plugin classes without the
  classes they extend. Studio loads plugins off your project's own resolved classpath, so `ServiceLoader`
  threw `NoClassDefFoundError` while constructing the plugin, Studio caught it — correctly; a project with
  no plugin on its classpath is an ordinary state — and you got an empty palette, no name recognition, no
  slot editors and one line on stderr. The toolkit is an ordinary dependency now, so it travels with the
  SDK wherever the SDK is resolved.

  It costs a headless bot 106 KB it never links. The toolkit has no dependencies of its own and its JavaFX
  is `provided`, so **nothing follows it onto a bot's classpath** — no JavaFX, no window toolkit, nothing
  new to download for the machine a bot actually runs on. A project created by Studio is unchanged either
  way: its pom already declared the toolkit itself.

- **Resolvable from JitPack again.** `v1.1.2` published a pom naming `botmaker-session` and
  `botmaker-studio-api` versions that had never been built there: neither of those modules pinned
  `maven-compiler-plugin`, and JitPack's Maven defaults it to 3.1, which predates
  `maven.compiler.release` and builds with `source 5`. Every module in the chain pins it now.

## [1.1.2] — 2026-09-02

- **Your bot now builds and runs on Java 25 (LTS), with JavaFX 25.0.4 behind the editors.** This is the one
  change here you have to act on: a project pinned to this SDK needs a JDK 25 or newer, and a bot run on an
  older JVM fails at class load with `UnsupportedClassVersionError` rather than at compile time. Nothing in
  `com.botmaker.sdk.api` moved — never-delete still holds — so once your JDK is new enough there is nothing
  to change in your code.

## [1.1.1] — 2026-09-02

- **Branching on what was found is a chain of calls now — `found.when(…).when(…).otherwise(…)`.** Deciding
  what to do about a *combination* of templates is what `Matches` exists for, and until now the only ways to
  write it were an `if`/`else if` chain or a Java 21 guarded switch the editor built for you:
  ```java
  ImageFinder.whileFindAny(POPUPS, found -> {
      found.when(m -> m.hasAny(MAIL, GIFT),             () -> ImageClicker.click(CLAIM))
           .when(m -> m.hasAll(CHEST) && !m.hasAny(AD), () -> ImageClicker.click(CHEST))
           .otherwise(                                   () -> Debug.log("nothing to do"));
  });
  ```
  It reads the way the switch did — **at most one branch runs**, and a later test is not even evaluated once
  one has matched — and every predicate sees the same frame, so two branches asking about the same instant
  cannot disagree. `otherwise` is optional; a chain that ends without one does nothing when nothing matched.
  Both older forms keep working and compiling; nothing you have written changes.

  The reason for the new shape is worth one line, because it is why the editor gets better: a `switch` is a
  language construct, so everything about it — the type name, the pattern variable, the guard, the mandatory
  `default` — had to be spelled by whoever wrote the editor. These are ordinary methods, so the editor offers
  them, draws them and edits their predicates with the machinery it already has for every other call.

- **You write an activity as a call now — `Activities.define`.** An activity is created on the Activity Flow
  canvas; what it *does* is this, written wherever you like:
  ```java
  Activities.define("Mining", ctx -> {
      if (bagFull()) return ctx.outcome("BAG_FULL");
      mineOnce();
      return ctx.done();
  });
  ```
  Both names are dropdowns in the editor, filled from your own canvas — the activities it holds, and the
  outcomes it declares — and both stay typeable, so you can write a body before you have drawn the activity. **An activity with no `define` call is
  not an error** — it behaves exactly like one you switched off and follows its `DISABLED` wire, so you can
  draw a whole flow before writing any of it. Two things to know: a misspelled outcome is not a compile error
  (it is reported, and behaves like an outcome you never wired), and renaming an activity on the canvas does
  not rename the string in your code.

- **Remote Pilot ships with the SDK now.** The 🎮 Pilot button, the phone pairing, the private-display
  controls and everything behind them come from the SDK rather than from Studio. For you that changes one
  thing: a project that does not depend on the BotMaker SDK has no Pilot button. Everything else — the
  pairing URL, the QR codes, Interact, background mode — works exactly as before, with one cost the first
  time: the pairing token moved with the feature, so an already-paired phone has to scan the QR code once
  more.

- **The picture library ships with the SDK now, as 🖼 Manage Pictures.** Renaming, retagging, replacing,
  deleting, importing and exporting the pictures your bot looks for were an editor window; they are the SDK's,
  beside ✂ Capture Templates on the toolbar. Nothing about what it does has changed, **including the part that
  edits your code**: renaming a picture still finds every block that names it and carries them along, and
  deleting one that is still in use still lists the uses and offers to point those blocks at another picture
  first — with the enclosing functions marked for review, because that one changes what your bot watches for.
  A rename is not marked, because it is not a change: it is the same picture under a new name.

  What moved underneath is worth one line, since it is why any plugin can now do this: rewriting your Java is
  the editor's job and it stays the editor's job. What the SDK supplies is only the knowledge that `ore.png`
  is written `Templates.ORE` — the editor no longer knows that, and never needed to.

- **Your capture targets are one list now, in `capture.json`.** The screens, windows and emulators you set up
  as targets were kept in the editor's own settings, while the one your bot actually looks at was kept
  separately in `botmaker-project.properties` — so the two could drift apart with nothing to tell you. The
  list is a project file the SDK owns, like `activities.json`, and the default target is what your bot
  resolves. Existing projects are migrated the first time they are saved; nothing you have set up is lost.

- **Bots with `Activity` subclasses keep working, unchanged.** Both kinds of activity share one registry, so
  `Activity.disable("Mining")` still finds either.

- **BotMaker writes none of your bot's Java any more.** Not the entry point, not `GoHome`, not `Popups`, not
  an activity's file. A project's structure is yours: the SDK owns the data files your bot reads back at run
  time (`activities.json`, `botmaker-project.properties`, the placeholder image) and nothing else. Existing
  projects are untouched — the files you already have keep compiling and are yours to edit, rename or delete.

- **Your bot can read its own settings by name — `Wire`.** `Wire.whole("minHealth")`,
  `Wire.duration("restBetween")`, `Wire.template("healthBar")`, `Wire.enabled("Mining")`: the values you set
  in the editor, read at run time by the name you gave them, one reader per type. **Nothing has been taken
  away** — the generated `Parameters` and `Activities` classes still work exactly as before, and this is the
  first half of letting a later release stop generating them. Two things worth knowing before you use it: a
  misspelled name is not a compile error (`Wire.whole("minHelath")` compiles and answers `0`), and **nothing
  here can throw** — a missing file, a missing name and a value that will not parse all fall back, because a
  bot must never fail to start because of its own configuration file.

- **BotMaker no longer writes five of your bot's nine files.** `Activities`, `Parameters`, `Templates`,
  `ActivityRegistry` and `FlowDriver` were rewritten every time you ticked a box, changed a value, captured a
  picture or moved a wire. None of them is generated any more, because all five said only what your project's
  own `activities.json` already said. **A new project gets four files: your entry point, `GoHome`, `Popups`,
  and one class per activity — every one of them yours to edit, none of them ever overwritten.**

- **`FlowGraph.run(YourBot.class, GoHome.INSTANCE::execute)` walks the flow you drew.** It replaces the
  generated `FlowDriver` and `ActivityRegistry` together: the start node, the wires, the step budget and the
  pause between activities all come from `activities.json`, and your activities are found beside your entry
  point at `<your package>.activities.<Name>`. Redrawing the canvas now changes no Java at all.
  `FlowGraph.of` / `.node` / `.route` are **deprecated but still work** — a bot with a hand-built or an
  already-generated table keeps running untouched.

- **`Wire.image("ore")` names a picture** — the replacement for `Templates.ORE`. Adding a picture to your
  project is no longer a source edit.

- **What this costs you, stated plainly.** `Parameters.minHealth` was an `int` field and misspelling it was a
  compile error; `Wire.whole("minHealth")` answers `0`. The one place you write a name by hand keeps its
  compiler — `return Outcome.BAG_FULL;` is still checked against an enum the editor maintains for you.

- **An existing bot is not touched.** If your project already has those five files, they stay exactly where
  they are and go on working. They are ordinary source you own now: nothing rewrites them, and nothing
  deletes them. Move to `Wire` and `FlowGraph.run` when you feel like it, or never.

- **Internal: the plugin-side code is smaller, and the generic half of it is the toolkit's.** `Slots`, the
  call-site matching, the bounded-number pill and the editor test stubs moved to
  `botmaker-plugin-toolkit`; `SdkPlugin` extends its new `AbstractStudioPlugin`, which also makes the
  52-facade palette reflection lazy rather than running it when `ServiceLoader` constructs the plugin.
  **Nothing under `com.botmaker.sdk.api` changed** and no behaviour did; a bot resolves nothing new.

- **The SDK now ships the editors for its own types.** The controls that stand in for a typed-out expression
  — drag a region on screen rather than write `new Rect(12, 40, 300, 80)` — used to live in Studio, which
  meant Studio had to know what an SDK type looked like. They are ordinary plugin contributions now
  (`com.botmaker.sdk.internal.plugin.editors`), reached exactly as any other plugin's would be. **Nothing
  changes for a bot**: the editors are `<optional>` dependencies alongside JavaFX and the widget toolkit, so
  they are in the jar and never linked on a bot's classpath — a bot is a headless program and must not resolve
  JavaFX. **Eight have moved** in this release: `Rect`, `Point` and `Size`; the Steam and Epic launch ids,
  the program path and the launch options of a `Game.launch…` call; the bounded `BotSettings` setters; and the
  wait length. The rest follow in the next.
- **The wait editor is now the same control in both places you meet one.** The Parameters window and a block
  in your bot's source used to have separate duration editors that had to be kept saying the same thing;
  there is one now, and it draws as four boxes in the window and as a pill that opens them on a block. What it
  writes is unchanged — the shortest form that says what you chose (`Duration.ofSeconds(2)`, not
  `ofMillis(2000)`), and an untouched value comes back exactly as it was written, so opening the editor and
  pressing OK never rewrites your source.
- **The launch and settings editors only appear where they mean something.** A Steam app id, a program path
  and a launch flag are all `String`, so these are chosen by the *call* they sit in rather than by their type
  — which is why they are offered on a `Game.launchSteam(…)` argument and not on every text field in the
  Parameters window.
- **What you read on a collapsed picker is unchanged, including the awkward cases** — a half-written
  `new Point(10)` still reads `10, 0`, a slot holding `bounds` still shows `bounds` rather than claiming
  `0, 0`, and a `long` literal keeps its value. Those were pinned by a Studio test and are pinned by an SDK
  one now.
- **OCR tuning is part of the SDK's API now.** `OcrOptions`, `OcrLanguage` and `TextResult` moved out of
  `botmaker-shared` into `com.botmaker.sdk.api.vision`, so the options you pass to `Text.read`, `Text.find`
  and friends are versioned like everything else you write down — and covered by the same
  `@ReplacedBy`/`@Replaces` machinery if they ever change spelling. **Update your imports**: any
  `import com.botmaker.shared.ocr.OcrOptions;` (or `OcrLanguage`/`TextResult`) becomes
  `com.botmaker.sdk.api.vision.…`. Studio repoints them for you on open; a bot built by hand needs the edit.
  Nothing else about OCR changed — same engine, same tuning knobs, same bundled languages.
- **`@Since`, `@ReplacedBy` and `@Replaces` moved to the plugin contract**, from
  `com.botmaker.sdk.api.meta` to `com.botmaker.plugin.api.meta`. They describe how *any* library keeps faith
  with the code that calls it, not something particular to this SDK, and the same annotation processor now
  checks them for any plugin. **If your bot writes `@Since` down** — most do not — change the import to
  `com.botmaker.plugin.api.meta.Since`; the old spellings still work for this whole minor, marked deprecated
  and pointing at the new ones, so Studio's *Modernise…* will do it for you. Nothing about what they mean
  changed, and this is the first rename the pointer pair has carried for itself.
- `TextResult.bounds()` is now an `api.geometry.Rect` instead of a `java.awt.Rectangle`, so it matches every
  other box the SDK hands you: `.x()`, `.y()`, `.width()`, `.height()` rather than public fields.
- **The block palette is the SDK's answer now.** The SDK ships a catalog of what it offers Studio's menus —
  which types, in which order, under which icon, and which of their members. A bot pinned to an older SDK is
  offered that catalog narrowed to what *its own* jar actually contains, so a newer Studio never proposes a
  method your pinned SDK does not have. Nothing changes in your bot's code, and nothing new is on its
  classpath: the catalog is served to the editor, never called by a bot.
- Practically, this restores the curation that `@Palette` used to carry and that was lost when the annotation
  was deleted — the menus have been offering every public method of every facade since then, and go back to
  offering the ones worth offering once Studio reads the catalog.

- **Your parameters are real values now, not text read at startup.** `Parameters.REST` used to be
  `Wire.duration(Wire.one("REST"))` — the bot opened `activities.json` when it launched and parsed `"1h30m"`
  out of it. It now says `java.time.Duration.ofMillis(5400000L)`, written when the file was generated. Your
  bot starts faster, cannot fail to start because a value in that file is malformed, and reads like something
  a person wrote. **`Wire` and the runtime config store are deleted**; `activities.json` is still your
  project's data, but nothing reads it while the bot runs.
- **One thing to know if you edit generated files by hand:** changing a value inside `Parameters.java` now
  lasts exactly until the next save. It always said "do not edit"; the difference is that a *value* is one of
  the things it means. Change it in Project ▸ Parameters, which also keeps the file beside it in step.
- **Your activity switches are no longer `final`.** `public static boolean Mining` rather than
  `public static final boolean Mining`. This is what stops `while (Activities.Mining) { … }` from becoming an
  "unreachable statement" compile error in *your* code when you untick that activity — a folded constant
  `false` makes the loop body dead. As a side effect you may now assign to one at run time; nothing objects.
- **Your project's values move to their own file.** `Activities` used to hold two unrelated things under one
  name — an activity's on/off switch and every value you configured — so `Activities.restDelay` sat beside
  `Activities.Mining` with nothing to tell them apart. The switches stay in `Activities`; the values are now
  `Parameters`. **You do not have to do anything:** Studio splits the two files and repoints every
  `Activities.<value>` in your own code the first time it opens the project, taking a Project History snapshot
  first. Nothing is marked for review, because nothing changed except the name in front of the dot.
- **The scaffold's two-author negotiation is gone.** The per-hole generation numbers, the surface ledgers and
  the pre-write refusal that existed to keep Studio and the SDK in step have been removed. They were the price
  of a file the two repositories co-authored; the SDK is becoming the generator, so there is nothing left to
  negotiate. Nothing in your bot changes.
- **The scaffold templates no longer ship in the jar.** `botmaker-templates/` and its `manifest.txt` are gone,
  along with the `@Template` annotation. They were the text Studio filled in to write your `Activities.java`,
  `Parameters.java`, `FlowDriver.java` and `ActivityRegistry.java`; that job moves into this SDK, where the
  files can be checked against the API they call in the same build. **Nothing in an existing bot changes** —
  the generated files already in your project are ordinary Java and keep compiling, running and being edited
  by hand. What is temporarily unavailable is Studio *rewriting* them: until the SDK ships its own generator,
  **New Project and Save Activity Flow are refused**, by name, with the reason.
- **`@Palette` and `@Scaffolding` are removed from `api.meta`.** Neither was ever something a bot wrote down —
  they told Studio which members to offer in its menus and which it wrote into generated files. Until the SDK
  serves the palette itself, Studio's menus simply offer everything public. The four pointer annotations
  (`@ReplacedBy`, `@Replaces`, `@Since`) are untouched.
- **The SDK now owns `activities.json`.** New package `com.botmaker.sdk.api.authoring`: your project's
  activities, variables, flow and presets are read and written here, against one schema with one owner,
  instead of by whichever editor happened to open the file. **Nothing in your bot changes and nothing in your
  project file changes** — the format is the same one you already have, including the two spellings it has
  carried over its life, both of which still load. What this buys you arrives next: the generator that writes
  your project's Java lives beside the API that Java calls, and is checked against it in the same build.
  Every entry point takes the SDK version your bot pins as its first argument, so an editor bundling a newer
  SDK than yours generates for *your* version or says plainly that it cannot.

## [1.1.0] — 2026-08-24

The 1.1.0 contract release. **This is the last window in which `api.*` moves freely** — from 1.1.0 the SDK is
under real semver and nothing is removed without a deprecation release naming its replacement
(`docs/refactor/21-api-compat.md`). Existing bots take plain compile errors for the moves below; Studio
repairs the imports on open.

- **`api` is reorganised and the root is empty.** `Point`/`Rect`/`Size`/`Direction` → `api.geometry`, the
  meta annotations → `api.meta`, `Session`/`BotSettings` → `api.bot`, `Time`/`BotMaker`/`Debug` → `api.util`.
  The rule it encodes: *`api` is what a bot can write down.* Eleven types a bot could only ever **receive**
  (`Desktop`, `Monitor`, `NamedWindow`, `SessionSource`, the six `api.observe` types) moved to `internal`,
  and `Screen` — which had no callers and was not even a `CaptureSource` — is gone.
- **`Point`, `Rect` and `Size` are records of `int`s.** They were mutable clones of `org.opencv.core.*` with
  public `double` fields and no `equals`, so `p1.equals(p2)` in a bot was an identity comparison.
- **`VisionContext` is `Vision`, and the accessors dropped `get`.** `MatchResult`, `ColorMatch`, `TextMatch`,
  `ImageTemplate`, `Rect` and `Vision` read as `m.confidence()`, not `m.getConfidence()`; mutators keep `set`.
- **A move can now be written down, at both ends.** `@ReplacedBy` on the deprecated element and `@Replaces`
  on the survivor, plus `note()` (the author's own sentence, shown to the user verbatim),
  `behaviourChanged()` (the flag for a redirect that keeps its shape but changes what it does) and `@Since`.
  `@ReplacedBy.value()` is a `String[]` with a parallel `whens()`, so **a member that became two** is
  expressible and Studio can ask, per call site, which one that call meant.
- **`@Palette` curates the menus without shrinking the API.** A public method with no `@Palette` stays public,
  supported and callable — it is simply not proposed in Studio's palette. 18 facades and 10 value types are
  curated; a jar with no `Palette` class at all is treated as uncurated and offers everything, exactly as
  before.
- **The files BotMaker generates for you now come out of this jar.** The entry point, `GoHome`, `Popups`,
  `ActivityRegistry`, `Activities`, `FlowDriver` and the activity stub ship inside the SDK as templates;
  Studio fills in what is true about *your* project and nothing else. What you get from that: the frame of
  every generated file is compiled and tested by the SDK's own build, and a Studio older than your SDK still
  writes files that compile, because anything it does not recognise stays at the SDK's own default.
- **Your Activity Flow is a table, not a generated `switch`.** `api.flow.FlowGraph` (with `PopupCheck` and
  `Recovery`) holds the graph, and the walk itself — the loop, the step budget and its give-up message, the
  watchdog tick, the delay between steps — is the SDK's, so it is the same in every bot and it is tested:
  branch, join, loop back, an outcome left unwired, a disabled activity falling through, an empty flow.
  `FlowDriver` keeps `MAX_STEPS` and `STEP_DELAY_MS` as your two knobs.
- **Your stored parameters are read by the SDK.** `api.config.Wire` is one reader per storable type and the
  loader behind it is compiled code, replacing ~150 lines of parser bodies that used to be generated into
  every `Activities` class as text. Every reader is total — an unreadable value falls back to a default, so a
  bot never fails to start because of its own configuration file. **The `1h30m` grammar now exists once**: it
  used to be written twice, once in the editor and once as generated text, with nothing able to compare them.
- **`@Scaffolding`** marks the members those templates use, so a release that moves one says so before you
  commit to the upgrade instead of failing half-way through it.
- **BotMaker Studio requires this version or newer** from its next release, for the generated files only: an
  older bot still opens, builds and runs, but its Activity Flow cannot be saved until *Project ▸ Upgrade SDK…*
  moves it here. The upgrade re-renders `FlowDriver` and `Activities` in the new shape and leaves everything
  you wrote yourself untouched.
- **The deprecation promise is now enforced, not just written down.** From this release the build refuses to
  delete anything from `api.*` that the previous release did not already mark `@Deprecated` — so a member you
  call cannot vanish between two versions without one release in which your compiler warned you about it
  first. (For maintainers: a committed `api-surface.txt` and `ApiSurfaceTest`.)
- The method audit (`docs/refactor/22-api-audit.md`) removed the type leaks and duplicated fields it found.

## [1.0.26] — 2026-08-22

- Build fix only: `flatten-maven-plugin` pinned to 1.4.1. 1.6.0 needs a Maven newer than JitPack's, so
  v1.0.25's own build never produced an artifact.

## [1.0.25] — 2026-08-22

- **A generated bot resolves on a clean machine again.** Every published SDK pom up to v1.0.24 declared
  `botmaker-shared:0.0.0-SNAPSHOT` — invisible on a dev box (whose `~/.m2` always has one) and fatal
  everywhere else. The pom is now flattened at publish time with the real shared/session tags baked in, and
  the JitPack build *requires* those pins rather than defaulting them.

## [1.0.24] — 2026-08-22

- Re-tagged so JitPack rebuilt the SDK against a new shared/session. No source change.

## [1.0.23] — 2026-08-21

- Re-tagged so JitPack rebuilt the SDK against a new shared/session. No source change.

## [1.0.22] — 2026-08-21

- **Three click verbs, and a template that names itself** — plus clicking what a group check already found,
  instead of searching for it a second time.
- **The mouse gained the two buttons under your thumb** (back/forward).
- **The observer API sees gestures, not just clicks**, and a debug run reads as a narrative rather than a log.
- An emulator ref's liveness stopped being "is the socket open"; the ambient capture source skips a session
  whose pixels are not on X11; an empty `ImageTemplateGroup` is legal and matches nothing.
- A bot logs the size of its display and where that size came from.

## [1.0.21] — 2026-08-04

- **`Time`** facade; the project's default capture source is honoured; launch-wait wiring.

## [1.0.20] — 2026-08-02

- **`Precision`**: `Tolerance` and `MinMatch` collapsed into one type, so a call carries one knob instead of
  two that could disagree.
- **`BotSettings`** replaced `ClickConfig` and is seeded from the project's own settings, so tuning lives in
  the project rather than in generated source.
- `Bot.start` supplies the launch step; `Mouse` stops discarding clicks made inside a private session; the
  session stack is resolved from `botmaker-session`.

## [1.0.19] — 2026-07-19

- **`Text`**, the OCR facade, with `findFuzzy` for edit-distance matching.
- **The emulator facade**, `Target`/`LaunchTarget`, and Epic Games launching.
- `Bot.start` became the sole public entry point; debug output moved behind one `Debug` switch.

## Earlier

v1.0.18 and below predate this file. `ROADMAP.md` has the dated log.
