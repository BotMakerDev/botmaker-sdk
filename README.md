# botmaker-sdk

The **BotMaker SDK** is one jar with two jobs:

- **the runtime** a bot compiles against — find a picture on the screen, read text, click, type, wait, and
  walk a flow of activities;
- **plugin #1** of [BotMaker Studio](https://github.com/BotMakerDev/botmaker-studio) — the Activity Flow
  canvas, the picture tools, the capture source and launch target, the run overlay and the Remote Pilot.

Studio bundles no plugin and never depends on this jar. A project that declares the SDK in its `pom.xml`
gets both halves: its bot runs on the first, and Studio loads the second from that project's classpath.

## Using it

Start from the [gamebot template](https://github.com/BotMakerDev/botmaker-gamebot) (**Create New Project ▸
Gamebot** in Studio), or add it to a project's pom:

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
scope; the gamebot template's `pom.xml` lists them with the versions to use. A bot links none of them at run
time. Do not declare `botmaker-plugin-toolkit` or `botmaker-plugin-basics` beside the SDK: it brings both,
and a direct entry wins by Maven's nearest-wins rule at a version the SDK was not built against.

## The runtime: what a bot calls

```java
public static void main(String[] args) {
    // walk the flow the bot's @Params pick, with the home, setup and capture source read off Sdk.java
    Bot.runFlow(Sdk.class);
}

public static Activities.Collect body() {           // one activity: do one unit of work, report an outcome
    if (!ImageClicker.click(Pictures.COLLECT)) {
        return Activities.Collect.NOTHING_LEFT;
    }
    Wait.between(Duration.ofMillis(600), Duration.ofMillis(1200));
    return Activities.Collect.NEXT;
}
```

| Package (`com.botmaker.sdk.api.*`) | What it holds |
|---|---|
| `bot` | `Bot.runFlow`, `Bot.stop`; `BotSettings`; `PopupGuard`; `ActivitySwitch`; the `@SdkValue` marker |
| `flow` | `Flow` — which activity starts, which method each runs, where each outcome leads, how the bot gets home |
| `vision` | `ImageFinder`, `ImageClicker`, `ImageWaiter` over `ImageTemplate` pictures (OpenCV matching); `ColorMatch`, `Pixel` |
| `text` | `Text` — OCR (Tesseract): read, find, wait for text on screen |
| `input` | `Mouse`, `Keyboard`, `Key`, `Combo` |
| `capture` | `CaptureSource` — the whole desktop, a monitor, a window, an emulator, or a region of one |
| `time`, `random`, `sound`, `console`, `geometry` | `Wait`, `Chance`, `Sound`, `Ask`/`Debug`, `Point`/`Rect`/`Size` |

A bot's facts are Java in its own project:

- **activities** are `public static` methods, each returning a constant of its own enum in
  `plugins/sdk/Activities.java` — a body cannot report another activity's outcome;
- **the flow, capture source and settings** are `@SdkValue` methods in `plugins/sdk/Sdk.java`, each
  `public static T id() { return <expr>; }`; the plugin hands Studio a value and Studio rewrites only that
  expression, so comments and helpers around it survive;
- **pictures** are constants in `plugins/sdk/Pictures.java`, one per file under `src/main/resources/images`;
- **parameters** are `@Param` static fields (the annotation is the plugin contract's, which the SDK brings).
  The run's own three — `flow`, `doSetup`, `skip` in `Sdk.java` — also carry `@SdkValue(…)`: the SDK declared
  them, so Studio writes one back if it goes missing and lets the runner change only its value.

The canvas and the code are joined by method reference (`Collect::body`), so a renamed or deleted activity is
a compile error, never a card that silently does nothing.

## Plugin #1: what it adds to Studio

| Toolbar | What it does |
|---|---|
| 🔀 **Activity Flow** | the activity cards and the arrows between their outcomes — `Sdk.flow()` and `Activities.java` |
| ✂ **Capture Templates** | draw regions over the game and save them as pictures |
| 🖼 **Manage Pictures** | rename, retag, replace, delete, import and export pictures; a rename moves every use |
| the game button | which game this bot plays on this computer — from Steam, Heroic, Faugus, Lutris, Waydroid or the app menu — and start it without running the bot |
| 📋 **Project Setup** | what the project still needs before it can run: something to launch, something to capture, the pictures |
| 🎯 **Capture Source** | what the bot looks at — a monitor, a window, an emulator instance |
| ⚙ **Bot Settings** | how the bot clicks and looks, and where it runs — this desktop, a private display, or a game VM |
| 🎮 **Pilot** | stream the run to a phone or browser, start and stop it, take over the input |
| ⌖ **Point bot here**, ✂ **Picture of this** | on the overlay editor's row: make the window under the overlay the capture source, or cut a picture out of it |

It also declares the editors for its own types (pictures, points, regions, keys, colours by eyedropper, OCR
options), the run overlay (the current activity and the last match, over the game), tools for Studio's
overlay editor and Assistant, and ▶ Try (`Bot.trial`), which runs one statement against the screen as it is.

The native work underneath — windows, input, emulators and adb, installed-game discovery, game VMs — is
[`botmaker-shared`](https://github.com/BotMakerDev/botmaker-shared); private displays are
[`botmaker-session`](https://github.com/BotMakerDev/botmaker-session).

## Building

```bash
mvn test                          # here
mvn -pl botmaker-sdk -am install  # from the umbrella root: the SDK and every module it needs, at main's -SNAPSHOT
```

A change to `api.*` is done only when both templates build against it, from the umbrella root:

```bash
mvn -Ptemplates -Dbotmaker.sdk.version=$(mvn -q -f botmaker-sdk/pom.xml help:evaluate \
    -Dexpression=project.version -DforceStdout) install
```

`api.*` may break between releases: Studio's plugin upgrade repairs a bot's calls. The package rules
(`api` / `internal` / `plugin`) are in [`docs/architecture/`](docs/architecture) and `CLAUDE.md`.

## Releasing

From the umbrella only, with `--sdk <version>` plus the downstream it forces (studio); `/release-check` names
it. Add `--gamebot` to move the template's pin to the new SDK.
