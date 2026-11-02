package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.bot.Bot;
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
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.time.Wait;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.internal.emulator.ReadableSource;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A run's reach: the capture source the run reads ({@link Source#current()}), matched at the bot's confidence and
 * clicked as {@code ImageClicker} clicks, at a spread point when the bot's settings say so.
 */
public final class RunReach implements Reach {

    /** How long a key is held, as the assistant's hands hold one: a game polling its keys misses a tap with none. */
    private static final Duration KEY_HOLD = Duration.ofMillis(40);

    /** Between a double tap's two taps on an emulator: well inside a phone's double-tap time. */
    private static final long DOUBLE_TAP_MS = 80;

    /** The tree read for the last frame an element was looked for on. */
    private final FrameTree trees = new FrameTree();

    @Override
    public Optional<BufferedImage> look() {
        return Optional.ofNullable(Source.current().capture());
    }

    @Override
    public boolean readsTrees() {
        return Source.current().base() instanceof ReadableSource;
    }

    /** The source's tree, read once per frame; read after the frame was taken, at the next element looked for. */
    @Override
    public Optional<ElementMatch.Matcher> treeOn(BufferedImage frame) {
        if (!(Source.current().base() instanceof ReadableSource source)) return Optional.empty();
        return trees.on(frame, source::tree);
    }

    /** The run ends, {@code why} its error: retrying cannot change what the bot says. */
    @Override
    public void stop(String why) {
        Bot.stop(why);
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
        List<Optional<Found>> found = new ArrayList<>(ImageFinder.bestMatches(pictures, new StillSource(frame)).stream()
                .map(best -> best.isFound() && best.confidence() >= needs
                        ? Optional.of(new Found(best.rect(), best.confidence())) : Optional.<Found>empty())
                .toList());
        // Each picture not found, tinted as each tint shade shows it, matched on the frame prepared once.
        List<Integer> missing = new ArrayList<>();
        List<ImageTemplate> copies = new ArrayList<>();
        for (Shade tint : tints()) {
            for (int i = 0; i < found.size(); i++) {
                if (found.get(i).isPresent()) continue;
                Optional<ImageTemplate> copy = ShadeFit.tinted(pictures.get(i), tint);
                if (copy.isEmpty()) continue;
                missing.add(i);
                copies.add(copy.get());
            }
        }
        if (!copies.isEmpty()) {
            List<MatchResult> tinted = ImageFinder.bestMatches(copies, new StillSource(frame));
            for (int k = 0; k < copies.size(); k++) {
                MatchResult best = tinted.get(k);
                if (found.get(missing.get(k)).isEmpty() && best.isFound() && best.confidence() >= needs) {
                    found.set(missing.get(k), Optional.of(new Found(best.rect(), best.confidence())));
                }
            }
        }
        rescaled(pictures, frame, found, needs);
        return found;
    }

    /**
     * Each picture drawn at each size the bot's scale shades learned, matched near where it was found, else near its
     * place (anywhere for a picture with none), all on the frame prepared once: a scaled match that scores better
     * replaces the cut's, so its place tells the scale. A small area each, so a map's poll stays nearly as fast.
     */
    /** By how much a scaled copy must outscore the cut's match to replace it: a flat button scores alike at any size. */
    static final double SCALED_BETTER = 0.02;

