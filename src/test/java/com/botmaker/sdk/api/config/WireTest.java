package com.botmaker.sdk.api.config;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bot-facing reader, over {@code src/test/resources/activities.json} — which sits exactly where a
 * generated bot's own file sits, so these tests exercise the classpath lookup a bot really performs.
 *
 * <p>What is being defended is the trade that made this class replace a generated {@code Parameters} of
 * {@code public static final} fields: the values keep their names, and a misspelling stops being a compile
 * error. That is only acceptable while <b>every</b> misspelling has a defined, harmless answer, which is what
 * most of these tests check.
 */
class WireTest {

    @Test
    void readsAValueByTheNameTheEditorGaveIt() {
        assertEquals(20, Wire.whole("minHealth"));
        assertEquals("hello", Wire.text("greeting"));
        assertEquals(Duration.ofSeconds(90), Wire.duration("restBetween"));
        assertEquals(10, Wire.point("anchor").x());
        assertEquals(20, Wire.point("anchor").y());
    }

    @Test
    void readsAnActivitysSwitchSeparatelyFromAnyVariable() {
        // Two different lists and two different questions: whether an activity runs is a property of the
        // activity, not of a variable somebody declared.
        assertTrue(Wire.enabled("Mining"));
        assertFalse(Wire.enabled("Fishing"));
        assertFalse(Wire.declares("Mining"));
    }

    @Test
    void readsAListWholeAndItsFirstItemAlone() {
        assertEquals(List.of("ore", "gem"), Wire.many("targets"));
        assertEquals("ore", Wire.one("targets"));
    }

    @Test
    void handsBackTheStoredTextForATypeItHasNoReaderFor() {
        // The escape hatch: a value another plugin owns is still text, and one() is all a caller needs.
        assertEquals("1m30s", Wire.one("restBetween"));
    }

    // ---- the cost of losing the compiler ----------------------------------------------------------------

    @Test
    void aMisspelledNameAnswersItsFallbackRatherThanFailing() {
        assertEquals(0, Wire.whole("minHelath"));
        assertEquals("", Wire.text("greetnig"));
        assertEquals(Duration.ZERO, Wire.duration("restBteween"));
        assertEquals(List.of(), Wire.many("targts"));
    }

    @Test
    void tellsAnUnsetValueApartFromAMisspelledOne() {
        assertTrue(Wire.declares("unset"));
        assertFalse(Wire.declares("unsett"));
        assertEquals("", Wire.text("unset"));
    }

    @Test
    void aReaderOfTheWrongTypeAnswersItsOwnFallback() {
        // Nothing here consults the type the editor stored — which reader is right for a name is the
        // caller's to know, and getting it wrong must still be harmless.
        assertEquals(0, Wire.whole("greeting"));
        assertFalse(Wire.flag("minHealth"));
        assertEquals(Color.WHITE, Wire.color("greeting"));
    }

    @Test
    void everyReaderAnswersForANameThatIsNotThere() {
        String absent = "nothingIsCalledThis";

        assertEquals("", Wire.text(absent));
        assertFalse(Wire.flag(absent));
        assertEquals(0, Wire.whole(absent));
        assertEquals(0.0, Wire.decimal(absent));
        assertEquals('a', Wire.letter(absent));
        assertEquals(2000, Wire.date(absent).getYear());
        assertEquals(0, Wire.time(absent).getHour());
        assertEquals(Duration.ZERO, Wire.duration(absent));
        assertEquals(Color.WHITE, Wire.color(absent));
        assertEquals(0, Wire.point(absent).x());
        assertEquals(0, Wire.size(absent).width());
        assertEquals(0, Wire.area(absent).width());
        // The enums fall back to their own first constant, and the records to a clamped default; asserting
        // only that none of them throws is the whole point.
        assertTrue(Wire.direction(absent) != null);
        assertTrue(Wire.key(absent) != null);
        assertTrue(Wire.mouseButton(absent) != null);
        assertTrue(Wire.precision(absent) != null);
        assertTrue(Wire.template(absent) != null);
    }

    @Test
    void listsTheNamesItCanAnswerFor() {
        assertTrue(Wire.names().contains("minHealth"));
        assertFalse(Wire.names().contains("Mining"));
    }
}
