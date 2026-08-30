package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.ParameterGroup;
import com.botmaker.plugin.api.SlotEditor;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.ToolbarGroup;
import com.botmaker.plugin.api.ToolbarItem;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.toolkit.AbstractStudioPlugin;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;
import com.botmaker.sdk.internal.plugin.editors.SdkEditors;
import com.botmaker.sdk.internal.plugin.pilot.RemotePilotUi;

import java.util.List;

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
 * <h2>Why the version is still an argument, though this plugin ignores it</h2>
 *
 * <p>{@link #catalog(String)} takes the version <em>the bot pins</em>, not this jar's, and until 2026-08-26
 * the SDK answered it from a per-version class. It no longer does: there is one catalog, reflected off the
 * facades in <em>this</em> build, and the pin is not consulted.
 *
 * <p>The rule it used to serve is unchanged, and is met somewhere better. What an older pin may be offered
 * is this catalog <b>intersected with the bot's own resolved jar</b>, which {@code SdkSurfaceService}
 * already computes from bytecode — so a member this build added is still absent from an older bot, because
 * that bot's jar does not contain it. A frozen class per version could only restate, by hand, what the jar
 * already says; and it had to be edited whenever a member was deleted, which made it untruthful about the
 * past exactly when it mattered.
 *
 * <p>The parameter stays on the contract regardless. It is not the SDK's to remove — another plugin may
 * ship per-version curation and needs somewhere to read the pin from — and a surface that narrows to fit
 * its only implementor is the back door this class exists to refuse.
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

    public SdkPlugin() {
        super(ID, "BotMaker SDK");
    }

    /**
     * Built once, by reflection over the facades named here. Every member is <em>discovered</em> rather than
     * named — {@code @Hidden}, {@code @PaletteDefault} and {@code @PaletteLabel} travel with the member they
     * annotate — so nothing in this list can go stale except a class that no longer exists, and that is a
     * javac error because these are class literals. Reflection runs exactly once in the editor and never at
     * all on a bot's classpath, where this class is not loaded.
     *
     * <p>The order here is the order the menus fall back to when two facades share a {@code @Palette} order;
     * {@code PaletteCatalog.of} sorts by that order first, so this list is documentation rather than policy.
     *
     * <p>It is a method rather than a {@code static final} field so that the reflection happens the first time
     * the palette is <em>asked for</em> rather than when {@code ServiceLoader} constructs this class, which
     * the host does while opening a project. {@link AbstractStudioPlugin} caches the answer.
     */
    @Override
    protected PaletteCatalog buildCatalog() {
        return PaletteCatalog.of(
            com.botmaker.sdk.api.interaction.Mouse.class,
            com.botmaker.sdk.api.interaction.Keyboard.class,
            com.botmaker.sdk.api.interaction.Wait.class,
            com.botmaker.sdk.api.vision.ImageFinder.class,
            com.botmaker.sdk.api.vision.ImageClicker.class,
            com.botmaker.sdk.api.vision.ImageWaiter.class,
            com.botmaker.sdk.api.vision.Pixel.class,
            com.botmaker.sdk.api.vision.Text.class,
            com.botmaker.sdk.api.vision.Vision.class,
            com.botmaker.sdk.api.bot.BotSettings.class,
            com.botmaker.sdk.api.util.Debug.class,
            com.botmaker.sdk.api.bot.Session.class,
            com.botmaker.sdk.api.bot.Bot.class,
            com.botmaker.sdk.api.bot.Watchdog.class,
            com.botmaker.sdk.api.bot.PopupGuard.class,
            com.botmaker.sdk.api.bot.Activity.class,
            com.botmaker.sdk.api.config.Wire.class,
            com.botmaker.sdk.api.launch.Game.class,
            com.botmaker.sdk.api.launch.Target.class,
            com.botmaker.sdk.api.emulator.Emulators.class,
            com.botmaker.sdk.api.capture.Source.class,
            com.botmaker.sdk.api.capture.Window.class,
            com.botmaker.sdk.api.util.Time.class,
            com.botmaker.sdk.api.util.BotMaker.class,
            com.botmaker.sdk.api.geometry.Point.class,
            com.botmaker.sdk.api.geometry.Rect.class,
            com.botmaker.sdk.api.geometry.Size.class,
            com.botmaker.sdk.api.bot.BotStuckException.class,
            com.botmaker.sdk.api.bot.StartMode.class,
            com.botmaker.sdk.api.capture.CaptureSource.class,
            com.botmaker.sdk.api.geometry.Direction.class,
            com.botmaker.sdk.api.emulator.Emulator.class,
            com.botmaker.sdk.api.emulator.EmulatorRef.class,
            com.botmaker.sdk.api.emulator.EmulatorSource.class,
            com.botmaker.sdk.api.interaction.Key.class,
            com.botmaker.sdk.api.interaction.MouseButton.class,
            com.botmaker.sdk.api.launch.LaunchTarget.class,
            com.botmaker.sdk.api.vision.ColorMatch.class,
            com.botmaker.sdk.api.vision.ImageTemplate.class,
            com.botmaker.sdk.api.vision.ImageTemplateGroup.class,
            com.botmaker.sdk.api.vision.Matches.class,
            com.botmaker.sdk.api.vision.MatchResult.class,
            com.botmaker.sdk.api.vision.Precision.class,
            com.botmaker.sdk.api.vision.TextMatch.class,
            com.botmaker.sdk.api.vision.OcrOptions.class,
            com.botmaker.sdk.api.vision.OcrLanguage.class,
            com.botmaker.sdk.api.vision.TextResult.class,
            com.botmaker.sdk.api.flow.FlowGraph.class,
            com.botmaker.sdk.api.flow.PopupCheck.class,
            com.botmaker.sdk.api.flow.Recovery.class,
            com.botmaker.sdk.api.meta.Since.class,
            com.botmaker.sdk.api.meta.ReplacedBy.class,
            com.botmaker.sdk.api.meta.Replaces.class);
    }

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
     */
    @Override
    protected List<SlotEditor> buildSlotEditors() {
        return SdkEditors.ALL;
    }

    /**
     * The seventeen types a project variable could hold before there was a registry to hold them in.
     *
     * <p>They are registered through the same builder any plugin uses, and their ids are the constant names
     * of the enum they used to be, so every project ever written keeps its meaning. That is the whole test
     * of the surface: the vocabulary that was hard-coded into the host is now contributed by a plugin, and
     * it had to give up nothing to become contributable.
     */
    @Override
    protected ValueCatalog buildValueTypes() {
        return SdkValueTypes.CATALOG;
    }

    /**
     * One section, {@code Parameters} — the class every bot has always had, now declared rather than assumed.
     *
     * <p>Its id is blank ({@link ParameterGroup#DEFAULT_ID}), which is the whole of the migration: a variable
     * in a project written before groups existed carries no group, reads back as blank, and is therefore this
     * plugin's. A second plugin declares {@code ParameterGroup.of("discord", "DiscordParameters")} and gets
     * its own section, its own file and its own namespace.
     *
     * <p>Total in the pin, like {@link #catalog(String)}: the class has existed in every SDK there has been.
     */
    @Override
    protected List<ParameterGroup> buildParameters() {
        return List.of(SDK_PARAMETERS);
    }

    /**
     * One button, <b>Pilot</b> — and it is the case the toolbar surface was added for.
     *
     * <p>The Remote Pilot is not an editor for a slot: it binds a port, opens a nested {@code :N} display,
     * streams frames to a phone and drives input back. It was Studio's until 2026-08-30 and it was never
     * Studio's subject — everything behind this button is about what a <em>bot</em> sees and does, which is
     * this plugin's subject. What the host keeps is the bar itself: the grouping, the order, the packing and
     * the overflow, which is exactly why an item is contributed as data rather than as a {@code Node}.
     *
     * <p>The UI is built lazily and kept, because it owns the port and the display: a second press must
     * re-show the pairing dialog rather than rebind and drop an already-paired phone. It is released in
     * {@link #projectClosing()}.
     */
    @Override
    public List<ToolbarItem> toolbarItems() {
        return List.of(ToolbarItem.of("pilot", "🎮 Pilot",
                "Stream what the bot sees to your phone or browser — watch it, start/stop it, "
                        + "or turn on Interact to click and drag in the game yourself",
                ToolbarGroup.RUN, 10, context -> pilot(context.services()).open()));
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

    /**
     * The one parameter group this plugin owns.
     *
     * <p>It lived on {@code SourceEmitter} until that class was deleted, which was always the wrong home: a
     * group is not about the generated {@code Parameters} file it once named — that file has not existed
     * since the derived files became runtime reads — it is how the editor's Parameters dialog decides which
     * plugin a variable belongs to.
     */
    private static final ParameterGroup SDK_PARAMETERS =
            new ParameterGroup(ParameterGroup.DEFAULT_ID, "Parameters", "Parameters");

}
