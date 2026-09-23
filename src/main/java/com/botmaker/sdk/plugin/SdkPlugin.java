package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.record.RecordedValue;
import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.api.toolbar.ToolbarGroup;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.plugin.toolkit.AbstractStudioPlugin;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.authoring.PictureAt;
import com.botmaker.sdk.internal.authoring.SdkFlowValues;
import com.botmaker.sdk.internal.authoring.SdkTypes;
import com.botmaker.sdk.internal.plugin.capture.CaptureExpr;
import com.botmaker.sdk.internal.plugin.capture.CaptureLabels;
import com.botmaker.sdk.internal.plugin.capture.CaptureTemplates;
import com.botmaker.sdk.internal.plugin.capture.CaptureValue;
import com.botmaker.sdk.internal.plugin.capture.SourcePicker;
import com.botmaker.sdk.internal.plugin.editors.SdkEditors;
import com.botmaker.sdk.internal.plugin.flow.ActivityFlowDialog;
import com.botmaker.sdk.internal.plugin.flow.FlowValue;
import com.botmaker.sdk.internal.plugin.pilot.RemotePilotUi;
import com.botmaker.sdk.internal.plugin.setup.ProjectSetup;
import com.botmaker.sdk.internal.plugin.templates.ResourceManagerDialog;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The BotMaker SDK, as a Studio plugin.
 *
 * <p>This is plugin #1 and the only one that ships today, and it is deliberately <b>an ordinary
 * implementation of {@link StudioPlugin} with no back door</b>: no {@code instanceof SdkPlugin} branch in
 * the host, no package-private hook, no second interface. What the SDK gets that a third-party plugin does
 * not is a set of <em>privileges</em> — it is always loaded, it owns the primary slot editors, and Studio's
 * own pom declares the dependency — never a wider API. One implementor proves little about a contract; an
 * implementor that cannot cheat proves rather more.
 *
 * <h2>The palette</h2>
 *
 * <p>Nothing here lists it: the toolkit's {@code buildCatalog()} scans this jar for {@code @Palette}, and
 * every {@code com.botmaker.sdk.api} class carrying one is catalogued. What an older pin may be offered is
 * that catalog <b>intersected with the bot's own resolved jar</b>, which the host computes from bytecode.
 *
 * <h2>Where this class may live, and where it may not</h2>
 *
 * <p>Under {@code com.botmaker.sdk.plugin}, never {@code com.botmaker.sdk.api} — a bot cannot write this
 * name down, and nothing under {@code api} may reference a {@code com.botmaker.plugin.api} type. That
 * invariant is what makes the contract's {@code <optional>true</optional>} scope safe: the class is in the
 * jar and cannot link on a bot's classpath, exactly like an SLF4J binding, and no bot ever reaches it.
 */
public final class SdkPlugin extends AbstractStudioPlugin {

    /** The stable identifier the host files this plugin's contributions under. */
    public static final String ID = "com.botmaker.sdk";

    /**
     * Does nothing but name the plugin, and that emptiness is load-bearing.
     *
     * <p>It registered the screen picker until 2026-09-05 — {@code Editors.pickWith(new SdkScreenPicks())},
     * defended on the grounds that it costs one field write and {@code ServiceLoader} constructs this class
     * before any editor is drawn. Both halves are true and the conclusion was wrong: {@code Editors} and
     * {@code ScreenPicks} are JavaFX-typed, {@code javafx-controls} is {@code optional} here, and
     * <b>{@code optional} means the classpath a host resolves this plugin onto does not have it</b>. So a
     * host with no JavaFX could not construct this plugin at all:
     *
     * <pre>
     * ServiceConfigurationError: Provider com.botmaker.sdk.plugin.SdkPlugin could not be instantiated
     *   Caused by: NoClassDefFoundError: javafx/scene/Node
     * </pre>
     *
     * <p>{@code PluginLoader} catches that — a classpath with no loadable plugin on it is an ordinary state
     * — so the symptom is an empty palette and one line on stderr, which is the failure mode the whole
     * plugin-loading design keeps meeting. The <b>plugin registry's own gate found it</b>, on the SDK's own
     * submission, by resolving the published artifact exactly as a host does: {@code botmaker validate
     * --coordinate com.github.LiQiyeDev:botmaker-sdk:v1.1.5} reproduces it. It never showed locally because
     * an {@code optional} dependency <em>is</em> on this module's own classpath, so validating the working
     * copy resolved JavaFX and validating the artifact did not.
     *
     * <p>The rule it states, and it is the one to apply to anything a constructor is tempted to do:
     * <b>constructing a plugin must not link an optional dependency.</b> A headless host — the CLI's
     * {@code validate} and {@code run}, the registry's CI — is a legitimate host, and it is the one that
     * decides whether a plugin may be published at all. Everything JavaFX-shaped belongs behind a
     * {@code build…} hook, which is where the catalog, the value types and the parameters already were.
     */
    public SdkPlugin() {
        super(ID, "BotMaker SDK");
    }

