package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.plugin.launch.GameButton;
import com.botmaker.sdk.plugin.launch.GameCatalog;
import com.botmaker.sdk.plugin.launch.GameDialog;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.screen.CaptureValue;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The editors for the bot's game: a {@link Launcher} — one of the games its author lists — and the
 * {@link LaunchChoice} the person running it picks among them. Each is a pill naming the game, and ▶ Launch, which
 * starts that game without running the bot so its window can be picked as the capture source (2026-10-13).
 *
 * <p>A {@code Launcher} is picked from every game on this computer (🎮 Game, {@link GameDialog#pick}); a
 * {@code LaunchChoice} only from the author's list, those whose system is another hidden and those this computer
 * lacks greyed (2026-10-19). Either hands the pick back as a value; a runner's pick of an emulator or phone app also
 * points capture at its device ({@link CaptureValue#follow}).
 */
public final class LauncherEditors {

    private static final String FIRST_AVAILABLE = "First available here";

    private LauncherEditors() {}

    public static Node launcher(ValueContext ctx) {
        Launcher current = ctx.value(Launcher.class).orElse(null);
        MenuButton pill = Pills.bare(current != null ? label(ctx.services(), current.spec())
                : Slots.sourceOr(ctx, "Choose a game…"));
        HBox row = new HBox(4, pill, launchButton(ctx.services(), current == null ? null : current.spec()));
        Pills.onOpen(pill, () -> List.of(Pills.item("Choose a game…", () ->
                GameDialog.pick(ctx.services(), Modals.owner(ctx.services()), spec -> {
                    Launcher picked = Launcher.of(spec);
                    ctx.set(picked);
                    pill.setText(label(ctx.services(), picked.spec()));
                    // A new button rather than the old one re-pointed: a launch still running re-enables the
                    // button it started from when it ends, which must not be the one for the new game.
                    row.getChildren().set(1, launchButton(ctx.services(), picked.spec()));
                }))));
        return row;
    }

    /** The game's name beside a declared choice, or {@code null} for the plain label. */
    public static Node preview(ValueContext ctx) {
        return ctx.value(Launcher.class).map(launcher -> (Node) new Label(label(ctx.services(), launcher.spec())))
                .orElse(null);
    }

    /**
     * The runner's pick: a pill listing {@link #FIRST_AVAILABLE} and the author's games this computer's system can
     * start, one this computer lacks greyed, and ▶ Launch for what the pick launches. Whether a game is here is the
     * last game scan's answer, started now when none ran: until it lands every game is offered.
     */
    public static Node choice(ValueContext ctx) {
        StudioServices services = ctx.services();
        String chosen = ctx.value(LaunchChoice.class).map(LaunchChoice::spec).orElse("");
        MenuButton pill = Pills.bare(choiceLabel(services, chosen));
        HBox row = new HBox(4, pill, launchButton(services, launches(services, chosen)));
        GameCatalog.scanOnce();
        Pills.onOpen(pill, () -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(Pills.item(FIRST_AVAILABLE, () -> choose(ctx, pill, row, "")));
            Os os = Os.current();
            boolean any = false;
            for (String spec : LaunchTargetValue.all(services)) {
                LaunchSpec parsed = LaunchSpec.parse(spec);
                if (parsed == null || !parsed.runsOn(os)) continue;
                any = true;
                boolean missing = GameCatalog.installed(spec).map(has -> !has).orElse(false);
                MenuItem item = Pills.item(label(services, spec) + (missing ? " (not installed)" : ""),
                        () -> choose(ctx, pill, row, spec));
                item.setDisable(missing);
                items.add(item);
            }
            if (!any) {
                MenuItem none = new MenuItem("The bot lists no game " + os.displayName() + " can start");
                none.setDisable(true);
                items.add(none);
            }
            return items;
        });
        return row;
    }

    /** The runner's pick beside a declared choice. */
    public static Node choicePreview(ValueContext ctx) {
        return ctx.value(LaunchChoice.class).map(choice -> (Node) new Label(choiceLabel(ctx.services(),
                choice.spec()))).orElse(null);
    }

    private static void choose(ValueContext ctx, MenuButton pill, HBox row, String spec) {
        Optional<String> refused = ctx.write(LaunchChoice.of(spec));
        if (refused.isPresent()) {
            if (ctx.services() != null) ctx.services().status(refused.get());
            return;
        }
        pill.setText(choiceLabel(ctx.services(), spec));
        String launched = launches(ctx.services(), spec);
        row.getChildren().set(1, launchButton(ctx.services(), launched));
        if (ctx.services() != null) CaptureValue.follow(ctx.services(), launched);
    }

    /** What a run here launches with {@code chosen} picked: it, or the first of the bot's games this system starts. */
    private static String launches(StudioServices services, String chosen) {
        return services == null ? null : InstalledGame.pick(LaunchTargetValue.all(services), chosen, Os.current());
    }

    /**
     * The pick's game, or what the first available is now. A pick the bot no longer lists, or this system cannot
     * start, is said to be passed over, so the pill names what ▶ Launch and a run start.
     */
    private static String choiceLabel(StudioServices services, String chosen) {
        String launched = launches(services, chosen);
        String wanted = InstalledGame.canonical(chosen);
        if (!wanted.isEmpty() && launched != null && InstalledGame.canonical(launched).equals(wanted)) {
            return label(services, chosen);
        }
        String first = launched == null ? FIRST_AVAILABLE
                : FIRST_AVAILABLE + ": " + GameButton.nameOf(services, launched);
        return wanted.isEmpty() ? first : first + " (" + GameButton.nameOf(services, chosen) + " is not available here)";
    }

    /**
     * ▶ Launch for {@code spec}'s own game, reporting on the host's status line; disabled, saying why, when
     * there is none, no project to launch it for, or this computer's system cannot start it.
     */
    private static Button launchButton(StudioServices services, String launcher) {
        Button launch = Styles.on(new Button("▶ Launch"), Styles.PILL);
        LaunchSpec spec = launcher == null || services == null ? null : LaunchSpec.parse(launcher);
        Os os = Os.current();
        if (spec != null && !spec.runsOn(os)) {
            launch.setDisable(true);
            launch.setTooltip(new Tooltip(spec.describe() + " does not run on " + os.displayName()));
            return launch;
        }
        QuickLaunch.bind(launch, spec, services, (ok, message) -> services.status(message));
        return launch;
    }

    /** What the game is and where it launches from — "Hades II · Steam game 1145350"; "No game" for none. */
    private static String label(StudioServices services, String spec) {
        return spec == null || spec.isEmpty() ? "No game" : GameButton.caption(services, spec);
    }
}
