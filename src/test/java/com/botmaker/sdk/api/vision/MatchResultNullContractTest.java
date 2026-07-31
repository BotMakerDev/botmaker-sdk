package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.Rect;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <b>The one rule every bot is written against: {@code if (m.isFound())} and then dereference.</b>
 *
 * <p>{@link MatchResult} has seven point-ish accessors and all seven return {@code null} when the match failed.
 * That is the whole contract, it is nowhere stated in a signature, and it is what a generated bot's very first
 * line of vision code depends on. Get it wrong in either direction and the failure is a
 * {@link NullPointerException} in *user* code — a bot author looking at a stack trace inside a template they
 * did not write.
 *
 * <p>Both directions matter, which is why each is asserted separately:
 * <ul>
 *   <li><b>not found ⇒ null</b>. If an accessor started returning a zero-valued {@link Point} instead, every
 *       {@code if (m.isFound())} guard in every bot would keep compiling and the bot would click (0,0) —
 *       silently, forever. A null is loud; a zero is not.</li>
 *   <li><b>found ⇒ non-null</b>. The guard is only worth writing if passing it means something.</li>
 * </ul>
 *
 * <p>This <b>gates SD5 and SD12</b>. §3 annotates these returns {@code @Nullable}, and SD12 may retype them to
 * {@link java.util.Optional} — a change that is correct and is also a rewrite of the first thing every bot
 * does. Pinned first, so that change is a migration rather than a discovery.
 *
 * <p>{@link MatchResult#miss(double)} gets its own case because it is the one that looks like an exception to
 * the rule and is not: it carries a real below-threshold score for telemetry, so {@code getConfidence()} is
 * non-zero while {@code isFound()} is still false and every accessor is still null.
 */
class MatchResultNullContractTest {

    /** The accessors that carry the null contract, named as a bot author would see them. */
    private static final List<Accessor> NULLABLE = List.of(
            new Accessor("getCenter", MatchResult::getCenter),
            new Accessor("getRandomClickPoint", MatchResult::getRandomClickPoint),
            new Accessor("getTopLeft", MatchResult::getTopLeft),
            new Accessor("getTopRight", MatchResult::getTopRight),
            new Accessor("getBottomLeft", MatchResult::getBottomLeft),
            new Accessor("getBottomRight", MatchResult::getBottomRight),
            new Accessor("getRect", MatchResult::getRect),
            new Accessor("getPointWithOffset", m -> m.getPointWithOffset(3, 4)));

    private record Accessor(String name, Function<MatchResult, Object> read) {}

    private static MatchResult found() {
        return new MatchResult(new Point(10, 20), 40, 30, 0.93, "button.png");
    }

    // ---- not found ⇒ null, for every accessor ----

    @Test
    void everyAccessorIsNullWhenNotFound() {
        MatchResult notFound = MatchResult.notFound();
        assertFalse(notFound.isFound());

        List<String> leaked = new ArrayList<>();
        for (Accessor a : NULLABLE) {
            if (a.read().apply(notFound) != null) leaked.add(a.name());
        }
        if (!leaked.isEmpty()) {
            fail("these returned a value for a match that was not found: " + String.join(", ", leaked)
                    + ". Every bot guards with `if (m.isFound())`; an accessor that returns a zero-valued Point "
                    + "instead of null makes that guard pointless and the bot clicks (0,0) in silence.");
        }
    }

    /** A near-miss is still a miss: it carries a score for telemetry and nothing else. */
    @Test
    void aMissCarriesItsScoreAndStaysNotFound() {
        MatchResult miss = MatchResult.miss(0.71);

        assertFalse(miss.isFound(), "a below-threshold score is not a find");
        assertEquals(0.71, miss.getConfidence(), 1e-9,
                "the real near-miss score is the point of miss() — an observer showing 0 cannot tell "
                        + "'nothing like it on screen' from 'almost matched'");
        for (Accessor a : NULLABLE) {
            assertNull(a.read().apply(miss), a.name() + " must be null on a miss, score or no score");
        }
    }

    @Test
    void aNotFoundResultHasNoTemplateIdAndZeroSize() {
        MatchResult notFound = MatchResult.notFound();
        assertNull(notFound.getTemplateId());
        assertEquals(0, notFound.getWidth());
        assertEquals(0, notFound.getHeight());
        assertEquals(0.0, notFound.getConfidence(), 1e-9);
    }

    // ---- found ⇒ non-null, with the geometry that makes the guard worth passing ----

    @Test
    void everyAccessorIsNonNullWhenFound() {
        MatchResult m = found();
        assertTrue(m.isFound());
        for (Accessor a : NULLABLE) {
            assertNotNull(a.read().apply(m), a.name() + " returned null for a match that was found");
        }
    }

    @Test
    void theCornersDescribeTheMatchedRectangle() {
        MatchResult m = found(); // (10,20) 40x30

        assertEquals(10.0, m.getTopLeft().x, 1e-9);
        assertEquals(20.0, m.getTopLeft().y, 1e-9);
        assertEquals(50.0, m.getTopRight().x, 1e-9);
        assertEquals(20.0, m.getTopRight().y, 1e-9);
        assertEquals(10.0, m.getBottomLeft().x, 1e-9);
        assertEquals(50.0, m.getBottomLeft().y, 1e-9);
        assertEquals(50.0, m.getBottomRight().x, 1e-9);
        assertEquals(50.0, m.getBottomRight().y, 1e-9);

        assertEquals(30.0, m.getCenter().x, 1e-9, "centre is top-left + half the width");
        assertEquals(35.0, m.getCenter().y, 1e-9);

        Rect r = m.getRect();
        assertEquals(10, r.x);
        assertEquals(20, r.y);
        assertEquals(40, r.width);
        assertEquals(30, r.height);
    }

    /**
     * The random click point must land inside the match, or a bot that clicks it misses the button it found.
     * Sampled, because it is random by design — that is the anti-detection point of it.
     */
    @Test
    void theRandomClickPointAlwaysLandsInsideTheMatch() {
        MatchResult m = found(); // (10,20) 40x30
        for (int i = 0; i < 500; i++) {
            Point p = m.getRandomClickPoint();
            assertTrue(p.x >= 10 && p.x <= 50, "x escaped the match: " + p.x);
            assertTrue(p.y >= 20 && p.y <= 50, "y escaped the match: " + p.y);
        }
    }

    @Test
    void offsetsAreRelativeToTheTopLeft() {
        Point p = found().getPointWithOffset(3, 4);
        assertEquals(13.0, p.x, 1e-9);
        assertEquals(24.0, p.y, 1e-9);
    }

    // ---- the list above must not silently stop covering the class ----

    /**
     * If an accessor is added and not listed here, the two contract tests keep passing while covering less than
     * they claim to. That is the failure mode Phase 2 deleted a test class for, so it is checked rather than
     * trusted: every public method returning a {@link Point} or {@link Rect} must appear in {@link #NULLABLE}.
     */
    @Test
    void theCoveredAccessorListIsComplete() {
        List<String> uncovered = new ArrayList<>();
        for (Method method : MatchResult.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) continue;
            Class<?> returns = method.getReturnType();
            if (returns != Point.class && returns != Rect.class) continue;
            if (NULLABLE.stream().noneMatch(a -> a.name().equals(method.getName()))) {
                uncovered.add(method.getName());
            }
        }
        if (!uncovered.isEmpty()) {
            fail("MatchResult gained " + String.join(", ", uncovered) + ", which no null-contract test covers. "
                    + "Add it to NULLABLE — a bot author will guard with isFound() and dereference it.");
        }
    }
}
