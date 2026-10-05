# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The **BotMaker SDK** is the runtime library that user bots compile against, **and plugin #1** (`SdkPlugin`).
The sibling **botmaker-studio** app (`../botmaker-studio`) loads it as a plugin off an open project's
classpath and never depends on it. The SDK depends on **botmaker-shared** (`../botmaker-shared`,
cross-platform native window plumbing), **botmaker-session**, the contract (`botmaker-studio-api`, at
`compile` so a bot has `@Param`/`@Managed`), **botmaker-plugin-basics** and, for its plugin half only,
**botmaker-plugin-toolkit**.

This file states what is true now. How it got here — the generated sources, `activities.json`, `Wire`,
`capture.json`, the seeds, the codecs, and every dated section this file carried until 2026-09-28 — is in
`../docs/refactor/31-umbrella-history.md` (*SDK*), and the old text itself is `git show 8d2cfe4:CLAUDE.md` in
this repository. Search there before re-deriving a decision.

The architecture is in `docs/architecture/`, one file per section (moved there unchanged on 2026-10-05).

## Read before touching

| Touching | Read (`docs/architecture/`) |
|---|---|
| what goes in `api` vs `internal` vs `plugin`, `@Palette`, `@Records`, `@ReplacedBy` | `api-vs-internal.md` |
| a class in `api.*` (`vision`, `text`, `bot`, `flow`, `capture`, `console.Ask`, `BotSettings`, activities/outcomes) | `what-the-api-holds.md` |
| `internal/*` (`observe`, `config`, `ocr`, `trace`, …) | `internal.md` |
| `Sdk.java`, `Pictures.java`, `SdkValues` | `plugin-values.md` |
| `SdkPlugin`, `SdkTypes`, `FlowTypes`, `CaptureTypes`, `SdkToolbarItems` | `declaration.md` |
| `plugin/editors`, a slot editor, a screen pick | `editors.md` |
| `EditorFrame`, `ScreenOverlay`, `OverlayStage`, `TemplateLibrary`, the Remote Pilot | `pixels-windows-pictures.md` |
| an `org.opencv` type, `ImageTemplate`, matching | `opencv.md` |
| `api.capture`, desktop capture, `Mouse.click` under Wayland | `screen-capture.md`, `../docs/display-pipeline.md` |
| `internal.emulator` (`Emulator`, `Emulators`) | `emulator.md` |

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
mvn install        # Install to ~/.m2 at the coordinate a bot resolves (see Local dev)
```

There are no `main`-method entry points. The module is a library: everything under `src/main` is
reachable from a generated bot, and everything that verifies it is JUnit under `src/test`. A new diagnostic
goes in `src/test` with the JUnit the rest of the module uses.

## Publishing

The SDK is consumed by **bot projects** (not by Studio), via JitPack as
`com.github.LiQiyeDev:botmaker-sdk:<tag>`. JitPack builds each git tag on demand and serves it under that
coordinate regardless of this pom's `groupId`/`version` (so the pom `version` is cosmetic). **The maintainer
owns the SDK → JitPack publish — don't push or publish the SDK yourself;** releases are cut from the
umbrella with `../release.sh`. The whole `CHANGELOG.md` is copied into the jar as
`META-INF/botmaker/whats-new.md`.

### Local dev (test SDK changes without pushing a tag)

A bot pins `com.github.LiQiyeDev:botmaker-sdk:<version>`, and `~/.m2` is checked before JitPack. Install
the SDK and what it builds on at `0.0.0-SNAPSHOT` from the umbrella root:

```bash
mvn -pl botmaker-sdk -am install     # shared, session, contract, toolkit, basics, then the SDK
```

Re-run it after each SDK edit; a bot pinned to `0.0.0-SNAPSHOT` resolves the fresh jar on its next
classpath resolve, and Studio's **Project ▸ Plugins & Libraries ▸ Reload plugins** re-opens the plugin loader over it. A dev-run
Studio (`AppVersion.isDevBuild()`, no jar manifest) lists local `*-SNAPSHOT` SDK builds first in its version
dropdowns, labelled `(local build)`; a packaged Studio never shows them.

## Code Style

Prefer **functional OOP**: minimize mutable class fields to avoid state-related bugs. Favor immutable
values (`record`s like `MatchResult`, `RawMatch`, `Point`/`Rect`/`Size`) and pure transformations;
pass dependencies in via parameters rather than holding mutable fields or static/singleton state.

**The three geometry types are records of `int`s, and both halves of that are deliberate.** They are
immutable, so `equals` is value equality and a getter hands back its field rather than a defensive copy —
don't reintroduce one. They are `int` because every producer is a pixel and every consumer is an input event
the native layer delivers at a whole pixel. **A fraction is rounded where it is created, never carried** —
`Rect.center`, `MatchResult.center`, `Pixel`'s centre of mass, `Mouse.drag`'s interpolation.
Keep side effects (screen capture, native library loading, process launching) at the edges. The
static facades (`ImageFinder`, `ImageClicker`, …) are stateless dispatchers.

## Rules

- **A type a bot can only receive, never write down, is `internal`**; the package tree is `api` / `internal`
  / `plugin` and nothing else (`PluginLayersTest`, `../docs/refactor/34-plugin-package-tree.md`).
- **No `api` signature names an `internal`, shared or OpenCV type.** `api.*` breaks freely; delete outright
  (`api-vs-internal.md`).
- **The palette is discovered, never listed**: `@Palette` marks what is offered, value types carry nothing.
- **No class under `plugin/` reads or writes Java text**; an editor reads the value and hands one back.
- **Nothing is stored outside the bot's Java (`Sdk.java`, `Pictures.java`) and run properties; no migration,
  by rule** (`plugin-values.md`).
- **A type is declared once, in `plugin/types/SdkTypes`**; the two string factories in `declaration.md` stay.
- **All OpenCV loading goes through shared's `OpenCvNative.ensureLoaded()`** (`opencv.md`).
- **The Tess4J / lept4j / bytedeco pins move together** (`internal.md`, `pom.xml`).
- **A debug line never writes its own `[Name]`, and a line that only echoes a public call is not written**
  (`internal.md`, `../docs/refactor/40-run-trace.md`).
