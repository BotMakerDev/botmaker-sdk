package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.Keyboard;
import com.botmaker.sdk.api.input.Mouse;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.emulator.EmulatorSource;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A run's reach: the capture source the run reads ({@link Source#current()}), matched at the bot's confidence and
 * clicked as {@code ImageClicker} clicks, at a spread point when the bot's settings say so.
 */
public final class RunReach implements Reach {

    /** How long a key is held, as the assistant's hands hold one: a game polling its keys misses a tap with none. */
    private static final Duration KEY_HOLD = Duration.ofMillis(40);

    /** Between a double tap's two taps on an emulator: well inside a phone's double-tap time. */
    private static final long DOUBLE_TAP_MS = 80;

    @Override
    public Optional<BufferedImage> look() {
        return Optional.ofNullable(Source.current().capture());
    }

    @Override
    public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
        MatchResult best = ImageFinder.bestMatch(picture, new StillSource(frame));
        return best.isFound() && best.confidence() >= BotSettings.current().confidence()
                ? Optional.of(best.rect()) : Optional.empty();
    }

    /** One frame read into the matcher once for all of {@code pictures}: a walk looks for every marker at each poll. */
    @Override
    public List<Optional<Rect>> findEach(List<ImageTemplate> pictures, BufferedImage frame) {
        return cleared(ImageFinder.bestMatches(pictures, new StillSource(frame)));
    }

    @Override
    public List<Optional<Found>> findEachScored(List<ImageTemplate> pictures, BufferedImage frame) {
        double needs = BotSettings.current().confidence();
        return ImageFinder.bestMatches(pictures, new StillSource(frame)).stream()
                .map(best -> best.isFound() && best.confidence() >= needs
                        ? Optional.of(new Found(best.rect(), best.confidence())) : Optional.<Found>empty())
                .toList();
    }

    @Override
    public List<Optional<Rect>> findEachNear(List<ImageTemplate> pictures, BufferedImage frame) {
        return cleared(ImageFinder.bestMatchesNear(pictures, MARKER_MARGIN, new StillSource(frame)));
    }

    private static List<Optional<Rect>> cleared(List<MatchResult> bests) {
        double needs = BotSettings.current().confidence();
        return bests.stream()
                .map(best -> best.isFound() && best.confidence() >= needs ? Optional.of(best.rect()) : Optional.<Rect>empty())
                .toList();
    }

    @Override
    public List<Rect> findAll(ImageTemplate picture, BufferedImage frame) {
        return ImageFinder.allMatches(picture, new StillSource(frame), BotSettings.current().confidence()).stream()
                .map(MatchResult::rect).toList();
    }

    @Override
    public List<Found> findAllScored(ImageTemplate picture, BufferedImage frame) {
        return ImageFinder.allMatches(picture, new StillSource(frame), BotSettings.current().confidence()).stream()
                .map(match -> new Found(match.rect(), match.confidence())).toList();
    }

    /** Looked for only in {@code area}, scaled to the frame as the picture is: its place there, with no margin. */
    @Override
    public Optional<Rect> findWithin(ImageTemplate picture, Rect area, BufferedImage frame) {
        return findWithinScored(picture, area, frame).map(Found::place);
    }

    @Override
    public Optional<Found> findWithinScored(ImageTemplate picture, Rect area, BufferedImage frame) {
        ImageTemplate inArea = picture.at(area);
        try {
            MatchResult best = ImageFinder.bestMatchesNear(List.of(inArea), 0, new StillSource(frame)).getFirst();
            return best.isFound() && best.confidence() >= BotSettings.current().confidence()
                    ? Optional.of(new Found(best.rect(), best.confidence())) : Optional.empty();
        } finally {
            // A copy of its own loads its pixels anew: released after the match, as native memory.
            inArea.close();
        }
    }

    @Override
    public Point pointOn(Rect place) {
        if (!BotSettings.current().randomizeClicks()) return place.center();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Point(place.x() + place.width() / 4 + random.nextInt(place.width() / 2 + 1),
                place.y() + place.height() / 4 + random.nextInt(place.height() / 2 + 1));
    }

    /**
     * A click and a hold through the source, so an emulator taps and long-presses; the other gestures as
     * {@code Mouse} does them on the source's window.
     */
    @Override
    public void press(Point at, Gesture gesture, Duration hold) {
        CaptureSource source = Source.current();
        if (source.base() instanceof EmulatorSource && gesture != Gesture.HOLD) {
            pressed(source, at, gesture);
            return;
        }
        switch (gesture) {
            case DOUBLE -> Mouse.doubleClick(source, at.x(), at.y());
            case RIGHT -> Mouse.rightClick(source, at.x(), at.y());
            case MIDDLE -> Mouse.middleClick(source, at.x(), at.y());
            // Pressed and let go where it was pressed, after the hold: a long press on an emulator too.
            case HOLD -> source.drag(onScreen(source, at), onScreen(source, at),
                    (hold == null ? Button.HOLD_TIME : hold).toMillis());
            case HOVER -> Mouse.move(onScreen(source, at));
            case CLICK, UNKNOWN -> source.click(onScreen(source, at));
        }
    }

    /**
     * {@code gesture} on an emulator, which has no mouse: a double tap for a double click, a tap for a right or middle
     * click, and nothing for a hover, which a touch screen has no way to do without pressing.
     */
    private void pressed(CaptureSource emulator, Point at, Gesture gesture) {
        switch (gesture) {
            case DOUBLE -> {
                emulator.click(onScreen(emulator, at));
                pause(DOUBLE_TAP_MS);
                emulator.click(onScreen(emulator, at));
            }
            case HOVER -> {
            }
            default -> emulator.click(onScreen(emulator, at));
        }
    }

    @Override
    public void keys(Combo combo) {
        Keyboard.combo(Source.current(), combo.hold().isZero() ? combo.held(KEY_HOLD) : combo);
    }

    /**
     * Typed on the source's window, cleared by Ctrl+A; on an emulator through its own text input, which has no
     * select-all, so a field there keeps what it held.
     */
    @Override
    public void type(String text, boolean clears, boolean submits) {
        CaptureSource source = Source.current();
        if (source.base() instanceof EmulatorSource emulator) {
            if (clears) Debug.log("An emulator's field is not cleared before typing: it has no select-all.");
            emulator.text(text);
            if (submits) emulator.key(EmulatorSource.KEYCODE_ENTER);
            return;
        }
        if (clears) Keyboard.combo(source, Combo.of(Key.CTRL, Key.A).held(KEY_HOLD));
        Keyboard.type(source, text);
        if (submits) Keyboard.combo(source, Combo.of(Key.ENTER).held(KEY_HOLD));
    }

    @Override
    public void scroll(Point at, int notches) {
        CaptureSource source = Source.current();
        source.scroll(onScreen(source, at), notches);
    }

    @Override
    public void drag(Point from, Point to, Duration time) {
        CaptureSource source = Source.current();
        source.drag(onScreen(source, from), onScreen(source, to), time.toMillis());
    }

    @Override
    public long nowMs() {
        return System.nanoTime() / 1_000_000;
    }

    @Override
    public void pause(long ms) {
        Wait.milliseconds((int) Math.min(Integer.MAX_VALUE, Math.max(0, ms)));
    }

    /** {@code at} on the frame as the source places a click: offset by where its frame starts. */
    private static Point onScreen(CaptureSource source, Point at) {
        Point origin = source.origin();
        return new Point(origin.x() + at.x(), origin.y() + at.y());
    }
}
