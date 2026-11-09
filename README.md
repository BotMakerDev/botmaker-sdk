# botmaker-sdk

The **BotMaker SDK** is one jar with two jobs:

- **the runtime** a bot compiles against — find a picture on the screen, read text, click, type, wait, and
  run a plan: walk the game's screens and press what serves the goals that matter most;
- **plugin #1** of [BotMaker Studio](https://github.com/BotMakerDev/botmaker-studio) — the 🗺 Screens window
  with its rail of views (the map, 🎯 Goals, 🖼 Pictures), the picture tools, the capture source and launch target, the run overlay and the Remote
  Pilot.

Studio bundles no plugin and never depends on this jar. A project that declares the SDK in its `pom.xml`
gets both halves: its bot runs on the first, and Studio loads the second from that project's classpath.

## Using it

Tick it in Studio's **New Project** (a blank project, or a published bot that already uses it), or add it to a
project's pom:

```xml
<repositories>
    <repository>
        <id>jitpack</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.BotMakerDev</groupId>
    <artifactId>botmaker-sdk</artifactId>
    <version><!-- latest tag: https://github.com/BotMakerDev/botmaker-sdk/tags --></version>
</dependency>
```

The plugin half also needs `javafx-controls`, `javafx-graphics`, `javalin` and zxing `core` at `provided`
scope; Studio writes them into a new project's `pom.xml` with the versions to use. A bot links none of them at
run time. Do not declare `botmaker-plugin-toolkit` or `botmaker-plugin-basics` beside the SDK: it brings both,
and a direct entry wins by Maven's nearest-wins rule at a version the SDK was not built against.

## The runtime: what a bot calls

```java
public static void main(String[] args) {
    // run the plan the bot's plan parameter picks, with the home, setup and capture source read off Sdk.java
    Bot.run(Sdk.class);
}

// Goals.java: what the bot wants, each a check
public static final Goal CLAIMED_TODAY = Goal.done(() -> Screens.HOME.claim).since(Reset.daily(LocalTime.of(5, 0)));

// Plans.java: tiers of goals, the first tier first
public static final Plan DAILY = Plan.of(Plan.tier(Goals.CLAIMED_TODAY), Plan.tier(Goals.ENOUGH_GOLD));

// Screens.java: on the screen where it is pressed, the action that serves a goal
public final Do claim = Do.press(collect).serves(Goals.CLAIMED_TODAY);
```

| Package (`com.botmaker.sdk.api.*`) | What it holds |
|---|---|
| `bot` | `Bot.run`, `Bot.stop`; `Run` (the home and the setup); `BotSettings`; `PopupGuard`; the `@SdkValue` marker |
| `screens` | the map: `Screen`, `Button`, `Way`, `Readout`, `Do` (an action and the goal it serves), `Custom` (a bot method run on a screen), `Navigate` |
| `plan` | `Goal`, `Need`, `Plan` (tiers of goals), `Reset` (when a daily comes due again) |
| `vision` | `ImageFinder`, `ImageClicker`, `ImageWaiter` over `ImageTemplate` pictures (OpenCV matching); `ColorMatch`, `Pixel` |
| `text` | `Text` — OCR (Tesseract): read, find, wait for text on screen |
| `input` | `Mouse`, `Keyboard`, `Key`, `Combo` |
| `capture` | `CaptureSource` — the whole desktop, a monitor, a window, an emulator, or a region of one |
| `time`, `random`, `sound`, `console`, `geometry` | `Wait`, `Chance`, `Sound`, `Ask`/`Debug`, `Point`/`Rect`/`Size` |

A bot's facts are Java in its own project:

The SDK's files are in the bot's `plugins/com_github_botmakerdev/botmaker_sdk/` (its coordinate; `plugins/sdk/`
before 2026-10-27, still found):

- **screens** are classes in `Screens.java`, their buttons, readouts and `Do` actions fields of them;
- **goals** are constants in `Goals.java`, **plans** constants in `Plans.java`;
- **the run, capture source and settings** are `@SdkValue` methods in `Sdk.java`, each
  `public static T id() { return <expr>; }`; the plugin hands Studio a value and Studio rewrites only that
  expression, so comments and helpers around it survive;
- **pictures** are constants in `Pictures.java`, one per file under `src/main/resources/images`;
- **parameters** are methods of `@Param` groups in the bot's `params` package (the annotation is the plugin
  contract's, which the SDK brings). The run's own two — `plan` and `doSetup`, in
  `params/com_github_botmakerdev/botmaker_sdk/Running.java` — also carry `@SdkValue(…)`: the SDK declared them,
  so Studio writes one back if it goes missing.

The map and the code are joined by method reference (`Custom.of(MyBot::solvePuzzle)`), so a renamed or deleted
method is a compile error, never a button that silently does nothing.

## Plugin #1: what it adds to Studio

| Toolbar | What it does |
|---|---|
| 🗺 **Screens** | the game's screens, their buttons and the ways between them, live over the game — `Screens.java`; its left rail shows 🎯 **Goals** (the plans' tiers, each goal and the actions that serve it — `Goals.java` and `Plans.java`) and 🖼 **Pictures** (rename, retag, replace, delete, import and export pictures; a rename moves every use) |
| the game button | which game this bot plays on this computer — from Steam, Heroic, Faugus, Lutris, Waydroid or the app menu — and start it without running the bot |
| 🎮 **Pilot** | stream the run to a phone or browser, start and stop it, take over the input |
| ⌖ **Point bot here**, ✂ **Picture of this** | on the overlay editor's row: make the window under the overlay the capture source, or cut a picture out of it |

It also declares the editors for its own types (pictures, points, regions, keys, colours by eyedropper, OCR
options), the run overlay (the goal being served and the last match, over the game), tools for Studio's
overlay editor and Assistant, and ▶ Try (`Bot.trial`), which runs one statement against the screen as it is.

The native work underneath — windows, input, emulators and adb, installed-game discovery, game VMs — is
[`botmaker-shared`](https://github.com/BotMakerDev/botmaker-shared); private displays are
[`botmaker-session`](https://github.com/BotMakerDev/botmaker-session).

## Building

```bash
mvn test                          # here
mvn -pl botmaker-sdk -am install  # from the umbrella root: the SDK and every module it needs, at main's -SNAPSHOT
```

A change to `api.*` is done only when the sample bot (`src/test/resources/samplebot`) still compiles against
it:

```bash
mvn test -Dtest=SampleBotCompileTest
```

`api.*` may break between releases: Studio's plugin upgrade repairs a bot's calls. The package rules
(`api` / `internal` / `plugin`) are in [`docs/architecture/`](docs/architecture) and `CLAUDE.md`.

## Releasing

From the umbrella only, with `--sdk <version>` plus the downstream it forces (studio); `/release-check` names
it. `SampleBotCompileTest` compiles a bot (`src/test/resources/samplebot`) against the SDK, so an `api.*`
change that breaks bots fails the SDK's own tests.
