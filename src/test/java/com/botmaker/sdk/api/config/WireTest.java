package com.botmaker.sdk.api.config;

import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.Precision;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * One reader per stored type. Every one of these was a Java method held inside a Java string until now, which
 * is why none of it had a test: the only way to find out what {@code "1h30m"} meant to a bot was to build one
 * and run it.
 *
 * <p>Two properties are asserted throughout, and they are the whole contract. Each reader answers what the
 * text says, and each reader answers <em>something</em> for text that says nothing — a bot never fails to
 * start because of its own configuration file.
 */
class WireTest {

    // ---- the simple ones ------------------------------------------------------------------------------

    @Test
    void textIsKeptExactly() {
        assertEquals("  spaced  ", Wire.text("  spaced  "), "a trailing space may be the value");
        assertEquals("", Wire.text(null));
    }

    @Test
    void aTickIsTrueOnlyWhenItSaysSo() {
        assertEquals(true, Wire.flag("true"));
        assertEquals(true, Wire.flag(" TRUE "));
        assertEquals(false, Wire.flag("yes"));
        assertEquals(false, Wire.flag(""));
    }

    @Test
    void numbersRoundRatherThanGiveUp() {
        assertEquals(3, Wire.whole("3"));
        assertEquals(3, Wire.whole("3.0"), "a spinner or a hand edit produces this easily");
        assertEquals(4, Wire.whole("3.6"));
        assertEquals(0, Wire.whole("banana"));
        assertEquals(2.5, Wire.decimal("2.5"));
        assertEquals(0.0, Wire.decimal(""));
        assertEquals(0.0, Wire.decimal("Infinity"), "not finite is not a number anyone meant");
    }

    @Test
    void aLetterIsTheFirstOneStored() {
        assertEquals('q', Wire.letter("qwerty"));
        assertEquals('a', Wire.letter(""));
        assertEquals('a', Wire.letter(null));
    }

    @Test
    void datesAndTimesFallBackToAFixedPoint() {
        assertEquals(LocalDate.of(2026, 8, 24), Wire.date("2026-08-24"));
        assertEquals(LocalDate.of(2000, 1, 1), Wire.date("last tuesday"));
        assertEquals(LocalTime.of(7, 30), Wire.time("07:30"));
        assertEquals(LocalTime.MIDNIGHT, Wire.time(""));
    }

    @Test
    void anUnreadableColourIsWhite() {
        assertEquals(new Color(0x336699), Wire.color("#336699"));
        assertEquals(Color.WHITE, Wire.color("cornflower"));
    }

    @Test
    void aTemplateNameBecomesAPathUnderTheProjectsImages() {
        assertEquals(Wire.IMAGE_PREFIX + "ore.png", Wire.template("ore").filePath());
    }

    // ---- durations: the grammar that used to exist twice ----------------------------------------------

    @Test
    void everyUnitMeansWhatItSays() {
        assertEquals(Duration.ofMillis(250), Wire.duration("250ms"));
        assertEquals(Duration.ofSeconds(90), Wire.duration("90s"));
        assertEquals(Duration.ofMinutes(5), Wire.duration("5m"));
        assertEquals(Duration.ofHours(1), Wire.duration("1h"));
        assertEquals(Duration.ofMinutes(90), Wire.duration("1h30m"));
    }

    @Test
    void msIsReadBeforeTheMItStartsWith() {
        assertEquals(Duration.ofMillis(500), Wire.duration("500ms"));
        assertEquals(Duration.ofMillis(30_500), Wire.duration("500ms30s"));
        assertEquals(Duration.ofMinutes(500), Wire.duration("500m"), "the same digits, 60,000x apart");
    }

    @Test
    void spacingAndCaseAreIgnored() {
        assertEquals(Duration.ofMinutes(90), Wire.duration("1H 30M"));
        assertEquals(Duration.ofMinutes(90), Wire.duration("  1h30m  "));
    }

    @Test
    void aBareNumberIsMilliseconds() {
        assertEquals(Duration.ofMillis(500), Wire.duration("500"));
    }

    @Test
    void anythingItCannotReadIsZero() {
        for (String text : List.of("", "   ", "abc", "s", "10x", "-5s", "99999999999999s")) {
            assertEquals(Duration.ZERO, Wire.duration(text), text);
        }
        assertEquals(Duration.ZERO, Wire.duration(null));
    }

    @Test
    void aTrailingNumberWithNoUnitIsStillMilliseconds() {
        assertEquals(Duration.ofMillis(300_003), Wire.duration("5m3"),
                "generous by design: the bare-number rule applies wherever the text runs out");
    }

    // ---- the closed sets ------------------------------------------------------------------------------

    @Test
    void aChoiceNoLongerOfferedFallsBackToTheFirstOne() {
        assertEquals(Key.ESCAPE, Wire.key("escape"));
        assertEquals(Key.values()[0], Wire.key("META_GALACTIC"));
        assertEquals(MouseButton.values()[0], Wire.mouseButton(""));
        assertEquals(Direction.values()[0], Wire.direction("sideways"));
        assertNotNull(Wire.direction(null));
    }

    @Test
    void aChoiceIsMatchedWhateverItsCase() {
        assertEquals(Wire.key("ESCAPE"), Wire.key(" escape "));
    }

    // ---- the records ----------------------------------------------------------------------------------

    @Test
    void geometryReadsComponentwiseAndMissingComponentsAreZero() {
        assertEquals(new Point(10, 20), Wire.point("10,20"));
        assertEquals(new Point(10, 0), Wire.point("10"));
        assertEquals(new Point(0, 0), Wire.point("banana"));
        assertEquals(new Size(64, 48), Wire.size("64,48"));
        assertEquals(new Rect(1, 2, 3, 4), Wire.area("1,2,3,4"));
        assertEquals(new Rect(1, 2, 0, 0), Wire.area("1,2"));
    }

    @Test
    void precisionIsClampedToWhatItsConstructorAccepts() {
        assertEquals(new Precision(5.0, 8, 2), Wire.precision("5.0,8,2"));
        // Its constructor throws on a negative tolerance or an area below one, and a stored value must never
        // be able to reach that — the bot would die before its first line.
        assertEquals(new Precision(0.0, 1, 0), Wire.precision("-3,-9,-1"));
        assertEquals(new Precision(12.0, 1, 0), Wire.precision(""));
    }

    // ---- lists ----------------------------------------------------------------------------------------

    @Test
    void aListItemGoesThroughTheSameReaderALoneValueDoes() {
        assertEquals(List.of(), Wire.many("NOTHING-STORED-THIS", Wire::key),
                "a key nobody stored is an empty list, not a null one");
        assertEquals("", Wire.one("NOTHING-STORED-THIS"));
    }
}
