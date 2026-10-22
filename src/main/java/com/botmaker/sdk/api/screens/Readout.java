package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.text.DigitSet;

/**
 * What a {@link Screen} shows to be read, never pressed: the gold, a timer, the hero's name. A field of its screen
 * beside its inputs, {@code public final Readout gold = Readout.digits(Zones.GOLD, Digits.GOLD);}, read by {@link
 * Navigate#read} and {@link Navigate#number} while the game shows that screen (2026-10-22).
 *
 * <p>A screen that shows one of several — hero N, shop item X — names which by its {@link #subject() subject}: {@code
 * Readout.text(Zones.HERO_NAME).subject()}, read by {@link Navigate#subject}.
 *
 * @param zone      where it is read, placed on the game as it shows; null: nowhere, never read
 * @param digits    the game font's digits it is read with; null to read it by OCR
 * @param isSubject whether it names which one the screen shows
 */
public record Readout(Zone zone, DigitSet digits, boolean isSubject) implements ScreenPart {

    /** The words {@code zone} shows, read by OCR. */
    public static Readout text(Zone zone) {
        return new Readout(zone, null, false);
    }

    /** The number {@code zone} shows, read with {@code digits}' pictures. */
    public static Readout digits(Zone zone, DigitSet digits) {
        return new Readout(zone, digits, false);
    }

    /** This readout, naming which one its screen shows: the hero's name on a hero's page. */
    public Readout subject() {
        return new Readout(zone, digits, true);
    }

    /** Whether it reads a number by digit pictures, not words by OCR. */
    public boolean isNumber() {
        return digits != null;
    }

    /** What a window or a tool calls it: {@code "digits gold"}, {@code "text"}, then {@code ", subject"}. */
    public String label() {
        return (digits == null ? "text" : "digits " + digits.name()) + (isSubject ? ", subject" : "");
    }
}
