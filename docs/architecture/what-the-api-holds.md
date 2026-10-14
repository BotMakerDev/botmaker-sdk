# What the API holds

- `api.vision` — `ImageFinder` (find + `exists` + the lambda control flow `whileExists`/`ifExists`/
  `untilExists`), `ImageClicker`, `ImageWaiter`, `MatchResult`, `ImageTemplate`, `ImageTemplateGroup`,
  `Pixel`, `Vision` (the last image and colour match), `Screen` (`waitStable`/`waitChange`, a fixed tolerance,
  2026-10-11). `api.text` is OCR: `Text` (with `Text.lastMatch()`),
  `TextMatch`, `TextResult`, `OcrOptions`, `OcrLanguage`. `Precision` is `Pixel`'s knobs as one value (`EXACT`/`TIGHT`/`DEFAULT`/`LOOSE`, a validating
  `of(…)`, withers): a type because ΔE has no obvious scale and the pixel count is an *area* routinely misread
  as a width, and because an editor is claimed by **type**, never by a method and an argument index.
- `api.bot` — `Bot.runFlow(Sdk.flow, Sdk.doSetup, Sdk.skip, Sdk.class)`, "Run activity flow", offered in
  `main` only (`@EntryPoint`; installs every managed value in the classes it is handed, `@SdkValue` or another
  plugin's mark, and walks the flow the bot's `flow` `@Param` names, `internal/flow/FlowWalker`; it was
  `Bot.run(goHome, …)` until 2026-10-10, when what gets home moved onto the flow, and `runFlow(Sdk.class)` until
  2026-10-11, when what a run does became the bot's `@Param`s), `Bot.stop("…")` (ends the run saying why), `Outcome` (the interface each activity's enum implements; `Outcome.NONE` is "no outcome yet",
  what a fresh editor value holds), `ActivitySwitch` (`enable`/`disable`/`active` an activity by its class,
  `Activities.Battle.class`, `@ActivityClass` for its picker), and
  **`BotSettings`**, the runtime tuning
  as an immutable value: `@SdkValue(SdkValue.Id.SETTINGS)` in `Sdk.java`, read as `BotSettings.current()`, changed for a
  while with `BotSettings.use(…)`, edited in ⚙ Bot Settings (`plugin/settings/BotSettingsWindow`).
- `api.flow` — `Flow` (`steps`, `edges`, `start`, `limits`), one per way of playing, each a constant of the
  bot's `Flows` (2026-10-11; presets are gone, a preset is a flow), `Flow.activity(Activities.Collect.class,
  Collect::body)` building a `Flow.Step`, each other part a named link written only when not the default
  (`.described(…)`, `.off()`, `.switchable()` — the runner may skip it through `Sdk.skip` — `.goesHome()`,
  `.whenOff(…)`; 2026-10-09, as `BotSettings.defaults().foundDelay(…)…` is),
  `Flow.edge(Activities.Collect.NOTHING_LEFT, Activities.Battle.class)` (its source is the outcome's enum),
  `Run` (`Run.of().home(Gamebot::goHome).setup(Gamebot::setup)` in `Sdk.run()`, once for the bot; an activity's
  work, the way home and setup are **method references**, so a rename is a compile error naming the holder),
  `Activity` (an activity by its enum's simple name, the key runtime and editor agree on), `ActivityBody<O>` (a
  body returns its own activity's enum), `FlowLayout` (the editor's card positions, keyed by flow then by
  activity name, which a run ignores); `internal.flow.InstalledRun` holds what `Bot.runFlow` installed.
- **A card may run another flow (2026-10-12).** `Flow.Step.node()` is a sealed `Flow.Node`: an `Activity` or a
  `Flow.Sub` (`Flow.subflow(DAILY)`). `FlowWalker.walk` runs the inner flow under its own limits and switches and
  returns the outcome it ended on; the outer wire is the `Flow.Edge` whose `via()` is that flow and whose `on()`
  is that outcome. A card's exits are the inner flow's unwired end outcomes, never held anywhere
  (`internal.flow.FlowTree.endOutcomes`). The editor holds `Flows.DAILY` by name (`BotConstants.flow`, so an
  empty flow or two equal ones stay apart) and labels the card `▸ DAILY` and an exit `Claim · Done`
  (`internal.flow.Labels`). The inner flow is declared above its user; the editor moves it there
  (`PluginValues.moveBefore`) and offers no flow that would loop, and a run refuses one read before its
  declaration. `plugin/flow/FlowRefs` answers what the canvas, the flow tree and the assistant's `FlowTools` ask
  across flows: exits, broken exits, which flows run which.
- **An activity is its outcome enum (2026-10-10; constants since 2026-10-02, never strings).** The bot's
  `@SdkValue(ACTIVITIES) final class Activities` in `plugins/sdk/` nests one `enum Collect implements Outcome
  { NEXT, NOTHING_LEFT }` per activity (`SdkValues.ACTIVITIES`, an `ofEnums` grouped open set); its constants
  are the activity's exits. A body is `public static Activities.Collect body()`, so javac refuses another
  activity's outcome. `NEXT` is an ordinary constant the editor seeds and the user may rename or remove;
  `DISABLED` is internal to the walker. A label is the name as words (`internal.flow.Labels`, `NOTHING_LEFT` →
  "Nothing left", `DailyReward` → "Daily reward"; a typed Java type name stays as typed). An editor cannot
  load the bot's enum, so it holds a constant by name (`internal.flow.BotConstants`, equal by name) and the
  host writes it back as the constant. The flow canvas works on labels (`plugin/flow/Arrow`, `Selection`) and
  converts at load and save through `FlowNames`. A save keeps the enums in step by binding
  (`plugin/flow/FlowConstants`: rename, add, write the flow, remove); an enum keeps at least one constant.
- `api.capture` — `CaptureSource` (`desktop()`, `monitor(i)`, `window(title)`, `emulator(name)`, `region(…)`;
  `capture()` and `origin()` go together), `Source.current()` (what `Bot.runFlow` installed, or the whole desktop).
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