    // SdkScreenPicks stood here as a nested class until 2026-09-22, registered process-wide through
    // Editors.pickWith. Both are deleted. The picker is com.botmaker.sdk.internal.plugin.editors's now,
    // reached by the one editor that needs it (GeometryEditors, which hands it to Editors.tuplePill as an
    // argument). pickWith held ONE static ScreenPicks for every plugin in the process, last writer wins and
    // nothing said so; passing it removes the shared state rather than relocating it.

    /**
     * The editors for this plugin's own types — a region dragged on screen instead of
     * {@code new Rect(12, 40, 300, 80)}, and the rest of {@link SdkEditors}.
     *
     * <p>They were the host's until 2026-08-27, which was the last place Studio still had to know what an SDK
     * type looked like. They are reached exactly as a third-party plugin's would be: the host asks every
     * plugin in turn, after its own editors and before its JDK fallbacks. Nothing here is privileged.
     *
     * <p>The classes behind this list touch JavaFX and the plugin widget toolkit, both
     * {@code <optional>true</optional>} in this module's pom, so they are in the jar and never linked on a
     * bot's classpath — the same arrangement that makes the contract dependency safe.
     *
     * <p><b>The screen picker was registered here</b>, from 2026-09-05 to 2026-09-22 — here rather than in
     * the constructor, for the reason that javadoc gives at length. It is registered nowhere now: the one
     * editor that picks on screen holds {@code SdkScreenPicks} itself and passes it, which keeps the
     * constructor's property (nothing JavaFX-shaped is linked until a host asks for editors) and drops the
     * process-wide static as well.
     */
    @Override
    protected List<SlotEditor> buildSlotEditors() {
        return SdkEditors.ALL;
    }

    /**
     * The fourteen types this plugin declares — what each one is, what a fresh one is, and how a person
     * edits one.
     *
     * <h2>This list answers two questions, and the second one used to be Studio's</h2>
     *
     * <p>It started as three {@code SourceSeed}s — the types the host's generic {@code new T()} cannot fill
     * on its own: an interface, a record with required components, and a type whose meaning lives in a
     * constant. It became fourteen because <b>Studio's {@code palette/BotType} used to list them</b>, as
     * fourteen {@code Class} literals with a default value beside each, and that list is what "Declare Bot
     * Variable" and the Add Function dialog offer. An editor curating one plugin's API is the back door the
     * platform exists to close — and a second plugin's types could never have joined that enum at all.
     *
     * <p>So being in this list means <em>this type is one a bot author can hold</em>. Adding a type here
     * makes it declarable; removing one takes it out of both menus.
     *
     * <h2>Eight are editable and six are not, which is the split that survived {@code SourceSeed}</h2>
     *
     * <p>{@code SdkTypes.ALL} carries both. The eight answer a real {@code fresh()} and a real editor. The
     * six — the ambient capture source, a template group and the four vision results — answer
     * {@code freshSource()} instead, because their honest starting value is a <em>call the bot
     * re-evaluates</em>: a match is something the bot found a moment ago, not something anyone configures,
     * and {@link CaptureExpr#projectDefault()} keeps following the project's source when that changes later.
     * A snapshot would silently freeze a declaration into whatever was true at project open.
     *
     * <p>Not cached here: {@link AbstractStudioPlugin} does the caching, and {@code fresh()} is asked every
     * time a value is seeded so it may read this plugin's live state.
     */
    @Override
    protected List<PluginType<?>> buildTypes() {
        return SdkTypes.ALL;
    }

