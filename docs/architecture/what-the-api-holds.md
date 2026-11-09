# What the API holds

- `api.vision` — `ImageFinder` (find + `exists` + the lambda control flow `whileExists`/`ifExists`/
  `untilExists`), `ImageClicker`, `ImageWaiter`, `MatchResult`, `ImageTemplate`, `ImageTemplateGroup`,
  `Pixel`, `Vision` (the last image and colour match), `Screen` (`waitStable`/`waitChange`, a fixed tolerance,
  2026-10-11). `api.text` is OCR: `Text` (with `Text.lastMatch()`),
  `TextMatch`, `TextResult`, `OcrOptions`, `OcrLanguage`. `Precision` is `Pixel`'s knobs as one value (`EXACT`/`TIGHT`/`DEFAULT`/`LOOSE`, a validating
  `of(…)`, withers): a type because ΔE has no obvious scale and the pixel count is an *area* routinely misread
  as a width, and because an editor is claimed by **type**, never by a method and an argument index.
- `api.bot` — `Bot.run(Sdk.class)`, "Run the bot's plan", offered in
  `main` only (`@EntryPoint`; installs every managed value in the classes it is handed, `@SdkValue` or another
  plugin's mark, reads the run's `@Param`s `plan` and `doSetup` by their marks, and runs that plan round after
  round, `internal/plan/RunGame`; no plan, or one with no goals, runs nothing and says so). It was
  `Bot.runFlow(Sdk.class)`, walking a flow of activities, until 2026-11-09, when flows were deleted outright
  (no deprecated release, the user's call). `Run` (`Run.of().home(MyBot::goHome).setup(MyBot::setup)` in
  `Sdk.run()`, once for the bot; the way home and the setup are **method references**, so a rename is a compile
  error naming the holder; `RunTypes` declares it, `internal.bot.InstalledRun` holds what `Bot.run` installed),
  `Bot.stop("…")` (ends the run saying why), and **`BotSettings`**, the runtime tuning
  as an immutable value: `@SdkValue(SdkValue.Id.SETTINGS)` in `Sdk.java`, read as `BotSettings.current()`, changed for a
  while with `BotSettings.use(…)`, edited in ⚙ Bot Settings (`plugin/settings/BotSettingsWindow`).
- `api.plan` (`Goal`, `Need`, `Plan`, `Reset`) and the map's `Do` and `Custom` (`api.screens`) are what a run
  does: `screens.md` § goals and plans. Java that needs no screen is plain Java in `main` before `Bot.run`, or a
  `Custom` input of a screen.
- `api.capture` — `CaptureSource` (`desktop()`, `monitor(i)`, `window(title)`, `emulator(name)`, `region(…)`;
  `capture()` and `origin()` go together), `Source.current()` (what `Bot.run` installed, or the whole desktop).
- `api.input` (`Mouse`, `Keyboard`, …), `api.time` (`Wait`, `Time`), `api.sound`, `api.geometry`.
- `api.console.Ask` — asks the user (`text`, `number`, `whole`, `yesNo`, `choice`). Under Studio it sends a
  `TelemetryEvent.Ask` on the run's telemetry socket (`IpcObserver.client()`) and blocks on the `Answer` Studio
  writes back; with no Studio it prints the prompt and reads stdin. Bad input re-asks; cancel throws
  `CancellationException`. `BotMaker` and its `BM-INPUT` stdout marker were deleted on 2026-10-01.
- **Api parameters that want an editor carry an annotation**: `internal.emulator.@EmulatorName` on `Emulators`, whose device picker no offered call reaches any more
  (kept, flagged 2026-10-01). The launch annotations and their game grids went on 2026-10-01. `SdkEditors.ALL`
  is `SlotEditor.onParameter(X.class).draw(…)`. The `BotSettings` withers carry nothing since 2026-10-01:
  `plugin/editors/SettingHints` keys each one's label and range by method reference (`@Setting` is deleted).
