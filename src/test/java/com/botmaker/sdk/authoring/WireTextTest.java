package com.botmaker.sdk.authoring;

import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stored-text grammar: one reader per type, and the one writer.
 *
 * <p>Two properties, and everything here is one or the other. <b>Nothing throws</b> — a value read out of a
 * file may be blank, may be a spelling an older editor wrote, or may name an option that no longer exists,
 * and every one of those has to open rather than fail. And <b>reading is generous while writing is
 * canonical</b>, so a person can type {@code "90 s"} and the file that comes back always says {@code 1m30s}.
 *
 * <p>These moved here from Studio with the grammar itself. They matter more than they did there: the
 * generator reads the same text when it bakes a value into {@code Parameters}, so a disagreement about what
 * {@code "1h30m"} means is now a disagreement about what a bot does.
 */
class WireTextTest {

    @Test
    void everyReaderAnswersItsTypesDefaultRatherThanThrowing() {
        for (String nothing : new String[]{null, "", "   ", "not a value at all"}) {
            assertEquals(false, WireText.flag(nothing));
            assertEquals(0, WireText.whole(nothing));
            assertEquals(0.0, WireText.decimal(nothing));
            assertEquals(Duration.ZERO, WireText.duration(nothing));
            assertEquals(Color.WHITE, WireText.color(nothing));
            assertEquals(LocalTime.MIDNIGHT, WireText.time(nothing));
            assertEquals(LocalDate.of(2000, 1, 1), WireText.date(nothing));
            assertEquals(new Point(0, 0), WireText.point(nothing));
            assertEquals(new Size(0, 0), WireText.size(nothing));
            assertEquals(new Rect(0, 0, 0, 0), WireText.area(nothing));
            assertEquals(Key.values()[0], WireText.key(nothing));
            assertEquals(MouseButton.values()[0], WireText.mouseButton(nothing));
            assertEquals(Direction.values()[0], WireText.direction(nothing));
        }
    }

    @Test
    void theOrdinaryReadings() {
        assertEquals(true, WireText.flag("true"));
        assertEquals(42, WireText.whole("42"));
        assertEquals(0.75, WireText.decimal("0.75"));
        assertEquals('x', WireText.letter("xyz"), "the first character, not a refusal");
        assertEquals(LocalDate.of(2026, 8, 25), WireText.date("2026-08-25"));
        assertEquals(LocalTime.of(7, 30, 15), WireText.time("07:30:15"));
        assertEquals(new Color(0x33, 0x66, 0xFF), WireText.color("#3366FF"));
        assertEquals(new Point(3, 4), WireText.point("3,4"));
        assertEquals(new Size(800, 600), WireText.size("800,600"));
        assertEquals(new Rect(1, 2, 3, 4), WireText.area("1,2,3,4"));
        assertEquals(Key.SPACE, WireText.key("SPACE"));
        assertEquals(Direction.SOUTH, WireText.direction("south"), "case is not the user's problem");
    }

    /** A template names a file in the project's own images directory, never a path the user typed. */
    @Test
    void aTemplateIsAPathUnderTheProjectsImages() {
        assertEquals(WireText.IMAGE_PREFIX + "ore.png", WireText.templatePath("ore"));
        assertEquals(WireText.IMAGE_PREFIX + "ore.png", WireText.templatePath("  ore  "));
    }

    /** Any spelling of the same length reads the same, which is what makes the grammar worth having. */
    @ParameterizedTest
    @ValueSource(strings = {"90s", "1m30s", "  90 S ", "90000ms", "90000", "30s1m"})
    void everySpellingOfNinetySecondsReadsTheSame(String text) {
        assertEquals(Duration.ofSeconds(90), WireText.duration(text));
    }

    /** Nothing usable is zero, not an exception and not a partial reading. */
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc", "s", "10x", "-5s", "m30"})
    void anythingUnreadableIsZero(String text) {
        assertEquals(Duration.ZERO, WireText.duration(text));
    }

    @Test
    void spellingIsCanonical() {
        assertEquals("0s", WireText.spellDuration(0));
        assertEquals("0s", WireText.spellDuration(-1));
        assertEquals("250ms", WireText.spellDuration(250));
        assertEquals("1m30s", WireText.spellDuration(90_000));
        assertEquals("1h30m", WireText.spellDuration(5_400_000));
        assertEquals("1h1m1s1ms", WireText.spellDuration(3_661_001));
    }

    /**
     * The pair, which is the property a user actually sees: however it was typed, what lands in the file is
     * stable, so a diff never churns on spacing.
     */
    @ParameterizedTest
    @ValueSource(strings = {"90s", "1m30s", "  90 S ", "90000ms", "90000"})
    void readingThenSpellingIsStable(String text) {
        String once = WireText.spellDuration(WireText.duration(text).toMillis());
        assertEquals("1m30s", once);
        assertEquals(once, WireText.spellDuration(WireText.duration(once).toMillis()));
    }
}
