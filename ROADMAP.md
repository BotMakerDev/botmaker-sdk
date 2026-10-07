# ROADMAP

Completed work up to 2026-10-05: see CHANGELOG.md, docs/refactor/, and `git show 9beeccf:ROADMAP.md`.

## Open

- **Release owed**: `--sdk 2.0.0 --gamebot … --studio` (the umbrella `CLAUDE.md` › Templates). The gamebot
  template stays on its old pin until that run.
- **Activity Flow**: a flow written from *+ New outcome…* while the Activity Flow window is open is
  overwritten by that window's next save (it holds its own drafts). Dragging from a wired port refuses
  ("remove that wire first") instead of moving the arrow — a gesture change, waiting for a decision. A
  hand-chained flow is shown read-only.
- **Launch targets**: `Target.set(String spec)` has no editor (a `@LaunchTargetSpec` could reuse the
  🎮 game dialog).
- **The 🎮 game dialog's tiles are far too tall** (seen on Windows 2026-10-07, either theme): a tile without
  cover art ("Firestone Online Idle RPG", Epic) is about 450 px tall for a 128 px cover and two caption lines,
  and a shorter name ("Luto") makes a shorter tile, so the height follows the caption's length. `GameDialog.tile`
  sets only a width and the caption wraps, so the tile's preferred height is probably measured before the
  wrapping width is known. The `FlowPane`'s default centred rows also offset tiles of different heights.
- **Waydroid on the desktop**: a bot reads Android's frame through `adb screencap`, which writes nothing on a
  hybrid AMD/NVIDIA laptop (`/vendor/etc/hwdata/amdgpu.ids: No such file or directory`). A private display
  is unaffected (it reads through X); on the desktop the Waydroid window's own X capture, or the Pilot's
  scrcpy stream, would be the fallback.
- **`OcrOptions`**: `withLanguages(OcrLanguage...)` is not a declared chain (the host would need to read a
  varargs argument inside a receiver chain); `Text.DEFAULT_OPTIONS` shows as written — an `OcrOptions`
  constant plus `.constants(…)` would fix both reading and fresh blocks.
- **`Points` and `Regions`**: only the assistant's `save_point`/`save_region` write them; Studio locks the
  files as it locks every `@Managed` holder, and no window picks or moves a spot by hand.
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

## Deliberately not planned

- **A plugin event bus**: every picker re-reads on open and the host's writes reach every editor. Revisit per
  fact, when a second real consumer exists.
- **Rewriting the dated sections of `CLAUDE.md`'s moved docs to 2.0 paths**: the header notes map them.
