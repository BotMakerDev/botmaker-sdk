package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.ToolbarHolder;
import com.botmaker.plugin.api.toolbar.EnabledWhen;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import com.botmaker.sdk.plugin.emulator.window.EmulatorsWindow;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.plugin.launch.GameDialog;
import com.botmaker.sdk.plugin.map.ScreenMap;
import com.botmaker.sdk.plugin.pictures.CaptureTemplates;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi;
import com.botmaker.sdk.plugin.screen.CaptureValue;

import java.util.List;

/**
 * The SDK's toolbar buttons. The host keeps the bar itself — the plugin's tab, the packing and the overflow —
 * which is why an item is contributed as data rather than as a {@code Node}.
 *
 * <p>The bar items read in one flat order (2026-10-28, when toolbar groups went): what the bot plays, then what it
 * does (its screens, whose rail holds its goals and pictures), and last the phone that watches it run. Where it
 * looks and how it clicks are parameters, set in the Parameters window (2026-11-09: 📋 Project Setup, 🎯 Capture
 * Source and ⚙ Bot Settings left the bar). The order values are steps of ten so a button can be put
 * between two without renumbering.
 *
 * <p>Each press names the {@code open(ActionContext)} of the feature it opens, behind the extra arrow
 * {@link com.botmaker.plugin.api.toolbar.Pressed} asks for: building this list must link no JavaFX, because a
 * headless host builds it too ({@code SdkPluginHeadlessTest}).
 *
 * <p>The ids are constants because the host prefixes each with the plugin's id and keeps it: a renamed id is
 * a button whose remembered state is lost.
 */
public final class SdkToolbarItems implements ToolbarHolder {

    public static final String GAME_ID = "game";
    public static final String EMULATORS_ID = "emulators";
    public static final String SCREEN_MAP_ID = "screen-map";
    public static final String PILOT_ID = "pilot";
    public static final String POINT_HERE_ID = "point-here";
    public static final String PICTURE_HERE_ID = "picture-here";

    /**
     * First: you pick what the bot plays — and a game's window can only be picked as a capture source once the
     * game is up. Says the current game's name and shows its cover ({@link GameButton}).
     */
    public static final ToolbarItem GAME = ToolbarItem.id(GAME_ID).label(GameButton::label)
            .tooltip("Which game this bot plays on this computer — pick it from Steam, Heroic, Faugus, Lutris, "
                    + "Waydroid or the app menu, and start it without running the bot")
            .onBar(10)
            .enabledWhen(EnabledWhen.PROJECT_OPEN)
            .icon(GameButton::icon)
            .onPress(() -> GameDialog::open);

    /**
     * This PC's emulators and their instances, the same for every bot, so it needs no project: what each should be,
     * what it is, and Start, Stop and Apply ({@link EmulatorsWindow}).
     */
    public static final ToolbarItem EMULATORS = ToolbarItem.id(EMULATORS_ID).label("📱 Emulators")
            .tooltip("This computer's Android emulators and their instances — what each should be, where it differs, "
                    + "and start, stop or apply them")
            .onBar(20)
            .enabledWhen(EnabledWhen.ALWAYS)
            .onPress(() -> EmulatorsWindow::open);

    /**
     * What the bot does: the game's screens and the actions on them, and behind its rail the goals and the pictures
     * (2026-11-09: 🎯 Goals, 🖼 Manage Pictures and ✂ Capture Templates left the bar; Screens cuts pictures itself).
     */
    public static final ToolbarItem SCREEN_MAP = ToolbarItem.id(SCREEN_MAP_ID).label("🗺 Screens")
            .tooltip("The game's screens as the bot knows them — how each was reached, its inputs and what they "
                    + "did; try, cut and go to one, and see where a run is. Its rail holds the goals and the pictures")
            .onBar(60)
            .onPress(() -> ScreenMap::open);

    /** Streams what the bot sees to a phone; the pilot is kept per project, see {@link RemotePilotUi#open}. */
    public static final ToolbarItem PILOT = ToolbarItem.id(PILOT_ID).label("🎮 Pilot")
            .tooltip("Stream what the bot sees to your phone or browser — watch it, start/stop it, "
                    + "or turn on Interact to click and drag in the game yourself")
            .onBar(90)
            .onPress(() -> RemotePilotUi::open);

    /** Drawn on the overlay editor's Actions row; its subject is the window the HUD is drawn over. */
    public static final ToolbarItem POINT_HERE = ToolbarItem.id(POINT_HERE_ID).label("⌖ Point bot here")
            .tooltip("Make the window the overlay is drawn over this project's capture source")
            .inOverlay(10)
            .onPress(() -> CaptureValue::pointHere);

    public static final ToolbarItem PICTURE_HERE = ToolbarItem.id(PICTURE_HERE_ID).label("✂ Picture of this")
            .tooltip("Cut a picture out of the window the overlay is drawn over, whatever the project's "
                    + "capture target is")
            .inOverlay(20)
            .onPress(() -> CaptureTemplates::pictureHere);

    /** Every item, the bar's in their reading order, then the overlay's; the host sorts on each item's order. */
    public static final List<ToolbarItem> ALL = List.of(GAME, EMULATORS, SCREEN_MAP, PILOT, POINT_HERE, PICTURE_HERE);

    private SdkToolbarItems() {}

    @Override
    public List<ToolbarItem> toolbar() {
        return ALL;
    }
}
