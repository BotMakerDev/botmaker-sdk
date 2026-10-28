# Declaration

- **`SdkPlugin` is one declaration** on the contract's `DeclaredPlugin`:
  `StudioPlugin.declare().types(SdkTypes.class, FlowTypes.class, …).editors(…).values(SdkValues.class)
  .toolbar(…).runOverlay(…).overlay(…).assistant(…).trial(Bot::trial).readiness(…)`. It names no id and no
  name: the pom's `botmaker.plugin` property names the class, its coordinate is the plugin's id and its
  `<name>` (*BotMaker SDK*) what Studio shows (2026-10-27). Every surface is named by its holder class
  (2026-10-28), which implements the step's holder interface and lists what it gives (`SdkTypes.types()`,
  `SdkEditors.editors()`, `SdkToolbarItems.toolbar()`, `SdkAssistant.tools()`, …); the host builds it only when it
  asks, so constructing the plugin links nothing (`SdkPluginHeadlessTest`: a headless host constructs it too, and
  `javafx-controls`, the toolkit, Javalin and ZXing are `optional` here). `onProjectOpened`/`onProjectClosing`
  bind the game button, readiness, the VM keeper and `SdkLog` (the plugin half's warnings, to
  `StudioServices.log()`) to the open project, and release the Remote Pilot. `SdkPluginSurfaceTest` runs the
  toolkit's `PluginTests.assertDeclared` and `assertListsEveryField` over every holder.
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
  X::part, …)`, `writtenAsRecord()`, `writtenAsEach` or `writtenAsConstant()`). A result nobody edits is
  declared by the host: its method carries `@Fills(label = …)` (`Vision.lastMatch()`). A part never picked on its own is `ComponentType.part(X.class).writtenAs(…)`, a
  field of a holder `SdkPlugin` names; a `Flow` is five parts (`FlowTypes`), a `CaptureSource` six calls
  (`CaptureTypes`). The host takes what each holder's `types()` and `components()` list, in that order, which
  is the menu's order and the order two shapes of one class are tried. Adding a type is one constant, listed.
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
  the context it is handed. The bar's nine read in one flat order (`onBar(10)` to `onBar(90)`, 2026-10-28, when
  toolbar groups went: Studio gives the plugin a tab of its own); the overlay's two are `inOverlay`.
