package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.text.DigitSet;

import java.time.Duration;
import java.util.Objects;

/**
 * What a {@link Screen} shows to be read, never pressed: the gold, a timer, the hero's name. A field of its screen
 * beside its inputs, {@code public final Readout gold = Readout.digits(Zones.GOLD, Digits.GOLD);}, read by {@link
 * Navigate#read} and {@link Navigate#number} while the game shows that screen (2026-10-22).
 *
 * <p>An app's text is read from the screen's tree, exactly as the app states it: {@code
 * Readout.element(Elements.BATTERY_LEVEL)} (2026-10-31). Such an element tells its screen when it is there only:
 * {@link #marking}, or {@link #absent} when it never is (2026-11-02). A zone read tells nothing, since what it reads
 * changes.
 *
 * <p>A screen that shows one of several — hero N, shop item X — names which by its {@link #subject() subject}: {@code
 * Readout.text(Zones.HERO_NAME).subject()}, read by {@link Navigate#subject}.
 *
 * <p>A readout that feeds a fact names it (2026-11-06): {@code Readout.digits(Zones.GOLD, Digits.GOLD)
 * .into(Progress::gold).staleAfter(Duration.ofMinutes(30))}. A run that sees its screen while the fact is unknown or
 * stale reads it there; the fact's type says how, an {@code int} the digits shown, a {@code String} the text, an
 * {@code enum} the constant it names, a {@code boolean} whether anything shows at all.
 *
 * @param zone       where it is read, placed on the game as it shows; null: nowhere, never read
 * @param digits     the game font's digits it is read with; null to read it by OCR
 * @param isSubject  whether it names which one the screen shows
 * @param element    the node of the screen's tree whose text it reads; null to read a zone
 * @param isMarking  whether its element tells its screen: in the tree while the app shows it; never for a zone
 * @param isAbsent   whether its element is never in the tree while the app shows its screen; never for a zone
 * @param fact       the fact of a {@code @Facts} group it is read into; null when it feeds none
 * @param staleAfter how long a reading of {@link #fact} holds; null for {@link #READING_HOLDS}
 */
public record Readout(Zone zone, DigitSet digits, boolean isSubject, Element element, boolean isMarking,
                      boolean isAbsent, Params.Of<?, ?> fact, Duration staleAfter) implements ScreenPart {

    /** How long a reading holds when its readout says nothing: one hour. */
    public static final Duration READING_HOLDS = Duration.ofHours(1);

    public Readout {
        if (element == null || isAbsent) isMarking = false;
        if (element == null) isAbsent = false;
    }

    /** A readout of {@code zone}, {@code digits} and {@code element}, telling nothing and feeding no fact. */
    private Readout(Zone zone, DigitSet digits, Element element) {
        this(zone, digits, false, element, false, false, null, null);
    }

    /** The words {@code zone} shows, read by OCR. */
    public static Readout text(Zone zone) {
        return new Readout(zone, null, null);
    }

    /** The number {@code zone} shows, read with {@code digits}' pictures. */
    public static Readout digits(Zone zone, DigitSet digits) {
        return new Readout(zone, digits, null);
    }

    /** The text of {@code element} in the screen's tree, else its accessibility label. */
    public static Readout element(Element element) {
        if (element == null) throw new IllegalArgumentException("A readout of an element needs the element");
        return new Readout(null, null, element);
    }

    /** This readout, naming which one its screen shows: the hero's name on a hero's page. */
    @Link
    public Readout subject() {
        return new Readout(zone, digits, true, element, isMarking, isAbsent, fact, staleAfter);
    }

    /** This readout of an element, telling its screen: in the tree while the app shows it. A zone's stays as it is. */
    @Link
    public Readout marking() {
        return new Readout(zone, digits, isSubject, element, true, false, fact, staleAfter);
    }

    /** This readout of an element, telling its screen apart from one alike by never being in the tree there. */
    @Link
    public Readout absent() {
        return new Readout(zone, digits, isSubject, element, false, true, fact, staleAfter);
    }

    /** This readout, telling nothing of its screen: neither {@link #marking} nor {@link #absent}. */
    public Readout unmarked() {
        return new Readout(zone, digits, isSubject, element, false, false, fact, staleAfter);
    }

    /**
     * This readout, read into {@code fact}, a method of one of the bot's {@code @Facts} groups ({@code Progress::gold}),
     * whenever its screen shows while the fact is unknown or stale.
     */
    public <G, T> Readout into(Params.Of<G, T> fact) {
        return new Readout(zone, digits, isSubject, element, isMarking, isAbsent,
                Objects.requireNonNull(fact, "fact"), staleAfter);
    }

    /** This readout's reading, holding {@code holds} before the fact is unknown again; it means nothing without {@link #into}. */
    public Readout staleAfter(Duration holds) {
        if (holds != null && (holds.isNegative() || holds.isZero())) {
            throw new IllegalArgumentException("A reading holds a time above zero, not " + holds);
        }
        return new Readout(zone, digits, isSubject, element, isMarking, isAbsent, fact, holds);
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
