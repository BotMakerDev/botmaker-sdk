# ROADMAP

Completed work up to 2026-10-05: see CHANGELOG.md, docs/refactor/, and `git show 9beeccf:ROADMAP.md`.

## Open

- **Left from Screens on TrackMania, Hades II, Ubisoft Connect (phase 6b, 2026-10-30)**:
  - the assistant's `launch_game` answers "Bringing up…" before *Close it and launch* ends, and its done-callback can
    be lost: the assistant is not told the game came up;
  - Silksong was not walked; TrackMania, Hades II and Ubisoft Connect were;
  - Steam shows "Sign in to Steam" first in each new private display;
  - an input whose kind changes (a scroll over an untried button) is removed and declared again: it moves to the end
    of its screen's class, and the removal is refused while the bot names the field. Change the field's type in place;
  - *Close it and launch* without user systemd stops nothing and waits 30 s before refusing: say so up front;
  - TrackMania's `white_series` matches the black series icon at 0.83: a cut, not a tool, but `read_map` does not
    say when two inputs of one screen match each other.
- **Left from Screens on game kinds (phase 6, 2026-10-30)**:
  - `try_input` reads every click on a screen whose background moves all over (Balatro's swirling menu) as a new
    screen. The motion mask is read over 400 ms before the input, but a swirl moves into pixels that were still
    then, and a mask over half of the frame is dropped anyway. Neither a larger mask nor a lower still threshold
    fixed it live: "a new screen opened" needs another proof there, such as the screen's own inputs being covered
    or gone, instead of the frame's changed share. Until then, map such a screen by `click` and look, then
    `crop_pictures` the screen that opened.
  - A changed launch target is caught only by `launch_game`: ▶ Launch and a run's hand-off still name the old
    private display (▶ Launch refuses with "stop it first").
  - The orphan sweep now spares any session of this JVM whose display server still runs, including one whose holder
    leaked it; it is reaped when Studio exits.
  - The first click into a freshly launched Balatro was dropped (likely focus), and the walk's first `Navigate.to`
    took 12.5 s cold against 1.9 s warm.
- **Left from the `ui` sweep of 2026-10-30**:
  - The 🎮 button and the Game dialog name a target by its id once the saved list is of an older version: "🎮
    com.zjcs.android.us@Waydroid" for Sword x Staff, "Heroic game 43d4ef20…" for Firestone. The names come back
    only after a scan, and the dialog's own rows are not redrawn when it lands.
  - The Game dialog's *Other apps* has a tile with no name; *Games in the app menu* and *Other apps* are still
    listed on Linux beside Lutris.
  - The Parameters card for `launchTargets` cuts its *Category*, *Value* and *Note* labels to "...".
  - The Screens window at its 1180-wide default cuts most of its toolbar and the side panel's buttons to "...".
  - Naming Firestone's 49 old shots in one step (`ScreenShots.adopt`) held Studio's FX thread 12.3 s, in
    `ProjectAnalyzer.createCompilationUnits` ×179: each setup write re-parses the project. Once per old map; not
    measured again since a batch reads the bot's types once (2026-10-30).
  - Moving Firestone's files into the SDK's folder (`ResourceMove.rewritePaths`, 334 picture writes in one batch)
    held the FX thread 24.7 s, in `SourceFormatter.snippet` ×444 and `JavaExpressions.parse` ×252: each write
    formats and parses its expression (`JavaValue.built`, the host's `ShortNames`). Once per bot; a batch should
    format each value once, or not until it is shown.
- **Left from the resources move (2026-10-30, phase 5d's review)**:
  - After *Not now*, the SDK's editors look in its folder only: Manage Pictures and Screens show none of the old
    files, and `ScreenShots.saveIfMissing` takes a new shot into the folder for a screen whose shot is still at the
    root. A run finds both. Reading the root as well in `TemplateLibrary` and `ScreenShots` would make *Not now*
    cost nothing.
  - `TemplateSource.FOLDER` spells the SDK's folder from its coordinate today; Studio derives it from the coordinate
    the plugin loaded under (`PluginOwners.folder`), so an SDK built under another groupId (a fork) would save
    pictures where its run does not look. The run could read its own coordinate from its jar's
    `META-INF/maven/.../pom.properties`.
- **macOS: OCR has never run on a Mac** (natives staged 2026-10-11 without one). Owed on Apple Silicon:
  `OcrEngine.recognize` and `getWords` load the `darwin-aarch64/` natives and read text; whether dyld maps
  Leptonica once or twice (`vmmap <pid> | grep lepton`), and that OCR works either way.
- **Release owed**: `--sdk 2.0.0 … --studio` (`/release-check` names what it forces).
- **Activity Flow**: a flow written from *+ New outcome…* while the Activity Flow window is open is
  overwritten by that window's next save (it holds its own drafts). Dragging from a wired port refuses
  ("remove that wire first") instead of moving the arrow — a gesture change, waiting for a decision. A
  hand-chained flow is shown read-only.
- **A screen map's conditions as expressions** (the user's call, 2026-10-15): `Input.when`, `Way.when` and
  `ScreenVariant.when` take only a method reference, since no plugin value carries Java text. An inline
  `() -> …` needs a contract capability first, a held part that is code which Studio edits as blocks, at the next
  contract release (`/contract-change`).
- **Screens on more than one surface** (the 2026-10-22 audit, gap 7): the map is one game window. A launcher
  before the game, a second window, a browser tab or switching apps has no place on it. The design question is a
  screen's `surface` (the game window, a launcher, another window by its identity) and how a walk moves between
  surfaces; not built.
- **Screens** (`docs/architecture/screens.md`), still open from the screen-map plan (`reactive-cooking-reef.md`):
  sub-states of a screen other than tabs (a toggle, a filter, a scroll position; phase 8) and chores kept as data
  (phase 9). Owed checks: the Discovery tools and
  `edit_map` live over MCP on Firestone, and the 🗺 Screens click-tests of the 2026-10-15 audit.
- **A timed-out find inside an activity** clears an unmapped overlay (`PopupGuard.uncover`) but does not walk back
  to the activity's screen: only a thrown `BotStuckException` does.
- **Launch targets**: `Target.set(String spec)` has no editor (a `@LaunchTargetSpec` could reuse the
  🎮 game dialog).
- **Waydroid on the desktop**: a bot reads Android's frame through `adb screencap`, which writes nothing on a
  hybrid AMD/NVIDIA laptop (`/vendor/etc/hwdata/amdgpu.ids: No such file or directory`). A private display
  is unaffected (it reads through X); on the desktop the Waydroid window's own X capture, or the Pilot's
  scrcpy stream, would be the fallback.
- **`OcrOptions`**: `withLanguages(OcrLanguage...)` is not a declared chain (the host would need to read a
  varargs argument inside a receiver chain); `Text.DEFAULT_OPTIONS` shows as written — an `OcrOptions`
  constant plus `.constants(…)` would fix both reading and fresh blocks.
- **`Spots` and `Zones`**: only the assistant's `save_point`/`save_region` write their numbers; Studio locks
  the files as it locks every `@SdkValue` holder, and a window only changes a place's anchor.
- **Read-only chains**: a hand-written capture chain (`CaptureSource.window("G").region(r)`) and a
  `Precision` wither chain; the host writes the one-call forms.
- **Remote Pilot**: a quick tunnel's address changes at every start, so the APK's saved connection goes
  stale. Tailscale Funnel `serve … off` dropping `:443`'s Funnel flag is unchecked end to end.
- **Trace prefixes**: `BotSettings` (`[Input]`) and `FlowWalker` (`[Flow]`, `[Activity]`) still spell them;
  their lines need two names each. `Sound` prints its notice with `System.out` and no debug gate.
- **Pickers**: the colour eyedropper opens `SurfaceMenu.choose` as a second menu; pins are not remembered
  between openings; a window tile is pre-selected only on an exact whole-title match; no FX test drives
  `KeyboardView`. `Color` stays here (its eyedropper needs screen capture, which basics lacks).
- **Pictures**: a user-typed path literal (`new ImageTemplate("…/ore.png")`) is not rewritten on rename, by
  rule; a picture named before the constant rule has no constant and no findable uses.
- **Capture Templates lost the suggested tag** (the open activity's); *which file the editor has open* is
  host state the contract does not carry. Revisit if a second plugin wants it.
- **`capture.source` in `botmaker-project.properties`** is still written beside the managed value; retiring
  the key is its own step.
- **The Capture Source dialog** has no empty-state message; a pick creates `Sdk.java` silently apart from the
  history entry.
- **`PilotProject` over a temp directory** would make `TargetCapture.captureDesktop`'s default-target arm
  testable; nothing covers it.
- **`Sound.miaou()`**: tune by ear against Scratch's meow; `play(file)` or a beep would sit on the same
  facade.
- **Screens, from the Firestone repair (2026-10-16)**:
  - `edit_map` rename keeps the constant (`FAVORED_WAR_MACHINES` now names "Select the favored war
    machines", the tab `FAVORED_WAR_MACHINES_2`); Studio's rename refuses `Screens.java`.
  - A crop call that sets a marker and notes inputs on a new screen leaves the inputs unchecked ("no marker
    yet"): check them after the marker is written.
- **Record into <activity>, past its first cut** (2026-10-22): a press is written when Live gives it, not once the
  game shows its result; a strip's or tree's member, a repeated button's `Pick` and the inspector's ▶ Try are not
  recorded; the flow editor has no box for a card's `.on(Screens.X)` (written in `Flows.java` by hand). Logic as a
  managed `Chores` value stays set aside (a second language, against "bodies are Java on map calls").
- **Screens audit gaps (2026-10-22)**, each its own plan:
  - A `Hotkey` of a `KeySequence` (combos one after another, a wait between); `Keyboard.sequence` already does it.
  - A text field or a drag cut as one in the cut form (today a button cut, then *Text field* or *Drag* in the input
    panel; a drag's drop cut on the same board); an emulator's
    field cleared before typing (adb has no select-all); a hotkey on an emulator sent as Android key events (it goes
    to the host's keyboard today).
  - Words, past their first cut (2026-10-22): a text button's gesture, traits and copies edited in the input panel
    and by set_input (today written in `Screens.java`, or cut again); words on a screen look (`ScreenVariant`) and
    words a screen never shows; a text button in a strip; the picker's box for one (its zone); the anchor of a words'
    zone in the cut form (center today); a walk's poll reads a screen's words twice when it falls back to the whole
    frame (`Markers.onEach` keeps them per call).
  - Places, past their first cut (2026-10-22): 🗺's board boxes a `beside` spot or a grid on its anchor, not where it
    is pressed (`ScreenCanvas`); the Pick editor shows `Pick.at` and `Pick.cell` read only; the input panel's *Where*
    and set_input place by a picture only (a place from words is written in `Screens.java`); a way through a placed
    copy (`Way.pick(Pick.at(…))`) is not offered in the window; a spot is moved by cutting it again; a spot and its
    anchor's own button share a picture, so a tool naming the picture reaches the anchor's button (name the spot by
    its field). Rows matched by content stay open.
  - Readouts, past their first cut (2026-10-22): `Navigate.read` and `number` are `@Hidden` from the palette, no
    block picking a readout (Studio reads `Screens.HOME.gold` as an input), and Studio offers a readout field in an
    input's slot, since the contract's fields stand for one reference
    type (`BotConstants.input`); 🗺's board and cards draw no readout; no tool reads one live (read_text by its zone
    does); a readout is renamed or removed in `Screens.java`; `Navigate.to` cannot say which subject to walk to (hero
    N), only `Navigate.subject` reads which one shows.
  - Tabs and looks, past their first cut (2026-10-22): the *Look* picker is on the side panel only (the card
    follows it, with no picker of its own); the audit checks a screen's own shot, so on a look's shot the boxes sit
    where the plain shot found them; a card shows no "tab of" pill; the cut form has no *tab* kind (a tab is marked
    from the ⋯ menu once its way in is cut, and its button's Selected look cut as a look); a look with no marker is
    never recognised, so its shot is never taken.
  - Screen times, past their first cut (2026-10-22): a screen's `takes` is written once, by the first try that opens
    or reaches it, and never again (a slower later show does not raise it); a walk's own steps measure nothing; no
    hand editor sets it in 🗺 (it is written in `Screens.java`); a way back costs the screen it returns to, the same
    whichever input closes.
  - The by-hand flow, past its first cut (2026-10-22): the input editors' rarer actions stay in their parts (folded,
    not in a ⋯); a suggested name is the cut form's only, crop_pictures still takes the assistant's; a part's fold
    is remembered per computer, not per project; a Try that opens a screen it is told the name of starts the marker
    cut, one that lands on an unknown screen with no name still writes nothing.
  - Scale: a parse cached by file hash (a 45-screen read takes ~1 s).
- **A run whose saved window is missing re-matches by the launch's pids** (launch-target plan 2026-10-19, not
  built): Launch and pick saves the window by class (+ title when generic); a run that launched the game and finds no
  such window could pick the new window whose pid descends from the launch, and log it. Only `exe:`/`cli:` (and
  Faugus) give real descendants; Steam and Lutris games are children of the client, so a pid says little there.
- **A Mac's Linux game VM is judged as macOS** (`SessionBootstrap.vmRefusal` asks `GuestLaunch.macCommand` by the
  host's system): ask the VM's `guestOs` and `linuxCommand` for a Linux guest.
- **The Runner's "First available here" skips a game its place can't start** (`InstalledGame.pick` reads only the
  system; the choice greys by `SessionBootstrap.placeRefusal`).

## Deliberately not planned

- **A plugin event bus**: every picker re-reads on open and the host's writes reach every editor. Revisit per
  fact, when a second real consumer exists.
- **Rewriting the dated sections of `CLAUDE.md`'s moved docs to 2.0 paths**: the header notes map them.
