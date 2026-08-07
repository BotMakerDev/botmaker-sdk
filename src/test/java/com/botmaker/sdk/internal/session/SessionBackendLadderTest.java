package com.botmaker.sdk.internal.session;

import com.botmaker.sdk.api.Session;
import com.botmaker.session.impl.NestedSession;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * <b>SDK MISSING 5 — the backend precedence ladder</b>, and the gate for SD8, which retypes exactly the
 * parameter this ladder resolves.
 *
 * <p>{@code SessionBootstrapTest} covers the <em>isolation</em> ladder (whether a bot gets a private display at
 * all). Nothing covered the <em>backend</em> ladder, which decides which kind of private display it gets — and
 * that decision has teeth: Xephyr is software GL, so picking it for a store-launcher game is the SIGTRAP the
 * auto-selection was written to prevent.
 *
 * <p>Four rungs, highest first: a bot's {@link Session#useBackend} pin, the {@code botmaker.session.backend}
 * system property, the project's {@code session.backend} key, then the kind-driven choice. The interesting
 * property is at the bottom: the last rung is not a constant but a <b>function of the launch kind</b>, so
 * "nothing is configured" gives different answers for a game and for a plain command. A refactor that
 * flattened the ladder into a default value would look equivalent and would send every game to software GL.
 *
 * <p>The other property worth pinning is that every rung parses through a <b>total</b> {@code fromId}: an
 * unrecognised value — {@code "auto"}, a typo, a value from a newer Studio — falls through to the next rung
 * rather than resolving to a backend it does not name. That is recorded in the method's javadoc as a past bug:
 * the previous {@code "gamescope".equalsIgnoreCase(x) ? GAMESCOPE : XEPHYR} mapped an explicit {@code auto}
 * onto Xephyr, i.e. onto the crash.
 */
class SessionBackendLadderTest {

    private static final String PROPERTY = SessionBootstrap.BACKEND_PROPERTY;

    @AfterEach
    void clearEveryRung() {
        System.clearProperty(PROPERTY);
        Session.clearOverrides();
    }

    /** A launch kind that wants a real GPU. */
    private static LaunchSpec game() {
        return LaunchSpec.parse("steam:440");
    }

    /** A launch kind that does not. */
    private static LaunchSpec plainCommand() {
        return LaunchSpec.parse("cmd:/usr/bin/xterm");
    }

    // ---- The rungs, in order ----

    @Test
    void aBotsPinBeatsEverythingBelowIt() {
        System.setProperty(PROPERTY, "gamescope");
        Session.useBackend("xephyr");

        assertEquals(NestedSession.Backend.XEPHYR, SessionBootstrap.backend(game()),
                "bot code that pins a backend is reproducing a specific problem; nothing configured elsewhere "
                        + "may override it");
    }

    @Test
    void thePropertyBeatsTheDefault() {
        System.setProperty(PROPERTY, "xephyr");

        assertEquals(NestedSession.Backend.XEPHYR, SessionBootstrap.backend(game()),
                "-Dbotmaker.session.backend is the operator's override and must beat the default");
    }

    /**
     * The bottom rung: with nothing configured, every launch kind isolates on gamescope. Xephyr is now reachable
     * only by naming it — the rungs above — because "lighter for a plain command" bought nothing and left the
     * least-exercised launch kinds on the only backend whose software GL crashes what a session usually runs.
     */
    @Test
    void withNothingConfiguredEveryKindGetsGamescope() {
        NestedSession.Backend forAGame = SessionBootstrap.backend(game());
        NestedSession.Backend forACommand = SessionBootstrap.backend(plainCommand());

        assertNotNull(forAGame);
        assertNotNull(forACommand);
        assertEquals(NestedSession.Backend.GAMESCOPE, forAGame,
                "a store-launcher game must get gamescope: Xephyr is software GL and that is the SIGTRAP this "
                        + "ladder exists to avoid");
        assertEquals(NestedSession.Backend.GAMESCOPE, forACommand,
                "and a plain command gets it too — one path, the one that is actually exercised");
    }

    // ---- Totality: an unrecognised value falls through rather than resolving ----

    /**
     * {@code auto} is the case that already caused this bug once: it is a real thing a user types, it is not a
     * backend id, and the old {@code equalsIgnoreCase} ternary mapped it onto Xephyr — onto the crash. It must
     * fall through to the default.
     */
    @Test
    void anExplicitAutoFallsThroughToTheDefault() {
        Session.useBackend("auto");

        assertEquals(NestedSession.Backend.GAMESCOPE, SessionBootstrap.backend(game()),
                "'auto' means 'decide for me', not 'Xephyr'");
    }

    @Test
    void aTypoFallsThroughRatherThanResolvingToABackendItDoesNotName() {
        System.setProperty(PROPERTY, "gamscope"); // one letter out

        // Xephyr is now the only thing a *match* on this property could not produce by accident: the default is
        // gamescope, so seeing XEPHYR here would mean the unparseable value had been mapped onto a backend —
        // which is exactly the old bug.
        assertEquals(NestedSession.Backend.GAMESCOPE, SessionBootstrap.backend(game()));
        assertEquals(NestedSession.Backend.GAMESCOPE, SessionBootstrap.backend(plainCommand()));
    }

    @Test
    void aBlankPinIsNotAPin() {
        Session.useBackend("   ");

        assertEquals(NestedSession.Backend.GAMESCOPE, SessionBootstrap.backend(game()),
                "useBackend(\"\") must clear the pin, not set an unmatchable one");
    }

    /** Ids are matched case- and whitespace-insensitively, so a hand-edited property still resolves. */
    @Test
    void idsAreNormalisedBeforeMatching() {
        System.setProperty(PROPERTY, "  XePhyr  ");

        assertEquals(NestedSession.Backend.XEPHYR, SessionBootstrap.backend(plainCommand()),
                "everything defaults to gamescope, so seeing XEPHYR here proves the property was parsed "
                        + "despite its casing and padding");
    }

    // ---- Options follow the ladder, rather than re-deciding ----

    /**
     * {@code options()} calls {@code backend()} rather than repeating the ladder. Pinned because duplicating a
     * four-rung precedence chain is the kind of thing that gets done for readability and then drifts — and the
     * drift is invisible until a game lands on software GL.
     */
    @Test
    void theOptionsBuiltForALaunchMatchTheBackendTheLadderChose() {
        System.setProperty(PROPERTY, "xephyr");

        assertEquals(NestedSession.Backend.XEPHYR, SessionBootstrap.backend(game()));
        assertNotNull(SessionBootstrap.options(game()),
                "options() must resolve through the same ladder, not a second copy of it");
    }
}
