package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.CompanionPlugin;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.ToolbarGroup;
import com.botmaker.plugin.api.ToolbarItem;
import com.botmaker.sdk.internal.plugin.pilot.RemotePilotUi;

import java.util.List;

/**
 * The Remote Pilot, as a plugin in its own right.
 *
 * <p><b>Why this is not part of {@link SdkPlugin}.</b> Everything {@code SdkPlugin} contributes decides what
 * a bot's <em>source</em> says: which members the palette proposes, how a {@code Rect} is written down, what
 * a project variable may hold, which values become fields. The pilot decides none of that. It binds a port,
 * opens a nested {@code :N} display, streams frames to a phone and drives input back, and it never reads or
 * writes a line of the project's Java. That is the line {@link CompanionPlugin} draws, and the pilot is the
 * case that drew it.
 *
 * <p>The split costs nothing at runtime — both are declared in {@code META-INF/services} and both are found
 * on the same pass — and buys two things. The host can tell the two kinds apart, which is what lets a
 * companion eventually be implemented by something that is not this JVM. And the SDK stops being one class
 * answering questions from two unrelated subjects, which is the shape that made the pilot look like Studio's
 * for as long as it did.
 *
 * <p><b>It stays in this jar for now.</b> The pilot reads the project's default capture target, which is the
 * SDK's own {@code capture.json}; shipping it separately is a question about packaging, not about which
 * interface it implements.
 */
public final class PilotCompanion implements CompanionPlugin {

    @Override
    public String id() {
        return "botmaker-pilot";
    }

    @Override
    public String displayName() {
        return "Remote Pilot";
    }

    /**
     * The one button, in the RUN group beside the host's own run controls.
     *
     * <p>What the host keeps is the bar itself: the grouping, the order, the packing and the overflow, which
     * is exactly why an item is contributed as data rather than as a {@code Node} — and why a companion
     * plugin, which can contribute nothing else to the editor, can still be reached.
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
}
