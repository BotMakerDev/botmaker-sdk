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

## Deliberately not planned

- **A plugin event bus**: every picker re-reads on open and the host's writes reach every editor. Revisit per
  fact, when a second real consumer exists.
- **Rewriting the dated sections of `CLAUDE.md`'s moved docs to 2.0 paths**: the header notes map them.