    private static void rescaled(List<ImageTemplate> pictures, BufferedImage frame, List<Optional<Found>> found,
                                 double needs) {
        List<Double> sizes = ShadeFit.sizes(ShadeFit.learned());
        if (sizes.isEmpty()) return;
        List<Integer> of = new ArrayList<>();
        List<ImageTemplate> copies = new ArrayList<>();
        List<ImageTemplate> own = new ArrayList<>();
        try {
            for (double size : sizes) {
                for (int i = 0; i < pictures.size(); i++) {
                    ImageTemplate picture = pictures.get(i);
                    Optional<ImageTemplate> copy = found.get(i)
                            .flatMap(at -> ShadeFit.scaledAround(picture, size, at.place(), frame));
                    copy.ifPresent(own::add);
                    if (copy.isEmpty() && found.get(i).isEmpty()) copy = ShadeFit.scaled(picture, size);
                    if (copy.isEmpty()) continue;
                    of.add(i);
                    copies.add(copy.get());
                }
            }
            if (copies.isEmpty()) return;
            List<MatchResult> bests = ImageFinder.bestMatchesNear(copies, MARKER_MARGIN, new StillSource(frame));
            for (int k = 0; k < copies.size(); k++) {
                MatchResult best = bests.get(k);
                Optional<Found> was = found.get(of.get(k));
                if (best.isFound() && best.confidence() >= needs
                        && (was.isEmpty() || best.confidence() > was.get().score() + SCALED_BETTER)) {
                    found.set(of.get(k), Optional.of(new Found(best.rect(), best.confidence())));
                }
            }
        } finally {
            // Placed on a match: copies of their own, their pixels native memory.
            own.forEach(ImageTemplate::close);
        }
    }

    /** The bot's tint shades: a tint drops a match below the confidence, so the cut is matched tinted too. */
    private static List<Shade> tints() {
        return ShadeFit.learned().stream().filter(shade -> shade.kind() == Shade.Kind.TINT).toList();
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

    /**
     * Every copy, and when none shows, every copy tinted as the first tint shade that finds one shows it, else drawn
     * at the first learned size that finds one.
     */
    @Override
    public List<Found> findAllScored(ImageTemplate picture, BufferedImage frame) {
        double needs = BotSettings.current().confidence();
        List<Found> all = allOf(picture, frame, needs);
        if (!all.isEmpty()) return all;
        // Each copy made only when the one before found nothing.
        Stream<Supplier<Optional<ImageTemplate>>> copies = Stream.concat(
                tints().stream().map(tint -> () -> ShadeFit.tinted(picture, tint)),
                ShadeFit.sizes(ShadeFit.learned()).stream().map(size -> () -> ShadeFit.scaled(picture, size)));
        return copies.map(Supplier::get).flatMap(Optional::stream)
                .map(copy -> allOf(copy, frame, needs))
                .filter(shown -> !shown.isEmpty())
                .findFirst().orElse(all);
    }

    private static List<Found> allOf(ImageTemplate picture, BufferedImage frame, double needs) {
        return ImageFinder.allMatches(picture, new StillSource(frame), needs).stream()
                .map(match -> new Found(match.rect(), match.confidence())).toList();
    }

    /**
     * The bot's learned shade the picture's file shows where it matched: the game's always, the ones its own input
     * causes on the button that input is on.
     */
    @Override
    public Optional<Shade> shade(ImageTemplate picture, Rect found, BufferedImage frame) {
        List<Shade> shades = ShadeFit.learned();
        if (shades.isEmpty()) return Optional.empty();
        return ShadeFit.shown(picture, found, frame, shades, ShadeFit.causes(found, ShadeFit.pointer(), nowMs()));
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
        boolean emulator = source.base() instanceof EmulatorSource;
        // Stamped when let go: a hold's press shows after it as a click's does.
        try {
            pressAs(source, emulator, at, gesture, hold);
        } finally {
            ShadeFit.moved(new ShadeFit.Pointer(at, nowMs(), gesture != Gesture.HOVER, !emulator));
        }
    }

    private void pressAs(CaptureSource source, boolean emulator, Point at, Gesture gesture, Duration hold) {
        if (emulator && gesture != Gesture.HOLD) {
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
        // The cursor rests where it scrolled; nothing there was pressed.
        ShadeFit.moved(new ShadeFit.Pointer(at, nowMs(), false, !(source.base() instanceof EmulatorSource)));
    }

    @Override
    public void drag(Point from, Point to, Duration time) {
        CaptureSource source = Source.current();
        source.drag(onScreen(source, from), onScreen(source, to), time.toMillis());
        ShadeFit.moved(new ShadeFit.Pointer(to, nowMs(), false, !(source.base() instanceof EmulatorSource)));
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
