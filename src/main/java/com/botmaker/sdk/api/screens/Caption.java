package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.text.Words;

/**
 * Words a {@link Screen} shows, read inside one of the bot's {@code Zones}: {@code Caption.of(Texts.SETTINGS,
 * Zones.TITLE)}. A screen is told by them as by a picture ({@link Setup#texts}), and a {@link TextButton} is clicked on
 * them (2026-10-22).
 *
 * <p>The text read there matches when a line of it is the words, whatever its case and the spaces or marks at its ends,
 * forgiving a misread letter in five to eight and two in longer words: {@code Claim} never matches {@code Claimed}, nor
 * {@code OK} {@code O} (the user's call, 2026-10-22).
 *
 * @param words the words, one of the bot's {@code Texts}; null: none, never read
 * @param in    where they are read, placed on the game as it shows; text outside it is never read; null: nowhere
 */
public record Caption(Words words, Zone in) {

    /** {@code words}, read inside {@code in}. */
    public static Caption of(Words words, Zone in) {
        return new Caption(words, in);
    }

    /** The words as a window or a tool names them: {@code "Settings"}. */
    public String said() {
        return words == null ? "?" : "\"" + words.text() + "\"";
    }
}
