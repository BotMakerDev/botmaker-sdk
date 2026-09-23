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
import com.botmaker.sdk.plugin.editors.SdkEditors;
import com.botmaker.sdk.plugin.flow.ActivityFlowDialog;
import com.botmaker.sdk.plugin.flow.FlowValue;
import com.botmaker.sdk.plugin.pictures.CaptureTemplates;
import com.botmaker.sdk.plugin.pictures.ResourceManagerDialog;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi;
import com.botmaker.sdk.plugin.setup.ProjectSetup;
import com.botmaker.sdk.plugin.source.CaptureLabels;
import com.botmaker.sdk.plugin.source.CaptureValue;
import com.botmaker.sdk.plugin.source.SourcePicker;
import com.botmaker.sdk.plugin.types.CaptureTypes;
import com.botmaker.sdk.plugin.types.FlowTypes;
import com.botmaker.sdk.plugin.types.PictureAt;
import com.botmaker.sdk.plugin.types.SdkTypes;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The BotMaker SDK, as a Studio plugin.
 *
 * <p>This is plugin #1, and it is deliberately <b>an ordinary implementation of {@link StudioPlugin} with no
 * back door</b>: no {@code instanceof SdkPlugin} branch in the host, no package-private hook, no second
 * interface. Studio loads it off the open project's classpath like any other plugin. One implementor proves
 * little about a contract; an implementor that cannot cheat proves rather more.
 *
 * <h2>The palette</h2>
 *
 * <p>Nothing here lists it: the toolkit's {@code buildCatalog()} scans this jar for {@code @Palette}, and
 * every {@code com.botmaker.sdk.api} class carrying one is catalogued. What an older pin may be offered is
 * that catalog <b>intersected with the bot's own resolved jar</b>, which the host computes from bytecode.
 *
 * <h2>Where this class may live, and where it may not</h2>
 *
 * <p>Under {@code com.botmaker.sdk.plugin}, never {@code com.botmaker.sdk.api} or {@code internal}: a bot
 * cannot write this name down, and nothing a bot links may reach JavaFX or the toolkit
 * ({@code PluginLayersTest}).
 */
public final class SdkPlugin extends AbstractStudioPlugin {

    /** The stable identifier the host files this plugin's contributions under. */
    public static final String ID = "com.botmaker.sdk";

    /**
     * Does nothing but name the plugin, and that emptiness is load-bearing.
     *
     * <p><b>Constructing a plugin must not link an optional dependency.</b> {@code javafx-controls} is
     * {@code optional} here, so the classpath a headless host resolves this plugin onto — the CLI's
     * {@code validate} and {@code run}, the registry's CI — does not have it, and a constructor that touched a
     * JavaFX type fails there with:
     *
     * <pre>
     * ServiceConfigurationError: Provider com.botmaker.sdk.plugin.SdkPlugin could not be instantiated
     *   Caused by: NoClassDefFoundError: javafx/scene/Node
     * </pre>
     *
     * <p>{@code PluginLoader} catches that, so the symptom is an empty palette and one line on stderr. It never
     * shows locally, because an {@code optional} dependency <em>is</em> on this module's own classpath.
     * Everything JavaFX-shaped belongs behind a {@code build…} hook ({@code SdkPluginHeadlessTest}).
     */
    public SdkPlugin() {
        super(ID, "BotMaker SDK");
    }

    /**
     * The editors for this plugin's own types — a region dragged on screen instead of
     * {@code new Rect(12, 40, 300, 80)}, and the rest of {@link SdkEditors}. The host asks every plugin in
     * turn, after its own editors and before its JDK fallbacks; nothing here is privileged.
     */
    @Override
    protected List<SlotEditor> buildSlotEditors() {
        return SdkEditors.ALL;
    }

    /**
     * The fourteen types this plugin declares — what each one is, what a fresh one is, and how a person
     * edits one.
     *
     * <p>Being in this list means <em>this type is one a bot author can hold</em>: it is what "Declare Bot
     * Variable" and the Add Function dialog offer. Adding a type here makes it declarable; removing one takes
     * it out of both menus.
     *
     * <p>Ten answer a real {@code fresh()}; the capture source's is the ambient source, which keeps following
     * the project's source when that changes later. The four vision results answer {@code freshCall()}
     * instead, because their honest starting value is a <em>call the bot re-evaluates</em>: a match is
     * something the bot found a moment ago, not something anyone configures.
     *
     * <p>Not cached here: {@link AbstractStudioPlugin} does the caching, and {@code fresh()} is asked every
     * time a value is seeded so it may read this plugin's live state.
     */
    @Override
    protected List<PluginType<?>> buildTypes() {
        return SdkTypes.ALL;
    }

