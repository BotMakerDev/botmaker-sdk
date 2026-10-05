# Declaration

- **`SdkPlugin` is one declaration** on the contract's `DeclaredPlugin`:
  `StudioPlugin.id(ID).named(NAME).types(…).parts(…).editors(…).values(…).toolbar(…).runOverlay(…)
  .overlay(…).assistant(…).trial(Bot::trial).recorded(…)`. The surfaces are suppliers, so constructing it links
  nothing (`SdkPluginHeadlessTest`: a headless host constructs it too, and `javafx-controls`, the toolkit,
  Javalin and ZXing are `optional` here). It adds only `projectClosing()`, which releases the Remote Pilot.
- **The overlay editor's part is `plugin/overlay/SdkOverlay`.**
  - Targets come from the `ActivityBody` type.
  - The watched screen is `Sdk.captureSource()`.
  - The probes are `SdkProbes`, which use `ImageFinder.bestMatch` and the project's confidence. A click's
    probe is `Probe.acting`.
  - The tools are `SdkTools`, with `PictureCuts` for the save.
- **The assistant's tools are `plugin/assist/SdkAssist`.**
  - Writes hop to the FX thread through `FxCall`, the only class there that links JavaFX.
  - Flow edits are `FlowEdits`, which 🔀 Activity Flow's save also uses.
- **A type is declared once, in `plugin/types/SdkTypes`**, as a contract `PluginType.value(X.class)` whose
  steps ask for the fresh value, the editor (`() -> X::editor`) and the Java (`writtenAs(Owner::factory,
  X::part, …)`, `writtenAsRecord()`, `writtenAsEach`, `writtenAsConstant()`, or `filledBy(Vision::lastMatch)`
  for a result nobody edits). A part never picked on its own is `ComponentType.part(X.class).writtenAs(…)` in
  `SdkTypes.PARTS`; a `Flow` is five parts (`FlowTypes`), a `CaptureSource` six calls (`CaptureTypes`).
  Adding a type is one constant there and nothing else.
- **Factories are method references, with two string exceptions that stay**:
  - `CaptureTypes`' `REGION` and `REGION_CHAIN` name `CaptureSource.region` through `Ref.member`, because the static
    `region(src, r)` and the instance `src.region(r)` share a name and an arity and javac cannot reference
    either, and both are what a bot writes — a new-named factory would still leave this one.
  - `FlowTypes`' activity body is the text `Collect::body`, written through the host's own source-leaf path
    (Studio's `ValueWriter.ofClass`), which parses it into a tree; renames already follow bindings in the real
    `Sdk.java`. A contract `MethodName` type was considered and declined (2026-09-28): one contract type,
    grammar changes and a flow-editor rewrite to save about forty lines.
  Neither is to be proposed for removal again without new facts.
- **Toolbar buttons are `SdkToolbarItems`**: one `ToolbarItem.id(…)` constant per button, each pressing its
  feature's own `open(ActionContext)` (`RemotePilotUi.open`, `CaptureTemplates.open`, `SourcePicker.choose`,
  `CaptureValue.pointHere`, …). Nothing holds a project in a static: a feature takes `StudioServices` from
  the context it is handed.
