# Changelog

What each released version of `botmaker-sdk` gives you, in the terms a bot author cares about.

This file is **not** the ROADMAP. `ROADMAP.md` is the detailed engineering log — why a thing was built, what
was rejected, what it cost. This is the short answer to *"should I upgrade, and what changes for me?"*, a few
bullets per version, and it is read by two things besides you:

- **`release.sh` refuses to cut a version with no section here** (`check_changelog`, in the decide pass,
  before anything is tagged). If the top section still says `## [Unreleased]`, rename it to the version being
  cut and date it.
- **The whole file ships inside the jar** as `META-INF/botmaker/whats-new.md`, so Studio's *Project ▸ Upgrade
  SDK…* can lead with what a release **gives** you before it lists what it costs — read offline, out of the
  jar it already downloads to diff. Whole, not one section: a bot may jump several releases at once, so the
  jar must be able to answer every span ending at its own version.

Sections are `## [x.y.z] — YYYY-MM-DD`, newest first. Versions absent from this file predate it; see
`ROADMAP.md` for those.

## [Unreleased]

No source changes since v1.1.12; re-released for updated upstream pins.

### Added

- **Your flow is a value in your own Java: `Flow`, `Flow.Activity`, `Flow.Edge`, `Flow.Limits`.** The SDK
  gives your project a file — `plugins/sdk/Sdk.java` — with a `@Managed("flow")` method that returns one,
  and BotMaker rewrites that one expression when you draw. Everything you add to the file survives.

- **An activity's body is a method reference: `Flow.activity(Collect::body, "Collect", …)`.** That is the
  point of the change. `Activities.define("Collect", …)` matched a string in a JSON file against a string in
  a Java call, so renaming or deleting the method compiled fine and the flow quietly took the `DISABLED`
  wire three screens into a run. `Collect::body` is resolved by javac, so the same rename **fails the
  build**, naming the file. The new type is `ActivityBody`, an ordinary functional interface.

- **`Flows.use(Flow)`** — the whole hand-off, one static call from the `install()` in the file you were
  given, which your `main` calls. No reflection, no service loader, no file.

- **The capture source is a value too**, `@Managed("capture")` in the same file, installed through the
  `Source.set` that has always existed. *Capture Targets ▸ Apply* and the overlay's "point the bot here"
  button both write it, beside the two files they already wrote.

- **`ActivityBody.NONE`** — the body of an activity you have drawn but not written yet. Drawing the flow
  first is an ordinary way to work, so the card has to be sayable: it is a node like any other, it does
  nothing, and the run takes the wire it would take for one switched off.

### Changed

- **✂ Activity Flow reads and writes `Sdk.flow()`, not `activities.json`.** Drawing changes one expression
  in your own source — one hunk in `git diff`, one entry in the project's history — and everything you wrote
  around it survives. A `flow()` body you wrote by hand is shown empty and **left alone**: the editor says
  so on its status line and refuses to save over it.

- **Where the cards sit is not in your source.** The graph is Java and committed; the positions go to
  `src/main/resources/plugins/com.botmaker/sdk/flow-layout.json`, which both templates gitignore, so
  dragging a node never shows up in a diff. A clone without the file opens on an auto-arranged canvas.

- **A flow carries its enable flags and its presets.** `Flow.Activity.enabled()` is part of what the bot
  does, so it is in the bot's own source; a saved preset is a named set of those flags, so it travels beside
  them rather than being lost the moment the project is cloned.

- **The side panel has a "Runs" box** — the method reference this card's work is written as. It is checked
  as you type against the same rule the file is read back with, so a value the editor accepts is never one
  it then shows read-only.

- **The flow no longer validates generated field names.** An activity's enable flag and a project's
  variables used to become fields of one generated class, so their names had to be unique identifiers in one
  namespace. Neither generates a field now, so javac is what has an opinion, and the editor checks only the
  names it is still the author of.

- **A bot runs from the flow it installed, not from `activities.json`.** `FlowGraph.load` and
  `FlowGraph.run` walk `Flows.installed()`. An activity whose body the flow names is run directly — the
  method reference javac resolved — and only one that names none is still looked for by the old convention
  at `<your package>.activities.<Name>`, so bots written the older way keep running.

