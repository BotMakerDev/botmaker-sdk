package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.robot.Robot;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Where a pick is made (2026-09-26): the bot's own source, another window or screen, or the whole desktop —
 * a small menu at the pointer, opened by the pill item the user just clicked. The last other surface is
 * remembered per project for this session and offered first; nothing is written anywhere.
 *
 * <p>Here and not in {@code screen}: it opens {@link SourcePicker}, and {@code source} already depends on
 * {@code screen} ({@code PluginLayersTest} refuses the cycle).
 *
 * <p>One entry opens {@link SourcePicker} for every other window, screen or emulator: it lists them together
 * and has no per-kind filter, so a menu entry per kind would open the same list three times.
 */
public final class SurfaceMenu {

    /** What the user chose; {@code botsOwn} grabs without raising, as a pixel editor should. */
    public record Surface(CaptureSource source, boolean botsOwn, String label) {}

    private static final Map<Path, Surface> LAST = new ConcurrentHashMap<>();

    private SurfaceMenu() {}

    /** Shows the menu and hands the choice over on the FX thread; a dismissed menu calls nothing. */
    public static void choose(StudioServices services, Consumer<Surface> onChosen) {
        Window owner = services.dialogs().ownerWindow().orElse(null);
        CaptureSource botSource = EditorFrame.defaultSource(services);
        Surface bots = botSource == null ? null
                : new Surface(botSource, true, "Bot's source (" + CaptureLabels.shortLabel(botSource) + ")");
        Consumer<Surface> remember = s -> {
            LAST.put(key(services), s);
            onChosen.accept(s);
        };
        if (owner == null) {
            remember.accept(bots != null ? bots : desktop());
            return;
        }
        ContextMenu menu = new ContextMenu();
        Surface last = LAST.get(key(services));
        if (last != null && !last.botsOwn() && !CaptureLabels.isDesktop(last.source())) {
            menu.getItems().add(item("Again: " + last.label(), last, remember));
        }
        if (bots != null) menu.getItems().add(item(bots.label(), bots, remember));
        MenuItem other = new MenuItem("Another window or screen…");
        other.setOnAction(e -> new SourcePicker(services, owner, false).showAndWait()
                .filter(SourcePicker.Selection.Concrete.class::isInstance)
                .map(SourcePicker.Selection.Concrete.class::cast)
                .ifPresent(c -> remember.accept(new Surface(c.target(), false,
                        CaptureLabels.shortLabel(c.target())))));
        menu.getItems().addAll(other, new SeparatorMenuItem(), item("Whole desktop", desktop(), remember));
        Robot robot = new Robot();
        menu.show(owner, robot.getMouseX(), robot.getMouseY());
    }

    private static MenuItem item(String text, Surface surface, Consumer<Surface> onChosen) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> onChosen.accept(surface));
        return item;
    }

    private static Surface desktop() {
        return new Surface(CaptureSource.desktop(), false, "Whole desktop");
    }

    private static Path key(StudioServices services) {
        Path dir = services.resourcesDir();
        return dir == null ? Path.of("") : dir;
    }
}