    /**
     * The five records a {@code Flow} is written as, the six calls a {@code CaptureSource} is written as, and
     * the chains a person writes by hand and the host only reads: {@code source.region(r)} and the three
     * {@code Precision} withers. None is a type anybody declares on its own; the host reads each back so an
     * editor is handed a value rather than a string.
     */
    @Override
    public List<ComponentType<?>> componentTypes() {
        return Stream.of(FlowTypes.ALL, CaptureTypes.ALL, SdkTypes.PRECISION_WITHERS)
                .<ComponentType<?>>flatMap(List::stream).toList();
    }

    /**
     * The three values this plugin's windows keep in step, each read-only on the canvas with a reason.
     *
     * <p>The picture constants — {@code static final ImageTemplate COLLECT = new ImageTemplate(…)} — are the
     * reason for the third. 🖼 Manage Pictures renames the file, the constant and every use of it together
     * (through the host's {@code Sources}); the canvas can only rename the one it is looking at, which leaves
     * the bot calling a name that is gone. The class says so itself, with {@code @Managed("pictures")} on the
     * file the bot holds, so it is a statement rather than an inference about shape.
     */
    @Override
    public List<ManagedValue> managedValues() {
        return List.of(
                new ManagedValue(FLOW,
                        "This is the bot's activity flow. Draw it in 🔀 Activity Flow, which keeps the"
                                + " activities, the wires and the layout in step."),
                new ManagedValue(CAPTURE,
                        "This is where the bot reads pixels from. Choose it in 🎯 Capture Source."),
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

    /**
     * Takes the project being bound.
     *
     * <p>A plugin is constructed once and then serves whatever the host binds, so which project it has can
     * only arrive this way. Nothing is read here — a project open must not pay for a window nobody has
     * looked at yet.
     */
    @Override
    public void projectOpened(StudioServices services) {
        // The flow is a value in the bot's own Java, so reading it needs the host rather than a path.
        // Two readers have no value cell to ask through — see FlowValue's own note — and this is where they
        // are given one.
        FlowValue.bind(services);
    }

    /**
     * The toolbar buttons. The host keeps the bar itself — the grouping, the order, the packing and the
     * overflow — which is why an item is contributed as data rather than as a {@code Node}.
     *
     * <p>The pilot UI is built lazily and kept, because it owns the port and the display: a second press
     * must re-show the pairing dialog rather than rebind and drop an already-paired phone. It is released in
     * {@link #projectClosing()}. Capture Templates keeps nothing — it is single-instance in its own class,
     * because what it owns is the screen rather than a resource, and the screen is gone when it closes.
     *
     * <p><b>Capture Templates is in {@link ToolbarGroup#TOOLS}</b>, whose own definition names a template
     * cutter: it is opened <em>over</em> a running target rather than beside the code.
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
                                + "to capture, and the pictures it looks for",
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
     * Writes the window the overlay is drawn over into this project's capture source.
     *
     * <p><b>This is the direction the fact travels, and it only travels this way.</b> The host tells the
     * plugin which window its own HUD is drawn over — something only the host can know — and the plugin
     * writes it into the bot's own Java as the expression {@code Sdk.captureSource()} returns. Studio holds
     * no capture source of its own; see {@code docs/refactor/28-overlay-items.md}.
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
     * <p>What the overlay adds is a target for <em>this</em> session, which makes the tool usable over a
     * window the project has never heard of, including a project that names no target at all. The override
     * is not written down. Pointing the bot at that window is the button beside this one, so a user who
     * wanted a picture does not silently get a re-pointed bot.
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
     * <p>The tag is not pre-filled: <em>which file the editor has open</em> is host state with no member on
     * the contract for it, and growing one is exactly the move the platform's stop condition exists to refuse.
     * The tag menu is on the naming dialog either way.
     */
    private void openCaptureTemplates(ActionContext context) {
        StudioServices services = context.services();
        CaptureTemplates.open(services, services.dialogs().ownerWindow().orElse(null), null);
    }

    /**
     * Opens the flow editor.
     *
     * <p>It is this plugin's window rather than a host frame with sections, because a flow's nodes, edges,
     * ports and outcomes are vocabulary of this plugin's own that the contract must never learn. Single-instance
     * is not enforced: this window owns no port and no display, so a second one is a second view of the same
     * value.
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
     * Chooses what the bot reads pixels from, and writes it into the bot's own Java — the one expression
     * {@code Sdk.captureSource()} returns.
     *
     * <p><b>The label is constant.</b> {@link #toolbarItems()} is called with no {@link StudioServices}, so a
     * label supplier here has no project to read the current source out of. Giving the plugin a services
     * field to close over would make the item's <em>label</em> depend on load order, which is worse than a
     * button that says what it opens.
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
     * Opens the project checklist. It sits at order 40, immediately <em>before</em> Capture Source, because
     * it is the window that sends a user to that one.
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
}
