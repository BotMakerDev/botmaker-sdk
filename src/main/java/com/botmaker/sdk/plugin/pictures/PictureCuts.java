package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A picture cut out of a frame the caller already holds, saved and declared as a {@code Pictures} constant.
 * This is what the overlay's Picture tab and the assistant's {@code crop_picture} do, without ✂ Capture
 * Templates' window.
 *
 * <p>It is the same save the capture tool makes: the PNG, its resolution sidecar, then
 * {@code Pictures.<NAME>} through {@link TemplateUses#declare}. Links no JavaFX.
 */
public final class PictureCuts {

    private PictureCuts() {}

    /** A saved picture: its name, the path {@code new ImageTemplate("…")} takes, and a note the host left. */
    public record Cut(String name, String path, Optional<String> note) {
    }

    /**
     * The part of {@code frame} under {@code region}, both in desktop pixels, with {@code frameArea} being where
     * the frame sits. Empty when the region misses the frame or has no size.
     */
    public static Optional<BufferedImage> crop(BufferedImage frame, Area frameArea, Area region) {
        if (frame == null || region == null || region.width() <= 0 || region.height() <= 0) return Optional.empty();
        int ox = frameArea == null ? 0 : frameArea.x();
        int oy = frameArea == null ? 0 : frameArea.y();
        int x = Math.max(0, region.x() - ox);
        int y = Math.max(0, region.y() - oy);
        int right = Math.min(frame.getWidth(), region.x() - ox + region.width());
        int bottom = Math.min(frame.getHeight(), region.y() - oy + region.height());
        if (right <= x || bottom <= y) return Optional.empty();
        BufferedImage copy = new BufferedImage(right - x, bottom - y, BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(frame.getSubimage(x, y, right - x, bottom - y), 0, 0, null);
        return Optional.of(copy);
    }

    /**
     * Saves {@code picture} as {@code typed} (sanitised the way every picture name is) and declares its
     * constant. On the FX thread, since declaring writes the bot's Java.
     *
     * @param frameWidth  the width of the frame it was cut from, for the resolution sidecar; 0 when unknown
     * @param frameHeight its height
     * @param title       the window it was cut from, or null
     * @throws IllegalArgumentException with the sentence to show: no name, a name taken or reserved, no project
     */
    public static Cut save(StudioServices services, BufferedImage picture, String typed, int frameWidth,
                           int frameHeight, String title) {
        Path resources = services == null ? null : services.resourcesDir();
        if (resources == null) throw new IllegalArgumentException("No project is open to save a picture into.");
        String name = TemplateLibrary.sanitizeName(typed);
        if (name.isEmpty()) throw new IllegalArgumentException("A picture needs a name.");
        if (TemplateLibrary.isReservedName(name)) throw new IllegalArgumentException("\"" + name + "\" is reserved.");
        if (TemplateLibrary.exists(resources, name)) {
            throw new IllegalArgumentException("A picture called \"" + name + "\" already exists.");
        }
        try {
            String path = TemplateLibrary.saveTemplate(resources, picture, name, frameWidth, frameHeight, title);
            return new Cut(name, path, TemplateUses.declare(services.pluginValues(), name));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + name + ".png: " + e.getMessage(), e);
        }
    }
}
