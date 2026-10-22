package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Size;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

/**
 * A picture of each whole screen of the map, for 🗺 Screens' cards and side panel: the marker alone says which screen
 * it is but not what is on it (the user's call, 2026-10-15). Kept at the capture's own size, so a picture cut on that
 * frame is boxed on it pixel for pixel (the user's call, 2026-10-15, after a 480-pixel shot read too blurred). One file
 * a screen, {@code screens/<CONSTANT>.png} in the project's resources, beside the pictures and never one of them:
 * nothing matches it, and a missing one only leaves the card showing its marker. try_input saves a draft when it opens a
 * screen, and the cut of the screen's marker replaces it with the frame it was cut from; which_screen saves one when it
 * sees a screen that has none. Any other shot is taken only on a frame the screen's marker shows on (2026-10-16).
 * A frame showing one of the screen's looks ({@code ScreenVariant}) is that look's shot, {@code <CONSTANT>@<look>.png},
 * and leaves the screen's own as it was (2026-10-22).
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

    /** Where {@code constant}'s shot is in {@code resources}, whether or not it exists yet. */
    public static Path file(Path resources, String constant) {
        return resources.resolve(FOLDER).resolve(constant + ".png");
    }

    /**
     * Where {@code constant}'s shot in its look {@code look} is, {@code screens/EVENT@halloween.png}, whether or not it
     * exists yet; the screen's own shot for null or blank, the plain look.
     */
    public static Path file(Path resources, String constant, String look) {
        String id = lookId(look);
        return id.isEmpty() ? file(resources, constant) : resources.resolve(FOLDER).resolve(constant + "@" + id + ".png");
    }

    /** The shot shown for {@code constant} in {@code look}: that look's, else the screen's own while the look has none. */
    public static Path shown(Path resources, String constant, String look) {
        Path own = file(resources, constant, look);
        return Files.isRegularFile(own) ? own : file(resources, constant);
    }

    /** {@code look} as its shot's file names it: lower case, a dash for each run of other marks; empty for none. */
    static String lookId(String look) {
        if (look == null) return "";
        return look.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /** Saves {@code frame} as {@code constant}'s shot, over the one it had; a shot that cannot be written is left. */
    static void save(Path resources, String constant, BufferedImage frame) {
        save(resources, constant, null, frame);
    }

    /** As {@link #save}, as the shot of {@code constant}'s look {@code look}: the screen's own for null. */
    static void save(Path resources, String constant, String look, BufferedImage frame) {
        if (resources == null || constant == null || frame == null) return;
        try {
            Path file = file(resources, constant, look);
            Files.createDirectories(file.getParent());
            ImageIO.write(frame, "png", file.toFile());
        } catch (IOException | RuntimeException unwritten) {
            // A card without its shot shows its marker: nothing a tool call should fail for.
        }
    }

    /** As {@link #save}, only when {@code constant} has no shot yet. */
    static void saveIfMissing(Path resources, String constant, BufferedImage frame) {
        if (resources != null && constant != null && !Files.isRegularFile(file(resources, constant))) {
            save(resources, constant, frame);
        }
    }

    /** Removes {@code constant}'s shot and each of its looks': its screen left the map. */
    static void remove(Path resources, String constant) {
        if (resources == null || constant == null) return;
        try {
            Files.deleteIfExists(file(resources, constant));
            Path folder = resources.resolve(FOLDER);
            if (!Files.isDirectory(folder)) return;
            try (DirectoryStream<Path> looks = Files.newDirectoryStream(folder, constant + "@*.png")) {
                for (Path look : looks) Files.deleteIfExists(look);
            }
        } catch (IOException ignored) {
            // A stray shot names no constant: nothing reads it.
        }
    }
}
