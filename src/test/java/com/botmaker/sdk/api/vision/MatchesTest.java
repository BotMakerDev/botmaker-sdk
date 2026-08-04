package com.botmaker.sdk.api.vision;

import com.botmaker.sdk.api.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for {@link Matches}, the value the group lambda helpers hand a bot's action.
 *
 * <p>Two properties matter beyond the obvious lookups, because bot code is written against them without
 * thinking: {@link Matches#get} never returns null (so {@code click(found.get(x))} is safe to write for a
 * template that wasn't there), and lookups are keyed by <em>template id</em> rather than object identity (so a
 * template reloaded from the same file still answers {@code has}). Duplicate ids are covered because an
 * {@code ImageTemplateGroup} is free to contain two templates that resolve to the same id — the slot can hold
 * only one, and it must be the better match.
 *
 * <p>Pure unit test: {@link ImageTemplate} does not touch its file until {@code getMat()}, so no fixture image
 * and no OpenCV load is needed here. The single-capture property of {@code findAllTemplates} is covered
 * separately by {@link MatchesFindTest}, which needs real matching.
 */
class MatchesTest {

    private static final ImageTemplate MAIL = new ImageTemplate("images/mail.png");
    private static final ImageTemplate CLAIM = new ImageTemplate("images/claim.png");
    private static final ImageTemplate ABSENT = new ImageTemplate("images/absent.png");

    private static MatchResult found(String templateId, double confidence) {
        return new MatchResult(new Point(10, 20), 30, 40, confidence, templateId);
    }

    private static Matches of(MatchResult... results) {
        return Matches.of(List.of(results));
    }

    @Test
    void looksUpByTemplateIdNotIdentity() {
        Matches m = of(found("mail", 0.9));

        assertTrue(m.has(MAIL));
        // A distinct object built from the same file resolves to the same id, so it must answer identically.
        assertTrue(m.has(new ImageTemplate("some/other/dir/mail.png")));
        assertFalse(m.has(ABSENT));
    }

    @Test
    void getReturnsNotFoundRatherThanNullForAnAbsentTemplate() {
        Matches m = of(found("mail", 0.9));

        MatchResult miss = m.get(ABSENT);
        assertNotNull(miss, "get() must never return null — bots pass it straight to click()");
        assertFalse(miss.isFound());
        assertFalse(m.get(null).isFound(), "a null template is a miss, not an NPE");
    }

    @Test
    void hasAllAndHasAnyOverACombination() {
        Matches m = of(found("mail", 0.9), found("claim", 0.8));

        assertTrue(m.hasAll(MAIL, CLAIM));
        assertFalse(m.hasAll(MAIL, ABSENT));
        assertTrue(m.hasAny(ABSENT, CLAIM));
        assertFalse(m.hasAny(ABSENT));

        assertTrue(m.hasAll(), "vacuously true: every template of an empty set is present");
        assertFalse(m.hasAny(), "no template can be present in an empty set");
    }

    @Test
    void dropsResultsThatWereNotFound() {
        Matches m = of(found("mail", 0.9), MatchResult.notFound(), MatchResult.miss(0.62));

        assertEquals(1, m.count());
        assertTrue(m.has(MAIL));
        assertFalse(m.isEmpty());
    }

    @Test
    void duplicateTemplateIdsKeepTheHigherConfidence() {
        MatchResult weak = found("mail", 0.71);
        MatchResult strong = found("mail", 0.95);

        assertSame(strong, of(weak, strong).get(MAIL), "later, better match wins the slot");
        assertSame(strong, of(strong, weak).get(MAIL), "earlier, better match keeps the slot");
        assertEquals(1, of(weak, strong).count(), "one id, one slot");
    }

    @Test
    void bestIsTheHighestConfidenceAndTiesKeepDeclarationOrder() {
        MatchResult first = found("mail", 0.8);
        MatchResult second = found("claim", 0.8);

        assertEquals("claim", of(found("mail", 0.7), found("claim", 0.93)).best().getTemplateId());
        assertSame(first, of(first, second).best(), "a tie keeps the group's priority order");
    }

    @Test
    void emptyIsEmpty() {
        Matches none = Matches.none();

        assertTrue(none.isEmpty());
        assertEquals(0, none.count());
        assertTrue(none.all().isEmpty());
        assertFalse(none.best().isFound());
        assertFalse(none.has(MAIL));
        assertTrue(of(MatchResult.notFound()).isEmpty(), "a frame with no hits is the empty result");
    }

    @Test
    void allIsOrderedAndUnmodifiable() {
        Matches m = of(found("mail", 0.9), found("claim", 0.8));

        assertEquals(List.of("mail", "claim"), m.all().stream().map(MatchResult::getTemplateId).toList());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> m.all().add(found("x", 1.0)));
    }
}
