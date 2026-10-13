package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.plugin.launch.GameDialog;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;

import java.util.List;

/**
 * The editor for a {@link Launcher} — one of the launchers a bot starts its game from — in the Runner and a
 * Parameters row: a pill naming the game that opens 🎮 Game to pick another. The pick is handed back as a value;
 * the dialog writes nothing itself ({@link GameDialog#pick}).
 */
public final class LauncherEditors {

    private LauncherEditors() {}

    public static Node launcher(ValueContext ctx) {
        MenuButton pill = Pills.bare(ctx.value(Launcher.class).map(launcher -> label(ctx, launcher))
                .orElseGet(() -> Slots.sourceOr(ctx, "Choose a game…")));
        Pills.onOpen(pill, () -> List.of(Pills.item("Choose a game…", () ->
                GameDialog.pick(ctx.services(), Modals.owner(ctx.services()), spec -> {
                    Launcher picked = Launcher.of(spec);
                    ctx.set(picked);
                    pill.setText(label(ctx, picked));
                }))));
        return pill;
    }

    /** The game's name beside a declared choice, or {@code null} for the plain label. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(Launcher.class).map(launcher -> (Node) new Label(label(ctx, launcher))).orElse(null);
    }

    /** The name the game was picked under, else the spec's short label; "No game" for an empty one. */
    private static String label(ValueContext ctx, Launcher launcher) {
        return launcher.spec().isEmpty() ? "No game" : GameButton.nameOf(ctx.services(), launcher.spec());
    }
}
