package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.DeclaredPlugin;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.api.bot.Bot;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.plugin.assist.SdkAssist;
import com.botmaker.sdk.plugin.overlay.SdkOverlay;
import com.botmaker.sdk.plugin.editors.SdkEditors;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi;
import com.botmaker.sdk.plugin.run.SdkRunOverlay;
import com.botmaker.sdk.plugin.types.PictureAt;
import com.botmaker.sdk.plugin.types.SdkTypes;

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

    /** The stable identifier the host files this plugin's contributions under. */
    public static final String ID = "com.botmaker.sdk";

    /** What Studio shows in Manage Plugins. */
    public static final String NAME = "BotMaker SDK";

    /**
     * The whole plugin, stated once: the types it owns and the parts inside its values ({@link SdkTypes}), the
     * editors a type cannot choose for itself ({@link SdkEditors}), the {@code @Managed} values its windows keep
     * ({@link SdkValues}), its buttons ({@link SdkToolbarItems}), its part of the run overlay
     * ({@link SdkRunOverlay}) and the picture under a recorded click ({@link PictureAt}).
     *
     * <p><b>Constructing a plugin must not link an optional dependency</b>, so each list is behind a supplier.
     * {@code javafx-controls} is {@code optional} here, so the classpath a headless host resolves this plugin
     * onto — the CLI's {@code validate} and {@code run}, the registry's CI — does not have it, and a constructor
     * that touched a JavaFX type fails there with:
     *
     * <pre>
     * ServiceConfigurationError: Provider com.botmaker.sdk.plugin.SdkPlugin could not be instantiated
     *   Caused by: NoClassDefFoundError: javafx/scene/Node
     * </pre>
     *
     * <p>{@code PluginLoader} catches that, so the symptom is an empty palette and one line on stderr. It never
     * shows locally, because an {@code optional} dependency <em>is</em> on this module's own classpath
     * ({@code SdkPluginHeadlessTest}).
     */
    public SdkPlugin() {
        super(StudioPlugin.id(ID).named(NAME)
                .types(() -> SdkTypes.ALL)
                .parts(() -> SdkTypes.PARTS)
                .editors(() -> SdkEditors.ALL)
                .values(() -> SdkValues.ALL)
                .toolbar(() -> SdkToolbarItems.ALL)
                .runOverlay(() -> SdkRunOverlay.ALL)
                .overlay(() -> SdkOverlay.PART)
                .assistant(() -> SdkAssist.ALL)
                .trial(Bot::trial)
                .recorded(() -> PictureAt.ALL));
    }

    /**
     * Releases the pilot's port and its nested display when the project it was serving is left. This plugin
     * instance is reused for the next project, and a pilot still answering on the old port would be streaming
     * a project nobody has open.
     */
    @Override
    public void projectClosing() {
        RemotePilotUi.release();
        GameButton.unbind();
    }

    /** The toolbar's game button names what this project launches, so it is told which project that is. */
    @Override
    public void projectOpened(StudioServices services) {
        GameButton.bind(services);
    }
}
