package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.robot.Robot;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * One entry of the menu: its words, and how it turns into a surface — at once, or through
     * {@link SourcePicker}, which may be cancelled and then calls nothing.
     */
    public record Entry(String label, Consumer<Consumer<Surface>> resolve) {}

    /**
     * The entries, in menu order: the last other surface again, the bot's own source, another window or
     * screen, the whole desktop. What {@link #choose} shows, and what a widget with a menu of its own lists
     * there instead (feedback 2, 2026-09-27). A choice is remembered as it resolves.
     */
    public static List<Entry> entries(StudioServices services) {
        Window owner = Modals.owner(services);
        Surface bots = botsOwn(services);
        List<Entry> entries = new ArrayList<>();
        Surface last = LAST.get(key(services));
        if (last != null && !last.botsOwn() && !CaptureLabels.isDesktop(last.source())) {
            entries.add(fixed(services, "Again: " + last.label(), last));
        }
        if (bots != null) entries.add(fixed(services, bots.label(), bots));
        if (owner != null) {
            entries.add(new Entry("Another window or screen…", onChosen -> new SourcePicker(services, owner, false)
                    .showAndWait()
                    .filter(SourcePicker.Selection.Concrete.class::isInstance)
                    .map(SourcePicker.Selection.Concrete.class::cast)
                    .ifPresent(c -> remember(services, new Surface(c.target(), false,
                            CaptureLabels.shortLabel(c.target())), onChosen))));
        }
        entries.add(fixed(services, "Whole desktop", desktop()));
        return List.copyOf(entries);
    }

    /** Shows the menu and hands the choice over on the FX thread; a dismissed menu calls nothing. */
    public static void choose(StudioServices services, Consumer<Surface> onChosen) {
        Window owner = Modals.owner(services);
        if (owner == null) {
            // No window to put a menu on: the bot's own source when there is one, else the desktop.
            Surface bots = botsOwn(services);
            remember(services, bots != null ? bots : desktop(), onChosen);
            return;
        }
        List<Entry> entries = entries(services);
        ContextMenu menu = new ContextMenu();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (i == entries.size() - 1) menu.getItems().add(new SeparatorMenuItem());
            MenuItem item = new MenuItem(entry.label());
            item.setOnAction(e -> entry.resolve().accept(onChosen));
            menu.getItems().add(item);
        }
        Robot robot = new Robot();
        menu.show(owner, robot.getMouseX(), robot.getMouseY());
    }

    private static Surface botsOwn(StudioServices services) {
        CaptureSource source = EditorFrame.defaultSource(services);
        return source == null ? null
                : new Surface(source, true, "Bot's source (" + CaptureLabels.shortLabel(source) + ")");
    }

    private static Entry fixed(StudioServices services, String label, Surface surface) {
        return new Entry(label, onChosen -> remember(services, surface, onChosen));
    }

    private static void remember(StudioServices services, Surface surface, Consumer<Surface> onChosen) {
        LAST.put(key(services), surface);
        onChosen.accept(surface);
    }

    private static Surface desktop() {
        return new Surface(CaptureSource.desktop(), false, "Whole desktop");
    }

    private static Path key(StudioServices services) {
        Path dir = services.resourcesDir();
        return dir == null ? Path.of("") : dir;
    }
}