    /**
     * The five records a {@code Flow} is written as, none of which is a type anybody declares — they are
     * parts of the one call that writes a flow, and the host has to read each back to hand the flow editor
     * a {@code Flow} rather than a string.
     */
    @Override
    public List<ComponentType<?>> componentTypes() {
        return SdkFlowValues.ALL;
    }

    /**
     * A picture constant — {@code static final ImageTemplate COLLECT = new ImageTemplate(…)} — is shown on the
     * canvas and changed in 🖼 Manage Pictures.
     *
     * <p>That window renames the file, the constant and every use of it together (through the host's
     * {@code Sources}); the canvas can only rename the one it is looking at, which leaves the bot calling a
     * name that is gone. The class holding them — {@code @Managed("pictures")} — is therefore read-only as a
     * whole, and says where to go.
     *
     * <p><b>The class says so itself now</b> (2026-09-20). This claimed every {@code static final} field
     * whose type was {@code ImageTemplate}, wherever it was written, and Studio inferred that a class of
     * nothing but those was managed whole. Both were guesses about shape: a bot keeping one picture beside
     * ordinary code was locked out of it, and a second class of pictures could not be told from the first.
     * The annotation is on the file this plugin ships, so it is a statement rather than an inference.
     */
    @Override
    public List<ManagedValue> managedValues() {
        return List.of(
                new ManagedValue(FLOW,
                        "This is the bot's activity flow. Draw it in ✂ Activity Flow, which keeps the"
                                + " activities, the wires and the layout in step."),
                new ManagedValue(CAPTURE,
                        "This is where the bot reads pixels from. Choose it in Project ▸ Settings."),
                new ManagedValue(PICTURES,
                        "Picture constants are managed in 🖼 Manage Pictures, which renames the picture and"
                                + " every use of it together."));
    }

    /** The {@code @Managed} id on the method holding this bot's flow. */
    public static final String FLOW = "flow";

    /** The {@code @Managed} id on the method holding this bot's capture source. */
    public static final String CAPTURE = "capture";

    /** The {@code @Managed} id on the class of picture constants this plugin's window keeps in step. */
    public static final String PICTURES = "pictures";

    // pluginSources() and shippedSource() stood here on 2026-09-20/21, shipping Sdk.java.txt and
    // Pictures.java.txt out of src/main/resources for the host to copy into a project. Both are deleted with
    // the contract method they implemented.
    //
    // Nothing about what those files ARE changed: a bot still holds this plugin's values as @Managed methods
    // in its own plugins/sdk/Sdk.java, and this plugin's windows still rewrite one returned expression at a
    // time. What changed is where a project's first copy comes from — the template it was created from,
    // which already carries one — and that the file no longer needs an install() for a bot's main to call,
    // because Bot.run installs every @Managed value it is handed.
    //
    // The one thing genuinely given up: adding this plugin to a project that has no Sdk.java brings none.
    // The answer to that is this plugin's own flow window offering to write one, which is a click and one
    // file rather than a host copying text on every bind.

    // buildValueTypes() returned SdkValueTypes.CATALOG here until 2026-09-22 -- seventeen, then eight,
    // ValueType constants each with a ValueCodec beside it. Both the registry and the codec are deleted
    // from the contract: a type's identity is its Java class, and nothing parses. buildTypes() above is
    // what replaced it, and SdkTypes is the one file that now names each type.

    // buildParameters(), parameterRows(String) and parameterEdited(ParameterEdit) stood here from
    // 2026-09-10 to 2026-09-22, over a ParameterStore held in a field set on bind. The whole surface is
    // deleted from the contract, and this was its only implementation.
    //
    // It declared one section and no rows. ParameterStore.declare -- the call that would have written a row
    // into this plugin's file -- had no caller in this module or any other, so parameterRows returned
    // whatever sat in a pre-2026-09-17 project's JSON and empty for every project created since. The
    // replacement was already in place by then: a parameter is a @Param field in the bot's own Java, and a
    // row this plugin wants for itself is a @Param field in the file this plugin ships. The host's ordinary
    // walk of the bot's sources finds it, which is why nothing here replaces the three methods.
    //
    // parameterDeclared went first, on 2026-09-17, for the same reason one step earlier.

