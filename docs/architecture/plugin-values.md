# A plugin's values are Java this plugin ships

`../docs/refactor/33-plugin-java.md`. Each value is marked `@SdkValue(SdkValue.Id.…)` (`api.bot.SdkValue`,
meta-annotated `@ManagedMarker`), so a misspelt id is a compile error. A project gets its files from its
template, in `src/main/java/<bot package>/plugins/sdk/`: **`Sdk.java`** — `FLOW` returning a `Flow`,
`CAPTURE` a `CaptureSource`, `SETTINGS` a `BotSettings`, `FLOW_LAYOUT` a `FlowLayout`, each written as a chain
of named calls (`Flow.activity(Activities.Collect.class, Collect::body).described("…").goesHome()`) — then
**`Pictures.java`**, `PICTURES` on the type, one `ImageTemplate` constant per picture, and
**`Activities.java`** (`ACTIVITIES`: one nested `enum Collect implements Outcome { NEXT, NOTHING_LEFT }` per
activity, each name's label its words). Beside them the host writes **`Spots.java`** and
**`Zones.java`** (`SPOTS`, `ZONES`, 2026-10-11): named `Spot`s and `Zone`s in the bot's pixels from the game's
corner, which the assistant's `save_point` and `save_region` add and a run places on the live game
(`internal/vision/Places`). `POINTS`/`REGIONS` (desktop `Point`s, source `Rect`s, 2026-10-06) are retired:
`SdkConversions.PLACES` turns them into spots and zones when a bot upgrades. They are the user's: the host
rewrites the expression a marked method returns and the constants of a marked type, and nothing else,
a body that is not exactly `return <expr>;` is read-only with a reason, and Studio locks the whole file on the
canvas while this plugin is loaded (its values change in this plugin's windows). A project without them gets
them from the host on bind, from the `ManagedValue`s `SdkValues` declares.

**Nothing is stored anywhere else.** There is no `activities.json`, `capture.json`, `capture.source`, `Wire`,
`ProjectData`, parameters JSON or generated source, and **no reader of those files**: a project written before
the flow was Java reads as having no flow, and nothing deletes anyone's old file. A later change to a managed
value's shape is different: the SDK declares its conversion and Studio runs it on upgrade
(`../docs/refactor/25-compatibility.md` §12, 2026-10-11). What one computer launches
is a run property; what a bot was tested on is its gallery entry.
