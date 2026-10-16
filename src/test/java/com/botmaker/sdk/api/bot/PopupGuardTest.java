package com.botmaker.sdk.api.bot;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.api.vision.ImageWaiter;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Matches;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The popup guard over the bot's map (2026-10-16): before a vision step that takes its own capture it closes a popup
 * screen of the map by its way out, once per step, never re-entering through its own looks; and a wait that timed out
 * on no screen of the map clears the overlay with the map's close buttons.
 *
 * <p>Exercised through the real facades, because the thing that can silently break is the wiring: an overload that
 * forgets the call, or one that delegates to another guarded overload (the check running twice per statement). Both
 * show as captures: a check with a popup in the map takes one frame, the step its own. Escape, a popup that stays
 * and the walk's own closing are {@code ScreenWalkTest}'s, on counted time and a scripted game.
 */
class PopupGuardTest {

    private static final Rect HOME_AT = new Rect(10, 100, 24, 24);
    private static final Rect OFFER_AT = new Rect(40, 40, 24, 24);
    private static final Rect CLOSE_AT = new Rect(120, 40, 24, 24);

    @TempDir
    Path tmp;

    private ImageTemplate home;
    private ImageTemplate offer;
    private ImageTemplate close;
    private ImageTemplate absent;

    @BeforeEach
    void pictures() throws Exception {
        home = picture("home", 3);
        offer = picture("offer", 5);
        close = picture("close", 7);
        absent = picture("absent", 11);
    }

    @AfterEach
    void clearTheProcessGlobals() {
        ScreenMaps.use(null);
        PopupGuard.enabled(true);
        Source.set(null);
    }

    /** A game whose frame shows Home, or a cover over it until its {@code closeAt} is clicked; captures counted. */
    private final class Game implements CaptureSource {
        final BufferedImage plain = frame(true);
        final BufferedImage covered;
        final Rect closeAt;
        boolean cover;
        int captures;
        final List<Point> clicks = new ArrayList<>();

        Game(BufferedImage covered, Rect closeAt) {
            this.covered = covered;
            this.closeAt = closeAt;
            this.cover = covered != null;
        }

        @Override public BufferedImage capture() {
            captures++;
            return cover ? covered : plain;
        }

        @Override public Point origin() {
            return new Point(0, 0);
        }

        @Override public void click(Point p) {
            clicks.add(p);
            if (closeAt != null && closeAt.contains(p)) cover = false;
        }
    }

    /** The noise every frame is, Home's marker on it when {@code withHome}. */
    private BufferedImage frame(boolean withHome) {
        BufferedImage bg = new BufferedImage(200, 150, BufferedImage.TYPE_3BYTE_BGR);
        Random rnd = new Random(7);
        for (int y = 0; y < bg.getHeight(); y++) {
            for (int x = 0; x < bg.getWidth(); x++) bg.setRGB(x, y, rnd.nextInt(0xFFFFFF));
        }
        if (withHome) draw(bg, "home", HOME_AT);
        return bg;
    }

    /** The offer popup over Home: its marker and its close button, Home's marker hidden under it. */
    private BufferedImage offerShown() {
        BufferedImage frame = frame(false);
        draw(frame, "offer", OFFER_AT);
        draw(frame, "close", CLOSE_AT);
        return frame;
    }