    /**
     * Takes the project being bound.
     *
     * <p>A plugin is constructed once and then serves whatever the host binds, so which project it has can
     * only arrive this way. Nothing is read here — a project open must not pay for a window nobody has
     * looked at yet — so this is one call, exactly as {@code buildValueTypes} and the other lazy builders
     * are.
     */
    @Override
    public void projectOpened(StudioServices services) {
        // The flow is a value in the bot's own Java now, so reading it needs the host rather than a path.
        // Two readers have no value cell to ask through — see FlowValue's own note — and this is where they
        // are given one.
        FlowValue.bind(services);
    }

    /**
     * Five buttons, of which <b>Pilot</b> is the case the toolbar surface was added for.
     *
     * <p>The Remote Pilot is not an editor for a slot: it binds a port, opens a nested {@code :N} display,
     * streams frames to a phone and drives input back. It was Studio's until 2026-08-30 and it was never
     * Studio's subject — everything behind this button is about what a <em>bot</em> sees and does, which is
     * this plugin's subject. What the host keeps is the bar itself: the grouping, the order, the packing and
     * the overflow, which is exactly why an item is contributed as data rather than as a {@code Node}.
     *
     * <p>The pilot UI is built lazily and kept, because it owns the port and the display: a second press
     * must re-show the pairing dialog rather than rebind and drop an already-paired phone. It is released in
     * {@link #projectClosing()}. Capture Templates keeps nothing — it is single-instance in its own class,
     * because what it owns is the screen rather than a resource, and the screen is gone when it closes.
     *
     * <p><b>Capture Templates is in {@link ToolbarGroup#TOOLS}</b>, whose own definition names a template
     * cutter: it is opened <em>over</em> a running target rather than beside the code. It was Studio's
     * <i>Capture Templates</i> menu entry and toolbar button until 2026-08-31, and it was never Studio's
     * subject — it reads a capture target out of {@code capture.json}, grabs pixels through
     * {@code botmaker-shared}, and writes an {@code ImageTemplate} into the picture folder, all three of
     * which are this plugin's.
     */
    @Override
    public List<ToolbarItem> toolbarItems() {
        return List.of(
                ToolbarItem.of("pilot", "🎮 Pilot",
                        "Stream what the bot sees to your phone or browser — watch it, start/stop it, "
                                + "or turn on Interact to click and drag in the game yourself",
                        ToolbarGroup.RUN, 10, context -> pilot(context.services()).open()),
                ToolbarItem.of("capture-templates", "✂ Capture Templates",
                        "Draw regions over the game and save them as pictures the bot can look for — "
                                + "one at a time, several in a pass, or an object cut out of its background",
                        ToolbarGroup.TOOLS, 20, this::openCaptureTemplates),
                ToolbarItem.of("manage-templates", "🖼 Manage Pictures",
                        "Rename, retag, replace, delete, import and export the pictures the bot looks for — "
                                + "a rename carries every block that uses it",
                        ToolbarGroup.TOOLS, 30, this::openResourceManager),
                ToolbarItem.of("activity-flow", "🔀 Activity Flow",
                        "Define what this bot does, one card per activity, and wire each outcome to what "
                                + "runs next — the graph, its loop safety, and which activities are on",
                        ToolbarGroup.AUTHORING, 10, this::openActivityFlow),
                ToolbarItem.of("capture-source", "🎯 Capture Source",
                        "Choose what the bot looks at — a monitor, an application window or an emulator "
                                + "instance",
                        ToolbarGroup.PROJECT, 50, this::openCaptureSource),
                ToolbarItem.of("project-setup", "📋 Project Setup",
                        "What this project still needs before it can run — something to launch, something "
                                + "to capture, a reference resolution, and the pictures it looks for",
                        ToolbarGroup.PROJECT, 40, this::openProjectSetup),
                ToolbarItem.of("point-here", "⌖ Point bot here",
                        "Make the window the overlay is drawn over this project's capture source",
                        ToolbarGroup.OVERLAY, 10, this::pointCaptureTargetHere),
                ToolbarItem.of("picture-here", "✂ Picture of this",
                        "Cut a picture out of the window the overlay is drawn over, whatever the project's "
                                + "capture target is",
                        ToolbarGroup.OVERLAY, 20, this::capturePictureHere));
    }

