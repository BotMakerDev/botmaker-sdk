package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.toolbar.ToolbarGroup;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import com.botmaker.sdk.plugin.flow.ActivityFlowDialog;
import com.botmaker.sdk.plugin.pictures.CaptureTemplates;
import com.botmaker.sdk.plugin.pictures.ResourceManagerDialog;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.settings.BotSettingsWindow;
import com.botmaker.sdk.plugin.setup.ProjectSetup;
import com.botmaker.sdk.plugin.source.SourcePicker;

import java.util.List;

/**
 * The SDK's toolbar buttons. The host keeps the bar itself — the grouping, the order, the packing and the
 * overflow — which is why an item is contributed as data rather than as a {@code Node}.
 *
 * <p>Each press names the {@code open(ActionContext)} of the feature it opens, behind the extra arrow
 * {@link com.botmaker.plugin.api.toolbar.Pressed} asks for: building this list must link no JavaFX, because a
 * headless host builds it too ({@code SdkPluginHeadlessTest}).
 *
 * <p>The ids are constants because the host prefixes each with the plugin's id and keeps it: a renamed id is
 * a button whose remembered state is lost.
 */
public final class SdkToolbarItems {

    public static final String PILOT_ID = "pilot";
    public static final String CAPTURE_TEMPLATES_ID = "capture-templates";
    public static final String MANAGE_PICTURES_ID = "manage-templates";
    public static final String ACTIVITY_FLOW_ID = "activity-flow";
    public static final String CAPTURE_SOURCE_ID = "capture-source";
    public static final String BOT_SETTINGS_ID = "bot-settings";
    public static final String PROJECT_SETUP_ID = "project-setup";
    public static final String POINT_HERE_ID = "point-here";
    public static final String PICTURE_HERE_ID = "picture-here";

    /** Streams what the bot sees to a phone; the pilot is kept per project, see {@link RemotePilotUi#open}. */
    public static final ToolbarItem PILOT = ToolbarItem.id(PILOT_ID).label("🎮 Pilot")
            .tooltip("Stream what the bot sees to your phone or browser — watch it, start/stop it, "
                    + "or turn on Interact to click and drag in the game yourself")
            .in(ToolbarGroup.RUN, 10)
            .onPress(() -> RemotePilotUi::open);

    /**
     * In {@link ToolbarGroup#TOOLS}, whose own definition names a template cutter: it is opened <em>over</em> a
     * running target rather than beside the code.
     */
    public static final ToolbarItem CAPTURE_TEMPLATES = ToolbarItem.id(CAPTURE_TEMPLATES_ID)
            .label("✂ Capture Templates")
            .tooltip("Draw regions over the game and save them as pictures the bot can look for — "
                    + "one at a time, several in a pass, or an object cut out of its background")
            .in(ToolbarGroup.TOOLS, 20)
            .onPress(() -> CaptureTemplates::open);

    public static final ToolbarItem MANAGE_PICTURES = ToolbarItem.id(MANAGE_PICTURES_ID).label("🖼 Manage Pictures")
            .tooltip("Rename, retag, replace, delete, import and export the pictures the bot looks for — "
                    + "a rename carries every block that uses it")
            .in(ToolbarGroup.TOOLS, 30)
            .onPress(() -> ResourceManagerDialog::open);

    /** {@link ToolbarGroup#AUTHORING} at 10. */
    public static final ToolbarItem ACTIVITY_FLOW = ToolbarItem.id(ACTIVITY_FLOW_ID).label("🔀 Activity Flow")
            .tooltip("Define what this bot does, one card per activity, and wire each outcome to what "
                    + "runs next — the graph, its loop safety, and which activities are on")
            .in(ToolbarGroup.AUTHORING, 10)
            .onPress(() -> ActivityFlowDialog::open);

    public static final ToolbarItem CAPTURE_SOURCE = ToolbarItem.id(CAPTURE_SOURCE_ID).label("🎯 Capture Source")
            .tooltip("Choose what the bot looks at — a monitor, an application window or an emulator instance")
            .in(ToolbarGroup.PROJECT, 50)
            .onPress(() -> SourcePicker::choose);

    public static final ToolbarItem BOT_SETTINGS = ToolbarItem.id(BOT_SETTINGS_ID).label("⚙ Bot Settings")
            .tooltip("How the bot clicks and looks — delays, match confidence, real input for games, and "
                    + "whether it runs on a private display")
            .in(ToolbarGroup.PROJECT, 60)
            .onPress(() -> BotSettingsWindow::open);

    /** At 40, immediately before Capture Source, because it is the window that sends a user to that one. */
    public static final ToolbarItem PROJECT_SETUP = ToolbarItem.id(PROJECT_SETUP_ID).label("📋 Project Setup")
            .tooltip("What this project still needs before it can run — something to launch, something "
                    + "to capture, and the pictures it looks for")
            .in(ToolbarGroup.PROJECT, 40)
            .onPress(() -> ProjectSetup::open);

    /** Drawn on the overlay editor's own row; its subject is the window the HUD is drawn over. */
    public static final ToolbarItem POINT_HERE = ToolbarItem.id(POINT_HERE_ID).label("⌖ Point bot here")
            .tooltip("Make the window the overlay is drawn over this project's capture source")
            .in(ToolbarGroup.OVERLAY, 10)
            .onPress(() -> CaptureValue::pointHere);

    public static final ToolbarItem PICTURE_HERE = ToolbarItem.id(PICTURE_HERE_ID).label("✂ Picture of this")
            .tooltip("Cut a picture out of the window the overlay is drawn over, whatever the project's "
                    + "capture target is")
            .in(ToolbarGroup.OVERLAY, 20)
            .onPress(() -> CaptureTemplates::pictureHere);

    /** Every item, in declaration order; the host sorts on each item's group and order. */
    public static final List<ToolbarItem> ALL = List.of(PILOT, CAPTURE_TEMPLATES, MANAGE_PICTURES, ACTIVITY_FLOW,
            CAPTURE_SOURCE, BOT_SETTINGS, PROJECT_SETUP, POINT_HERE, PICTURE_HERE);

    private SdkToolbarItems() {}
}
