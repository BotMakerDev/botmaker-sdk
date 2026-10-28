package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.DeclaredPlugin;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.plugin.launch.GameReady;
import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.assist.SdkAssist;
import com.botmaker.sdk.plugin.overlay.SdkOverlay;
import com.botmaker.sdk.plugin.editors.SdkEditors;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi;
import com.botmaker.sdk.plugin.run.SdkRunOverlay;
import com.botmaker.sdk.plugin.settings.VmKeeper;
import com.botmaker.shared.platform.Os;
import com.botmaker.sdk.plugin.types.CaptureTypes;
import com.botmaker.sdk.plugin.types.FlowTypes;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import com.botmaker.sdk.plugin.types.SdkTypes;
import com.botmaker.sdk.plugin.types.SettingsTypes;

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
 * <p>Nothing here lists it: the host scans this jar for {@code @Palette}, and every {@code com.botmaker.sdk.api}
 * class carrying one is catalogued. What an older pin may be offered is that catalog <b>intersected with the
 * bot's own resolved jar</b>, which the host computes from bytecode.
 *
 * <h2>Where this class may live, and where it may not</h2>
 *
 * <p>Under {@code com.botmaker.sdk.plugin}, never {@code com.botmaker.sdk.api} or {@code internal}: a bot
 * cannot write this name down, and nothing a bot links may reach JavaFX or the toolkit
 * ({@code PluginLayersTest}).
 */
public final class SdkPlugin extends DeclaredPlugin {

    /**
     * The whole plugin, stated once: the types it owns and the parts inside its values ({@link SdkTypes}), the
     * editors a type cannot choose for itself ({@link SdkEditors}), the {@code @SdkValue} values its windows keep
     * ({@link SdkValues}), its buttons ({@link SdkToolbarItems}), its part of the run overlay
     * ({@link SdkRunOverlay}), and what it does while a project is open ({@link #opened}, {@link #closing}).
     *
     * <p><b>Constructing a plugin must not link an optional dependency</b>, so every surface is named by its
     * holder class, built and asked only when the host asks.
     * {@code javafx-controls} is {@code optional} here, so the classpath a headless host resolves this plugin
     * onto — the CLI's {@code validate} and {@code run}, the registry's CI — does not have it, and a constructor
     * that touched a JavaFX type fails there with:
     *
     * <pre>
     * NoClassDefFoundError: javafx/scene/Node
     * </pre>
     *
     * <p>{@code PluginLoader} catches that, so the symptom is an empty palette and one refusal. It never
     * shows locally, because an {@code optional} dependency <em>is</em> on this module's own classpath
     * ({@code SdkPluginHeadlessTest}).
     */
    public SdkPlugin() {
        super(StudioPlugin.declare()
                .types(SdkTypes.class, FlowTypes.class, ScreenTypes.class, CaptureTypes.class, SettingsTypes.class)
                .editors(SdkEditors.class)
                .values(SdkValues.class)
                .toolbar(SdkToolbarItems.class)
                .runOverlay(SdkRunOverlay.class)
                .overlay(SdkOverlay.class)
                .assistant(SdkAssistant.class)
                .assistantGuide(SdkAssist.GUIDE)
                .trial(Bot::trial)
                .readiness(GameReady::now)
                .onProjectOpened(SdkPlugin::opened)
                .onProjectClosing(SdkPlugin::closing));
    }

    /**
     * The toolbar's game button names what this project launches, so it is told which project that is, and the
     * plugin half's warnings go to its log. On Windows, the game VMs' shutdown settings are applied while a project
     * is open.
     */
    private static void opened(StudioServices services) {
        SdkLog.bind(services);
        GameButton.bind(services);
        GameReady.bind(services);
        if (Os.current() == Os.WINDOWS) VmKeeper.start(services);
    }

    /**
     * Releases the pilot's port and its nested display when the project it was serving is left. This plugin
     * instance is reused for the next project, and a pilot still answering on the old port would be streaming
     * a project nobody has open.
     */
    private static void closing() {
        RemotePilotUi.release();
        GameButton.unbind();
        GameReady.unbind();
        VmKeeper.stop();
        SdkLog.unbind();
    }
}
