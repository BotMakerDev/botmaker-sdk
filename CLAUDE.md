# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The **BotMaker SDK** is the runtime library that user bots compile against. The sibling
**botmaker-studio** app (`../botmaker-studio`) generates user projects that depend on this SDK and
call its public `com.botmaker.sdk.api.*` facades. The SDK itself depends on **botmaker-shared**
(`../botmaker-shared`, cross-platform native window plumbing).

## Planning

For large changes, write the plan to a dedicated plan file before starting implementation, so work
can be resumed if a session is interrupted.

**Always update `ROADMAP.md` whenever you add a feature or refactor code** — append a dated entry
under "Done" (and add/adjust "Deferred / next" items as needed). It is the running history future
sessions rely on to understand what changed and what's intentionally left for later.

## Commands

```bash
mvn compile        # Build
mvn test           # Run tests (JUnit Jupiter)
mvn install        # Install to the local Maven repo (umbrella coordinate); for a generated bot to pick
                   #   up local changes use ./dev-install.sh instead (see Publishing › Local dev)
```

There are no `main`-method entry points. The module is a library: everything under `src/main` is
reachable from a generated bot, and everything that verifies it is JUnit under `src/test`. The five
manual harnesses that used to sit in `internal/` (and a hello-world `Main` that shipped in the
published jar) were deleted in 2026-07 — they were unreferenced, untested, and on every bot's
classpath. A new diagnostic goes in `src/test` with the JUnit the rest of the module uses.

## Publishing

The SDK is consumed by the **bot projects that Studio generates** (not by Studio itself), via JitPack as
`com.github.LiQiyeDev:botmaker-sdk:<tag>`. JitPack builds each git tag on demand and serves it under that
`com.github.LiQiyeDev` coordinate regardless of this pom's `groupId`/`version` (so the pom `version` is
cosmetic). **The maintainer owns the SDK → JitPack publish — don't push or publish the SDK yourself;**
releases are cut from the umbrella with `../release.sh`.

### Local dev (test SDK changes without pushing a tag)

> The old `dev-install.sh` script was removed and is **no longer needed**: this module's pom `groupId` is now
> `com.github.LiQiyeDev` (matching the coordinate JitPack serves), so a plain `mvn install` already lands the
> SDK at the coordinate a generated bot resolves. The whole `local-SNAPSHOT` / `-Dbotmaker.shared.version`
> dance the old script automated is obsolete.

A generated bot pins `com.github.LiQiyeDev:botmaker-sdk:<version>`, and the local `~/.m2` is checked before
JitPack. To make a bot pick up your local SDK changes, install both shared and the SDK to `~/.m2` at
`0.0.0-SNAPSHOT` in one shot from the umbrella root:

```bash
mvn -pl botmaker-sdk -am install     # builds+installs shared (-am) then the SDK, both at 0.0.0-SNAPSHOT
```

`-am` ("also make") builds the SDK's reactor dependency `shared` first, so both land at `0.0.0-SNAPSHOT`
(the version every consumer defaults to via `${botmaker.shared.version}`), and the installed SDK depends on
that local shared. Re-run it after each SDK edit; a bot pinned to `0.0.0-SNAPSHOT` resolves the freshly
installed jar on its next classpath resolve.

You never type the version into Studio: it auto-lists locally-installed `*-SNAPSHOT` SDK builds (newest
first) at the top of the SDK version dropdown (New Project and Project ▸ Manage Libraries), labeled
`(local build)` and **preselected**, so a bot created in a dev-run Studio is pinned to `0.0.0-SNAPSHOT`
automatically. This affordance is gated on `AppVersion.isDevBuild()` (true only when there's no jar
manifest, i.e. an IDE/`javafx:run` launch), so packaged/released builds never surface your `~/.m2` snapshots
and their users only ever pick real released versions.

## Code Style

Prefer **functional OOP**: minimize mutable class fields to avoid state-related bugs. Favor immutable
values (`record`s like `MatchResult`, `RawMatch`, `Point`/`Rect`/`Size`) and pure transformations;
pass dependencies in via parameters rather than holding mutable fields or static/singleton state.

**The three geometry types are records of `int`s, and both halves of that are deliberate.** `Point`, `Rect`
and `Size` were OpenCV `org.opencv.core.*` clones until 2026-08-23 — mutable public `double` fields,
`set(double[])`, `clone()` — none of which anything used, while the missing `equals` on `Point` and `Rect`
made `p1.equals(p2)` an identity comparison in every bot that tried it. They are `int` because every producer
is a pixel and every consumer is an input event the native layer delivers at a whole pixel; the old `double`
was cast straight back at fourteen call sites. **A fraction is rounded where it is created, never carried** —
`Rect.center`, `MatchResult.center`, `Pixel`'s centre of mass, `Mouse.drag`'s interpolation. Because
they are immutable, a getter hands back its field rather than a defensive copy; don't reintroduce one.
Keep side effects (screen capture, native library loading, process launching) at the edges. The
static facades (`ImageFinder`, `ImageClicker`, `ScreenCapture`, …) are stateless dispatchers.

## Architecture

### Public API vs internal plumbing

- **The line between the two is one question: can a bot *write the name down*?** A type it can only ever
  *receive* — from a factory, as an event, as a return value — belongs in `internal`, however public its
  methods are. That rule was applied in 1.1.0 and moved eleven classes out: the `CaptureSource`
  implementations (`Desktop`, `Monitor`, `NamedWindow`, `SessionSource`), which only ever arrive from
  `CaptureSource.desktop()/monitor()/window()` and `Source.current()` — all of which declare the *interface*
  as their return type — and the whole observation stack (`Bots`, `BotObserver`, `Surface`, `ClickEvent`,
  `MatchEvent`, `SwipeEvent`), whose only consumer was ever `internal.observe.IpcObserver`. `Screen` was
  deleted outright: no callers, and not even a `CaptureSource`. **The generated catalog is the mirror** of
  this decision, not a second one — a class that leaves `api` leaves the catalog, which is how the palette
  stops offering it. (Until phase 7 the mirror was Studio's hand-written `palette/SdkType` enum; it is
  deleted, and the SDK now *serves* the answer instead of Studio keeping a copy.)