    /**
     * The one parameter type of this plugin's {@code @Records} methods the host cannot fill: the picture under
     * a recorded click. Everything else a recording writes — coordinates, keys, text, durations, the capture
     * source — the host fills by type.
     */
    @Override
    public List<RecordedValue<?>> recordedValues() {
        return List.of(new PictureAt());
    }

    /**
     * Writes the window the overlay is drawn over into this project's capture target.
     *
     * <p><b>This is the direction the fact travels, and it only travels this way.</b> The host tells the
     * plugin which window its own HUD is drawn over — something only the host can know — and the plugin
     * writes it into the bot's own Java as the expression {@code Sdk.captureSource()} returns. Studio holds
     * no capture source of its own; see {@code docs/refactor/28-overlay-items.md}.
     *
     * <p>It wrote {@code capture.json} and projected onto {@code botmaker-project.properties} as well until
     * 2026-09-22. Both files are deleted, and what is left is the one write that was always the real one —
     * which is why this is now a single call where it was a three-file transaction on a worker thread.
     */
    private void pointCaptureTargetHere(ActionContext context) {
        StudioServices services = context.services();
        String title = context.overWindowTitle().orElse(null);
        if (title == null) {
            services.status("Nothing to point at — the overlay is not over a window.");
            return;
        }
        CaptureValue.point(services, CaptureSource.window(title));
        services.status("Capture source is now \"" + title + "\".");
    }

    /**
     * Cuts a picture out of the window the overlay is over, rather than out of the project's default target.
     *
     * <p>There is no screen chooser to skip — the capture tool has always read the project's own default —
     * so what the overlay adds is the opposite: a target for <em>this</em> session, which makes the tool
     * usable over a window the project has never heard of, including a project that names no target at all.
     * The override is not written down. Pointing the bot at that window is the button beside this one, so a
     * user who wanted a picture does not silently get a re-pointed bot.
     *
     * <p>{@link ActionContext#overBounds()} is deliberately unused: the capture tool re-probes and raises its
     * target at save time so a window the user has since moved is still tracked, and a rectangle captured
     * when the HUD opened would be stale exactly then.
     */
    private void capturePictureHere(ActionContext context) {
        StudioServices services = context.services();
        CaptureSource target = context.overWindowTitle()
                .map(CaptureSource::window)
                .orElse(null);
        CaptureTemplates.open(services, services.dialogs().ownerWindow().orElse(null), target, null, () -> {});
    }

    /**
     * Opens the capture tool over the project's target.
     *
     * <p>The tag is not pre-filled, and that is the one thing this lost on the way out of the host. Studio's
     * menu entry passed the open activity's tag, so pictures captured while an activity was open were filed
     * under it by default — and <em>which file the editor has open</em> is host state with no member on the
     * contract for it. Growing one is exactly the move the platform's stop condition exists to refuse, and
     * the cost is a default rather than a capability: the tag menu is on the naming dialog either way.
     */
    private void openCaptureTemplates(ActionContext context) {
        StudioServices services = context.services();
        CaptureTemplates.open(services, services.dialogs().ownerWindow().orElse(null), null);
    }

    /**
     * Opens the flow editor.
     *
     * <p>It sits in {@link ToolbarGroup#AUTHORING} at order 10, the slot Studio's own 🔀 Flow button vacated,
     * so the bar reads where it always did. It was the host's until 2026-09-11, and it is the <b>only</b> one
     * of the three windows over this plugin's project data that moved: a parameter is a
     * {@link com.botmaker.plugin.api.parameters.ParameterRow} and the host can draw one, while a flow's
     * nodes, edges,
     * ports and outcomes are vocabulary of this plugin's own that the contract must never learn.
     *
     * <p>Single-instance is not enforced: this window owns no port and no display, so a second one is a
     * second view of the same file. What it does own is the writing of {@code activities.json}, which is why
     * Studio's copy was deleted in the commit this arrived in rather than a commit later.
     */
    private void openActivityFlow(ActionContext context) {
        StudioServices services = context.services();
        new ActivityFlowDialog(services, services.dialogs().ownerWindow().orElse(null)).show();
    }

