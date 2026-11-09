package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.text.DigitSet;

import java.time.Duration;
import java.util.Objects;

/**
 * What a {@link Screen} shows to be read, never pressed: the gold, a timer, the hero's name. A field of its screen
 * beside its inputs, {@code public final Readout gold = Readout.digits(Area.of(Pictures.GOLD_ICON, 1.2, 0, 3, 1), Digits.GOLD);}, read by {@link
 * Navigate#read} and {@link Navigate#number} while the game shows that screen (2026-10-22).
 *
 * <p>An app's text is read from the screen's tree, exactly as the app states it: {@code
 * Readout.element(Elements.BATTERY_LEVEL)} (2026-10-31). Such an element tells its screen when it is there only:
 * {@link #marking}, or {@link #absent} when it never is (2026-11-02). An area read tells nothing, since what it reads
 * changes. An area is measured from a picture beside the number ({@link Area}, 2026-11-09): an anchor not shown reads
 * nothing.
 *
 * <p>A screen that shows one of several — hero N, shop item X — names which by its {@link #subject() subject}: {@code
 * Readout.text(Area.of(Pictures.HERO_PORTRAIT, 0, 1.1, 2, 0.5)).subject()}, read by {@link Navigate#subject}.
 *
 * <p>A readout that feeds a fact names it (2026-11-06): {@code Readout.digits(Area.of(Pictures.GOLD_ICON, 1.2, 0, 3, 1), Digits.GOLD)
 * .into(Progress::gold).staleAfter(Duration.ofMinutes(30))}. A run that sees its screen while the fact is unknown or
 * stale reads it there; the fact's type says how, an {@code int} the digits shown, a {@code String} the text, an
 * {@code enum} the constant it names, a {@code boolean} whether anything shows at all.
 *
 * @param area       where it is read, measured from a picture the screen shows; null: nowhere, never read
 * @param digits     the game font's digits it is read with; null to read it by OCR
 * @param isSubject  whether it names which one the screen shows
 * @param element    the node of the screen's tree whose text it reads; null to read an area
 * @param isMarking  whether its element tells its screen: in the tree while the app shows it; never for an area
 * @param isAbsent   whether its element is never in the tree while the app shows its screen; never for an area
 * @param fact       the fact of a {@code @Facts} group it is read into; null when it feeds none
 * @param staleAfter how long a reading of {@link #fact} holds; null for {@link #READING_HOLDS}
 */
public record Readout(Area area, DigitSet digits, boolean isSubject, Element element, boolean isMarking,
                      boolean isAbsent, Params.Of<?, ?> fact, Duration staleAfter) implements ScreenPart {

    /** How long a reading holds when its readout says nothing: one hour. */
    public static final Duration READING_HOLDS = Duration.ofHours(1);

    public Readout {
        if (element == null || isAbsent) isMarking = false;
        if (element == null) isAbsent = false;
    }

    /** A readout of {@code area}, {@code digits} and {@code element}, telling nothing and feeding no fact. */
    private Readout(Area area, DigitSet digits, Element element) {
        this(area, digits, false, element, false, false, null, null);
    }

    /** The words {@code area} shows, read by OCR. */
    public static Readout text(Area area) {
        return new Readout(area, null, null);
    }

    /** The number {@code area} shows, read with {@code digits}' pictures. */
    public static Readout digits(Area area, DigitSet digits) {
        return new Readout(area, digits, null);
    }

    /** The text of {@code element} in the screen's tree, else its accessibility label. */
    public static Readout element(Element element) {
        if (element == null) throw new IllegalArgumentException("A readout of an element needs the element");
        return new Readout(null, null, element);
    }

    /** This readout, naming which one its screen shows: the hero's name on a hero's page. */
    @Link
    public Readout subject() {
        return new Readout(area, digits, true, element, isMarking, isAbsent, fact, staleAfter);
    }

    /** This readout of an element, telling its screen: in the tree while the app shows it. An area's stays as it is. */
    @Link
    public Readout marking() {
        return new Readout(area, digits, isSubject, element, true, false, fact, staleAfter);
    }

    /** This readout of an element, telling its screen apart from one alike by never being in the tree there. */
    @Link
    public Readout absent() {
        return new Readout(area, digits, isSubject, element, false, true, fact, staleAfter);
    }

    /** This readout, telling nothing of its screen: neither {@link #marking} nor {@link #absent}. */
    public Readout unmarked() {
        return new Readout(area, digits, isSubject, element, false, false, fact, staleAfter);
    }

    /**
     * This readout, read into {@code fact}, a method of one of the bot's {@code @Facts} groups ({@code Progress::gold}),
     * whenever its screen shows while the fact is unknown or stale.
     */
    public <G, T> Readout into(Params.Of<G, T> fact) {
        return new Readout(area, digits, isSubject, element, isMarking, isAbsent,
                Objects.requireNonNull(fact, "fact"), staleAfter);
    }

    /** This readout's reading, holding {@code holds} before the fact is unknown again; it means nothing without {@link #into}. */
    public Readout staleAfter(Duration holds) {
        if (holds != null && (holds.isNegative() || holds.isZero())) {
            throw new IllegalArgumentException("A reading holds a time above zero, not " + holds);
        }
        return new Readout(area, digits, isSubject, element, isMarking, isAbsent, fact, holds);
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
