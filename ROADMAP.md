# ROADMAP

Completed work up to 2026-10-05: see CHANGELOG.md, docs/refactor/, and `git show 9beeccf:ROADMAP.md`.

## Open

- **macOS: OCR has never run on a Mac** (natives staged 2026-10-11 without one). Owed on Apple Silicon:
  `OcrEngine.recognize` and `getWords` load the `darwin-aarch64/` natives and read text; whether dyld maps
  Leptonica once or twice (`vmmap <pid> | grep lepton`), and that OCR works either way.
- **Release owed**: `--sdk 2.0.0 --gamebot … --studio` (the umbrella `CLAUDE.md` › Templates). The gamebot
  template stays on its old pin until that run.
- **Activity Flow**: a flow written from *+ New outcome…* while the Activity Flow window is open is
  overwritten by that window's next save (it holds its own drafts). Dragging from a wired port refuses
  ("remove that wire first") instead of moving the arrow — a gesture change, waiting for a decision. A
  hand-chained flow is shown read-only.
- **A screen map's conditions as expressions** (the user's call, 2026-10-15): `Input.when`, `Way.when` and
  `ScreenVariant.when` take only a method reference, since no plugin value carries Java text. An inline
  `() -> …` needs a contract capability first, a held part that is code which Studio edits as blocks, at the next
  contract release (`/contract-change`).
- **Screens** (`docs/architecture/screens.md`): a long `GameScreen` chain is written as one line by the host;
  it needs Java formatting, one call per line. Still open from the screen-map plan (`reactive-cooking-reef.md`):
  sub-states of a screen (phase 8) and chores kept as data (phase 9). Owed checks: the Discovery tools and
  `edit_map` live over MCP on Firestone, and the 🗺 Screens click-tests of the 2026-10-15 audit.
- **A screen's chores in the picture picker**: a click's picture after `Navigate.to(Screens.X)` in the same body
  could offer X's `Kind.CHORE` inputs first. The plugin reads no Java text, so the host would have to say which
  screen the body walked to: a contract capability first (`/contract-change`).
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
- **Activities coupled to the map** (Screens audit 2026-10-22, §6c of `~/.claude/plans/make-an-audit-of-functional-fern.md`;
  a spec first, by brainstorming): an activity declares its screen (`Flow.activity(..).on(Screens.TAVERN)`), so 🗺
  lights an activity's screens and the picture picker offers its chores; 🗺 and Live get *Record into <activity>*,
  each press or Try appended to the body as a `Navigate.press(..)` line (a new host capability: insert a statement
  in a method, `/contract-change`). The body stays Java; logic as a managed `Chores` value was weighed and set aside
  for now (a second language, against "bodies are Java on map calls").
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
    its field). Rows matched by content and a screen with a subject (hero N) stay open.
  - Readouts and results on a screen: zones and digits per screen, an input's outcomes (Victory | Defeat), so a body
    stops waiting on pictures by hand.
  - Edge costs: measured step and settle times feed `ScreenGraph.pathTo` and the walk's waits instead of `STEP_MS`.
  - The by-hand flow (after the owed click-tests): "next untried" in the window, suggested cut names, the cut form
    split by intent, Try that freezes the opened screen and starts its marker cut, an empty map instead of the
    template's placeholder Home.
  - Scale: a parse cached by file hash (a 45-screen read takes ~1 s).
- **A screen's looks on 🗺 Screens** (the user's call, 2026-10-16, a later plan): a `ScreenVariant` (Halloween)
  cannot be shown; each screen keeps one shot and rows only say "look X". Wanted: one shot per look, saved when
  Discovery cuts the look; a look picker on the card and the panel; rows, boxes and list strips filtered to the
  picked look. A button's looks already step with ◀ ▶ (`LookToggle`).
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
