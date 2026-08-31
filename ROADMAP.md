# ROADMAP

A running history of features and refactors for future Claude Code sessions. **Append here whenever
you add a feature or refactor** (this is required — see `CLAUDE.md` › Planning).

Format: newest first. Each dated entry has a **Done** list (what shipped) and, when relevant, updates
to **Deferred / next** (intentionally left for later, with enough context to pick up cold).

---

## 2026-08-31 — Capture Templates is a toolbar item

**Done**

- **`internal/plugin/capture/CaptureTemplates`** — Studio's `OverlayTemplateCapture`, and the second whole
  feature to reach the bar as a `ToolbarItem` after the pilot. `SdkPlugin.toolbarItems()` contributes it into
  `ToolbarGroup.TOOLS` at order 20, the slot Studio's own ✂ Templates vacated. Everything behind the button
  is this plugin's: the capture target and the capture size out of `capture.json`, the pixels through
  `botmaker-shared`, an `ImageTemplate` into the picture folder. **Nothing was added to the contract.**
- **`EditorFrame` grew the two components an overlay needs** — `bounds` (where the pixels are, so a
  rubber-band surface can be placed and a crop mapped back) and `onScreen` (false only for an emulator,
  whose frame arrives over ADB and is nowhere on the display; a transparent surface over one would show the
  host window while the crop came from the ADB frame). A pixel editor ignores both.
- **A second `grabAsync`, not a flag**, and the difference belongs at the call site: a pixel editor samples
  the target *as it is*, because raising a game to read one colour rearranges the user's screen for nothing;
  a capture raises the window and snaps it to the project's size first, because what it writes becomes a
  picture the bot matches against. Both are no-ops for a target that is not a window.
- **The Wayland fallback came across with it.** A blank per-window native grab falls through to a
  whole-desktop capture cropped to the window's bounds — without it every capture on a native Wayland
  session reads as "the target produced a blank frame" while the window is plainly on screen.
- **`OverlayStage` gained `bar` and `installDrag`**, the ownerless, unthemed, draggable mini-toolbar Studio's
  `OverlayToolbars.show` used to build. Studio's class is a two-line seam over this one now.

**Deferred / next**

- **The suggested tag is gone from this flow**, and it is the one thing the move cost. Studio's menu entry
  passed the open activity's tag, so pictures captured while an activity was open were filed under it by
  default. *Which file the editor has open* is host state with no member on the contract, and growing one is
  what the stop condition exists to refuse. The tag menu is still on the naming dialog, so this is a default
  rather than a capability — but it is worth revisiting if a second plugin ever wants the same thing.

---

## 2026-08-31 — the size pictures are captured at is this plugin's

**Done**

- **`CaptureModel` gained a third component, `reference`** — the capture resolution, in `capture.json`
  beside the targets. It was `StudioProjectSettings.Resolution` in the editor's `settings.json`, and the
  maintainer's framing is the whole argument: *the reference resolution is a property of the SDK, not of
  Studio*. It describes the pictures — every one carries it in its sidecar and the matcher rescales by it —
  so the plugin that captures and matches them has to be able to read it. A resolution stored beside the
  editor's window layout is one the capture overlay cannot reach once that overlay is a plugin's.
- **`CaptureModel.Resolution` is the one vocabulary now.** Studio's nested record is deleted and its ~25
  sites retype onto this one — the same move the four capture-target record shapes made on 2026-08-30, and
  for the same reason: a size is a size on both sides of the boundary.
- **A zero or negative size reads as none**, normalised in the compact constructor. It can only come from a
  hand-edited file, and a project must still open.

**Deferred / next**

- **The overlay still needs bounds and an on-screen flag.** With the resolution readable, what `EditorFrame`
  is still missing for *Capture Templates* is where the target is on screen and whether its pixels are there
  at all (an emulator's are not). That is the last thing between the overlay and a `ToolbarItem`.

---

## 2026-08-31 — naming a captured picture, and the tag it gets

**Done**

- **`internal/plugin/capture/TemplateNaming`** — the naming step for a freshly captured picture, in both its
  forms: one crop (a thumbnail, a name field, a tag picker) and a whole batch (one row per crop, with a
  Discard toggle and a *Tag them all as* control). It was two classes in Studio, `ImageTemplatePicker`'s
  private `prompt` loop and `BatchTemplateNamingDialog`, which enforced the same three refusals apart and
  had already drifted: the single form had **no tag field at all**, so a picture captured on its own could
  only be filed later from the resource manager. One `nameProblem` now answers for both.
- **`internal/plugin/capture/TagPicker`** — Studio's `TagPicklist`, over `StudioServices` and this plugin's
  own `TagCatalog`. A tag is a tag *of a picture*, and a picture is `ImageTemplate`'s concept; the catalog is
  read out of the manifest under `resourcesDir()`, and nothing about it needs the host beyond knowing which
  project is open.
- **Nothing was added to the contract.** Theming is `services.theme()`, the thumbnails are
  `services.capture().toFxImage`, and the rest is files.

**Deferred / next**

- **`OverlayTemplateCapture` itself has not moved**, and it is what becomes a `ToolbarItem`. It still needs
  the host's `TargetCapture` for three things this plugin has no equivalent of yet: the target's **bounds**
  (to place the rubber-band surface), whether the pixels are really on screen (to decide a backdrop), and
  snapping a window to the project's reference resolution before each grab. `EditorFrame` answers the frame
  and the label but not those, so it is what grows next.

---

## 2026-08-31 — the picture editor, and three dispatch sites rather than two

**Done**

- **`internal/plugin/editors/TemplateEditors`** — `ImageTemplate` in a canvas slot, in a Parameters row, and
  as the tile beside a declared choice. Registered with the contract's new three-argument `SlotEditor.of`,
  whose third argument is `preview`.
- **Three of Studio's arms deleted, not two.** The `PickerRegistry` entry, `ValueEditors`' `IMAGE_TEMPLATE`
  case with its `TemplateChip`, and `optionGraphic`'s `IMAGE_TEMPLATE` arm. The third had no contract hook
  and is what forced `SlotEditor.preview`; Studio's `previewFromPlugin` is the host side of it.
- **The reader was rebuilt off the syntax tree**, as every ported editor's has been: the constructor's one
  argument read with `Slots.arguments`/`stringLiteral`. It accepts a fully-qualified constructor and any
  folder in the path, and answers *no picture* for a variable, a constant or a call — a reference the editor
  cannot represent and must not overwrite. `TemplateEditorTest`, 9 tests, holds those cases. 542 SDK tests.

**Deferred / next** *(the two runs landed later the same day — see below; what is left is the group slot)*

- **`ImageTemplateGroup` has not moved**, and its `PickerRegistry` entry stays for now. Its chip row spans a
  **run** of arguments rather than one slot — that is what `SlotRun` was added for — and moving it needs
  `HostSlotContext.run()` implemented at the three places Studio composes such a row: the group slot, the
  image varargs tail in `MethodInvocationBlock`, and the `Matches` switch, whose narrowing must switch from
  decoded template paths to element sources.

---

## 2026-08-31 — several pictures, over a run of slots

**Done**

- **`TemplateEditors.group`**, drawn over two shapes with one body: the arguments of an
  `ImageTemplateGroup.of(…)` slot, and a `SlotRun` — which is what a varargs tail
  (`found.hasAny(coin, gem)`) and a `Matches` case are. It is registered **before** the single-picture
  editor, because it claims a subset of what that one would: an `ImageTemplate` argument the host says is
  one of a run.
- **Studio's writer stopped spelling this API.** `CodeEditor.setImageTemplateArgs` and
  `setMatchesCheckTemplates` took template *paths* and built `new ImageTemplate(path)` themselves; they are
  one `setTrailingArguments(call, fromIndex, expressions, imports…)` that puts back whatever it is handed.
  `MatchesGroupScope.allowedSources` is the same change to the narrowing — element source, not paths —
  while `allowedPaths` survives for `StatementFactory`, which is *generating* a seeded switch and genuinely
  needs a path. Two questions, two answers.
- **Two rules the row follows, both about not destroying what it cannot read.** An element it cannot parse
  is kept as it stands, since every write hands back the whole list; and *Remove* is **disabled** at
  `run.minimum()` with the reason in its label, because a `Matches` branch with no pictures is
  unconditional and would not compile.

**Deferred / next**

- **The `ImageTemplateGroup` slot is still Studio's**, deliberately. Filling one is the second of the two
  edits that let the host seed a group find's body with a `Matches` switch (`LambdaCallHandler.seedIfReady`),
  and that seeding emits this API from the host. Claiming the slot before the generation phase moves that
  would silently delete the seed.

---

## 2026-08-30 — the capture surfaces move, and `ZoomPan` finally lands

**Done**

- **`internal/plugin/capture/{CaptureSurface, ObjectCaptureSurface, MagicWand, OverlayStage}`**, out of
  Studio's `ui/app/capture` and `ui/app/overlay`. The overlay is a feature of this plugin, not of the host:
  it exists to turn a `CaptureTargetModel` into an `ImageTemplate`, and both of those are ours. The two
  `MagicWand` tests came with it (11 tests).
- **`ZoomPan` reached `botmaker-plugin-toolkit`**, where it has belonged since it arrived here — a gesture
  over a `Pane` and a `Group`, naming nobody's vocabulary. It could not go earlier only because its second
  caller was Studio's, and Studio source may not name a toolkit type.
- **The dead parameter became the live one.** Both surfaces took a `Window owner` they never used (they are
  deliberately ownerless, so the editor can be minimised mid-capture); it is now `StudioServices`, which is
  how they reach `Capture.toFxImage`. Nothing was added to the contract.
- **`Styles.UNTHEMED`** replaced Studio's `ThemedWindows.UNTHEMED` — the same string, moved to the toolkit,
  because a plugin drawing a translucent surface over a live game has to be able to refuse the host's theme.
- **`OverlayStage`** carries the promote-above-fullscreen trick. It is here rather than in the toolkit
  because the raise is `botmaker-shared`'s; Studio's `OverlayToolbars` delegates to it until the launch
  pickers take its last two callers.

**Deferred / next**

- **`OverlayTemplateCapture` and `BatchTemplateNamingDialog` are still Studio's.** They need `TagPicklist`
  and `ImageTemplatePicker`, which move with the template editors, so they follow in the same step as the
  toolbar item rather than ahead of it.

---

## 2026-08-30 — the picture store moves

**Done**

- **`authoring/{TemplateLibrary, TagCatalog, TemplateManifest}`** — Studio's `services.ImageTemplateLibrary`
  and its two records, keyed on the resources directory rather than on Studio's `ProjectConfig`. That key is
  `Authoring`'s idiom and is exactly what `StudioServices.resourcesDir()` hands a plugin, so the pickers that
  follow reach their own pictures with nothing added to the contract.
- **`pathFor` stopped needing a project root**: a template is a PNG directly inside
  `src/main/resources/images` and there is nowhere else for one to be, so the stored path is
  `WireText.IMAGE_PREFIX` plus the file's own name.
- **`TagCatalog.of` takes activity names**, not a parsed activities file — the only thing it ever wanted from
  one. `TagCatalogTest` and `TemplateManifestTest` came along (27 tests).
- **Studio keeps a delegating `ImageTemplateLibrary`** so its ~90 call sites did not move, and keeps
  `openActivityTag`, whose question is about the editor's open file rather than about the folder.

**Deferred / next**

- **The pickers, the capture surfaces and the toolbar item.** `TemplateEditors` over `Modals.gallery`,
  `internal/plugin/capture/{CaptureSurface, ObjectCaptureSurface, MagicWand, BatchTemplateNamingDialog,
  OverlayTemplateCapture}`, and *Capture Templates* as a `ToolbarItem`. `ZoomPan` reaches
  `botmaker-plugin-toolkit` with them. *(The surfaces and `ZoomPan` landed later the same day — see the
  entry above; what is left of this item is `BatchTemplateNamingDialog`, `OverlayTemplateCapture`, the
  editors and the toolbar item.)*
- **`ResourceManagerDialog` stays in Studio**, and the reason is worth knowing before anyone tries to move
  it: its rename and delete guards go through `TemplateReferences`, which reads the editor's open buffers
  (`ProjectState`) and writes `@NeedsReview` through `ReviewMarker`. Rewriting a user's Java is host work.
- **`ResourcesChangedEvent` has no subscriber** — four publishers, nothing listening, and a javadoc claiming
  open template pickers refresh on it. So the event bus is not a coupling the capture flow has to solve; it
  is dead code to delete on the way past.

---

## 2026-08-30 — the strictness editor moves, and Studio stops naming `internal`

**Done**

- **`internal/plugin/editors/PrecisionEditors`** — the `Precision` editor, matched by type so it is drawn on
  a block *and* in the Parameters window. The ΔE slider against the type's own anchors with a swatch strip
  showing what the tolerance lets through, the minimum area drawn to scale over a 1:1 grid, and a readout of
  what these settings would actually find in a frozen frame of the capture target. Writes the shortest exact
  Java form into a slot (`Precision.TIGHT.minArea(400)`) and `deltaE,minArea,minCount` into a row — the same
  spelling `SdkValueTypes`' own `PRECISION` codec writes, because they are two writers of one file.
- **The parse moved off JDT.** Studio read the current value from a syntax tree; `settingsOf` walks the
  expression's **top-level dotted segments** and applies each one it recognises. A wither chain is exactly
  that, so no parsing library is needed, a fully-qualified spelling costs nothing, and `Precision.of(12.5)`
  stays readable because the split is at top-level dots only. Eleven tests, no JavaFX toolkit needed.
- **Both of Studio's dispatch arms went**, as with colour: the `PickerRegistry` entry and `ValueEditors`'
  `PRECISION` case, whose `PrecisionRow` drew the three numbers as a preset dropdown and three bare fields.
  A Parameters row now gets the whole dialog, with all three knobs — `knobsFor(null)` is the honest answer
  where there is no enclosing call to narrow them.
- **Studio names nothing under `com.botmaker.sdk.internal` any more.** The colour slice left
  `PrecisionArgPicker` reaching `EditorFrame`/`ColorSampler`; that picker is deleted.

**Deferred / next**

- **`ZoomPan` is still in this module and still belongs in `botmaker-plugin-toolkit`.** Its second caller is
  Studio's `ObjectCaptureSurface`, whose own caller is `OverlayTemplateCapture` — so it moves with the
  **image-template** slice, not this one. The plan lists `ObjectCaptureSurface` under precision; the code
  says otherwise, and the code is right.

---

## 2026-08-30 — the colour editor moves, and brings its frame with it

**Done**

- **`internal/plugin/editors/ColorEditors`** — a `java.awt.Color` as a swatch plus an eyedropper, matched by
  type so it is drawn on a block *and* in the Parameters window. Writes `new java.awt.Color(r, g, b)` into a
  slot and `#RRGGBB` into a row, through `Slots.write`. Seven tests, no JavaFX toolkit needed.
- **`internal/plugin/capture/EditorFrame`** — one frozen frame of the project's default capture target, read
  out of `capture.json` through `Authoring` and grabbed through shared. Reports either a frame or a
  `Failure` saying which of the two things went wrong, exactly once, on the FX thread.
- **`internal/plugin/capture/{ColorSampler, ZoomPan}`**, moved from Studio: the loupe, the ΔE spread of the
  surrounding 5×5, the ctrl-scroll zoom. `ColorSampler` now takes a `StudioServices` for theming, the owning
  window and the AWT→FX conversion, and a frame rather than fetching one.
- **Studio deleted `ColorArgPicker`, `ValueEditors.ColorRow` and both of their dispatch arms.** A type the
  host answers is a type no plugin is ever offered, so removing the arms is what lets this editor be drawn.

**What this slice establishes, since three more follow it.** A plugin's editor grabs its own pixels: the host
answers *which project is open* and nothing else, the target comes from the plugin's own file, and shared
does the grabbing. **Nothing was added to `StudioServices`** — the standing condition on every move like
this.

**Why the contract's `grabFrame` could not be used**, which is the finding worth carrying: it reports a
failed or blank grab by never invoking its callback, so an editor cannot distinguish failure from a slow
grab. `EditorFrame.Failure` exists because *no target configured* and *the grab came back blank* send a user
to two different places, and under Wayland the second happens to perfectly good targets.

**The eyedropper falls back to the host's live screen pick** when the project has no capture target, after
saying so once — which is more than either editor it replaced offered, and is what let this slice land while
the capture-targets dialog is still Studio's.

**Two things are deliberately in the wrong place for one more step.** `ZoomPan` names nothing of the SDK's
API and belongs in the toolkit, but its other caller (`ObjectCaptureSurface`) is Studio's and Studio may not
name a toolkit type. And `PrecisionArgPicker` reaches `EditorFrame`/`ColorSampler` from Studio. Both end when
the precision picker moves.

## 2026-08-30 — one capture-target vocabulary

**Done**

- **`CaptureTargetModel` answers what a record shape used to.** Four factories (`desktop`, `monitor(int)`,
  `window(String)`, `emulator(String)`), five readers (`is(CaptureSourceKind)`, `isDesktop`, `monitorIndex`,
  `windowTitle`, `emulatorName`) and two labels (`longLabel`, `shortLabel`, plus null-safe statics — an
  unset default is the whole desktop). All `@JsonIgnore`, all derived from the spec, nothing stored.
- **Studio's `project.capture.{CaptureTarget,CaptureTargets,CaptureTargetNames}` are deleted** and ~180
  references across 20 files retyped onto this model. Studio had four sealed records, an adapter mapping
  them onto the spec grammar, and a label table; the store was already `capture.json`, so deleting the
  adapter was the whole change. The editor and the running bot can no longer spell a target differently.
- **The pilot's private `monitorIndex` is gone** and `PilotRoutes.configuredInstanceName` reads
  `emulatorName()`. The two spellings had drifted: an index that is not a number read as *monitor 0* in
  the editor and as *no frame this tick* in the pilot, which is a black stream where the editor shows the
  primary screen. The model's answer wins in both.
- **Six new tests** on the accessors, covering the two that matter: an accessor answers only for its own
  form (a window title read off a monitor target is `null`, not a guess), and an unreadable spec is the
  whole desktop everywhere rather than an exception.

**Why it is here and not in Studio.** The move it completes is *Studio knows only the contract*, and every
one of that plan's next steps — the colour picker, the precision picker, the image-template picker — needs
the project's default capture target. A target's identity is its spec text, which is this module's grammar,
so the questions asked of a spec belong beside it; a copy in the editor is a second answer that drifts, and
had already drifted twice.

**What deliberately did not move: the live window id.** Studio's `TargetCapture.WindowRef` is a title plus
an optional native handle. A persisted handle is meaningless — which is what `window:<title>` says by having
nowhere to put one — but a gamescope host window cannot be named by title at all, so the caller that
launched it carries the id for the length of one session.

## 2026-08-30 — the pilot was split onto a second plugin interface, and put back

**Done**

- `PilotCompanion` — a `CompanionPlugin` carrying the 🎮 Pilot toolbar item and the `projectClosing()` that
  releases the port and the nested display — was extracted out of `SdkPlugin`, along with a second
  `META-INF/services` file. **Reverted the same day**, whole. `SdkPlugin` owns the pilot again, and the
  services file names `com.botmaker.sdk.plugin.SdkPlugin` and nothing else.

**Why it went back.** The split existed to make the pilot runnable in another process, in TypeScript — and
answering that took a new module, a JSON-RPC dependency, a process supervisor and a wire-record parallel of
four contract records. The maintainer's call: heavier than the problem, and the problem was not clearly
stated. **The pilot is an exception attached to this plugin, and that is a deliberate position rather than
an accident** — see `../botmaker-studio-api/ROADMAP.md` for the full reasoning and what to keep if it is
ever proposed again.

**What must stay true, and is what "correctly attached" means here:**

- `SdkPlugin` is named in `META-INF/services/com.botmaker.plugin.api.StudioPlugin`, so `ServiceLoader`
  constructs it and Studio's `PluginHost` finds it.
- Its `toolbarItems()` returns the 🎮 Pilot item in `ToolbarGroup.RUN` at order 10, built lazily — the
  `RemotePilotUi` is not constructed until the button is first pressed, because constructing one binds
  nothing but is still work a project that never opens the pilot should not pay.
- Its `projectClosing()` nulls and closes that field. This is the member that matters: a pilot still
  answering on the old port would be streaming a project nobody has open.
- The `pilot` field is touched only on the JavaFX thread — a toolbar press and `projectClosing()` both
  arrive there — which is why it needs no synchronisation and must not acquire a background caller.

## 2026-08-30 — the Remote Pilot is an SDK feature

**Done**

- **`internal/plugin/pilot/`** — 17 files and ~3,400 lines from `botmaker-studio` (`services/pilot/*`,
  `ui/app/pilot/*`, `QrCodes`), plus the built web client at `src/main/resources/pilot/` and the `pilot`
  Maven profile that rebuilds it. Javalin and ZXing are new dependencies here, **optional** like JavaFX and
  the toolkit: a headless bot resolves none of them.
- **`SdkPlugin.toolbarItems()` contributes one button** and `projectClosing()` releases what it opened. This
  is the case the toolbar surface was added for — a whole feature behind one button, the host owning the bar
  and the plugin owning everything the press opens.
- **What the pilot needs from a host is four facts, and all four were already on the contract**:
  `resourcesDir`, `status`, `theme`/`dialogs().owner()` and `runs`. It held four editor classes for them and
  **nothing was added to `StudioServices`**, which was the standing condition on the move.
- **`PilotProject`** is the seam: the default capture target out of `capture.json` through `Authoring`, the
  reference resolution out of `botmaker-project.properties` through shared's new `ProjectFile`. Read on
  demand, never cached — a target changed in another window while the pilot streams must take effect.
- **Telemetry arrives as `TelemetryFrame` bytes** through `Runs.onTelemetry` and is decoded here, which is
  what keeps the wire's vocabulary off the contract. A frame that will not decode is dropped, as the wire
  reader itself drops one.
- **The pairing token and bind port are the plugin's own** (`PilotPreferences`, a `java.util.prefs` node)
  rather than the editor's preferences file.

**Deferred / next**

- The pilot's tests moved with it (71 of them, including the golden wire corpus, now
  `src/test/resources/pilot-wire/`). They needed no rewrite beyond their package: every one of them passes
  `null` where the project used to be, which is a fair statement of how little of the editor they ever
  touched. A `PilotProject` over a temp directory would make the default-target arm of
  `TargetCapture.captureDesktop` testable, and nothing covers it today.

---

## 2026-08-30 — the capture targets are authoring data

**Done**

- **`authoring/CaptureModel` + `authoring/CaptureTargetModel`, stored as `capture.json`**, read and written
  by `Authoring.readCapture`/`writeCapture`/`captureJson` — the same shape `activities.json` has, because it
  is the same kind of fact: a file describing the bot, owned by the bot's own SDK version.
- **A target's identity is its spec text**, in shared's `CaptureSourceKind` grammar (`desktop`,
  `monitor:<index>`, `window:<title>`, `emulator:<instance>`). Storing four record shapes instead would be a
  second grammar to keep in step with the one a running bot already reads, and the editor and the bot
  disagreeing about which window to look at is the bug this move exists to end.
- **No schema stamp on this file**, deliberately: the migration ledger is the caller's and its one entry
  point is `activities.json`. A second stamp is a second ledger.
- `defaultIndex` is boxed and normalised — an index naming nothing becomes absent rather than being trusted —
  and `defaultTarget()` is total, standing in the first target for a project that never chose.

**Deferred / next**

- **A bot cannot read `capture.json` itself.** `Authoring` reaches the value vocabulary in
  `botmaker-studio-api`, which is deliberately off a bot's classpath, so the running bot still resolves
  `capture.source` out of `botmaker-project.properties` — which Studio now writes from the default target in
  the same pass. A classpath reader beside `internal/config/ProjectData` is what would retire that
  projection.

---

## 2026-08-30 — the two pickers the lambda was built for

**Done**

- **`internal/plugin/editors/ActivityEditors`** — `activityName` and `outcomeName`, registered in
  `SdkEditors.ALL` behind `CallSites.ACTIVITY_NAME` (argument 0 of `Activities.define`) and
  `CallSites.OUTCOME_NAME` (argument 0 of `ActivityContext.outcome`). Both values are a `String`, so nothing
  but the call could choose them — which is why `outcome` takes a context at all.
- The list comes from `Authoring.readModel` over `StudioServices.resourcesDir()`: the canvas is a file, so
  there is nothing to ask a host for beyond which project is open. Read when the dropdown opens, not when
  the block is drawn.
- **The outcome box offers the project's outcomes, not this activity's own.** An editor is told the call it
  sits in and no more; the enclosing `define(` is two levels up a syntax tree no plugin sees. The union,
  duplicates collapsed, is what can honestly be answered.
- Both boxes stay typeable (`Editors.choiceSlot`, new in the toolkit the same day) — a body written before
  the activity is drawn is an ordinary way to work.

---

## 2026-08-29 — an activity is a lambda

**Done**

- **`api.bot.Activities`** — `define(String name, Function<ActivityContext, Outcome> body)` and
  `active(String)`. **`api.bot.ActivityContext`** — `name()`, `outcome(String)`, `done()`, `enable()`,
  `disable()`. **`api.bot.Outcome`** — a final value type whose identity is a name.
- **`internal/bot/ActivityRegistry`** — the one name map, holding a `Runner` (name, `active`, `setEnabled`,
  `execute`). **`internal/bot/LegacyActivity`** — an `Activity` subclass seen as a `Runner`.
  `Activity`'s own map and `clearRegistry` are gone; `Activity.setEnabled(name, …)` delegates.
- **`ActivityLoader` inverted**: registry first, then the `<pkg>.activities.<Name>` class by convention as a
  fallback, then nothing — which is no longer an error.
- **`FlowGraph.assemble` keeps a node with no runner**, `FlowWalker` treats it as disabled, `Node.runner()`
  is added and `Node.activity()` / `Node.target(Enum<?>)` are deprecated with `@ReplacedBy`.
- **`ProjectData.use(ProjectData)`** made public as a test seam.
- **`ActivitiesTest`** (16) and two rewritten flow cases: `anActivityWithNoBodyIsANodeThatDoesNothing`
  (`FlowLoadTest`, replacing `…IsNotANodeAtAll`) and `anActivityWithNoBodyFallsThroughItsDisabledWire`
  (`FlowWalkerTest`, the same thing walked end to end). 395 tests green; japicmp clean.

**Why**

An activity exists in *data* — it is created on the canvas and lives in `activities.json` — but its behaviour
is code, and nothing writes code into a user's project any more. So the bridge is a call the user writes,
wherever they like.

Two decisions are worth not re-litigating. **The context is not a convenience**: it exists so
`ctx.outcome("…")` is a call on a *known receiver type*, which is the only thing that lets the editor draw a
dropdown of that activity's own outcomes where the name is typed — a body returning a bare `String` is
indistinguishable from any other string-returning body. And **an unwritten activity is not an error**: it
takes the `DISABLED` wire, which reverses `assemble` dropping such a node, and is what makes drawing a flow
ahead of its code an ordinary way to work.

What is given up is the compile check the per-activity `Outcome` enum bought. The picker replaces it, and a
reported outcome the canvas does not declare gets one console line and then behaves like any unwired outcome.

## 2026-08-29 — the SDK writes no `.java`, and the seeds go with the emitter

**Done**

- **`SourceEmitter` deleted**, with `Authoring.sources`, `.activityStub` and `.generatedFileNames`, and with
  `ScaffoldEmitTest`. `ProjectWriter.create` still makes a project — `activities.json`, the project
  properties, the placeholder image, the four `src/` directories — and every `.java` now arrives through
  `Authoring.createProject`'s `callerFiles`, from the host.
- **`internal/plugin/seeds/` deleted** (`GoHome`, `Popups`, `ActivityTemplate`), with
  `SdkPlugin.scaffold`/`seedings`/`pathOf`, `SdkPluginSeedsTest`, and the pom's `<resources>` block that
  shipped the seed sources into the jar. The block's removal restores Maven's default `src/main/resources`,
  which declaring `<resources>` at all had switched off.
- **`SdkPlugin.SDK_PARAMETERS` rehomed** off `SourceEmitter` and made private. A `ParameterGroup` was never
  about the generated `Parameters` file it once named; it is how the Parameters dialog attributes a variable
  to a plugin.
- **`ProjectCreateTest` inverted**: `theSdkWritesNoJava` walks the created project and asserts the only
  `.java` on disk is the one the caller handed in. The collision test now collides on `activities.json`,
  since there is no SDK-written source left to collide with.

**Why**

The seeds were one day old and were the better version of the thing being deleted: real compiling classes
marked with what a host may substitute, so javac checked them and a broken seed was a red build here rather
than in somebody's project. The flaw is one level up — it made *writing files into a user's project* a plugin
surface, and the host grew a key ledger, a reconciler and a rename engine to keep owning what it wrote. **A
project's structure belongs to the user; a plugin contributes methods a user calls.** That is the argument
`pom.xml` had already won, applied without an exception left: the entry point *installs* the plugins, which
is the same argument, and every other file is the user's in a plainer way.

An activity's behaviour becomes `Activities.define("Mining", ctx -> …)` (landing next). The one real loss is
the compile check a per-activity `Outcome` enum bought; a host picker on the argument replaces it.
`ActivityModel.id` survives the seeds that motivated it — nothing keys on it today, and it costs nothing to
keep for the next thing that needs to tell a rename from a delete-plus-create.

## 2026-08-28 — five editors become three tables and two prompts

Part F, phase C. The SDK's plugin half keeps shrinking to *what only the SDK knows*, and this is the
clearest instance of it so far: two files lost every widget they had and kept every fact.

**Done**

- **`GeometryEditors` is three `Editors.TupleSpec` constants** (145 → 72 lines). The pill, the screen
  picker, the typed dialog and the not-a-number fallback are `Editors.tuplePill` in the toolkit; what stays
  is that a `Rect` is `x, y, width, height` and reads `10, 20  640×480`, that a `Point` is picked under a
  magnifier and a `Size` is *measured* — a region dragged with its origin thrown away. `holdsNumbers` and
  `isNumber` went to `Slots.holdsNumbers`.
- **`GeometryLabelTest` is unchanged**, all ten cases, still calling `rectLabel`/`pointLabel` — which
  are now one-line delegations to `Editors.tupleLabel` through the spec. That is the point of keeping them:
  the labels are what a user reads off a collapsed pill, and the move must not quietly change one.
- **`LaunchEditors.program()` and `option()` are two lines each**, over `Editors.program` and
  `Editors.textSlot`. `game()` is untouched — it names Steam and Epic, walks a library through
  `botmaker-shared`, resolves cover art off the FX thread, and is the half that could never move.
- **They survive as methods rather than being deleted, because the prompts are SDK knowledge.** *"Path or
  command"* says a launch target need not be a file on this machine; *"launch option (e.g. `--fullscreen`)"*
  stops the slot reading as a second program to run. Both are sentences about what a launch call is passed,
  which is exactly what the toolkit may not say.

## 2026-08-28 — `LiteralWriter`'s escaping is total, and stays here on purpose

Part F, phase B. `quote`/`quoteChar` escaped the backslash, the quote, `\n`, `\r` and `\t` and stopped, so a
value carrying a form feed or any other control character — text a person pasted — was emitted raw into a
generated bot, which then did not compile, against a line nobody wrote. Both go through one `escape(char,
char)` now: the two `\b`/`\f` cases, and `\uXXXX` for everything below 0x20 and for 0x7f.

**It is not `Source.string`, which is the toolkit's identical answer, and the comment on it says why.** Only
the SDK's plugin half (`plugin/`, `internal/plugin/`) may name `botmaker-plugin-toolkit`: a plugin's widget
kit is resolved onto that plugin's own classloader, and `internal/authoring` is library code reached by
whatever host is generating a project. Studio happens to carry a toolkit now; a host that does not would
find a library half it cannot load. So the fifteen lines are duplicated deliberately.

## 2026-08-28 — the plugin code shrinks: five classes lifted into the toolkit

Plugin-ecosystem plan, phase 4. Nothing in `api.*` moved and no behaviour changed; what moved is code that
was in this module by accident of who wrote the first editor rather than because it knew anything about the
SDK. The acceptance test each time was the toolkit's own rule: *nothing there may name a plugin's
vocabulary*.

- **`internal/plugin/editors/Slots.java` is deleted** — it is `com.botmaker.plugin.toolkit.Slots` now.
  `GeometryEditors`, `LaunchEditors`, `DurationEditor` and `SettingsEditors` import it.
- **`CallSites` went from 99 lines to a table of five constants.** The matching — declining a Parameters row,
  comparing an argument index, tolerating a qualified or simple class name, the per-overload varargs index —
  is `toolkit.CallSites`'s. What stayed is the part that is genuinely ours: which class, which method names.
  `LAUNCH_OPTION`'s three-way switch became a `Map.of("launch", 1, "launchIfNotRunning", 2,
  "launchAndWait", 3)` passed to `trailingArgumentOf`, and `BOT_SETTING` a `firstArgumentWhere` still asking
  `SettingsEditors.bounds`.
- **`SettingsEditors` is now the table and nothing else** (172 → 84 lines). The pill, the modal, the
  spinner-or-slider division, the clamping and the label are `Editors.boundedPill`/`Editors.flag`. The
  `Bound` record became the toolkit's `Editors.NumberRange` plus a local `Setting` pairing it with a flag
  label — one record rather than two tables, because the dispatch must have a single answer to *is this
  setter claimed*.
- **`SdkPlugin extends AbstractStudioPlugin`**, and its 52-facade `PaletteCatalog.of(…)` moved from a
  `static final` field to `buildCatalog()`. That is a real improvement rather than ceremony: a static
  initialiser runs when `ServiceLoader` constructs the plugin, which the host does while opening a project,
  so the reflection now happens the first time the palette is actually asked for. `slotEditors`,
  `valueTypes` and `parameters` became `build…` hooks the same way; `id()`/`displayName()` are constructor
  arguments.
- **The two editor tests dropped their hand-rolled stubs** for `toolkit.testing.TestContexts` — 28 lines out
  of `GeometryLabelTest`, 50 out of `DurationSourceTest`, with `wroteToSlot`/`wroteToCall` becoming
  `replacement()`/`enclosingReplacement()`. All 354 SDK tests still pass, `ApiCatalogTest` included, which is
  what confirms the catalog is byte-identical through the new path.

**What deliberately did not move: `SdkValueTypes` still uses its own private `codec(…)`/`seeded(…)`
helpers rather than the toolkit's new `Codecs`.** `Authoring` reaches `SdkValueTypes.CATALOG` on **Studio's**
classpath, and Studio must not depend on the toolkit — so a toolkit class named from there is a
`NoClassDefFoundError` the first time anyone generates a project. This module is a library *and* a plugin,
and only the plugin half (`plugin/`, `internal/plugin/`) may name the toolkit. Worth keeping in mind before
the next lift: `internal/authoring` is not plugin code, whatever its shape suggests.

## 2026-08-28 — the GitHub Release is published from here, by JReleaser

**Done**

- **`jreleaser.yml` and a `release` job in `ci.yml`.** A `v*` tag now publishes this module's GitHub
  Release from its own CI, with the `## [x.y.z]` section of `CHANGELOG.md` as the body. It used to be a
  `gh release create` inside the umbrella's `release.sh`, which keeps everything JReleaser cannot express:
  which modules are cut, at what versions, in what order, this module's `.deps.env`, Studio's
  `SDK_FALLBACK_VERSION` bump, and the tag itself.
- **`tools/changelog-section.sh`** — the extractor, moved out of `release.sh` into this repository. This is
  the module where `CHANGELOG.md` has a **third** reader: the whole file ships in the jar as
  `META-INF/botmaker/whats-new.md` and Studio's upgrade dialog leads with it. One extractor for the gate
  and the notes; the jar keeps carrying the unextracted original, because a bot may jump several releases
  at once and must be able to answer every span ending at its own version.
- **Two findings worth keeping, because each reads as a configuration mistake until you hit it.** JReleaser
  **cannot open a submodule**: here `.git` is a `gitdir:` FILE and its JGit reports *repository not found*,
  while `--git-root-search` gets past that only by resolving the **umbrella** repository. Hence CI, where a
  checkout is standalone. And `jreleaser-maven-plugin` is not a way round it: it ignores `jreleaser.yml`
  entirely and takes the version from `<version>`, which here is the cosmetic `0.0.0-SNAPSHOT` JitPack
  overrides. The version arrives as `JRELEASER_PROJECT_VERSION`, read off the tag.
- **Nothing in the build changed** — no Maven plugin, no lifecycle binding, no pom edit. That is the
  property `mvn -pl botmaker-sdk -am install` depends on before every Studio dev-run, and it was the
  constraint the whole change was held to: seeing a local change must never require cutting a release.

---

## 2026-08-27 — reflection replaces the processor, japicmp replaces the back edge

**Done**

- **The annotation set narrowed to five, and two bits replaced three.** `@Facade` → **`@Palette`** (no `role`
  element), `@Internal` → **`@Hidden`** (palette-only again, and moved from `meta` to `palette`). A type is
  *catalogued* (`@Palette`) and either *offered* or not (`@Hidden`); `FacadeRole`'s third state was read by
  nothing, and `FacadeEntry.role` is now `boolean offered`. 52 `@Facade` sites and 16 `@Internal` sites
  rewritten; **all sixteen were methods**, which is what made the phase-8c widening ("not versioned surface")
  unnecessary.
- **The catalog is reflected, not generated.** `SdkPlugin.CATALOG = PaletteCatalog.of(Mouse.class, …)` — 52
  class literals. `botmaker-plugin-processor`, `<annotationProcessorPaths>`, `-Abotmaker.surface`,
  `-Abotmaker.catalog` and the generated `internal/plugin/catalog/Catalog.java` are all gone, and the pom
  compiles with `<proc>none</proc>`.
