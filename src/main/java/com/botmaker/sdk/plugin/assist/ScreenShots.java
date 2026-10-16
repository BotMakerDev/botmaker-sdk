package com.botmaker.sdk.plugin.assist;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * A picture of each whole screen of the map, for 🗺 Screens' cards and side panel: the marker alone says which screen
 * it is but not what is on it (the user's call, 2026-10-15). Kept at the capture's own size, so a picture cut on that
 * frame is boxed on it pixel for pixel (the user's call, 2026-10-15, after a 480-pixel shot read too blurred). One file
 * a screen, {@code screens/<CONSTANT>.png} in the project's resources, beside the pictures and never one of them:
 * nothing matches it, and a missing one only leaves the card showing its marker. try_input saves a draft when it opens a
 * screen, and the cut of the screen's marker replaces it with the frame it was cut from; which_screen saves one when it
 * sees a screen that has none. Any other shot is taken only on a frame the screen's marker shows on (2026-10-16).
 */
public final class ScreenShots {

    /** The folder in the project's resources. */
    static final String FOLDER = "screens";

    private ScreenShots() {}

    /** How wide the shot in {@code file} is, read off its header; 0 when there is none. */
    public static int width(Path file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) return 0;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return 0;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return reader.getWidth(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException unread) {
            return 0;
        }
    }

    /** Where {@code constant}'s shot is in {@code resources}, whether or not it exists yet. */
    public static Path file(Path resources, String constant) {
        return resources.resolve(FOLDER).resolve(constant + ".png");
    }

    /** Saves {@code frame} as {@code constant}'s shot, over the one it had; a shot that cannot be written is left. */
    static void save(Path resources, String constant, BufferedImage frame) {
        if (resources == null || constant == null || frame == null) return;
        try {
            Path file = file(resources, constant);
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

    /** Removes {@code constant}'s shot: its screen left the map. */
    static void remove(Path resources, String constant) {
        if (resources == null || constant == null) return;
        try {
            Files.deleteIfExists(file(resources, constant));
        } catch (IOException ignored) {
            // A stray shot names no constant: nothing reads it.
        }
    }
}