- **`ActivityContext`'s constructor is public.** A body is a `public static Outcome body(ActivityContext
  ctx)` in your own file, so a plain JUnit test can call it: `assertEquals("BAG_FULL",
  Collect.body(new ActivityContext("Collect")).name())`. That is the thing the method-reference design
  buys, and it needed a constructor to be reachable.

- **`Flows.enabled(name)`** — whether the installed flow has an activity switched on. An activity the flow
  does not mention reads as **on**, which is the answer `Settings.enabled` gave for a name with no entry: a
  bot may define an activity that is not on the canvas at all.

- **`Activities.define(String, …)` is deprecated**, with a `@ReplacedBy` pointing at `Flow.activity`. It
  still works and still registers a body by name; the name is exactly the link the method reference
  replaces. Move the lambda into a `public static Outcome body(ActivityContext ctx)` and name that.

- **Your picture class says it is managed, instead of Studio guessing.** Until now Studio locked every
  `static final ImageTemplate` field it found anywhere, and treated a class of nothing but those as owned
  whole by *🖼 Manage Pictures*. So a bot that kept one picture beside ordinary code was locked out of that
  code, and a second class of pictures could not be told from the first. The class carries
  `@Managed("pictures")` now and only the class that carries it is refused; a picture constant you write
  yourself, anywhere else, is yours to edit. The SDK **ships that file** (`plugins/sdk/Pictures.java`), so
  the class is one this plugin owns rather than one it hoped you had: picture renames searched for
  `Templates.ORE` before, in bots whose author had called theirs something else, and found nothing.

### Removed

- **`activities.json`, and everything that read or wrote it.** `Authoring.readModel`, `writeModel`,
  `modelJson` and `readSchemaVersion`; `ProjectModel`, `FlowModel`, `FlowNodeModel`, `PresetModel`,
  `ActivityModel` and `VariableModel`; `internal.config.ProjectData`; `internal.config.SdkGrammar` and its
  `META-INF/services` registration; `AuthoringMixins` and `ValueJson`.

  **There is no migration, and that is deliberate** — a project written before this reads as having no
  flow, which is a state the editor can show and offer to fix, where a converter would be a second reader
  of a format nothing writes. Nothing deletes anyone's `activities.json`; it is simply not read.

  `Authoring.SCHEMA_FIELD` survives, because `capture.json` carries the same stamp.

### Added

- **The SDK's eight value types read their own Java back.** A picture, a precision, a point, a rectangle, a
  size and the three enums could all be *written* into a user's `@Param` field and none of them could be
  read, so the editor listed them read-only and could only ever offer to overwrite. Each inverse now sits in
  the same expression as the literal it undoes. They recognise **only** what the SDK emits — `Point.of(3,
  4)` and `Precision.TIGHT.minArea(400)` mean the same thing and are declined, because the author wrote
  those on purpose and the window shows them as written. An enum constant this build does not have is
  declined rather than parsed: `Direction.UP` would otherwise read back as `NORTH`.

- **Picture constants are managed by 🖼 Manage Pictures.** A `static final ImageTemplate` field is shown in
  the editor with its thumbnail and cannot be edited on the canvas, which says to use that window — the one
  that renames the file, the constant and every use of it together. A class of nothing but picture constants
  (the template's `Pictures`) is read-only as a whole.

### Changed

- **A duration, a colour and a precision are edited the same way everywhere.** Each of them drew one control
  in the Parameters window and a different one on a block — four boxes written on every keystroke against a
  pill committing on OK, a hex string against a constructor call, three stored numbers against the shortest
  exact Java. A value is written as Java in both places now, so there is one control and one spelling. Two
  consequences worth naming: a colour the editor cannot write back (`Color.RED`, a variable) leaves the
  swatch alone instead of showing white, and a duration in the Parameters window commits when you press OK
  rather than as you type.

- **`VariableModel`'s type is a `ValueForm`.** The component is called `form` and the stored file still calls
  it `type`, so every project on disk reads unchanged; `ValueJson` writes the `type`/`shape`/`list` object it
  always wrote and reads it back through plugin-basics' `StoredForms`. `VariableModel.of` and
  `LiteralWriter`'s four methods take a form too. The contract deleted `ValueChoice` and `ValueShape` on
  2026-09-20 — `api.*`'s never-delete rule does not reach these, which are `authoring` and `internal`.

- **`VariableModel.fromWire` and `listShapeOf` are gone, and so is the question they answered.** A stored
  `ANY_OF` meant tick boxes over the author's choices or a free list the user fills in, and which one showed
  only in whether any choices were written down — so reading a variable's type needed a sibling field. Both
  shapes emitted `List<T>`; a form says `List<T>` and the widget question is asked of the row's options,
  where it belongs. Jackson binds the canonical constructor again.

  One consequence is stated plainly because it is user-visible for one release: the shape written into
  `activities.json` can no longer say *a set was declared*, so a Studio built before this change draws a
  free list where it drew tick boxes. The values are the same either way, and it goes when the file does.

### Fixed

- **A picture loads whatever directory the bot was started from.** `new ImageTemplate("src/main/resources/
  images/collect.png")` only resolved against the working directory, so a bot run from an IDE rooted
  anywhere but the project, or from its own jar, failed with `Failed to load image template`. The path as
  written is still tried first; then the same picture on the classpath, where Maven packages it
  (`images/collect.png`). The picture's size sidecar is found the same way.
- **A picture that cannot be found says where it looked**: every location tried and the working directory,
  instead of one absolute path that was only ever the first guess.

## [1.1.12] — 2026-09-19

### Added

- **Picture constants are managed by 🖼 Manage Pictures.** A `static final ImageTemplate` field is shown in
  the editor with its thumbnail and cannot be edited on the canvas, which says to use that window — the one
  that renames the file, the constant and every use of it together. A class of nothing but picture constants
  (the template's `Pictures`) is read-only as a whole.

### Fixed

- **A picture loads whatever directory the bot was started from.** `new ImageTemplate("src/main/resources/
  images/collect.png")` only resolved against the working directory, so a bot run from an IDE rooted
  anywhere but the project, or from its own jar, failed with `Failed to load image template`. The path as
  written is still tried first; then the same picture on the classpath, where Maven packages it
  (`images/collect.png`). The picture's size sidecar is found the same way.
- **A picture that cannot be found says where it looked**: every location tried and the working directory,
  instead of one absolute path that was only ever the first guess.

## [1.1.11] — 2026-09-19

### Changed

- **The Remote Pilot's install QR code points at `BotMakerDev/botmaker-pilot`**, where the app's releases
  moved on 2026-09-18. The old address still redirects, so a code printed before this still works.

## [1.1.10] — 2026-09-18

No source changes since v1.1.9; re-released for updated upstream pins.

No source changes since v1.1.8; re-released for updated upstream pins.

### Changed

- **The six parameter categories are gone** — Timing, Targets, Vision, Input, Limits, Debug. They were this
  plugin's declaration of how a bot's settings are filed, and a bot's settings are its own Java now: a
  `@Param`'s `category` is free text, so the Parameters rail lists the categories *you* wrote and nothing
  else. A project that used one keeps it, because the category was always just text on the row; what
  changes is that the six no longer appear in an empty project, offering a filing system for parameters
  that do not exist yet.
- **`SdkPlugin` no longer stores declared parameter rows.** The contract's
  `parameterDeclared(ParameterDeclaration)` is gone (studio-api, 2026-09-17): a user parameter is a
  `@Param` field in the bot's own Java and the host writes it there. This plugin's own rows — an activity's
  enable flag — are unaffected, and it still answers `parameterRows` and `parameterEdited`. Nothing in
  `api.*` changed, so no bot is affected.

## [1.1.9] — 2026-09-18

No source changes since v1.1.8; re-released for updated upstream pins.

### Changed

- **The six parameter categories are gone** — Timing, Targets, Vision, Input, Limits, Debug. They were this
  plugin's declaration of how a bot's settings are filed, and a bot's settings are its own Java now: a
  `@Param`'s `category` is free text, so the Parameters rail lists the categories *you* wrote and nothing
  else. A project that used one keeps it, because the category was always just text on the row; what
  changes is that the six no longer appear in an empty project, offering a filing system for parameters
  that do not exist yet.
- **`SdkPlugin` no longer stores declared parameter rows.** The contract's
  `parameterDeclared(ParameterDeclaration)` is gone (studio-api, 2026-09-17): a user parameter is a
  `@Param` field in the bot's own Java and the host writes it there. This plugin's own rows — an activity's
  enable flag — are unaffected, and it still answers `parameterRows` and `parameterEdited`. Nothing in
  `api.*` changed, so no bot is affected.

## [1.1.8] — 2026-09-17

### Changed

- **The six parameter categories are gone** — Timing, Targets, Vision, Input, Limits, Debug. They were this
  plugin's declaration of how a bot's settings are filed, and a bot's settings are its own Java now: a
  `@Param`'s `category` is free text, so the Parameters rail lists the categories *you* wrote and nothing
  else. A project that used one keeps it, because the category was always just text on the row; what
  changes is that the six no longer appear in an empty project, offering a filing system for parameters
  that do not exist yet.
- **`SdkPlugin` no longer stores declared parameter rows.** The contract's
  `parameterDeclared(ParameterDeclaration)` is gone (studio-api, 2026-09-17): a user parameter is a
  `@Param` field in the bot's own Java and the host writes it there. This plugin's own rows — an activity's
  enable flag — are unaffected, and it still answers `parameterRows` and `parameterEdited`. Nothing in
  `api.*` changed, so no bot is affected.

## [1.1.7] — 2026-09-16

### Added

- **Three buttons on the overlay editor's own row** — the translucent HUD Studio draws over the game now
  carries this plugin's items, so the window you are looking at is the subject of all three. **⌖ Point bot
  here** makes that window the project's capture target, written to `capture.json` through the same path the
  Capture Targets dialog's Apply uses. **✂ Picture of this** opens the capture tool against that window
  whatever the project's target is — including a project that names none, which used to make the tool refuse
  to open — and changes no file. **⏺ Record at cursor** gives the recorder back what it lost when it became a
  plugin: an *Insert at cursor* button that places the recorded lines where you were working, instead of
  handing you text to paste.

- **The Activity Flow editor is this plugin's** — 🔀 Activity Flow on the toolbar, where Studio's own 🔀 Flow
  button used to be. The canvas, the cards, the outcome ports, the wiring rules, the auto-arrange, the undo
  arrows and the loop-safety fields all came across unchanged; what changed is who owns them. It reads and
  writes `activities.json` itself, so the editor and the running bot cannot disagree about what a flow is,
  and it is the only one of the three windows over this plugin's project data that moved: a parameter is a
  `ParameterRow` the host can draw, while a flow's nodes, edges and outcomes are this plugin's own
  vocabulary.

  Two consequences worth knowing. **Studio's Project ▸ Activity Flow menu entry and its New Activity button
  in the file explorer are gone** — the toolbar item is the one way in, and adding an activity is a button on
  the canvas, which is the only place that can also say where it sits and what it reports. And the window no
  longer remembers its size between openings, which is host state a plugin has no access to.

- **The Parameters window's rows are this plugin's, not the host's.** `SdkPlugin` implements the contract's
  parameter-data surface — `parameterRows(groupId)` and `parameterEdited(edit)`. Studio parsed the project
  file itself, which meant the host knew one plugin's storage format and no second plugin could have had
  parameters at all. A plugin with no project bound answers nothing, and a group id this plugin does not own
  answers nothing — a project's parameters can now be several plugins' at once. The value a host hands back
  is **canonicalised, clamped to any declared range and pruned to the options still on offer** before it is
  stored, and the row that comes back says what was actually stored — so a window renders the value the bot
  will get rather than the text somebody typed.

  **The declaration half landed the same day**: `parameterDeclared` takes the row the host wants and answers
  the row this plugin stored — an add, a rename, a retype, new choices, a range, a category, a note, a
  visibility change or a removal, all as one call. What each costs is `ParameterStore`'s rule, so the window
  that asks needs to know nothing about what a value type is.

  **Where they are stored changed the same day.** All three methods go through
  `botmaker-plugin-basics`' `ParameterStore`, over this plugin's own folder in the project —
  `src/main/resources/plugins/com.botmaker/sdk/parameters.json`. Storing parameters was plugin #1's
  privilege only because plugin #1 owned `activities.json`; it is an ordinary plugin's file now, and any
  plugin declares parameters the same way. **A project created before this reads as having none**: nothing
  converts the old `variables` array, and nothing deletes it either, so the data is on disk and a converter
  can be written later.

- **`Settings` replaces `Wire`, and two methods replace eighteen.** The type is an argument now:

  ```java
  Duration   rest  = Settings.load("restBetween", Duration.class);
  int        health = Settings.load("minHealth", int.class);
  List<Rect> zones = Settings.loadAll("zones", Rect.class);
  boolean    on    = Settings.enabled("Mining");
  ```

  `Wire.whole`, `Wire.duration`, `Wire.area` and the fifteen others could only ever read *this* plugin's
  value types — `Wire.one(String)`'s own javadoc called itself "the escape hatch for a type this class has no
  reader for". Passing the type in makes the set open: whichever plugin introduced a type ships a
  `com.botmaker.plugin.basics.store.ValueGrammar` that reads it, and every call site is the same shape. Both
  `int.class` and `Integer.class` resolve, so you write whichever your field is.

  Everything else is unchanged. A missing file, a missing name, a name declared as another type and text that
  will not parse all answer that type's own fallback, so **a bot still never fails to start because of its own
  configuration file**. One thing throws, and it is not about the file: a type no grammar on the classpath
  claims, which is a bot compiled against a plugin it does not run with.

  The parsers are the same parsers. `internal.config.SdkGrammar` wraps the `WireText` calls the editor's own
  codecs use, so the Parameters window and the running bot cannot disagree about what `"3s500ms"` means.

- **`Images.named("ore")` is `images/ore.png`** — `Wire.image` moved to `com.botmaker.sdk.api.vision`. It
  reads a *file*, where everything else on that class read a *variable*, and it was most of why `Wire` looked
  like it did too much. A picture is vision's business.

### Removed

- **`com.botmaker.sdk.api.config.Settings` and `com.botmaker.sdk.api.config.Wire` are deleted.** A bot reads
  its own parameters from **`com.botmaker.plugin.basics.store.Settings`**, which is where the work always
  happened — the SDK classes were a facade over it holding no logic, so every answer is the one you already
  get. The change you make is the import:

  ```java
  import com.botmaker.plugin.basics.store.Settings;      // was com.botmaker.sdk.api.config.Settings

  Settings.load("minHealth", int.class);                 // unchanged
  Settings.loadAll("zones", Rect.class);                 // unchanged
  Settings.enabled("Mining");                            // unchanged
  ```

  `Settings.one`, `Settings.many` and `Settings.names` were the facade's own and are
  `ProjectValues.current().one(…)` / `.many(…)` / `.variables()` in the same package. `Wire`'s eighteen typed
  readers were already replaced by `Settings.load(name, T.class)` and `Wire.image` by `Images.named(file)`;
  those replacements are unchanged and are what the calls become.

  **This breaks a bot that imports either class, and there is no migrator for a deleted type** — only a
  compile error naming it. It is a deliberate decision, taken while `api.*` was still allowed one last
  pre-policy removal: never-delete's baseline is `v1.2.0`, which is the release this deletion is in, so the
  rule begins from a surface that no longer offers them. Nothing is removed from `api.*` after it.

  **No menu offers a settings read until `botmaker-plugin-basics` catalogues its own `Settings`** — the SDK
  may not catalogue another plugin's API, which is the same rule that moved the nine JDK value types there.
  Write the call yourself in the meantime; it compiles and runs exactly as before.

### Changed

- **A bot reads its parameters through `botmaker-plugin-basics`**, and that is the package to import if you
  call `Settings` directly rather than through `com.botmaker.sdk.api.config.Settings`:
  `com.botmaker.plugin.basics.store.Settings`. It was `com.botmaker.plugin.toolkit.config.Settings` for part
  of one day and never shipped under that name. `SdkGrammar` now reads the SDK's own eight types and
  plugin-basics' `BasicsGrammar` reads the nine JDK ones — two grammars, indexed together off the classpath,
  which is what the mechanism was built for and had never been exercised while there was one plugin.

- **The nine JDK value types are `botmaker-plugin-basics`' now** — `TEXT`, `YES_NO`, `WHOLE_NUMBER`,
  `DECIMAL_NUMBER`, `CHARACTER`, `COLOR`, `DATE`, `TIME_OF_DAY`, `DURATION`. Nothing about a whole number or
  a time of day is about automating a game; they were registered here only because this SDK was written
  first, which made having a duration variable plugin #1's privilege. **Nothing changes for a bot or for a
  stored project**: the ids are the same, `WireText`'s nine readers and their two spellers are still here and
  still public — they delegate to `com.botmaker.plugin.basics.values.JdkText`, so there is one grammar rather
  than two — and `Settings.load("rest", Duration.class)` reads exactly what it read before. What changes is
  who registers them: this SDK now declares `botmaker-plugin-basics` as an ordinary `compile`-scope
  dependency, one plugin depending on another, and contributes the eight types that really are its own
  (`ImageTemplate`, `Precision`, `Point`, `Rect`, `Size`, `Direction`, `Key`, `MouseButton`). A bot's pom
  must **not** declare `botmaker-plugin-basics` itself: Maven's nearest-wins mediation would make that entry
  outrank the one this jar brings, and the bot would run a version this SDK was never built against.

- **`ProjectData` keeps the flow and delegates the rest.** The untyped store — a variable's stored text, an
  activity's flags — is `com.botmaker.plugin.basics.store.ProjectValues` now, so every plugin can read a bot's
  parameters and not only this one. Nothing a bot writes changes and no method was removed: `value`,
  `values`, `declares`, `variables`, `enabled`, `outcomes`, `activities`, `goHome`, `popupCheck` and
  `isEmpty` all still answer here, through the store. `ProjectData.use(…)` sets the shared seam, so a test
  stubbing the model here also stubs what `Settings` reads.

  What stayed is the half that knows what the file *means*: `start`, `placed`, `maxSteps`, `stepDelayMs`
  and `routes` are `FlowModel`'s and `FlowEdgeModel`'s rules, and reading them through a second set anywhere
  else is how the editor and a running bot would come to disagree about which activity runs first.

- **The seven authoring records are `com.botmaker.sdk.authoring` again** — `ProjectModel`, `ActivityModel`,
  `VariableModel`, `FlowModel`, `FlowNodeModel`, `FlowEdgeModel`, `PresetModel`. They spent a week in the
  plugin contract (2026-08-31 to 2026-09-07). Nothing a bot writes changes; they sit beside `SdkVersion`,
  `Authoring` and `WireText`, which is where they were before and where the file they describe is read.
  `AuthoringMixins` keeps the Jackson marks out of the records regardless of which module they live in.
- `AuthoringModelTest.aStoredAnyOfWithNoSetBehindItReadsAsAnOpenList` is restored. It calls
  `VariableModel.listShapeOf`, which is package-private; the move away made it unreachable and the move back
  makes it reachable. The rule it asserts never stopped running.

- **An `ImageTemplate` no longer loads the OpenCV native when it is constructed**, only when a matcher first
  asks for its pixels. Holding a template is now something a bot does without meaning to — the value grammar
  builds one as the fallback for an unreadable image variable — and a class-initialiser load would have made
  every bot that reads *any* setting extract and link the library. Nothing about matching changes.

- Nothing a bot can see. `WireText` grew the writers for the five types whose spellings the editor had kept
  privately (`spellColor`, `spellPrecision`, `spellPoint`, `spellSize`, `spellArea`), so that reading and
  writing one stored value is one grammar rather than two — the same reason `spellDuration` is there.
  `SdkPlugin` follows `Region` to its new home in the toolkit, and `WireText`'s class
  javadoc stops describing a generator that no longer exists — it claimed a value's text was parsed "at
  generation time" and written into source as `Duration.ofMillis(5400000L)`, which stopped being true when
  the inversion was reversed between 2026-08-29 and 2026-09-02. A bot reads its own text at run time.

## [1.1.6] — 2026-09-05

### Fixed

- **The palette, the slot editors and the toolbar buttons appear when you install the SDK through
  *Manage Plugins*.** Constructing the plugin needed JavaFX, and JavaFX is `optional` in this jar — which
  means *not transitive*, so it is on the SDK's own classpath and absent from yours. A project that added
  `botmaker-sdk` as a dependency therefore got:

  ```
  ServiceConfigurationError: Provider com.botmaker.sdk.plugin.SdkPlugin could not be instantiated
    Caused by: NoClassDefFoundError: javafx/scene/Node
  ```

  which the host catches — a classpath with no loadable plugin on it is an ordinary state — leaving an empty
  palette, no name recognition, no slot editors and one line on stderr. Nothing crashed and nothing failed to
  compile. The cause was one line in the constructor registering the screen picker with the toolkit; it now
  happens where the editors are built, which only a host that has JavaFX ever asks for.

  **Found by the plugin registry's own gate**, on this plugin's first real submission, because the gate
  resolves the *published* artifact exactly as a host does. `botmaker validate --coordinate
  com.github.LiQiyeDev:botmaker-sdk:v1.1.5` reproduces it; validating a working copy never could, since an
  `optional` dependency is present on the module's own classpath.

  The rule, now held by `SdkPluginHeadlessTest`: **constructing a plugin must not link an optional
  dependency.** A headless host — `botmaker validate`, `botmaker run`, the registry's CI — is a legitimate
  host, and it is the one that decides whether a plugin may be published at all.

## [1.1.5] — 2026-09-05

### Fixed

- **`1.1.4` still does not resolve — third time, third plugin, and this one is caught by a gate rather than
  by a tag.** `botmaker-session` and `botmaker-studio-api` did build this time. `botmaker-plugin-toolkit`
  did not: it pinned `flatten-maven-plugin` 1.6.0, which declares a Maven 3.6.3 prerequisite that JitPack's
  **Apache Maven 3.6.1** cannot satisfy, so it published nothing and this jar's pom named a toolkit that
  does not exist. The toolkit and `botmaker-plugin-host` now pin 1.4.1, exactly as this module always has.

  The SDK itself is unchanged from `1.1.3`. What changed outside it is that the umbrella's `release.sh`
  gained `check_jitpack_plugins`, which reads each pinned plugin's own `<prerequisites><maven>` and refuses
  the release while nothing has been pushed — three chains have now been burned on this one failure, each
  time on a different plugin, and each time invisible locally because a developer's Maven is new enough.

## [1.1.4] — 2026-09-04

### Fixed

- **`1.1.3` still does not resolve, and this is the release that fixes it.** The upstream repair it
  announced was pinning `maven-compiler-plugin` in the modules that lacked one — and the pin named 3.13.0,
  which the plugin's own Maven prerequisite (raised to 3.6.3 in 3.12.0) puts out of reach of JitPack's
  Maven. So `botmaker-session` and `botmaker-studio-api` failed to build again, one line later, and this
  jar's pom went on naming versions that do not exist. Every module now pins **3.11.0**, the version
  `botmaker-shared` has always used. Nothing in the SDK itself changed between `1.1.3` and this release.

## [1.1.3] — 2026-09-04

### Fixed

- **Installing the SDK through *Project ▸ Manage Plugins* now gives you a working palette.** It did not.
  The SDK is a library *and* Studio's plugin #1, and its plugin half extends `AbstractStudioPlugin` from
  `botmaker-plugin-toolkit` — which this pom declared `<optional>true</optional>`. `optional` means **not
  transitive**, so a project that added `botmaker-sdk` as a dependency got the plugin classes without the
  classes they extend. Studio loads plugins off your project's own resolved classpath, so `ServiceLoader`
  threw `NoClassDefFoundError` while constructing the plugin, Studio caught it — correctly; a project with
  no plugin on its classpath is an ordinary state — and you got an empty palette, no name recognition, no
  slot editors and one line on stderr. The toolkit is an ordinary dependency now, so it travels with the
  SDK wherever the SDK is resolved.

  It costs a headless bot 106 KB it never links. The toolkit has no dependencies of its own and its JavaFX
  is `provided`, so **nothing follows it onto a bot's classpath** — no JavaFX, no window toolkit, nothing
  new to download for the machine a bot actually runs on. A project created by Studio is unchanged either
  way: its pom already declared the toolkit itself.

- **Resolvable from JitPack again.** `v1.1.2` published a pom naming `botmaker-session` and
  `botmaker-studio-api` versions that had never been built there: neither of those modules pinned
  `maven-compiler-plugin`, and JitPack's Maven defaults it to 3.1, which predates
  `maven.compiler.release` and builds with `source 5`. Every module in the chain pins it now.

## [1.1.2] — 2026-09-02

- **Your bot now builds and runs on Java 25 (LTS), with JavaFX 25.0.4 behind the editors.** This is the one
  change here you have to act on: a project pinned to this SDK needs a JDK 25 or newer, and a bot run on an
  older JVM fails at class load with `UnsupportedClassVersionError` rather than at compile time. Nothing in
  `com.botmaker.sdk.api` moved — never-delete still holds — so once your JDK is new enough there is nothing
  to change in your code.

## [1.1.1] — 2026-09-02

- **Branching on what was found is a chain of calls now — `found.when(…).when(…).otherwise(…)`.** Deciding
  what to do about a *combination* of templates is what `Matches` exists for, and until now the only ways to
  write it were an `if`/`else if` chain or a Java 21 guarded switch the editor built for you:
  ```java
  ImageFinder.whileFindAny(POPUPS, found -> {
      found.when(m -> m.hasAny(MAIL, GIFT),             () -> ImageClicker.click(CLAIM))
           .when(m -> m.hasAll(CHEST) && !m.hasAny(AD), () -> ImageClicker.click(CHEST))
           .otherwise(                                   () -> Debug.log("nothing to do"));
  });
  ```
  It reads the way the switch did — **at most one branch runs**, and a later test is not even evaluated once
  one has matched — and every predicate sees the same frame, so two branches asking about the same instant
  cannot disagree. `otherwise` is optional; a chain that ends without one does nothing when nothing matched.
  Both older forms keep working and compiling; nothing you have written changes.

  The reason for the new shape is worth one line, because it is why the editor gets better: a `switch` is a
  language construct, so everything about it — the type name, the pattern variable, the guard, the mandatory
  `default` — had to be spelled by whoever wrote the editor. These are ordinary methods, so the editor offers
  them, draws them and edits their predicates with the machinery it already has for every other call.

- **You write an activity as a call now — `Activities.define`.** An activity is created on the Activity Flow
  canvas; what it *does* is this, written wherever you like:
  ```java
  Activities.define("Mining", ctx -> {
      if (bagFull()) return ctx.outcome("BAG_FULL");
      mineOnce();
      return ctx.done();
  });
  ```
  Both names are dropdowns in the editor, filled from your own canvas — the activities it holds, and the
  outcomes it declares — and both stay typeable, so you can write a body before you have drawn the activity. **An activity with no `define` call is
  not an error** — it behaves exactly like one you switched off and follows its `DISABLED` wire, so you can
  draw a whole flow before writing any of it. Two things to know: a misspelled outcome is not a compile error
  (it is reported, and behaves like an outcome you never wired), and renaming an activity on the canvas does
  not rename the string in your code.

- **Remote Pilot ships with the SDK now.** The 🎮 Pilot button, the phone pairing, the private-display
  controls and everything behind them come from the SDK rather than from Studio. For you that changes one
  thing: a project that does not depend on the BotMaker SDK has no Pilot button. Everything else — the
  pairing URL, the QR codes, Interact, background mode — works exactly as before, with one cost the first
  time: the pairing token moved with the feature, so an already-paired phone has to scan the QR code once
  more.

- **The picture library ships with the SDK now, as 🖼 Manage Pictures.** Renaming, retagging, replacing,
  deleting, importing and exporting the pictures your bot looks for were an editor window; they are the SDK's,
  beside ✂ Capture Templates on the toolbar. Nothing about what it does has changed, **including the part that
  edits your code**: renaming a picture still finds every block that names it and carries them along, and
  deleting one that is still in use still lists the uses and offers to point those blocks at another picture
  first — with the enclosing functions marked for review, because that one changes what your bot watches for.
  A rename is not marked, because it is not a change: it is the same picture under a new name.

  What moved underneath is worth one line, since it is why any plugin can now do this: rewriting your Java is
  the editor's job and it stays the editor's job. What the SDK supplies is only the knowledge that `ore.png`
  is written `Templates.ORE` — the editor no longer knows that, and never needed to.

- **Your capture targets are one list now, in `capture.json`.** The screens, windows and emulators you set up
  as targets were kept in the editor's own settings, while the one your bot actually looks at was kept
  separately in `botmaker-project.properties` — so the two could drift apart with nothing to tell you. The
  list is a project file the SDK owns, like `activities.json`, and the default target is what your bot
  resolves. Existing projects are migrated the first time they are saved; nothing you have set up is lost.

- **Bots with `Activity` subclasses keep working, unchanged.** Both kinds of activity share one registry, so
  `Activity.disable("Mining")` still finds either.

- **BotMaker writes none of your bot's Java any more.** Not the entry point, not `GoHome`, not `Popups`, not
  an activity's file. A project's structure is yours: the SDK owns the data files your bot reads back at run
  time (`activities.json`, `botmaker-project.properties`, the placeholder image) and nothing else. Existing
  projects are untouched — the files you already have keep compiling and are yours to edit, rename or delete.

- **Your bot can read its own settings by name — `Wire`.** `Wire.whole("minHealth")`,
  `Wire.duration("restBetween")`, `Wire.template("healthBar")`, `Wire.enabled("Mining")`: the values you set
  in the editor, read at run time by the name you gave them, one reader per type. **Nothing has been taken
  away** — the generated `Parameters` and `Activities` classes still work exactly as before, and this is the
  first half of letting a later release stop generating them. Two things worth knowing before you use it: a
  misspelled name is not a compile error (`Wire.whole("minHelath")` compiles and answers `0`), and **nothing
  here can throw** — a missing file, a missing name and a value that will not parse all fall back, because a
  bot must never fail to start because of its own configuration file.

- **BotMaker no longer writes five of your bot's nine files.** `Activities`, `Parameters`, `Templates`,
  `ActivityRegistry` and `FlowDriver` were rewritten every time you ticked a box, changed a value, captured a
  picture or moved a wire. None of them is generated any more, because all five said only what your project's
  own `activities.json` already said. **A new project gets four files: your entry point, `GoHome`, `Popups`,
  and one class per activity — every one of them yours to edit, none of them ever overwritten.**

- **`FlowGraph.run(YourBot.class, GoHome.INSTANCE::execute)` walks the flow you drew.** It replaces the
  generated `FlowDriver` and `ActivityRegistry` together: the start node, the wires, the step budget and the
  pause between activities all come from `activities.json`, and your activities are found beside your entry
  point at `<your package>.activities.<Name>`. Redrawing the canvas now changes no Java at all.
  `FlowGraph.of` / `.node` / `.route` are **deprecated but still work** — a bot with a hand-built or an
  already-generated table keeps running untouched.

- **`Wire.image("ore")` names a picture** — the replacement for `Templates.ORE`. Adding a picture to your
  project is no longer a source edit.

- **What this costs you, stated plainly.** `Parameters.minHealth` was an `int` field and misspelling it was a
  compile error; `Wire.whole("minHealth")` answers `0`. The one place you write a name by hand keeps its
  compiler — `return Outcome.BAG_FULL;` is still checked against an enum the editor maintains for you.

- **An existing bot is not touched.** If your project already has those five files, they stay exactly where
  they are and go on working. They are ordinary source you own now: nothing rewrites them, and nothing
  deletes them. Move to `Wire` and `FlowGraph.run` when you feel like it, or never.

- **Internal: the plugin-side code is smaller, and the generic half of it is the toolkit's.** `Slots`, the
  call-site matching, the bounded-number pill and the editor test stubs moved to
  `botmaker-plugin-toolkit`; `SdkPlugin` extends its new `AbstractStudioPlugin`, which also makes the
  52-facade palette reflection lazy rather than running it when `ServiceLoader` constructs the plugin.
  **Nothing under `com.botmaker.sdk.api` changed** and no behaviour did; a bot resolves nothing new.

- **The SDK now ships the editors for its own types.** The controls that stand in for a typed-out expression
  — drag a region on screen rather than write `new Rect(12, 40, 300, 80)` — used to live in Studio, which
  meant Studio had to know what an SDK type looked like. They are ordinary plugin contributions now
  (`com.botmaker.sdk.internal.plugin.editors`), reached exactly as any other plugin's would be. **Nothing
  changes for a bot**: the editors are `<optional>` dependencies alongside JavaFX and the widget toolkit, so
  they are in the jar and never linked on a bot's classpath — a bot is a headless program and must not resolve
  JavaFX. **Eight have moved** in this release: `Rect`, `Point` and `Size`; the Steam and Epic launch ids,
  the program path and the launch options of a `Game.launch…` call; the bounded `BotSettings` setters; and the
  wait length. The rest follow in the next.
- **The wait editor is now the same control in both places you meet one.** The Parameters window and a block
  in your bot's source used to have separate duration editors that had to be kept saying the same thing;
  there is one now, and it draws as four boxes in the window and as a pill that opens them on a block. What it
  writes is unchanged — the shortest form that says what you chose (`Duration.ofSeconds(2)`, not
  `ofMillis(2000)`), and an untouched value comes back exactly as it was written, so opening the editor and
  pressing OK never rewrites your source.
- **The launch and settings editors only appear where they mean something.** A Steam app id, a program path
  and a launch flag are all `String`, so these are chosen by the *call* they sit in rather than by their type
  — which is why they are offered on a `Game.launchSteam(…)` argument and not on every text field in the
  Parameters window.
- **What you read on a collapsed picker is unchanged, including the awkward cases** — a half-written
  `new Point(10)` still reads `10, 0`, a slot holding `bounds` still shows `bounds` rather than claiming
  `0, 0`, and a `long` literal keeps its value. Those were pinned by a Studio test and are pinned by an SDK
  one now.
- **OCR tuning is part of the SDK's API now.** `OcrOptions`, `OcrLanguage` and `TextResult` moved out of
  `botmaker-shared` into `com.botmaker.sdk.api.vision`, so the options you pass to `Text.read`, `Text.find`
  and friends are versioned like everything else you write down — and covered by the same
  `@ReplacedBy`/`@Replaces` machinery if they ever change spelling. **Update your imports**: any
  `import com.botmaker.shared.ocr.OcrOptions;` (or `OcrLanguage`/`TextResult`) becomes
  `com.botmaker.sdk.api.vision.…`. Studio repoints them for you on open; a bot built by hand needs the edit.
  Nothing else about OCR changed — same engine, same tuning knobs, same bundled languages.
- **`@Since`, `@ReplacedBy` and `@Replaces` moved to the plugin contract**, from
  `com.botmaker.sdk.api.meta` to `com.botmaker.plugin.api.meta`. They describe how *any* library keeps faith
  with the code that calls it, not something particular to this SDK, and the same annotation processor now
  checks them for any plugin. **If your bot writes `@Since` down** — most do not — change the import to
  `com.botmaker.plugin.api.meta.Since`; the old spellings still work for this whole minor, marked deprecated
  and pointing at the new ones, so Studio's *Modernise…* will do it for you. Nothing about what they mean
  changed, and this is the first rename the pointer pair has carried for itself.
- `TextResult.bounds()` is now an `api.geometry.Rect` instead of a `java.awt.Rectangle`, so it matches every
  other box the SDK hands you: `.x()`, `.y()`, `.width()`, `.height()` rather than public fields.
- **The block palette is the SDK's answer now.** The SDK ships a catalog of what it offers Studio's menus —
  which types, in which order, under which icon, and which of their members. A bot pinned to an older SDK is
  offered that catalog narrowed to what *its own* jar actually contains, so a newer Studio never proposes a
  method your pinned SDK does not have. Nothing changes in your bot's code, and nothing new is on its
  classpath: the catalog is served to the editor, never called by a bot.
- Practically, this restores the curation that `@Palette` used to carry and that was lost when the annotation
  was deleted — the menus have been offering every public method of every facade since then, and go back to
  offering the ones worth offering once Studio reads the catalog.

- **Your parameters are real values now, not text read at startup.** `Parameters.REST` used to be
  `Wire.duration(Wire.one("REST"))` — the bot opened `activities.json` when it launched and parsed `"1h30m"`
  out of it. It now says `java.time.Duration.ofMillis(5400000L)`, written when the file was generated. Your
  bot starts faster, cannot fail to start because a value in that file is malformed, and reads like something
  a person wrote. **`Wire` and the runtime config store are deleted**; `activities.json` is still your
  project's data, but nothing reads it while the bot runs.
- **One thing to know if you edit generated files by hand:** changing a value inside `Parameters.java` now
  lasts exactly until the next save. It always said "do not edit"; the difference is that a *value* is one of
  the things it means. Change it in Project ▸ Parameters, which also keeps the file beside it in step.
- **Your activity switches are no longer `final`.** `public static boolean Mining` rather than
  `public static final boolean Mining`. This is what stops `while (Activities.Mining) { … }` from becoming an
  "unreachable statement" compile error in *your* code when you untick that activity — a folded constant
  `false` makes the loop body dead. As a side effect you may now assign to one at run time; nothing objects.
- **Your project's values move to their own file.** `Activities` used to hold two unrelated things under one
  name — an activity's on/off switch and every value you configured — so `Activities.restDelay` sat beside
  `Activities.Mining` with nothing to tell them apart. The switches stay in `Activities`; the values are now
  `Parameters`. **You do not have to do anything:** Studio splits the two files and repoints every
  `Activities.<value>` in your own code the first time it opens the project, taking a Project History snapshot
  first. Nothing is marked for review, because nothing changed except the name in front of the dot.
- **The scaffold's two-author negotiation is gone.** The per-hole generation numbers, the surface ledgers and
  the pre-write refusal that existed to keep Studio and the SDK in step have been removed. They were the price
  of a file the two repositories co-authored; the SDK is becoming the generator, so there is nothing left to
  negotiate. Nothing in your bot changes.
- **The scaffold templates no longer ship in the jar.** `botmaker-templates/` and its `manifest.txt` are gone,
  along with the `@Template` annotation. They were the text Studio filled in to write your `Activities.java`,
  `Parameters.java`, `FlowDriver.java` and `ActivityRegistry.java`; that job moves into this SDK, where the
  files can be checked against the API they call in the same build. **Nothing in an existing bot changes** —
  the generated files already in your project are ordinary Java and keep compiling, running and being edited
  by hand. What is temporarily unavailable is Studio *rewriting* them: until the SDK ships its own generator,
  **New Project and Save Activity Flow are refused**, by name, with the reason.
- **`@Palette` and `@Scaffolding` are removed from `api.meta`.** Neither was ever something a bot wrote down —
  they told Studio which members to offer in its menus and which it wrote into generated files. Until the SDK
  serves the palette itself, Studio's menus simply offer everything public. The four pointer annotations
  (`@ReplacedBy`, `@Replaces`, `@Since`) are untouched.
- **The SDK now owns `activities.json`.** New package `com.botmaker.sdk.api.authoring`: your project's
  activities, variables, flow and presets are read and written here, against one schema with one owner,
  instead of by whichever editor happened to open the file. **Nothing in your bot changes and nothing in your
  project file changes** — the format is the same one you already have, including the two spellings it has
  carried over its life, both of which still load. What this buys you arrives next: the generator that writes
  your project's Java lives beside the API that Java calls, and is checked against it in the same build.
  Every entry point takes the SDK version your bot pins as its first argument, so an editor bundling a newer
  SDK than yours generates for *your* version or says plainly that it cannot.

## [1.1.0] — 2026-08-24

The 1.1.0 contract release. **This is the last window in which `api.*` moves freely** — from 1.1.0 the SDK is
under real semver and nothing is removed without a deprecation release naming its replacement
(`docs/refactor/21-api-compat.md`). Existing bots take plain compile errors for the moves below; Studio
repairs the imports on open.

- **`api` is reorganised and the root is empty.** `Point`/`Rect`/`Size`/`Direction` → `api.geometry`, the
  meta annotations → `api.meta`, `Session`/`BotSettings` → `api.bot`, `Time`/`BotMaker`/`Debug` → `api.util`.
  The rule it encodes: *`api` is what a bot can write down.* Eleven types a bot could only ever **receive**
  (`Desktop`, `Monitor`, `NamedWindow`, `SessionSource`, the six `api.observe` types) moved to `internal`,
  and `Screen` — which had no callers and was not even a `CaptureSource` — is gone.
- **`Point`, `Rect` and `Size` are records of `int`s.** They were mutable clones of `org.opencv.core.*` with
  public `double` fields and no `equals`, so `p1.equals(p2)` in a bot was an identity comparison.
- **`VisionContext` is `Vision`, and the accessors dropped `get`.** `MatchResult`, `ColorMatch`, `TextMatch`,
  `ImageTemplate`, `Rect` and `Vision` read as `m.confidence()`, not `m.getConfidence()`; mutators keep `set`.
- **A move can now be written down, at both ends.** `@ReplacedBy` on the deprecated element and `@Replaces`
  on the survivor, plus `note()` (the author's own sentence, shown to the user verbatim),
  `behaviourChanged()` (the flag for a redirect that keeps its shape but changes what it does) and `@Since`.
  `@ReplacedBy.value()` is a `String[]` with a parallel `whens()`, so **a member that became two** is
  expressible and Studio can ask, per call site, which one that call meant.
- **`@Palette` curates the menus without shrinking the API.** A public method with no `@Palette` stays public,
  supported and callable — it is simply not proposed in Studio's palette. 18 facades and 10 value types are
  curated; a jar with no `Palette` class at all is treated as uncurated and offers everything, exactly as
  before.
- **The files BotMaker generates for you now come out of this jar.** The entry point, `GoHome`, `Popups`,
  `ActivityRegistry`, `Activities`, `FlowDriver` and the activity stub ship inside the SDK as templates;
  Studio fills in what is true about *your* project and nothing else. What you get from that: the frame of
  every generated file is compiled and tested by the SDK's own build, and a Studio older than your SDK still
  writes files that compile, because anything it does not recognise stays at the SDK's own default.
- **Your Activity Flow is a table, not a generated `switch`.** `api.flow.FlowGraph` (with `PopupCheck` and
  `Recovery`) holds the graph, and the walk itself — the loop, the step budget and its give-up message, the
  watchdog tick, the delay between steps — is the SDK's, so it is the same in every bot and it is tested:
  branch, join, loop back, an outcome left unwired, a disabled activity falling through, an empty flow.
  `FlowDriver` keeps `MAX_STEPS` and `STEP_DELAY_MS` as your two knobs.
- **Your stored parameters are read by the SDK.** `api.config.Wire` is one reader per storable type and the
  loader behind it is compiled code, replacing ~150 lines of parser bodies that used to be generated into
  every `Activities` class as text. Every reader is total — an unreadable value falls back to a default, so a
  bot never fails to start because of its own configuration file. **The `1h30m` grammar now exists once**: it
  used to be written twice, once in the editor and once as generated text, with nothing able to compare them.
- **`@Scaffolding`** marks the members those templates use, so a release that moves one says so before you
  commit to the upgrade instead of failing half-way through it.
- **BotMaker Studio requires this version or newer** from its next release, for the generated files only: an
  older bot still opens, builds and runs, but its Activity Flow cannot be saved until *Project ▸ Upgrade SDK…*
  moves it here. The upgrade re-renders `FlowDriver` and `Activities` in the new shape and leaves everything
  you wrote yourself untouched.
- **The deprecation promise is now enforced, not just written down.** From this release the build refuses to
  delete anything from `api.*` that the previous release did not already mark `@Deprecated` — so a member you
  call cannot vanish between two versions without one release in which your compiler warned you about it
  first. (For maintainers: a committed `api-surface.txt` and `ApiSurfaceTest`.)
- The method audit (`docs/refactor/22-api-audit.md`) removed the type leaks and duplicated fields it found.

## [1.0.26] — 2026-08-22

- Build fix only: `flatten-maven-plugin` pinned to 1.4.1. 1.6.0 needs a Maven newer than JitPack's, so
  v1.0.25's own build never produced an artifact.

## [1.0.25] — 2026-08-22

- **A generated bot resolves on a clean machine again.** Every published SDK pom up to v1.0.24 declared
  `botmaker-shared:0.0.0-SNAPSHOT` — invisible on a dev box (whose `~/.m2` always has one) and fatal
  everywhere else. The pom is now flattened at publish time with the real shared/session tags baked in, and
  the JitPack build *requires* those pins rather than defaulting them.

## [1.0.24] — 2026-08-22

- Re-tagged so JitPack rebuilt the SDK against a new shared/session. No source change.

## [1.0.23] — 2026-08-21

- Re-tagged so JitPack rebuilt the SDK against a new shared/session. No source change.

## [1.0.22] — 2026-08-21

- **Three click verbs, and a template that names itself** — plus clicking what a group check already found,
  instead of searching for it a second time.
- **The mouse gained the two buttons under your thumb** (back/forward).
- **The observer API sees gestures, not just clicks**, and a debug run reads as a narrative rather than a log.
- An emulator ref's liveness stopped being "is the socket open"; the ambient capture source skips a session
  whose pixels are not on X11; an empty `ImageTemplateGroup` is legal and matches nothing.
- A bot logs the size of its display and where that size came from.

## [1.0.21] — 2026-08-04

- **`Time`** facade; the project's default capture source is honoured; launch-wait wiring.

## [1.0.20] — 2026-08-02

- **`Precision`**: `Tolerance` and `MinMatch` collapsed into one type, so a call carries one knob instead of
  two that could disagree.
- **`BotSettings`** replaced `ClickConfig` and is seeded from the project's own settings, so tuning lives in
  the project rather than in generated source.
- `Bot.start` supplies the launch step; `Mouse` stops discarding clicks made inside a private session; the
  session stack is resolved from `botmaker-session`.

## [1.0.19] — 2026-07-19

- **`Text`**, the OCR facade, with `findFuzzy` for edit-distance matching.
- **The emulator facade**, `Target`/`LaunchTarget`, and Epic Games launching.
- `Bot.start` became the sole public entry point; debug output moved behind one `Debug` switch.

## Earlier

v1.0.18 and below predate this file. `ROADMAP.md` has the dated log.