    private void draw(BufferedImage on, String id, Rect at) {
        try {
            on.getGraphics().drawImage(ImageIO.read(tmp.resolve(id + ".png").toFile()), at.x(), at.y(), null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A 24 px texture of its own, absent from the noise unless drawn in. */
    private ImageTemplate picture(String id, int seed) throws Exception {
        BufferedImage patch = new BufferedImage(24, 24, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                patch.setRGB(x, y, ((x * seed * 3 % 256) << 16) | ((y * seed * 5 % 256) << 8) | (seed * 20 % 256));
            }
        }
        Path file = tmp.resolve(id + ".png");
        ImageIO.write(patch, "png", file.toFile());
        return new ImageTemplate(file.toString());
    }

    /** Home, and the offer popup closed by its close button. */
    private ScreenGraph withOffer() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").markers(List.of(home)));
        screens.put("OFFER", GameScreen.named("Offer").markers(List.of(offer)).back(Input.click(close)).popup());
        return ScreenGraph.of(screens);
    }

    private Game play(ScreenGraph map, BufferedImage covered, Rect closeAt) {
        ScreenMaps.use(map);
        Game game = new Game(covered, closeAt);
        Source.set(game);
        return game;
    }

    @Test
    void aMapWithNoPopupTakesNoFrame() {
        Game game = play(ScreenGraph.of(Map.of()), null, null);
        assertFalse(PopupGuard.isEnabled(), "no popup in the map means no guard");
        assertFalse(ImageFinder.find(absent, game));
        assertEquals(1, game.captures, "an unguarded find is one capture");
    }

    @Test
    void aMappedPopupIsClosedByItsWayOutBeforeAFind() {
        Game game = play(withOffer(), offerShown(), CLOSE_AT);
        assertTrue(PopupGuard.isEnabled());

        assertTrue(ImageFinder.find(home, game), "Home's marker shows once the offer is closed");

        assertEquals(1, game.clicks.size(), "the offer's close button, once: " + game.clicks);
        assertTrue(CLOSE_AT.contains(game.clicks.getFirst()));
        assertFalse(game.cover);
    }

    @Test
    void theGuardRunsOncePerVisionStepAndItsOwnLooksDoNotReenterIt() {
        Game game = play(withOffer(), null, null);

        ImageFinder.find(absent, game);
        assertEquals(2, game.captures, "the guard's look, then the find's");

        ImageFinder.findAny(ImageTemplateGroup.of(absent), game);
        assertEquals(4, game.captures, "findAny is one statement: the overload that delegates must not guard as well");

        ImageClicker.click(absent, game);
        assertEquals(6, game.captures, "a click that locates its own target is a vision step too");
    }

    @Test
    void aDisabledGuardIsSkippedAndCanBeTurnedBackOn() {
        Game game = play(withOffer(), offerShown(), CLOSE_AT);

        PopupGuard.enabled(false);
        assertFalse(PopupGuard.isEnabled());
        assertFalse(ImageFinder.find(home, game), "a body that switched it off works under the popup");
        assertEquals(List.of(), game.clicks);

        PopupGuard.enabled(true);
        assertTrue(ImageFinder.find(home, game));
    }

    /**
     * Clicking a match you already located must not first dismiss a popup: the coordinate was measured in an
     * earlier frame, and moving the screen underneath it is how a "safe" guard produces a misclick.
     */
    @Test
    void clickingAnAlreadyLocatedMatchIsNotGuarded() {
        Game game = play(ScreenGraph.of(Map.of()), null, null);
        MatchResult[] found = {Matches.none().get(null)};
        ImageFinder.ifFindAny(ImageTemplateGroup.of(home), game, m -> found[0] = m.get(home));
        assertTrue(found[0].isFound(), "fixture: Home's marker must be findable in the frame");

        ScreenMaps.use(withOffer());
        int before = game.captures;
        ImageClicker.click(found[0], game);

        assertEquals(before, game.captures, "click(MatchResult) is deliberately unguarded");
        assertEquals(1, game.clicks.size());
    }

    @Test
    void aWaitIsGuardedOnItsPolls() {
        Game game = play(withOffer(), offerShown(), CLOSE_AT);

        assertTrue(ImageWaiter.waitFor(home, game, Duration.ofSeconds(2)), "the popup closed inside the wait");
        assertEquals(1, game.clicks.size());
    }

    /** A wait that timed out on no screen of the map: the map's close buttons clear the overlay, and say so. */
    @Test
    void anOverlayTheMapLacksIsClearedByTheMapsCloseButtons() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").markers(List.of(home)));
        screens.put("SHOP", GameScreen.named("Shop").markers(List.of(absent)).back(Input.click(close)));
        BufferedImage overlay = frame(false);
        draw(overlay, "close", CLOSE_AT);
        Game game = play(ScreenGraph.of(screens), overlay, CLOSE_AT);

        assertFalse(ImageWaiter.waitFor(offer, game, Duration.ofMillis(300)));

        assertEquals(1, game.clicks.size(), "Shop's close button, the map's only one: " + game.clicks);
        assertFalse(game.cover);
        assertEquals("HOME", ScreenMaps.lastSeen(), "the screen it uncovered");
    }

    @Test
    void aTryInAWarmJvmStartsWithTheGuardOn() {
        PopupGuard.enabled(false);
        Bot.trial(() -> {
            ScreenMaps.use(withOffer());
            assertTrue(PopupGuard.isEnabled(), "the switch a body left off is back on");
        });
    }
}
