# What `internal` holds

Plumbing, free to rework — and small, because most of what is not SDK-specific (desktop capture backends,
launch, the emulator transport, OpenCV matching) lives in **shared**, where Studio can reach it too:

- `internal/observe` — the observation stack and `IpcObserver`, the adapter from observer callbacks onto
  shared's telemetry wire (`shared.ipc.TelemetryClient`).
- `internal/config/ProjectDefaults` — what a running bot was told from outside its code: the
  `botmaker.launch.target` run property (set per machine through `Runs.setProperty`,
  `plugin/settings/LaunchTargetValue`) and the session half of its `BotSettings`. No file is read.
- `internal/ocr/{OcrEngine,OcrNative,OcrPreprocessor}` — the Tesseract stack behind `api.vision.Text`.
  `OcrNative` extracts the bundled `tessdata` and delegates the OpenCV load to shared's
  `OpenCvNative.ensureLoaded()`. **The Tess4J / lept4j / bytedeco pins move together** — a mismatch throws an
  undefined-symbol `UnsatisfiedLinkError` on the `getWords` path only, at a bot's runtime and never at build
  time. `pom.xml`'s property block has the version table and `OcrEngineNativeTest` is the only guard; read
  both before bumping anything in that stack.
- `internal/flow`, `internal/bot`, `internal/capture` (the `CaptureSource` implementations, `RegionSource`,
  `CurrentSource`, `WindowBacked`), `internal/session`, `internal/vision` (`TemplateNames`, the `img:`
  prefix), `internal/trace`, `internal/sound`.
- `internal/trace` — `Trace` (collapsed runs, located lines) and `TraceSources`: **a debug line never writes its
  own `[Name]`**. `Debug.log("…")` is printed and traced under the calling top-level class's simple name, and
  only that (`@TraceSource` deleted 2026-09-30). Only a class whose lines need two names (`BotSettings`,
  `FlowWalker`) spells one. **A line that only echoes a public call is not written**: Studio's trace agent
  writes one per call a bot makes into an offered class, so `Mouse`, `Wait` and `Keyboard` write none of their
  own; a line adds what the call's arguments and result cannot say (the match score, the window keys went to,
  a collapsed run). `Debug.error`/`Diag.error` print and trace whatever the debug switch says.
  `../docs/refactor/40-run-trace.md`.
