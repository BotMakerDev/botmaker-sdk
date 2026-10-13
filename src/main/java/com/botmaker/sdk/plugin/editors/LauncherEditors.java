package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.plugin.launch.GameDialog;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The editor for a {@link Launcher} — one of the launchers a bot starts its game from — in the Runner and a
 * Parameters row: a pill naming the game that opens 🎮 Game to pick another, and ▶ Launch, which starts that game
 * without running the bot so its window can be picked as the capture source (2026-10-13). The pick is handed back
 * as a value; the dialog writes nothing itself ({@link GameDialog#pick}).
 */
public final class LauncherEditors {

    private LauncherEditors() {}

    public static Node launcher(ValueContext ctx) {
        Launcher current = ctx.value(Launcher.class).orElse(null);
        MenuButton pill = Pills.bare(current != null ? label(ctx, current) : Slots.sourceOr(ctx, "Choose a game…"));
        HBox row = new HBox(4, pill, launchButton(ctx.services(), current));
        Pills.onOpen(pill, () -> List.of(Pills.item("Choose a game…", () ->
                GameDialog.pick(ctx.services(), Modals.owner(ctx.services()), spec -> {
                    Launcher picked = Launcher.of(spec);
                    ctx.set(picked);
                    pill.setText(label(ctx, picked));
                    // A new button rather than the old one re-pointed: a launch still running re-enables the
                    // button it started from when it ends, which must not be the one for the new game.
                    row.getChildren().set(1, launchButton(ctx.services(), picked));
                }))));
        return row;
    }

    /** The game's name beside a declared choice, or {@code null} for the plain label. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(Launcher.class).map(launcher -> (Node) new Label(label(ctx, launcher))).orElse(null);
    }

    /**
     * ▶ Launch for {@code launcher}'s own game, reporting on the host's status line; disabled, saying why, when
     * there is none, no project to launch it for, or this computer's system cannot start it.
     */
    private static Button launchButton(StudioServices services, Launcher launcher) {
        Button launch = Styles.on(new Button("▶ Launch"), Styles.PILL);
        LaunchSpec spec = launcher == null || services == null ? null : LaunchSpec.parse(launcher.spec());
        Os os = Os.current();
        if (spec != null && !spec.runsOn(os)) {
            launch.setDisable(true);
            launch.setTooltip(new Tooltip(spec.describe() + " does not run on " + os.displayName()));
            return launch;
        }
        QuickLaunch.bind(launch, spec, services, (ok, message) -> services.status(message));
        return launch;
    }

    /** The name the game was picked under, else the spec's short label; "No game" for an empty one. */
    private static String label(ValueContext ctx, Launcher launcher) {
        return launcher.spec().isEmpty() ? "No game" : GameButton.nameOf(ctx.services(), launcher.spec());
    }
}
