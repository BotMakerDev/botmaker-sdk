package com.botmaker.sdk.api.text;

import com.botmaker.sdk.api.geometry.Size;

import java.util.Objects;

/**
 * A game font's digits as pictures — {@code new DigitSet("src/main/resources/digits/gold")}, a folder holding
 * {@code 0.png} to {@code 9.png}, one per digit, cut from the game (2026-10-11). {@link Text#readDigits} reads a
 * number with it where OCR cannot read the game's font.
 *
 * <p>A bot's sets are the {@code Digits} constants; the assistant's {@code save_digits} cuts the digits of a number
 * it sees on screen into the folder and writes the constant. Like a picture, the digits are scaled when the game
 * shows another size than the bot's screen, and a set cut from a frame of another size says so ({@link #in}).
 */
public final class DigitSet {

    private final String folder;
    /** The frame its digits were cut from when that is not the bot's screen size; null when it is. */
    private final Size frame;

    /** The digits in {@code folder}, cut at the bot's screen size. */
    public DigitSet(String folder) {
        this(folder, null);
    }

    private DigitSet(String folder, Size frame) {
        if (folder == null || folder.isBlank()) throw new IllegalArgumentException("A digit set needs its folder");
        this.folder = folder;
        this.frame = frame == null || frame.empty() ? null : frame;
    }

    /**
     * This set, cut from a frame of {@code frame} rather than the bot's screen size ({@code Sdk.screen()}). Written
     * only when the two differ.
     */
    public DigitSet in(Size frame) {
        return new DigitSet(folder, frame);
    }

    /** The folder holding {@code 0.png} to {@code 9.png}. */
    public String folder() {
        return folder;
    }

    /** The frame its digits were cut from when that is not the bot's screen size, else null. */
    public Size frame() {
        return frame;
    }

    /** The folder's last part: {@code gold}. */
    public String name() {
        String trimmed = folder.endsWith("/") ? folder.substring(0, folder.length() - 1) : folder;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DigitSet other && folder.equals(other.folder) && Objects.equals(frame, other.frame);
    }

    @Override
    public int hashCode() {
        return Objects.hash(folder, frame);
    }

    @Override
    public String toString() {
        return "DigitSet{" + folder + (frame == null ? "" : ", in=" + frame) + "}";
    }
}
