package com.botmaker.sdk.internal.vision;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How the digits found on a line spell a number. */
class DigitReaderTest {

    private static DigitReader.Hit hit(int digit, int x, double score) {
        return new DigitReader.Hit(digit, x, 10, 12, 20, score);
    }

    @Test
    void digitsAreReadLeftToRightWhateverOrderTheyWereFoundIn() {
        assertEquals(OptionalLong.of(4071), DigitReader.assemble(List.of(hit(7, 26, 0.95), hit(4, 2, 0.97),
                hit(1, 38, 0.93), hit(0, 14, 0.96)), "gold"));
    }

    @Test
    void whereTwoDigitsStandOnOnePlaceTheSurerStays() {
        // A 1 found inside the 4's stroke scores lower than the 4 itself.
        assertEquals(OptionalLong.of(42), DigitReader.assemble(List.of(hit(4, 2, 0.97), hit(1, 5, 0.86),
                hit(2, 14, 0.95)), "gold"));
        // Nearly as sure, the narrow 1 still loses to the whole 4 it is a stroke of.
        DigitReader.Hit stroke = new DigitReader.Hit(1, 7, 10, 5, 20, 0.97);
        assertEquals(OptionalLong.of(4), DigitReader.assemble(List.of(stroke, hit(4, 2, 0.95)), "gold"));
    }

    @Test
    void aGameShowingTwiceTheBotsSizeScalesTheDigitsTwice() {
        assertEquals(2.0, DigitReader.scale(new com.botmaker.sdk.api.geometry.Size(640, 360),
                new com.botmaker.sdk.api.geometry.Size(1280, 720)));
        assertEquals(1.0, DigitReader.scale(null, new com.botmaker.sdk.api.geometry.Size(1280, 720)));
    }

    @Test
    void tinyDigitsAreEnlargedUpToThreeTimes() {
        assertEquals(1, DigitReader.enlargement(20));
        assertEquals(1, DigitReader.enlargement(12));
        assertEquals(2, DigitReader.enlargement(8));
        assertEquals(3, DigitReader.enlargement(5));
        assertEquals(3, DigitReader.enlargement(2), "never more than three times");
        assertEquals(1, DigitReader.enlargement(0), "no digit, nothing to enlarge");
    }

    @Test
    void aGapWiderThanADigitIsACharacterTheSetLacks() {
        // 1 ? 5: the 7 between them is not in the set, so the number is not read as 15.
        assertEquals(OptionalLong.empty(), DigitReader.assemble(List.of(hit(1, 2, 0.95), hit(5, 26, 0.95)), "gold"));
        // A narrow separator is no digit: 1,5 reads 15.
        assertEquals(OptionalLong.of(15), DigitReader.assemble(List.of(hit(1, 2, 0.95), hit(5, 18, 0.95)), "gold"));
    }

    @Test
    void onlyTheSurestDigitsLineIsRead() {
        DigitReader.Hit below = new DigitReader.Hit(9, 2, 60, 12, 20, 0.9);
        assertEquals(OptionalLong.of(3), DigitReader.assemble(List.of(hit(3, 2, 0.97), below), "gold"));
    }

    @Test
    void nothingFoundOrTooLongIsNoNumber() {
        assertEquals(OptionalLong.empty(), DigitReader.assemble(List.of(), "gold"));
        List<DigitReader.Hit> nineteen = java.util.stream.IntStream.range(0, 19).mapToObj(i -> hit(9, i * 12, 0.9))
                .toList();
        assertEquals(OptionalLong.empty(), DigitReader.assemble(nineteen, "gold"));
    }
}
