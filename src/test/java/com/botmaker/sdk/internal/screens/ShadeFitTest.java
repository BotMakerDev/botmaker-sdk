package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.State;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A button's usual picture fitted against how it shows: dim, grey, bright, tint and fade, and none for another. */
class ShadeFitTest {

    private static final Set<Shade.Cause> GAME = Set.of(Shade.Cause.GAME);

    /** A 24×24 icon with a gradient in each channel and see-through corners, so every fit has spread to read. */
    private static BufferedImage icon() {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                boolean corner = (x < 3 || x > 20) && (y < 3 || y > 20);
                int r = 60 + x * 7;
                int g = 200 - y * 6;
                int b = 40 + (x + y) * 3;
                image.setRGB(x, y, (corner ? 0 : 0xFF) << 24 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    /** {@code cut} as a frame at (10, 10) in a black 50×50 frame, each channel turned by {@code turn}. */
    private static BufferedImage shown(BufferedImage cut, IntUnaryOperator[] turn) {
        BufferedImage frame = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < cut.getHeight(); y++) {
            for (int x = 0; x < cut.getWidth(); x++) {
                int argb = cut.getRGB(x, y);
                int r = clamp(turn[0].applyAsInt((argb >> 16) & 0xFF));
                int g = clamp(turn[1].applyAsInt((argb >> 8) & 0xFF));
                int b = clamp(turn[2].applyAsInt(argb & 0xFF));
                frame.setRGB(10 + x, 10 + y, r << 16 | g << 8 | b);
            }
        }
        return frame;
    }

    private static IntUnaryOperator[] each(IntUnaryOperator turn) {
        return new IntUnaryOperator[]{turn, turn, turn};
    }

    private static int clamp(int v) {
        return Math.clamp(v, 0, 255);
    }

    private static final Rect AT = new Rect(10, 10, 24, 24);

    private static ShadeFit.Fit fit(BufferedImage frame) {
        return ShadeFit.fit(icon(), 1, frame, AT).orElseThrow();
    }

    @Test
    void theButtonAsCutFitsNoChange() {
        ShadeFit.Fit fit = fit(shown(icon(), each(v -> v)));
        assertFalse(fit.grey());
        assertEquals(1, fit.gain().red(), 0.01);
        assertEquals(0, fit.offset().green(), 0.01);
        assertEquals(0, fit.error(), 0.01);
    }

    @Test
    void aDimIsEveryChannelScaledDownAlike() {
        ShadeFit.Fit fit = fit(shown(icon(), each(v -> (int) Math.round(v * 0.45))));
        assertEquals(Shade.Kind.DIM, fit.kind());
        assertEquals(0.45, fit.gain().blue(), 0.01);
        Shade learned = fit.learned(Shade.Cause.GAME, State.DISABLED);
        assertEquals(new Shade.Channels(0.45, 0.45, 0.45), learned.gain());
        assertEquals(Optional.of(learned), ShadeFit.closest(fit, List.of(learned), GAME));
    }

    @Test
    void aGreyFollowsTheLightNotTheChannel() {
        BufferedImage cut = icon();
        BufferedImage frame = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int argb = cut.getRGB(x, y);
                int light = (int) Math.round(0.299 * ((argb >> 16) & 0xFF) + 0.587 * ((argb >> 8) & 0xFF)
                        + 0.114 * (argb & 0xFF));
                frame.setRGB(10 + x, 10 + y, new Color(light, light, light).getRGB());
            }
        }
        ShadeFit.Fit fit = fit(frame);
        assertTrue(fit.grey());
        assertEquals(Shade.Kind.GREY, fit.kind());
        assertEquals(1, fit.gain().green(), 0.02);
    }

    @Test
    void aBrighteningLiftsEveryChannelAlike() {
        ShadeFit.Fit fit = fit(shown(icon(), each(v -> (int) Math.round(v * 1.1))));
        assertEquals(Shade.Kind.BRIGHT, fit.kind());
    }

    @Test
    void aTintScalesEachChannelApart() {
        IntUnaryOperator[] reddish = {v -> v, v -> (int) Math.round(v * 0.6), v -> (int) Math.round(v * 0.5)};
        ShadeFit.Fit fit = fit(shown(icon(), reddish));
        assertEquals(Shade.Kind.TINT, fit.kind());
        assertEquals(1, fit.gain().red(), 0.02);
        assertEquals(0.6, fit.gain().green(), 0.02);
        assertEquals(0.5, fit.gain().blue(), 0.02);
    }

    @Test
    void aFadeGoesTowardsTheBackground() {
        // Half the cut over a grey of 200: shown = 0.5 · cut + 100.
        ShadeFit.Fit fit = fit(shown(icon(), each(v -> (int) Math.round(v * 0.5 + 100))));
        assertEquals(Shade.Kind.FADE, fit.kind());
        assertEquals(100 / 255.0, fit.offset().red(), 0.02);
    }

    @Test
    void noiseStaysWithinTheTolerance() {
        Random random = new Random(7);
        ShadeFit.Fit fit = fit(shown(icon(), each(v -> (int) Math.round(v * 0.45) + random.nextInt(9) - 4)));
        Shade dim = new Shade(Shade.Kind.DIM, Shade.Channels.all(0.45), Shade.Channels.all(0), 0, Shade.Cause.GAME,
                State.DISABLED);
        assertEquals(Optional.of(dim), ShadeFit.closest(fit, List.of(dim), GAME));
    }

    @Test
    void anUnrelatedPictureFitsNone() {
        Random random = new Random(3);
        BufferedImage frame = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 50; y++) {
            for (int x = 0; x < 50; x++) frame.setRGB(x, y, random.nextInt(0xFFFFFF));
        }
        Shade dim = new Shade(Shade.Kind.DIM, Shade.Channels.all(0.45), Shade.Channels.all(0), 0, Shade.Cause.GAME,
                State.DISABLED);
        assertEquals(Optional.empty(), ShadeFit.closest(fit(frame), List.of(dim), GAME));
        assertEquals(Optional.empty(), ShadeFit.closest(fit(shown(icon(), each(v -> v))), List.of(dim), GAME),
                "the button as cut is not dimmed");
    }

    @Test
    void theBotsOwnInputCausesAShadeOnlyWhereItIs() {
        ShadeFit.Pointer clicked = new ShadeFit.Pointer(new Point(20, 20), 1000, true, true);
        assertEquals(Set.of(Shade.Cause.GAME, Shade.Cause.HOVER, Shade.Cause.FOCUS, Shade.Cause.PRESS),
                ShadeFit.causes(AT, clicked, 1200));
        assertEquals(Set.of(Shade.Cause.GAME, Shade.Cause.HOVER, Shade.Cause.FOCUS),
                ShadeFit.causes(AT, clicked, 1000 + ShadeFit.PRESS_MS), "let go a while ago");
        assertEquals(GAME, ShadeFit.causes(new Rect(100, 100, 20, 20), clicked, 1200), "another button");
        ShadeFit.Pointer tapped = new ShadeFit.Pointer(new Point(20, 20), 1000, true, false);
        assertFalse(ShadeFit.causes(AT, tapped, 5000).contains(Shade.Cause.HOVER), "an emulator has no hover");
    }

    @Test
    void aHoverThatDarkensKeepsTheButtonUnderTheCursorNormal() {
        Shade hover = new Shade(Shade.Kind.DIM, Shade.Channels.all(0.5), Shade.Channels.all(0), 0.1,
                Shade.Cause.HOVER, State.DISABLED);
        assertEquals(State.NORMAL, hover.state(), "the bot's input never changes what a button means");
        Shade dim = new Shade(Shade.Kind.DIM, Shade.Channels.all(0.45), Shade.Channels.all(0), 0.1, Shade.Cause.GAME,
                State.DISABLED);
        ShadeFit.Fit darker = fit(shown(icon(), each(v -> (int) Math.round(v * 0.47))));
        Set<Shade.Cause> underCursor = ShadeFit.causes(AT, new ShadeFit.Pointer(new Point(20, 20), 0, false, true),
                10_000);
        assertEquals(Optional.of(hover), ShadeFit.closest(darker, List.of(dim, hover), underCursor));
        assertEquals(Optional.of(dim), ShadeFit.closest(darker, List.of(dim, hover), GAME), "elsewhere: dimmed");
    }

    @Test
    void theFocusOfAButtonJustPressedDoesNotHideTheGamesDim() {
        Shade focus = new Shade(Shade.Kind.DIM, Shade.Channels.all(0.5), Shade.Channels.all(0), 0.1,
                Shade.Cause.FOCUS, State.NORMAL);
        Shade dim = new Shade(Shade.Kind.DIM, Shade.Channels.all(0.45), Shade.Channels.all(0), 0.1, Shade.Cause.GAME,
                State.DISABLED);
        ShadeFit.Fit darker = fit(shown(icon(), each(v -> (int) Math.round(v * 0.45))));
        Set<Shade.Cause> pressedHere = ShadeFit.causes(AT, new ShadeFit.Pointer(new Point(20, 20), 0, true, false),
                10_000);
        assertEquals(Optional.of(dim), ShadeFit.closest(darker, List.of(focus, dim), pressedHere), "the closer wins");
    }

    @Test
    void aGreyCutStillNamesALearnedGrey() {
        BufferedImage cut = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int v = 40 + x * 8;
                cut.setRGB(x, y, 0xFF000000 | v << 16 | v << 8 | v);
            }
        }
        BufferedImage frame = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int v = (int) Math.round((40 + x * 8) * 0.6);
                frame.setRGB(10 + x, 10 + y, v << 16 | v << 8 | v);
            }
        }
        Shade grey = new Shade(Shade.Kind.GREY, Shade.Channels.all(0.6), Shade.Channels.all(0), 0, Shade.Cause.GAME,
                State.DISABLED);
        assertEquals(Optional.of(grey),
                ShadeFit.closest(ShadeFit.fit(cut, 1, frame, AT).orElseThrow(), List.of(grey), GAME));
    }

    @Test
    void aMovingPictureIsFittedByTheFrameThatShows() {
        BufferedImage one = icon();
        BufferedImage strip = new BufferedImage(48, 24, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                strip.setRGB(x, y, 0xFF000000 | (x * 10) << 16 | (y * 10) << 8 | 128);
                strip.setRGB(24 + x, y, one.getRGB(x, y));
            }
        }
        ShadeFit.Fit fit = ShadeFit.fit(strip, 2, shown(one, each(v -> (int) Math.round(v * 0.45))), AT).orElseThrow();
        assertEquals(Shade.Kind.DIM, fit.kind(), "the second frame is the one shown");
        assertEquals(0.45, fit.gain().red(), 0.01);
    }

    @Test
    void aShadeAppliedToTheCutIsWhatItFits() {
        Shade tint = new Shade(Shade.Kind.TINT, new Shade.Channels(1, 0.6, 0.5), Shade.Channels.all(0), 0,
                Shade.Cause.GAME, State.DISABLED);
        BufferedImage tinted = ShadeFit.shaded(icon(), tint);
        assertEquals(0, tinted.getRGB(0, 0) >>> 24, "see-through stays so");
        BufferedImage frame = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        frame.getGraphics().drawImage(tinted, 10, 10, null);
        assertEquals(Optional.of(tint), ShadeFit.closest(fit(frame), List.of(tint), GAME));
    }
}
