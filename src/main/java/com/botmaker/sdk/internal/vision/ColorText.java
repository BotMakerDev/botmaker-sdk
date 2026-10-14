package com.botmaker.sdk.internal.vision;

import java.awt.Color;
import java.util.Locale;
import java.util.Optional;

/** A colour as a person writes it and reads it: {@code #RRGGBB}, or {@code r,g,b}. What {@code Pixel} searches for. */
public final class ColorText {

    private ColorText() {}

    /** {@code #RRGGBB}, upper case. */
    public static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** {@code #RRGGBB}, {@code RRGGBB} or {@code r,g,b}; empty for anything else. */
    public static Optional<Color> parse(String typed) {
        if (typed == null) return Optional.empty();
        String text = typed.trim();
        try {
            if (text.contains(",")) {
                String[] parts = text.split(",");
                if (parts.length != 3) return Optional.empty();
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                if ((r | g | b) < 0 || r > 255 || g > 255 || b > 255) return Optional.empty();
                return Optional.of(new Color(r, g, b));
            }
            String digits = text.startsWith("#") ? text.substring(1) : text;
            if (digits.length() != 6) return Optional.empty();
            return Optional.of(new Color(Integer.parseInt(digits, 16)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
