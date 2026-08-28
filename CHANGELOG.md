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