- **The class-classification rule was retracted on 2026-08-27, hours after it landed, with the processor that
  enforced it.** What stood here described `@Internal` as *not versioned surface*, marked once per package by
  eleven `package-info.java` files, and three javac errors from `PluginSurfaceProcessor` switched on by
  `-Abotmaker.surface=com.botmaker.sdk`. All of it is gone: the twelve `package-info.java` files, the
  `-A` options, `<annotationProcessorPaths>`, and `botmaker-plugin-processor` itself. The reasons are worth
  keeping, because each is a decision and not a cleanup:

  - **All sixteen real `@Internal` sites were methods of `@Facade` classes** — not one was on a type. So the
    annotation's entire actual job was *hide this member from the palette*, which is what `@NotInPalette` had
    been before phase 8c widened it. The widening bought a rule nobody used and cost the weld below.
  - **The weld is dissolved rather than paid for.** `@Internal` made *not-surface* and *not-offered* one bit,
    so a type that is versioned but should not be proposed had to take `@Facade(role = "VALUE")`. Two
    annotations now say two things: **`@Palette` = catalogued** (the recognition set — imports, "does `Point`
    mean ours or `java.awt`'s"), **`@Hidden` on the type = not offered in an insert menu**. `FacadeRole`'s
    third state was read by nothing; `FacadeEntry.role` is a `boolean offered`.
  - **The catalog is reflected, not generated.** `SdkPlugin` calls
    `PaletteCatalog.of(Mouse.class, …)` — 52 class literals — and members are **discovered**. The generator's
    one defended property was *a catalog naming a renamed member does not compile*; nothing names a member any
    more, so nothing can go stale, and the class list stays javac-checked because it is class literals. What
    the processor also cost was unpayable by anyone outside this repo: a third-party pom omitting
    `<annotationProcessorPaths>` got no catalog and no diagnostic.

  The switch was verified by diffing the last generated `Catalog.java` against the reflected catalog: **same
  52 facades, same order, same member names, and every `.order(…)` prefix reproduced** — because
  `PaletteCatalog` reads the class file's own `methods` table for declaration order (`SourceOrder`), which is
  the one thing reflection alone cannot supply. Every failure path there falls back to alphabetical, so the
  worst case is a cosmetic menu order and never a project that will not open.

  One deliberate narrowing: **constructors are not catalogued.** Reflecting them put an `<init>` entry under
  seven offered static facades whose public constructor exists only because nobody wrote a private one, and a
  palette entry inserts a *call*. `MemberId` keeps its constructor support for a plugin that wants one.

- **A second rule, from the 1.1.0 method audit: no `api` signature may name a type the SDK does not version.**
  `botmaker-shared` and OpenCV are *freely breakable* by design while `api.*` is under contract, so a public
  `api` method returning one of their types promises a spelling nobody keeps — and no gate on either side can
  see it break. `ImageTemplate.getMat()` (`org.opencv.core.Mat`) is package-private for this reason, and
  `targetWindow()` (shared's `GenericWindow`) left `CaptureSource`/`Window` for
  **`internal.capture.WindowBacked`**, which `Window`, `NamedWindow`, `SessionSource` and `RegionSource`
  implement and `Keyboard` reaches via `WindowBacked.of(source)`. **The last knowingly-open leak closed in
  1.2.0**: `Text`'s nine `shared.ocr.OcrOptions` overloads were a real feature with no `api`-owned
  replacement, so rather than remove them the whole of `com.botmaker.shared.ocr` moved *here* — `OcrOptions`,
  `OcrLanguage` and `TextResult` into `api.vision` under contract, `OcrEngine`/`OcrNative`/`OcrPreprocessor`
  into `internal.ocr` where a bot can only receive their results. shared had exactly one consumer of that
  package (this facade), so the move cost nothing and dissolved the leak instead of working around it.
  **`docs/refactor/22-api-audit.md` is the record** of that audit: every verdict, the near-misses and why they
  were near-misses, and the additions it deliberately deferred.

- **`com.botmaker.sdk.api.*`** is the API generated bots compile against, and every class in it sits in a
  sub-package that says what it is: `api.geometry` (`Point`, `Rect`, `Size`, `Direction`), `api.meta` (the
  three pointer annotations — **deprecated shims since 1.2.0**; the vocabulary itself is
  `com.botmaker.plugin.api.meta` now, see below), `api.bot`, `api.capture`, `api.emulator`, `api.interaction`, `api.launch`,
  `api.util` (`Time`, `BotMaker`, `Debug`), `api.vision`. **The `api` root holds no classes** — it was a
  junk drawer of annotations, geometry and five facades until 1.1.0, and a name landing there again means
  somebody skipped the question above. It is under a **compatibility convention** — real semver, and a removal announced by one full minor marked
  `@Deprecated(since = "x.y.z", forRemoval = true)` whose Javadoc `@deprecated` line names the replacement.
  A member added after 1.1.0 carries `@since`; the 1.1.0 surface itself carries none, because comparing two
  published jars already yields the exact per-version added/removed set and 818 identical tags would not.
  The full picture is **`../docs/refactor/21-api-compat.md`**.

  **Convention, not enforcement, and that is a deliberate 2026-08-22 decision.** There was a gate here:
  japicmp against the previously published jar, `ApiRulesCheck` in `src/api-check/java`, an `api-check`
  profile, and `release.sh` refusing a version number the diff did not justify. All of it is deleted. It
  existed to protect a repair model where a break was carried across by **pointing one member at another**
  — the old `fix` kinds — and there, a wrong or missing declaration produced a bot that compiled and
  behaved differently, so the declarations had to be checkable. What it costs is stated plainly in
  `release.sh`: nothing now refuses a breaking change released as a patch, and nothing sizes the bump.

  **japicmp is back, in one line: `com.botmaker.sdk.api.**` never removes anything** (2026-08-27). That
  reverses the paragraph above, and the reversal is legitimate for a reason worth stating rather than
  asserting. The 2026-08-22 gate died because **CI cannot tell an intended break from an accident, since it
  cannot see the version** — a statement about a *conditional* rule, where a major release may legitimately
  remove. Never-delete is **unconditional**: there is no legitimate removal, so there is nothing to
  distinguish, and the objection evaporates. Hence no ignore list, no exemption annotation and no verdict
  file — an escape hatch is what killed the last one. It is bound to `verify`, scoped to `api.**` so
  `internal.**` stays freely breakable, and its baseline is `botmaker.japicmp.baseline` in the pom: **v1.2.0,
  the release the rule begins at**, because v1.1.0 → now already removed `api.config.Wire`, `@Palette`,
  `@Scaffolding` and `Text`'s nine shared-`OcrOptions` overloads, every one a recorded decision taken while
  `api.*` was still freely breakable.

  The accepted cost, stated plainly: **`com.botmaker.sdk.api` only ever grows.** That is the trade, and it is
  the same policy the JDK runs.

  **What carries a rename is `@ReplacedBy`, written on the deprecated element** (2026-08-23; it replaced
  `@ApiId` and `META-INF/botmaker/migrations.json`, both deleted). A jar diff sees `ImageClicker#click` go and
  `IClicker#tap` arrive and cannot see that one became the other; read as a removal, that is hundreds of calls
  replaced by default values in someone's bot. It is read out of the bot's **own** jar — the bot still spells
  the element the old way, so that is where the forward pointer has to be. Each target is `fqn`, `fqn#member`
  or `fqn#<init>`, no arity (it sits on one overload). **An empty value is an explicit "nothing takes my
  place"**, not an omission — which is why it is *required* on every deprecated public element.

  **`@Replaces` — the back edge, written on the survivor — was deleted on 2026-08-27, and japicmp is what
  makes that safe.** It existed for one case: Studio holds only two jars at upgrade, the bot's pin and the
  target, so a bot on 1.0 jumping to 3.0 could not see a pointer added in 2.0 on an element 3.0 deleted, and
  the answer had to survive on the survivor. **Under never-delete the target jar still carries the deprecated
  element and its own `@ReplacedBy`**, so the forward pointer alone answers every upgrade including a skipped
  one. A rename is now: add the new name, deprecate the old, keep both, point one at the other. `@Since` went
  the same day, for the reason this repo applies to every gate — *the question a check answers must not
  already be answered by bytecode*, and the release an element first shipped in is answerable from the jar the
  bot resolves.

  **`@ReplacedBy.value()` is a `String[]`, and that is the split.** One old member can become two, and
  *which* one a given call meant is a property of **that call**, not of the member — `Mouse.scroll(int)`,
  whose sign decides `scrollUp` from `scrollDown`, is the worked example, and no annotation can know a sign.
  So the SDK does not resolve a split, it **offers** one: the targets in preference order (first preferred)
  plus a parallel **`whens()`** carrying one sentence per candidate (*"when notches is positive"*), and
  Studio puts the choice to the user once per call site. `@ReplacedBy("…#tap")` is unchanged in source and
  in bytecode — a single value is already a one-element array — so the ordinary one-target pointer is the
  degenerate case of all of it.

  **Pointers compose into a chain** — `a`→`b` in 2.0 and `b`→`c` in 3.0 land a bot still spelling it `a` on
  `c`, with the 2.0 jar never fetched, because never-delete keeps `a` and its pointer in the 3.0 jar. Write
  the pointer **in the release that makes the change**, while both ends are compilable: that is what lets the
  gate below verify the link from a single build. A pointer is an ordinary annotation — correct a wrong one in
  a later release.

  **`ApiPointersTest` is the gate, and it is not the one that was deleted.** One offline ClassGraph scan of
  `target/classes` plus the contract's, run by CI on every build and by `release.sh check_api_pointers`.
  **Four rules** since the back edge went (2026-08-27), each wrong at every version: every deprecated element
  carries a pointer (1); **every** target resolves (2); a `behaviourChanged` move carries its sentence (8); a
  split says when each candidate applies (11). Rules 3–7 read `@Replaces` or `@Since` and went with them —
  with them went `-Dbotmaker.api.maxVersion`, so nothing here is version-aware any more. **It is not a
  coverage rule**: an uncovered break is a supported outcome (default value plus review mark), and these four
  only ask that a link somebody *did* declare is complete.

  **`api-surface.txt` was the second gate, and it was deleted on 2026-08-25 — a deliberate reversal of the
  decision recorded here the day before, and the second one this month.** It was a committed, generated file
  at the module root holding the *previous* release's public `api.*` surface, one sorted line per element
  (`type#member(paramTypes):returnType [deprecated] [since=…]`), which `ApiSurfaceTest` diffed this build
  against: an element that left had to have carried `[deprecated]` (the announced window), an element in both
  kept the exact `@Since` it had, and a new element had to carry one. It went with `ApiSurfaceTest`,
  `release.sh`'s `check_api_surface` / `refresh_api_surface` and `--allow-removal`
  (`-Dbotmaker.api.allowUndeprecatedRemoval`).

  Why: it was a **second, hand-maintained record of what the SDK offers**, kept in a text file beside the
  code, and the inversion is about to build the real one — an explicit per-version catalog in
  `api.authoring`, written in code, keyed by `SdkVersion`, which answers *what did 1.2 offer* rather than
  only *what did the last build offer*. Two records of the same fact is exactly the shape this teardown
  keeps removing. The catalog subsumes the gate, and until it exists the window is a **convention** again:
  `@Deprecated(since, forRemoval = true)` one full minor ahead, with a pointer, still the rule — nothing
  mechanically refuses an undeprecated removal.

  **What survives is `@ReplacedBy` and `ApiPointersTest`.** `ApiPointerProcessor` — the same rules again as
  javac errors, red in the IDE while the annotation is being typed — moved into `botmaker-plugin-processor` on
  2026-08-27 (phase 8c) and was **deleted with that module the same day**, when the catalog stopped being
  generated and the processor had nothing left to do that a test does not do. The ergonomic loss is real and
  accepted: a bad pointer is now a red test rather than a red line. The test was always the authority — *if
  the two ever disagreed the test was right* — so nothing that decided anything went with it.

  **The vocabulary itself left this module in 1.2.0 (phase 8c.4).** `@ReplacedBy` is
  `com.botmaker.plugin.api.meta` now: it describes how any library keeps faith with the code that calls it,
  and a second plugin renaming its own types wants the same machinery rather than a copy of it. What is left
  in `com.botmaker.sdk.api.meta` is three `@Deprecated(since = "1.2.0", forRemoval = true)` shims —
  **the pointer's first use was its own move**, which is the fairest test it could have had. Under
  never-delete those shims stay in the jar rather than being removed after a window; `Since` and `Replaces`
  now carry `@ReplacedBy({})`, "nothing takes my place", because their contract-side targets are gone too.
  Two things worth knowing:

  - **A pointer may cross modules**, and rule 2 resolves against `com.botmaker.sdk.api` ∪
    `com.botmaker.plugin.api` for exactly that reason — a carve-out exempting contract targets would have
    had to be removed again later, where a wider universe is simply the truth.
  - **`ApiPointersTest.first(…)` filters with `directOnly()`, and that is load-bearing rather than tidy.**
    ClassGraph folds meta-annotations into a class's annotation list, and these annotations annotate *each
    other* — so without the filter every element that merely *uses* one reads as carrying whatever that
    annotation's own declaration carries. A redirect is a statement about the element it is written on;
    nothing here ever wanted an inherited one.

  **Two elements sit on the pointer itself**, `@Retention(CLASS)`, read from the jar by the same scan
  (2026-08-23). Each records something that is cheap while both ends of a move still exist and impossible
  afterwards:

  - **`@ReplacedBy(note = "…")`** — the author's own sentence, shown to the user **verbatim**. The pointer
    says *what*; nothing else can say *why*. (It is what `migrations.json`'s deleted `summary` used to be.)
  - **`@ReplacedBy(behaviourChanged = true)`** — the replacement *does something different*. This is the one
    gap the redirect model cannot see: Studio takes a pointer by comparing **shapes**, so "same shape,
    different meaning" is exactly a silent, successful rename, and the bot compiles and misbehaves. Setting it
    forces a review mark on every redirected site, with the note as its text — hence rule 8: `true` with a
    blank `note` is refused, since a mark that says nothing costs a hand review and answers nothing.

  **`@Since` was the third and was deleted on 2026-08-27**, with rule 7. It recorded the release an element
  first shipped in, so the upgrade dialog could group additions by version — and that is answerable from the
  bot's own resolved jar, which is the test this repository applies to every gate. Twenty sites carried it;
  the pre-1.1.0 surface deliberately never did.
  **Two more lived here and were deleted on 2026-08-25 — `@Scaffolding` and `@Palette`.** Both existed to let
  **two repositories agree about something neither could read in the other**, and the maintainer's decision is
  to remove the disagreement rather than manage it: the SDK becomes the generator and the palette, so there is
  no second author left to inform. Worth knowing they existed, because both left visible holes:

  - **`@Scaffolding`** said *Studio writes this element into the files it generates*, so renaming it broke
    bots that never mentioned it. `ApiPointersTest` rule 9 refused a `@Deprecated` `@Scaffolding` element with
    an empty `@ReplacedBy`, and `ScaffoldTemplatesTest` read the templates' own constant pools to require the
    annotation on every `com.botmaker.sdk.*` member they reached. (It had been reconciled through a committed
    `scaffolding-surface.txt` written by Studio's own test, until the scaffold moved into
    `src/templates/java` on 2026-08-24 and made the file unnecessary — the same argument, one step further
    along, is why the annotation itself is gone.) Both rules went with it.
  - **`@Palette`** said *Studio offers this in its block palette* — a strict, per-overload whitelist, where
    **hiding was not deprecating**: an unannotated method stayed public, supported and under contract, simply
    not proposed. It was the answer to the wall the method audit kept hitting
    (`docs/refactor/22-api-audit.md` §3, §5), where a method could only leave the menu by leaving the API.
    Deleting it **widens Studio's menus** — `SdkSurfaceService` treats a jar with no `Palette` class as
    uncurated and offers everything public — until the SDK serves the palette itself. That is a known interim
    cost, not a regression. The maintainer's per-facade curation *prose* survives in each facade's Javadoc
    (*"Curated for the palette: …"*); the per-member verdicts lived only in the annotations and are gone.

  Both were public `api.meta` types removed after 1.1.0 shipped, i.e. an undeprecated removal, taken
  deliberately via `release.sh --allow-removal` with `api-surface.txt` regenerated in the same commit — the
  last use of a switch and a file that were themselves deleted hours later (above). Neither was a name a bot
  could write down.

## `api.config.Wire` — a bot reads its own settings (2026-08-29)

The runtime half of *derived files stop being Java*, and the precondition for deleting `SourceEmitter`. A
generated `Parameters` class of `public static final` fields exists only to give stored values a name;
`Wire.whole("minHealth")` gives them the same name and costs one thing, stated plainly: **a misspelled name
is not a compile error.** Nothing is generated in exchange, and nothing is rewritten under the user.

Three things about it are decisions rather than details.

**It reads a JSON tree, not the authoring records, and it has no choice.** `com.botmaker.sdk.authoring` has
`ProjectModel`/`VariableModel` for all of this and they are **unloadable in a bot**: `VariableModel` names
`ValueChoice`, `Range`, `Visibility` and `ParameterGroup` in its own components, and `botmaker-studio-api` is
`optional` — deliberately off a generated bot's classpath. Loading one in a bot is `NoClassDefFoundError`. So
`internal/config/ProjectData` walks the tree over field names that are the records' component names.

**What is deliberately not duplicated is the part that would hurt.** Every codec in `SdkValueTypes` parses
through `WireText`, which imports only `sdk.api.*` and the JDK — so `Wire` delegates to it and the editor and
the running bot cannot disagree about what `"1m30s"` means. Two readers of one file is a standing risk;
`ProjectDataTest.readsBackWhatTheEditorWrites` writes with `Authoring.modelJson` and reads with `ProjectData`,
which is the only honest mitigation. **Add a value type to `SdkValueTypes` and add its reader here.**

**Nothing throws, and that is load-bearing rather than polite.** The old generated class wrote *parsed
literals* (`new java.awt.Color(255, 0, 0)`, never `Color.decode(…)`) precisely so a bot could not fail at
class initialisation over its own configuration. Moving to a runtime read is only safe because `WireText` is
total: a missing file, a missing key and an unparseable value each have a documented fallback. A reader that
threw would give that guarantee back.

`Wire.enabled(name)` is an **activity's** switch and is a different list from `Wire.flag(name)`, which is a
yes/no variable. `ProjectData` is `internal` and is what `FlowGraph`'s loader reads; `Wire` is what a bot
author writes.

## Five generated files became reads (2026-08-29)

`SourceEmitter` wrote nine `.java` files; five of them — `Activities`, `Parameters`, `Templates`,
`ActivityRegistry`, `FlowDriver` — followed **entirely** from the project's own model and were rewritten on
every tick, value, capture and wire. All five are deleted, and none was replaced by a different generator.
`Authoring.regenerate`, `.templates` and the `imageBaseNames` parameter went with them; there is no longer a
subset of a project that is re-rendered after creation.

**The rule this states: a file whose contents follow from project data is data.** That is what made deleting
the emitter possible at all. The four files it left were an entry point, `GoHome`, `Popups` and one stub per
activity — which went too, hours later, when the answer turned out not to be *a better way to write them* but
*the SDK writes no source*. See below.

| Was | Is |
|---|---|
| `Activities.MINING` | `Wire.enabled("Mining")` |
| `Parameters.minHealth` | `Wire.whole("minHealth")` |
| `Templates.ORE` | `Wire.image("ore")` |
| `ActivityRegistry.MINING` | `ActivityLoader`, by convention |
| `FlowDriver.run()` | `FlowGraph.run(Main.class, GoHome.INSTANCE::execute)` |

**`FlowGraph.load/run` route on outcome *names*, and that is the one check given up.** The generated table
was typed — `node` is generic in the activity's own outcome enum, so a route built from another activity's
constant did not compile. Read from a file it cannot be. The check that mattered is kept where a human
actually writes one: `return Outcome.BAG_FULL;` in an activity's body, against an enum the editor maintains.
A wire in a file the editor wrote was never where the mistakes were. `of`/`node`/`route` are `@Deprecated`
with `@ReplacedBy` and **not removed** — never-delete is unconditional, and an existing bot's generated table
goes on working.

**The activity's class is found by convention, and a manifest was refused.** `<the anchor's package>.activities.<Name>`,
which is where the editor has always written the stub; the anchor is a `Class<?>` so a rename of the class,
the package, or both changes nothing. A resource manifest listing class names was the obvious alternative and
is a second statement of a fact the file already carries — written by somebody, kept in step by somebody, and
wrong the first time it is not. `ActivityLoader` **constructs** every activity the model names, placed or
not: `Activity`'s constructor registers it by name, which is what makes `Activity.disable("Mining")` resolve,
and it is the only thing the old registry's `ALL` field was for.

**`assemble` is in `api.flow.FlowGraph` rather than in `internal`**, alone among the flow code, because
`Node`'s constructor is private and widening it so a loader elsewhere could call it would put the only
unchecked way to build a node on the public surface. Reading the model is `ProjectData`'s and constructing
the activities is `ActivityLoader`'s; only the assembly is here.

**What is *not* affected: `LiteralWriter`.** It still writes Java, for slot values in a bot's own body
(`SdkValueTypes`' codecs). Only the files that held a project's data stopped being source.

## The SDK writes no `.java` (2026-08-29)

`SourceEmitter` is **deleted**, with `Authoring.sources`, `.activityStub`, `.generatedFileNames`, the
`internal/plugin/seeds/` package that briefly replaced it, `SdkPlugin.scaffold`/`seedings`, `ScaffoldEmitTest`
and `SdkPluginSeedsTest`. `ProjectWriter` still creates a project — `activities.json`, the project
properties, the placeholder image, the four `src/` directories — and every `.java` now arrives through
`Authoring.createProject`'s `callerFiles`, from the host.

**The rule: a project's structure belongs to the user, and a plugin contributes methods a user calls.** It is
the argument `pom.xml` had already won — the pom declares *which* SDK and which other plugins a project has,
and the SDK is one plugin among them, so only the thing that knows the whole set can write it — applied
without an exception left. Nothing is *installed* anywhere: what a bot's entry point holds is ordinary static
calls into whatever plugins its pom pins — `PopupGuard.install`, `Bot.start`, `FlowGraph.run` — written by
the user and deletable by the user, like every other line in the file.

**The seeds were the near miss, and they lasted one day.** `internal/plugin/seeds/` held `GoHome`, `Popups`
and `ActivityTemplate` as real compiling classes marked with what a host could substitute, so javac checked
them and a broken seed was a red build here rather than in somebody's project. Every step of that improved on
the emitter. What was wrong is one level up: it made *writing files into a user's project* a plugin surface,
and the host grew a key ledger, a reconciler and a rename engine to keep owning what it had written. See
`../botmaker-studio-api/CLAUDE.md`, which records the reversal from the contract's side.

## An activity is a lambda (2026-08-29)

`Activities.define("Mining", ctx -> …)` is what replaces the generated `class Mining extends
Activity<Mining.Outcome>`. Three new `api.bot` types — `Activities`, `ActivityContext`, `Outcome` — plus
`internal/bot/{ActivityRegistry,LegacyActivity}`.

**`ActivityContext` exists so the editor has a receiver to hang a picker on.** A body returning a bare
`String` looks, to Studio, exactly like a body returning any other string; `ctx.outcome("BAG_FULL")` is a
call on a known type, which is what lets a dropdown of *this activity's* declared outcomes be drawn where the
name is typed. Having built it for that, it is also the natural home for what a body used to reach through
`this`: `name()`, `enable()`, `disable()`. The activity **name** is a plain `String` for the reason an
`ActivityName` wrapper was already refused in Studio — it is resolved through a `String`-keyed registry
anyway, so a wrapper adds ceremony at a boundary that must be a runtime string; the editor draws a dropdown
there too, chosen by the call.

**There is one registry, and that is the load-bearing part.** `internal/bot/ActivityRegistry` holds a
`Runner` — name, `active()`, `setEnabled`, `execute()` — and both kinds of activity land in it:
`Activities.define` registers a lambda, and `Activity`'s constructor registers itself through
`LegacyActivity`. Two maps would make `Activity.disable("Mining")` silently miss half a bot's activities,
which reads to a user as the flow being wrong rather than as a bug. `Runner` deliberately does not carry the
outcome *type*: the walk has only ever asked an outcome for its name.

**An activity with no body takes its `DISABLED` wire**, and that is a deliberate reversal. `FlowGraph.assemble`
used to drop such a node, so a wire into it ended the run; it now builds a node with a `null` runner and
`FlowWalker` treats that exactly as a switched-off activity. That is what makes drawing a flow *before*
writing its code an ordinary way to work — every card is on the canvas and the run walks through, rather than
stopping at the first one nobody has written.

**`FlowGraph.Node.activity()` is deprecated, not removed**, and answers `null` for the two cases that were
impossible when it was written: a lambda-defined activity, and one with no body at all. `Node.runner()`
answers for all three, and `target(Outcome)` joins the deprecated `target(Enum<?>)`. Never-delete, as ever.

**What is given up: a misspelled outcome is not a compile error.** `ctx.outcome("BAG_FUL")` compiles, is
reported, matches no wire, and ends the run — the same answer as an outcome the user declared and never
wired, deliberately. The picker is the replacement for the check, and one console line names the typo when
the reported outcome is not one the canvas declares.

**`ProjectData.use(ProjectData)` is a public test seam** because the readers of `current()` are spread across
packages now — `Wire`, an `ActivityContext` checking an outcome name, a defined activity asking whether it is
switched on — and each wants a model written in the test rather than a resource file per case.

**`ActivityModel.id` survives the seeds that motivated it**, and is worth keeping for the same reason it was
added: the name is what a rename changes, so anything keying on the name sees a delete plus a create. Nothing
keys on it today. **Absent means the name and nothing migrates** — stable, needs no rewrite of a stored file,
where a random default would make every open of an old project look like a rename.

**`SdkPlugin.SDK_PARAMETERS` moved off `SourceEmitter`** and is private to the plugin. It was never about the
generated `Parameters` file it once named — a `ParameterGroup` is how the editor's Parameters dialog decides
which plugin a variable belongs to.

## The Remote Pilot is this plugin's feature (2026-08-30)

`internal/plugin/pilot/` — the server, the routes, the input path, the video encode, the Tailscale Funnel
work and every dialog they put on screen, plus the built web client under `src/main/resources/pilot/`. It was
`botmaker-studio`'s until 2026-08-30 and it was never Studio's *subject*: everything behind it is about what a
bot sees and does.

**The entry point is a `ToolbarItem`, and that is the whole surface it uses.** `SdkPlugin.toolbarItems()`
contributes one button; `projectClosing()` releases the bound port and the nested display. A plugin
contributes no menu items and no panels, so the View-menu entry it used to have is simply gone.

**What a host must supply turned out to be four facts, and the contract already had all four**:
`resourcesDir` (which project), `status` (a line in the host's status area), `theme` +
`dialogs().owner()` (looking like the application, and being owned by its window), and `runs` (the bot as a
process). **Nothing was added to `StudioServices`** — that was the standing condition on the move, and it is
the test to apply to the next feature that wants to leave: if it needs a new service, the split is wrong.

**`PilotProject` is the seam.** The default capture target comes from `capture.json` through `Authoring`, the
reference resolution from `botmaker-project.properties` through shared's `ProjectFile`; both read on demand,
never cached, because a target changed in another window has to take effect in the running stream.

**Telemetry crosses as `TelemetryFrame` bytes** (`Runs.onTelemetry`) and is decoded here with the same shared
codec the bot encoded it with. That is what keeps `TelemetryEvent`'s vocabulary off the contract, and it is
the general rule for this boundary: **strings and bytes cross, shapes do not.**

**Javalin and ZXing are `optional`**, like JavaFX and the toolkit and for the same reason — the pilot is a
plugin feature with a window, so a headless bot resolves neither.

## The capture targets are authoring data (2026-08-30)

`authoring/CaptureModel` + `authoring/CaptureTargetModel`, stored as **`capture.json`** beside
`activities.json` and reached through `Authoring.readCapture`/`writeCapture`/`captureJson`. It is the same
shape as the model file because it is the same kind of fact — *a file describing the bot, owned by the bot's
own SDK version* — and it exists because the same list was being stored twice: the editor's `settings.json`
held the targets a picker offered, `botmaker-project.properties` held the one spec a running bot reads, and
nothing kept them in step. Both files parse, so the disagreement was silent.

**A target's identity is its spec text**, in shared's `CaptureSourceKind` grammar. Four record shapes would be
a second grammar to keep in step with the one the bot already reads; the spec is what both sides mean.

**And since later the same day there is only one vocabulary, because `CaptureTargetModel` answers the
questions a shape used to.** `desktop()`/`monitor(int)`/`window(String)`/`emulator(String)` build one;
`is(CaptureSourceKind)`, `isDesktop()`, `monitorIndex()`, `windowTitle()`, `emulatorName()` read one; and
`longLabel()`/`shortLabel()` name one at the two lengths a UI needs. Studio's
`project.capture.{CaptureTarget,CaptureTargets,CaptureTargetNames}` — four sealed records, an adapter onto
this model and a label table — are **deleted**, and so are the pilot's private `monitorIndex` and its
inline kind checks. That is the point of the accessors being here rather than at each caller: the two
spellings had already drifted, a monitor index that is not a number reading as *monitor 0* in the editor and
as *no frame this tick* in the pilot's stream. Every accessor is deliberately narrow — a window title read
off a monitor target is `null`, not a guess — because the shapes they replace could only ever be one thing,
and a widened accessor would silently capture the wrong surface.

**A spec nothing recognises is the whole desktop, everywhere.** It was unreachable while the vocabulary was
four sealed records and is ordinary now: a hand-edited file, or one written by a newer Studio that knows a
form this one does not. Nothing throws and nothing refuses the project.

**The one thing that stayed out of the model is the live window id**, in Studio's
`TargetCapture.WindowRef`. A gamescope host window cannot be named by title, so the caller that launched it
holds its native handle for the length of one session — and a handle is meaningless once persisted, which is
exactly what `window:<title>` says by having nowhere to put one.

**No schema stamp on this file**, deliberately — the migration ledger is the caller's and its one entry point
is `activities.json`; a second stamp is a second ledger. And **the model normalises rather than trusts**: an
index naming nothing becomes absent, `defaultTarget()` is total and stands in the first target for a project
that never chose.

**A bot cannot read this file, and that bounds the move.** `Authoring` names the value vocabulary in
`botmaker-studio-api`, which is `optional` and deliberately off a bot's classpath — the same trap
`api.config.Wire` documents. So the running bot still resolves `capture.source` out of the properties file,
and Studio writes that key **from the default target, in the same pass as the list**: one writer, one
direction, a cache rather than a second answer. A classpath reader beside `internal/config/ProjectData` is
what would retire it.

## The colour editor, and the frame it samples (2026-08-30)

`internal/plugin/editors/ColorEditors` plus `internal/plugin/capture/{EditorFrame, ColorSampler, ZoomPan}` —
the first of the capture-shaped editors to leave Studio, and the one that says how the rest should go.

**One editor replaced two that had drifted.** Studio drew a `java.awt.Color` slot with a swatch and a frozen
sampler, and a Parameters row with a swatch and a live screen pick: the same value, the same question, two
widgets, and only one of them could report the ΔE spread that is the whole point of sampling from a real
frame. Both of Studio's arms are deleted — the `PickerRegistry` entry *and* `ValueEditors`' `COLOR` case —
because **a type the host answers is a type no plugin is ever offered**, which is the same deletion that let
`DurationEditor` reach both places.

**`EditorFrame` is the plugin grabbing its own pixels, and that is the shape to copy.** Which project is open
is the one thing only the host knows (`StudioServices.resourcesDir()`); *which target that project chose* is
read from this plugin's own `capture.json` through `Authoring`, and the pixels come from `botmaker-shared`,
which any plugin may depend on. Nothing was added to the contract.

**The contract's `Capture.grabFrame` cannot serve this, and that is why it is scheduled for deletion.** It
reports a failed or blank grab by *never calling back*, so an editor cannot tell "failed" from "still
working" and has nothing to say to the person waiting. `EditorFrame.Failure` draws the distinction that
matters instead — *no target configured* versus *the grab came back blank* — because they send a user to two
different places, and on a Wayland session the second happens to targets that are configured perfectly well.

**The eyedropper has a fallback, and it is what made this slice possible at all.** With a capture target it
opens the frozen sampler; without one it falls back to the host's live screen pick (`Capture.sampleColor`),
after saying so once. That is why this editor never has to send anybody to a dialog before they can answer
the question in front of them — which matters because the capture-targets dialog is still Studio's.

**`ZoomPan` was in the wrong module on purpose and left on 2026-08-30**, for `botmaker-plugin-toolkit`, the
moment `ObjectCaptureSurface` — its second caller, and the reason it could not go earlier, since Studio
source may not name a toolkit type — arrived here too. What the SDK keeps is the two surfaces that use it.

## The capture surfaces are this plugin's (2026-08-30)

`internal/plugin/capture/{CaptureSurface, ObjectCaptureSurface, MagicWand, OverlayStage}`, out of Studio's
`ui/app/capture` and `ui/app/overlay`. **The overlay is a feature of the SDK, not of Studio** — it exists to
produce an `ImageTemplate`, which is this plugin's type, from a `CaptureTargetModel`, which is this plugin's
data. Studio's `OverlayTemplateCapture` still drives them for one more step and names them where they now
live, exactly as it named `ZoomPan` before.

Three things in the move are worth keeping:

- **The dead parameter became the live one.** Both surfaces took a `Window owner` they never used — they are
  deliberately ownerless, so a user can minimise the editor and keep capturing. That parameter is now
  `StudioServices`, which is how they reach `Capture.toFxImage` for the frozen backdrop. Nothing was added
  to the contract to make the move: the conversion was already on it.
- **`Styles.UNTHEMED` replaced `ThemedWindows.UNTHEMED`.** A translucent surface over a live game must not
  acquire the shell's chrome, and the host themes a plugin's windows for it, so the opt-out had to become
  something a plugin can say. It is the same string, now in the toolkit.
- **`OverlayStage` is here rather than in the toolkit** because the raise itself is `botmaker-shared`'s
  (`NativeControllerFactory.promoteOverlayAboveFullscreen`, EWMH hints found by window title) and the
  toolkit may name no BotMaker upstream but the contract. It is not on the contract either, deliberately:
  any plugin may depend on shared and do this for itself, so the host is not the only possible source.
  Studio's `OverlayToolbars` delegates to it, and its last two callers leave with the launch pickers.

## The project's pictures are this plugin's folder (2026-08-30)

`authoring/TemplateLibrary` — Studio's `services.ImageTemplateLibrary` until this date — with `TagCatalog`
and `TemplateManifest` beside it. It is the store: the PNGs, their resolution sidecars, the tag manifest, the
pixel hash that finds duplicates, rename and delete.

**It is here for the reason `capture.json` is.** A *named picture* is `ImageTemplate`'s own concept, so the
plugin that offers the type owns the folder; the alternative is two readers of one folder, which is the
drift the capture-target work spent a whole phase deleting. Half of the vocabulary was already here —
`TemplateNames` holds the file↔constant bijection and the placeholder picture — so what moved is the folder
half that had been left behind.

**It is keyed on the resources directory**, which is `Authoring`'s idiom and, not by accident, exactly what
`StudioServices.resourcesDir()` hands a plugin. Studio's `ProjectConfig` was answering three questions here
(the images folder, the project root to relativize against, the activities file) and every one is derivable
from that single path — which is why `pathFor` now builds `src/main/resources/images/<name>.png` from
`WireText.IMAGE_PREFIX` and the file's own name rather than relativizing against a root it no longer has.

**Two things deliberately did not come**, and the line between them is the same one the whole move runs on —
*a picture folder is the plugin's, an open editor is the host's*:

- **`openActivityTag`** reads which file the editor has open. `TemplateLibrary.declaredTag` is the half that
  could travel (turn a name into a tag the project actually declares); Studio's façade keeps the half that
  asks the editor.
- **`TemplateReferences`** — where in the bot's *source* a picture is used, and how to repoint those uses. It
  stands on the open buffers (`ProjectState`) and on `ReviewMarker`, and it rewrites a user's Java. That is
  host work and always will be, so the Resource Manager's rename and delete guards stay in Studio with it.

**`TagCatalog.of` takes activity *names* now**, not a parsed activities file. That is what let it leave: the
only thing it ever wanted from an `ActivitiesConfig` was `name()` in file order, so asking for that directly
means each caller reads the file with whichever reader it already has — `Authoring.readModel` here, an
editor's own parse there.

## How exact a match has to be, in both places (2026-08-30)

`internal/plugin/editors/PrecisionEditors` is the second picker to arrive, and it retired the other
temporary the colour slice left behind: nothing in Studio names
`com.botmaker.sdk.internal.plugin.capture` any more.

**Three numbers that each fail silently, so the editor shows rather than states.** ΔE has no obvious top and
is not a percentage; `minArea` is an *area* and invites being read as a length; `minCount` is the colour
present at all, clustered or not, which sounds like the same question as the area and is not. So the slider
is laid out against the type's own anchors with a strip of swatches at increasing ΔE marking what the current
tolerance lets through, the area is drawn **to scale** over a 1:1 grid, and *Sample from game* reports what
these settings would actually find in a frozen frame — how many blobs, how big the largest, and how much of
the colour is in the frame at all. Without a frame the other two are abstractions.

**The parse is the part that moved, and it is the shape to copy.** Studio read the current value off a JDT
syntax tree; the contract hands a plugin **source text**, so `settingsOf` walks the expression's *top-level
dotted segments* and applies each one it recognises — an anchor, `of(…)`, or a `tolerance`/`minArea`/
`minCount` wither. That is all a wither chain is, so it needs no parser, and it makes a leading package name
free: `com.botmaker.sdk.api.vision.Precision.LOOSE` is six segments nothing matches followed by one that
does. Splitting at *top-level* dots is what keeps `Precision.of(12.5)` readable.

**Studio's `PRECISION` row is deleted with the `PickerRegistry` entry**, on the rule the colour slice
established. It drew the same value as a preset dropdown and three bare fields — the shape a record's
components suggest — with none of the swatch strip, the blob preview or the frame readout. A Parameters row
now gets the whole dialog, and `knobsFor(null)` offers it all three knobs, which is the honest answer where
there is no enclosing call to narrow them.

**What is written stays two spellings of one value.** A slot gets the shortest exact Java form
(`Precision.TIGHT.minArea(400)`); a row gets `deltaE,minArea,minCount` spelled exactly as `SdkValueTypes`'
own `PRECISION` codec spells it. The editor and the codec are two writers of one file, and a disagreement
between them is a value that changes meaning when it is written back.

**No screen-pick fallback here, unlike the colour editor.** What these settings are previewed against has to
be a frame of the thing the bot will look at; the desktop behind the dialog is not it, so a project with no
capture target gets the sentence and no preview rather than a preview of something else.

## The two pickers the lambda was built for (2026-08-30)

`internal/plugin/editors/ActivityEditors` is the other half of the paragraph above: `CallSites.ACTIVITY_NAME`
(argument 0 of `Activities.define`) and `CallSites.OUTCOME_NAME` (argument 0 of `ActivityContext.outcome`)
are two `SlotEditor`s in `SdkEditors.ALL`, and they are the reason `outcome` takes a context rather than the
body returning a `String`.

- **The list is read out of `activities.json`, by `Authoring.readModel`.** The canvas is the source of truth
  and it is a file, so there is nothing to ask a host for beyond `StudioServices.resourcesDir()` — which is
  the contract's rule doing its job: which project is open is the one thing only the host knows.
- **Read when the dropdown opens, never when the block is drawn** (`Editors.choiceSlot` takes a `Supplier`,
  the same rule as `Editors.gallery`), so an activity added in the flow window a moment ago is offered
  without reopening anything.
- **The outcome box offers every outcome in the project, not this activity's own**, and the honest reason is
  that an editor is told the call it sits in and no more: the `Activities.define("Mining", …)` it is nested
  inside is two levels up a syntax tree no plugin sees. The union with duplicates collapsed is what can
  actually be answered; a name typed anyway is still accepted.
- **Both boxes stay typeable**, which is `Editors.choiceSlot`'s editable `ComboBox`. Writing a body before
  drawing the activity is an ordinary way to work, and an editor that could only pick from what exists would
  make it unsayable — the same trade `define`'s string name already makes.

## The scaffold templates are gone (2026-08-25)

`src/templates/java` — nine files the SDK compiled and shipped as *text* under `botmaker-templates/` for
Studio to fill — is **deleted**, with `@Template`, `apt/TemplateProcessor`, the generated `manifest.txt` and
`ScaffoldTemplatesTest`. The pom went back to two compiler passes: `compile-processor` (`src/apt/java` →
`target/apt-classes`, `-proc:none`) and the main compile that runs `ApiPointerProcessor`. Passes 3 and 4 went
together — pass 4 existed only to undo pass 3's `projectArtifact.setFile(target/template-classes)`.
(**Phase 8c took the remaining two down to one**: the processor became `botmaker-plugin-processor`, an
`<annotationProcessorPaths>` entry. **The annotation rework then took it to none** — that module is deleted,
the catalog is reflected by `PaletteCatalog.of(...)` and this pom runs `<proc>none</proc>`.)

This is the second half of the same reversal: the templates existed so **two repositories could co-author one
file**, the SDK owning its frame and Studio splicing the fences. The inversion removes the second author —
the SDK becomes the generator outright (`api.authoring`, inversion Phase 2) — so a text-and-fences protocol
between them has nothing left to mediate.

**The interim cost is real and is the point:** with nothing to fill, **Studio cannot generate a project or
save an Activity Flow** until Phase 2 lands. Both paths refuse by name. Do not re-add a template as a way
around that; the emitters belong in this module.

  The API contains:
  - `api.vision` — `ImageFinder` (find + `exists` + the lambda control-flow `whileExists`/`ifExists`
    /`untilExists`), `ImageClicker`, `ImageWaiter`, `MatchResult`, `ImageTemplate`.
  - `api.vision.Precision` — `Pixel`'s precision knobs as one value type rather than a bare
    `double`/`int`. A record with named constants (`Precision.EXACT`/`TIGHT`/`DEFAULT`/`LOOSE`) and a
    validating `of(...)`. It is a type because the numbers are unreadable alone — ΔE has no obvious scale,
    and the pixel count is an *area* routinely misread as a width — and because it lets Studio dispatch its
    editor by **type** instead of by a `(method, argIndex)` table that would silently stop firing whenever
    `Pixel` gains an overload. (It was two types, `Tolerance` and `MinPixels`, until they were merged; this
    entry named them long after they were gone.)
  - `api.BotSettings` — the bot's runtime tuning (delays, confidence, compare margin, retry budget, real
    input), seeded from the project's `botmaker-project.properties` on first read. Was `api.vision.ClickConfig`.
  - `api.capture.Screen` (`capture()`), `api.interaction.Mouse`/`Wait`, `api.core.Direction`,
    geometry `api.Point`/`Rect`/`Size`.
  - `api.BotMaker` — console IO. `readX()` prints a SOH-wrapped `BM-INPUT:<type>` marker to stdout
    before blocking on stdin; Studio detects/strips it to show a modal input prompt. Changing that
    marker on one side without the other breaks input prompts.
- **`com.botmaker.sdk.internal.*`** is plumbing, free to rework — and now nearly empty, because most of what
  was in it was not SDK-specific: `opencv`, `capture` (desktop backends), `launch` and the emulator transport
  all live in **shared**, where Studio can reach them too. What is left is genuinely SDK-shaped:
  - `internal/observe/IpcObserver` — it *implements* `api.observe.BotObserver` and consumes
    `MatchEvent`/`ClickEvent`/`Surface`/`Bots`; it is the adapter from SDK observer callbacks onto shared's
    already-shared telemetry wire (`shared.ipc.TelemetryClient`). Moving it would move the SDK types with it.
  - `internal/config/ProjectDefaults` — a thin typed accessor mapping shared's `ProjectProperties` (which owns
    the file, the key names and the parsing) onto `CaptureSource`/`Size`.
  - `internal/ocr/{OcrEngine,OcrNative,OcrPreprocessor}` — the Tesseract stack behind `api.vision.Text`,
    moved here from `com.botmaker.shared.ocr` in 1.2.0. It is internal because a bot only ever *receives*
    a `TextResult`; it never names the engine. `OcrNative` extracts the bundled `tessdata` and delegates the
    OpenCV load to shared's `OpenCvNative.ensureLoaded()`. **The Tess4J / lept4j / bytedeco pins move
    together** — a mismatch throws an undefined-symbol `UnsatisfiedLinkError` on the `getWords` path only, at
    a bot's runtime and never at build time. `pom.xml`'s property block has the version table and
    `OcrEngineNativeTest` is the only guard; read both before bumping anything in that stack.
  (The manual harnesses that were also left behind here — `internal/Main`, `capture/CaptureTest`,
  `capture/ImageDisplay`, `capture/linux/LinuxControllerTest`, `opencv/OpencvTest` — have since been
  deleted; they were dev tools nothing referenced.)

### OpenCV / native loading

The native library is `org.openpnp:opencv` (self-contained — bundles the OS native and loads it via
`nu.pattern.OpenCV.loadLocally()`). **All loading goes through shared's single idempotent loader
`com.botmaker.shared.opencv.OpenCvNative.ensureLoaded()`.** It is invoked from a `static {}` block on the
classes that first touch an `org.opencv` type — `ImageTemplate` (which owns the image `Mat`) and shared's
`OpencvManager` — so every find/match path loads the native before any Mat is created, independent of JVM
class-link order. Do not rely on scattered per-class blocks elsewhere; a class that links an OpenCV type
without a guaranteed-loaded path is how "opencv not loaded" errors return. `ImageFinder.find` deliberately
does **not** catch `Error`s (e.g. `UnsatisfiedLinkError`), so a genuine load failure surfaces instead of
masquerading as "not found".

**The matching engines live in shared** (`shared.opencv`: `OpencvManager`, `ColorMatcher`,
`ResolutionScaler`), because Studio's Magic Wand matches at edit time exactly as a bot does at run time — and
because it collapses three independent copies of the OpenCV loader into one. They work directly on
`org.opencv.core.Mat` and return the raw `RawMatch`/`RawColorMatch` records (plain ints + score, no OpenCV
types); **mapping those onto the public `MatchResult`/`ColorMatch` is the SDK's job**, in `api.vision`. The
OpenCV `Mat` still lives in `ImageTemplate` — the old `Template`/`InternalMatch`/`MatType` wrapper layer has
been collapsed.

Because shared cannot see `api.Size`, the matcher takes the authored resolution as a `java.awt.Dimension`.
`ImageTemplate.authoredSize()` is the single conversion point; the vision call sites go through it rather
than each converting.

### Screen capture

`com.botmaker.shared.capture.ScreenCapture` is the **single** desktop-capture facade, and it now lives in
shared beside per-window capture — the platform knowledge is the same either way, and Studio's picker wants
the same grab. `api.capture.Screen`/`Desktop`/`Monitor` and every `NativeController.captureDesktop()` route
through it; there is one `getVirtualScreenBounds()` (the AWT all-monitor union). See
`../botmaker-shared/CLAUDE.md` for the backend selection (`RobotCapture` vs `SpectacleCapture`) and the
Wayland notes.

(A Swing `ImageDisplay` preview window used to live here for the `internal` dev harnesses; it went with
them. A JFrame was never something the JavaFX Studio would consume.)

### Mouse clicks & the Wayland input limitation

`api.interaction.Mouse.click` routes through `NativeControllerFactory.get()` (Windows → `Clicker`/
`User32 PostMessage`; Linux → `LinuxController` XTest, with an AWT `Robot` fallback). Match
coordinates are absolute: `ImageFinder` adds `Screen.captureOrigin()` (the virtual-screen
origin) so clicks are correct even when a monitor sits left/above the primary.

On Linux the click warps the real cursor, then restores it. **Restore is X11-only:** under native
Wayland the JVM is an **XWayland** client that can *write* the pointer (warp + click work) but
**cannot read the global cursor position** (XQueryPointer / AWT `MouseInfo` return a stale constant
when the cursor isn't over our surface — and the bot has no window). `LinuxController` therefore
skips the restore when `WAYLAND_DISPLAY` is set, leaving the cursor on the target. The Wayland-correct
"click without disturbing the cursor" path is the xdg-desktop-portal **RemoteDesktop** (libei/
PipeWire) interface — deferred; see `ROADMAP.md`.

## Android emulator (`api.emulator`)

The emulator **capability** — the dadb transport (`AdbDevice`) and product discovery (`Platforms`,
`BlueStacks`/`LdPlayer`, `WindowsRegistry`, `EmulatorInstance`) — lives in **shared**
(`com.botmaker.shared.emulator`), because both the SDK (connect at runtime) and Studio (list instances in the
picker) need it, and so a future Studio capture-picker can preview an emulator screen. dadb therefore comes in
transitively via shared — it is **not** a direct SDK dependency.

The SDK owns only the bot-facing facade `api.emulator`: **`Emulator implements CaptureSource`** (wraps a shared
`AdbDevice`; `origin()` is `(0,0)` so a match's coords are already emulator pixels and the whole vision/click
stack works unchanged; `click(Point)` → `adb input tap`) and **`Emulators`** (static discovery over shared's
`Platforms`: `list`/`first`/`named`/`connect`, plus `use()`/`use(String)` connect-and-set-`Source` shorthands).