- **Verified by diff, not by assertion.** The last generated `Catalog.java` was dumped before the switch and
  compared against the reflected catalog: **same 52 facades, same order, same member names, every `.order(…)`
  prefix reproduced, no problems reported**. Declaration order survives because `PaletteCatalog` reads the
  class file's own `methods` table (`SourceOrder`); every failure path there falls back to alphabetical.
- **Constructors backed out.** Reflecting them added an `<init>` entry to seven *offered* static facades
  (Mouse, Keyboard, Wait, ImageFinder, ImageClicker, ImageWaiter, Pixel) whose public constructor exists only
  because nobody wrote a private one — a menu row that inserts a call cannot render one. `MemberId` keeps its
  constructor support for a plugin that wants it.
- **`PaletteCatalog.of` degrades, never throws.** A facade whose members cannot be read (`LinkageError` from
  an optional dependency the host did not resolve) is reported into `problems()` and offered with no members.
  Found by actually hitting it: `getDeclaredMethods()` on `api.capture.Source` throws
  `NoClassDefFoundError: com/botmaker/session/DesktopSession` when session is off the classpath.
- **`@Replaces` and `@Since` deleted; 12 `package-info.java` deleted; 20 `@Since` sites stripped.**
  `ApiPointersTest` is four rules now (1, 2, 8, 11) and is no longer version-aware —
  `-Dbotmaker.api.maxVersion` reads nothing. `ApiCatalogTest` moved to `com.botmaker.sdk.plugin` and asserts
  `problems()` is empty. `com.botmaker.sdk.api.meta.{Since,Replaces}` stay as `@ReplacedBy({})` shims: they
  are released public `api.*` types, so never-delete applies to them — its first real exercise.
- **japicmp, `verify` phase, scoped to `com.botmaker.sdk.api.**`, refusing `METHOD_REMOVED` /
  `CLASS_REMOVED` / `FIELD_REMOVED`.** No ignore list, no exemption annotation, no verdict file. Baseline is
  `botmaker.japicmp.baseline` = **v1.2.0**, the release never-delete begins at, not the current newest tag:
  pointed at v1.1.0 it correctly failed on `api.config.Wire`, `@Palette`, `@Scaffolding` and `Text`'s nine
  shared-`OcrOptions` overloads — every one a deliberate pre-policy removal. That failure is also the
  gate-seen-to-fail check. Offline builds pass `-Dbotmaker.japicmp.skip=true`.

**Why japicmp is legitimate now when it was not in August:** `21-api-compat.md` §3 names the blocker as *CI
cannot tell an intended break from an accident, because it cannot see the version* — a statement about a
**conditional** rule. Never-delete is unconditional, so there is no legitimate removal to distinguish. The
cost, stated plainly: `com.botmaker.sdk.api` only ever grows.

---

## 2026-08-27 — the SDK ships the editors for its own types (plugin platform, phase 12a)

**Done**

- **`com.botmaker.sdk.internal.plugin.editors`**, and the pom that lets it exist:
  `botmaker-plugin-toolkit`, `javafx-controls` and `javafx-graphics`, all `<optional>true</optional>` for the
  same reason the contract is — a bot is a **headless** program, and resolving JavaFX for it would fail on a
  machine with no JavaFX distribution for its platform. `PLUGIN_TOOLKIT_TAG` joins `.deps.env`, `jitpack.yml`
  and `release.sh`, and cutting the toolkit now forces an SDK release.
- **`Slots`** — the read/write bridge, and the class the rest of the package is built on.
- **`GeometryEditors`** — `Rect`, `Point`, `Size`. Studio's three pickers are deleted.
- **`SdkEditors.ALL`**, returned from `SdkPlugin.slotEditors()`. Reached exactly as a third-party plugin's
  would be: the host asks every plugin in turn, after its own editors and before its JDK/enum fallbacks.
- **`GeometryLabelTest`** (10) — Studio's `CoordinatePickerLabelTest`, inherited assertion for assertion.

**Why `Slots` is one class and not a method on each editor.** The host edits a value in two places and they
*spell* it differently: a slot in a bot's source holds one string that happens to be Java
(`new Rect(12, 40, 300, 80)`), a row of the Parameters window holds four strings. `ValueContext.asSlot()` is
the question that separates them, and it is asked here and nowhere else — so an editor is written once and
appears in both, which is the whole thing the contract's `ValueContext`/`SlotContext` split was for.

**Reading source text instead of an AST changed one thing, and the test is where it shows.** The old picker
held a JDT `ClassInstanceCreation` and could simply *ask* whether the node was a constructor. The contract
hands over a `String` and no syntax tree (its rule 3), so the same question is asked of the text —
`raw.startsWith("new ")` — and `Slots.arguments` is a brace-and-quote-aware split rather than a parser. Two
consequences worth not rediscovering:

- **A missing argument still reads as zero and a non-constructor still shows verbatim.** `new Point(10)` is
  `10, 0` (what a freshly inserted block looks like before the user picks); `bounds` is `bounds`, because
  rewriting somebody's `target.center()` into `0, 0` is a lie about what the bot does. The first draft of
  `holdsNumbers` required *n* arguments and got the first of those wrong.
- **Java literals are read leniently, and that leniency lives in `Slots`, not the toolkit.** `100L` is 100 and
  `1_000` is 1000, because these come out of source where a person wrote them. The toolkit's `Values` reads a
  project file's stored strings, where a value is already plain — putting the leniency there would be widening
  a parser to fit one caller.

**What is deliberately *not* here.** The type being constructed is never checked: which editor a slot gets was
decided from its declared type one layer up, so the read is positional and a second check would be dead code.
That is inherited behaviour, pinned by the test, not an oversight.

**Deferred / next — phase 12b, the remaining ten.** Precision, LaunchTarget, CaptureSource/Window,
ImageTemplate, ImageTemplateGroup, Duration, Emulator, Game (Steam/Epic), LaunchOption, BotSettings, with
their dialogs (`GameLibraryPickerDialog`, `EmulatorPickerDialog`, `TemplateGallery` — SDK-private, not
toolkit widgets: a widget with one consumer belongs to that consumer). The contract they need landed in 12a,
so this is a repeat of the slice above rather than a design problem — but it is ~2,500 lines.

**One decision 12b owes rather than assumes:** `DurationFields` serves *both* the block editor's wait picker
and `ValueEditors`' `DURATION` row. Moving it here means Studio's own `DURATION` arm goes and this plugin's
editor serves that row through the hook. Right end state, and a visible change to the Parameters window, so it
belongs in a turn that can be tested.

## 2026-08-27 — a variable belongs to a plugin's section (plugin platform, phase 11)

**Done**

- `VariableModel` gains a ninth component, `group` — which `ParameterGroup` the variable is filed under, and
  so which generated class it becomes a field of. Blank is the default plugin's (this SDK's `Parameters`),
  which is every variable in every project ever written: the partition needs no migration because the absent
  field already means the right thing. `withGroup` and `isIn(groupId)` come with it; `fromWire` reads
  `"group"` off the JSON.
- `ProjectModel.variablesIn(groupId)` and `variableGroups()` are the partition, and **`nameClash` takes a
  group**: `nameClash(name, except, groupId)` checks all the activities (the stubs are the host's, one set
  for the whole project) but only that group's variables. The two-argument form is the default group's and is
  what every existing caller keeps. Two plugins may now both offer a `timeout`.
- `SourceEmitter.parameters` takes a `ParameterGroup` and emits **one file per group** — the class name, the
  variables and the imports all come from the group. `SDK_PARAMETERS` is this plugin's, and `regenerated`
  emits that one and no other: standing in for another plugin's `DiscordParameters.java` would be writing a
  file the SDK does not own. Phase 14 is where a second plugin gets to write its own.
- `SdkPlugin.parameters(pin)` returns that single group, so the SDK declares its section the same way any
  plugin would rather than being the section the host assumes.
- `ProjectModelBehaviourTest` gains the two rules: the namespace is the group, and a variable with no group
  is the default plugin's.

## 2026-08-27 — the seventeen types carry their own picker heading (plugin platform, phase 10b)

**Done**

- Every `SdkValueTypes` registration now names a `group(…)`: *Basics*, *Date & time*, *Vision*, *Geometry*,
  *Input*. The headings were Studio's `BotType.Group` enum, which no second plugin could ever be added to;
  they are the contract's free `String` now (`ValueType.group()`), named once as five private constants here
  rather than spelled at seventeen call sites, where a typo would silently split a group in two.
- Nothing about the wire changed — a group is a menu heading, never persisted.

## 2026-08-27 — the SDK declares itself a service (plugin platform, phase 15a)

**Done**

- `src/main/resources/META-INF/services/com.botmaker.plugin.api.StudioPlugin` names `SdkPlugin`. Studio no
  longer writes `new SdkPlugin()`: it discovers plugins with `ServiceLoader` over a `URLClassLoader` built
  from the project's own resolved artifacts, so a bot pinned to an older SDK is answered by the plugin
  inside *that* jar. The SDK reaches the list exactly as a third-party plugin would — it is Studio's plugin
  #1 and gets no back door.
- Naming an interface whose dependency is `<optional>` is safe: `ServiceLoader` resolves the file only when
  something asks for `com.botmaker.plugin.api.StudioPlugin`, and nothing on a bot's classpath ever does.
- `SdkValueTypes` registers the seventeen value types through the contract's `ValueCatalog` builder, and
  `WireText.color` accepts a colour with no leading `#` — `Color.decode` reads a bare `1a2b3c` as a decimal
  number, which is not a rejection a user can see the reason for, and the editor's own parser had always
  allowed it. One visible default moves with the codec: a PRECISION value's minArea seeds 1 rather than 4,
  because `WireText.precision` clamps to what the record accepts. The plugin that owns the type owns the
  number, which is the point of the vocabulary moving.

## 2026-08-27 — the pointer vocabulary leaves the SDK, carried by itself (plugin platform, phase 8c.4)

**Changed:** `@ReplacedBy`, `@Replaces` and `@Since` moved from `com.botmaker.sdk.api.meta` to
**`com.botmaker.plugin.api.meta`** in `botmaker-studio-api`, joining `@Internal`. They describe how any
library keeps faith with the code that calls it — compatibility trap #8 was that a second plugin renaming its
own types had no equivalent, only the SDK's copy — and `PluginSurfaceProcessor` already checks them for any
plugin, matching by FQN string.

- **The SDK's three survive one minor as shims**: `@Deprecated(since = "1.2.0", forRemoval = true)` plus
  `@ReplacedBy` at the new FQN with a `note()` telling the author to change the import, keeping
  `@Facade(role = "VALUE")` so the 8c.3 completeness gate still passes. So **the pointer pair's first use is
  its own move**, which is the fairest test it could have had. In `ReplacedBy.java` the annotation use has to
  be spelled fully qualified — the simple name there resolves to the type being declared.
- **Six one-line import repoints** in the SDK (`api.vision.{OcrLanguage,TextResult,OcrOptions}`,
  `api.flow.{PopupCheck,FlowGraph,Recovery}`); every other `@Since` in the module is on an `api.*` element
  that already imported it by simple name.
- **`ApiPointersTest` resolves targets against `com.botmaker.sdk.api` ∪ `com.botmaker.plugin.api`**, scanning
  both modules' `target/classes`. A carve-out exempting contract targets from rules 2/3 would have had to be
  removed again later; a wider universe is simply the truth. Rules 1, 4–9 were checked against the widened
  scan and are unaffected.
- **`first(…)` filters with `directOnly()`, and that is the one real cost of expressing the move in the
  vocabulary itself.** ClassGraph folds meta-annotations into a class's annotation list, and these three
  annotations now annotate each other — so the contract `@Since`'s own `@Replaces` read as being carried by
  every element that merely *uses* `@Since`: twenty-odd bogus double claims, one of them contested with the
  real one. A redirect is a statement about the element it is written on; nothing here ever wanted an
  inherited one. The processor never had the problem — `javax.lang.model` gives direct annotations only.
- **Studio reads three spellings** (`SdkApiModel`): the contract's, `com.botmaker.sdk.api.meta.*`, and the
  pre-1.1.0 `com.botmaker.sdk.api.*`, through the `either(…)` helper that already existed for the second.

**Known gap:** `SdkFixtures.jarOf` roots every fixture package under `com.botmaker.sdk.api`, so a
`com.botmaker.plugin.api.meta` fixture cannot be expressed without changing the builder. The contract
spelling is therefore uncovered in Studio's tests while the two older ones stay covered; `either(…)` is
spelling-agnostic, so what is untested is the constant, not the mechanism. Teach `jarOf` absolute packages
when something else needs it.

---

## 2026-08-27 — the processor leaves, and every class says whether it is surface (plugin platform, phase 8c)

**Changed:** `src/apt/java/**` **deleted** (`ApiPointerProcessor`, `PaletteCatalogProcessor`) — both now live
in the new sibling module **`botmaker-plugin-processor`**, merged into one `PluginSurfaceProcessor`;
`pom.xml` loses the two-pass build (`compile-processor`, `target/apt-classes`, `-proc:none`,
`<annotationProcessors>`) and gains one `<annotationProcessorPaths>` entry plus `-Abotmaker.surface` and
`-Abotmaker.catalog`; `jitpack.yml` requires `PLUGIN_PROCESSOR_TAG`; eleven new `package-info.java`;
`@Facade(role = "VALUE")` on six types that are import targets rather than menus (`FlowGraph`, `PopupCheck`,
`Recovery`, and the three `api.meta` annotations); `@NotInPalette` → `@Internal` across `api.*`;
`ApiCatalogTest` gains the runtime half of the `@Internal` rule; `CHANGELOG.md`, `CLAUDE.md`. Umbrella:
`release.sh` (`--plugin-processor`, `PLUGIN_PROCESSOR_TAG`), `pom.xml` (reactor order), `.gitmodules`,
`CLAUDE.md`. studio-api: `plugin/api/meta/Internal`, `palette/NotInPalette` deleted. Studio:
`util/MethodSignature`, `suggestions/ProjectAnalyzer` (erase type variables in the signature key).

**Done**

- **The generator is the contract's, not the SDK's.** A second plugin wanting a catalog would have had to
  re-derive 434 lines of `PaletteCatalogProcessor` — the back door this plan exists to close, in the one
  place nobody looks, because a processor is build machinery rather than a type.
  `annotationProcessorPaths` **takes artifact coordinates, not a source tree**, which is the whole reason the
  SDK compiled `src/apt/java` in a first pass with `-proc:none`. Once the processor is a coordinate, the
  two-pass build is deleted outright.
- **The processor depends on nothing — not even `botmaker-studio-api`.** It matches annotations by fully
  qualified *name* out of `AnnotationMirror`s and never resolves a `Class<?>`, which is what lets it run over
  a plugin built against a different contract version, and what lets it check the SDK's own `api.meta`
  annotations without a cycle. The catalog's output FQN is an option (`-Abotmaker.catalog`), not a constant.
- **`@NotInPalette` became `com.botmaker.plugin.api.meta.@Internal`, and the meaning widened on purpose.**
  `@NotInPalette` said only *the menus should not suggest this*; `@Internal` says **not versioned surface** —
  freely breakable, owed no `@Since`, owed no pointer on removal. That is what `internal.**` has always
  meant, said in a way a second plugin can reproduce without adopting our package names. It targets packages,
  so eleven `package-info.java` say it once per package rather than once per class.
- **Every class under the declared root is classified, and javac refuses one that is not** — the completeness
  gate the maintainer asked for, opt-in per module via `-Abotmaker.surface` so a plugin that only wants a
  catalog is not made to classify anything. It closes the silent outcome phase 8 half-closed: an unmarked
  *method* defaults to offered, but an unmarked *class* defaulted to nothing at all.
- **`@Internal` and `@Facade` on one type is a compile error**, and the reason is the repo's own boundary
  rule — *can a bot write the name down?* A palette entry inserts a call into a bot's source, so offering a
  member **is** making a bot write the name down, which makes the type surface. `@Facade(role = "HIDDEN")` is
  how a type is recognised without being proposed; a type a bot legitimately calls but which is plumbing is
  **misfiled** and moves (`shared.ocr` in 8b, `api.authoring` in 10a are the precedents).
- **The cost, recorded rather than argued away:** `@Internal` welds *not-surface* to *not-offered*. Six types
  that are versioned but should not be proposed — `FlowGraph`, `PopupCheck`, `Recovery` and the three
  `api.meta` annotations — take `@Facade(role = "VALUE")` instead, which is honest (they are import targets)
  and is why they are catalogued with no members.
- **A generic method is keyed by its *erased* descriptor now, in all three vocabularies.**
  `FlowGraph.<O extends Enum<O>> route(O, String)` had a generic signature reading `O,String`, which no
  `MemberId` can ever produce — a descriptor can only say `Enum`. `MethodSignature` and `ProjectAnalyzer`
  read `getTypeDescriptor()` rather than `getTypeSignatureOrTypeDescriptor()`, which is also the better key:
  `O` is a letter that means nothing to a slot editor and changes if the SDK renames it.

**Deferred / next:** **8c.4** — `@ReplacedBy`, `@Replaces` and `@Since` move from `com.botmaker.sdk.api.meta`
to `com.botmaker.plugin.api.meta` under a one-minor `@Deprecated(forRemoval = true)` + `@ReplacedBy` window
(the pointer machinery covering its own move). The processor already accepts both spellings, so the move is
the deprecation window and nothing else.

---

## 2026-08-27 — the value vocabulary leaves the SDK and opens (plugin platform, phase 10a)

**Changed:** `api/authoring/**` → **`sdk/authoring/**`** (13 main + 3 test files, one package move);
`api/authoring/{ValueType,ValueShape,ValueChoice,Visibility,Range}` **deleted**;
`internal/authoring/SdkValueTypes` and `internal/authoring/ValueJson` **new**;
`internal/authoring/LiteralWriter` rewritten (157 lines → a lookup); `internal/authoring/SourceEmitter`,
`internal/authoring/ProjectWriter`, `sdk/authoring/Authoring` (a `valueTypes(SdkVersion)` entry point, and
the mapper registers the value module); eleven `@Since` removals; `AuthoringModelTest`,
`ProjectModelBehaviourTest`, `ScaffoldEmitTest`, `ProjectCreateTest`.

**Done**

- **The vocabulary moved to `com.botmaker.plugin.api.value` in `botmaker-studio-api`** — `ValueType`,
  `ValueShape`, `ValueChoice`, `Visibility`, `Range`, `ValueCodec` and `ValueCatalog`. A variable's type is
  now a question the *contract* answers, so a plugin can have a variable of a type it owns without the SDK
  granting it one.
- **`ValueType` is no longer an enum, and its identity is the persisted `id()`.** A closed enum is exactly
  right for one plugin and wrong for two: a Discord plugin wanting a `Channel` variable would need a constant
  added to the SDK's enum — the back door the platform exists to close. Never compare by object identity: two
  plugin classloaders make that meaningless, and the id is what a file holds anyway.
- **`ValueType.unknown(id)` is what makes an open vocabulary safe.** An id nothing registered keeps its raw
  `List<String>`, renders read-only, and **declines to emit** (`SourceEmitter` writes a comment naming the
  missing type in place of the field). It was an unreachable state while the set was closed and it is the
  ordinary state of a project opened without one of its plugins; the alternatives — refusing the file, or
  reading it as text — destroy a user's value because a jar is missing.
  **An absent id is still text**: `null`/blank is a field older than the vocabulary, not a name nobody claimed.
- **`api.authoring` became `sdk.authoring`, and that is what keeps the `api.*` invariant literal.** Nothing
  under `com.botmaker.sdk.api` may reference a `com.botmaker.plugin.api` type — and these records name
  `ValueType` in their components. The plan proposed amending the invariant; moving the package instead keeps
  it unamended, and it costs nothing: a bot never writes `ProjectModel` down, so the package never belonged in
  `api.*`. The eleven `@Since` annotations went with the move — it is no longer API surface.
  `ApiPointersTest` is unaffected (it scans `.acceptPackages("com.botmaker.sdk.api")`).
- **`ValueCodec<T>` is per *item*, not per value** — `parse(String)`/`store(T)`/`literal(T)`, deliberately not
  the plan's `parse(List<String>)`. Shape is composed above it by `ValueCatalog.initializer`, so one codec
  serves `ONE`, `ONE_OF`, `ANY_OF` and `OPEN_LIST` without knowing they exist. `T` never crosses to the host:
  only ever `literal(parse(wire))` behind a wildcard capture, so Studio never loads a plugin's value class.
- **`LiteralWriter`'s seventeen-arm `switch` is a catalog lookup**, as is `isClosedSet`. Neither was ever
  really exhaustive — javac only thought so because the set was closed for as long as there was one plugin.
- **`SdkValueTypes` registers the SDK's seventeen types through the same builder any plugin uses.** No
  privileged path, and the ids are the old enum constant names, so every project ever written keeps its
  meaning.
- **`ValueJson` supplies Jackson, in the SDK, because the contract carries no annotations.**
  `botmaker-studio-api` has one dependency (`javafx-controls`, `provided`) and adding a JSON library to it
  would tie the contract to that library's compatibility rate and impose it on every plugin. So the contract
  declares the wire *form* — an id out, a total factory back — and whoever owns the file supplies the parser.
  `Range` is hand-serialized because `isEmpty()` reads as a getter and Jackson would otherwise write an
  `"empty"` member into every stored bound.
- **Contract records are frozen, and `ValueType`/`ValueCatalog.Entry` are final classes with builders** for
  the reason `docs/refactor/25-compatibility.md` trap #2 records: adding a component to a public record
  changes its canonical constructor descriptor, which is `NoSuchMethodError` in every already-compiled plugin.
- **`ValueCatalog.merge` is left-biased and never throws** — deliberately unlike whole-file generation, where
  a collision is a hard error before a byte is written. There, refusing costs a regenerate; here it costs
  every project that has a plugin installed.
- **`SourceEmitter` holds the SDK's *own* catalog, not a merged one.** The SDK writes the files the SDK owns;
  another plugin's typed variable is that plugin's to emit into its own file.

**Deferred / next**

- **Phase 10b:** Studio's `palette/BotType` (32 files) and `project/activity/VariableWire` (16 files) are
  deleted and `ActivityVariable` retypes onto the contract vocabulary — the ~94-file switchover. Studio keeps
  a *narrowed* list for the block editor's declarable types, which is the second thing `BotType` is: an
  `Initializer` seed table with a `NOTHING`/void entry that is not a storable value at all.

---

## 2026-08-26 — a disabled activity gets its own wire (plugin platform, phase 9)

**Changed:** `api/authoring/FlowEdgeModel` (new `DISABLED_OUTCOME`), `api/authoring/ActivityModel` (new
`flowPorts()`; `allOutcomes()` now filters `DISABLED`), `internal/authoring/SourceEmitter` (`whenDisabled` is
read from the `DISABLED` wire, not inferred from `NEXT`), `ScaffoldEmitTest`, `ProjectModelBehaviourTest`.
Studio: `FlowEdge`, `ActivityDefinition`, `ActivityDraft`, `FlowCanvas`, `FlowNames`, `NewActivityDialog`,
`ActivityFlowDialog`, `blocks.css`.

**Done**

- **`DISABLED` is now an outcome the flow is wired from**, beside the implicit `NEXT`. It was always a slot in
  the generated code — `FlowGraph.node`'s `whenDisabled`, checked by `FlowWalker` before the popup guard and
  before `GO_HOME` — but nothing ever wrote to it deliberately: the emitter inferred it from the activity's
  `NEXT` wire, so where a switched-off activity sent the run was invisible in the editor and impossible to
  choose. `FlowGraph` and `FlowWalker` are **unchanged**; the mechanism existed end to end already.
- **A port, never an `Outcome` constant** — which is why `allOutcomes()` and `flowPorts()` are two methods.
  An activity can't *report* being switched off, because it didn't run, so a `DISABLED` enum constant would be
  one nothing could ever return and a route beside `whenDisabled` would be a second mechanism for one thing.
  `allOutcomes()` stays the enum list and filters `DISABLED` out defensively; `flowPorts()` appends it last,
  and is what the canvas draws ports from **and** prunes wires against. Pruning against the enum list instead
  would have deleted every `DISABLED` wire the moment it was drawn.
- **No migration, and this is a behaviour change.** An existing project has no `DISABLED` wire, so switching an
  activity off now *ends the run* where it used to carry on to the `NEXT` target. Deliberate: guessing `NEXT`
  was the wrong answer often enough that preserving it would preserve the bug. The flow dialog says so —
  "stops the run if switched off: …" naming every activity the flow continues past whose `DISABLED` is
  unwired. Leaf nodes are excluded: they end the run either way, so reporting them would flood every project.
- `DISABLED` is a reserved outcome name in both the live check (`FlowNames.outcomeProblem`) and the save-time
  one (`ActivityFlowDialog.validate`), and `NewActivityDialog` shows it as a fixed last row for the same
  reason `NEXT` is a fixed first one — hiding it would grow a port the dialog never mentioned.

**Deferred / next:** phases 10–15 of the plugin-platform plan — the value vocabulary moving to the contract
and opening (10), parameters as a plugin surface (11), the slot editors (12), the toolbar (13), emission as a
contract surface (14), and Studio dropping its compile-scope dependency on the SDK (15).

---

## 2026-08-26 — the catalog stops being written by hand (plugin platform, phase 8)

**Changed:** deleted `internal/plugin/catalog/{Catalogs,V1_1_0,V1_2_0}`; new
`src/apt/java/…/apt/PaletteCatalogProcessor` generating `internal/plugin/catalog/Catalog`; `@Facade` on all
24 facades and `@NotInPalette` on 13 members; `SdkPlugin` repointed; `ApiCatalogTest` rewritten. Umbrella:
`release.sh` (`check_catalog_freeze` and `--allow-removal` **deleted**), `docs/refactor/24-plugin-platform.md`
§6, new `docs/refactor/25-compatibility.md`. Studio: `SdkSurfacePaletteTest`.

**Done**

- **Curation is now an annotation on the member being curated**, and the catalog is generated from it. The
  four annotations live in `botmaker-studio-api` (`com.botmaker.plugin.api.palette`), with all-`String`
  elements — an annotation element's type has to resolve where the annotation is *applied*, so a contract
  annotation can never take an SDK-defined enum constant.
- **Opt-out, not opt-in.** `CatalogBuilder.addAll()` offers every public declared method; only the exceptions
  are marked. The 620 hand-written lines had the default the wrong way round: a method *added* to a facade
  stayed invisible until somebody typed a line about it — the same staleness `SdkType` was deleted for.
  Thirteen `@NotInPalette` marks replace the whole of the old exclusion list, each carrying its reason.
- **The unit of curation is the member *name*.** One entry per name, lead shape plus submenu; hiding an
  overload alone is not expressible and no longer needs to be (phase 8b dissolved the one case that wanted
  it). `@PaletteDefault` is needed exactly once in the SDK — `Wait.seconds(double)`.
- **The per-version catalogs are gone, hours after landing.** `SdkPlugin.catalog(pin)` ignores its argument;
  the parameter stays on the contract because another plugin may use it. Narrowing to a pin is
  `SdkSurfaceService`'s intersection against the bot's *own resolved jar*, read from bytecode — which a
  hand-frozen class could only restate, and had to be edited on every deletion, making it untruthful about
  the past precisely when that was the question. Accepted: a pre-1.1.0 pin narrows from everything-offered to
  this curation ∩ its jar, and label/order edits are retroactive.
- **`check_catalog_freeze` and `--allow-removal` went with them** — they read the forced edit to a frozen file
  as the signal of a removal, and there is no longer a file to force. Nothing in `release.sh` refuses a
  removal now, deprecated or not; `check_api_pointers` is untouched.
- **Trap worth the line:** an annotation processor must emit from a processing round, **never** the final one.
  A file created after `processingOver()` is written to disk and then not compiled, and it presents as
  `package … does not exist` with a perfectly good generated file on disk.

**Deferred / next:** phases 9–15 of the plugin platform — the `DISABLED` outcome, the value vocabulary moving
to the contract, parameters and slot editors as plugin surfaces, and Studio dropping `botmaker-sdk` to test
scope.

---

## 2026-08-26 — the OCR stack moves in, and the last api leak closes (plugin platform, phase 8b)

**Done**

- **`com.botmaker.shared.ocr` is now the SDK's**, split by the `api` boundary rule: `OcrOptions`,
  `OcrLanguage` and `TextResult` are `com.botmaker.sdk.api.vision` (`@Since("1.2.0")`) — versioned surface a
  bot writes down by hand — while `OcrEngine`, `OcrNative` and `OcrPreprocessor` are
  `com.botmaker.sdk.internal.ocr`, which a bot can only ever receive results from and the palette therefore
  never offers.
- **The last knowingly-open leak of the 1.1.0 audit is closed.** `Text`'s nine `shared.ocr.OcrOptions`
  overloads put a freely-breakable, unversioned shared type in a bot's hands; `docs/refactor/22-api-audit.md`
  deferred them because removing a working feature (PSM, upscale, binarize, char whitelist) is a regression.
  Moving the type under contract dissolves the problem rather than working around it. `Text` lost its three
  `shared.ocr` imports and the javadoc paragraph now records the leak as closed.
- **It was also a prerequisite for phase 8.** Palette curation is now per member *name*, not per overload, so
  those nine shapes could no longer be hidden one at a time and hiding `read`/`find`/`findAll`/`waitFor`
  whole would be absurd. They are now ordinary submenu entries of a versioned SDK type, needing no
  `@NotInPalette` at all — which is what lets the name-level rule stand unqualified.
- **Two net simplifications came free.** `TextResult.bounds()` is an `api.geometry.Rect` rather than a
  `java.awt.Rectangle` (converted once, at the Tess4J boundary), and `OcrPreprocessor.bufferedImageToMat`
  delegates to `OpencvManager` instead of forking it — the fork's rationale was a module boundary that no
  longer exists.
