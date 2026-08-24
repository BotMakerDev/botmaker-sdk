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
- **`@Scaffolding`** marks the members Studio writes into generated files, so a release that moves one says so
  before you commit to the upgrade instead of failing half-way through it.
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
