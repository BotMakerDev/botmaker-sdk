# A plugin's values are Java this plugin ships

`../docs/refactor/33-plugin-java.md`. A project gets two files from its template, in
`src/main/java/<bot package>/plugins/sdk/`: **`Sdk.java`** — `@Managed("flow")` returning a `Flow`,
`@Managed("capture")` a `CaptureSource`, `@Managed("settings")` a `BotSettings`, `@Managed("flow.layout")` a
`FlowLayout` — and **`Pictures.java`**, `@Managed("pictures")` on the type, one `ImageTemplate` constant per
picture. Beside them the host writes **`Points.java`** and **`Regions.java`** (`@Managed("points")`,
`@Managed("regions")`, 2026-10-06): named `Point`s in the bot's pixels and `Rect`s in the capture source's,
which the assistant's `save_point` and `save_region` add. They are the user's: the host rewrites the expression a `@Managed` method returns and nothing else,
a body that is not exactly `return <expr>;` is read-only with a reason, and Studio locks the whole file on the
canvas while this plugin is loaded (its values change in this plugin's windows). A project without them gets
them from the host on bind, from the `ManagedValue`s `SdkValues` declares.

**Nothing is stored anywhere else.** There is no `activities.json`, `capture.json`, `capture.source`, `Wire`,
`ProjectData`, parameters JSON or generated source, and **no migration, by rule**: a project written before
the flow was Java reads as having no flow, and nothing deletes anyone's old file. What one computer launches
is a run property; what a bot was tested on is its gallery entry.
