package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A picture of each whole screen of the map, for 🗺 Screens' cards and side panel: the marker alone says which screen
 * it is but not what is on it (the user's call, 2026-10-15). Kept at the capture's own size, so a picture cut on that
 * frame is boxed on it pixel for pixel (the user's call, 2026-10-15, after a 480-pixel shot read too blurred). One file
 * a screen in the project's resources, beside the pictures and never one of them: nothing matches it, and a missing
 * one only leaves the card showing its marker. try_input saves a draft when it opens a screen, and the cut of the
 * screen's marker replaces it with the frame it was cut from; which_screen saves one when it sees a screen that has
 * none. Any other shot is taken only on a frame the screen's marker shows on (2026-10-16). A frame showing one of the
 * screen's looks ({@code ScreenVariant}) is that look's shot, and leaves the screen's own as it was (2026-10-22).
 *
 * <p><b>The screen's Java names its shot</b>, {@code Setup.shot("screens/HOME.png")} and a look's
 * {@code ScreenVariant.shot(…)} (the user's call, 2026-10-22): a file found by the screen's constant was lost by any
 * rename that did not move it too, Studio's or the IDE's. The first save picks a file no other screen names,
 * {@code screens/<CONSTANT>.png} or {@code screens/<CONSTANT>@<look>.png}, and writes its path in; a rename leaves both
 * as they are. A screen whose Java names no shot has none, whatever files sit in the folder, except that a map made
 * before that names its old files once when 🗺 Screens opens it ({@link #unnamed}, 2026-10-30).
 */
public final class ScreenShots {

    /** The folder in the project's resources. */
    static final String FOLDER = "screens";

    private ScreenShots() {}

    /**
     * How big the shot in {@code file} is, read off its header without decoding it, so a card can be laid out at the
     * shot's shape while the shot itself loads; {@code 0 × 0} when there is none.
     */
    public static Size size(Path file) {
        if (file == null) return new Size();
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return new Size();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return new Size();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new Size(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException unread) {
            return new Size();
        }
    }

    /** Where {@code constant}'s shot is in {@code resources}, as its Java names it; empty when it names none. */
    public static Optional<Path> file(Path resources, ScreenGraph graph, String constant) {
        return file(resources, graph, constant, null);
    }

    /**
     * Where {@code constant}'s shot in its look {@code look} is, as the look names it; the screen's own for null or
     * blank, the plain look. Empty when it names none, or {@code resources} is not known.
     */
    public static Optional<Path> file(Path resources, ScreenGraph graph, String constant, String look) {
        MapScreen screen = graph == null || constant == null ? null : graph.screens().get(constant);
        if (resources == null || screen == null) return Optional.empty();
        return Optional.ofNullable(named(screen, look)).map(resources::resolve);
    }

    /** Where {@code screen}'s own shot is in {@code resources}, as its Java names it; empty when it names none. */
    public static Optional<Path> file(Path resources, MapScreen screen) {
        if (resources == null || screen == null) return Optional.empty();
        return Optional.ofNullable(screen.setup().shot()).map(resources::resolve);
    }

    /** The shot shown for {@code constant} in {@code look}: that look's, else the screen's own while the look has none. */
    public static Optional<Path> shown(Path resources, ScreenGraph graph, String constant, String look) {
        return file(resources, graph, constant, look).filter(Files::isRegularFile)
                .or(() -> file(resources, graph, constant));
    }

    /** Whether {@code constant}'s own shot is there: named, and its file on disk. */
    public static boolean has(Path resources, ScreenGraph graph, String constant) {
        return file(resources, graph, constant).filter(Files::isRegularFile).isPresent();
    }

    /** {@code look} as its shot's file names it: lower case, a dash for each run of other marks; empty for none. */
    static String lookId(String look) {
        if (look == null) return "";
        return look.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /**
     * A path for {@code constant}'s first shot in {@code look} (the screen's own for null) that no screen or look of
     * {@code graph} names already: {@code screens/HOME.png}, then {@code screens/HOME_2.png}. A file there that no
     * screen names is nobody's, and is written over.
     */
    static String fresh(ScreenGraph graph, String constant, String look) {
        Set<String> taken = taken(graph);
        String id = lookId(look);
        String base = FOLDER + "/" + constant + (id.isEmpty() ? "" : "@" + id);
        String path = base + ".png";
        for (int n = 2; taken.contains(path); n++) path = base + "_" + n + ".png";
        return path;
    }

    /**
     * Saves {@code frame} as {@code constant}'s shot in {@code look} (its own for null), over the one it had, and
     * writes the path into its Java when it named none: one step of Studio's history. Off the FX thread: it writes a
     * file. A shot that cannot be written is left, as is a look the screen does not have.
     */
    static void save(StudioServices services, ScreenGraph graph, String constant, String look, BufferedImage frame) {
        Path resources = services == null ? null : services.resourcesDir();
        MapScreen screen = graph == null || constant == null ? null : graph.screens().get(constant);
        if (resources == null || screen == null || frame == null) return;
        String plain = lookId(look).isEmpty() ? null : look;
        Optional<ScreenVariant> variant = plain == null ? Optional.empty()
                : screen.variants().stream().filter(v -> v.name().equals(plain)).findFirst();
        if (plain != null && variant.isEmpty()) return;
        String named = named(screen, plain);
        String path = named != null ? named : fresh(graph, constant, plain);
        if (!write(resources.resolve(path), frame) || named != null) return;
        try {
            FxCall.call(() -> ScreenBook.update(services, constant, s -> s.setup(x -> variant.isEmpty() ? x.shot(path)
                    : x.withVariant(variant.get().shot(path)))));
        } catch (IllegalStateException busy) {
            // The file is kept; the next save names it again.
        }
    }

    /** As {@link #save}, as the screen's own shot, only when it has none yet. */
    static void saveIfMissing(StudioServices services, ScreenGraph graph, String constant, BufferedImage frame) {
        if (services != null && !has(services.resourcesDir(), graph, constant)) {
            save(services, graph, constant, null, frame);
        }
    }

    /** Writes {@code frame} to {@code file}, its folder made; false when it could not. */
    static boolean write(Path file, BufferedImage frame) {
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(frame, "png", file.toFile());
            return true;
        } catch (IOException | RuntimeException unwritten) {
            // A card without its shot shows its marker: nothing a tool call should fail for.
            return false;
        }
    }

    /** Removes the files {@code screen} names, its own shot and each of its looks': its screen left the map. */
    static void remove(Path resources, MapScreen screen) {
        if (resources == null || screen == null) return;
        try {
            if (screen.setup().shot() != null) Files.deleteIfExists(resources.resolve(screen.setup().shot()));
            for (ScreenVariant variant : screen.variants()) {
                if (variant.shot() != null) Files.deleteIfExists(resources.resolve(variant.shot()));
            }
        } catch (IOException ignored) {
            // A stray shot is named by no screen: nothing reads it.
        }
    }

    /**
     * A shot taken before the screen's Java named it (2026-10-30): {@code path} is {@code constant}'s own shot, or its
     * look {@code look}'s.
     */
    public record Unnamed(String constant, String look, String path) {}

    /**
     * The shots a map made before 2026-10-22 kept by its constant alone: for a screen naming no shot,
     * {@code screens/<CONSTANT>.png}, and for a look naming none, {@code screens/<CONSTANT>@<look>.png}, each while the
     * file is in {@code resources} and no other screen or look names it. Off the FX thread: it states files.
     */
    public static List<Unnamed> unnamed(Path resources, ScreenGraph graph) {
        if (resources == null || graph == null) return List.of();
        Set<String> taken = taken(graph);
        List<Unnamed> found = new ArrayList<>();
        for (Map.Entry<String, MapScreen> entry : graph.screens().entrySet()) {
            String constant = entry.getKey();
            MapScreen screen = entry.getValue();
            if (screen.setup().shot() == null) {
                unnamed(resources, taken, constant, null).ifPresent(found::add);
            }
            for (ScreenVariant variant : screen.variants()) {
                if (variant.shot() == null && !lookId(variant.name()).isEmpty()) {
                    unnamed(resources, taken, constant, variant.name()).ifPresent(found::add);
                }
            }
        }
        return found;
    }

    private static Optional<Unnamed> unnamed(Path resources, Set<String> taken, String constant, String look) {
        String id = lookId(look);
        String path = FOLDER + "/" + constant + (id.isEmpty() ? "" : "@" + id) + ".png";
        if (!Files.isRegularFile(resources.resolve(path)) || !taken.add(path)) return Optional.empty();
        return Optional.of(new Unnamed(constant, look, path));
    }

    /**
     * Writes each of {@code shots} into its screen's Java, as {@link #save} names a first shot: one step of Studio's
     * history. On the FX thread. Empty when written, else why not.
     */
    public static Optional<String> adopt(StudioServices services, List<Unnamed> shots) {
        if (services == null || shots.isEmpty()) return Optional.empty();
        Map<String, UnaryOperator<MapScreen>> changes = new LinkedHashMap<>();
        for (Unnamed shot : shots) {
            // Read again by then: a shot the screen or look has come to name since is kept.
            UnaryOperator<MapScreen> named = s -> s.setup(x -> shot.look() == null
                    ? x.shot() == null ? x.shot(shot.path()) : x
                    : x.variants().stream().filter(v -> v.name().equals(shot.look()) && v.shot() == null).findFirst()
                    .map(v -> x.withVariant(v.shot(shot.path()))).orElse(x));
            changes.merge(shot.constant(), named, (was, then) -> s -> then.apply(was.apply(s)));
        }
        return ScreenBook.updateAll(services, "Name the screens' shots", changes);
    }

    /** Every path a screen or look of {@code graph} names for its shot. */
    private static Set<String> taken(ScreenGraph graph) {
        Set<String> taken = new HashSet<>();
        for (MapScreen screen : graph.screens().values()) {
            if (screen.setup().shot() != null) taken.add(screen.setup().shot());
            for (ScreenVariant variant : screen.variants()) if (variant.shot() != null) taken.add(variant.shot());
        }
        return taken;
    }

    /** The path {@code screen}'s Java names for its shot in {@code look}, its own for null or blank; null for none. */
    private static String named(MapScreen screen, String look) {
        if (lookId(look).isEmpty()) return screen.setup().shot();
        return screen.variants().stream().filter(v -> v.name().equals(look)).findFirst().map(ScreenVariant::shot)
                .orElse(null);
    }
}
