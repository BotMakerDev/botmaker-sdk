# A plugin's values are Java this plugin ships

`../docs/refactor/33-plugin-java.md`. Each value is marked `@SdkValue(SdkValue.Id.…)` (`api.bot.SdkValue`,
meta-annotated `@ManagedMarker`), so a misspelt id is a compile error. A project gets its files from its
template, in `src/main/java/<bot package>/plugins/sdk/`: **`Sdk.java`** — `RUN` returning a `Run` (the game's
home and setup, which no flow owns), `SETTINGS` a `BotSettings`, `FLOW_LAYOUT` a
`FlowLayout` keyed by flow, `LAUNCH_TARGETS` the games the bot supports, `VM` its virtual machine, each written as a
chain of named calls — then
**`Flows.java`** (`FLOWS`, an open set: one `Flow` constant per way of playing, `MAIN` by default, each its
steps, arrows and start, `Flow.activity(Activities.Collect.class, Collect::body).described("…").goesHome()`),
**`Pictures.java`**, `PICTURES` on the type, one `ImageTemplate` constant per picture, filed under its tags and
activities by its own chain (`.tags(List.of(Tags.UI)).activities(List.of(Activities.Collect.class))`, 2026-10-14),
**`Tags.java`** (`TAGS`: one `PictureTag.named("UI")` per custom tag 🖼 Manage Pictures groups pictures under,
which a picture refers to by its constant), and
**`Activities.java`** (`ACTIVITIES`: one nested `enum Collect implements Outcome { NEXT, NOTHING_LEFT }` per
activity, each name's label its words),
**`Goals.java`** (`GOALS`, an open set, 2026-11-06: one `Goal` constant per thing the bot wants, `Goal.of(Need.atLeast(
Progress::gold, 100))`, `Goal.of(GameLogic::eventFinished)` or `Goal.done(() -> Screens.HOME.claim).within(…)`) and
**`Plans.java`** (`PLANS`: one `Plan` constant per plan, its tiers of goals, `Plan.of(Plan.tier(Goals.A), …)`). What
is done on a screen is a `Do` field of that screen in `Screens.java`, beside the input it presses, never a value of its
own (`screens.md` § Actions). Beside them the host writes **`Zones.java`** (`ZONES`, 2026-10-11):
named `Zone`s in the bot's pixels from the game's corner, which the assistant's `save_region` adds and a run
places on the live game (`internal/vision/Places`). `Spots.java` (`SPOTS`) went on 2026-10-15: a bot clicks
pictures, never places. They replaced `POINTS`/`REGIONS` (desktop `Point`s, source `Rect`s, 2026-10-06),
which nothing converts. **`Digits.java`** (`DIGITS`,
2026-10-11) holds a `DigitSet` per game font, a folder of `0.png`–`9.png` under `src/main/resources/digits/`
that the assistant's `save_digits` cuts (`plugin/pictures/DigitCuts`) and `Text.readDigits` reads with
(`internal/vision/DigitReader`). They are the user's: the host
rewrites the expression a marked method returns and the constants of a marked type, and nothing else,
a body that is not exactly `return <expr>;` is read-only with a reason, and Studio locks the whole file on the
canvas while this plugin is loaded (its values change in this plugin's windows). A project without them gets
them from the host on bind, from the `ManagedValue`s `SdkValues` declares.

**A parameter is the user's, and private (2026-11-04).** `ROUTINE` (what a run runs, a `Flow` or a `Plan`, picked in
the Runner, 2026-11-06; it replaces `FLOW`, deprecated and read while `ROUTINE` is `Flow.NONE`) and `DO_SETUP` are methods of
`params/com_github_botmakerdev/botmaker_sdk/Running.java`, `LAUNCH_TARGET` and `CAPTURE` of `Game.java` beside it
(`Holder.RUNNING`, `Holder.GAME`, 2026-11-05; each group is a category), this computer's and never committed, with the
author's starting values in `starting-params/` at the same paths; `ParamGroups` finds the groups from the holder
(`…plugins.<g>.<a>.Sdk` reads every `@Param` class of `…params.<g>.<a>`). What the author alone sets (`LAUNCH_TARGETS`, `VM`) is a marked
method above, in the committed holder. A fact is a method of the bot's own `@Facts` group in `params/bot/`,
`@Param.Shown(changes = DURING_RUN)`, holding its value only; a screen's readout says where it is read and how long a
reading holds, `Readout.into(Progress::gold).staleAfter(…)` (2026-11-06; `@GameState`, `@ReadOn`, `@StaleAfter` and
`@Assumed` are deprecated, read by `RunFacts` until removed, a readout winning over `@ReadOn`): a run writes it (`Params.set`) and keeps its time
in `@Param.Kept`, `Params.when(Progress::gold).atLeast(5)` tests it, and the assistant adds, lists and forgets it
through `edit_map` `add_fact` and `setup` `game_facts`/`forget`. The assistant never sees a parameter changed outside
a run. A game account is plain user data, with nothing here around it.

**Nothing is stored anywhere else.** There is no `activities.json`, `capture.json`, `capture.source`, `Wire`,
`ProjectData`, parameters JSON, `templates.json` (the picture tags, gone 2026-10-14) or generated source, and
**no reader of those files**: a project written before
the flow was Java reads as having no flow, and nothing deletes anyone's old file. A later change to a managed
value's shape converts nothing either: Studio's upgrade repairs the bot's values as it repairs its code, an id
that is gone removed and a value that no longer compiles reset to its first value and marked `@Refactor`
(`../docs/refactor/25-compatibility.md` §12, 2026-10-11). What one computer launches
is a parameter, `LAUNCH_TARGET`; what a bot was tested on is its gallery entry.
