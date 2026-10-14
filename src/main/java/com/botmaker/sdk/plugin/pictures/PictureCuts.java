package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.geometry.Rect;

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
 * <p>It is the same save the capture tool makes: the PNG, then {@code Pictures.<NAME>} written with where it was
 * cut through {@link TemplateUses#cut}. Links no JavaFX.
 */
public final class PictureCuts {

    private PictureCuts() {}

    /** A saved picture: its name, the path {@code new ImageTemplate("…")} takes, and a note the host left. */
    public record Cut(String name, String path, Optional<String> note) {
    }

    /** A picture cut out of a frame, and where in that frame. */
    public record Piece(BufferedImage image, Rect place) {
    }

    /**
     * The part of {@code frame} under {@code region}, both in desktop pixels, with {@code frameArea} being where
     * the frame sits, and the box it took in the frame's own pixels. Empty when the region misses the frame or has
     * no size.
     */
    public static Optional<Piece> crop(BufferedImage frame, Area frameArea, Area region) {
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
        return Optional.of(new Piece(copy, new Rect(x, y, right - x, bottom - y)));
    }

    /**
     * {@code piece} with the part under {@code ignore}, in the frame's pixels as its place is, made transparent:
     * the matcher reads a transparent pixel as one to skip, so a part that changes from copy to copy (a timer's
     * digits on a map pin) no longer keeps the copies apart. Unchanged when {@code ignore} misses it.
     */
    public static Piece masked(Piece piece, Rect ignore) {
        Rect place = piece.place();
        int left = Math.max(ignore.x(), place.x()) - place.x();
        int top = Math.max(ignore.y(), place.y()) - place.y();
        int right = Math.min(ignore.x() + ignore.width(), place.x() + place.width()) - place.x();
        int bottom = Math.min(ignore.y() + ignore.height(), place.y() + place.height()) - place.y();
        if (right <= left || bottom <= top) return piece;
        BufferedImage copy = new BufferedImage(place.width(), place.height(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(piece.image(), 0, 0, null);
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) copy.setRGB(x, y, copy.getRGB(x, y) & 0x00FFFFFF);
        }
        return new Piece(copy, place);
    }

    /**
     * Saves {@code piece} as {@code typed} (sanitised the way every picture name is) and declares its constant,
     * cut at its place in {@code frame}. On the FX thread, since declaring writes the bot's Java.
     *
     * @throws IllegalArgumentException with the sentence to show: no name, a name taken or reserved, no project
     */
    public static Cut save(StudioServices services, Piece piece, String typed, BufferedImage frame) {
        Path resources = services == null ? null : services.resourcesDir();
        if (resources == null) throw new IllegalArgumentException("No project is open to save a picture into.");
        String name = TemplateLibrary.sanitizeName(typed);
        if (name.isEmpty()) throw new IllegalArgumentException("A picture needs a name.");
        if (TemplateLibrary.isReservedName(name)) throw new IllegalArgumentException("\"" + name + "\" is reserved.");
        if (TemplateLibrary.exists(resources, name)) {
            throw new IllegalArgumentException("A picture called \"" + name + "\" already exists.");
        }
        try {
            String path = TemplateLibrary.saveTemplate(resources, piece.image(), name);
            return new Cut(name, path, TemplateUses.cut(services, name,
                    FrameCut.of(frame.getWidth(), frame.getHeight(), piece.place())));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + name + ".png: " + e.getMessage(), e);
        }
    }
}