    /**
     * Opens the picture library: the other end of Capture Templates, managing the pictures that exist. Its
     * rename and delete guards rewrite the user's Java through {@link com.botmaker.plugin.api.Sources}. Not
     * single-instance: it owns no port and no display.
     */
    private void openResourceManager(ActionContext context) {
        StudioServices services = context.services();
        ResourceManagerDialog.open(services, services.dialogs().ownerWindow().orElse(null));
    }

    /**
     * Chooses what the bot reads pixels from, and writes it into the bot's own Java.
     *
     * <p><b>It was a list manager until 2026-09-22.</b> {@code CaptureTargets} kept several configured
     * targets in {@code capture.json} and marked one of them the default. That file is deleted and a project
     * has exactly <b>one</b> capture source — the expression {@code Sdk.captureSource()} returns — so what
     * is left is the pick itself, which {@link SourcePicker} already drew for that window. A list of things
     * where only one is ever used is a list nobody needed.
     *
     * <p><b>The label is constant, and it used not to be.</b> Studio's own button read "🎯 " plus the
     * current source's short name, because the editor held it in memory. It does not hold it, and
     * {@link #toolbarItems()} is called with no {@link StudioServices}, so a label supplier here has no
     * project to read one out of. Giving the plugin a services field to close over would make the item's
     * <em>label</em> depend on load order, which is worse than a button that says what it opens.
     */
    private void openCaptureSource(ActionContext context) {
        StudioServices services = context.services();
        new SourcePicker(services, services.dialogs().ownerWindow().orElse(null), false)
                .showAndWait()
                .ifPresent(selection -> {
                    if (!(selection instanceof SourcePicker.Selection.Concrete concrete)) return;
                    CaptureValue.point(services, concrete.target(), concrete.region());
                    services.status("Capture source is now "
                            + CaptureLabels.shortLabel(concrete.target()) + ".");
                });
    }

    /**
     * Opens the project checklist.
     *
     * <p>It sits at order 40, immediately <em>before</em> Capture Targets, because it is the window that sends
     * a user to that one. It was Studio's <i>Project Setup</i> dialog until 2026-08-31 and it was never
     * Studio's subject: all four of its rows read files this module owns — the launch target and the capture
     * size out of {@code botmaker-project.properties}, the capture target and the reference resolution out of
     * {@code capture.json}, the pictures out of the images folder — so the editor could only ever have
     * answered them by asking here.
     */
    private void openProjectSetup(ActionContext context) {
        StudioServices services = context.services();
        ProjectSetup.open(services, services.dialogs().ownerWindow().orElse(null));
    }

    /**
     * Releases the pilot's port and its nested display when the project it was serving is left.
     *
     * <p>This plugin instance is reused for the next project, so the field is dropped as well as closed: a
     * pilot still answering on the old port would be streaming a project nobody has open, and one whose
     * {@code resourcesDir} points at the previous project would be worse.
     */
    @Override
    public void projectClosing() {
        RemotePilotUi open = pilot;
        pilot = null;
        if (open != null) open.close();
        FlowValue.unbind();
    }

    private RemotePilotUi pilot(StudioServices services) {
        if (pilot == null) pilot = new RemotePilotUi(services);
        return pilot;
    }

    /**
     * The pilot for the project currently bound, or {@code null} until its button is first pressed.
     *
     * <p>Touched only on the JavaFX thread — a toolbar press and {@code projectClosing()} both arrive there —
     * so it needs no synchronization.
     */
    private RemotePilotUi pilot;

    // SDK_PARAMETERS -- ParameterGroup.of(DEFAULT_ID, "Parameters") -- stood here until 2026-09-22. It had
    // already lost its categories on 2026-09-17, when a @Param's category became the free text a bot's author
    // writes; what went now is the group itself, with the whole contract surface that read it. The Parameters
    // window's sections are the bot's own classes, which javac already guarantees are distinct.
}
