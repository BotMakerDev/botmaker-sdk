package com.botmaker.sdk.plugin.editors;

import com.botmaker.shared.opencv.ColorMatcher;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins in, a tolerance out: the smallest ΔE that takes every good pin, and the bad pins it cannot keep out. */
class ToleranceTeacherTest {

    private static final Color TARGET = new Color(200, 50, 50);

    @Test
    void theToleranceCoversTheFarthestGoodPinWithAMargin() {
        Color near = new Color(205, 52, 48);
        Color far = new Color(185, 60, 60);
        double farthest = ColorMatcher.deltaE(TARGET, far);

        ToleranceTeacher.Lesson lesson = ToleranceTeacher.teach(TARGET, List.of(near, far), List.of(), 3);

        assertEquals(Math.round((farthest + 0.5) * 10) / 10.0, lesson.deltaE(), 1e-9);
        assertTrue(lesson.conflicts().isEmpty());
    }

    @Test
    void aBadPinInsideTheToleranceIsAConflict() {
        Color good = new Color(170, 70, 70);
        Color bad = new Color(195, 55, 52);

        ToleranceTeacher.Lesson lesson = ToleranceTeacher.teach(TARGET, List.of(good), List.of(bad), 3);

        assertEquals(1, lesson.conflicts().size());
        assertEquals(bad, lesson.conflicts().getFirst().color());
    }

    @Test
    void withNoGoodPinsTheToleranceStaysWhereItWas() {
        ToleranceTeacher.Lesson onlyBad = ToleranceTeacher.teach(TARGET, List.of(), List.of(Color.BLUE), 12.5);
        ToleranceTeacher.Lesson none = ToleranceTeacher.teach(TARGET, List.of(), List.of(), 12.5);

        assertEquals(12.5, onlyBad.deltaE());
        assertTrue(onlyBad.conflicts().isEmpty(), "blue is far outside 12.5");
        assertEquals(12.5, none.deltaE());
    }

    /** With nothing to take, nothing is asked of the tolerance, so a red pin inside it is no conflict of the pins'. */
    @Test
    void redPinsAloneAreNeverAConflict() {
        Color near = new Color(195, 55, 52);

        ToleranceTeacher.Lesson lesson = ToleranceTeacher.teach(TARGET, List.of(), List.of(near), 12);

        assertEquals(12, lesson.deltaE());
        assertTrue(lesson.conflicts().isEmpty());
    }

    /** The margin gives way to a red pin just past the farthest green one: the two are separable, so separate them. */
    @Test
    void theMarginStopsShortOfARedPinJustBeyondTheGreenOnes() {
        Color good = new Color(185, 60, 60);
        double farthest = ColorMatcher.deltaE(TARGET, good);
        Color bad = nearestShadeBeyond(farthest + 0.25);
        double badDistance = ColorMatcher.deltaE(TARGET, bad);

        ToleranceTeacher.Lesson lesson = ToleranceTeacher.teach(TARGET, List.of(good), List.of(bad), 3);

        assertTrue(lesson.deltaE() >= farthest, "the green pin still matches: " + lesson.deltaE());
        assertTrue(lesson.deltaE() < badDistance, "the red pin is kept out: " + lesson.deltaE());
        assertTrue(lesson.conflicts().isEmpty());
    }

    /** A grey-shifted shade of the target whose ΔE lies in (lower, lower + 0.4). */
    private static Color nearestShadeBeyond(double lower) {
        for (int r = 200; r >= 0; r--) {
            for (int g = 50; g <= 120; g++) {
                Color c = new Color(r, g, 50);
                double d = ColorMatcher.deltaE(TARGET, c);
                if (d > lower && d < lower + 0.4) return c;
            }
        }
        throw new AssertionError("no shade just beyond " + lower);
    }

    @Test
    void theBoundaryColoursSitAtTheToleranceFromTheTarget() {
        Color[] edges = ToleranceTeacher.boundary(TARGET, 12);

        assertEquals(12, ColorMatcher.deltaE(TARGET, edges[0]), 1.0);
        assertEquals(12, ColorMatcher.deltaE(TARGET, edges[1]), 1.0);
        assertTrue(brightness(edges[0]) < brightness(TARGET) && brightness(edges[1]) > brightness(TARGET));
    }

    private static int brightness(Color c) {
        return c.getRed() + c.getGreen() + c.getBlue();
    }
}
