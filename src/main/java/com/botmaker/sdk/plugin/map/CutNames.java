package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.plugin.map.CutForm.Kind;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * The name the cut form suggests for a cut, so a cut is saved without typing one (2026-10-22: Firestone's map needed
 * some 240 names typed by hand): the words OCR reads on it ({@code Claim all!} → {@code claim_all}), else
 * {@code <screen>_<kind>_<n>} ({@code home_button_3}); lowercase with underscores, as a picture's name is, and taken by
 * no picture, zone or other cut.
 */
final class CutNames {

    /** The longest name made from words: past it, a sentence read off a panel. */
    static final int LONGEST = 32;

    private CutNames() {}

    /** The name for a cut of {@code kind} on {@code screen} (null: none) that reads {@code words} (null: none). */
    static String suggest(String words, String screen, Kind kind, Set<String> taken) {
        String said = fromWords(words);
        if (!said.isEmpty()) return unique(said, taken);
        String base = (screen == null || screen.isBlank() ? "cut" : slug(screen)) + "_" + kind.slug();
        for (int n = 1; ; n++) {
            if (!taken.contains(base + "_" + n)) return base + "_" + n;
        }
    }

    /**
     * {@code words}' first line as a name, or empty when it gives none of two letters or more: a digit, a mark or an
     * icon OCR misread is no name. Accents go, and digits a name would start with.
     */
    static String fromWords(String words) {
        if (words == null) return "";
        String line = words.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        String name = slug(line).replaceFirst("^[0-9_]+", "");
        if (name.length() > LONGEST) {
            int cut = name.lastIndexOf('_', LONGEST);
            name = name.substring(0, cut > 0 ? cut : LONGEST);
        }
        return name.chars().filter(c -> c >= 'a' && c <= 'z').count() >= 2 ? name : "";
    }

    /** {@code name}, or {@code name_2}, {@code name_3}… the first not {@code taken}. */
    static String unique(String name, Set<String> taken) {
        if (!taken.contains(name)) return name;
        for (int n = 2; ; n++) {
            if (!taken.contains(name + "_" + n)) return name + "_" + n;
        }
    }

    /** Lowercase letters and digits, one underscore between words, none at either end. */
    private static String slug(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
