package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.Keyboard;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A run's reach: the capture source the run reads ({@link Source#current()}), matched at the bot's confidence and
 * clicked as {@code ImageClicker} clicks, at a spread point when the bot's settings say so.
 */
public final class RunReach implements Reach {

    /** How long a key is held, as the assistant's hands hold one: a game polling its keys misses a tap with none. */
    private static final int KEY_HOLD_MS = 40;

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
        double needs = BotSettings.current().confidence();
        return ImageFinder.bestMatches(pictures, new StillSource(frame)).stream()
                .map(best -> best.isFound() && best.confidence() >= needs ? Optional.of(best.rect()) : Optional.<Rect>empty())
                .toList();
    }

    /** Looked for only in {@code area}, scaled to the frame as the picture is: its place there, with no margin. */
    @Override
    public Optional<Rect> findWithin(ImageTemplate picture, Rect area, BufferedImage frame) {
        ImageTemplate inArea = picture.at(area);
        try {
            MatchResult best = ImageFinder.bestMatchesNear(List.of(inArea), 0, new StillSource(frame)).getFirst();
            return best.isFound() && best.confidence() >= BotSettings.current().confidence()
                    ? Optional.of(best.rect()) : Optional.empty();
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

    @Override
    public void click(Point at) {
        CaptureSource source = Source.current();
        source.click(onScreen(source, at));
    }

    @Override
    public void key(Key key) {
        CaptureSource source = Source.current();
        Keyboard.press(source, key);
        Wait.milliseconds(KEY_HOLD_MS);
        Keyboard.release(source, key);
    }

    @Override
    public void scroll(Point at, int notches) {
        CaptureSource source = Source.current();
        source.scroll(onScreen(source, at), notches);
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
