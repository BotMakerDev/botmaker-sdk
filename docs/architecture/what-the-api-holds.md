# What the API holds

- `api.vision` — `ImageFinder` (find + `exists` + the lambda control flow `whileExists`/`ifExists`/
  `untilExists`), `ImageClicker`, `ImageWaiter`, `MatchResult`, `ImageTemplate`, `ImageTemplateGroup`,
  `Pixel`, `Vision` (the last image and colour match). `api.text` is OCR: `Text` (with `Text.lastMatch()`),
  `TextMatch`, `TextResult`, `OcrOptions`, `OcrLanguage`. `Precision` is `Pixel`'s knobs as one value (`EXACT`/`TIGHT`/`DEFAULT`/`LOOSE`, a validating
  `of(…)`, withers): a type because ΔE has no obvious scale and the pixel count is an *area* routinely misread
  as a width, and because an editor is claimed by **type**, never by a method and an argument index.
- `api.bot` — `Bot.run(Home::goHome, Sdk.class)` (installs every `@Managed` value it is handed and walks the
  flow, `internal/flow/FlowWalker`), `Outcome` (a body is `public static Outcome body()` returning one of the
  bot's `Outcomes` constants or `Outcome.NEXT`; `FlowWalker.current()` says which activity runs and logs an
  unwired outcome the step does not declare), `ActivitySwitch` (`enable`/`disable`/`active` an `Activity` —
  the facade was `Activities` until 2026-10-02, when that name became the bot's own class of constants), and
  **`BotSettings`**, the runtime tuning
  as an immutable value: `@Managed("settings")` in `Sdk.java`, read as `BotSettings.current()`, changed for a
  while with `BotSettings.use(…)`, edited in ⚙ Bot Settings (`plugin/settings/BotSettingsWindow`).
- `api.flow` — `Flow` (`steps`, `edges`, `presets`, `start`, `limits`), `Flow.activity(Activities.COLLECT,
  Collect::body, …)` building a `Flow.Step` (an activity's work is a **method reference**, so a rename is a
  compile error naming `Sdk.java`), `Activity`, `FlowLayout` (the editor's card positions, keyed by label,
  which a run ignores), `ActivityBody`; `internal.flow.Flows.enabled(activity)` reads an activity's switch.
- **Activities and outcomes are constants, never strings (2026-10-02).** `Activity.named("Collect")` and
  `Outcome.named("Won")` are values equal by label; a bot holds them as `@Managed("activities")
  Activities` and `@Managed("outcomes") Outcomes` open sets in `plugins/sdk/` (`SdkValues.ACTIVITIES`/
  `OUTCOMES`), and the host writes a value equal to a constant as the constant. The flow canvas works on
  labels (`plugin/flow/Arrow`, `Selection`) and converts at load and save. A label is free text and its
  constant is `FlowNames.constantFor` (`"Bag full"` → `BAG_FULL`); a save keeps the two classes in step by
  binding (`plugin/flow/FlowConstants`: rename, add, write the flow, remove), and an outcome is one constant
  however many cards declare it.
- `api.capture` — `CaptureSource` (`desktop()`, `monitor(i)`, `window(title)`, `emulator(name)`, `region(…)`;
  `capture()` and `origin()` go together), `Source.current()` (what `Bot.run` installed, or the whole desktop).
- `api.input` (`Mouse`, `Keyboard`, …), `api.time` (`Wait`, `Time`), `api.sound`, `api.geometry`.
- `api.console.Ask` — asks the user (`text`, `number`, `whole`, `yesNo`, `choice`). Under Studio it sends a
  `TelemetryEvent.Ask` on the run's telemetry socket (`IpcObserver.client()`) and blocks on the `Answer` Studio
  writes back; with no Studio it prints the prompt and reads stdin. Bad input re-asks; cancel throws
  `CancellationException`. `BotMaker` and its `BM-INPUT` stdout marker were deleted on 2026-10-01.
- **Api parameters that want an editor carry an annotation**: `internal.emulator.@EmulatorName` on `Emulators`, whose device picker no offered call reaches any more
  (kept, flagged 2026-10-01). `@ActivityName`/`@OutcomeName` went on 2026-10-02: an `Activity` and an
  `Outcome` are `SdkTypes` with their own pickers (`plugin/editors/ActivityEditors`). The launch annotations and their game grids went on 2026-10-01. `SdkEditors.ALL`
  is `SlotEditor.onParameter(X.class).draw(…)`. The `BotSettings` withers carry nothing since 2026-10-01:
  `plugin/editors/SettingHints` keys each one's label and range by method reference (`@Setting` is deleted).
