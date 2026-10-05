# Public API vs internal plumbing

- **The line between the two is one question: can a bot *write the name down*?** A type it can only ever
  *receive* — from a factory, as an event, as a return value — belongs in `internal`, however public its
  methods are. The `CaptureSource` implementations (`Desktop`, `Monitor`, `NamedWindow`, `SessionSource`)
  only ever arrive from `CaptureSource.desktop()/monitor()/window()` and `Source.current()`, and the
  observation stack (`Bots`, `BotObserver`, `Surface`, `ClickEvent`, `MatchEvent`, `SwipeEvent`) only ever
  reached `internal.observe.IpcObserver`, so all of them are `internal`. **The palette mirrors this**: a
  class that leaves `api` leaves the palette.

- **The palette is discovered, never listed.** `@Palette` on an `api` class = **offered** (its own menu
  entry). A value type carries **no** annotation: what an offered call takes or returns from this jar is
  catalogued by reach (the recognition set — imports, "does `Point` mean ours or `java.awt`'s"), and plumbing
  no offered call reaches (`Debug`, `Watchdog`, `Session`) is not catalogued at all (2026-09-30; type-level
  `@Hidden` and `order` are gone, facades are alphabetical). `@Hidden` is for a member. The host finds every
  `@Palette` class in this jar (`botmaker-plugin-host`'s `Palettes`, since
  `SdkPlugin` declares no catalog); members are discovered in the class file's own declaration order
  (`SourceOrder`, alphabetical on any failure). **Constructors are not catalogued**: a palette entry inserts
  a *call*.

- **Recording is the host's; the SDK annotates.** `@Records(Gesture, rank)` on a public static `api` method
  (`Mouse.click`, `Keyboard.type/tap/combo`, `Wait.time`, `ImageClicker.click` at rank 10, `ImageWaiter.waitFor`
  for `AWAIT`, …) says which call writes a gesture. The host fills parameters by type; the one type it cannot
  fill is `plugin/types/PictureAt`, a `RecordedValue` (the project picture under the click). This module
  writes no Java for a recording.

- **No `api` signature may name a type a bot cannot write**: not `internal` (`PluginLayersTest`, by
  reflection), and not `botmaker-shared` or OpenCV, which are freely breakable. `ImageTemplate.getMat()`
  (`org.opencv.core.Mat`) is package-private for this reason, a window handle is reached through
  **`internal.capture.WindowBacked`** (`WindowBacked.of(source)`), an emulator source is
  `CaptureSource.emulator(name)`, and OCR lives here rather than in shared (`api.text`; `internal.ocr` the
  engine). **`../docs/refactor/22-api-audit.md` is the record** of the shared audit.

- **`com.botmaker.sdk.api.*` is what a bot writes, in packages named by what a bot does** (2026-10-01):
  `api.bot`, `api.flow`, `api.capture`, `api.input` (`Mouse`, `MouseButton`, `Keyboard`, `Key`, `Combo`,
  `KeySequence`), `api.time` (`Wait`, `Time`), `api.vision`, `api.text` (OCR), `api.geometry` (`Point`,
  `Rect`, `Size`), `api.sound`, `api.random` (`Chance`), `api.console` (`Debug`, `Ask`) —
  `PluginLayersTest` holds the list.
  **The `api` root holds no classes**. Plumbing the plugin half needs and a bot never names is public in
  `internal`: `internal.launch` (`Game`, `Target`, `LaunchTarget`), `internal.emulator`, `internal.bot`
  (`Session`, `Watchdog`, `BotStuckException`), `internal.flow.Flows`, `internal.capture.Window`.

- **The package tree is `api` / `internal` / `plugin`, and nothing else** (`../docs/refactor/34-plugin-package-tree.md`,
  enforced by `plugin/PluginLayersTest`). `api` and `internal` are what a bot links: no JavaFX, no toolkit, no
  `com.botmaker.sdk.plugin` name. `plugin/` is the Studio half: `SdkPlugin` (the declaration),
  `SdkToolbarItems`, `types/` (`SdkTypes`, `FlowTypes`, `CaptureTypes`, `SettingsTypes`, `PictureAt`),
  `editors/`, and one package per feature — `flow`, `pictures`, `screen`, `source`, `settings`, `pilot`
  (+ `pilot/ui`), `emulator`, `launch`, `setup`. **No class under `plugin/` reads or writes Java text**: an
  editor reads the value the host hands it and hands one back.

- **`api.*` breaks freely (2026-10-01, the maintainer's ruling).** Studio's refactor-safe upgrade drops a
  statement whose method is gone, so the never-delete rule, its japicmp gate and the "2.0.0 is the one
  sanctioned break" wording are retired. Delete outright; no `@Deprecated` is owed.

- **A rename may carry `@ReplacedBy`** (contract's `com.botmaker.plugin.api.meta`), a courtesy Studio's
  upgrade follows: targets `fqn`, `fqn#member` or `fqn#<init>`; an empty value is an explicit "nothing takes
  my place"; several values are a split, with a parallel `whens()` sentence per candidate; `note` is shown
  verbatim; `behaviourChanged = true` forces a review mark and needs a `note`. **`ApiPointersTest`** is the gate
  (four rules: every deprecated element has a pointer, every target resolves, a behaviour change has its
  note, a split says when). It is not a coverage rule. `first(…)` filters with `directOnly()`, because these
  annotations annotate each other.