- **`pom.xml` gained the whole OCR stack**: the tess4j dependency, the three version properties
  (`tess4j` 5.19.0, `bytedeco.tesseract` 5.5.3-1.5.14, `bytedeco.leptonica` 1.87.0-1.5.14), and both Linux
  native-staging passes — `maven-dependency-plugin` unpacks the JavaCPP presets to `target/native-stage`,
  the antrun `stage-linux-ocr-natives` execution copies them into `linux-x86-64/` (JNA's `RESOURCE_PREFIX`)
  as the unversioned `libtesseract.so` and the versioned `libleptonica.so.6`. Both filenames are
  load-bearing. The version-pinning comment came across **verbatim**: a mismatch fails at a user's first
  `recognize()`, not at build time, and `OcrEngineNativeTest` is the only guard.
- `tessdata/` (four `tessdata_fast` files, ~10.5 MB) and the three OCR tests moved with the code; all nine
  test methods pass in their new home, `OcrEngineNativeTest` included — it reaches `getWords()` and confirms
  no `/usr` native was mapped.

**Deferred / next**

- Studio still resolves tess4j transitively, because it still depends on the SDK at compile scope. That
  disappears when Studio drops the SDK (phase 15), and only then is the app-image size win real.

---

## 2026-08-26 — the per-version palette catalog, and the SDK as plugin #1 (plugin platform, phase 6)

**Changed:** new `internal/plugin/catalog/{V1_1_0,V1_2_0,Catalogs}`, new `plugin/SdkPlugin`, new test
`internal/plugin/catalog/ApiCatalogTest`. Umbrella: `release.sh` (`check_catalog_freeze`,
`--allow-removal`), `docs/refactor/24-plugin-platform.md` §6, `CLAUDE.md`.

**Done:**

- **One catalog class per released `SdkVersion`**, each written as the previous one plus deltas, served by
  `SdkPlugin.catalog(pinnedVersion)` — so a bot pinned to an older SDK gets *that* version's palette out of a
  newer Studio. `Authoring` gains nothing: a catalog is a plugin contribution, not an authoring one.
- **`V1_1_0` is the deleted `@Palette` curation, reconstructed** — sourced from `21b4825^`, ~380 members over
  38 facades, in `SdkType`'s exact menu order, as method references. `V1_2_0` returns it unchanged, which is a
  **finding**: 1.2.0's whole addition is `api.authoring`, and a bot never calls that.
- **`SdkPlugin` is an ordinary `StudioPlugin` with no back door** — no second interface, no package-private
  hook. Under `com.botmaker.sdk.plugin`, never `api.*`, which is the invariant that keeps the contract's
  `<optional>true</optional>` safe on a bot's classpath.
- **A witness cannot exclude `add(M0)`.** JLS 15.12.2.1: a non-generic method stays applicable when explicit
  type arguments are supplied, so any overload set containing a no-arg or varargs member is ambiguous under
  `.<X>add(…)`. Nineteen entries are therefore written as a **cast** — `.add((M1<Key[]>) Keyboard::combo)`.
  Recorded in `V1_1_0`'s class comment and in the platform doc; it will bite again.
- **The removal gate is back, over the catalog rather than beside it.** A frozen catalog compiles only while
  every member it names exists, so deleting one forces an edit to that file — the edit *is* the signal, and
  `release.sh check_catalog_freeze` refuses it unless `--allow-removal V1_1_0` names it. Nothing generated,
  nothing to refresh; this is what `api-surface.txt` was rebuilt as.
- **The `M5` cap's one casualty today:** `Emulator.swipe(int,int,int,int,long)` is arity-6 with its receiver
  and cannot be named. Left uncatalogued deliberately — the answer, if ever, is `M6` in the contract.

**Deferred / next:** Studio still reads `palette/SdkType`, so the menus stay uncurated until phase 7 wires
`SdkSurfaceService` onto the served catalog.

## 2026-08-26 — `Templates` gets its own entry point (inversion, phase 4)

**Changed:** `api/authoring/Authoring` (`templates`), `internal/authoring/SourceEmitter` (`templatesFile`).

**Done:**

- **`Authoring.templates(version, spec, imageBaseNames)`** — the generated `Templates` class alone, rather
  than as a slice of `regenerate`. It is the one generated file that says nothing about the model: it is a
  function of the images folder, an `EMPTY` project has one too, and it is rewritten on every capture,
  rename and delete. Folding it into `regenerate` would rewrite four files that did not change every time a
  user names a screenshot, which is noise in the diff the user actually reads.
- Nothing else in the SDK moved: phase 4 is Studio reconnecting its five refusing call sites to the
  generator that landed in phase 2, and the only thing it turned out to need that was not already public was
  this.

## 2026-08-26 — the pom goes back to Studio (a one-day reversal)

**Changed:** `pom.xml` (`maven-model` removed), `api/authoring/Authoring` (`pomXml`, `defaultRepositories`,
`isDefaultDependency`, `SDK_GROUP_ID`, `SDK_ARTIFACT_ID` all removed; `createProject` gains `callerFiles`),
`internal/authoring/ProjectWriter`, test `internal/authoring/ProjectCreateTest`. Deleted:
`internal/authoring/PomWriter`.

**Done:**

- **The SDK no longer writes `pom.xml`.** Recorded as a reversal, not edited away: the entry below shipped
  the opposite decision the day before. The argument that moved it here was that a pom is bot-facing and
  every line in it is there *because of* the SDK. True, and not enough. The pom is not a file *about* the
  SDK, it is the file that declares **which** SDK — and the maintainer's framing is that **the SDK is
  Studio's default plugin**, not Studio. A second plugin would be invisible to the SDK, so a pom it wrote
  would silently omit that plugin's dependency, with nothing to notice it. Only the thing that knows the
  whole plugin set can write the manifest of that set.
- **`createProject` takes `callerFiles`** — a `Map<String, String>` of project-relative paths the caller
  composed, committed in the **same all-or-none pass**. That is how both properties survive at once: Studio
  authors the pom, and a failed creation still leaves nothing behind. Writing it before would trip the
  "there is already a project here" guard; writing it after would put a source tree with no build file on
  disk, which is the half-created state the rule exists to prevent.
- **A caller file colliding with a generated one is refused**, never merged. Whole-file ownership keyed by
  path — two authors of one file is precisely what the scaffold contract was deleted for.
- **`maven-model` is off the SDK again**, and the default dependency/repository sets are back in
  `MavenService`, beside the "is this a user library?" predicate that has to be the same list.

## 2026-08-26 — the SDK creates the project (inversion, phase 3)

*Superseded in part, the same day, by the entry above: the pom is Studio's again, `PomWriter` is gone, and
`createProject` takes the caller's files. Everything else below stands.*

**Changed:** `pom.xml` (`org.apache.maven:maven-model`, `<optional>true</optional>`), `api/authoring/Authoring`
(`createProject`, `pomXml`, `defaultRepositories`, `isDefaultDependency`, `SDK_GROUP_ID`/`SDK_ARTIFACT_ID`),
`api/authoring/TemplateNames`, `internal/authoring/SourceEmitter`. New:
`internal/authoring/PomWriter`, `internal/authoring/ProjectWriter`, test
`internal/authoring/ProjectCreateTest`.

**Done:**

- **`Authoring.createProject(SdkVersion, ProjectSpec, Path, int schemaVersion)` writes a whole project.** The
  `pom.xml` (Maven Model API + `MavenXpp3Writer`, no XML string templating), the four `src/` directories, every
  `.java`, `activities.json`, `botmaker-project.properties` and the placeholder PNG — **rendered in memory
  first and committed only then**, so a refusal never leaves a half-created directory tree behind. That is
  `ProjectCreator`'s old rule, moved rather than re-invented.
- **`maven-model` is `<optional>true</optional>`.** It is not transitive, so a generated bot's classpath is
  unchanged; Studio, which already has Maven Resolver, resolves the classes for its own call.
- **The default dependency and repository sets are the SDK's** — a bot's pom is bot-facing, so
  `MavenService.DEFAULT_DEPENDENCIES` / `DEFAULT_REPOSITORIES` moved here. `Authoring.isDefaultDependency`
  is what lets Studio still tell a *user* library from a default one when reading a pom back.
- **The schema stamp is an argument, not a derivation.** `activities.json` is the SDK's file, but the
  migration ledger is still Studio's, so `createProject` takes the version number to stamp. Deriving it in
  two places would be two answers to one question.

---

## 2026-08-25 — the generator, and the death of `Wire` (inversion, phase 2)

**Changed:** `api/config/Wire.java`, `internal/config/ConfigStore.java`, `WireTest`, `ConfigStoreTest` and two
JSON fixtures **deleted**. New: `api/authoring/WireText.java` (the 17 readers, plus the one writer),
`api/authoring/TemplateNames.java`, `internal/authoring/LiteralWriter.java`,
`internal/authoring/SourceEmitter.java`; `Authoring` gains `sources`, `regenerate` and `activityStub`. New
tests `internal/authoring/ScaffoldEmitTest` (6) and `api/authoring/WireTextTest` (22).

**Done:**

- **The SDK generates the bot's Java.** `Authoring.sources` returns every file a project is made of, keyed by
  its path relative to the project root; `Authoring.regenerate` returns the five rewritten on every save
  (`Activities`, `Parameters`, `Templates`, `ActivityRegistry`, `FlowDriver`); `activityStub` returns one
  SEED file. Nothing is written to disk — the caller commits the map, which is what makes an all-or-none
  write possible.
- **A value is a literal now, not a parser call.** `public static final java.time.Duration REST =
  java.time.Duration.ofMillis(5400000L);` where the old scaffold said `Wire.duration(Wire.one("REST"))`.
  The objection that had blocked this — "then a re-run needs a re-build" — turned out to be false: every Run
  recompiles the project before launching it, so the re-build was always happening. With it goes the reason
  for `ConfigStore`, for shipping `activities.json` as a classpath resource, and for `Wire` itself.
- **`LiteralWriter` writes the *parsed* value, never the text** — `new java.awt.Color(51, 102, 255)`, not
  `Color.decode("#3366FF")`; `java.time.LocalDate.of(2026, 8, 25)`, not `LocalDate.parse("…")`. A generated
  file therefore holds no expression that can throw at class initialisation: a bot cannot fail to start
  because of its own configuration. JDK types are written fully qualified (an import that does not exist
  cannot be forgotten); SDK types by simple name, with the import set computed — which is what makes an
  `api.*` rename break *this* build rather than a bot's.
- **`Activities`' flags are emitted without `final`, and that is load-bearing.** Now that the initialiser is
  a literal, `public static final boolean Mining = false;` *is* a JLS §4.12.4 constant variable, javac folds
  it, and a user's `while (Activities.Mining) { … }` becomes an *unreachable statement* — a compile error
  caused by unticking a box. `ScaffoldEmitTest` proves it by compiling exactly that loop against a flag
  stored `false`, rather than asserting it in prose. `Parameters` stays `final`: a folded value can never
  make a statement unreachable.
- **`WireText` is where the parsers landed, not where they died.** The editor has to read `"1h30m"` too, so
  deleting the grammar with `Wire` would have recreated it in Studio. One grammar, one implementation — and
  the generator converts *once*, at generation time, which is the whole argument for baking the value in.
  Studio's `DurationWire.format` came the other way for the same reason: reading and writing one grammar in
  two repositories is how they drift, so `WireText.spellDuration` is now the only writer.
- **`ScaffoldEmitTest` is `ScaffoldCompileTest`'s guarantee, moved to where it means something.** It emits a
  corpus — bare, one activity, a branching/cyclic flow with an orphan, and every `ValueType` scalar *and*
  list with blank, unparseable and quote/newline/`*/`-carrying values — and javacs it against **this build's
  own `target/classes`**, `-Xlint:all`, warnings failing the test. Over in Studio the same test compiled
  against a resolved SDK *jar*, which is a weaker question and one that went stale with the jar.
- **`Wire` was public `api.*` and `@Since("1.1.0")`, so this is an undeprecated removal**, taken on the
  maintainer's explicit waiver (2026-08-25): no published bot consumes the SDK yet and the upgrade path is
  itself under construction.

**Deferred / next:**

- **Inversion Phase 3** — `Authoring.createProject`, with `maven-model` as `<optional>true</optional>` and
  `MavenService.DEFAULT_DEPENDENCIES`/`DEFAULT_REPOSITORIES` moving here.
- **Inversion Phase 4** — Studio calls `regenerate`, which clears the four costs phase 0b took on. Six
  Studio tests are red until it lands; that count is unchanged by this phase.
- **The pickers, and with them the last of Studio's wire knowledge.** `VariableWire`'s coercion (clamping,
  option pruning, retype resets) stays in the editor *only* because a widget has nowhere else to put it. Once
  the SDK ships the per-type pickers, `ValueEditors` and `VariableWire` go together and Studio holds no
  stored-value grammar at all. The **Activity Flow canvas deliberately does not follow them**: a picker's
  option list is version-dependent vocabulary, a canvas's drag behaviour is not, and everything about the
  flow that *is* version-dependent (the model, reachability, the emitted driver) is already here.

---

## 2026-08-25 — demolition, part 2: the templates, the surface file (inversion, phase 0b)

**Changed:** `src/templates/java/**` **deleted in full** — nine files, 407 lines, including
`templates/meta/Template.java` itself; `apt/TemplateProcessor.java` (288) deleted;
`src/test/.../templates/ScaffoldTemplatesTest.java` (261) deleted; `api-surface.txt` (66 KB) and
`src/test/.../api/ApiSurfaceTest.java` deleted; `pom.xml` loses compiler passes 3 (`compile-templates`) and 4
(`restore-artifact-directory`) and both explanatory comment blocks. Nothing writes
`target/classes/botmaker-templates/` any more and no `manifest.txt` ships. **`src/apt/` survives** —
`ApiPointerProcessor` lives there and pass 1 is untouched.

**Done:**

- **The templates go because the second author goes.** They existed so the SDK and Studio could co-author one
  generated file through a text-and-fences protocol. The inversion makes the SDK the generator outright, so
  there is no counterparty left to negotiate with. Phase 0 kept them for one more phase; that half-measure was
  judged not worth carrying.
- **`api-surface.txt` goes because the real record is about to be built.** It was a hand-maintained second
  copy of *what the SDK offers*, in a text file. `api.authoring`'s per-version catalog (inversion Phase 6)
  answers the strictly larger question — *what did 1.2 offer* — in code. Until it lands, the deprecation
  window is a convention, not a gate: `@Deprecated(since, forRemoval = true)` one full minor ahead with a
  pointer is still the rule, and nothing mechanically refuses an undeprecated removal.
- **The pointer machinery is untouched** — `@ReplacedBy`, `@Replaces`, `ApiPointersTest` (10 tests, still
  green) and `ApiPointerProcessor`. Only the surface *diff* went.
- **Known interim cost:** Studio cannot create a project or save an Activity Flow until inversion Phase 2
  puts the emitters in this module. Both paths refuse by name. This is deliberate.

**Deferred / next:**

- **Inversion Phase 2** — `Authoring.scaffold(SdkVersion, ScaffoldModel)` returning whole files, with
  `ScaffoldEmitTest` compiling a corpus against the reactor's own `target/classes`. This is what re-enables
  Studio's generation.
- **The `Wire` redesign** — the reason for the whole teardown. `Wire.duration(Wire.one("REST"))` buries the
  parameter name inside the type while `Wire.many("HOTKEYS", Wire::key)` spells it the other way round; one
  class does both locating and parsing; the storable type set is written out eight times across two repos.

---

## 2026-08-25 — demolition: the scaffold contract apparatus is removed (inversion, phase 0)

**Changed:** `api/meta/Palette.java` and `api/meta/Scaffolding.java` **deleted** with all 435 use sites across
38 files; `templates/meta/Template.java` loses `holes()`; all 13 fences in `src/templates/java/**` lose their
generation (`/*<STUDIO:FIELDS:2>*/` → `/*<STUDIO:FIELDS>*/`); `apt/TemplateProcessor` loses `checkHoles` and
the fence↔`holes` reconciliation, and the manifest drops its `holes` column (**format 2 → 3**);
`ApiPointersTest` loses rules 9 and 10; `ScaffoldTemplatesTest` loses the constant-pool rule and everything
only it used; `api-surface.txt` regenerated.

**Done:**

- **The apparatus existed to make two repositories agree about a file they co-author.** Studio rendered the
  fragments, the SDK shipped the frame, and a negotiated protocol — fenced holes, per-hole generation numbers,
  a generated manifest, a committed ledger on each side, two `release.sh` gates, and Studio's
  `ScaffoldCheck` → `ScaffoldRepair` → refuse — kept them from disagreeing. The decision is to **remove the
  disagreement rather than manage it**: the SDK becomes the generator, so there is no scaffold on Studio's
  side at all. This is phase 0 of that inversion, done first and on its own so the `Wire`/parameter redesign
  that follows lands on a small surface.
- **What survives:** the templates themselves, `@Template(id, kind, target)`, the generated manifest, and the
  fences — reduced to a plain `name → text` fill. `TemplateStore` still reads them for one more phase.
- **`@Scaffolding` went with the rule that read it.** It was a claim about which SDK members Studio wrote into
  generated files; with one author there is no second party to warn. `ScaffoldTemplatesTest` keeps the two
  rules that are still about *this* repository (the manifest matches what ships; every fence is one matched
  pair) and drops the third, which scanned the templates' constant pools to enforce the annotation.
- **`@Palette` went with the blast radius the maintainer chose.** It was a strict per-overload whitelist for
  Studio's block menus. Deleting it widens those menus — `SdkSurfaceService` treats a jar with no `Palette`
  class as uncurated — until the SDK serves the palette itself in a later phase. The maintainer's per-facade
  curation *prose* is preserved in each facade's Javadoc; the per-member verdicts lived only in the 400
  annotations and are lost.
- **Both are public `api.meta` types removed after 1.1.0 shipped**, so this is an undeprecated removal, taken
  deliberately: `release.sh --allow-removal com.botmaker.sdk.api.meta.Palette,…Scaffolding`, with
  `api-surface.txt` regenerated in the same commit. Neither was a type a bot could write down.
- **Manifest format 2 → 3, not 1 → 2 as the plan assumed** — it was already at 2 when the plan was written.
  An older Studio refuses a format it cannot read, which is the correct answer for a new SDK under an old
  Studio.
- **The pom's pass 4 (`restore-artifact-directory`) is kept**, against the plan, which held it existed only to
  undo pass 3 for a departing gate. It undoes `maven-compiler-plugin` re-pointing the project artifact at the
  last `compile` execution's output; pass 3 is not going anywhere this phase, so without it an ordinary
  `mvn -pl botmaker-studio -am test` hands Studio `target/template-classes`.

---

## 2026-08-25 — `Parameters.java`, and the first hole whose generation moves

**Changed:** new `src/templates/java/.../Parameters.java` (`@Template(id = "PARAMETERS", kind = REGENERATED,
holes = {"IMPORTS:1", "FIELDS:1", "INITS:1"})`); `Activities.java` reduced to the enable flags, its holes now
`{"FIELDS:2", "INITS:2"}`. The generated `manifest.txt` follows from both.

**Done:**

- **Two files where there was one.** `Activities` held an activity's on/off tick and the delay it waits for
  side by side in one flat namespace, spelled identically, with nothing in the name saying which was which —
  while the two are governed differently at every level above the field. The flags stay in `Activities`;
  every configured value the bot reads is now `Parameters`.
- **The first real `:1 → :2` bump**, and the case the generation number was added for one commit earlier:
  `Activities`' `FIELDS`/`INITS` still declare the same holes and still take fields and static initialisers —
  only the *contents* changed shape. Every member survives, so `@ReplacedBy`/`@Replaces` has nothing to say,
  and the number is the only thing that distinguishes them. An older Studio, which can produce only `:1`, now
  refuses by name instead of writing values into a frame that no longer holds them.
- It also proves the per-hole design end to end: `FIELDS:1` and `FIELDS:2` belong to **two different
  templates** and no call site branches on it — `TemplateStore.render` takes the number from the template it
  is filling.
- **Owed at release:** this is the first SDK to carry `PARAMETERS`, so Studio's `MIN_SDK_VERSION` and
  `SDK_FALLBACK_VERSION` move to its version together. See `docs/refactor/23-scaffold-contract.md` §11.

---

## 2026-08-25 — a template declares itself, and the manifest is generated from that declaration

**Changed:** new `templates/Template.java` (`@Template(id, kind, target, holes)`, `Kind` an enum) and
`src/apt/java/.../TemplateProcessor.java`; every template's fences gain a generation
(`/*<STUDIO:FLOW:1>*/`); `src/templates/manifest.txt` **deleted** — it is generated into
`botmaker-templates/` at `format 2`; `pom.xml` loses the `copy-resources` (the processor writes the text) and
gains a fourth compile pass; `ScaffoldTemplatesTest` narrowed.

**Done:**

- **The declaration is compiled, not transcribed.** `id` / `kind` / `target` / `holes` sat in a hand-written
  `manifest.txt` beside the templates, where nothing could check them against the files. They are an
  annotation on the template class now — javac enforces `Kind`, and `TemplateProcessor` fails the build on any
  fence↔`holes` disagreement: a fence not declared, a declared hole with no fence, an unpaired fence, a hole
  named twice. The manifest is then *written* by the processor, so it cannot drift from what ships.
- **A hole is `NAME:generation`** — the version of its *shape*, so an SDK that reshapes what a fill must
  contain (`FlowGraph.of(String, Node…)` → `of(Node, Node…)`) is caught by an older Studio instead of writing
  its old arrangement into the new frame. Every hole is `:1` today; nothing generated changes.
- **A fourth, no-op compile pass** (`restore-artifact-directory`) puts the reactor artifact back at
  `target/classes`. `maven-compiler-plugin` ends every `compile` execution with
  `projectArtifact.setFile(outputDirectory)`, so the templates pass was handing every downstream *reactor*
  module `target/template-classes` — seven scaffold classes and no api at all. Invisible to `mvn install` and
  to a module-scoped build; it broke `mvn -pl botmaker-studio -am` outright.
- **`release.sh check_scaffold_sdk`** runs `ScaffoldTemplatesTest` on every `--sdk` release — offline, and
  the first release gate this half of the scaffold has had.

---

## 2026-08-24 — the SDK owns the scaffold: an injection API and compiling templates (phases 1–3 of 7)

**Changed:** new `api/flow/` (`FlowGraph`, `PopupCheck`, `Recovery`) + `internal/flow/FlowWalker`; new
`api/config/Wire` + `internal/config/ConfigStore`; new source root `src/templates/java` and
`src/templates/manifest.txt`, packed into the jar as `botmaker-templates/`; new `FlowWalkerTest`,
`WireTest`, `ConfigStoreTest`, `ScaffoldTemplatesTest`; `pom.xml` gains a third compiler execution and a
`copy-resources`; `ApiPointersTest` rule 12 deleted; `api-surface.txt` regenerated.

**Done:**

- **The scaffold Studio generates now lives here, as Java the compiler reads.** It was text blocks inside
  `botmaker-studio`. A text block cannot be asked what it names, so "which SDK members does Studio write
  into generated files?" was answered by a 484-line JDT visitor over the generators' output, a hand-kept
  declaration, and a committed `scaffolding-surface.txt` ferried between two repositories that cannot read
  each other. `src/templates/java` holds the seven files instead — entry point, `GoHome`, `Popups`,
  `ActivityRegistry`, `Activities`, `FlowDriver`, the activity stub — and the build compiles them, so
  renaming `Watchdog#checkpoint` breaks *this* build on the line that calls it.
- **The defaults are a working one-activity bot, not placeholders.** That is what makes the compile mean
  something: every call Studio will ever inject is compiled here first.
- **Tokens are fenced comments** — `/*<STUDIO:MAX_STEPS>*/ 1000 /*</STUDIO:MAX_STEPS>*/`. Fill = replace
  fence to fence. **Ignore = do nothing, and the default stands**, which is the entire forward-compatibility
  rule: a newer SDK may add tokens an older Studio has never heard of.
- **Templates ship as text and never as classes.** Pass 3 of the compiler writes to
  `target/template-classes`, the same trick `src/apt/java` uses — so no bot finds a
  `com.botmaker.sdk.templates.Activities` on its classpath beside its own. Verified against the built jar:
  90 classes, none of them a template's.
- **Logic moved out of generated text.** `FlowWalker` is the walk loop, the step budget, the give-up message
  and the after-not-before delay — all of which used to be emitted as source, and none of which was testable;
  it now has 13 shape tests (branch, join, loop, unwired outcome, disabled fall-through, empty flow,
  give-up). `Wire` is one reader per stored type and `ConfigStore` the Jackson loader, replacing 13
  `*_HELPER` parser bodies held as Java inside Java strings. That fixed a real defect: the `1h30m` grammar
  existed twice, in Studio and in a text block, with no test able to compare them.
- **The injection API is static-call-shaped, deliberately.** `ScaffoldRepair` can mechanically repair a type
  that moved and a static call whose member moved, and nothing else — every call after the first in a fluent
  chain has an instance receiver whose type its parser cannot know. So `FlowGraph.of/node/route`, never a
  builder. `node` stays generic in the activity's outcome enum, so a route may only be built from *that*
  activity's constants; the old generated `switch` had that guarantee and it was not worth trading.
- **`ApiPointersTest` rule 12 is deleted, and `ScaffoldTemplatesTest` is what replaces it.** `@Scaffolding`
  is no longer a claim about another repository, so it no longer needs a file to be compared through: the
  templates are here, and the test reads their **constant pools** for every `com.botmaker.sdk.*` member they
  reach, failing on any that is not annotated. Verified by removing `@Scaffolding` from `Recovery` and
  watching it name `Recovery#NONE — called from FlowDriver.class`.
- **One direction only, for now.** The templates may not call an unannotated member; an annotated member no
  template calls is not yet an error, because Studio's old generators are still emitting the previous
  scaffold. The reverse rule lands with their deletion.

**Deferred / next (this plan's remaining phases, all in `botmaker-studio`):** `TemplateStore` extract-and-fill
and the deletion of the text blocks; `ScaffoldScan`'s deletion and `ScaffoldSurfaceTest` rewritten as an
in-test `javac` run over assembled output; `MIN_SDK_VERSION`/`SDK_FALLBACK_VERSION` → 1.1.0 with the
post-bump regeneration hook; then the docs.

**2026-08-24, phase 7 (the plan closes):** `CHANGELOG.md`'s `## [Unreleased]` becomes **`## [1.1.0] —
2026-08-24`**, which is what `release.sh`'s `check_changelog` was holding the release on, and gains four
bullets in a bot author's terms: the generated files come out of this jar, the Activity Flow is a table with
the walk behind it, stored parameters are read by compiled code with **one** `1h30m` grammar, and Studio
requires 1.1.0 or newer to *write* a generated file (an older bot still opens, builds and runs). The whole
file ships in the jar as `META-INF/botmaker/whats-new.md`, so that is also what Studio's upgrade dialog leads
with. `docs/refactor/21-api-compat.md` §5.3 is rewritten end to end — three layers, the token protocol, the
static-call shape constraint, the three checks that replaced five mechanisms, and the floor — and the §6
paragraph that **rejected** a reference bot compiled by the SDK's own build now records why that objection
was overruled rather than being quietly deleted. `ApiPointersTest` is documented as ten rules plus the
release-time one, with rule 12's deletion recorded in place.

**2026-08-24, phase 5:** `scaffolding-surface.txt` is **deleted**, together with `botmaker-studio`'s
`ScaffoldScan` that wrote it. Nothing in this module read it after rule 12 went, and nothing over there
compares against it now: Studio compiles four whole generated projects against the real jar instead. The
cross-repo channel is gone in both directions — `@Scaffolding` is checked here, against these templates'
constant pools, and that is the only place it is checked.

---

## 2026-08-24 — the contract document catches up with the contract (phase 12 of 12, the plan closes)

**Changed:** `docs/refactor/21-api-compat.md`, `docs/refactor/99-progress.md`, umbrella `CLAUDE.md`,
`botmaker-studio/CLAUDE.md`, `botmaker-studio/CHANGELOG.md`, both `ROADMAP.md`s. No code.

**Done**

- **`21-api-compat.md` was describing a five-rule gate and a single-valued pointer.** §3 now carries all
  twelve `ApiPointersTest` rules with rule 4's relaxation stated as what it is — *the split's back edge, not a
  loophole* — plus two subsections for the gates that landed in phases 7 and 8: `api-surface.txt` (the
  deprecation window, enforced by reading the previous release out of a **file this repository already has**
  rather than a published jar, which is what the deleted japicmp gate could not do offline) and
  `ApiPointerProcessor` (the per-element rules as javac errors, in a source root that never reaches the jar).
- **§2 said two things that had stopped being true.** *"Skipping the deprecation window is discouraged, not
  refused"* — it is refused again, by a different mechanism, and the paragraph now says which and why the
  escape hatch names elements one at a time. And `@since` was described as a Javadoc tag on members added
  from 1.2.0; it is the `api.meta` annotation, required on **every** new element, written once and never
  edited.
- **§4 gained the split and the three annotations that can only be written during a deprecation window** —
  `note()` and `behaviourChanged()` on *both* ends (with the precedence rule: the old jar's note wins, because
  it is the author speaking on the element the bot actually calls), `@Since`, the optional arity on a
  `@Replaces` entry, and `whens()`. `Mouse.scroll(int)` is recorded as the case that revealed the gap and
  **not** as something this plan deprecated.
- **§5 gained three subsections and lost a stale bullet.** §5.2 the split per call site and the positional
  site key; §5.3 the scaffold end to end; §5.4 `@Palette` and the two questions it deliberately does not
  merge. The bullet arguing that scaffolding must be *refused* rather than regenerated is now the record of
  why that was true and what changed: re-rendering old-SDK text is still useless, re-rendering it and then
  repairing it through the pointers is not.
- The `api` package references throughout moved to `api.meta`, where the annotations have lived since 1.1.0.

## 2026-08-24 — `@Scaffolding` is checked against the repository it is a claim about (phase 9 of 12)

**Changed:** `src/test/java/com/botmaker/sdk/api/ApiPointersTest.java` (rule 12), new committed
`scaffolding-surface.txt` at the module root, `CLAUDE.md`.

**Done**

- **Rule 12: the `@Scaffolding` set must equal what Studio's generators actually emit.** The annotation says
  *Studio writes this element into the files it generates* — a fact that lives in Studio's text blocks, not
  here — so it has always been a second copy, and nothing kept the two in step. Both drifts were silent and
  both were harmful: an element that stopped being generated kept its annotation and went on refusing
  upgrades for a reason that had ceased to be true, and one a new generator started writing carried none, so
  rule 9 never asked its author for a survivor and the upgrade broke a generated file mid-apply.
- **The comparison goes through a committed file, because neither side can read the other.** Studio compiles
  against the SDK and never the reverse, and comparing the annotations with themselves proves nothing. So
  `botmaker-studio`'s new `ScaffoldSurfaceTest` — which holds the truth, parsing the generators' real output
  with JDT and asserting its own declaration matches — writes `scaffolding-surface.txt` here, and rule 12
  reads it back, naming the difference in **both** directions. Regenerate with
  `mvn -pl botmaker-studio test -Dtest=ScaffoldSurfaceTest -Dbotmaker.scaffold.writeSurface=true`.
- **A line is `fqn`, or `fqn#member(params)` with the *declared* parameter count.** Not the number of
  arguments a generator passes: `ImageTemplateGroup.of()` reaches a varargs parameter with none, and this end
  reads `MethodInfo.getParameterInfo()` and has no call site to count. Studio resolves each of its sites to
  the declaration before writing, which is what lets two vocabularies compare at all.
- The set is **29 elements**, not the 28 the previous phase's prose claimed — the scan of the real generators
  found one more than the hand count did, which is the whole argument for the rule.

---

## 2026-08-24 — the per-element pointer rules move to javac (phase 8 of 12)

**Changed:** `src/apt/java/com/botmaker/sdk/apt/ApiPointerProcessor.java` (new, a second source root),
`pom.xml` (a two-pass `maven-compiler-plugin`).

**Done**

- **Three of `ApiPointersTest`'s rules now also run as `javac` errors**, on the element and on the annotation:
  a `@Deprecated` public `api.*` element carries a `@ReplacedBy`; every target it names resolves; every
  target carries the matching `@Replaces` back-edge. The author sees them red in the IDE, on the line, while
  the annotation is still being typed — which is the moment the answer is actually known. A surefire failure
  names a class and a rule; this names a line.
- **The other rules stay in the test, and could not move.** A processor sees elements one at a time and never
  the surface at once: rule 4 (no *undeclared* double claim) is a question about every `@Replaces` in the
  API, and rules 6–7 need `-Dbotmaker.api.maxVersion`, which only the release caller can supply. The test
  remains the gate CI and `release.sh` read, so one mistake now fails the build twice — deliberately — and
  **where the two disagree the test is right**.
- **The build is two passes and the processor never enters the jar.** A `compile` execution at
  `process-sources` builds `src/apt/java` with `-proc:none` (it must exist before the API it checks, and must
  not be run over itself) into **`target/apt-classes`**, and the main compile names it explicitly via
  `annotationProcessors` + `-processorpath`. That is a deviation from the plan's `maven-jar-plugin
  <excludes>` and a stronger guarantee than one: the classes are never in the packaged directory, so no
  future packaging change can start shipping them. `annotationProcessorPaths` cannot express it — it takes
  artifact coordinates, not a directory this build just produced. Verified: `com/botmaker/sdk/apt/` appears
  in neither the jar nor the sources jar.
- **It reads mirrors, not classes.** Being compiled first, the processor cannot reference
  `com.botmaker.sdk.api.meta.ReplacedBy` at all; annotations are matched by FQN string and read out of
  `AnnotationMirror`s by hand. That is not a workaround for the build order — it is what keeps the dependency
  one-way. Explicit values only, so `@ReplacedBy` with no `value` stays distinguishable from an omitted
  annotation, which is rule 1's whole subject.
- **Verified by a throwaway probe source**, since the API today contains no pointers at all (nothing is
  deprecated yet, so the processor is a pure gate): each of the three rules failed the compile on its own
  line and column, `@ReplacedBy` with no value passed silently, and a complete pointer pair compiled clean.
  Full suite 235 tests, 0 failures.
- **The stated fallback stands**: if the two-pass setup proves fragile on JitPack, delete both executions and
  `src/apt` — nothing downstream depends on them. The surface file (phase 7) and `ApiPointersTest` are the
  load-bearing halves; this phase is ergonomics.

**Deferred / next:** phase 9 — Studio declares `ScaffoldSurface`, the SDK-side half being a committed
`scaffolding-surface.txt` and a matching `@Scaffolding` rule in `ApiPointersTest`.

---

## 2026-08-24 — the deprecation window becomes a gate, and it is a committed file (phase 7 of 12)

**Changed:** `api-surface.txt` (new, generated, 733 lines), `src/test/java/com/botmaker/sdk/api/ApiSurfaceTest.java`
(new), umbrella `release.sh` (`check_api_surface`, `refresh_api_surface`, `--allow-removal`).

**Done**

- **`api-surface.txt` is the previous release's public `api.*` surface, committed**, and `ApiSurfaceTest`
  diffs this build against it. One rule carries the phase: an element in the file and gone from the build
  must have said `[deprecated]` **in the file** — which means the previous *jar* carried `@Deprecated` and,
  by `ApiPointersTest` rule 1, a `@ReplacedBy` beside it. A removal is therefore announced one full release
  ahead or it does not happen.
- **Why a file, when the pointer gate needs no old jar.** `ApiPointersTest` verifies a link the author
  *declared*, and both ends of a declared link are in one build; that is what a deprecation window is for.
  This gate asks the one question no build can ask about itself — **what was here before?** — because a
  deletion leaves nothing behind to be scanned. So the previous answer is written down. No network, no
  resolved artifact, and the diff is reviewable like any other file.
- **It is not the japicmp gate that was deleted** (`docs/refactor/21-api-compat.md` §3). That one enforced
  *coverage*: every break had to ship a way across it. An uncovered break is now a supported outcome
  (default value + `@NeedsReview`), and this gate does not care whether anything replaces the element, only
  that the going was announced. Nor does it size the version bump — that remains a human judgement.
- **Two more rules, both about `@Since`, both about a fact that expires.** An element in both surfaces keeps
  the exact version it had — including *not having one* — because back-filling asserts something about a
  release nobody can re-check; and an element absent from the file carries one, since the commit that adds
  it is the last moment its introduction version is knowable rather than archaeology. The pre-1.1.0 surface
  has none and the first generated file records that faithfully: 733 lines, zero `since=`.
- **Parameters are erased types, not the arity the plan specified.** An arity disambiguates a name a human
  wrote (which is what a `@Replaces` entry needs); here it is the identity the diff keys on, and this API is
  full of same-arity overloads — `click(Point)` beside `click(Rect)`. Under an arity key, deleting one of
  two same-arity overloads is a line that never changes and the window rule never fires. Longer lines, a key
  that is actually unique; a collision now throws rather than silently dropping an element from the surface.
- **Regenerating is refused while a rule is broken**, and that is what makes the rules a gate. Writing first
  would leave the removed element out of the file, so the failing run would be followed by a passing one —
  two commands and the window is gone, silently. `-Dbotmaker.api.writeSurface=true` reads the committed
  snapshot *before* overwriting and writes nothing unless the three rules hold.
- **The escape hatch is element by element and expires by itself.**
  `--allow-removal 'com.botmaker.sdk.api.X#y'` (repeatable) reaches the test as
  `-Dbotmaker.api.allowUndeprecatedRemoval`. A major is allowed to break things outright and a rule with no
  exit is a rule people delete — but an entry that matches nothing is itself a failure, so a stale exemption
  cannot sit in a release script quietly permitting the next removal spelled that way.
- **`release.sh` runs it in the decide pass** (`check_api_surface`, beside `check_api_pointers`, before any
  tag is pushed) and **re-records the file in the SDK's own release commit** (`refresh_api_surface`, just
  before `commit_tag_push`) — the same reason `.deps.env` is written there: what this release shipped is
  what the next one is diffed against, and a tag should be self-describing.

**Deferred / next**

- Phase 8 moves the *per-element* pointer rules to an annotation processor so they fail at `javac` with a
  file and line. The surface file is the load-bearing half of the enforcement decision; that phase is
  ergonomics, and its own fallback is to stay in the test.

---

## 2026-08-24 — a release says what it gives you, and the jar carries the answer (phase 5 of 12)

**Changed:** `CHANGELOG.md` (new), `pom.xml` (the `whats-new` antrun execution), umbrella `release.sh`
(`changelog_section`, `check_changelog`, `publish_release`).

**Done**

- **`CHANGELOG.md`, a few bullets per released version**, seeded from the tags' own commits rather than
  from the ROADMAP — the ROADMAP is the engineering log (why, what was rejected, what it cost) and is far
  too detailed to be release notes. Same-day re-tags are recorded as what they were ("re-tagged so JitPack
  rebuilt it"), because a changelog that invents content for a mechanical tag teaches its reader to skim.
- **The whole file ships in the jar** as `META-INF/botmaker/whats-new.md`. Whole, not the section being
  upgraded to: a bot may jump several releases at once, so the jar has to answer every span ending at its
  own version. That is what lets Studio's upgrade dialog lead with what the release *gives* you, offline,
  out of a jar it already downloaded to diff — no GitHub API, no release notes, no network (phase 6).
- **antrun, not `maven-resources-plugin`.** The file has to be *renamed*: the entry name is a contract
  with Studio's reader and must not follow whatever the file is called in the repo, and `copy-resources`
  has no `fileMappers` on that goal. `failonerror=false`, so a tree with no changelog still builds — which
  is what every jar built from an older tag is.
- **`release.sh` refuses an SDK release with no section for the version being cut** (`check_changelog`,
  decide pass, no network and no mvn), and now publishes a real GitHub Release for the SDK with that
  section as its body — until now an SDK tag was a bare ref that only warmed JitPack.

---

## 2026-08-24 — a pointer becomes a set: the split (phase 3 of 12, the half that was missing)

**Changed:** `api/meta/ReplacedBy.java` (`value()` widened to `String[]`, new `whens()`),
`api/meta/Replaces.java` (new `note()`, `behaviourChanged()`, an optional arity in the entry grammar),
`ApiPointersTest` (rules 2, 3, 4, 5, 8, 9 reworked; new rule 11). **231 tests green.**

**Done**

- **Phase 3 landed only its first half on 2026-08-23**, and nothing recorded that. The four annotations
  shipped; the *split* work the plan put in the same phase did not, and neither that entry nor a Deferred
  note mentioned it. Phase 4 reads exactly these members, so it was blocked on them. This entry closes it.
- **`@ReplacedBy.value()` is a `String[]`, ordered, first preferred.** The old single-`String` model could
  not express a **split** at all — one old member becoming two, where *which* one a given call meant is a
  property of that call rather than of the member. `Mouse.scroll(int)` is the worked example: the sign of
  `notches` decides `scrollUp` from `scrollDown`, and no annotation can know a sign. `@ReplacedBy("…#tap")`
  is unchanged in source and in bytecode — a single value is already a one-element array — so nothing that
  was written before this needs touching.
- **`whens()` is what makes a split a choice anybody can make.** One sentence per candidate, same order,
  same length — *"when notches is positive"*, *"when negative"*. The target names say what each candidate is
  called; only this says when it is the right one, which is the entire question Studio will put to the user
  per call site in phase 4.5. Rule 11 refuses a split without it.
- **`@Replaces` gained `note()` and `behaviourChanged()` — the same two the forward end has, duplicated on
  purpose.** They are read out of *different jars* and only one of the two survives: a bot upgrading through
  the deprecation release reads the forward end, a bot that skipped it finds that element gone and has only
  this one. The forward note wins when both are present; the flag is a logical OR. Rule 8 now checks both
  ends separately, because the forward note cannot rescue a flag on a jar that no longer exists.
- **An entry may carry an arity — `fqn#member(2)@1.2.0`.** `@ReplacedBy` needs none (it sits *on* one
  overload, whose parameter count is in the bytecode beside it); this end names an overload that may already
  be deleted, so when only one of several was taken over, this is how the survivor says which. Rule 5 parses
  it and — only while the named element is still in this build, since that is the one case where there is
  anything to compare against — checks it matches one of its overloads.
- **Rule 4 relaxed, and this is the interesting one.** "Two survivors claim one `name@version`" *is* what a
  split looks like from the back edge, and once the old member is deleted that pair of claims is the only
  place the split still exists. Refusing it flatly, as before, would have made a split readable during its
  deprecation window and unreadable forever after. So a double claim is legal **exactly** when the claimed
  element's own `@ReplacedBy` lists precisely those claimants — checkable inside one build, while both ends
  are still compilable, which is this gate's whole design. Every other double claim is still an error, and
  Studio still reads an undeclared contested entry as unpaired.
- **Rules 2, 3 and 9 run per candidate.** A split is only as good as its weakest target: one unresolvable
  candidate in a menu of two is a menu entry that cannot be chosen. Rule 11 additionally refuses a blank
  target mixed in with real ones — `{}`/`{""}` is the whole-value statement *nothing takes my place*, and it
  means nothing sitting beside a candidate that does.
- **Each of the six new behaviours was verified by breaking it deliberately** — a legal declared split
  passing first, then a mismatched `whens()` length, a blank `whens()` entry, an undeclared double claim, a
  wrong arity, a mixed blank target, and `behaviourChanged` on the back edge with no note — reading each
  failure message, then deleting the probe.

**Deferred / next**

- **Studio's `SdkUpgradeService` still reads the old single-`String` `value()` at runtime.** It reads both
  annotations by FQN string through ClassGraph, so its *compile* is unaffected and nothing is broken today
  (no `api.*` element carries either annotation yet). Phase 4 makes `replacedBy` a `List<String>` beside
  `whens`, and gives `Claim` the nullable arity plus `note`/`behaviourChanged`; phase 4.5 makes the pairing
  graph multi-valued and adds the per-call-site choice.

---

## 2026-08-24 — the sweep reaches the types a bot can hold (phase 3.13 of 12)

**Changed:** ten value types curated — `Point` 4/4, `Size` 4/4, `MatchResult` 13/13, `ColorMatch` 9/9,
`TextMatch` 6/6, `ImageTemplate` 6/8, `CaptureSource` 7/13, `LaunchTarget` 5/8, `Key` 0/1, `MouseButton` 0/1 —
**54 offered, 13 hidden, nothing removed or deprecated**. With the 18 facades of 3.9–3.11, every type in
`api.*` a bot can reach now has a verdict. Table and reasons: `docs/refactor/22-api-audit.md` §5.

**Done**

- **These types had no reader until phase 3.12.** A value type is reached through a variable's member menu,
  which is `MenuBuilders.buildScopeMenu`, which was unfiltered until 3.12 — so curating them earlier would
  have changed nothing anybody could see. That is also why 3.10 skipped the three result types on the
  reasoning that a result is *received* rather than called: true of the statement menu, and false of the
  member menu 3.12 opened. The verdict was reversed and written into `MatchResult`'s javadoc, which had none.
- **Five of the ten are fully offered, as a verdict rather than a shrug.** A result or geometry type has no
  rival spellings: its members are different *questions* about one value, where a facade's overloads are
  different *ways of asking* one question. The type-level annotation still earns its place — under strict mode
  it is what makes "looked at, nothing hidden" a recorded fact instead of an omission.
- **Where a value type does hide something it is one of two shapes.** *Plumbing returning a type the editor
  cannot declare*: `CaptureSource.capture()`/`origin()` hidden as a pair (a `BufferedImage`, and the offset
  whose only documented use is being added to a match from it), `LaunchTarget.launchSpec()` returning
  `shared.launch`'s freely-breakable `LaunchSpec`, `ImageTemplate.unload()`/`close()` managing a `Mat` the bot
  never sees. And *implementor surface*, which is new: `CaptureSource.hasWindowIdentity()`/`click(Point)` exist
  to be **overridden** by a new kind of source — `click`'s own javadoc calls it "the single seam that lets the
  whole vision→click pipeline target an emulator" — while the supported bot path is `Emulators.use()` then
  plain `Mouse`. An override point is not a menu entry.
- **`LaunchTarget.parse(String)` is `Target.set(String)` again**: a spec grammar the user must already know,
  returning `null` rather than complaining. `LaunchTargetArgPicker` still writes `LaunchTarget.parse("…")` with
  a spec it built itself, and a picker is not a menu, so hiding the method costs the picker nothing.
- **`Key` and `MouseButton` are 0/1, where the type annotation *is* the verdict.** Both hide their one method
  and keep every constant — fields are never curated, and enum constants reach the pickers through
  `SdkType.enumConstantNames()`, which reads the `Class<?>` and never consults the index. That single method
  is exactly what separates them from `Direction` and `StartMode`, which needed no annotation.
- **`ApiPointersTest` rule 10 earned its keep mid-phase.** `MatchResult` was annotated on all thirteen members
  and not on the class; the build failed naming every one of them. A curated method in an uncurated type is
  invisible, and the gate is the only thing that would have noticed.
- **`Palette`'s javadoc gains the value-type case** — the annotation is not facade-only, every type a bot can
  hold is curated for its member menu, and the two hiding shapes above are named there.

**Deferred / next**

- **The result types stay classes, not records** — asked and answered this phase. A public record cannot keep
  the package-private constructor that makes `ImageFinder`/`Pixel`/`Text` the only things able to mint a
  result (JLS 8.10.3: the canonical constructor is at least as accessible as the record); the
  `null`-when-not-found contract lives in accessors a record would have disagreeing with its own
  `equals`/`toString`; and `MatchResult` is 6 fields behind 13 accessors, so the header would publish the
  components on top of the members and undo this phase's curation. If value semantics are ever wanted, the
  cheap version is hand-written `equals`/`hashCode` on the three.

---

## 2026-08-24 — the `@Palette` sweep finishes the facades (phase 3.11 of 12, part 3)

**Changed:** the last three facades curated — `Target` 7/9, `Session` 4/8, `Emulators` 4/9 — **15 offered, 11
hidden, nothing removed or deprecated**. All eighteen facades and 159 methods are now decided; verdicts and
reasons in `docs/refactor/22-api-audit.md` §5.

**Done**

- **`Target` inverted the sweep's usual argument verdict, which is what finally pinned down the rule.** Of its
  two `set` overloads the **`LaunchTarget`** one is offered and the **`String`** one is hidden — the opposite
  of `Time`, where only the `String` half of each `ZoneId`/`String` pair survived. They are not in conflict:
  the rule was never *prefer `String`*, it is **prefer the argument the editor can produce**. `ZoneId` has no
  picker; `LaunchTarget` has a dedicated one (`PickerRegistry` → `LaunchTargetArgPicker`, offering the game
  library and a file chooser and committing `LaunchTarget.parse("…")`), while `set(String)` takes not a name
  but a spec grammar (`steam:12345`, `exe:C:\…`) the user must already know. `current()` is hidden on the
  return side of the same fact — a `LaunchTarget` is not declarable, so a menu entry producing one hands back
  a value that cannot be named or stored. Every verb (`start`, `startIfNotRunning`, `restart`, `isRunning`,
  `launchAndWait`, `waitForLaunch`) is offered.
- **`Emulators` 4/9 — the return-value rule at full strength: a call whose only product is a handle the editor
  cannot hold is not a menu entry.** Neither `Emulator` nor `EmulatorRef` is a declarable variable type
  (Studio's `BotType` says in as many words that they "come from `Emulators.named(…)`"), so `first()`,
  `named(String)` and `connect(String, int)` are hidden: inserted from a menu, each stands as a statement that
  connects to an emulator and then discards it. `list()`/`listAll()` are the stronger form of the same fact —
  a `List<…>` is not declarable at all — and `listAll` is documented as a picker's feed rather than as bot
  vocabulary. What is offered is the four that *act*: `use()` and `use(String)`, which the SDK had already
  shipped as `first().use()` / `named(name).use()` collapsed into one statement precisely for callers who
  cannot hold the handle, plus `launch(String)` / `stop(String)`. The `Mouse.scroll(int)` shape again.
- **That verdict decides phase 3.13's list.** With every handle-producing method hidden, `Emulator`,
  `EmulatorRef` and `EmulatorSource` are unreachable from any menu, so they leave the value-type sweep — a
  member menu they can never open is not worth curating. If Studio ever makes `Emulator` declarable, `first`
  and `named` earn their annotation that day; an addition is free for the SDK's whole life.
- **`Session` 4/8 needed the least deciding of any facade, because the class had already written the verdicts
  down.** `pinnedBackend()` and `override()` call themselves *internal plumbing* (and `override()` returns a
  tri-state `Boolean` whose `null` is the interesting value); `clearOverrides()` says it exists for tests;
  `set(boolean)` is `Debug.set(boolean)` exactly — the third instance of *a flag whose two values already have
  named methods*. `isEnabled`/`enable`/`disable`/`useBackend` are the vocabulary.
- **`useBackend(String)` is the mirror of `Wait`'s `Duration`.** Its argument is a bare `String`, but the
  accepted set is closed and named in the javadoc (`gamescope`, `xephyr`, `auto`) and an unrecognised name
  degrades to `auto` rather than throwing — so a menu entry cannot produce a bot that breaks. Fillable is
  about whether the editor can write a *valid* argument, not about which package the type came from.
- Every hidden method stays public, supported and under the 1.1.0 contract. Hiding is not deprecating.

**Deferred / next**

- **Phase 3.13** — the value types, now 10 rather than 13: `Point`, `Size`, `MatchResult`, `ColorMatch`,
  `TextMatch`, `ImageTemplate`, `CaptureSource`, `LaunchTarget`, `Key`, `MouseButton`.
- Making `Emulator` declarable in Studio's `BotType` would reopen `Emulators.first`/`named`; not scheduled.

---

## 2026-08-23 — the `@Palette` sweep, ten more facades and a change to `@Palette` itself (phase 3.11 of 12, part 2)

**Changed:** ten more facades curated — `Source`, `Debug`, `BotMaker`, `Activity`, `Wait`, `Watchdog`,
`PopupGuard`, `Window`, `Bot`, `Rect` — **45 offered, 14 hidden, nothing removed or deprecated**; and
`@Palette` gained `ElementType.RECORD_COMPONENT` so a record's generated accessors can be curated at all.
Verdicts and reasons in `docs/refactor/22-api-audit.md` §5. Three facades remain: `Target`, `Emulators`,
`Session` (26 methods).

**Done**

- **The sweep's unit changed, deliberately.** §5's counts up to part 1 were public *statics*, which is what
  `StatementMenu` builds a facade submenu from. But `@Palette` gates a **type**, and
  `MethodInvocationBlock`'s ⚙ overload picker consults the curation for a call on a variable exactly as for a
  static one — so a curated type that decided only its statics claims a verdict it never took. `Window`,
  `Activity` and `Rect` therefore decide their instance methods too (+11, +8, +13).
- **`Rect` 18/0, and it forced the annotation change.** A record's accessors are generated, so there is no
  declaration to annotate: a curated `Rect` would have reported `x()`/`y()`/`width()`/`height()` as not
  offered. The first pass at this phase left `Rect` uncurated for that reason — wrong, and an
  over-generalisation, since the thirteen *declared* methods annotate fine and only the four accessors were
  out of reach. `ElementType.RECORD_COMPONENT` is now in the target set; an annotation on a record component
  propagates to whichever of field/accessor/parameter its target admits (JLS 8.10.3), and this one admits
  `METHOD` only, so it lands on the accessor and nowhere else. `javap` confirms it on the generated `x()`.
  Constructors stay outside the set: Studio inserts `new Rect(…)` through an arg picker, not this menu.
- **`Bot` 1/3, `PopupGuard` 3/5, `Activity` 6/11 — one finding, three times: a member the generated code owns
  is not a menu entry.** `Bot.start` (both), `PopupGuard.install`/`uninstall` and `Activity.execute()` are each
  written once by the scaffold, and a second call from a menu can only undo what the scaffold set up — a nested
  supervise loop, a replaced `Popups` handler, a `run()` skipping the hooks `execute()` exists to provide.
  `Bot` is the extreme case: `start` does not return, so the scaffold's call is the only one that can exist.
- **`Debug` 6/1 and `Activity`'s two `setEnabled`s — `Mouse.scroll(int)` again with a flag instead of a sign.**
  A `boolean` argument selecting between two behaviours that already have named methods. `PopupGuard.enabled(boolean)`
  is offered anyway, which is the same rule rather than an exception: there is no `enable()`/`disable()` pair to
  prefer, and the finding is about duplication, not about booleans.
- **`Wait` 5/1 corrects a rule this sweep could have misread.** `time(Duration)`/`between(…)` are offered
  despite `Duration` being a JDK type: the rule was never "JDK-typed", it is *an argument the editor cannot
  produce*, and Studio ships `DurationPicker`/`DurationFields` and treats `Duration` as a declarable `BotType`.
  `Window`'s two hidden methods are the same rule on the return side — `BufferedImage` and `GenericWindow` are
  neither declarable nor pickable, so a menu entry producing one hands back a value the user cannot name.
  `seconds(int)` is hidden as a second spelling of `seconds(double)`; `Watchdog.reset()` likewise, its body
  being `progress();`.
- **`Source` 2/0, `BotMaker` 5/0** — nothing to trim. `BotMaker`'s four `readX` look like an overload family
  and are not one: they differ in *return* type, so none can stand in for another.
- SDK suite **230 tests, 0 failures**, `ApiPointersTest` **11/11** (rule 10 holds across all ten new curated
  types). Studio **1392 tests, 0 failures** against the rebuilt SDK.

**Deferred / next**

- **Three facades left in 3.11:** `Target` 9, `Emulators` 9, `Session` 8. `Emulators` still carries the note
  from 3.7 — its nine methods are the only reason `Emulator` and `EmulatorRef` are reachable, so its verdict
  decides theirs.
- `Point` and `Size` are not in the phase's list and stay uncurated. They are now *expressible* whenever
  someone decides them, which they were not before this entry.
- `MatchResult`, `ColorMatch`, `TextMatch`, `ImageTemplate` remain deliberately uncurated (3.10) — result
  types rather than facades.

---

## 2026-08-23 — the `@Palette` sweep continues: `Time`, `BotSettings`, `Mouse`, `Game`, `Keyboard` (phase 3.11 of 12, part 1)

**Changed:** five of the eighteen facades outside `api.vision` curated — 81 offered, 11 hidden, nothing
removed or deprecated. Verdicts and reasons in `docs/refactor/22-api-audit.md` §5.

**Done**

- **`Time` 22/9** — the phase's one genuinely new shape. The duplication here is not an overload family but a
  parallel *vocabulary*: `hourUtc`, `minuteUtc`, `secondUtc`, `millisecondUtc` and `formatUtc` ask exactly what
  their local twins ask, in the zone `setDefaultTimeZone` already sets. Hidden. But `nowUtc()` and
  `isBetweenUtc` are **not** — a bot watching a local play window *and* a UTC server reset needs both zones in
  one run, and a global default holds only one, so the property does not actually substitute there. The rule is
  applied where it holds and suspended where it does not; UTC costs two entries instead of seven.
- Two smaller `Time` verdicts: only the `String` half of each `ZoneId`/`String` pair is offered (`ZoneId` has no
  `SdkType`, so the argument is unfillable from the editor — the same fact that hid `Text`'s `OcrOptions`
  overloads in 3.10), which also hides `getDefaultTimeZone()` on its own; `nanoTime()` is a bare `System`
  passthrough at a resolution no screen automation reads.
- **`BotSettings` 19/0**, and the zero is load-bearing. Everything 3.10 hid, it hid by saying *a property
  already answers that* — and this class is that property store. Hiding any of it, readers included, would make
  the earlier justification false where the user acts on it: the per-call knob taken away, and its replacement
  not shown.
- **`Mouse` 15/1** and **`Game` 15/1** — in both the verdict was already written in the author's own javadoc
  ("Prefer the clearer `scrollUp` / `scrollDown`"; "Convenience overload accepting a numeric appId") and the
  menu had been contradicting it by offering every shape as an equal. `scroll(int)`'s signed argument is exactly
  the ambiguity the named pair removes; `launchSteam(int)` only `Integer.toString`s a value Studio's game picker
  already produces as a string.
- **`Keyboard` 10/0** — five operations × the plain/`CaptureSource` pair, which is `ImageFinder`'s rule with no
  third variant to trim.
- SDK **230 tests green**, `ApiPointersTest` 11/11 (rule 10 — no curated method in an uncurated type — holds
  across all five). Studio **1392 green** against the rebuilt SDK, `SdkSurfacePaletteTest` 8/8 and
  `SignatureKeyAgreementTest` 1/1.

**Deferred / next**

- **Phase 3.11 part 2**, the remaining thirteen facades / 67 methods: `Target` 9, `Emulators` 9, `Session` 8,
  `Debug` 7, `Wait` 6, `Watchdog` 6, `BotMaker` 5, `PopupGuard` 5, `Window` 3, `Bot` 3, `Activity` 3,
  `Source` 2, `Rect` 1. One facade per commit; each stays uncurated (and its menu unchanged) until decided.
- `Emulators` is the one carrying a note from 3.7: its nine methods are the only reason `Emulator` and
  `EmulatorRef` are reachable at all, so its verdict decides theirs.

---

## 2026-08-23 — the `@Palette` sweep: the vision facades (phase 3.10 of 12)

**Changed:** `api/vision/` — `ImageClicker`, `Text`, `Pixel`, `Vision`, `ImageWaiter`, `Precision`,
`ImageTemplateGroup` and `Matches` curated. 102 offered, 29 hidden, nothing removed. Verdicts and reasons in
`docs/refactor/22-api-audit.md` §5.

**Done**

- **`ImageClicker` — 29 of 42.** The `ImageFinder` rule verbatim (plain and `CaptureSource` forms offered,
  bare `double confidence` hidden, the three `*Compare` families keeping every shape), plus one extra: the
  four-argument core `click(t, source, double, int delayMs)` is hidden twice over, because `delayMs` has a
  home in `BotSettings.foundDelay()` exactly as the threshold has one in `ImageTemplate.threshold()`.
- **`ImageWaiter` — 6 of 12.** The same rule, unmodified. The timeout stays a parameter in every offered
  shape: it is the question these methods exist to ask.
- **`Text` — 13 of 22, and this is where `@Palette` pays for itself.** The nine `shared.ocr.OcrOptions`
  overloads are hidden. The audit's §4 had recorded them as the third type leak and left them open, since the
  only lever was deletion and the overloads are genuinely useful. The palette answers it exactly:
  `OcrOptions` lives in `botmaker-shared`, so it is not an `SdkType` — Studio has no picker for it, no import
  and no declarable variable of that type, and an offered `read(source, opts)` hands the user a block whose
  second argument **cannot be filled from the editor at all**. Hiding a broken menu entry costs the API
  nothing; the methods stay public for hand-written code, which is where tuned OCR is written anyway.
- **`Pixel` — 19 of 19, and the reason matters more than the count.** A mechanical reading of the rule would
  have gutted it. The rule is not "hide the extra parameter", it is *"hide the parameter whose question a
  property already answers"*. A `java.awt.Color` is a JDK type and cannot carry a tolerance the way
  `ImageTemplate` does, so there is no property to teach instead — `Precision` **is** that place, it is a
  palette type with its own picker, and it varies per colour rather than per bot.
- **`Vision` 17 of 17, `Precision` 6 of 6, `Matches` 9 of 9, `ImageTemplateGroup` 3 of 4** (`toArray()`
  hidden: it exists so the varargs matchers can be reached from a group, which is plumbing between two SDK
  classes, and the palette has no reason to teach a bot author to hold an `ImageTemplate[]`).
- **Annotating a facade that hides nothing is still worth the commit.** Strict mode means the type-level
  annotation is what *fixes* the verdict — and a method added to `Pixel` or `Vision` later is hidden until
  somebody decides otherwise, which is the polarity the phase was chosen for.
- **Three facades carry instance verdicts, not only static ones.** `Precision`, `Matches` and
  `ImageTemplateGroup` are values a bot holds — a `Precision` variable, the lambda parameter of `ifFindAny` —
  so what they offer is asked through the value rather than through a static facade submenu. `toString` is an
  `Object` override and never a menu entry in either vocabulary.
- **§3's keep list re-checked, which the phase's verify step requires.** Everything §3 kept *because a picker
  seeds it* is still offered: `clickAny(ImageTemplate…)`, `clickAny(CaptureSource, ImageTemplate…)`,
  `clickEachLast(…)`, `clickAllLast(…)`, `Matches.hasAll`/`hasAny`, `ImageTemplateGroup.of(ImageTemplate…)`.
  The only varargs shapes hidden also take a `double confidence`, and the picker is reachable through the
  offered pair. This was the one regression the sweep could cause silently.
- Two javadoc stragglers from the 3.8 accessor rename fixed in passing: `Pixel`'s class sample said
  `Vision.getLastColorMatch()`, and `Matches` linked `MatchResult#getTemplateId()`.

**Deferred / next**

- **Phase 3.11 — everything outside `api.vision`**, 159 methods: `Time` 31, `BotSettings` 19, `Game` 16,
  `Mouse` 16, `Keyboard` 10, `Target` 9, `Emulators` 9, `Session` 8, `Debug` 7, `Wait` 6, `Watchdog` 6,
  `BotMaker` 5, `PopupGuard` 5, `Window` 3, `Bot` 3, `Activity` 3, `Source` 2, `Rect` 1. One facade per
  commit, verdicts appended to audit §5.
- **`MatchResult`, `ColorMatch`, `TextMatch` and `ImageTemplate` stay uncurated** — they are result types
  rather than facades, and were out of 3.10's scope. Deciding them is a judgement 3.11 or later can make;
  until then their menus are unchanged, which is exactly what the type-level gate is for.

---

## 2026-08-23 — `@Palette`: a method can leave the menu without leaving the API (phase 3.9 of 12)

**Changed:** new `api/meta/Palette.java`; `api/vision/ImageFinder.java` annotated as the pilot (42 of its 54
overloads offered); a tenth rule in `ApiPointersTest`.

**Done**

- **The lever the audit did not have.** `docs/refactor/22-api-audit.md` §3 kept recording methods that are
  worth having and not worth browsing, and could do nothing about them: Studio's statement menu enumerates
  *every* public static method of every facade, so "which methods exist" and "which methods Studio offers"
  were one question and the only lever was deletion. `@Palette` separates them.
- **Hiding is not deprecating.** An unannotated method stays public, supported, and under the same contract —
  a bot calling it compiles, keeps compiling and is migrated across renames like any other call. It is simply
  not *proposed*. `@Deprecated` says "stop using this"; the absence of `@Palette` says "we do not lead with
  this", and promises nothing about the future.
- **It is the one lever that outlives the free window.** Removing or renaming costs a major version from
  1.1.0; adding an annotation costs nothing, ever. Curation stays available for the whole life of the API.
- **Strict, and per overload.** In a curated jar nothing is offered without the annotation, so a newly added
  method is invisible until someone decides it earns a menu entry. Per *overload* because the surface's size
  is mostly one systematic pattern — most matchers exist in four shapes — which a per-name switch could not
  touch.
- **An old jar is detected, not guessed.** Studio looks for the annotation class itself in the index it
  already builds of `com.botmaker.sdk.api` (which contains `api.meta`). Absent → uncurated → today's menu
  byte for byte. No version comparison anywhere.
- **A facade is uncurated until its type carries `@Palette`**, which is what lets the sweep proceed one
  facade at a time. Rule 10 of the gate makes the *half-done* state the error instead: `@Palette` on a method
  whose declaring type has none changes nothing and shows nothing, so it fails the build.
- **The pilot rule on `ImageFinder`:** every operation is offered; of each matcher's four shapes, the plain
  form and the `CaptureSource` form are, and the bare `double confidence` ones are not — a per-call threshold
  is the second answer to a question `ImageTemplate.threshold()` already answers, and the image picker sets
  that one. The `*Compare` families keep all four, their `double` being a comparison *margin* with no other
  home. 42 offered, 12 hidden.

**Deferred / next**

- **Phases 3.10 / 3.11 — the sweep**, the remaining 25 facades (176 vision methods, then 159 elsewhere), one
  facade per commit, recorded as §5 of the audit. The regression to watch: anything hidden that audit §3
  recorded as kept *because a picker seeds it* — the varargs `findAny`/`clickAny` families.

---

## 2026-08-23 — `VisionContext` → `Vision`, and the accessors drop `get` (phase 3.8 of 12)

**Changed:** `api/vision/VisionContext.java` → `api/vision/Vision.java`; the `get` prefix dropped from every
accessor on `MatchResult`, `ColorMatch`, `TextMatch`, `ImageTemplate`, `Rect` and `Vision`; Studio's
`palette/SdkType.VISION_CONTEXT` → `VISION` and the three `BotType` initializer seeds repointed.

**Done**

- **One spelling for accessors across `api`.** `api.geometry` became records of `int`s in phase 3.5, so
  `Rect.width()` sat one import away from `MatchResult.getWidth()` — and `Rect` contained *both*
  conventions itself, `getCenter()` next to `x()`, `size()` and `area()`. The rule adopted, and the whole of
  it: **an accessor drops `get`; a mutator keeps `set`.** `ImageTemplate.setThreshold` is unchanged, and
  `isFound`, `inFrame`, `lastMatchFound`, `clearLastMatch` and `ifLastMatch` were never accessors and keep
  their names.
- **`VisionContext` is `Vision`.** "Context" was a placeholder noun for a class that is really *what the last
  search saw*, and the call site is what a bot author reads:
  `VisionContext.getLastMatch().getCenter()` against `Vision.lastMatch().center()`. Its accessors moved with
  it — `lastMatch`, `lastMatchList`, `lastMatches`, and the colour/text equivalents.
- **The private `ThreadLocal` fields now share their accessors' names** (`lastMatch` the field, `lastMatch()`
  the method). Legal — fields and methods are separate namespaces — and exactly what a record does; noted
  because it reads as a clash at first glance and is not one.
- **The compiler enumerated the call sites, not a grep.** `getWidth` alone matches 142 places in the two
  modules and almost none of them are ours (JavaFX nodes, `BufferedImage`, shared's `GenericWindow`), so the
  method was: rename in the declaring class, compile, fix exactly what javac names, repeat. The one class of
  site javac *cannot* see — an SDK member named in a string literal — was swept separately, and found one:
  `LambdaCallBlock`'s hint text offering `m.getCenter()` to the user.
- **`MatchResultNullContractTest` needed both halves moved together**: it pairs a method reference with a
  name *string* and reflects over `MatchResult` to prove the covered list is complete, so a half-rename would
  have left it passing while covering nothing.

**Deferred / next**

- **Existing bots take a plain compile error**, by decision. `ImportManager.repairSdkImports` keys on a simple
  name, so it carries a package move for free but not a class rename, and a *member* rename is the
  `@ReplacedBy`/`@Replaces` machinery, deliberately unused pre-contract. Three bots, all the maintainer's —
  the same trade phase 3.6 took.
- **Phase 3.9 — `@Palette`, the curation layer.** This audit's §3 recorded methods *worth keeping but not
  worth offering* and had no lever but deletion. `@com.botmaker.sdk.api.meta.Palette` separates the two:
  strict whitelist, per overload, read from the jar by the ClassGraph scan Studio already runs. **Hiding is
  not deprecating** — an unannotated method stays public, supported and under contract.

---

## 2026-08-23 — the method audit: three type leaks and one duplicated field (phase 3.7 of 12)

**Changed:** every public method in `api.*` read once before the contract starts at 1.1.0, with a verdict and
a one-line reason recorded in **`docs/refactor/22-api-audit.md`** — the authoritative record; this entry is a
summary, not a substitute.

**Done**

- **The audit's own rule, worth keeping:** *spend the window on what the window is for.* Removals and renames
  are free today and expensive forever after; an **addition** is free today *and* free forever after (a minor
  bump, any time). So this phase applied removals, demotions and renames, and deferred every "there should
  also be an overload that…" — §4 of the doc lists six, none of which gets harder later.
- **537 public methods is mostly an artefact.** Matchers take an optional `CaptureSource` and an optional
  threshold, so most operations exist ×4: `ImageFinder`'s 54 methods are 16 operations. The menu collapses
  overloads to one entry per name already. The audit's conclusion is *not* "cut deeply" — it is that the real
  defects were invisible to a method count.
- **Removed `ImageClicker.clickBest(ImageTemplate …)` ×4** — `click(ImageTemplate …)` under a second name
  (both call `ImageFinder.findInternal`; "best" is only a question across several templates, which is what the
  `ImageTemplateGroup` overloads, kept, are for). It was the worse of the two: it never reached
  `VisionContext.setLastMatch`, so `getLastMatch()` went stale after it — the opposite of its own Javadoc.
- **Removed `BotSettings.defaultTimeZone()` / `setDefaultTimeZone(String)`** — a genuine bug, not just a
  duplicate. Two fields synced one way: `BotSettings` pushed into `Time`, but `Time.setDefaultTimeZone` (the
  one `Time.now()` reads) never pushed back, so after a bot called it the two disagreed and `BotSettings`
  answered a zone nothing was using. No project-properties key ever seeded it. `Time` owns the timezone.
- **Two of three type leaks closed.** `api.*` is under contract from 1.1.0; shared and OpenCV are explicitly
  freely breakable — so a public `api` signature naming their types promises a spelling nobody keeps.
  `ImageTemplate.getMat()` (returned `org.opencv.core.Mat`; zero callers outside `api.vision`) is
  package-private. `CaptureSource`/`Window.targetWindow()` (returned shared's `GenericWindow`) is now
  `internal.capture.WindowBacked`, implemented by `Window`, `NamedWindow`, `SessionSource` and the new
  `RegionSource`; `Keyboard` asks `WindowBacked.of(source)`. **Nothing in `api` names `GenericWindow` or
  `Mat`.**
- **`CaptureSource.region(Rect)` returns a named `internal.capture.RegionSource`** instead of an anonymous
  class — an anonymous class can implement only the type it is written as, and a region of a window has to be
  both a `CaptureSource` and `WindowBacked`. It also lands where every other implementation went in 3.6.
- **The near-misses are written down** (§3): the `findAny`/`clickAny` varargs families were nearly cut as a
  second spelling of the group form and are in fact first-class in the editor — `MethodInvocationBlock` gives
  every `ImageTemplate` vararg its own image picker. `Emulators`' nine methods, which the plan singled out,
  are all kept: `Role.VALUE` already keeps `Emulator`/`EmulatorRef` out of the palette.

**Deferred / next**

- **Phase 3.8 — the accessor rename** — decided here, specified in §2 of the audit doc, **landed the same
  day**; see the entry above.
- **The third leak stays open, deliberately:** `Text`'s nine `shared.ocr.OcrOptions` overloads plus
  `DEFAULT_OPTIONS`. Unlike the other two it is a working, documented feature, so removing it alone is a
  capability regression and replacing it means designing an `api.vision`-owned tuning type — which the plan
  puts out of scope for an audit. Top follow-up in §4.

---

## 2026-08-23 — the `api` package reorganised, eleven classes demoted to `internal` (phase 3.6 of 12)

**Changed:** 21 classes moved package; `api/capture/Screen.java` deleted; ~65 files' imports repointed;
Studio's `palette/SdkType` lost 10 constants; new `ImportManager.repairSdkImports` + `CodeEditor` entry
point + `ImportManagerSdkMoveTest`; `SdkUpgradeService` now reads both pointer spellings.

**Done**

- **The `api` root is empty.** It held the four pointer annotations, the three geometry records and five
  facades (`BotMaker`, `BotSettings`, `Debug`, `Session`, `Time`) while everything else already lived in a
  sub-package that said what it was — and `api.core` existed to hold one enum. Now: **`api.geometry`**
  (`Point`, `Rect`, `Size`, `Direction` — `api.core` dissolved), **`api.meta`** (`ReplacedBy`, `Replaces`,
  `Since`, `Scaffolding`), **`api.util`** (`Time`, `BotMaker`, `Debug`), and `Session`/`BotSettings` into
  the `api.bot` they belong to.

- **Eleven classes left the public API on one rule: a type a bot can only ever *receive* is not API.** The
  `CaptureSource` implementations (`Desktop`, `Monitor`, `NamedWindow`, `SessionSource`) are only ever
  returned — `CaptureSource.desktop()/monitor()/window()` and `Source.current()` all declare the
  *interface* — so they moved to `internal.capture`; `NamedWindow`'s constructor had to become public,
  which is the only visible trace of the move. The observation stack (`Bots`, `BotObserver`, `Surface`,
  `ClickEvent`, `MatchEvent`, `SwipeEvent`) had **zero** references in Studio and one consumer anywhere —
  `internal.observe.IpcObserver`, which it now sits beside. `SwipeEvent` was never even in Studio's
  `SdkType`, so it had been invisible API the whole time.

- **`Screen` was deleted.** Zero callers in the SDK, in Studio, or in generated text; not a
  `CaptureSource` despite `SdkType`'s comment saying it once was; and its `captureOrigin()` reached
  `java.awt.Toolkit` through `ScreenCapture`, which is the wrong thing to hold in a headless session.

- **No pointers, deliberately.** The contract starts at 1.1.0, the newest tag is v1.0.26 and the gallery
  index holds three bots, all the maintainer's. A `@ReplacedBy` for a move nobody can be holding is noise
  `ApiPointersTest` would then verify at every future version. Recorded in the CHANGELOG (phase 5) instead.

- **Studio repairs the imports rather than letting projects open red.** Every existing bot has
  `import com.botmaker.sdk.api.Point;`, which no longer resolves — a hard compile error on a line the user
  never wrote. `ImportManager.repairSdkImports` repoints an `api.*` import by asking `SdkType` for the
  simple name's current FQN. A name `SdkType` does not know is **left alone**, not dropped or guessed: a
  wrong import compiles into a different type, an untouched one fails where the user can read why — and
  nothing a bot could have named falls in that gap, which is the whole premise of the demotion.

- **`SdkUpgradeService` reads both pointer spellings**, and the legacy one is load-bearing rather than
  polite: the jar being upgraded *from* is by definition older, so for a bot coming off 1.0.x it is the
  only jar carrying `@ReplacedBy` on the element that bot still calls. Reading only `api.meta` would have
  turned every pre-1.1.0 redirect into an unpaired break.

**Deferred / next**

- **Phase 3.7 — the method audit.** `StatementMenu.sdkFacadeSubmenu` builds a submenu from *every* public
  static method of every menu facade, resolved at runtime from the bot's own jar, so all ~537 public
  methods are one click away and there is no curation layer. The criterion there is editorial ("would a bot
  author reach for this?"), never "has it a caller" — every facade method has a possible caller by
  construction.

---

## 2026-08-23 — Point/Rect/Size are records of ints (phase 3.5 of 12)

**Changed:** `api/Point.java`, `api/Rect.java`, `api/Size.java` rewritten as records; 13 call sites in
`api.*`/`internal.*` and 8 test classes moved to accessors.

**Done**

- **The three geometry types were `org.opencv.core.*` clones and are now records.** Public mutable
  `double` fields, `set(double[])`, `double[]` constructors, `clone()`, `Size(Point)`, `Rect(Point, Size)`
  and `Rect.fullScreen()` were copied wholesale from OpenCV; **nothing in the SDK, Studio or any generated
  file ever mutated one, and the vestigial half had zero callers**. The mutability bought nothing and cost a
  real defect: `Point` and `Rect` declared no `equals`, so `p1.equals(p2)` in a bot was identity comparison —
  a silently wrong answer rather than a compile error. Records give `equals`/`hashCode`/`toString` for free.
- **`int`, not `double`, in all three.** Every producer is a pixel (a window origin, a match's top-left, a
  capture region) and every consumer is an input event the native layer can only deliver at a whole pixel.
  The old `double` was carried only to be cast straight back: `(int) p.x` appeared at fourteen call sites,
  and `Size.toString` already printed `(int) width`. The three genuinely fractional producers — `Rect`'s and
  `MatchResult`'s midpoints, `Pixel`'s centre of mass, `Mouse.drag`'s interpolation — **round at the point
  they are built** rather than carrying the fraction to a consumer that will discard it. `Mouse.drag` rounds
  per step instead of truncating, which had biased every step of a glide towards its start.
- **Immutability deleted the defensive copies.** `ColorMatch.getCenter`/`getTopLeft`, `MatchResult.getTopLeft`
  and `TextMatch.getBounds` each built a fresh instance to avoid handing out a mutable field; they now return
  the field. `Rect(Point, Size)` replaced the two `new Rect((int) location.x, (int) location.y, w, h)` sites.
- **Why now: this was the last release it is free in.** The contract starts at 1.1.0 and the newest tag is
  v1.0.26. After that this is a major, and worse, it is the one break the pointer model provably cannot
  carry — a public field becoming an accessor turns `p.x` into `p.x()`, and a bare instance-field read is not
  a call, so `SdkReferences` never sees it and `CallMigrator` has nothing to rewrite. It would land in the
  `TYPE_REMOVED`-style refusal branch across 42 public signatures.
- **Studio needed no change.** `VariableWire.geometryHelper` already emitted `int[] n = ints(s, N);
  new Point(n[0], n[1])` — the generated bots were always passing ints and the SDK was widening them. The
  pickers write and parse the same `new Point(x, y)` text. `CLAUDE.md` line 72 had already been describing
  these three as records for some time; it is now accurate.
- **Deferred:** an audit of the rest of `api.*` for members in the same category (vestigial, misnamed or
  never called) is worth doing in the same free window — see *Deferred / next*.

---

## 2026-08-23 — four annotations written while both ends still exist (phase 3 of 12)

**Changed:** `api/ReplacedBy.java` (`note`, `behaviourChanged`), new `api/Since.java`, new
`api/Scaffolding.java`, `ApiPointersTest` (rules 7–9), and 18 `api.*` files annotated.

**Done**

- **`@ReplacedBy` gained `note` — the author's own sentence, shown verbatim.** Everything else about a move
  is machine-readable and therefore says only *what*; this is the one channel for *why* ("the new one measures
  from the template's centre"). `migrations.json` had a `summary` field that was deleted with it and nothing
  replaced it. Studio prefers it over its own generated wording and never paraphrases or truncates it.
- **`@ReplacedBy` gained `behaviourChanged` — the one gap the model cannot see by construction.** Studio
  decides whether to take a pointer by comparing *shapes*, so a same-shape redirect lands silently, which is
  the point of a rename. "Same shape, different behaviour" is that same case, and the bot then compiles and
  quietly does something else. Nothing in the bytecode reveals it, so the author says it, and Studio marks
  every redirected site for review with the note as the mark's text.
- **New `@Since`, and deliberately *not* back-filled.** The version an element first shipped in is
  unrecoverable after the fact — a jar diff needs both jars and nobody kept them — so a guessed value is worse
  than none. **The pre-1.1.0 surface carries none**, which is a decision recorded in the annotation's Javadoc
  rather than an omission; every element added from 1.1.0 on carries one. It is what lets the upgrade dialog
  group additions by release instead of showing one flat alphabetical diff, and an absent value degrades to
  exactly today's behaviour.
- **New `@Scaffolding` — Studio writes this element into the files it generates.** These break differently
  from the rest of the API: a bot's own code is migrated (default value + review mark, and it compiles),
  generated code is *regenerated*, and a defaulted value inside a generated file is a broken feature rather
  than a repair. Studio's only answer there is to refuse the upgrade, or the Activity Flow edit, until Studio
  itself is updated — a much sharper consequence, and one an SDK author renaming `Watchdog#checkpoint` should
  see at the declaration rather than a release later.
- **28 elements annotated, read out of the generators rather than guessed.** Seeds:
  `BotMaker#print`, `Bot#start`/`#stop`, `PopupGuard#install`, `Debug#error`, `Watchdog#checkpoint`,
  `Activity` (+ `#isEnabled`, `#run`, `#active`, `#execute`), `ImageFinder#whileFindAny`,
  `ImageTemplateGroup` (+ `#of`). Regenerated: the same plus `PopupGuard#enabled` and `Wait#milliseconds`,
  and — through the `Activities` variable helpers — `ImageTemplate`, `Precision`, `Key`, `MouseButton`,
  `Direction`, `Point`, `Rect`, `Size` with the constructors those helpers call. The plan's provisional list
  named `ClickConfig` (gone — it is `BotSettings` now) and `Precision.TIGHT` (no generator emits it) and
  missed the whole `Activities` half; phase 9 reconciles the set with Studio's own declaration mechanically,
  so this stops being a list anybody maintains by eye.
- **Three new gate rules in `ApiPointersTest`** (now 9 + the scan sanity check): every `@Since` is a semver
  and, at release time, not dated after the version being cut — *absence is never an error*;
  `behaviourChanged = true` requires a non-blank `note`, since a review mark with no sentence is worse than
  the silent redirect it replaced; a `@Deprecated` `@Scaffolding` element requires a **non-empty**
  `@ReplacedBy`, because "nothing takes my place" is not a repair generated code can take. Each was verified
  by breaking it deliberately and reading the failure, then reverting.

## 2026-08-23 — the docs the pointer plan owed (phase 1 of 12)

**Changed:** `CLAUDE.md` (the `@ApiId` + `migrations.json` bullets replaced by the pointer pair and the gate).

**Done**

- **`@ApiId` and `migrations.json` are described as deleted, not as live.** The module's `CLAUDE.md` still
  told a reader to keep an id across a rename and to write a pairing into a JSON file that no longer exists.
  It now carries the pointer grammar (`fqn` / `fqn#member` / `fqn#<init>`; `""` as an explicit "nothing takes
  my place"; `@Replaces` entries `fqn[#member]@<version>`, never pruned), why there are two ends (a bot holds
  only two jars, and the back-edge is the only thing that survives the deprecated element's deletion), and
  the rule that both halves are written **in the release that makes the change**.
- **The gate is named as a gate, and bounded.** `ApiPointersTest`'s five always-true rules plus the opt-in
  `-Dbotmaker.api.maxVersion`, with the sentence that keeps it from growing back into the thing that was
  deleted: *it is not a coverage rule* — an uncovered break is a supported outcome.
- Cross-module docs moved with it: `../docs/refactor/21-api-compat.md` §3–§4, `../docs/refactor/99-progress.md`,
  the umbrella `CLAUDE.md` (*API stability* and *Releasing* — the latter had claimed an `--sdk` release needs
  no `mvn` in the decide pass, which `check_api_pointers` made untrue).

## 2026-08-23 — the pointer gate: `ApiPointersTest`, in CI and in `release.sh` (phase 2 of 6)

**Changed:** `src/test/java/com/botmaker/sdk/api/ApiPointersTest.java` (new), `pom.xml` (ClassGraph
4.8.179 at **test scope**), `.github/workflows/ci.yml` (one targeted step; the stale `@ApiId` /
`migrations.json` paragraph rewritten), umbrella `release.sh` (`check_api_pointers` in the decide pass).

**Done**

- **A redirect is checked, not trusted — starting with the SDK's own end of it.** Six `@Test`s over a
  ClassGraph scan of this build's `target/classes`: (1) every `@Deprecated` public `api.*` element carries
  a `@ReplacedBy`, a target or a deliberate empty value; (2) a named target exists in this build; (3) the
  target carries the matching `@Replaces` back-edge; (4) no two elements claim one `name@version`; (5)
  every entry parses, its version is a semver, and it may name a live element only while that element is
  the deprecated one pointing back; (6) — release-time only — no entry is dated after the version being
  cut. A seventh asserts the scan saw the API at all, so an empty classpath cannot pass the other six
  vacuously.
- **Why it needs no previously published jar.** A deprecation window puts **both ends in the same build** —
  the deprecated member is still there, that is what the window is for. So the back-edge is written and
  verified while the element it names is still compilable, and by the time that element is deleted a
  release later, the entry is already proven. Nothing here fetches, resolves or diffs an old artifact.
- **It is not the gate that was deleted.** `docs/refactor/21-api-compat.md` §3 records a japicmp gate
  removed on 2026-08-22 for enforcing **coverage** — and an uncovered break is now a *supported* outcome
  (default value + review mark). No coverage rule and no version-bump rule return: these checks ask only
  whether a link the author **did** declare is complete. §3's two traps are sidestepped too — the rules
  are wrong at *every* version, so CI needs no version awareness, and there is no japicmp, no Java-26
  Groovy and no third source root.
- **The scan is pinned to the main output**, via the code source of `ReplacedBy.class`, not the plain
  classpath: the test sources sit in the same package, and a fixture must not be able to fail — or
  accidentally satisfy — a rule about the API surface.
- **The failure message opens with the offenders, then explains.** Surefire's one-line summary is
  truncated at the first newline and is all the CI log and `release.sh` show, so a message that opened
  with prose named no element.
- **Two places it bites.** `ci.yml` gains one step (the single documented exception to that file's
  "CI is a compile gate, tests run locally" rule — the thing it protects cannot be caught later, since a
  missing back-edge compiles fine and becomes unrecoverable the release the member is deleted).
  `release.sh` gains `check_api_pointers`, called from the **decide** pass beside `check_sdk_floor`, for
  its neighbour's reason: a pushed tag cannot be edited. It adds `-Dbotmaker.api.maxVersion=$SDK_VER`,
  the one check only the release caller can make. Needs `mvn`; no network; `--force` overrides.
- ClassGraph is **test scope only** — this is a library a bot compiles against, and the gate must never
  put a scanner on a bot's classpath. It is the version Studio pins, so the gate and the consumer parse a
  CLASS-retention annotation the same way by construction rather than by coincidence.

**Deferred / next** — phases 3–6 are Studio-side and doc work: composed pairing from both ends in
`SdkUpgradeService`, the checked `Redirect` at the call site, the Modernise action, then the docs.

---

## 2026-08-23 — `@ReplacedBy` / `@Replaces` replace `@ApiId` (phase 1 of 6)

**Changed:** `api/ReplacedBy.java` and `api/Replaces.java` (new); `api/ApiId.java` (deleted) and the
annotation + import stripped from all 54 public types under `api/**`;
`src/main/resources/META-INF/botmaker/migrations.json` (deleted, with its now-empty directory).

**Done**

- **A pointer instead of an identity.** `@ApiId` had two limits the maintainer hit: an id cannot be
  corrected once published (retire-never-reuse makes a mistake permanent), and it pairs **types only** —
  a renamed or moved *method* had no mechanism, which is why `migrations.json` survived beside it as a
  second one. Both are replaced by a single pair of annotations that name a **replacement** rather than
  asserting an identity, reach methods, constructors and fields, and can be corrected in a later release.
- **Both ends, because a bot holds only two jars.** `@ReplacedBy` sits on the deprecated element and is
  read out of the bot's **own (older)** jar — the bot still spells the member the old way. `@Replaces`
  sits on the survivor and is read out of the **newer** jar, which is the only place the answer survives
  once the deprecated member is finally deleted. Either resolves one hop; composed they resolve a chain
  (`a`→`b` announced in 2.0, `b`→`c` in 3.0) for a bot that skipped both releases, **with no intermediate
  jar ever fetched**.
- **Grammar**, lifted from the deleted `migrations.json` `_readme` so nothing was re-invented: `fqn` for a
  type, `fqn#member` for a method or field, `fqn#<init>` for a constructor (an enum constant *is* a static
  field). A `@Replaces` entry additionally carries `@<version>` — the last release that spelling existed
  in — which is what lets Studio filter by era and what distinguishes two entries naming the same member at
  different points in the API's history. Neither `#` nor `@` occurs in a Java FQN, so the parse is
  unambiguous. **No arity in the string:** the annotation sits on one overload, so the bytecode already
  knows the parameter count of both ends.
- **An empty `@ReplacedBy` is an explicit statement**, not an omission — "nothing takes this element's
  place", which Studio reads as default-and-mark. The annotation stays required on every deprecated public
  element so the author decides rather than forgets; phase 2's build gate is what checks that.
- **Why a declared redirect is safe now when it was rejected a day ago.** The objection was that two
  members need not share a return type. Studio holds both jars, so it no longer has to trust one: in
  **statement** position the target's return type cannot matter and the redirect is always taken (today
  that call is *deleted*, losing work); in **expression** position the redirect is taken only when the new
  type fits where the old one did, and otherwise the call still falls back to a default value. A wrong
  pointer therefore degrades, it does not produce a bot that compiles and misbehaves.
- **CLASS retention, verified end to end.** A scratch class compiled against the built jar and read with
  `javap -v` shows all three forms under `RuntimeInvisibleAnnotations` —
  `ReplacedBy(value="com.example.New#tap")`, `Replaces(value=["com.example.Old#click@1.2.0"])`, and the
  bare `ReplacedBy` whose empty value is left to the annotation's default. RUNTIME would put this in every
  running bot's reflection data for nothing.

**Deferred / next (this plan's remaining phases)**

- Phase 2: `ApiPointersTest` + the CI step and `release.sh check_api_pointers` that make the link checkable
  offline from a single build.
- Phases 3–5 are Studio-side (composed pairing, the checked redirect at the call site, a Modernise action);
  phase 6 is docs.

---

## 2026-08-22 — `@ApiId`, renames only, and the end of api-check

**Changed:** `src/main/java/com/botmaker/sdk/api/ApiId.java` (new) and every one of the 54 public types
under `api/**` (annotation + import only); `src/main/resources/META-INF/botmaker/migrations.json` (rewritten
to schema 2); `src/api-check/` (deleted), `pom.xml` (the `api-check` profile and the four `botmaker.api.*`
properties deleted), `.github/workflows/ci.yml` (the three gate steps and `fetch-depth: 0` deleted),
`CLAUDE.md`. Umbrella side: `release.sh` (`check_api_bump` + `level_between` deleted),
`docs/refactor/21-api-compat.md` (§2–§5 rewritten), `CLAUDE.md`.

**Done**

- **The repair model inverted, and took the enforcement with it.** Yesterday's engine carried a break across
  by *pointing one member at another*. The maintainer's judgement: that is guessing — two members need not
  share a return type, an arity or any semantics, and the failure mode is a bot that compiles and behaves
  differently, which is worse than a compile error. So a call to a member the newer jar does not offer is now
  replaced with a **default value of the type it used to return** (`void` → the statement is deleted) and the
  enclosing function is marked for review. Nothing is declared, so there is nothing to check.
- **Deleted, all of it:** `ApiRulesCheck` and its `src/api-check/java` source root, the `api-check` profile,
  japicmp, the `api-verdict.json` protocol, the CI gate steps, and `release.sh`'s `check_api_bump`. One day
  old. **Stated cost, accepted:** nothing now refuses a breaking change released as a patch, and nothing
  catches an unannounced removal at build time. §2 of `21-api-compat.md` is a convention.
- **`@ApiId` is what survives, because a rename must stay a rename.** `@Retention(CLASS)`,
  `@Target({TYPE, METHOD, FIELD})`, one kebab-case `String value()`. `ImageClicker` → `IClicker` read as a
  removal is hundreds of deleted statements; with the id kept it is one rename, *known* rather than declared
  or guessed. All 54 public `api.*` types carry one, ids unique.
- **CLASS retention, verified end to end.** `javap -v` shows `RuntimeInvisibleAnnotations:
  com.botmaker.sdk.api.ApiId(value="image-clicker")`, and a scratch ClassGraph probe against the built jar
  printed `ImageClicker -> image-clicker`, `Precision -> precision`, `Key -> key` — through the same library
  and the same `.enableAnnotationInfo()` scan Studio's `TypeSummaryManager` already runs. RUNTIME would put
  the annotation in every running bot's reflection data for nothing.
- **Three rules keep the ids honest**, all in the annotation's Javadoc: absence of an id **is** the "this
  role is gone" signal, so it can never invent a counterpart; an id pairs the **type name only** — members
  are still resolved one by one, so an id kept across a redesign degrades to defaults plus review marks; an
  id is **retired, never re-pointed** at a different class.
- **`migrations.json` is schema 2 and rename-only:** `{"schema": 2, "versions": {"<v>": [{from, to}]}}`.
  `fix`, `manual`, `summary`, `when.arity`, the kind table, the coverage rule and the ordered replay are
  gone. It is the fallback for what ids cannot reach — chiefly anything renamed relative to **v1.0.26, which
  carries no ids** — and is expected to stay nearly empty. **Version keys stay** although replay does not: a
  bot jumping 1.x → 3.0 still spells a twice-renamed member the 1.x way, so Studio composes every version in
  `(from, to]` ascending into one map and makes a single pass. Composition, not passes.

**Verified**

`mvn -q -o -pl botmaker-sdk -am install -Dmaven.test.skip=true` clean and offline. The built jar carries
`com/botmaker/sdk/api/ApiId.class` and the new `META-INF/botmaker/migrations.json`, and **no** apicheck
class — the profile never reached the artifact. `grep` confirms zero `api-check`/`japicmp` mentions left in
`pom.xml`; `bash -n release.sh` passes.

**Deferred / next**

The Studio half is unbuilt: pairing by id, defaults in place of fixes, the generated `@NeedsReview`
annotation, marking the other three refactors (signature edit, template retarget, variable delete), and a
review panel. Plan phases 2–5.

---

## 2026-08-22 — one `migrations.json`, and the checker stops being Python

**Changed:** `src/main/resources/META-INF/botmaker/migrations.json` (new),
`src/api-check/java/com/botmaker/sdk/apicheck/ApiRulesCheck.java` (new);
`src/main/resources/META-INF/rewrite/botmaker-sdk.yml`,
`src/main/resources/META-INF/botmaker/upgrade-notes.json` and `tools/check-api-rules.py` (all deleted);
`pom.xml`, `.github/workflows/ci.yml`, `CLAUDE.md`. Umbrella side: `release.sh`,
`docs/refactor/21-api-compat.md` (§3–5), `docs/refactor/99-progress.md`, `CLAUDE.md`.

**Done**

- **OpenRewrite is gone, and the two files it needed are now one.**
  `META-INF/botmaker/migrations.json` is keyed by the release that introduced each break; every entry
  carries a `member`, a `summary`, and **exactly one** of a `fix` (Studio repairs the call sites) or a
  `manual` sentence. The executable/prose split became a *field* rather than a file: one grammar, one
  reader in the checker, one in Studio, one coverage rule. The file's `_readme` is the authority.
- **Why, since the entry above argues the opposite.** OpenRewrite was chosen for one requirement —
  `mvn rewrite:run` migrating a bot with no Studio at all, recorded as "a stated requirement, not a
  bonus, and it is what rules out a bespoke format". **The maintainer withdrew that requirement.** With
  it gone, an engine we do not control bought nothing `CallMigrator` could not do, and cost a dependency
  (`rewrite-java-21` + `rewrite-maven`) on all three legs of Studio's package matrix plus a rewriter that
  knows nothing of `MethodLock`/`LockedRegions`/`FileRole`. Genuinely lost: type-attributed overload
  resolution (Studio matches by **arity**, having no bindings) and the standalone path.
- **The swap cost nothing because nothing had shipped.** Neither pom depended on OpenRewrite, Studio
  referenced it once as a string constant, the `recipeList` was empty, and v1.0.26 carries neither file.
- **`check-api-rules.py` became `ApiRulesCheck`.** Reading the OpenRewrite YAML needed PyYAML — the
  script's one non-stdlib import, which `ci.yml` had to install explicitly. With one JSON file left and
  Jackson already a direct dependency, the rule is compiler-checked against the format's own shape. Both
  rules and all four verdicts are unchanged in meaning.
- **It lives in its own source root, `src/api-check/java`,** compiled by a `maven-compiler-plugin`
  execution inside the `api-check` profile into `target/api-check-classes` and run by
  `exec-maven-plugin`. Neither obvious home works: `src/main` would ship build tooling in the jar on
  every generated bot's classpath (the reason the `internal/` harnesses were deleted), and `src/test` is
  not compiled at all when `release.sh` runs the gate with `-Dmaven.test.skip=true`. `flattenMode=oss`
  already strips `<profiles>` from the published pom, so none of it reaches a consumer — **verified** by
  unzipping the built jar.
- **The verdict is a file, not an exit code.** `target/japicmp/api-verdict.json` carries the code and the
  offending members, and the build passes regardless unless `-Dbotmaker.api.failOnViolation=true`. That
  is what keeps "the API broke" separate from "the build broke": CI sets the flag, `release.sh` leaves it
  off and reads the file. A Maven plugin that simply failed would collapse the distinction. **New code
  4** — the migrations file is itself invalid — always fails, since a broken input is not a verdict.
- **The known coverage hole is closed.** The Python checker matched coverage against the whole file and
  documented that an older release's recipe could satisfy a brand-new break. The checker now knows the
  comparison baseline, so an entry only counts when filed under a version **strictly newer** than it.

**Verified**

Six verdicts against synthetic japicmp reports: removal with no cycle → 2 (**methods and fields alike**),
removal with a cycle and no entry → 3, entry under a newer version → 1, the *same* entry under an older
version → still 3, a bogus `fix.kind` → 4, `"schema": 99` → 4, `fix` and `manual` together → 4.
`failOnViolation` shown to gate the exit code (2 vs 0) without changing the verdict. End to end: renaming
a real public method (`Wait.seconds(int)`) made `./release.sh --sdk patch --dry-run` refuse and name the
exact member, with the checker's report replayed — `release.sh` captures Maven's output rather than
streaming it, so that replay had to be added or the refusal would have arrived with no list.

**Deferred / next**

- **Studio reads the merged file now, but applies nothing from it.** *(updated 2026-08-22)* The
  `mvn rewrite:run` card is gone and the report splits the span into what Studio could repair and what
  needs the user — with Apply present and inert. The rewriter that carries the `fix` entries out is the
  next piece of work.
- **`renameField`/`moveMember` have no consumer yet.** They are in the kind set and the checker validates
  them, but Studio's scanner reads only methods and constructors — a break on `Key.ENTER` is demanded by
  CI and invisible to the upgrade report. That asymmetry is a known bug, scheduled.
- **Still nothing published.** v1.0.26 remains the newest tag and carries none of this. The first release
  under the contract is **v1.1.0**.

---

## 2026-08-22 — a break must ship the fix for itself

**Changed:** `src/main/resources/META-INF/rewrite/botmaker-sdk.yml` (new),
`src/main/resources/META-INF/botmaker/upgrade-notes.json` (new), `tools/check-api-rules.py`,
`.github/workflows/ci.yml`, `pom.xml` (stale comment), `CLAUDE.md`. Umbrella side:
`docs/refactor/21-api-compat.md` (§3–4), `release.sh`, `CLAUDE.md`.

**Done**

- **The SDK jar now carries the recipes that migrate its own consumers.** `META-INF/rewrite/` is
  OpenRewrite's documented classpath-discovery slot, so the file needs no pom wiring to ship and no code
  of ours to read. One recipe per breaking release, composed oldest-first by
  `com.botmaker.sdk.UpgradeToLatest`. An earlier draft invented `migrations.json` for this; it was
  reinventing the slot.
- **A user migrates with one command and no edit to their pom.** `rewrite-maven-plugin` takes both the
  recipe classpath and the recipe name as *user properties*
  (`-Drewrite.recipeArtifactCoordinates`, `-Drewrite.activeRecipes`). Better than planned: nothing is
  pinned in the bot, so this works on bots generated long before the feature existed, and a breaking SDK
  release is never gated on a Studio release.
- **RULE 2 in `check-api-rules.py` (exit 3):** every binary-incompatible change must be named by a recipe
  or by an `upgrade-notes.json` entry. Wrong at every version — a major release still owes users a way
  across — so CI fails on it and `release.sh` refuses even a `--sdk 2.0.0`. Needs PyYAML, which `ci.yml`
  installs explicitly rather than trusting the runner image.
- **The suspected recipe gap does not exist.** The plan flagged "inserting an argument at call sites" as
  uncovered because `AddMethodParameter` targets declarations. `AddLiteralMethodArgument` and
  `AddNullMethodArgument` do exactly that job at invocations. The real limit is narrower: a new parameter
  whose default is an *expression* rather than a literal or null.
- **Verified on a real bot, not just built:** the recipe was discovered from inside the installed jar,
  rewrote the genuine call site in `~/BotMakerProjects/zeggze` (`Target.restart()` →
  `startIfNotRunning()`), left the javadoc mention of it alone, touched no other file, and the bot still
  compiled. Both coverage paths (recipe, note) were shown to turn exit 3 into exit 1, and the empty
  aggregator was shown to run as a clean no-op — the case a user hits when already up to date.

**Two version traps, both hit while building this**

- **`rewrite-maven-plugin` 6.12.0 cannot read `META-INF/rewrite/` at all on JDK 24+.** Discovery goes
  through ClassGraph's `enableMemoryMapping()`, which now throws there, so the failure lands squarely on
  this feature's own code path. **6.46.1 is the floor**; it is what the end-to-end test ran on under JDK
  26. This is the second time this toolchain has been JDK-coupled — see the Groovy note below.
- **`search.maven.org`'s JSON API reported 6.12.0 as the newest release when 6.46.1 was out.** Read
  `repo1.maven.org/…/maven-metadata.xml` and trust `<release>`.

**Deferred / next** *(added 2026-08-22, when the Studio half of the same plan landed)*

- **None of this is published yet.** v1.0.26 is still the newest tag, and it carries neither the recipes
  nor `upgrade-notes.json`. The first release under the contract is **v1.1.0** — additive-only by
  definition, since it is the baseline everything after it is compared to. Until it is cut, Studio's
  upgrade report has nothing to read: `SdkUpgradeService` finds no notes in any published jar and offers
  no `mvn rewrite:run` command, which is correct behaviour, not a bug.
- **Studio now consumes this jar's two `META-INF` files** (`../botmaker-studio` → *Project ▸ Upgrade
  SDK…*): `upgrade-notes.json` is read out of the **target** jar and shown verbatim under *what cannot be
  migrated*, and the presence of `META-INF/rewrite/botmaker-sdk.yml` is what decides whether a rewrite
  command is offered at all. Renaming either entry, or changing the notes schema
  (`versions` → `[{member, summary, action}]`, a constructor being `Fqcn#<init>`), breaks a Studio the
  user is not upgrading at the same time. Treat both paths as API.

---

## 2026-08-22 — the bump level stops being a guess

**Changed:** `pom.xml` (`api-check` profile), `tools/check-api-rules.py` (new),
`.github/workflows/ci.yml`, `CLAUDE.md`. Umbrella side: `docs/refactor/21-api-compat.md` (new),
`release.sh` (`check_api_bump`), `CLAUDE.md`.

**Done**

- **The contract starts at v1.1.0.** Everything up to v1.0.26 was released under the "freely breakable, no
  published bot consumes it yet" licence; that licence is over. Semver for real: additive → minor,
  breaking → major, and nothing removed without one full minor marked
  `@Deprecated(since, forRemoval = true)` naming its replacement. `../docs/refactor/21-api-compat.md` is
  authoritative.
- **japicmp compares each build against the previously published jar**, in an `api-check` profile that is
  **off by default** so the daily `mvn -pl botmaker-sdk -am install` stays offline. `<oldVersion>` is
  always injected, never auto-detected: japicmp's auto-detect reads `maven-metadata.xml`, and JitPack's
  lists every release twice (`v1.0.7` *and* `1.0.7` — what `JitPackSearch.dedupeVPrefix` exists to clean
  up). Because it compares jars, it works **retroactively** — v1.0.26 is a fine baseline.
- **Two questions, two owners.** "Was something removed that was never announced?" is wrong at every
  version, so `tools/check-api-rules.py` answers it in CI *and* at release time (exit 2). "Is this break
  allowed?" depends on the version being cut, which no PR build knows — `release.sh` owns it. That is why
  japicmp's own `breakBuildOn*` flags are off: they would block every legitimate major-release PR.
- **Verified end to end, not just built:** no-change → passes; a removal never marked `forRemoval` →
  refused at *any* version, naming the member; a properly deprecated removal → refused as a patch
  ("Release it as 10.0.0"), accepted as a major; a broken build → a distinct message rather than a false
  API verdict.

**Rejected along the way** (both were implemented before being replaced — don't re-propose them):

- **A checked-in `api-baseline.txt`** of all 818 members plus a bespoke comparator. It worked, and it was
  a hand-maintained, uncompilable mirror of the API. japicmp answers the same question against the real
  artifact.
- **japicmp's `postAnalysisScript`** (Groovy), the natural home for the deprecation rule: japicmp 0.26.1
  bundles a Groovy that cannot read Java 26 class files (`Unsupported class file major version 70`) and
  this box runs JDK 26 while CI runs 21. Pinning a newer Groovy just moves the race to the next JDK, so
  the rule parses japicmp's XML instead.
- **A `@since 1.1.0` sweep** over ~53 files: 818 identical tags carry no information, and comparing two
  released jars gives exact per-version added/removed for free. `@since` is required only from 1.2.0 on.

**Deferred / next**

- **OpenRewrite recipes in the jar** (next phase) at `META-INF/rewrite/botmaker-sdk.yml` — the standard
  slot, so `mvn rewrite:run` migrates a bot with no Studio at all — plus `META-INF/botmaker/
  upgrade-notes.json` for what cannot be automated, cross-checked by `check-api-rules.py` so a breaking
  change with no recipe is a red build. **Check early** whether a stock recipe can *insert* an argument at
  call sites: `AddMethodParameter` targets declarations, not invocations.
- **`src/main/resources/images/default_template.png` is gitignored** (`.gitignore:15`, the bare `images`
  rule) — so a clean clone has no default template resource. Pre-existing, unrelated to this entry, and
  left alone deliberately: whether that file belongs in git is the maintainer's call.

---

## 2026-08-22 — the published SDK pom names real shared/session tags (it never did)

**Changed:** `pom.xml` (flatten-maven-plugin + property comments), `.gitignore`.

**Done**

- **Fixed: every released SDK was unresolvable on a machine that had never built this repo.** A bot
  generated by Studio pins a real SDK tag, but that tag's *published pom* declared
  `botmaker-shared:${botmaker.shared.version}` with the property still at `0.0.0-SNAPSHOT`, so a clean
  `mvn compile` died with `Could not find artifact com.github.LiQiyeDev:botmaker-shared:jar:0.0.0-SNAPSHOT
  in jitpack.io` (and the same for session). Reported from a Windows install; invisible on the Linux dev
  box, whose `~/.m2` always holds a `0.0.0-SNAPSHOT` shared from `mvn -pl botmaker-sdk -am install`.
- **Root cause: `mvn install -Dbotmaker.shared.version=v0.0.18` does not rewrite the pom Maven publishes.**
  The `-D` steers that build's own resolution; the artifact installed is the *committed* pom. So the whole
  `.deps.env` → `jitpack.yml` → `-D` chain (2026-08-21, below) compiled the jar against the right shared
  and then threw the information away at publish time. It was never right — this is not a 1.0.24 regression.
- **`flatten-maven-plugin` 1.6.0** now writes `.flattened-pom.xml` with the effective values and Maven
  installs *that*. `flattenMode=oss` plus `<pomElements><repositories>keep</repositories></pomElements>` —
  `oss` strips `<repositories>`, and the jitpack.io declaration is how a consumer reaches the transitive
  shared/session at all. The committed pom keeps `0.0.0-SNAPSHOT`, so the reactor and Studio's `package`
  job (which builds all three from source at that version) are unchanged.
- Property comments rewritten: the old text claimed the `-D` injection was sufficient, which is the exact
  gap that produced the bug.
- **`jitpack.yml` now requires `SHARED_TAG`/`SESSION_TAG` instead of defaulting them.** Flatten changes what
  a stale fallback costs: `${SHARED_TAG:-v0.0.15}` was harmless while the published pom kept the unresolved
  property (a wrong `-D` only affected that build), but it now gets **baked into the published pom**, so a
  tag cut by hand without `.deps.env` would point every consumer at a long-dead shared. The build fails with
  a readable message instead. Both branches exercised by replaying the generated install script with and
  without `.deps.env` present.

**Deferred / next**

- `botmaker-shared` deliberately does **not** flatten — no property-driven dependency to resolve, and `oss`
  drops `<profiles>`, where its OS-activated native selection lives. Revisit only if shared gains one.

---

## 2026-08-21 — the SDK's JitPack build stops guessing its upstreams

**Changed:** `jitpack.yml`, new `.deps.env`.

**Done**

- **`jitpack.yml` reads the shared/session refs from a committed `.deps.env` instead of resolving them
  with `git ls-remote --tags | sort -V | tail -1`.** The guess was "the newest published tag", which is
  only *usually* "the tag this release was cut against" — and it forced the umbrella `release.sh` to poll
  JitPack between links (tag shared, wait for its `.pom`, tag session, wait, tag the SDK) so that the
  newest tag would be the intended one. `.deps.env` is written into the SDK's own release commit by
  `release.sh`, so an SDK tag now says which shared and session it was built against, and the release can
  push all three tags back to back: JitPack resolves and builds a *pinned* dependency tag on demand.
- The committed pom is still never edited — `botmaker.shared.version` / `botmaker.session.version` stay
  `0.0.0-SNAPSHOT` and the refs arrive via `-D`. The `${SHARED_TAG:-v0.0.15}` fallbacks cover a tag cut
  by hand, without `release.sh`, in a tree that has no `.deps.env`.

---

## 2026-08-19 — the side buttons

**Changed:** `api/interaction/MouseButton.java`.

**Done**

- **`MouseButton` gains `BACK(8)` and `FORWARD(9)`.** The enum stopped at left/middle/right, so the two thumb
  buttons on an ordinary gaming mouse were not sayable at all — a bot that wanted one had no value to pass.
  The numbers are X11's, which the Linux backends already pass through untouched; shared's `WindowsController`
  translates them (`MOUSEEVENTF_XDOWN` plus an `XBUTTON` selector in `dwData`), which is the one place the
  mapping is not the identity.
- **Named by what they do, not where they sit.** The class doc now says so outright, because it is the answer
  to "how do we handle different mouse layouts": the OS reports the button the user configured it to be, so a
  bot that says `BACK` keeps working on a left-handed mouse or one whose driver has remapped its side buttons,
  and nothing in the editor has to ask which physical mouse the machine running the bot has.

---

## 2026-08-17 — three click verbs, and a template names itself

**Changed:** `api/vision/VisionContext.java` (the frame keeps its pixels; `requireFrame` → `currentFrame`),
`api/vision/ImageFinder.java` (`findAllTemplates` → `findFrame`; new `findAllIn`),
`api/vision/ImageClicker.java` (`clickEachLast`/`clickAllLast` + filtered overloads),
`api/vision/ImageTemplate.java` (sidecar reading moved out); new `internal/vision/TemplateMetadata.java`;
`ClickLastFrameTest` rewritten.

**Done**

- **`clickAllLast()` split into `clickEachLast()` and `clickAllLast()`.** The old name meant "one click per
  visible template", which is what `clickEachLast()` is now called. `clickAllLast()` is the question that had
  no verb: **every occurrence of every visible template** — the whole row of chests, not the best one of each.
  It costs no capture either: the frame now retains the screenshot it was measured in, so the finer question
  is asked of that same instant by re-matching (`ImageFinder.findAllIn`) rather than by looking again. That is
  also what distinguishes it from `clickAll(template)`, which does look again.
- **Filtered overloads** — `clickEachLast(t…)` / `clickAllLast(t…)`. A branch that matched three templates can
  act on the two it cares about with no second capture. The filter can only narrow: a template the frame never
  saw is not searched for, so the click order stays the group's rather than the argument's.
- **The frame verbs no longer throw.** `VisionContext.requireFrame` is gone; `currentFrame()` answers null and
  every verb reports "nothing clicked" (`false` / `0`) outside a callback, exactly as it already did for an
  empty frame. The reasoning against clicking a *stale* coordinate still holds and is still enforced — it was
  the remedy that was wrong. A bot that drifts out of a callback should carry on, not die.
- **`TemplateMetadata` owns the `<name>.json` sidecar.** `ImageTemplate.loadCaptureResolution()` and its two
  mutable cache fields are gone; the template's contract to the matcher is `authoredSize()` and nothing else.
  The sidecar is a Studio artefact — its file convention, key names and JSON parser are editor plumbing, not
  something a value type constructed from a path should know. Now cached by **path** rather than per instance,
  so a bot that rebuilds the same template every loop iteration reads the file once per JVM instead of
  stat-ing it per match.

---

## 2026-08-16 — clicking what the group check already found

**Changed:** `api/vision/VisionContext.java` (the frame), `api/vision/ImageFinder.java` (the four group
helpers), `api/vision/ImageClicker.java` (`clickLast`/`clickAllLast`); new `ClickLastFrameTest`.

**Done**

- **`ImageClicker.clickLast()` / `clickAllLast()`** — click the best / every match of the frame the enclosing
  group check is running over, with no capture and no matching. `whileFindAny(POPUPS, found ->
  ImageClicker.click(POPUP))` captures twice per iteration to act on a template the loop had already located;
  these cost one. `clickAllLast` clicks each visible *template* once (a `Matches` holds one match per
  template), not every occurrence — that is still `clickAll(template)`, which does look again.
- **`VisionContext` gained a frame**, distinct from `getLastMatches()`. The frame is `(Matches, CaptureSource)`
  and exists only for the duration of a callback (`runInFrame`, restoring any outer frame in a `finally`, so a
  nested group check doesn't leave the thread frameless and a throwing callback doesn't leak one).
  `getLastMatches()` outlives its callback, which is fine for reading and wrong for acting: a coordinate is
  only valid for the frame it was measured in. Carrying the source with it also means the verbs click through
  whatever surface the find used, with no source argument to get wrong.
- **Outside a callback both verbs throw `IllegalStateException`** naming the caller and the fix, rather than
  falling back on the last match — a click at a coordinate the screen has moved past is a silent wrong answer
  that reads as a flaky bot. `VisionContext.inFrame()` is the public way to ask first.
- **Named `clickLast`, not the no-arg `click()`/`clickAll()` the plan sketched.** Studio's "Click Image" block
  seeds its arguments from *the overload with the fewest parameters*, so a no-arg `click()` would have
  silently turned that block into a click-the-last-match, and `ImageClicker.click()` in a bot's source says
  nothing about what it clicks. `clickLast` also matches the vocabulary already in `VisionContext`
  (`getLastMatch`, `lastMatchFound`).

**Studio needs no change for these to appear.** Facade *methods* come from the bot's own resolved SDK jar
(`ProjectAnalyzer` via ClassGraph) and their docs from its `:sources` jar, so both verbs show up in the
ImageClicker submenu once a bot pins an SDK that has them — only a new *class* would need a `palette/SdkType`
constant, and `IMAGE_CLICKER` is long since there.

---

## 2026-08-08 — an emulator ref's liveness stops being a socket

**Done**

- Followed shared's `EmulatorInstance` onto `AdbEndpoint` (its `host` + `adbPort` pair is gone — see
  `../botmaker-shared/ROADMAP.md` for why a phone on a USB cable cannot be written as a host and a port).
- **`EmulatorRef.running()` delegates to the endpoint** instead of opening its own `Socket`. It was one of
  the two hand-rolled copies of that probe; a TCP connect is meaningless for a device the host adb server
  owns by serial, so the answer has to live with the address. `connect()` and `Emulators.tryConnect` take
  `AdbDevice.connect(instance.adb())`, which routes a serial through the adb server and a TCP address through
  dadb directly, exactly as before for every emulator.

No bot-facing signature changed: `Emulators.connect(host, port)` still takes a host and a port, since that is
the right shape for the thing it does.

---

## 2026-08-08 — the observer API sees gestures, not just clicks

**Changed:** new `api/observe/SwipeEvent.java`; `api/observe/BotObserver.java`, `api/observe/Bots.java`,
`api/interaction/Mouse.java`, `api/emulator/Emulator.java`, `internal/observe/IpcObserver.java`;
`IpcObserverTest`.

**Done**

- **`SwipeEvent(surface, start, end, durationMs)`** + `BotObserver.onSwipe` (a default no-op, like the rest)
  + `Bots.fireSwipe`. Fired from the two places a bot actually drags: `Mouse.drag` (desktop) and
  `Emulator.swipe` (ADB). One event for the completed gesture, not one per interpolated move — the moves are
  how the driver got there, and an observer that logged each would print a hundred lines for one flick.
- **Emitted after the release, not before the press**, and behind `Bots.hasObservers()` like every other
  emission: an overlay draws what happened, and a gesture that threw part-way through did not happen.
- `IpcObserver.onSwipe` translates it to `TelemetryEvent.Swipe`. `Emulator.swipe` passes emulator-local
  coordinates through unchanged, exactly as `click()` does — that route's `origin()` is (0,0), so the numbers
  ADB was handed are already the ones an overlay draws on the streamed frame.

---

## 2026-08-08 — a debug run reads as a narrative

**Changed:** new `internal/trace/Trace.java`; `api/bot/Activity.java`, `api/bot/Bot.java`,
`api/bot/PopupGuard.java`, `api/vision/ImageFinder.java`; new `TraceTest`, `DebugTraceTest`.

`Debug`/`Diag` already existed, were on by default and printed to the stdout Studio captures — the
interesting moments simply never printed. A run's console showed launch chatter and then nothing until it
crashed.

**Done**

- **`internal/trace/Trace`** — `elapsed(millis)` (a duration as a person says it: `340ms`, `1.4s`, `Locale.ROOT`
  so a French desktop doesn't render `1,4s`) and `Trace.Runs`, which collapses a burst of one repeating event
  into a single line. A run closes either when its opposite happens (`flush`) or when it outlives 5s (`tick`),
  the second so a bot waiting five minutes for a template isn't silent for five minutes — that reads as a hang.
- **`[Activity] Mining → BAG_FULL (1.2s)`** from `Activity.execute()`, and `→ stuck: <msg>` on a
  `BotStuckException` before it is rethrown. This is the coarsest "what is the bot doing" unit, and `execute()`
  is the only place that can print it: the flow driver switches on the outcome without ever naming the activity.
- **`[Bot] cold start` / `[Bot] goHome` / `[Bot] restarting the game`** around the supervisor's start-up and
  recovery, where a bot spends its most confusing time — without them, `goHome` navigating a game that is
  already gone and a restart waiting on a launch are one indistinguishable silence.
- **`[Vision] find Foo → (312,88) 0.94`** on a hit, from the two single-frame chokepoints (`findInternal`,
  `findAllTemplates`) so every overload — `find`/`findAny`/`findBest`/`ifFind`/`whileFind`/`ifFindAny` — is
  covered once. The centre, not the top-left: that is the point a click lands on. **Misses are collapsed** per
  template (`[Vision] Foo not found ×47 in 3.4s`); a wait loop polls many times a second and printing each
  would bury every other line. The counter lives in the finder because "how long has this template been
  missing" is a property of the template, not of the overload that asked.
- **`[Popup] checked ×214 in 5.0s`**, plus an immediate `[Popup] check took 820ms` past 500ms. Deliberately
  *not* the plan's "`checking` on entry + elapsed on exit": the guard runs before every vision step, so that
  would wrap two lines around every find — the same drowning the vision misses are collapsed to avoid. A slow
  check is the actual signal, because it turns a working bot into one that looks hung.
- Every line goes through `Debug`, so `Debug.disable()` is silence rather than less noise — `DebugTraceTest`
  asserts empty output for each chokepoint, which is the half that is easy to lose to a stray `println`.

**Deferred / next**

- The trace is stdout only. The structured IPC stream stays reserved for geometry the pilot draws
  (`TelemetryEvent`), and `[Vision]` deliberately does not duplicate what `emitMatch` already sends there.
- `compare`/`compareAny`/`compareAll` and `findAllInternal` emit observer events but no `[Vision]` line. They
  are batch shapes where "the hit" isn't a single result; if a compare-heavy bot proves hard to read, give
  them a count line rather than one per template.

---

## 2026-08-08 — the ambient source skips a session whose pixels aren't on X11

**Changed:** `api/capture/Source.java`.

**Done**

- `Source.current()` now yields a `SessionSource` only when the active session answers
  `DesktopSession.x11Capturable()` (new in `botmaker-session`, same date). A session hosting a Wayland-only
  client — Waydroid under `gamescope --expose-wayland` — hands back a valid frame of an empty X11 root, so
  every no-source `find` matched against black and missed, with nothing logged to say why. Falling through
  reaches `ProjectDefaults.source()`, which for a Waydroid bot is its `EmulatorSource` over ADB.
- An explicit `Source.set(...)` pin is unaffected: it already wins over an active session and still does.

---

## 2026-08-07 — an empty `ImageTemplateGroup` is legal, and means "matches nothing"

**Changed:** `api/vision/ImageTemplateGroup.java`, `api/vision/ImageFinder.java`; new
`ImageFinderEmptyGroupTest`, updated `ImageTemplateGroupTest`. Driven by the Studio side of the same day's
work (`../botmaker-studio/ROADMAP.md`): the generated `Popups` scaffold now ships a real `whileFindAny` loop
with its group still to be filled in, which is impossible while the empty group throws.

**Done**

- **`ImageTemplateGroup.of()` no longer throws `IllegalArgumentException`.** The non-empty invariant only ever
  protected against a group that finds nothing — which is exactly what a scaffold wants to express, and the
  same thing a group whose templates are all missing from the screen already does. The record now just copies
  the list (still rejecting `null`), and gained `isEmpty()`.
- **"Matches nothing" is enforced, not assumed.** Four helpers got it wrong by default, and each is a
  one-line guard now: `ifFindAll` and `whileFindAll` because `Matches.hasAll(new ImageTemplate[0])` is
  *vacuously true* (the first would run its action on a blank screen; the second would loop forever doing
  so), `untilFindAny` because nothing can ever appear so it would run its action forever, and `untilFindAll`
  for the symmetric vacuous `allMatch`. The plain queries (`findAny`/`findBest`/`findAll`, and every
  `ImageClicker` group entry point) already degrade correctly — they iterate the templates.
- **An empty group costs no capture.** `findAllTemplates` returns `Matches.none()` before touching the
  source. This is the one that would have been felt: a scaffolded bot runs its popup check before *every*
  vision step, so an empty group that still grabbed a frame would have doubled the capture work of every bot
  that hadn't filled one in yet.
- **`ImageFinderEmptyGroupTest`** asserts all of it against a `CaptureSource` that fails the test if it is
  captured or clicked, with `@Timeout(10)` on every case so a regression in the loop guards hangs the one
  test rather than the build.

---

## 2026-08-06 — the project-file readers stop re-spelling shared's grammar

**195 tests (unchanged).** Changed: `internal/config/ProjectDefaults.java`,
`internal/session/SessionBootstrap.java`. Phase 5 of the stringly-typed sweep; the shared half (the new
`CaptureSourceKind`, `ProjectProperties.parseBoolean`) is in `../botmaker-shared/ROADMAP.md`, same date.

- **`ProjectDefaults.source()` switches over `CaptureSourceKind`** instead of testing four literals with
  hand-counted substring offsets (`substring(8)` for `"monitor:"`, `7` for `"window:"`, `9` for
  `"emulator:"`). Studio writes these specs and this reads them back, so the grammar belongs to shared; what
  is left here is the one thing shared cannot do — mapping a form onto `Desktop`/`Monitor`/`Window`/
  `EmulatorSource`. Because the switch is exhaustive over the enum, a fifth capture form added in shared now
  fails this compile rather than silently reading as "no default source".
- One behaviour change falls out of it: `window:` (or `emulator:`) with nothing after the colon now yields
  no default source instead of a source named `""`. An empty name matched every window.
- **`SessionBootstrap.overrideBool` deleted** in favour of `ProjectProperties.parseBoolean` — it was a
  byte-identical copy of shared's `true/1/yes/on` switch, and two copies of a lenient vocabulary drift
  quietly (an env override that rejects `on` while the project key accepts it). Its env var name is now a
  constant, `ISOLATED_ENV`, beside the `ISOLATED_PROPERTY` it mirrors.

---

## 2026-08-04 — The month is a `Month`, and the hour windows are gone

**195 tests** (was 192). Changed `api/Time.java` and `TimeWindowTest.java`. Improvements round 2 phase 4.

`month()` returns `java.time.Month` instead of a 1–12 `int`, following `dayOfWeek()`, which has returned
`DayOfWeek` since it was written. The number was ambiguous by one in the direction that never fails to
compile: half the languages a bot author has met index months from zero, and a bot reading `month() == 11` as
December was wrong all year, silently. Added `isMonth(Month...)` mirroring `isDay(DayOfWeek...)`.

Removed `isBetween(int startHour, int endHour)` and `isBetweenUtc(int, int)` in favour of the `LocalTime`
pair. A whole-hour window is still one call — `isBetween(LocalTime.of(5, 0), LocalTime.of(7, 0))` — and the
bare pair cost two things: a runtime 0–23 range check that the type makes impossible to fail, and, in Studio,
the last `(method, argIndex)` picker hook for this facade (deleted there in the same phase).

**Deviation from the plan, deliberate:** the plan said to remove `isBetweenUtc` outright, but nothing else in
the facade expresses a UTC window, and a server reset is exactly the case that is pinned to UTC rather than to
the machine's zone. So it survives retyped as `isBetweenUtc(LocalTime, LocalTime)`. Both windows now share one
private `isWithin(now, start, end)` — the midnight wrap was duplicated before, and it is the rule that fails
silently (read as `start ≤ now ≤ end`, a 23:50–00:10 window matches nothing, all day).

---

## 2026-08-04 — The wait length is `java.time.Duration`, and the range is a call

**192 tests** (was 195; `DurationTest`'s 8 became `WaitTest`'s 5). Deleted `api/interaction/Duration.java`
and `DurationTest.java`; added `api/interaction/WaitTest.java`; changed `api/interaction/Wait.java`.
Improvements round 2 phase 3.

The entry below introduced a BotMaker `Duration`. It is gone: `java.time.Duration` already models a length of
time, every Java author knows it, and the duplicate **simple name was itself a live hazard** — Studio's
`ImportManager` mapped the bare name `Duration` to `java.time.Duration` while the picker inserted the SDK's,
so anything resolving that name through the import table imported the wrong class.

Its one irreplaceable feature, the range, moved to **`Wait.between(min, max)`**. That is the more important
half of this change and it is not a mechanical port: the old range was a *value* whose `millis()` accessor
re-rolled on **every read**, so two reads of the same object disagreed — the logged wait was never guaranteed
to be the slept wait, and only `Wait.time` reading it exactly once kept that honest. Rolling at the call site
removes the trap entirely, and there is nothing left that a plain `java.time.Duration` cannot say.

Decisions worth keeping:

- **`between` waits, it does not return a `Duration`.** A helper returning a randomized length would put the
  roll back at a distance from the sleep. The bot-facing API is "wait a random amount", which is a verb.
- **Mixed units are now free.** `Wait.between(Duration.ofMillis(800), Duration.ofSeconds(2))` is two
  independent arguments, where the old range type had to reject mixed ends because its picker could only show
  one unit.
- **The unit an author typed is still not recoverable from the value** (`ofSeconds(1)` *is* `ofMillis(1000)`),
  so Studio's picker keeps reading the unit off the factory name in the source, not off the number.
- `Wait.milliseconds(int)` / `seconds(double)` / `minutes(double)` stay as literal shorthands, unchanged.

## 2026-08-04 — `Duration`: a wait that carries its unit, and can be a range

**183 → 195 tests.** Added `api/interaction/Duration.java`, `api/interaction/DurationTest.java`,
`api/TimeWindowTest.java`. Changed: `api/interaction/Wait.java`, `api/Time.java`.

`Wait.milliseconds(2)` and `Wait.seconds(2)` differ by a factor of a thousand and read identically, and a slot
typed `int` gives Studio nothing to dispatch an editor on. `Duration` is the same argument already recorded
here for `Precision`: a value type so the editor is chosen **by type**, never by a `(method, argIndex)` table
that stops firing the day the facade gains an overload.

The second half is the one that matters for bots: a `Duration` may be a **range**
(`Duration.between(Duration.ms(800), Duration.ms(1500))`), re-rolled on every read. Humanized delays are the
normal case, not an advanced one — a bot that waits exactly 1000ms between every action is trivially
identifiable as one — so the range lives in the type every waiting call already takes, rather than in a
separate API an author has to go find. A fixed duration is just the range whose ends are equal, which is why
`millis()` is the only accessor callers need.

Decisions worth keeping:

- **`Wait.time(Duration)` is added, not swapped in.** The plan said retype `milliseconds`/`seconds`; keeping
  them costs nothing (a one-off fixed pause reads best as `Wait.seconds(2)`, and the SDK uses them for its own
  poll intervals), and the picker fires on the `Duration` type wherever it appears. Studio's palette inserts
  `Wait.time(Duration.seconds(1))`, so the editable form is the one users meet.
- **Milliseconds are the storage unit**, so `Duration.seconds(1.5)` *equals* `Duration.ms(1500)` — which means
  the unit the author typed is not recoverable from the value. Studio's picker therefore reads the unit off the
  *source*, not the number.
- **`Time` gained the two daily-reset predicates the Studio pickers needed something to edit:**
  `isBetween(LocalTime, LocalTime)` (minute precision, wraps midnight — a 23:50–00:10 reset window read as
  "start ≤ now ≤ end" matches nothing, all day, silently) and `isDay(DayOfWeek...)`.

---

## 2026-08-04 — `PopupGuard`: dismiss the game's interruptions before every vision step

**177 → 183 tests.** Added `api/bot/PopupGuard.java`, `api/bot/PopupGuardTest.java`. Changed:
`api/vision/ImageFinder.java`, `api/vision/ImageClicker.java`, `api/vision/ImageWaiter.java`.

Games interrupt with daily rewards, mail, level-ups and ads, and each one hides whatever the next `find` was
looking for. Without a guard every activity has to open with its own defensive dismissal code and get it
right. `PopupGuard` runs one project-wide check instead, from inside the finder/clicker/waiter — the only
place it cannot be forgotten. The *logic* stays the bot author's: Studio generates an editable `Popups`
activity and installs it with `PopupGuard.install(Popups.INSTANCE::execute)`. Blind-clicking anything
cross-shaped is wrong (the same cross often belongs to the screen the bot is working on, and a popup's body
usually isn't clickable), so dismissal is a question about which *combination* is present — which is what
`Matches` answers, and why that landed first.

Three decisions worth keeping:

- **Reentrancy is a `ThreadLocal` flag, not a lock.** The check is written with `ImageFinder`/`ImageClicker`
  — the only way to write it — so it would re-enter the guard and recurse forever. `check()` is a no-op while
  the check is already running on that thread, so the handler's own vision calls behave like ordinary ones.
- **Guarded once per vision *statement*, not per capture.** The call sits in the overloads that take their
  own capture, never in the ones that merely delegate to another guarded overload; a full screenshot per
  guard makes double-firing expensive rather than merely untidy. `untilFind…` needs no guard of its own — it
  polls through `find`/`findAny`, which are guarded — and the two waiters guard *inside* their poll loop,
  because a popup opening mid-wait is exactly the case that would otherwise burn the whole timeout.
- **`click(MatchResult)` is deliberately unguarded.** It clicks a coordinate located in an earlier frame;
  dismissing a popup first would move the screen out from under it, which is how a "safe" guard misclicks.

`PopupGuardTest` exercises all of this through the real facades rather than by calling `check()` directly:
what can break silently is the wiring (an overload that forgot the call, or one that guards twice), and that
is invisible to a unit test of the class alone.

---

## 2026-08-04 — `Matches`: the group lambdas hand back the whole combination

**164 → 177 tests.** Added `api/vision/Matches.java`, `api/vision/MatchesTest.java`,
`api/vision/MatchesFindTest.java`. Changed: `api/vision/ImageFinder.java`, `api/vision/ImageClicker.java`,
`api/vision/VisionContext.java`.

The group lambda helpers were unusable for the job real bots need them for — "which of these templates are on
screen right now, and what do I do about *that combination*". `ifFindAny`/`whileFindAny` passed a single
`MatchResult` (the first hit, so a second template visible in the same frame was simply invisible to the
body), and `ifFindAll`/`whileFindAll` passed a bare `Runnable` on the recorded grounds that "every template is
present has no single meaningful `MatchResult`". Both shapes are gone; per this repo's *API stability* note
they were removed outright, with no compatibility overload.

### Done

- **`api/vision/Matches`** — immutable, built from one frame, keyed by `MatchResult.getTemplateId()`:
  `has` / `hasAll` / `hasAny` / `get` / `all` / `best` / `isEmpty` / `count`. Two contracts that bot code
  leans on without thinking, both tested: `get` returns `MatchResult.notFound()` rather than null, so
  `click(found.get(x))` is safe to write unguarded for a template that wasn't there; and lookups key on
  *template id*, not object identity, so a template reloaded from the same file still answers `has`. Two
  templates sharing an id can only hold one slot — the higher-confidence match wins it.
- **All four group lambda helpers now take `Consumer<Matches>`** (`ifFindAny`, `whileFindAny`, `ifFindAll`,
  `whileFindAll`). Single-template `ifFind`/`whileFind` keep `Consumer<MatchResult>` — one template has one
  answer. `untilFind*` keep their `Runnable`: they run *while nothing is found*, so there is genuinely nothing
  to hand over.
- **`ImageFinder.findAllTemplates(group, source, confidence)`** — one capture, one `Mat`, re-matched per
  template. It deliberately does not loop `findInternal` (that screenshots once per template) and cannot
  delegate to `findAnyInternal` (that short-circuits on the first hit — precisely the information loss
  `Matches` undoes). N templates therefore still cost one frame, and every answer in a `Matches` describes the
  same instant. `MatchesFindTest` asserts the capture count directly, since nothing else would catch a
  regression to the N-frame shape.
- **`ImageClicker.click(MatchResult)` / `click(MatchResult, CaptureSource)`** — the missing companion. Picking
  a match out of a `Matches` and acting on *that one* had no expression: every `click` overload re-located a
  template first, so the only way to click a chosen match was `Mouse.click(match.getCenter())`, which bypasses
  the `CaptureSource` click routing an emulator source depends on.
- **`VisionContext.getLastMatches()`** beside the existing slots, set by the four helpers.
  `setLastMatches` also seeds `lastMatch` with `matches.best()`, so Studio's palette-seeded
  `MatchResult match = VisionContext.getLastMatch()` keeps meaning something after a group check.

### Deferred / next

- **Studio still generates the old shapes.** `blocks/vision/LambdaCallBlock` declares
  `Variant("ifFindAll", true, false)` — `hasParam = false`, i.e. a `Runnable` body — which no longer compiles
  against this API, and it never renders the lambda parameter at all, so `found` is unreachable in the block
  editor. Both are the next phase's work, in the Studio repo.
- `findAllTemplates` is package-private: the lambda helpers are the intended surface. If a bot ever wants the
  combination without a loop, promote it as `ImageFinder.matches(group)` rather than growing a second path.

---

## 2026-08-04 — CI: the OCR tests error where there is no libtesseract

**164 tests, unchanged.** Changed: `api/vision/TextTest.java`, `.github/workflows/build.yml`. This repo's
GitHub Actions run was red with 3 errors, all of them `TextTest` — the SDK's only tests that recognize text,
routing straight into shared's `OcrEngine`.

### Done

- **`TextTest` skips instead of erroring without a system `libtesseract`,** via a `@BeforeEach` assumption
  probing `Class.forName("net.sourceforge.tess4j.TessAPI")`. The probe catches `Throwable`: the binding loads
  lazily and fails as an `UnsatisfiedLinkError` (then `NoClassDefFoundError` on every later attempt), which
  surefire counts as an *error* rather than a failure, and `OcrEngine` intentionally does not catch it so a
  genuine load failure can't masquerade as "no text". Same guard shape as shared's `OcrEngineTest`.
- **CI installs the natives** (`libtesseract-dev libleptonica-dev`) so that guard never fires there — a
  skipped test is not a passing one. Windows needs nothing: Tess4J bundles its DLLs.

---

## 2026-08-02 — `Tolerance` + `MinPixels` → one `Precision`

**157 → 164 tests.** Deleted `api/vision/{Tolerance,MinPixels}.java`; added `api/vision/Precision.java`.
Changed: `api/vision/Pixel.java`, `api/vision/ColorMatch.java`, `api/vision/PixelTest.java`,
`ToleranceAndMinPixelsTest` → `PrecisionTest`.

(Landed in two steps the same day: `MinPixels` first became a paired `MinMatch(area, count)`, then that and
`Tolerance` collapsed into `Precision`. Only the end state is described here — `MinMatch` never left this
repo.)

### Done

- **`record Precision(double deltaE, int minArea, int minCount)`** is now the single strictness argument of
  every `Pixel` search. Anchors `EXACT`/`TIGHT`/`DEFAULT`/`LOOSE` (the old `Tolerance` constants, each
  carrying the default area floor of 4 and no count requirement), `of(deltaE)` / `of(deltaE, area, count)`,
  and withers `tolerance(d)` / `minArea(n)` / `minCount(n)` so you start from an anchor and move one knob.
- **`minCount` is new** — matching pixels in total, however they clump, gated on the raw mask by shared's
  `ColorMatcher` before clustering. An area floor over an unbounded search mostly says "not a speck", which
  is rarely the question anyone had; the count is what turns it into a real assertion.
- **Argument order is now `(what, where, how)`** — `find(RED, hud, Precision.TIGHT.minArea(400))`. The
  source comes before the precision throughout, including `waitFor`/`waitForGone` (timeout last).
- **Three operations read only part of it, by design and by test.** `matchesAt` tests one pixel and
  `coverage` never clusters, so both read only `deltaE`; `findInRange` takes a colour band, so it reads only
  the two quantity gates. Each says so in its javadoc, and `PixelTest` pins that a knob they cannot use has
  *exactly* no effect — the collapse is only safe while that holds.
- **Validation is asymmetric on purpose:** `deltaE ≥ 0` and non-NaN, `minArea ≥ 1` (a cluster of nothing
  cannot be honoured), `minCount ≥ 0` (the honest "no requirement", and what every anchor uses).

### Deferred / next

- Studio's `ToleranceArgPicker` + `MinPixelsArgPicker` still dispatch on the old type names and must become
  one `Precision` editor — which should show only the knobs the call it is attached to actually uses, turning
  the javadoc caveat above into something the UI enforces. Planned as Phase B alongside the colour sampler.

---

## 2026-08-02 — improvements Phase 9: the launch step moves into `Bot.start`

**156 → 157 tests.** Changed: `api/bot/Bot.java`, `api/bot/StartMode.java`, `api/launch/Target.java`,
`api/bot/BotTest.java`.

### Done

- **`Bot.start(Runnable body, Runnable goHome)` now supplies the start-up step itself** —
  `Target.startIfNotRunning()` on the cold start, `Target.restart()` on a recovery, switched on the
  `StartMode` the supervisor already hands out. That is the entire body of the `Startup.java` Studio used to
  generate into every game bot: the launch target was never in that file, it is read from
  `botmaker-project.properties` at runtime, so the file was a per-project copy of SDK behaviour with only its
  `package` line to make it the project's. It is no longer generated (see the Studio roadmap).
- **This retypes the existing 2-arg overload rather than adding one.** `start(body, recovery)` and
  `start(body, goHome)` have the same erasure, so they cannot coexist. The generic
  "run forever, recover with this" form is subsumed by the 3-arg `start(body, goHome, startGame)` — pass a
  no-op start-up step — and the shape that every generated bot actually calls is now the short one. Free to
  do because no published bot consumes the API yet; a shim would have been the wrong trade.
- **`launchConfiguredTarget` is private.** A bot wanting different start-up passes its own
  `Consumer<StartMode>`; the two `Target` calls it delegates to are public, so nothing is walled off.
- The new test asserts the observable half — with no target configured the launch is a documented no-op, so
  what would have been lost silently if the overload had simply dropped the step is that `goHome` still runs
  once, before the first body pass.

---

## 2026-08-01 — improvements Phase 8: `Tolerance` and `MinPixels`, the two `Pixel` knobs as types

**152 → 156 tests.** New: `api/vision/Tolerance.java`, `api/vision/MinPixels.java`,
`api/vision/ToleranceAndMinPixelsTest.java`.

### Done

- **`Pixel`'s `double tolerance` → `Tolerance`, `int minPixels` → `MinPixels`.** Both are records with the
  named constants that used to sit on `Pixel` as bare numbers (`Pixel.TIGHT` → `Tolerance.TIGHT`,
  `Pixel.DEFAULT_MIN_PIXELS` → `MinPixels.DEFAULT`) plus a validating `of(...)`. The old constants are gone
  from `Pixel` — the API is freely breakable and there is no shim.
- **Why types.** The numbers are unreadable on their own: ΔE has no obvious scale and is not a percentage
  (so `0.12` meant as "12%" silently becomes a near-exact match), and `minPixels` is an *area* that reads as
  a width (`20` asks for a 4×5 blob, not one 20 across). Both failures are silent — the bot simply never
  sees its colour. The constructors now reject a negative ΔE and a sub-1 area rather than degrading into
  "matches nothing" / "matches everything".
- **`MinPixels.equivalentSide()`** lives on the type, not in the Studio's editor, so the value and its
  preview cannot disagree about what the unit is.
- **The dispatch payoff.** Studio now selects the editors for these arguments by *type*. The alternative was
  a `(method, argIndex)` table in Studio duplicating `Pixel`'s overload list — tolerance is index 1 of
  `find`/`coverage`/`waitFor` but index 3 of `matchesAt` — which would have gone stale on the next overload.

---

## 2026-08-01 — improvements Phase 7: `ClickConfig` → `api.BotSettings`, seeded from the project

**143 → 152 tests.** New: `api/BotSettings.java`, `api/BotSettingsTest.java`. Removed:
`api/vision/ClickConfig.java`.

### Done

- **`api.vision.ClickConfig` → `api.BotSettings`.** It moves out of `api.vision` because it was never only
  about vision (it carried the input backend and the retry budget), and it is now shaped like
  `api.capture.Source`: one ambient value, seeded lazily from the project, overridable at runtime.
- **The public mutable fields became accessors, and that is the load-bearing part.** `DEFAULT_CONFIDENCE`,
  `RANDOMIZE_CLICKS`, `MAX_RETRY_ATTEMPTS` and friends were `public static` fields read directly by ~60 call
  sites in `ImageFinder`/`ImageClicker`/`ImageWaiter`/`Watchdog`. A bare field read cannot trigger a lazy
  load, so the project's values would have applied only if something happened to call a method first. They
  are now `confidence()`, `randomizeClicks()`, `maxRetryAttempts()`, … each going through `ensureLoaded()`.
  The `DEFAULT_*` names survive as genuine constants (the SDK's own defaults).
- **`useRealInput` no longer needs a generated call, and that is what makes dropping the file safe.** It
  swaps the process-wide Linux input backend, one-way, and must run *before the first click* — which is why
  generated projects put it at the top of `main`. `input.real` is now applied inside `ensureLoaded()`, so
  every click path performs the swap by reading a setting, ahead of the click that needs it. `loaded` is set
  *before* the swap, so anything the native controller touches that reads a setting back does not re-enter.
  This fails silently when wrong (the click is dropped and neither OS reports it), so it is tested directly:
  `readingAnySettingEscalatesInputWhenTheProjectAsksForIt` asserts a plain `confidence()` read escalates.
- **`DEFAULT_COMPARE_MARGIN` gained a real setter** (`setCompareMargin`), validated to 0–1. It used to be
  assigned as a bare field, which is why the generated file wrote `ClickConfig.DEFAULT_COMPARE_MARGIN = x;`
  rather than a call.
- **`resetToDefaults()` marks itself loaded** rather than clearing the flag: the point of a reset is to end
  up on the SDK defaults, so a later read must not re-seed the project's values over the top.

### Deferred / next

- `Debug`'s own project default (`debug`) is still read by `ProjectDefaults` rather than by `BotSettings`.
  Folding it in would make one class own every project-seeded runtime value, but `Debug` has its own
  lifecycle (it is toggled from Studio at run time over the telemetry wire) and is deliberately left alone.

---

## 2026-07-31 — refactor Phase 3: the test floor (SD5 partial, SD6)

Part of the repo-wide refactor scheduled in `../docs/refactor/02-execution-order.md`; this module's share is
units **SD5** and **SD6**, test-only. **118 → 143 tests**, no production code touched.

### Done

- **MISSING 1 — `MissingTemplateTest`, and B9 is reproduced.** `find()` and `findAny()` on a template whose
  PNG does not exist return normally, indistinguishable from "not on screen"; both red on this commit,
  `@Disabled` pending **SD3**. The two tests that already pass are as important: `getMat()` *does* produce the
  precise diagnostic (naming the file), so every fix is about propagating it rather than writing it. And a
  fourth test pins the distinction the fix must not lose — a template that **loads** and is genuinely absent
  stays an ordinary quiet miss, or SD3 trades a silent wrong answer for a bot that throws on most polls.
- **MISSING 2 — `MatchResultNullContractTest`.** All eight accessors, both directions (`!found ⇒ null` and
  `found ⇒ non-null`), plus the geometry that makes the guard worth passing and 500 samples proving the random
  click point lands inside the match. `miss()` gets its own case: it looks like an exception to the rule and
  is not — a real below-threshold score for telemetry, everything else still null. A ninth test asserts the
  **accessor list is complete** by reflection, so adding an accessor cannot silently shrink what the other
  tests claim to cover.
- **MISSING 5 — `SessionBackendLadderTest`.** `SessionBootstrapTest` covered the isolation ladder; the
  *backend* ladder had nothing. Four rungs pinned in order, plus the property that matters most: the bottom
  rung is **not a constant but a function of the launch kind**, so flattening the ladder into a default would
  look equivalent and send every game to Xephyr's software GL. Totality is pinned too — `auto` and typos fall
  through — because that exact case has already been a bug here once.
- **MISSING 6 — `ImageTemplateReloadTest`.** The reload guard overwrites a non-null-but-empty `Mat` without
  releasing it, leaking one native handle per reload; red on this commit, `@Disabled` pending **SD9**. Worth
  noting what writing it turned up: the only way to reach that state through the public API is to release the
  returned `Mat` directly, which the javadoc forbids. So either the branch is reachable and leaks, or it is
  dead code shaped like a reload path — and SD9 should decide which rather than patching it.

### Not written — SD5's remaining items

**MISSING 3** (`compare*` agreement across `ImageClicker` and `ImageFinder`), **MISSING 4** (`api.emulator`,
0.0% over 176 lines) and **MISSING 7** (`Bots`' optional-bridge `LinkageError` degradation) are **not in this
commit**. They are the three that need a fixture this module does not have yet — a matcher harness, a fake ADB
device, and a classloader that can fail one class on demand — and writing any of them badly would produce the
green-but-vacuous test Phase 2 deleted a class for. Scheduled to land with the rest of Phase 3.

---

## 2026-07-31 — refactor Phase 2: the harnesses leave the published jar

Part of the repo-wide refactor scheduled in `../docs/refactor/02-execution-order.md`; this module's
share is units **SD1** and **SD2**.

### Done

- **Deleted six dead files, 728 lines — 8.5% of the module.** None was referenced by anything, in
  any of the four modules or any test:

  | File | Lines | What was in it |
  |---|---:|---|
  | `internal/capture/linux/LinuxControllerTest` | 348 | 96 prints, a `main()`, the module's only `ProcessBuilder`, 3 broad catches, a `printStackTrace` |
  | `internal/opencv/OpencvTest` | 149 | 9 prints, a `main()` |
  | `internal/capture/CaptureTest` + `internal/capture/ImageDisplay` | 180 | 18 prints, 3 `printStackTrace`, a Swing `JFrame` |
  | `internal/Main` | 25 | `ImageDisplay imageDisplay = null;` — declared, assigned null, never used — and a `placeholder()` that printed `"[Debug] Placeholder function executed."` |
  | `com/botmaker/sdk/Main` | 26 | an IDE hello-world scaffold **shipped in the published jar**: printed "Hello and welcome!", then clicked `src/main/resources/images/accept_button.png`, a path `.gitignore` guarantees is absent from a clean checkout |

  `CLAUDE.md` documented them as "manual `main`-method harnesses, not JUnit tests", so they were
  deliberate once. They were also the module's only untested code, they were `main()`s no one had
  run, and they were on the classpath of every bot the SDK ships to. **All five `printStackTrace`
  calls in this module went with them** — the SDK now has none.
- **Deleted `ImageFinderGroupTest`** — a class with zero `@Test` methods whose javadoc claimed the
  screen-dependent group paths were "exercised by the manual `com.botmaker.sdk.Main` harness". Both
  halves of that sentence were false, and a reader had no way to tell. The group paths are genuinely
  uncovered; the audit's MISSING list is where that is now recorded.
- **Coverage moved 32.0% → 38.4% line, 73.1% → 80.3% class, without a test being written** — 417
  missed lines stopped existing. (The audit predicted ≈65%; see the note below.)

### Note on the ≈65% estimate

`13-sdk.md` §14 projected "post-deletion the same tests read **≈65%**". That is arithmetically out of
reach for this deletion and the plan should not be read as having a missed target: 797 covered lines
against 2494 total is 32.0%, and 65% would need the total down near 1226 — a cut of ~1270 missed
lines from a deletion of 728 source lines, of which only 417 were executable. The real number is
**38.4%**. Recorded here rather than silently absorbed, per `01-audit-method.md`'s rule about items
the plan got wrong.

---

## 2026-07-30 — Phase 12: `Mouse` stops throwing away every click it makes in a session

The reported symptom was a bot that found its template, clicked, and got only the game's **hover** effect — on a
session it had correctly adopted. `Mouse.click`/`rightClick`/`middleClick` called
`NativeController.clickRestoringCursor` unconditionally, and `doubleClick`/`drag` did their own
`cursorPosition()` → `mouseMove(origin)`, so on a private display the pointer was warped off the target
microseconds after the release. A UI that polls pointer position per frame (rather than reading the event's
coordinate) then applied the click wherever the cursor had gone.

### Done

- Every gesture now goes through shared's `PointerPolicy`: `click` for the press paths, `restoreTo` for the
  end-of-gesture restore in `doubleClick`/`drag`. On `:0` nothing changes — the cursor is still handed back, which
  is the only reason synthesized input is tolerable there. The policy lives in shared because Studio's pilot had
  implemented it and the SDK had not; see `botmaker-shared/ROADMAP.md` for that half.
- **`Mouse.doubleClick` now holds each press** for `controller().pressHoldMs()` instead of being
  `down(); up(); down(); up();` — back-to-back that is under one frame at 60 fps, so a target sampling input once
  per frame could drop it entirely. Same reason `NativeController.click` and `ControllerPointer.click` hold.
- `MouseSessionPointerTest` (4) pins all of it by asserting the *absence* of the trailing warp in a session, its
  presence on `:0`, the same for a drag, and that the double-click's holds actually elapse.

---

## 2026-07-30 — Phase 11 (step 3): adopt the session we were handed

`SessionBootstrap.launchIsolated` gains a rung above every other: when the spawning process offers a live private
display (`botmaker.session.display` — Studio's background launcher passes it after "▶ Launch now"), the bot joins
it via shared's `AdoptedSession` and **does not launch the target again**. Building a second session instead means
handing the launch to the copy already running there (every store launcher is single-instance), so the game ends up
on a display nobody is watching.

The rung sits *below* the isolation gate, so `session.isolated=false` still means `:0` — an offer, not an
imposition. An offered display with no window on it is declined and closed: the target isn't up there, and an
adopted session can't launch it, so adopting would hand the bot a black frame with no way out.

---

## 2026-07-30 — Phase 11 (step 2): sweep before deciding whether to isolate

`SessionBootstrap.launchIsolated` now calls `NestedSession.reapOrphanSessions()` **before**
`LaunchIsolation.check`. The probes behind that verdict read a dead session's leftovers as a launcher that is up
— measured: a bot refused to isolate, naming a Heroic that had been closed for hours — and the only other sweep
lives inside a successful `NestedSession.start`, which a refusal never reaches. Sweeping late meant refusing a
launch on a dead session's account and running the bot on the user's real desktop instead.

---

## 2026-07-30 — Follow-on: `postLeftClickScreen` → `click(x, y, button)`

shared folded `NativeController.postLeftClickScreen` onto the new `click(x, y, button)` — the same call
hardcoded to button 1, under a name left over from the `XSendEvent` era (see shared's Phase 10 A3/A4 entry for
why the click paths were reworked). Nothing in the SDK's public API touches it; this is the mechanical
follow-on.

### Done

- `internal/capture/CaptureTest` and `internal/capture/linux/LinuxControllerTest` (the manual harnesses) call
  `click(x, y, 1)`.
- The `NativeController` test doubles drop their `postLeftClickScreen` overrides — `click` has a portable
  default (move → settle → press → hold → release), so `RecordingNativeController` keeps recording the same
  `move`/`button` calls it always did.
- `Mouse.click` is unchanged: it still takes `clickRestoringCursor`, which is the right policy on `:0`, and is
  now that default plus a warp back rather than a second copy of the sequence.

---

## 2026-07-29 — Isolated-launch fixes, Phase 7: decline to `:0` with a reason, before spawning anything

`SessionBootstrap.launchIsolated` used to find out that a target couldn't be confined only *after* bringing up a
display and burning the whole window budget on it — then fall back to `:0` with a guess as the explanation.

**Done**

- **The isolatability question is asked first.** `launchIsolated` consults shared's new
  `LaunchIsolation.check(spec)` before choosing a backend; a non-isolatable target logs the verdict's reason and
  returns `false` (the caller's normal `:0` launch) without spawning an X server. The reason is shared's wording,
  so a headless bot run and Studio's Launch button say the same thing.
- **The post-launch message reports what happened instead of guessing.** When a window never appears on `:N`,
  `LaunchIsolation.noWindowDiagnosis(spec)` distinguishes "it is running, but outside the private display" from
  "nothing is running under that target" by reading the process table — the two used to be offered as one "or".

---

## 2026-07-29 — Isolated-launch fixes, Phase 3: `api.Session`, isolation as a first-class SDK setting

Isolation was real but unspoken: it lived only in `botmaker-project.properties` plus two environment overrides,
so a bot's own source never said whether it ran on a private display. It is now a facade shaped exactly like
`api.Debug` — the setting a bot reads and writes the way it reads and writes every other setting.

**Done:**
- **`api.Session`**: `isEnabled()`/`enable()`/`disable()`/`set(boolean)` plus `useBackend("gamescope"|"xephyr"|
  "auto")`. Default on, so the *default costs no code* — the generated `BotSettings` will emit `Session.disable()`
  only when the project opts out (Phase 4), leaving a default project's source free of session boilerplate.
- **One precedence ladder, bot code at the top**: explicit `Session` call → `botmaker.session.isolated` sysprop →
  `BOTMAKER_SESSION_ISOLATED` → the project's `session.isolated` key → `true`; `useBackend` follows the same
  shape over `botmaker.session.backend` / `session.backend`, bottoming out in the *kind-driven* choice rather
  than a fixed backend. Bot code outranks the environment so a bot can force its own behaviour on a machine that
  disagrees. `Session.isEnabled()` reports the **resolved** answer, not merely what bot code asked for.
- **Deliberately not seeded from the project file** the way `Debug` seeds its flag: seeding would make "the
  project says true" indistinguishable from "the bot said true", and the override could then never be ranked
  above the environment. `Session` holds a nullable override; `SessionBootstrap` owns the ladder.
- **Bug fixed on the way**: `SessionBootstrap.backend` resolved every rung with
  `"gamescope".equalsIgnoreCase(x) ? GAMESCOPE : XEPHYR`, so an explicit `session.backend=auto` — or any typo —
  pinned a *game* to Xephyr's software GL, the exact crash the kind-driven choice exists to prevent. Every rung
  now parses through the new total `NestedSession.Backend.fromId` (shared owns it, since Studio needs the same
  parse), which is empty for `auto`/unknown and therefore falls through to the next rung.
- **The bot-side fallback now explains itself**: when the display comes up but nothing maps on `:N`, the trace
  appends `HostLauncherProbe.refusalMessage(kind)` if a host launcher is running — the same wording Studio uses,
  because a forwarded Heroic/Steam invocation mapping the game on `:0` is the overwhelmingly common cause.
- **Tests**: `SessionBootstrapTest` covers each rung — an explicit call beating the sysprop in both directions,
  `isEnabled()` reporting the resolved value, `useBackend` beating the property, `"auto"` un-pinning, and the
  `auto`/typo regression above. `Session.clearOverrides()` exists for the teardown (process-wide statics).

**Deferred / next:** Phase 4 — Studio's generated `BotSettings` line, the "Session" dialog section, and using
the refusal message on the Launch buttons.

---

## 2026-07-29 — Bot-owned-display plan, Phase I: explicit `Game.launch*` isolates too

`Target.start()` routed through `SessionBootstrap.launchIsolated`, but a hand-written
`Game.launchHeroic("Firestone")` went straight to `GameLauncher` on `:0`. Extend the same seam to every
explicit launch entry point.

**Done:**
- `Game.launch`/`launchSteam`/`launchEpic`/`launchHeroic`/`launchFaugus` build the call's `LaunchSpec` and
  try `SessionBootstrap.launchIsolated(spec)` first, falling back to the `GameLauncher.*` host launch only
  when isolation is off / declined. `launch(exe)` with no args → `EXE` (gamescope under isolation);
  `launch(exe, args…)` → a `CLI` command preserving the args (Xephyr, per the cli policy); a blank path → the
  host path so the usual validation error still fires. `launch()` returns `null` when routed into `:N` (no
  host process handle) — documented. The `…IfNotRunning` variants isolate transitively via these.
- `GameTest`: a fake active session proves a launch is routed into `:N` (returns null / no host spawn);
  opt-out falls back to a real host process; the reject-empty contracts still hold.

**Deferred / next:** Phase J surfaces the opt-out toggle in Studio and backgrounds its Launch buttons.
Carrying `exe:` arguments into an isolated display (today args force the CLI/Xephyr path) needs a shared
`LaunchCommands`/`NestedSession` change — left for when it matters.

## 2026-07-29 — Bot-owned-display plan, Phase H: isolation is on by default

Isolation was gated on a system property nothing set, so a bot only isolated from BotPilot. Drive it from the
project's `session.isolated` setting (default true) so a bot run *anywhere* with its project file on the
classpath isolates — no `DebuggingService`/`CodeExecutionService` change needed, since those already run the
bot JVM with the project root as cwd and `src/main/resources` on the classpath.

**Done:**
- `ProjectDefaults.sessionIsolated()` (delegates to shared, default true) and `sessionBackend()`.
- `SessionBootstrap.isolationRequested()` now defaults from `ProjectDefaults.sessionIsolated()`, with the
  `botmaker.session.isolated` system property / `BOTMAKER_SESSION_ISOLATED` env as an override in either
  direction. `backend(spec)` also honours the project's `session.backend` as an override alongside the
  system property.
- `SessionBootstrapTest`: default-on, system-property-overrides-to-off, opt-out disables bring-up.

**Deferred / next:** Phase I routes explicit `Game.launch*` through the same seam; Phase J surfaces the
opt-out toggle in Studio and backgrounds its Launch buttons.

## 2026-07-29 — Bot-owned-display plan, Phase G: auto-select the backend by launch kind

`SessionBootstrap` picked its backend from `botmaker.session.backend` alone (default Xephyr), so an isolated
game launch crashed on Xephyr's software GL. It now consumes shared's `SessionBackends` so the backend follows
the launch kind, and declines bring-up (loud install hint, graceful `:0`) when the required backend is absent.

**Done:**
- `backend()` / `options()` → `backend(LaunchSpec)` / `options(LaunchSpec)`: the `BACKEND_PROPERTY` override
  still wins, but with no override the backend is `SessionBackends.preferredBackend(spec)` — a game gets
  gamescope, a plain command gets Xephyr.
- `launchIsolated`: before bring-up, if the chosen backend isn't installed (`SessionBackends.isAvailable`),
  logs `SessionBackends.installHint` and returns `false` (runs on `:0`) — a bot never crashes on a Xephyr that
  can't run its game, and gamescope-missing is a clear message, not a SIGTRAP loop.
- `SessionBootstrapTest` updated: backend auto-selects from kind, override wins, options track the kind.

**Deferred / next:** Phase H makes isolation the default via a `session.isolated` project setting; Phase I
routes explicit `Game.launch*` through the same seam.

## 2026-07-28 — Bot-owned-display plan, Phase D: `Window.find` uses shared ranked matching

`Window.find` (and thus `NamedWindow`/`CaptureSource.window(title)`) took the **first** window whose title
contained the needle, so a bot pointed at "Firestone" could bind a wiki tab or launcher entry instead of the
game.

**Done:**
- `find` now delegates to shared `WindowMatch.best(controller().getAllWindows(), needle)` — an exact/prefix/
  whole-word hit beats an incidental substring, dynamic title suffixes are tolerated, and the shortest/largest
  matching window wins ties. Same matcher the Studio pilot uses, so runtime and editor resolve identically.
  No API change; `all()`/`foreground()` unchanged. SDK input/routing suites stay green.

## 2026-07-28 — Bot-owned-display plan, Phase B: bot-runtime routing onto a nested `:N` session

The session infrastructure (shared) and the Studio pilot producer (Phase A) were in place, but the **generated
bot's own runtime** still drove the global `:0` singleton — `Mouse`/`Keyboard` used `NativeControllerFactory.get()`
and `Source` resolved windows by title. Phase B routes the bot runtime onto the active session, so an isolated
bot clicks/types/reads its game on its private `:N` display exactly as a plain bot does on `:0`.

**Done**

- **Input choke points** — `Mouse.controller()` / `Keyboard.controller()` now return
  `ActiveSession.get().controller()` when a session is registered, else the `:0` singleton. With no session
  (the default) behaviour is byte-for-byte today's; `InputApiTest` is unchanged and still green.
- **`api.capture.SessionSource`** — a `CaptureSource` over the active `DesktopSession`: `capture()` is the
  session's frame of its owned window, `targetWindow()`/`origin()` come from `attached()`. This is where the
  "a nested session owns one window, so there's no capture *target* to pick" answer lands in the SDK — the
  `capture.source` title selector is bypassed while a session is active.
- **`Source.current()`** prefers a `SessionSource` whenever `ActiveSession` is set and the bot hasn't pinned a
  source; an explicit `Source.set(...)` still wins (and `set(null)` clears the pin, handing control back to the
  session). Non-isolated bots keep the project-default path unchanged.
- **The bot-runtime producer** — `internal.session.SessionBootstrap`, reached from `Target.start()`/
  `startIfNotRunning()`: gated **off by default** behind the `botmaker.session.isolated` system property (or
  `BOTMAKER_SESSION_ISOLATED` env). When on, it brings up a `NestedSession` (Xephyr default;
  `botmaker.session.backend=gamescope` opts into 3D) sized from the project's authored resolution, registers it
  via `ActiveSession`, and launches the target into it — idempotent, with graceful fallback to `:0` when
  bring-up fails or no window maps. A persisted project setting + Studio UX for isolated runs is the follow-up;
  the property gate keeps the seam testable and reversible without touching Studio or the project file format.
- Tests: `SessionRoutingTest` (input routes to the session controller when active, the global one when not;
  `Source` follows the session window; an explicit pin wins) and `SessionBootstrapTest` (the pure gate /
  backend / size selection). Full sdk suite green (106). Live bring-up stays manual (needs a real X server).

**Deferred / next** — Phase C (shared): gamescope-variant live test. Then real bot-on-`:N` end-to-end on a box
with a display; a persisted `session.isolated` project key + Studio toggle; `restart()` relaunch into `:N`.

---

## 2026-07-23 — `Keyboard`'s javadoc stops promising something it never did

**Done**

- The class javadoc said keys are "Backed per-OS by XTest (Linux)". Linux input has been a pluggable backend
  (xsendevent / uinput / xdotool / XTest) for a while, and which one is active decides whether a key reaches a
  game at all — so the one line a bot author reads was both stale and hiding the thing that matters.
- Documented the actual trade the targeted overloads make on Linux: under the cursor-safe default the key is
  delivered in the background but games reject it; once real input is on, it comes from a kernel virtual
  device a game cannot distinguish from a real keyboard — and that device carries no window, so targeting is
  implemented as *raise the window, then type*. Keys land, but the game is brought forward. See shared's
  ROADMAP entry of the same date for the backend work behind this.
- No signature changes: the fix is in `LinuxController` behind the existing `keyDown(window, code)` entry
  point, so `Keyboard`'s targeted overloads keep their meaning and simply start working.

---

## 2026-07-23 — `internal` shrinks to what is genuinely SDK-shaped

**Done**

- **Moved to shared:** `internal/opencv/*` (the matching engines, the raw records and the OpenCV loader) and
  the desktop-capture backends `internal/capture/{ScreenCapture,CaptureBackend,RobotCapture,SpectacleCapture}`,
  together with the `opencv` and `CaptureBackend` tests. Studio matches at edit time and captures at edit
  time; only the SDK had this code. See shared's ROADMAP for the full move and for the three-OpenCV-loaders
  finding.
- **`internal/config/ProjectDefaults` is now a thin typed accessor.** The file, its key names, the caching and
  the parsing belong to shared's `ProjectProperties` — Studio writes exactly those keys — leaving here only
  what shared cannot do: mapping the raw values onto `CaptureSource` and `Size`.
- **The SDK owns the mapping to its public types.** `api.vision` maps `RawMatch`/`RawColorMatch` onto
  `MatchResult`/`ColorMatch` as before, and the new `ImageTemplate.authoredSize()` is the single place the
  authored resolution converts from `api.Size` to the `java.awt.Dimension` shared's matcher takes. No SDK
  type crosses into shared.
- **What stayed, and why:** `internal/observe/IpcObserver` implements `api.observe.BotObserver` and consumes
  `MatchEvent`/`ClickEvent`/`Surface`/`Bots` — it *is* the SDK-side adapter onto shared's telemetry wire, so
  moving it would drag the SDK types along. The `main`-method dev harnesses (`internal/Main`, `CaptureTest`,
  `OpencvTest`, `capture/linux/LinuxControllerTest`) stay, as does the Swing `ImageDisplay` they are the only
  callers of.
- No compatibility shims, per the freely-breakable note. 95 tests pass.

---

## 2026-07-23 — `Game`/`LaunchTarget` become facades over `shared.launch`

**Done**

- The OS half of `api.launch.Game` (protocol URLs, CLI ladders, `kill`, `isRunning(String)`),
  `api.launch.RunningProbe` and `internal.launch.UriLauncher` **moved to `com.botmaker.shared.launch`** so
  Studio can launch and describe a target without depending on the SDK. See shared's ROADMAP for the full
  move and for the Heroic false-positive fix that rode along with it.
- What stayed, because it needs SDK types: `Game`'s `CaptureSource`-shaped methods (`isRunning(CaptureSource)`,
  `waitForLaunch`, `launchIfNotRunning`, `launchAndWait`, `launchEpicIfNotRunning`) are unchanged, and
  `LaunchTarget` keeps its sealed hierarchy plus the one running-detection layer shared cannot see — the
  ambient `Source.current()` window. Each variant is now just its `launchSpec()`; `start`/`isRunning`/
  `restart`/`spec`/`runningToken` are defaults delegating to `Launcher`/`LaunchSpec`.
- No compat shims (the API is freely breakable until a published bot consumes it): `RunningProbe` is gone from
  `api.launch` and `internal/launch` no longer exists. Bot-facing names and behaviour are unchanged.

---

## 2026-07-22 — `startIfNotRunning` stops relaunching a game that is already up

**Done**

- **The probe asked the wrong thing.** `LaunchTarget.startIfNotRunning()` decided "already running" purely from
  the ambient capture source's window. When a project captures the **desktop or a monitor**,
  `CaptureSource.hasWindowIdentity()` is false, so the answer was an unconditional "not running" and every
  Steam/Epic/Heroic/Faugus target relaunched on every run. A plain PID probe can't replace it either: the
  `steam://` / `xdg-open` / `faugus-launcher` process we spawn hands off and exits within a second, so the game
  was never our child.
- **New `LaunchTarget.isRunning()`, layered over observations — no cooldown, no "we launched it recently".**
  First hit wins: (1) the ambient source's window, when it has a window identity at all — the cheap answer;
  (2) a process *we* spawned for this `spec()` still being alive; (3) any live process whose **command line**
  mentions the target's `runningToken()`, via `ProcessHandle.allProcesses()`; (4) a window titled after that
  token, enumerated from the OS through `NativeController.getAllWindows()` rather than from the capture source.
  `startIfNotRunning()` is now `if (isRunning()) return; start();`, and `Target.isRunning()` exposes it to bots.
- **`runningToken()` is the launcher's launch identity, not our `spec()`.** Steam's is `AppId=<id>`, *not* the
  bare id — a 3-digit number would match unrelated command lines by accident, whereas Steam's own wrapper
  spells it `reaper SteamLaunch AppId=570 --`. Epic/Heroic use the `AppName`, Faugus the `gameid`, `exe:`/`cli:`
  the executable's file name. Matching the **wrapper** is the point: `reaper`, `proton`, `umu-run` and
  `legendary` all carry the token, and one of them is what a launcher-started game actually runs as.
- **Steam gets its own authority first.** Steam publishes the app id it is running — Windows
  `HKCU\Software\Valve\Steam\RunningAppID` (via shared's `WindowsRegistry`, normalising the `0x…` DWORD form),
  Linux `~/.steam/registry.vdf`. A mismatch **falls through** to the shared layers rather than answering "no":
  the key isn't written for every launch path (non-Steam shortcuts), so it can confirm but must not veto.
- **`EmulatorApp` answers over ADB** — instance up *and* `currentApp()` matching the package. Nothing on the
  host process table describes an app running *inside* an emulator, so the generic layers can't see it.
- `Game.launch` returns the spawned `Process` so layer 2 can record it; `exe:`/`cli:` are the only variants that
  do, since theirs is the only spawned process that *is* the target. `tryStart` deliberately stayed `boolean` —
  recording a launcher process that exits a second later would be a handle that always says "dead".
- The old `Exe`/`Cli` `startIfNotRunning` overrides (which shelled out to `pgrep -f`/`tasklist` via
  `Game.isRunning(String)`) are gone — layer 3 does the same job in-process. `Game.isRunning(String)` stays as a
  bot-facing block.
- **Accepted residual risk:** for the ~second between `start()` handing off and the wrapper appearing, every
  layer is legitimately false, so a bot calling `startIfNotRunning()` in a tight loop could double-launch.
  `Game.launchAndWait` (blocks on the window) is the existing answer, and the `[Target]` traces make it visible.
- Covered by `LaunchTargetProbeTest`: the desktop-source regression, Steam's token shape, the command-line scan
  against a real process, and a recorded handle that stops counting once it exits.

**Deferred / next**

- Nothing verifies the Steam registry/VDF read on a real machine yet — both paths are keyed off files this repo
  has no fixture for. Manual check: start a Steam game, confirm `isRunning()` traces "Steam reports it".

---

## 2026-07-22 — Clicks and keys that land in a game

**Done**

- **`Mouse.click` was the odd one out.** `rightClick`/`middleClick`/`doubleClick`/`drag` already drove real
  input, while `click` alone called `postLeftClickScreen` — a posted `WM_LBUTTONDOWN` on Windows and an
  `XSendEvent` on Linux, both of which games drop by design. All of them now go through shared's
  `NativeController.clickRestoringCursor`, so the pointer moves to the target and returns to where it was.
  `doubleClick` restores only after both presses (restoring between them would read as two single clicks),
  and `drag` restores only after the button is released.
- **`ClickConfig.useRealInput(boolean)`** — the switch a bot uses to say "my target is a game". Lives here
  with the other tuning knobs, in the same shape as the existing `enableDebugMode` delegate. This cannot be
  auto-detected: neither X nor Windows reports whether a synthetic event was accepted — the target simply
  drops it — so "the click didn't land" is unobservable and it has to be a setting. One-way, because on
  Linux it swaps the process-wide input backend; call it once before the first click. Studio's generated
  `Game bot` template now emits it at the top of `main`, so it is visible and editable as a block rather
  than hidden in a properties file.

**Deferred / next**

- An earlier iteration routed this through a `ProjectDefaults` key (`input.real`) read at start-up. Replaced
  because it made the SDK's behaviour depend on a file format Studio had to write correctly — an implicit
  contract — where `ClickConfig` is explicit API the bot author can see. If a Studio-side toggle is wanted
  later, it should rewrite that generated statement, not reintroduce the key.

---

## 2026-07-22 — Debug: one switch, every method, silent when off

**Done**

- **`Debug` is now a delegate over `botmaker-shared`'s new `com.botmaker.shared.Diag`** — same API
  (`isEnabled`/`enable`/`disable`/`set`/`log`/`error`), plus an `error(String, Throwable)` overload that
  replaces every `printStackTrace()`. The flag lives in `shared` because `shared` prints diagnostics of its
  own and can't depend on the SDK; one toggle now silences both modules.
- **Ungated prints routed through `Debug`** — `ImageClicker`, `ImageFinder`, `OpencvManager`,
  `RobotCapture`, `SpectacleCapture`, `UriLauncher`. These bypassed the flag entirely, which is the "it
  debugs even when it's off" symptom. `ImageWaiter`/`Pixel`/`Text` had the *right* gate but wrote through
  `System.out` inside an `if (Debug.isEnabled())` block; collapsed to a plain `Debug.log`/`Debug.error`.
- **Traces added to the facades that had none** — `Mouse` (every click/move/button/drag/scroll),
  `Keyboard` (press/release/tap/combo/type, naming the target window or "focused window"), `Wait`,
  `Emulator` + `Emulators`, `Source.set`/default resolution, `Window` (find/foreground/click/focus/move/
  resize), `NamedWindow` (an unresolvable title is now an explicit error line, not a silent `null` capture),
  `Watchdog` (enable/disable + the stuck throw). Previously debug output was vision- and launch-only, so an
  input problem produced nothing at all — the `Mouse`/`Keyboard` lines are what make the click fixes
  observable.
- **Not gated, deliberately:** `BotMaker.print` (that's the bot's own output) and the
  `\u0001BM-INPUT:` marker Studio parses.

---

## 2026-07-22 — Faugus Launcher as a launch target

**Done**

- **`Game.launchFaugus(String gameId)`** — Faugus registers no protocol handler, so it goes straight to the
  CLI: `faugus-launcher --game <id>`, then `flatpak run io.github.Faugus.faugus-launcher --game <id>`. The id
  is Faugus's `gameid` (matched exactly by its runner), not the title.
- **`LaunchTarget.Faugus(gameId)`** with spec `faugus:<gameId>`, wired into `parse` and the spec table.
  `startIfNotRunning` comes free from the window-based default. Faugus is how non-Steam Windows launchers
  (Battle.net, EA App, HoYoPlay) run under umu/Proton, which is what the maintainer actually automates.

---

## 2026-07-22 — "Already running" is decided on the window, not the process

**Done**

- **`LaunchTarget.startIfNotRunning()` now has a real shared default**: it asks the ambient
  `Source.current()` whether the target's window is open and skips the cold launch when it is. Previously
  `Steam`/`Epic`/`Heroic` inherited a default that just called `start()` (so they always relaunched), and
  `Exe`/`Cli` probed a *process name* — which never matches a game started through Steam's `reaper`, Heroic's
  `legendary` or a Proton/Wine wrapper, where the running process has nothing to do with the spec token. The
  window the bot captures is by definition the thing that has to be up, so it is the right probe, and it is
  uniform across every launcher and both OSes.
- **`CaptureSource.hasWindowIdentity()`** (default `false`, `true` on `NamedWindow`, delegated by
  `region(...)`) gates that check. `isPresent()` is hardcoded `true` for `desktop()`/`monitor()` because they
  always exist, so trusting it there would report "already running" forever; when it is false the target falls
  through to `start()` — or, for `Exe`/`Cli` (the only variants whose spec token *is* a real process name), to
  the existing process probe, which they keep as a fallback. `EmulatorApp`'s ADB path is untouched.
- **`Game.isRunning(String)` no longer matches the bot's own JVM**: `pgrep -f` matches whole command lines, so
  a name that appeared in the bot's own arguments made every game look running. It now passes `--` (so a name
  starting with `-` isn't read as a flag), reads the pids and discards `ProcessHandle.current().pid()`.
- `LaunchTargetRunningTest` covers the three branches (window present → skip, window absent → launch, source
  with no window identity → never skips) against a fake `NativeController`.

## 2026-07-22 — No-arg `Keyboard` targets the project source; Heroic + CLI launch targets

**Done**

- **No-arg `Keyboard.press/release/type` now target the ambient `Source.current()`** instead of whatever holds
  focus — the same "where" every no-source vision/mouse call uses, so a bot configured to a game window types
  into that window. They delegate to the `(CaptureSource, …)` overloads, which already fall back to the
  focused-window path when the source has no single window (`desktop()`/`monitor()`/emulator/unopened). `tap`/
  `combo` compose from `press`/`release`, so they follow automatically.
- **`Game.launchHeroic(appName)`** — launches Epic/GOG games through the Heroic Games Launcher (the practical
  path on Linux): opens `heroic://launch/<appName>` via `UriLauncher`, falling back to the Heroic CLI
  (`heroic --no-gui launch`, then the Flatpak `flatpak run com.heroicgameslauncher.hgl --no-gui launch`).
- **Two new `LaunchTarget` variants**: `Heroic(appName)` (`heroic:<appName>`) and `Cli(commandLine)`
  (`cli:<command>`, tokenized on whitespace and run via `Game.launch`; `startIfNotRunning`/`restart` keyed on
  the first token as the process name). Both added to `LaunchTarget.parse`; round-trip covered in
  `LaunchTargetTest`. Studio's picker (`HeroicLibraryScanner` + "Heroic game…"/"CLI command…") produces them.

## 2026-07-22 — `Keyboard` is targetable at a `CaptureSource` (symmetric with `Mouse`)

**Done**

- **`Keyboard.press/release/tap/combo/type(CaptureSource, …)`** overloads, mirroring
  `Mouse.click(CaptureSource, …)`: they deliver keys to *that* window instead of whatever holds focus, so a bot
  can type into a background window without focusing it. Pure additions — the no-arg methods are unchanged.
- **The seam is `CaptureSource.targetWindow()`** (default `null`): a resolved `Window` (and a `region(...)` of
  one) returns its native `GenericWindow`; `desktop()`/`monitor()`/an unopened `window(...)`/an emulator return
  `null`, so the targeted call transparently falls back to the focused-window path. `NamedWindow` resolves
  lazily like its capture path.
- Backed by shared's new `NativeController.keyDown/keyUp/typeText(GenericWindow, …)` (2026-07-22, shared
  ROADMAP). No Studio change: the palette discovers the new overloads by scanning the SDK jar at runtime, and
  a `CaptureSource`-typed first argument is already auto-seeded from the project default.
- Verified: `InputApiTest` gained targeted tap/combo/type routing + a no-window fallback case (12 tests green).

---

## 2026-07-20 — `Activity` is outcome-typed (`Activity<O extends Enum<O>>`)

**Done**

- **`Activity.run()` returns an outcome instead of `void`**, and `execute()` carries it back:
  `Activity<O extends Enum<O>>`, `abstract O run()`, `final O execute()`. An activity reports *what happened*
  (`BAG_FULL`, `NO_ORE`) and the flow drawn in Studio decides where each outcome goes — the activity never
  names another activity, so rewiring the canvas touches no Java. (API break — allowed; lands with the
  matching Studio flow-driver codegen.)
- **Generic rather than a shared marker interface** because `execute()` is `final`: a marker would force every
  driver dispatch through a cast, and a covariant override can't remove it on a final method. The type
  parameter makes `MINING.execute()` statically `Mining.Outcome`, so the generated `switch` over it is
  exhaustive and compiler-checked — the whole reason for using an enum here.
- The static name registry is now `Map<String, Activity<?>>`: enabling by name has nothing to do with the
  outcome type, so it deliberately forgets it. `disable`/`enable`/`setEnabled(String, …)` are unchanged.
- A stuck activity still produces **no** outcome — `BotStuckException` propagates to the supervisor rather
  than becoming a routable result, since recovery, not the flow, decides what happens next.

## 2026-07-20 — `Emulator.platform()` returns `PlatformId`

**Done**

- Followed shared's `String platformId` → `PlatformId` enum change. `Emulator.platform()` and
  `EmulatorRef.platform()` now return the enum (callers wanting the old string use `.id()`, or
  `.displayName()` for UI); `toString()` still prints the wire id.
- `Emulators.connect(host, port)` stamped a hand-rolled sixth id, `"custom"`, when an endpoint matched no
  discovered instance — it now uses `PlatformId.UNKNOWN`, which is exactly what that constant is for.

## 2026-07-20 — Start-vs-restart-aware startup (`StartMode`)

**Done**
- **New `api.bot.StartMode { COLD, RESTART }`** and the supervisor now hands the start-up step which one it is.
  `Bot.start`/`supervise`'s `startGame` param changed from `Runnable` to `Consumer<StartMode>`: cold start calls
  `startGame(COLD)` then `goHome`; recovery calls `goHome` then `startGame(RESTART)`. (API break — allowed;
  lands with the matching Studio `Startup` template.)
- **`Target.startIfNotRunning()`** (cold path) + `LaunchTarget.startIfNotRunning()` default. **`Exe`** overrides
  it to skip launch when its process is already running (`Game.isRunning(name)`), and overrides `restart()` to
  force-stop by process name (`Game.kill`) then relaunch — the "shut a frozen game down before restarting" case.
  Steam/Epic keep the idempotent-relaunch default; `EmulatorApp.restart()` already stop-then-starts the app.
- Wired `Game.kill` / `Game.isRunning(String)` / the skip-if-running primitives that existed but were unused.

---

## 2026-07-19 — Unified debug output switch (`api.Debug`)

**Done**
- **New `api.Debug` facade** — one global switch governing *all* SDK diagnostic printing. `isEnabled()` /
  `enable()` / `disable()` / `set(boolean)` plus `log(String)` / `error(String)` helpers that print only when on.
  **Default on**, seeded once from the project's `debug` key (see below); overridable at runtime.
- **Unified the two prior debug paths onto it.** The formerly *unconditional* lifecycle/launch traces
  (`[Bot]` / `[Game]` / `[Target]` / `[Activity]` in `Bot`/`Activity`/`Target`/`LaunchTarget`/`Game`) now route
  through `Debug.log`/`Debug.error`, and the vision traces (find/click/wait/pixel/text) now consult
  `Debug.isEnabled()` instead of the separate `ClickConfig.DEBUG_MODE`. **`ClickConfig.DEBUG_MODE` field
  removed**; `ClickConfig.enableDebugMode(boolean)` kept as a thin delegate to `Debug.set(...)`.
- **`ProjectDefaults.debug()`** parses the optional `debug` key (`true/1/yes/on` ↔ `false/0/no/off`) →
  `Boolean` (null when absent/unparseable so `Debug` keeps its default-on).

## 2026-07-19 — `Text.findFuzzy` (edit-distance OCR matching)

**Done**
- **`Text.findFuzzy(needle[, maxDistance], source[, opts])`** (+ current-source overload) — approximate,
  case-insensitive text search tolerant of OCR noise (`l↔1`, `O↔0`, a dropped letter). Slides the needle across
  each recognized line and accepts a window within `maxDistance` Levenshtein edits (`DEFAULT_FUZZY_DISTANCE = 2`);
  exact substring is a distance-0 fast path. Fills `VisionContext.getLastTextMatch()` like the other finders.
  Complements the existing `find` (substring) / `findExact` / `findMatching` (regex).

---

## 2026-07-19 — `Bot.start` is the single public entry point

**Done**
- **`Bot.start(body, recovery)` / `Bot.start(body, goHome, startGame)`** added as the only public way to run a
  bot; both delegate to the existing loop. **`Bot.supervise(...)` is now package-private** — it stays the
  internal machinery but is no longer part of the public palette (Studio surfaces only `public` facade methods
  as blocks, so `supervise` disappears from the menus). Generated game-bot `main` now calls
  `Bot.start(GameLoop::run, GoHome::run, Startup::run)`. `BotTest` stays in-package so it still drives
  `supervise` directly.

---

## 2026-07-19 — Launch target holder + emulator capture source (Phase 3)

**Done**
- **New `api.launch.Target`** — the ambient launch-side counterpart to `api.capture.Source`. `current()` lazily
  initialises from the project default (`ProjectDefaults.launchTarget()`), `set(String|LaunchTarget)` overrides,
  `start()`/`restart()` launch it. `null` target = no-op (a game-bot that hasn't picked a game yet just doesn't
  launch anything). The generated game-bot `Startup.run()` is now simply `Target.start()`.
- **New sealed `api.launch.LaunchTarget`** with variants `Steam`/`Epic`/`Exe`/`EmulatorApp`, each knowing how to
  `start()` (delegating to `Game`/`Emulators`) and round-trip to a `spec()` string. Parsed from the
  `launch.target` project key: `steam:<id>` | `epic:<name>` | `exe:<path>` | `emu-app:<pkg>@<instance>`
  (exe keeps its Windows drive colon; emu-app splits on the last `@`). `EmulatorApp.start()` ensures the named
  instance is running (launch + poll up to 120s), connects, starts the app, disconnects; `restart()` force-stops
  first. Pure parsing is unit-tested (`LaunchTargetTest`).
- **New `api.emulator.EmulatorSource implements CaptureSource`** — resolves an emulator **by instance name**,
  connecting lazily and dispatching a one-time launch if the instance is down (non-blocking: `capture()` returns
  null until it boots, then connects). This is the auto-launch-on-set capture source deferred from Phase 2.
- **`ProjectDefaults`** gained `launchTarget()` (raw `launch.target` spec) and `capture.source = emulator:<name>`
  support (→ `EmulatorSource`). **`Emulator.stopApp(pkg)`** added (`am force-stop`) for `EmulatorApp.restart()`.

**Deferred / next**
- The Studio game/emulator picker writing `launch.target` (+ the full emulator picker dialog) is Phase 4.

## 2026-07-19 — Emulator facade: launch/stop, all-instances, app queries (Phase 2)

**Done**
- **`Emulators.listAll()`** returns every *configured* instance (running or not) as new lightweight
  **`EmulatorRef`** DTOs (name/platform/endpoint, no ADB connection). `EmulatorRef` exposes `running()` (cheap
  TCP probe of the ADB port), `launch()`/`stop()` (host console tool via shared `EmulatorLauncher`), and
  `connect()` → live `Emulator`. This is the "show every instance I could pick without pre-launching" list the
  Studio picker (Phase 4) and the target holder (Phase 3) build on. `list()`/`first()`/`named()` stay the
  running-only connectors.
- **`Emulators.launch(name)` / `stop(name)`** start/stop a configured instance by name without connecting.
- **`Emulators.connect(host,port)` no longer stamps `"adb"`** — it recovers the real product identity when the
  endpoint matches a discovered instance, else labels it `"custom"` (with no launch/stop commands).
- **`Emulator` now carries its `EmulatorInstance`** (instead of loose name/platformId) and gained
  `installedApps()`, `isInstalled(pkg)`, `currentApp()`, `reboot()` (guest `adb reboot`), and `stop()` (host
  console tool, falling back to powering off the guest). Its ctor is `(AdbDevice, EmulatorInstance)`.

**Deferred / next**
- Auto-launch-on-set (resolve an emulator CaptureSource by name, launching + waiting if stopped) lands with
  the Phase 3 current-target holder.

## 2026-07-19 — Fix Epic Games launch opening the Documents folder

**Done**
- `UriLauncher.tryNativeOpener` (Windows) switched from `explorer.exe <uri>` back to
  `rundll32 url.dll,FileProtocolHandler <uri>`. `explorer.exe` treats a custom scheme carrying a query
  string — `com.epicgames.launcher://apps/<AppName>?action=launch&silent=true` — as a filesystem target,
  fails to resolve it, and silently opens a default Explorer window (the user's Documents) instead of the
  game. `rundll32`/ShellExecute takes the full URI as a single argument (no shell, so the `&` isn't split)
  and routes it to the registered protocol handler. Steam (`steam://rungameid/N`, no query string) worked
  either way and still does. The blank-browser bug the `explorer.exe` switch originally targeted was actually
  `Desktop.browse` (already gated to http/https/file only), so this is a safe revert of that one line.

## 2026-07-18 — Emulator capability hoisted to shared; SDK keeps only the `api.emulator` facade

**Done** (Phase 3 refactor — supersedes the Slice A layout below)
- **Moved the whole emulator capability to `botmaker-shared` (`com.botmaker.shared.emulator`):** the dadb
  transport `AdbDevice` **and** discovery (`Platforms`, `EmulatorPlatform`, `BlueStacks`/`LdPlayer`/scaffolds,
  `WindowsRegistry`, `EmulatorInstance`). Reason: discovery is needed by both the SDK (connect) and Studio (list
  instances in the picker), so it can't be SDK-only; moving the transport too lets a future Studio capture-picker
  preview the emulator screen. `dev.mobile:dadb` moved to shared's pom and was **removed from the SDK pom** — the
  SDK now gets it transitively. The SDK's `internal/emulator` package no longer exists.
- **SDK keeps only `api.emulator`** (`Emulator`/`Emulators`), repointed to import `com.botmaker.shared.emulator.*`.
  `Emulator` still `implements CaptureSource` (the SDK-only type that anchors it here). Added `Emulators.use()` /
  `use(String)` — connect-and-`Source.set` shorthands (the one-block "use emulator as source" flow Studio inserts).
- **The click-routing seam stays in the SDK** (it's an `api.capture`/`api.vision` change): `CaptureSource` has
  `default void click(Point)` (region delegates to parent) and `ImageClicker` routes clicks through `source.click`
  so an emulator overrides to `adb input tap`. Guarded by `ImageClickerRoutingTest` (stays in the SDK). The
  discovery parser tests moved to shared.

**Deferred / next**
- Live smoke test on real BlueStacks/LDPlayer (screencap decode, `input tap`, per-instance ADB-port discovery) —
  can't run without an emulator installed.

---

## 2026-07-18 — Android emulator, Slice A: dadb transport + `Emulator` capture source

> Superseded by the entry above: the `internal/emulator/*` classes named here (`AdbDevice`, `Platforms`,
> `BlueStacks`/`LdPlayer`, `WindowsRegistry`) now live in `com.botmaker.shared.emulator`. Kept for history.

**Done** (Phase 3, Slice A — SDK side; Studio picker is Slice B, deferred)
- **Cleared the old ADB stack** — deleted `internal/emulator/*` (ddmlib-based), `internal/inspector/RegistryInspector`,
  `internal/interaction/*`, and the `com.android.tools.ddms:ddmlib` dependency + its Google Maven repo. Rebuilt from scratch.
- **Transport = dadb** (`dev.mobile:dadb:1.2.9`, pure-JVM ADB — no `adb.exe`/server to ship). Pulls kotlin-stdlib
  transitively into generated bots — accepted cost of shipping no adb binary. dadb owns the RSA auth key
  (`~/.android/adbkey`), so there's no key lifecycle to manage here. Note: the Kotlin package is `dadb.*`, not the
  `dev.mobile` groupId.
- **`internal/emulator/AdbDevice`** — one live connection: `screencap()` (binary-safe via `exec:screencap -p`, decoded
  with ImageIO), `tap`/`swipe`/`key`/`text`/`startApp`/`getProp`/`shell`, `isConnected`.
- **Discovery** — `EmulatorPlatform` interface + `EmulatorInstance` record; `BlueStacksPlatform` (parses
  `bluestacks.conf` `bst.instance.<n>.status.adb_port`) and `LdPlayerPlatform` (parses `leidian<i>.config`; ADB port
  = 5555 + 2·i) discover for real; MEmu/MuMu/Gameloop scaffolded (return empty). `WindowsRegistry` (`reg query`)
  replaces the deleted BlueStacks-specific inspector. `Platforms.discoverAll()` aggregates. Windows-first; empty
  elsewhere; never throws. Parsers are pure + unit-tested.
- **`api.emulator.Emulator implements CaptureSource`** — the crux: `origin()` is `(0,0)` so a match's coords are
  already emulator pixels, and `capture()` = `screencap`, so `ImageFinder`/`Pixel`/`Text` work on it unchanged.
  Plus native verbs (`tap`/`swipe`/`back`/`home`/`text`/`key`/`startApp`), `use()` = `Source.set(this)`.
  `api.emulator.Emulators` = static discovery (`list`/`first`/`named`/`connect`).
- **Click-routing seam** — `CaptureSource.click(Point)` (default `Mouse.click`; `region(...)` delegates to parent);
  `ImageClicker` now dispatches every click through `source.click(...)` instead of `Mouse.click(...)`, so an emulator
  source taps via ADB. `ImageClickerRoutingTest` pins it (real OpenCV match → click recorded on the source, incl.
  region delegation). SDK suite green (90 tests).

**Deferred / next**
- **Slice B (Studio):** register `Emulators` facade in `palette/SdkApi`, add a `CONNECT_EMULATOR` block, a Studio-side
  `EmulatorInstanceScanner` (reads the same BlueStacks/LDPlayer configs), and an `EmulatorArgPicker` + `PickerContext`
  /`PickerRegistry` wiring — mirrors the Steam/Epic game picker.
- **Live smoke test** on real BlueStacks/LDPlayer (couldn't run here — no emulator installed): verify `screencap`
  decode, `input tap`, and per-instance ADB port discovery. BlueStacks needs ADB enabled in its settings.
- **Native-window capture backend** behind the same `CaptureSource` as a throughput optimization (a full-frame PNG
  per `screencap` is the ceiling). MEmu/MuMu/Gameloop discovery parsers.

## 2026-07-18 — Epic Games launch (`Game.launchEpic`)

**Done**
- **`Game.launchEpic(String appName)` + `launchEpicIfNotRunning(appName, source)`** — mirror the Steam pair.
  Opens `com.epicgames.launcher://apps/<appName>?action=launch&silent=true` through the scheme-agnostic
  `UriLauncher` (no `UriLauncher` change needed). `appName` is the Epic manifest `AppName` launch token
  (Studio's game picker fills it in), not the store title. Unlike Steam there's no CLI fallback, so a genuine
  failure (launcher not installed / no protocol handler) throws. Empty-input validation mirrors `launchSteam`;
  `GameTest.launchEpicRejectsEmptyAppId` pins it.

**Deferred / next**
- GOG / other stores follow the same shape when wanted (new `launch<Store>` + a `GameLibraryProvider` in Studio).

## 2026-07-18 — `Text` OCR facade (`api.vision.Text`)

**Done**
- **New `api.vision.Text` facade — on-screen text recognition, shaped exactly like `Pixel`.** Per-call
  `CaptureSource`, region via `source.region(...)`, results in **absolute** screen coords, parked in
  `VisionContext`, no-source overloads use `Source.current()`. The heavy lifting lives in
  `botmaker-shared`'s new `com.botmaker.shared.ocr.OcrEngine` (OpenCV preprocessing + Tesseract), so Studio
  can reuse OCR later without depending on the SDK. Surface:
  - `read(source[, opts])` — all recognized text as one string.
  - `find(needle, source)` (case-insensitive substring), `findExact`, `findMatching` (regex) — booleans that
    store the hit in `VisionContext.getLastTextMatch()`.
  - `findAll(needle, source)` / `readAll(source)` — counts, list in `VisionContext.getLastTextMatchList()`.
  - `waitFor` / `waitForGone` — poll loops identical to `Pixel`'s. Every call accepts an `OcrOptions` overload
    (languages, PSM, upscale, binarize, char whitelist). Default options read whole **lines** so multi-word
    phrases (`"Game Over"`) match by substring.
- **`api.vision.TextMatch`** — result type mirroring `ColorMatch`/`MatchResult` (package-private ctor,
  `notFound()` sentinel, `null` accessors when not found): `getText`, `getBounds` (absolute `Rect`),
  `getCenter`, `getConfidence`.
- **`api.vision.VisionContext`** — added a **separate** thread-local text slot (`lastTextMatch` /
  `lastTextMatchList` + getters/`ifLastTextMatch`/`clearLastTextMatch`), not shared with the template or
  colour slots, since bots interleave all three.
- **Bridge** maps shared `TextResult` (source-local box) → `TextMatch` (absolute via `source.origin()`),
  exactly like `Pixel.map(...)`. A genuine native-load failure surfaces as an `Error` (not caught), matching
  the rest of the vision layer.
- **Tests** — `TextTest` drives `Text` against a fixed-origin `CaptureSource` stub (rendered text) and
  asserts recognition, `VisionContext` storage, and absolute-coordinate mapping.

**Deferred / next**
- **Studio palette** — register `Text` in `palette/SdkApi.FACADE_CLASSES` and add palette blocks (a new
  facade needs registering; its methods are then auto-discovered). Not started.

---

## 2026-07-16 — `Game.kill` + name-based `Activity` control

**Done**
- **`Game.kill(name)` / `Game.isRunning(name)`** — cross-platform process control by executable name
  (Windows `taskkill`/`tasklist`, Linux/mac `pkill`/`pgrep`), best-effort and never throwing on "no such
  process". **Why:** the Firestone restart routine does `Process, Close, Firestone.exe` → relaunch, which the
  launch-only `Game` couldn't reproduce. Complements the existing window-based `isRunning(CaptureSource)`.
- **`Activity` self-registers by name; static `Activity.disable(name)`/`enable(name)`/`setEnabled(name,bool)`.**
  The constructor registers `this` in a static name→instance map, so any code can toggle an activity by name
  without a reference (one activity disabling another, or `GoHome`/`Startup` toggling one). Unknown name →
  stderr warning + no-op (never crashes a running bot). Instance `active()/setEnabled/enable/disable` unchanged.
  Package-private `clearRegistry()` for test isolation. **Why:** the Studio "disable this activity" self-call
  only worked inside an Activity and only self-targeted; the block now emits `Activity.disable("Name")` with a
  picker (see `../botmaker-studio/ROADMAP.md`).
- Tests: `GameTest` (kill/isRunning validation + no-throw on a bogus name); `ActivityTest` (static
  disable/enable by name toggles only that activity; unknown name warns + no-ops).

## 2026-07-16 — `Bot.stop()`: let the bot end cleanly

**Done**
- **New `Bot.stop()` ends the bot** — throws a **private nested** `BotStoppedException` (extends
  `RuntimeException`) which both `supervise` overloads catch *before* the `BotStuckException`/`RuntimeException`
  catches, log `[Bot] Stopped by request.` and **return** (not recover). The 3-arg cold-start try also honours
  it (a `stop()` during Startup/GoHome ends the bot before the loop). **Why:** `supervise`'s `while (true)` had
  no exit — once a bot disabled every activity, the loop spun forever with nothing to do ("the bot can't end").
  Exception (not a boolean body return) so `stop()` unwinds cleanly from arbitrarily deep in an activity's
  `run()`; nested-private so the only public surface is `Bot.stop()` (users never see/throw the exception),
  mirroring how `BotStuckException` is caught internally. `Activity.execute()` only catches `BotStuckException`,
  so the stop propagates through it untouched.
- Studio pairs this with an auto-`Bot.stop()` in the generated `GameLoop` (registry non-empty + no active
  activity) and a "Stop This Bot" palette block — see `../botmaker-studio/ROADMAP.md`.
- Tests: `BotTest` — `stop()` breaks the loop and `supervise` returns without recovering; a `stop()` during
  cold start ends the bot before the first body pass.

## 2026-07-16 — Cold-start launch sequence + runtime activity enable/disable + launch diagnostics

**Done**
- **`Bot.supervise(body, goHome, startGame)` now runs the start-up sequence once at launch** — `startGame()`
  then `goHome()` — *before* the first loop pass, reusing one shared `recovery` runnable. A cold-start failure
  routes through that same recovery instead of aborting the bot. **Why:** the 3-arg supervisor only ran
  GoHome/Startup *after* the loop threw, so "launch the game in Startup" never fired on a normal run (the
  reported bug: prints in Startup were silent while the activity `run()` printed fine). The 2-arg
  `supervise(body, recovery)` is unchanged.
- **`Activity` gained a runtime enable override** — nullable `enabledOverride` plus `setEnabled(boolean)`,
  `enable()`, `disable()`, and `active()` (the *effective* state: override if set, else the configured
  `isEnabled()`). The macro loop should consult `active()`, so a mid-run `disable()` actually stops the
  activity next pass (the reported "can't turn an activity off → GameLoop runs forever"). `isEnabled()`'s
  javadoc reworded to "configured default"; it stays the `Activities.<FLAG>` wiring.
- **`Game.launch`/`launchSteam` now log the exact command/URI they invoke** (`[Game] launch: …`,
  `[Game] launchSteam <id> → <uri>` + opener result) so a silent "nothing happened" launch becomes
  diagnosable in the Studio console. Behavior is otherwise identical.
- Tests: `BotTest` now asserts cold-start order (SHB / SHBHS / SHSB); new `ActivityTest` covers
  `active()`/`setEnabled`/`enable`/`disable`.
- **Release note:** generated bots pinned to a *released* SDK don't get cold-start or `active()` until this
  ships; the Studio GameLoop template emits `activity.active()`, which needs this SDK. Local
  `0.0.0-SNAPSHOT` dev runs pick it up immediately. Cut with `../release.sh` (shared unchanged → sdk → studio).

---

## 2026-07-15 — `Activity` names itself (no-arg constructor)

**Done**
- **`api/bot/Activity` gained `protected Activity()`**, defaulting `name` to `getClass().getSimpleName()`.
  Additive — `Activity(String)` stays for a name that shouldn't track the class name, and `name` stays final.
- **Why:** the only constructor was `Activity(String name)`, so every Studio-generated activity subclass had to
  carry a `public Mining() { super("Mining"); }` that restated the class name and asked the bot author for
  nothing. The Studio now generates a stub with `run()` and `isEnabled()` and no constructor at all; the
  generated `ActivityRegistry`'s `new Mining()` binds this inherited ctor.
- **Release note:** generated bots pinned to a *released* SDK can't use the constructor-less stub until this
  ships. Local `0.0.0-SNAPSHOT` dev runs pick it up immediately. Cut with `../release.sh` when ready.

---

## 2026-07-15 — Pixel colour detection (new `Pixel` facade)

**Done**
- **`api/vision/Pixel`** — the colour counterpart to `ImageFinder`, following the same conventions
  (`CaptureSource` per call, region-as-`source.region(...)`, absolute coords via `source.origin()`, results
  parked in `VisionContext`, no-source overloads use `Source.current()`). Surface: `colorAt`, `matchesAt`,
  `distance`, `find`, `findAll`, `findInRange`, `coverage`, `waitFor`, `waitForGone`.
- **Two precisions are separate knobs, deliberately.** `tolerance` is *colour* precision only — a **CIELAB
  ΔE76** distance (constants `EXACT`/`TIGHT`/`DEFAULT_TOLERANCE`/`LOOSE` = 0/5/12/25). *Location* precision is
  the searched region plus `minPixels`, the smallest connected blob that counts (kills stray anti-aliased
  pixels). Coupling the two into one knob is what makes Studio's old magic wand unusable; don't repeat it.
- **`api/vision/ColorMatch`** — mirrors `MatchResult` (package-private ctors, `notFound()` sentinel, null
  accessors when not found). Exposes `getCenter()` as the **centroid**, not the bbox centre — an L-shaped
  blob's bbox centre can lie outside the blob.
- **`internal/opencv/ColorMatcher`** + **`RawColorMatch`** (OpenCV-free, crosses internal/api like `RawMatch`).
  Pipeline: BGR → **float** Lab → per-pixel ΔE → threshold → `connectedComponentsWithStats` → `minPixels`
  filter → largest-first. The float conversion matters: converting an 8-bit image via `COLOR_BGR2Lab` gives L
  rescaled to 0..255 and a/b offset by 128, so the distances would not be ΔE.
- **`VisionContext`** gains `getLastColorMatch` / `getLastColorMatchList` / `lastColorMatchFound` /
  `clearLastColorMatch` / `ifLastColorMatch`. Colour results use their own thread-locals rather than sharing
  the template slots — bots interleave the two and would otherwise clobber each other.
- Studio: `SdkApi.FACADE_CLASSES` gains `Pixel` (a new *facade* needs registering; new *methods* on an
  existing facade do not — ClassGraph discovers those at runtime).

---

## 2026-07-14 — Bot lifecycle: supervisor + watchdog + Activity (new `api.bot`)

**Done**
- **New package `com.botmaker.sdk.api.bot`** with the runtime primitives a game bot needs:
  - **`BotStuckException`** (first custom exception in `api.*`) — unchecked; thrown by the watchdog, caught
    by the supervisor.
  - **`Watchdog`** — stuck detector. Piggybacks on existing `api.observe` match telemetry: while enabled it
    installs a `BotObserver` that counts consecutive identical match *signatures* (`templateId + coarse
    location`, or `"miss"`). The observer only counts (never throws — `ImageFinder.findInternal` swallows
    `Exception`); the throw is deterministic at `checkpoint()` once `repeats >= ClickConfig.MAX_RETRY_ATTEMPTS`.
    `progress()`/`reset()` clear the per-thread counter.
  - **`Bot.supervise(body, recovery)`** / `supervise(body, goHome, startGame)` — the outer restart loop:
    runs `body` forever, catches `BotStuckException`/`RuntimeException`, resets the watchdog, runs recovery.
  - **`Activity`** — abstract base a bot author subclasses per game task: `isEnabled()`/`run()` abstract,
    `before()`/`after()`/`onStuck()` overridable no-ops, `final execute()` orchestrates them. Studio
    generates one subclass per activity and a registry of instances.
- **`ClickConfig.MAX_RETRY_ATTEMPTS`** is now genuinely live (the watchdog's threshold); javadoc updated.
- Tests: `api/bot/WatchdogTest` (counter → checkpoint throws / resets) and `api/bot/BotTest` (supervisor
  recovery ordering) — standalone, no OpenCV/screen.
- Studio mirror: added `Bot`, `Watchdog` to `palette/SdkApi.FACADE_CLASSES` (`Activity` is a base class, not
  a static facade, so it's intentionally not listed).

## 2026-07-14 — Transparent-background templates: alpha-as-mask matching

**Done**
- **`ImageTemplate.getMat()` loads with `Imgcodecs.IMREAD_UNCHANGED`** so a transparent PNG keeps its alpha
  channel (4-channel BGRA); opaque PNGs still load as 3-channel BGR.
- **`OpencvManager` uses the alpha channel as a match mask.** New `extractAlphaMask` + `runMatch` helpers:
  when a template carries alpha, transparent pixels are ignored via `TM_CCORR_NORMED` with a mask (the
  reliably mask-supporting normed method); opaque templates keep `TM_CCOEFF_NORMED`. Threaded through
  `matchScaled`, `findMultipleMatches`, and `scoreAround`. This makes Studio's new "Capture object"
  (transparent-background) templates actually match regardless of the scene behind the object.
  Note: masked scores use CCORR_NORMED, so the 0.8 default threshold reads slightly differently for
  transparent templates. New `MaskedMatchTest` covers the path.

---

## 2026-07-11 — Game window-detection takes `CaptureSource`; add `ImageFinder.findAnyCompare`/`findAllCompare`

**Done**
- **`Game` window-detection now uses `CaptureSource`, not a bare window-title `String`** (so the Studio
  offers the visual capture-source picker instead of a free-text field, matching the vision API):
  `isRunning(CaptureSource)`, `waitForLaunch(CaptureSource, long)`,
  `launchIfNotRunning(String executablePath, CaptureSource, String... args)`,
  `launchSteamIfNotRunning(String appId, CaptureSource)`,
  `launchAndWait(String executablePath, CaptureSource, long timeout, String... args)`. The executable path
  and Steam appId are now the **first** parameter. Still window-based (no process detection yet).
- **Capture-layer presence**: new `CaptureSource.isPresent()` (default `true`; desktop/monitor are always
  present). New lazy `NamedWindow` source — `CaptureSource.window(title)` now returns it instead of eagerly
  resolving + falling back to `desktop()`. It re-resolves `Window.find(title)` on every use, so a
  window source survives the window not existing yet (before launch) and reports `isPresent()` correctly;
  `Game`'s detection delegates to it. Also makes ordinary vision matching more robust (re-binds a moved/
  reopened window per frame). `GameRunningTest` updated to drive `CaptureSource.window(...)`.
- **New compare finders** on `ImageFinder`: `findAnyCompare(good, bad[, source][, margin])` → `boolean`
  (first good template, in order, that beats every bad by the margin) and
  `findAllCompare(...)` → `int` (every winning good location; stored in `VisionContext` last-match-list).
  Mirror the existing `findCompare` overload set; new private `compareAny`/`compareAll` + shared
  `beatsAllBads` helper (which `compare` was refactored to reuse). `ImageClicker.clickCompare*` untouched.

## 2026-07-11 — Stamp the jar manifest with a build identifier

**Done**
- `pom.xml` now runs `maven-jar-plugin` with `addDefaultImplementationEntries` + a `Build-Time`
  manifest entry (`${maven.build.timestamp}`, ISO-8601). The pom `version` is cosmetic (`0.0.0-SNAPSHOT`
  locally; JitPack overrides it with the tag), so `Build-Time` is the signal that distinguishes local
  rebuilds that all reuse the same `botmaker-sdk-0.0.0-SNAPSHOT.jar` file name. Studio reads it at project
  open to report which SDK build the editor indexed — closes the "no way to tell which SDK a bot loaded" gap
  behind the stale-editor-cache bug fixed in the Studio ROADMAP (same date).

## 2026-07-11 — Fix `steam://` launch opening a blank browser page

**Done**
- `UriLauncher.open` no longer routes custom protocol schemes through `Desktop.browse`. On Windows
  `Desktop.browse("steam://…")` handed the URI to the default *browser* (blank page) instead of Steam. Now
  only `http`/`https`/`file` URLs use `Desktop.browse`; everything else goes straight to the OS protocol
  handler. The Windows native opener switched from `rundll32 url.dll,FileProtocolHandler` to `explorer.exe`
  (ShellExecute — the reliable way to invoke a registered protocol handler). `Game.launchSteam`'s CLI
  fallback is unchanged.

## 2026-07-10 — Compare API trim, click Any/All Compare, Game running-detection, per-template resolution

**Done**
- **Trimmed the Compare surface.** Removed every `clickCompare`/`findCompare` overload that took a solo
  `ImageTemplate` (`ImageClicker`, `ImageFinder`); the `ImageTemplateGroup` overloads remain the single
  Compare shape. This is an intentional breaking API change (no bot consumes the SDK yet).
- **New `ImageClicker.clickAnyCompare` / `clickAllCompare`** over `ImageTemplateGroup` (with
  `CaptureSource` + `margin` overloads). `clickAnyCompare` clicks the first good in group order that beats
  the bad set; `clickAllCompare` clicks every winning location and returns the count. Backed by new private
  `compareAnyInternal` / `compareAllInternal` (+ shared `beatsAll` neighbour-scoring helper).
- **`Game` running-detection & wait** (window-title based, via `NativeControllerFactory.get()
  .getAllWindows()`): `isRunning(title)`, `waitForLaunch(title, timeoutMs)` (~250ms poll),
  `launchIfNotRunning(...)`, `launchSteamIfNotRunning(...)`, `launchAndWait(...)`. Avoids relaunching an
  already-running game and lets a bot block until the game window appears. `launch`/`launchSteam` unchanged.
- **Per-template capture resolution.** Resolution-independent matching now prefers each template's own
  authored resolution over the project-wide `ProjectDefaults.defaultResolution()`. `ImageTemplate
  .captureResolution()` lazily reads `captureWidth`/`captureHeight` from a `<name>.json` sidecar (written by
  Studio; best-effort, null when absent). Threaded through `ResolutionScaler.primaryScale(live, authored)`
  and new authored-aware overloads of `OpencvManager.findBest`/`findBestMatch`/`findMultipleMatches`;
  templates without a sidecar keep the previous project-wide behaviour.

## 2026-07-10 — Verify `Game.launchSteam`

**Done**
- Reviewed `api/launch/Game.launchSteam(int)` → delegates to `launchSteam(String)`, which opens
  `steam://rungameid/<id>` via `UriLauncher` (Desktop.browse → `xdg-open`/`open`/`rundll32` fallback) and
  then falls back to the `steam -applaunch <id>` CLI. URI + fallback are correct; kept the numeric overload
  as a documented convenience (no signature change).
- Added `api/launch/GameTest` pinning the reject-empty-input contract for `launch` and `launchSteam`
  (String + numeric overload) — deliberately does not perform a real launch (no process/Steam spawned in CI).

## 2026-07-09 — API cleanup: global Source, concrete capture sources, Mouse ergonomics, resolution-independent matching

**Done**
- **Global ambient capture source.** New `api.capture.Source` holds a mutable global `current()`
  source; `Source.set(CaptureSource)` overrides it until changed. Every no-source overload in
  `ImageFinder`/`ImageClicker`/`ImageWaiter` now resolves through `Source.current()` instead of the
  inlined `CaptureSource.desktop()`. `current()` initialises lazily to the **project default source**
  (`internal.config.ProjectDefaults`, read from the classpath resource `/botmaker-project.properties`
  that Studio bakes into a bot), falling back to the whole `Desktop` when unset.
- **Concrete capture-source hierarchy.** Added `api.capture.Desktop` and `api.capture.Monitor`
  implementing `CaptureSource` (replacing the anonymous inner classes `Screen.asSource()` /
  `Screen.monitorSource(int)`, now removed). `CaptureSource.desktop()`/`monitor(int)` construct these;
  `Screen` remains the low-level static desktop-capture utility.
- **Mouse ergonomics.** Added `down(MouseButton, Point)` (move-then-press), `drag(Point, Point, long
  durationMs)` (timed interpolated drag), `move(int, int)` (merged with `move(Point)`; `moveTo(int,int)`
  removed), and `scrollUp(int)`/`scrollDown(int)` helpers with a clearer `scroll(int notches)` sign doc.
- **Vision facade aligned.** Removed `find(ImageTemplateGroup ...)` overloads (find is single-template);
  added `findAny(ImageTemplateGroup ...)` and `findAll(ImageTemplateGroup ...)`; removed
  `findBest(ImageTemplate ...)` (redundant — `find` already returns the best single-template match),
  keeping `findBest(ImageTemplateGroup ...)`. `ImageClicker.clickBest(ImageTemplate)` now delegates to
  `find`.
- **Resolution-independent template matching.** `internal.opencv.ResolutionScaler` derives a primary
  scale = `liveCaptureSize / projectDefaultResolution` (from `ProjectDefaults.defaultResolution()`;
  `1.0` when unset or implausible). `OpencvManager` resizes the template by that scale before matching:
  `findBest` (single scale for near-miss telemetry), `findBestMatch` (primary scale, then a small
  fallback pyramid ±10–20% **only on a threshold miss**, early-out on a hit), and `findMultipleMatches`
  (primary scale). Pre-existing pixel-exact behaviour is preserved when no default resolution is set.

**Deferred / next**
- **Studio side of resolution independence:** UI to set the project default capture resolution and to
  write `capture.width`/`capture.height` (+ `capture.source`) into `/botmaker-project.properties` of a
  generated bot. Until Studio writes it, the SDK falls back to native scale (no behaviour change).

## 2026-07-08 — Report best score on a template-match miss + SNAPSHOT shared pin

**Done**
- **Confidence is no longer always `0` on a miss.** `OpencvManager` split into `findBest(...)` (returns the top
  `TM_CCOEFF_NORMED` peak regardless of threshold; `null` only when the template can't fit) and
  `findBestMatch(...,threshold)` (unchanged gate, delegates to `findBest`). `ImageFinder.find` emits
  `MatchResult.miss(bestScore)` on a below-threshold miss so the telemetry Match carries the real near-miss
  confidence (e.g. `0.77`) — explaining "detected half the time" as a score straddling the threshold. The public
  find contract is unchanged (`isFound()` still `false`, click points still `null`); no telemetry wire change.
- **`botmaker.shared.version` committed value is now `0.0.0-SNAPSHOT`** (was a pinned tag). The real shared tag
  is resolved and injected at build time by `jitpack.yml` (`-Dbotmaker.shared.version=<newest v* tag>`); the
  committed pom is never edited by `release.sh`.

**Deferred / next**
- Consider surfacing the near-miss best score on the public API (today it's telemetry-only via `MatchResult.miss`).

## 2026-07-08 — CaptureSource redesign: three kinds, region-as-modifier, full method coverage

**Done**
- **`CaptureSource` is now exactly one of three, on one class:** `CaptureSource.desktop()` (whole virtual
  desktop, replaces `screen()`), `CaptureSource.monitor(int)` (a single screen, replaces `Screen.at(int)` as
  the public factory), `CaptureSource.window(String)`. `Screen.at` → internal `Screen.monitorSource(int)`
  backing `monitor(int)`. **Breaking** (sanctioned — early dev): callers of `CaptureSource.screen()` /
  `Screen.at(i)` move to `desktop()` / `monitor(i)`.
- **Region is a modifier on a source, not a separate parameter:** `source.region(Rect)` / `region(x,y,w,h)`
  returns a sub-source that actually **crops** `capture()` to that rect (in the source's own pixel space) and
  shifts `origin()` — so it both restricts the search area (fixes the old offset-only `region` that never
  cropped) and keeps absolute click coords. Regions compose.
- **Vision facades collapsed to `(template, CaptureSource[, double confidence])`.** Dropped every bare `Rect
  region` overload across `ImageFinder`/`ImageClicker`/`ImageWaiter`; each op now has a whole-desktop default
  + a `CaptureSource` form + optional trailing confidence. Uniform source coverage by construction — notably
  **`ImageClicker.clickCompare` gained CaptureSource overloads** (previously zero, though `findCompare` had
  them), plus the missing `ImageWaiter`/`findAny` forms.
- **`Mouse.click(CaptureSource src, int x, int y)`** — plain click at `src.origin() + (x,y)`: a fixed point
  inside a window/monitor/region, monitor-independent.
- **Observability preserved without a region param:** added `CaptureSource.base()` / `subRegion()` hooks;
  `ImageFinder` emits `MatchEvent(Surface.of(source.base()), source.subRegion(), result)` so overlays still
  know the window/screen + searched sub-rect.
- Supersedes the prior additive-overload entry below (which piled Rect+source overloads on); those Rect
  overloads are now gone in favour of `source.region(...)`.

**Deferred / next**
- Studio-side visual rubber-band region selection (interim: numeric x/y/w/h entry in the capture chooser).

## 2026-07-08 — Full CaptureSource overload coverage + `CaptureSource.window`

**Done**
- **`CaptureSource.window(String titleSubstring)`** — new static factory returning the first window whose
  title contains the substring as a `CaptureSource`, or the whole `screen()` if none matches (unwraps the
  `Window.find` `Optional` in one call). Studio now emits this inline instead of a generated `BotConfig`
  helper (the `BotConfig.java` sidecar was dropped Studio-side).
- **`ImageFinder` — CaptureSource-targeted overloads for the whole family**: `findAll`, `findAny`, group
  `find`, `findBest` (single + group), `findCompare` (single/varargs/group), `exists`/`notExists`/`existsAny`
  /`existsAll` (single + group), and the `if`/`while`/`untilExists*` lambda control-flow (single + group).
  Previously only `find`/`findAll` accepted a source. All additive, routed through the existing source-aware
  cores (`find(t,source,region,conf)`, `findAll(...)`, `compare(...)`), so coordinates stay absolute.
- **`ImageClicker` / `ImageWaiter`** — matching source overloads for `click`/`clickBest`/`clickAny`/`clickAll`
  and `waitFor`/`waitUntilGone`/`waitAndClick`, so a targeted window/monitor can drive click + wait blocks too.
- No existing public signature changed. Released bots need this SDK version to use the new source overloads;
  Studio's capture-source picker now attaches to every `CaptureSource`/`Window` parameter across these.

## 2026-07-08 — Telemetry carries the bot source line

**Done**
- `internal/observe/IpcObserver` now stamps each `Match`/`Click` telemetry event with the bot's source line
  (`botLine()` walks the current stack for the first non-`com.botmaker.*`/non-JDK frame — the user's bot
  class). Uses the shared wire v2 field (see `../botmaker-shared/ROADMAP.md`). Enables the Studio's
  running-block highlight on a plain run. Additive; a bot with no Studio attached still registers no observer.

## 2026-07-07 — Per-monitor CaptureSource (`Screen.at`)

**Done**
- **`Screen.at(int index)`** — a new public `CaptureSource` for a single monitor (0-based index into the OS
  screen-device list), so a bot can match against just one screen on a multi-monitor desktop instead of the
  whole virtual desktop (`CaptureSource.screen()` / `Screen.asSource()`). `origin()` is the monitor's
  top-left in virtual-screen space, so in-image matches convert to absolute clickable coordinates as usual.
- **`internal/capture/ScreenCapture`** gained `monitorBounds(int)` (AWT device bounds, falls back to the
  virtual desktop for an out-of-range index) and `captureMonitor(int)` (crops the single `captureDesktop()`
  grab to those bounds — one backend selection, no second capture path).
- Consumed by the Studio's generated `BotConfig.screen(index)` helper (capture-source picker → block code).
  Additive only; no existing public signature changed. Released bots need this SDK version for a
  specific-screen block; local dev picks it up via `./dev-install.sh`.

## 2026-07-07 — Observer SPI + Studio telemetry bridge

**Done**
- **New public, Studio-agnostic observability SPI** (`com.botmaker.sdk.api.observe`): `BotObserver`
  (default no-op `onMatch`/`onClick`), event records `MatchEvent`/`ClickEvent`, a `Surface` target
  (window title+bounds, or screen; `Surface.of(CaptureSource)` resolves it), and the `Bots` registry
  (copy-on-write `add/removeObserver`, `hasObservers`, `fireMatch`/`fireClick`). A first-class feature
  usable standalone — log actions, assert on them in tests, drive custom tooling. `Bots` is the one bit
  of static facade state (a deliberate, documented exception to the stateless-dispatcher style).
- **Emit sites** in the vision/interaction facades, each guarded by `Bots.hasObservers()` so a normal run
  builds/pays nothing: `ImageFinder.find(...)` (found + not-found), private `compare(...)`, `findAll(...)`
  (one event per match, or a not-found), and `ImageClicker.click`/`clickResult`/`clickAll` (left-click).
- **Internal, env-gated Studio bridge** `internal/observe/IpcObserver`: translates SDK events →
  `com.botmaker.shared.ipc.TelemetryEvent` and ships them via `TelemetryClient.fromEnvironment()`. It
  self-installs only when `BM_IPC_PORT` is set (Studio-launched). `Bots` loads it *by name* in a static
  block, so the public API keeps zero compile-time dependency on the bridge/socket — a bot never needs
  the Studio, and outside it no observer registers and no socket opens.
- Tests: `api/observe/BotsTest` (registration, fan-out, error isolation, `Surface`). Real end-to-end
  (find→click overlays) is validated with the Studio's stub bot.
- **`dev-install.sh` now routes a local SDK build to the local `botmaker-shared` build.** It rewrites
  `botmaker.shared.version` → `0.0.0-SNAPSHOT` in the temporary pom (restored on exit), alongside the
  existing groupId/version rewrites. Without this, once a release pins the property to a real tag (e.g.
  `v0.0.2`) a `local-SNAPSHOT` SDK build silently pulled shared from JitPack, so local shared changes were
  ignored. Studio also now auto-lists the installed `local-SNAPSHOT` in its version dropdown (see Studio
  ROADMAP), so there's nothing to type.

**Deferred / next**
- Optional raw `Mouse.click` (bare point, no template context) click events — left out for now.
- The SDK pom still pins `botmaker.shared.version` to the released tag; the umbrella `release.sh` bumps it
  to the new shared tag that carries the `ipc` package when this ships. For local dev build with
  `-Dbotmaker.shared.version=0.0.0-SNAPSHOT` after `botmaker-shared/dev-install.sh`.

## 2026-07-06 — Group/`Any`/`All` variants for the loop & existence helpers

**Done**
- **Lambda control-flow over an `ImageTemplateGroup`** (`ImageFinder`) — `whileExistsAny`/`ifExistsAny`
  hand the action the first visible match (`Consumer<MatchResult>`, first-match via `find(group)`);
  `whileExistsAll`/`ifExistsAll`/`untilExistsAny`/`untilExistsAll` take a `Runnable` ("all present" /
  "waiting" has no single meaningful match, mirroring `untilExists`). All are one-capture-per-check
  like the single-template originals.
- **Group/`All` existence booleans** — `exists(ImageTemplateGroup)` (any, first-match),
  `existsAll(ImageTemplate...)` and `existsAll(ImageTemplateGroup)` (every one visible; empty input is
  false), `notExists(ImageTemplateGroup)`. Complements the pre-existing `existsAny(...)`.
- **`findCompare(good, bad)` Javadoc** now documents `@param good`/`@param bad` (Studio surfaces these
  as argument labels + the "learn about it" description).
- All additive — no existing public signature changed. Tests: `ImageFinderGroupTest` (headless-safe
  `existsAll()` empty guard; screen-dependent paths stay in the manual `Main` harness).

**Deferred / next**
- `ImageWaiter.waitForAny/waitForAll/waitUntilGone` group overloads (nice-to-have; `ImageClicker`
  already covers group clicking).

## 2026-07-05 — Multi-template vision: `ImageTemplateGroup`, best-match, compare

**Done**
- **New value type `ImageTemplateGroup`** (`api.vision`) — immutable, non-empty, ordered wrapper
  around `List<ImageTemplate>` with `of(ImageTemplate...)` / `of(List)` factories and `toArray()`.
  Serves as the first-class multi-template value (Studio detects it as a special type → dedicated
  list picker).
- **`ImageFinder.find(ImageTemplateGroup)`** (+ region/confidence) — first-match over the group
  (delegates to `findAny`; keeps cheap short-circuit). Mirror `ImageClicker.click(group)`.
- **Re-introduced `findBest`/`clickBest`** (previously deleted 2026-07-03) with clearer semantics:
  exhaustive highest-score match. Overloads for a **single `ImageTemplate`** (returns the global
  argmax vs. `find`'s first-acceptable) and for an **`ImageTemplateGroup`** (best score across all
  templates).
- **`findCompare`/`clickCompare`** — a "good" template must out-score similar "bad" variants (other
  in-game states of the same element) **at the same location** by `ClickConfig.DEFAULT_COMPARE_MARGIN`
  (0.05). Overloads: `(good, bad)`, `(good, bad...)`, `(ImageTemplateGroup good, ImageTemplateGroup bad)`
  (+ region/margin). Single-capture: one screenshot, good located via `findBestMatch`, each bad
  re-scored in a padded window at good's location via new internal `OpencvManager.scoreAround(...)`.
- **Tests:** `ImageTemplateGroupTest` (guards/immutability), `ScoreAroundTest` (synthetic
  good-vs-distractor ranking, self-contained — no fixture image needed).

**Note:** the beta status let us add these to the public `api.vision` surface freely.

## 2026-07-03 — Vision API simplification

**Done**
- **Collapsed `api.vision` from 9 classes to 3 action classes** (+ the unchanged value/config types
  `MatchResult`/`ImageTemplate`/`ClickConfig`):
  - **`ImageFinder`** — now owns single-frame lookup *and* existence: `find`/`findAll`/`findAny`, the boolean
    `exists`/`notExists`/`existsAny` (moved from `ImageMatcher`), and lambda control-flow `whileExists` /
    `ifExists` (take `Consumer<MatchResult>` — one capture per check, hands the action the live match) and
    `untilExists` (takes `Runnable`, since no match exists while the template is absent).
  - **`ImageClicker`** — trimmed to `click`/`clickAny`/`clickAll`.
  - **`ImageWaiter`** — unchanged: `waitFor`/`waitUntilGone`/`waitAndClick`.
- **Deleted** `Vision` (+ `evaluate`/`snapshot`), `ImageState` (+ `ScreenState`), `ImageMatcher`, and the
  `…then…`/long-tail variants (`clickBest`, `clickFirst`, `clickUntilSuccess`, `clickWhileVisible`, `findBest`,
  `retryUntilFound`, `clickAndThen`, `clickThenWaitFor`, `waitForGoneThenClick`, `clickOrWaitAndClick`). The
  multi-template single-capture branch (`Vision.evaluate`/`ScreenState`) is replaced by the `whileExists`-style
  lambdas; deleted `VisionEvaluateTest`.
- **Callers updated:** `capture/CaptureSource` + `capture/Window` javadocs no longer reference
  `Vision`/`ImageState`; `Main` unaffected. Studio drops `ImageMatcher`/`ImageState` from its SDK-facade list.

## 2026-07-02 — Game launch API

**Done**
- **`api.launch.Game` facade.** New public static entry point exposed as Studio blocks:
  `Game.launch(String executablePath, String... args)` starts any executable via `ProcessBuilder`
  (detached), and `Game.launchSteam(String appId)` / `launchSteam(int)` hands a Steam appId to the
  local Steam client via the cross-platform `steam://rungameid/<appId>` URL, falling back to
  `steam -applaunch <appId>`. Launching a Steam game needs no login of ours — the installed, signed-in
  Steam client owns the session; we never touch credentials.
- **`internal/launch/UriLauncher`.** Small OS URL-handler opener (Desktop.browse → `xdg-open`/`open`/
  `rundll32`), mirroring the Studio's `util.BrowserLauncher` (duplicated because the SDK can't depend
  on the Studio). Used by `launchSteam` to invoke `steam://` URLs.

**Deferred / next**
- No "wait for game window" helper yet — the vision blocks (`ImageWaiter`) already poll for on-screen
  templates, which is how a test bot detects the game is up.

## 2026-06-30 — Linux click support

**Done**
- **Linux click works.** `api.interaction.Mouse.click` now routes through
  `internal/capture/core/NativeControllerFactory.get()` instead of calling the Windows-only
  `internal/capture/Clicker` (JNA `User32`) directly. This fixes
  `UnsatisfiedLinkError: Unable to load library 'user32'` on Linux, which surfaced whenever a click
  actually fired (i.e. when `ImageFinder` found the template on screen). Windows path is unchanged
  (factory → `WindowsController` → `Clicker` → `User32 PostMessage`).
- **Multi-monitor coordinates fixed.** Added `api.capture.Screen.captureOrigin()` (the virtual-screen
  origin from `ScreenCapture.getVirtualScreenBounds()`). `ImageFinder.find/findAll` and
  `ImageState.findWhichAreVisibleDetailed` now add this origin to match coordinates, so reported
  points are **absolute** screen coordinates. Previously they were image-local, so clicks landed off
  by the virtual origin whenever a monitor was placed left/above the primary (negative origin).
  Note: `ImageFinder.find/findAll` now return absolute coords (corrected contract).
- **Cursor save/restore on Linux click (X11 only).** `LinuxController.postLeftClickScreen` reads the
  pre-click pointer via X11 `XQueryPointer` (added the binding in `linux/X11.java`) against the
  default root window — same coordinate space as `XTestFakeMotionEvent` — and warps back after the
  click. Gated on `WAYLAND_DISPLAY == null`; skipped under Wayland (see below).

**Deferred / next**
- **Click without disturbing the cursor on native Wayland.** Under native Wayland the JVM is an
  XWayland client: it can *write* the pointer (warp/click via XTest) but **cannot read** the global
  cursor position, so save/restore is impossible there (it would teleport the cursor to a stale
  constant). Current interim: on Wayland we skip restore and leave the cursor on the click target.
  Proper fix: implement input injection via the **xdg-desktop-portal RemoteDesktop** interface
  (libei / PipeWire) — this is also what raises the one-time "allow control of pointer/keyboard"
  prompt. Alternative (weaker): window-relative motionless click via `XSendEvent`, but many apps
  ignore synthetic events and it can't reach native Wayland windows. Likely belongs alongside the
  capture backends as a new injection strategy.
- **GNOME/sway capture.** Add a portal/PipeWire `CaptureBackend` and wire into
  `CaptureBackend.select()` (noted in `CLAUDE.md` › Screen capture).
