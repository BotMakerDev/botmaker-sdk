package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.text.DigitSet;

/**
 * What a {@link Screen} shows to be read, never pressed: the gold, a timer, the hero's name. A field of its screen
 * beside its inputs, {@code public final Readout gold = Readout.digits(Zones.GOLD, Digits.GOLD);}, read by {@link
 * Navigate#read} and {@link Navigate#number} while the game shows that screen (2026-10-22).
 *
 * <p>An app's text is read from the screen's tree, exactly as the app states it: {@code
 * Readout.element(Elements.BATTERY_LEVEL)} (2026-10-31).
 *
 * <p>A screen that shows one of several — hero N, shop item X — names which by its {@link #subject() subject}: {@code
 * Readout.text(Zones.HERO_NAME).subject()}, read by {@link Navigate#subject}.
 *
 * @param zone      where it is read, placed on the game as it shows; null: nowhere, never read
 * @param digits    the game font's digits it is read with; null to read it by OCR
 * @param isSubject whether it names which one the screen shows
 * @param element   the node of the screen's tree whose text it reads; null to read a zone
 */
public record Readout(Zone zone, DigitSet digits, boolean isSubject, Element element) implements ScreenPart {

    /** The words {@code zone} shows, read by OCR. */
    public static Readout text(Zone zone) {
        return new Readout(zone, null, false, null);
    }

    /** The number {@code zone} shows, read with {@code digits}' pictures. */
    public static Readout digits(Zone zone, DigitSet digits) {
        return new Readout(zone, digits, false, null);
    }

    /** The text of {@code element} in the screen's tree, else its accessibility label. */
    public static Readout element(Element element) {
        if (element == null) throw new IllegalArgumentException("A readout of an element needs the element");
        return new Readout(null, null, false, element);
    }

    /** This readout, naming which one its screen shows: the hero's name on a hero's page. */
    @Link
    public Readout subject() {
        return new Readout(zone, digits, true, element);
    }

    /** Whether it reads a number by digit pictures, not words by OCR. */
    public boolean isNumber() {
        return digits != null;
    }

    /**
     * What a window or a tool calls it: {@code "digits gold"}, {@code "text"} or {@code "element \"Battery\""}, then
     * {@code ", subject"}.
     */
    public String label() {
        String kind = element != null ? "element " + element.shown()
                : digits == null ? "text" : "digits " + digits.name();
        return kind + (isSubject ? ", subject" : "");
    }
}
