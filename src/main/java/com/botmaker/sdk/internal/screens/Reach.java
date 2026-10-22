package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Caption;
import com.botmaker.sdk.api.screens.Drag;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Place;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Scroll;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.ocr.LineMatch;
import com.botmaker.sdk.internal.ocr.OcrEngine;
import com.botmaker.sdk.internal.vision.BotScreen;
import com.botmaker.sdk.internal.vision.DigitReader;
import com.botmaker.sdk.internal.vision.Places;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.function.ToLongFunction;

/**
 * What acts on the game, for whoever acts: a run ({@code RunReach}) or the assistant's tools ({@code ToolReach}).
 * Points are in the frame's pixels, its top-left (0, 0). {@link #perform} is the one place an {@link Input} is done,
 * so a picture the assistant tries is found and clicked as a run would find and click it.
 */
public interface Reach {

    /**
     * The longest a strip is let settle after a scroll before it is looked at again, and between two looks after: a
     * strip that settles fast is looked at sooner, a sixth of its {@link Scroll#settle}, down to {@link #MEASURE_MS}.
     */
    long SCROLL_SETTLE_MS = 400;

    /** How often a strip is looked at while {@link #measure} times it: the shortest settle it can tell. */
    long MEASURE_MS = 100;

    /** Scrolls in a row the first way with no member showing before the strip is scrolled the other way. */
    int BLIND_SCROLLS = 3;

    /** How long the game is let settle between two copies of a button {@link Pick#each} presses. */
    long CLICK_SETTLE_MS = 600;

    /** How long a text field is given to take its click before its text is typed. */
    long FIELD_FOCUS_MS = 200;

    /** How far a member may shift between two looks and still count as not moved: a match is found to a pixel or two. */
    int STILL_PX = 4;

    /** The game's screen now; empty when it could not be captured. */
    Optional<BufferedImage> look();

    /** Where {@code picture} is on {@code frame} when it clears this reach's confidence; empty otherwise. */
    Optional<Rect> find(ImageTemplate picture, BufferedImage frame);

    /** {@link #find} for each of {@code pictures} on one frame, in their order. */
    default List<Optional<Rect>> findEach(List<ImageTemplate> pictures, BufferedImage frame) {
        return pictures.stream().map(picture -> find(picture, frame)).toList();
    }

    /** A picture found: where, and how well it matched, from 0 to 1. */
    record Found(Rect place, double score) {}

    /** {@link #findAll} with each match's score; by default each scores 1. */
    default List<Found> findAllScored(ImageTemplate picture, BufferedImage frame) {
        return findAll(picture, frame).stream().map(place -> new Found(place, 1)).toList();
    }

    /**
     * {@link #findEach} with each match's score: of a button's looks that all clear the confidence, the one that
     * matches best is the one shown. By default each scores 1, so the first found wins.
     */
    default List<Optional<Found>> findEachScored(List<ImageTemplate> pictures, BufferedImage frame) {
        return findEach(pictures, frame).stream().map(at -> at.map(place -> new Found(place, 1))).toList();
    }

    /**
     * How far from its cut place a marker is looked for, in the pixels of the frame it was cut from, scaled with the
     * frame: a title moves a few pixels at most.
     */
    int MARKER_MARGIN = 60;

    /**
     * {@link #findEach}, each picture looked for only within {@link #MARKER_MARGIN} of its place (anywhere when it has
     * none): what tells a screen sits where it was cut. A look for every marker over the whole frame took Firestone's
     * walk 11 s at each poll (2026-10-16).
     */
    default List<Optional<Rect>> findEachNear(List<ImageTemplate> pictures, BufferedImage frame) {
        return findEach(pictures, frame);
    }

    /**
     * The lines of text {@code image} shows, each with its place in it, read by OCR as {@code Text} reads them. A read
     * that fails reads none; natives that cannot load still throw, as they do for {@code Text}, so they never pass for
     * a screen with no words.
     */
    default List<TextResult> read(BufferedImage image) {
        return ocr(image);
    }

    /** {@link #read} by the SDK's OCR, for whoever reads with no reach: the audit of a shot. */
    static List<TextResult> ocr(BufferedImage image) {
        try {
            return OcrEngine.recognize(image, Text.DEFAULT_OPTIONS);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Where {@code caption}'s words are on {@code frame}: the first line read inside its zone, placed on the frame as
     * {@code source.region} places one, that is the words ({@link LineMatch#same}). Empty when no line there is.
     */
    default Optional<Rect> findText(Caption caption, BufferedImage frame) {
        return findText(caption, frame, this::read);
    }

    /** {@link #findText(Caption, BufferedImage)}, the zone read by {@code read}. */
    static Optional<Rect> findText(Caption caption, BufferedImage frame,
                                   Function<BufferedImage, List<TextResult>> read) {
        if (caption == null || caption.words() == null || caption.in() == null) return Optional.empty();
        Optional<Rect> zone = onFrame(caption.in(), frame);
        if (zone.isEmpty()) return Optional.empty();
        Rect z = zone.get();
        for (TextResult line : read.apply(frame.getSubimage(z.x(), z.y(), z.width(), z.height()))) {
            if (!LineMatch.same(line.text(), caption.words().text())) continue;
            Rect b = line.bounds();
            return Optional.of(new Rect(b.x() + z.x(), b.y() + z.y(), b.width(), b.height()));
        }
        return Optional.empty();
    }

    /** {@code zone} placed on {@code frame} as {@code source.region} places it, cut to the frame; empty off it. */
    private static Optional<Rect> onFrame(Zone zone, BufferedImage frame) {
        Rect placed = Places.inSource(zone, BotScreen.size().orElse(null),
                new Size(frame.getWidth(), frame.getHeight()));
        int x = Math.max(0, placed.x());
        int y = Math.max(0, placed.y());
        int w = Math.min(frame.getWidth(), placed.x() + placed.width()) - x;
        int h = Math.min(frame.getHeight(), placed.y() + placed.height()) - y;
        return w <= 0 || h <= 0 ? Optional.empty() : Optional.of(new Rect(x, y, w, h));
    }

    /**
     * What {@code readout} shows on {@code frame}: the lines read in its zone, joined by a space, or the number its
     * digits spell there. Empty when nothing is read.
     */
    default Optional<String> readout(Readout readout, BufferedImage frame) {
        if (readout == null || readout.zone() == null) return Optional.empty();
        Optional<Rect> zone = onFrame(readout.zone(), frame);
        if (zone.isEmpty()) return Optional.empty();
        Rect z = zone.get();
        if (readout.isNumber()) {
            OptionalLong number = number(readout.digits(), z, frame);
            return number.isPresent() ? Optional.of(Long.toString(number.getAsLong())) : Optional.empty();
        }
        String text = read(frame.getSubimage(z.x(), z.y(), z.width(), z.height())).stream()
                .map(line -> line.text().strip()).filter(line -> !line.isEmpty()).collect(Collectors.joining(" "));
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    /** The number {@code digits}' pictures read in {@code area} of {@code frame}, at a run's confidence; empty for none. */
    default OptionalLong number(DigitSet digits, Rect area, BufferedImage frame) {
        return digitsIn(digits, area, frame, BotSettings.current().confidence());
    }

    /** {@link #number} at {@code confidence}; a read that fails reads none. */
    static OptionalLong digitsIn(DigitSet digits, Rect area, BufferedImage frame, double confidence) {
        try {
            return DigitReader.read(frame, area, digits, confidence);
        } catch (RuntimeException | LinkageError unreadable) {
            return OptionalLong.empty();
        }
    }

    /** Presses at {@code at} by {@code gesture}, a {@link Gesture#HOLD} held {@code hold}; anything unknown clicked. */
    void press(Point at, Gesture gesture, Duration hold);

    /** Presses {@code combo}'s keys together, held as it says, else a game's tap. */
    void keys(Combo combo);

    /** One left click at {@code at}. */
    default void click(Point at) {
        press(at, Gesture.CLICK, null);
    }

    /** {@code key} pressed. */
    default void key(Key key) {
        keys(Combo.of(key));
    }

    /**
     * Types {@code text} into the field just clicked: what it holds selected first when {@code clears}, so the text
     * takes its place, and Enter pressed after when {@code submits}.
     */
    void type(String text, boolean clears, boolean submits);

    /**
     * {@code button} pressed at {@code place}, where it was found on {@code frame}, by its gesture; a text field's text
     * typed after, once the field has taken the click; a drag moved from there to its drop on {@code frame}. A field
     * whose body gives its text types nothing here: {@code ScreenWalk.press} hands it the body's text as {@link
     * Button#types}. False when a drag's drop is not on {@code frame}, and nothing was done.
     */
    default boolean press(Button button, Rect place, BufferedImage frame) {
        if (button.isDrag()) {
            Optional<Point> drop = dropOn(button, frame);
            if (drop.isEmpty()) return false;
            drag(pointOn(place), drop.get(), button.dragTime());
            return true;
        }
        press(pointOn(place), button.gesture(), button.holdTime());
        if (button.text() == null) return true;
        pause(FIELD_FOCUS_MS);
        type(button.text().text(), button.isClearing(), button.isSubmitting());
        return true;
    }

    /**
     * Where {@code button}'s drag lets go on {@code frame}: on its picture, found as any picture is, or at the centre
     * of its zone, placed on the frame as {@code source.region} places one. Empty when it is no drag, or its picture is
     * not there.
     */
    default Optional<Point> dropOn(Button button, BufferedImage frame) {
        Drag drag = button.drag();
        if (drag == null) return Optional.empty();
        if (drag.onto() != null) return find(drag.onto(), frame).map(this::pointOn);
        if (drag.into() == null) return Optional.empty();
        Size live = new Size(frame.getWidth(), frame.getHeight());
        return Optional.of(Places.inSource(drag.into(), BotScreen.size().orElse(null), live).center());
    }

    void scroll(Point at, int notches);

    /** Presses at {@code from}, moves to {@code to} over {@code time}, and lets go. */
    void drag(Point from, Point to, Duration time);

    /** {@code scroll} done once over {@code over}: the wheel turned there, or the pointer dragged from there. */
    default void scroll(Point over, Scroll scroll) {
        if (scroll.drag() == null) {
            scroll(over, scroll.notches());
            return;
        }
        double side = scroll.px() / Math.sqrt(2);
        int px = scroll.px();
        int d = (int) Math.round(side);
        Point to = switch (scroll.drag()) {
            case NORTH -> new Point(over.x(), over.y() - px);
            case SOUTH -> new Point(over.x(), over.y() + px);
            case EAST -> new Point(over.x() + px, over.y());
            case WEST -> new Point(over.x() - px, over.y());
            case NORTH_EAST -> new Point(over.x() + d, over.y() - d);
            case NORTH_WEST -> new Point(over.x() - d, over.y() - d);
            case SOUTH_EAST -> new Point(over.x() + d, over.y() + d);
            case SOUTH_WEST -> new Point(over.x() - d, over.y() + d);
            case CENTER -> over;
        };
        drag(over, to, scroll.time());
    }

    /**
     * How far the view moved from {@code before} to {@code after}, in pixels, right and down positive: read off the
     * pixels, for a tree no node of which shows on both. Empty when it cannot tell.
     */
    default Optional<Point> shift(BufferedImage before, BufferedImage after) {
        return FrameShift.of(before, after);
    }

    /** Milliseconds on a clock that only goes forward. */
    long nowMs();

    /** Waits {@code ms}; a stop or an interrupt ends it early, and the caller reads the thread's interrupt. */
    void pause(long ms);

    /** Where on {@code place} a click lands: its centre unless this reach spreads its clicks. */
    default Point pointOn(Rect place) {
        return place.center();
    }

    /** A button as {@code frame} shows it: which of its looks (what it means, and its picture), and where. */
    record Seen(State state, Rect place, ImageTemplate picture) {}

    /**
     * Where {@code picture} is anywhere in {@code area} on {@code frame}, {@code area} in the frame the picture was cut
     * from, when it clears this reach's confidence: a member of a {@link Strip}. By default it is found anywhere and
     * kept only when its centre is in {@code area}; a reach that matches through the SDK's matcher looks only there,
     * scaled as the picture is.
     */
    default Optional<Rect> findWithin(ImageTemplate picture, Rect area, BufferedImage frame) {
        return find(picture, frame).filter(at -> area.contains(at.center()));
    }

    /** {@link #findWithin} with the match's score; by default it scores 1. */
    default Optional<Found> findWithinScored(ImageTemplate picture, Rect area, BufferedImage frame) {
        return findWithin(picture, area, frame).map(place -> new Found(place, 1));
    }

    /** {@link #seen(Button, Strip, Pick, BufferedImage)} of a button at its place, in no strip, its first copy. */
    default Optional<Seen> seen(Button button, BufferedImage frame) {
        return seen(button, null, null, frame);
    }

    /**
     * How {@code button} looks on {@code frame}: its looks looked for before its usual picture, since a badge or a
     * grey copy of a button also passes for the button itself; a member of {@code strip}, anywhere along it. A
     * repeated button is the copy {@code pick} names (null: the first), and every copy for {@link Pick.Mode#EACH}
     * starts at the first. A button {@link Button#place placed}, or a pick {@link Pick#at at} a place, is the copy
     * nearest the place's anchor that meets it; a grid's {@link Pick#cell cell} is that cell. Empty for a button not
     * there.
     */
    default Optional<Seen> seen(Button button, Strip strip, Pick pick, BufferedImage frame) {
        Pick p = pick == null ? Pick.first() : pick;
        if (p.mode() == Pick.Mode.CELL) return cell(button, p, frame);
        if (button.repeats() || button.place() != null || p.place() != null) {
            List<Seen> all = copies(button, strip, p.order(), frame);
            List<Seen> copies = p.place() == null ? all : placed(all, p.place(), frame);
            if (p.mode() == Pick.Mode.UNTIL_NONE) {
                // The first copy still to press; every one greyed out, the first: a Claim under grey ones (Firestone's
                // Quests, 2026-10-17).
                return copies.stream().filter(copy -> !copy.state().blocks()).findFirst()
                        .or(() -> copies.stream().findFirst());
            }
            int at = p.mode() == Pick.Mode.NTH ? p.nth() - 1 : 0;
            return at < copies.size() ? Optional.of(copies.get(at)) : Optional.empty();
        }
        List<Look> order = lookOrder(button);
        if (order.isEmpty()) return Optional.empty();
        Rect band = band(strip);
        List<Optional<Found>> found = band == null
                ? findEachScored(order.stream().map(Look::picture).toList(), frame)
                : order.stream().map(look -> findWithinScored(look.picture(), band, frame)).toList();
        // The best match wins, the first in order on a tie: a green Play passed for its grey look too, 0.82 to its
        // own 0.91 (Firestone's Tavern, 2026-10-17); the usual look only by more than LOOK_TIE.
        int best = -1;
        for (int i = 0; i < order.size(); i++) {
            if (found.get(i).isPresent() && (best < 0 || found.get(i).get().score()
                    > found.get(best).get().score() + tie(order.get(i)))) {
                best = i;
            }
        }
        return best < 0 ? Optional.empty()
                : Optional.of(new Seen(order.get(best).state(), button.spots(placeOf(button, order.get(best),
                found.get(best).get().place())).getFirst(), order.get(best).picture()));
    }

    /**
     * {@code pick}'s cell of {@code button}'s grid, its anchor found at its best match; empty for a button of no grid,
     * a cell past its rows or columns, or an anchor not there.
     */
    private Optional<Seen> cell(Button button, Pick pick, BufferedImage frame) {
        if (button.grid() == null || button.picture() == null || pick.column() > button.grid().columns()
                || pick.nth() > button.grid().rows()) {
            return Optional.empty();
        }
        int at = (pick.nth() - 1) * button.grid().columns() + pick.column() - 1;
        return find(button.picture(), frame).map(anchor -> new Seen(State.NORMAL, button.spots(anchor).get(at),
                button.picture()));
    }

    /** {@code copies} that meet {@code place} on {@code frame}, nearest its anchor first; none when it is not there. */
    private List<Seen> placed(List<Seen> copies, Place place, BufferedImage frame) {
        Optional<Rect> anchor = anchorOf(place.picture(), place.words(), frame);
        if (anchor.isEmpty()) return List.of();
        Rect other = null;
        if (place.side() == Place.Side.BETWEEN) {
            Optional<Rect> second = anchorOf(place.otherPicture(), place.otherWords(), frame);
            if (second.isEmpty()) return List.of();
            other = second.get();
        }
        return Placed.meeting(copies, Seen::place, place.side(), anchor.get(), other);
    }

    /** Where an anchor shows on {@code frame}: its picture found, or its words read in their zone. */
    private Optional<Rect> anchorOf(ImageTemplate picture, Caption words, BufferedImage frame) {
        if (picture != null) return find(picture, frame);
        return words == null ? Optional.empty() : findText(words, frame);
    }

    /**
     * How much better than another look the usual one must match to be taken: a button's usual picture cut in some
     * state matches that state as well as the look cut for it does, and the state is told by what differs. A tree
     * node seen only maxed has its usual picture cut on the maxed card; both scored 1.00 and the node was pressed
     * (Firestone's Tree I, 2026-10-17).
     */
    double LOOK_TIE = 0.02;

    private static double tie(Look look) {
        return look.state() == State.NORMAL ? LOOK_TIE : 0;
    }

    /**
     * Where {@code button} itself sits when its {@code look} is found at {@code found}: a look cut at another place
     * than the button's picture, a node's whole card that says Max level, is moved back by where each was cut, scaled
     * as it was found, so a tree's offsets and a click land on the button whatever look it shows (Firestone's Tree I,
     * 2026-10-17). The place found when either place is unknown.
     */
    private static Rect placeOf(Button button, Look look, Rect found) {
        Rect own = button.picture() == null ? null : button.picture().place();
        Rect cut = look.picture().place();
        if (own == null || cut == null || cut.equals(own) || cut.width() <= 0 || cut.height() <= 0) return found;
        double sx = (double) found.width() / cut.width();
        double sy = (double) found.height() / cut.height();
        return new Rect(found.x() + (int) Math.round((own.x() - cut.x()) * sx),
                found.y() + (int) Math.round((own.y() - cut.y()) * sy),
                (int) Math.round(own.width() * sx), (int) Math.round(own.height() * sy));
    }

    /**
     * Every place {@code picture} clears this reach's confidence on {@code frame}, in no order: the copies of a
     * repeated button. By default only its best match; a reach that matches through the SDK's matcher finds them all.
     */
    default List<Rect> findAll(ImageTemplate picture, BufferedImage frame) {
        return find(picture, frame).map(List::of).orElse(List.of());
    }

    /**
     * Every copy of {@code button} on {@code frame}, along {@code order}, each in the look it shows: a copy is found by
     * any of its looks, its other looks before its usual picture, and a place another look already holds is that
     * copy. Only those along {@code strip}, for a member of one. A button pressed {@link Button#beside} its picture is
     * each spot, or cell, of each copy of the picture; a {@link Button#place placed} one only the copies that meet its
     * place, nearest the anchor first.
     */
    default List<Seen> copies(Button button, Strip strip, Order order, BufferedImage frame) {
        List<Seen> found = pictureCopies(button, strip, frame);
        if (button.isBeside()) {
            found = found.stream().flatMap(copy -> button.spots(copy.place()).stream()
                    .map(spot -> new Seen(copy.state(), spot, copy.picture()))).toList();
        }
        if (button.place() != null) return placed(found, button.place(), frame);
        return ordered(found, Seen::place, order, new Size(frame.getWidth(), frame.getHeight()));
    }

    /** Every copy of {@code button}'s picture on {@code frame}, in no order, as {@link #copies} finds them. */
    private List<Seen> pictureCopies(Button button, Strip strip, BufferedImage frame) {
        Rect band = band(strip);
        List<Seen> all = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Look look : lookOrder(button)) {
            for (Found found : findAllScored(look.picture(), frame)) {
                Rect at = placeOf(button, look, found.place());
                if (band != null && !band.contains(at.center())) continue;
                int same = -1;
                for (int i = 0; i < all.size() && same < 0; i++) {
                    if (all.get(i).place().contains(at.center())) same = i;
                }
                // A place two looks match is the look that matches it best: a green Claim passed for a grey one too.
                if (same < 0) {
                    all.add(new Seen(look.state(), at, look.picture()));
                    scores.add(found.score());
                } else if (found.score() > scores.get(same) + tie(look)) {
                    all.set(same, new Seen(look.state(), at, look.picture()));
                    scores.set(same, found.score());
                }
            }
        }
        return all;
    }

    /** {@code button}'s looks in the order they are looked for: a badge or a grey copy also passes for the button. */
    private static List<Look> lookOrder(Button button) {
        List<Look> looks = button.allLooks();
        if (looks.isEmpty()) return List.of();
        List<Look> order = new ArrayList<>(looks.subList(1, looks.size()));
        order.add(looks.getFirst());
        return order;
    }

    /** {@code items} in reading order by their {@code place}: {@link Order#NORTH}. */
    static <T> List<T> readingOrder(List<T> items, Function<T, Rect> place) {
        return ordered(items, place, Order.NORTH, null);
    }

    /**
     * {@code items} along {@code order} by their {@code place}, on a frame of {@code frame}'s size (needed for
     * {@link Order#CENTER} only). A side goes row by row, or column by column: two whose centres are less than half
     * the first one's height (its width, for a column) apart are on one row.
     */
    static <T> List<T> ordered(List<T> items, Function<T, Rect> place, Order order, Size frame) {
        Function<T, Point> at = item -> place.apply(item).center();
        return switch (order) {
            case NORTH -> lines(items, place, Comparator.comparingInt(i -> at.apply(i).y()), true,
                    Comparator.comparingInt(i -> at.apply(i).x()));
            case SOUTH -> lines(items, place, Comparator.comparingInt(i -> -at.apply(i).y()), true,
                    Comparator.comparingInt(i -> at.apply(i).x()));
            case WEST -> lines(items, place, Comparator.comparingInt(i -> at.apply(i).x()), false,
                    Comparator.comparingInt(i -> at.apply(i).y()));
            case EAST -> lines(items, place, Comparator.comparingInt(i -> -at.apply(i).x()), false,
                    Comparator.comparingInt(i -> at.apply(i).y()));
            case NORTH_EAST -> sorted(items, i -> at.apply(i).y() - at.apply(i).x(), at);
            case NORTH_WEST -> sorted(items, i -> at.apply(i).x() + at.apply(i).y(), at);
            case SOUTH_EAST -> sorted(items, i -> -(at.apply(i).x() + at.apply(i).y()), at);
            case SOUTH_WEST -> sorted(items, i -> at.apply(i).x() - at.apply(i).y(), at);
            case CENTER -> sorted(items, i -> distance(at.apply(i), frame), at);
        };
    }

    private static <T> List<T> sorted(List<T> items, ToLongFunction<T> key, Function<T, Point> at) {
        List<T> all = new ArrayList<>(items);
        all.sort(Comparator.comparingLong(key).thenComparingInt(i -> at.apply(i).y())
                .thenComparingInt(i -> at.apply(i).x()));
        return all;
    }

    private static long distance(Point at, Size frame) {
        if (frame == null) return 0;
        long dx = at.x() - frame.width() / 2;
        long dy = at.y() - frame.height() / 2;
        return dx * dx + dy * dy;
    }

    /**
     * {@code items} in lines along {@code across} (rows when {@code rows}, else columns), each line along
     * {@code along}.
     */
    private static <T> List<T> lines(List<T> items, Function<T, Rect> place, Comparator<T> across, boolean rows,
                                     Comparator<T> along) {
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(across);
        List<T> ordered = new ArrayList<>();
        List<T> line = new ArrayList<>();
        for (T item : sorted) {
            if (!line.isEmpty()) {
                Rect first = place.apply(line.getFirst());
                Rect now = place.apply(item);
                int apart = rows ? Math.abs(now.center().y() - first.center().y())
                        : Math.abs(now.center().x() - first.center().x());
                if (apart * 2 >= (rows ? first.height() : first.width())) {
                    line.sort(along);
                    ordered.addAll(line);
                    line.clear();
                }
            }
            line.add(item);
        }
        line.sort(along);
        ordered.addAll(line);
        return ordered;
    }

    /** {@link #perform(Input, Strip, Pick, BufferedImage)} of an input at its place, in no strip, its first copy. */
    default boolean perform(Input input, BufferedImage frame) {
        return perform(input, null, null, frame);
    }

    /**
     * Does {@code input} on {@code frame}: a key pressed; a strip scrolled once over a member that shows (the middle
     * of its band when none does); a button found, in any look but a disabled one, and pressed by its gesture, or
     * dragged to its drop when that is on the frame too. A member of
     * {@code strip} not showing is scrolled to first: the strip's scroll is done over a member that shows, up to its
     * {@link Scroll#most} times each way, the game looked at again once the strip holds still. A scroll after which
     * no member moved a quarter of the strip's step met its end, and the scroll turns the other way once: the
     * members' places say it, never the pixels,
     * since a strip may sit over a moving fight. False when the button is not there or greyed out, and nothing but
     * those scrolls was done.
     *
     * <p>A repeated button is pressed on the copy {@code pick} names; {@link Pick#each} presses every copy the frame
     * shows but a disabled one, the game let settle {@link #CLICK_SETTLE_MS} between them, last to first along its
     * order: a card claimed and gone shifts the ones after it, never one still to press. False for that one when no
     * copy was pressed.
     */
    default boolean perform(Input input, Strip strip, Pick pick, BufferedImage frame) {
        return switch (input) {
            case Hotkey hotkey -> {
                keys(hotkey.combo());
                yield true;
            }
            case TextButton words -> {
                Optional<Rect> place = findText(words.caption(), frame);
                place.ifPresent(at -> press(pointOn(at), words.gesture(), words.holdTime()));
                yield place.isPresent();
            }
            // A tree is walked to one of its nodes, never pressed as a whole.
            case Tree ignored -> false;
            case Strip scrolled -> {
                Map<String, Rect> shown = members(scrolled, frame);
                Rect band = band(scrolled);
                Point over = !shown.isEmpty() ? shown.values().iterator().next().center()
                        : band == null ? null : band.center();
                if (over == null || scrolled.scroll() == null) yield false;
                scroll(over, scrolled.scroll());
                yield true;
            }
            case Button button -> {
                if (button.repeats() && pick != null && pick.mode() == Pick.Mode.EACH) {
                    yield performEach(button, strip, pick, frame);
                }
                Optional<Seen> seen = inView(button, strip, pick, frame);
                if (seen.isEmpty() || seen.get().state().blocks()) yield false;
                yield press(button, seen.get().place(), frame);
            }
        };
    }

    /** {@link #perform} of a repeated button on every copy. */
    private boolean performEach(Button button, Strip strip, Pick pick, BufferedImage frame) {
        BufferedImage on = frame;
        if (seen(button, strip, pick, frame).isEmpty()) {
            // Scrolled to, the copies are read off the strip as it now stands.
            if (inView(button, strip, pick, frame).isEmpty()) return false;
            Optional<BufferedImage> now = look();
            if (now.isEmpty()) return false;
            on = now.get();
        }
        boolean any = false;
        for (Seen copy : copies(button, strip, pick.order(), on).reversed()) {
            if (copy.state().blocks()) continue;
            if (any) {
                pause(CLICK_SETTLE_MS);
                if (Thread.currentThread().isInterrupted()) return true;
            }
            // A drag after the first is let go where its drop is now: the last one may have filled the slot it found.
            BufferedImage drops = any && button.isDrag() ? look().orElse(on) : on;
            any |= press(button, copy.place(), drops);
        }
        return any;
    }

    /**
     * {@code button} on {@code frame}, or once {@code strip} is scrolled to it, as {@link #perform} scrolls; empty when
     * neither way brought it. Nothing but scrolls is done: the assistant's try_input brings a member into view by it
     * before clicking it as a run would.
     */
    default Optional<Seen> inView(Button button, Strip strip, Pick pick, BufferedImage frame) {
        Optional<Seen> seen = seen(button, strip, pick, frame);
        return seen.isPresent() || strip == null || strip.scroll() == null || !strip.scroll().moves() ? seen
                : scrolledTo(button, strip, pick, frame);
    }

    /**
     * {@code button} once {@code strip} is scrolled to it; empty when neither way brought it. The way back gets the
     * scrolls the first way took on top of its own, so it searches as far past the start as the first did.
     */
    private Optional<Seen> scrolledTo(Button button, Strip strip, Pick pick, BufferedImage frame) {
        Map<String, Rect> members = members(strip, frame);
        // Scrolled past every member, the wheel stays where the last one showed: a point in the strip. With none
        // showing yet (scrolled to an end of icons the strip does not name), it turns over the middle of the band.
        Rect band = band(strip);
        Point over = band == null ? null : band.center();
        Scroll scroll = strip.scroll();
        int done = 0;
        boolean first = true;
        boolean shown = !members.isEmpty();
        for (Scroll way : List.of(scroll, scroll.back())) {
            int most = scroll.most() + done;
            done = 0;
            int blind = 0;
            for (int i = 0; i < most; i++) {
                if (!members.isEmpty()) over = members.values().iterator().next().center();
                if (over == null) return Optional.empty();
                scroll(over, way);
                done++;
                Optional<Settled> next = settled(strip, members);
                if (next.isEmpty()) return Optional.empty();
                Optional<Seen> seen = seen(button, strip, pick, next.get().frame());
                if (seen.isPresent()) return seen;
                Map<String, Rect> now = next.get().members();
                boolean end = !now.isEmpty() && still(members, now, endPx(scroll));
                // No member shown yet, the first way: an end of icons the strip does not name, scrolled further in.
                shown |= !now.isEmpty();
                blind = shown ? 0 : blind + 1;
                members = now;
                if (end || first && blind >= BLIND_SCROLLS) break;
            }
            first = false;
        }
        return Optional.empty();
    }

    /** A frame of a strip that held still, and where its members show on it. */
    record Settled(BufferedImage frame, Map<String, Rect> members) {
    }

    /**
     * {@code strip} once it holds still after a scroll: looked at every {@link #poll} until two looks in a row show its
     * members at the same places, for up to its scroll's {@link Scroll#settle}. Members still where they were
     * {@code before} the scroll are looked at to the end of that time: a game may start gliding late, and only then
     * is the strip at its end. Empty when the game could not be captured or the wait was stopped.
     */
    private Optional<Settled> settled(Strip strip, Map<String, Rect> before) {
        Scroll scroll = strip.scroll();
        long until = nowMs() + scroll.settle().toMillis();
        long poll = poll(scroll);
        Settled last = null;
        while (true) {
            pause(poll);
            Optional<BufferedImage> frame = look();
            if (frame.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
            Settled now = new Settled(frame.get(), members(strip, frame.get()));
            boolean held = last != null && still(last.members(), now.members())
                    && (before.isEmpty() || !still(before, now.members(), endPx(scroll)));
            if (held || nowMs() >= until) return Optional.of(now);
            last = now;
        }
    }

    /** How long between two looks at a strip settling: a sixth of its settle, within {@link #MEASURE_MS} and 400 ms. */
    static long poll(Scroll scroll) {
        return Math.clamp(scroll.settle().toMillis() / 6, MEASURE_MS, SCROLL_SETTLE_MS);
    }

    /**
     * How little a member may move on a scroll for the strip to be at its end: a quarter of its measured
     * {@link Scroll#step}, so a game that bounces a few pixels at its end is not taken for one still scrolling;
     * {@link #STILL_PX} for a strip not measured yet.
     */
    static int endPx(Scroll scroll) {
        return Math.max(STILL_PX, scroll.step() / 4);
    }

    /**
     * {@code strip} scrolled once by its own scroll, over a member {@code frame} shows, and timed: how far its members
     * moved and how long they took to hold still (two calm looks in a row), looked at every {@link #MEASURE_MS} for
     * up to its {@link Scroll#settle} or {@link Scroll#SETTLE}, the longer. The scroll answered has that
     * {@link Scroll#step} and a settle of half as long again plus 200 ms, rounded up to 100 ms: a strip is judged by
     * its own speed, with room for a slow frame. Empty when no member shows, none shows both before and after, nothing
     * moved (an end), or the game could not be captured; the strip stays scrolled either way. Discovery measures a
     * strip by it.
     */
    default Optional<Scroll> measure(Strip strip, BufferedImage frame) {
        Scroll scroll = strip.scroll();
        if (scroll == null || !scroll.moves()) return Optional.empty();
        Map<String, Rect> before = members(strip, frame);
        if (before.isEmpty()) return Optional.empty();
        long start = nowMs();
        long until = start + Math.max(scroll.settle().toMillis(), Scroll.SETTLE.toMillis());
        scroll(before.values().iterator().next().center(), scroll);
        Map<String, Rect> last = null;
        long movedAt = start;
        int calm = 0;
        while (true) {
            pause(MEASURE_MS);
            Optional<BufferedImage> look = look();
            if (look.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
            Map<String, Rect> now = members(strip, look.get());
            long at = nowMs();
            // Two calm looks in a row: an eased glide moves under STILL_PX in one look near its end.
            if (last != null && still(last, now) && !still(before, now)) {
                if (++calm >= 2) break;
            } else {
                calm = 0;
                movedAt = at;
            }
            if (at >= until) return Optional.empty();
            last = now;
        }
        List<Integer> moved = new ArrayList<>();
        for (Map.Entry<String, Rect> was : before.entrySet()) {
            Rect to = last.get(was.getKey());
            if (to == null) continue;
            Point a = was.getValue().center();
            Point b = to.center();
            moved.add(Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y())));
        }
        // No member seen both before and after (a scroll that carries them all out of view): how far is not known.
        if (moved.isEmpty()) return Optional.empty();
        int step = moved.stream().sorted().toList().get(moved.size() / 2);
        long took = movedAt - start;
        long settle = (took * 3 / 2 + 200 + 99) / 100 * 100;
        return Optional.of(scroll.step(step).settle(Duration.ofMillis(settle)));
    }

    /** Where each member of {@code strip} that shows is on {@code frame}, by its id, in the strip's order. */
    private Map<String, Rect> members(Strip strip, BufferedImage frame) {
        Rect band = band(strip);
        Map<String, Rect> found = new LinkedHashMap<>();
        for (ImageTemplate member : strip.members()) {
            (band == null ? find(member, frame) : findWithin(member, band, frame))
                    .ifPresent(at -> found.put(member.id(), at));
        }
        return found;
    }

    /** Whether the same members show at the same places, to {@link #STILL_PX}: the strip did not move. */
    /**
     * Whether a member seen both {@code before} and {@code after} moved more than {@link #STILL_PX}: one gone behind a
     * popup or a tooltip is no move.
     */
    static boolean moved(Map<String, Rect> before, Map<String, Rect> after) {
        for (Map.Entry<String, Rect> was : before.entrySet()) {
            Rect now = after.get(was.getKey());
            if (now == null) continue;
            Point a = was.getValue().center();
            Point b = now.center();
            if (Math.abs(a.x() - b.x()) > STILL_PX || Math.abs(a.y() - b.y()) > STILL_PX) return true;
        }
        return false;
    }

    static boolean still(Map<String, Rect> before, Map<String, Rect> after) {
        return still(before, after, STILL_PX);
    }

    /** Whether the same members show, none moved more than {@code px}. */
    static boolean still(Map<String, Rect> before, Map<String, Rect> after, int px) {
        if (!before.keySet().equals(after.keySet())) return false;
        for (Map.Entry<String, Rect> was : before.entrySet()) {
            Point a = was.getValue().center();
            Point b = after.get(was.getKey()).center();
            if (Math.abs(a.x() - b.x()) > px || Math.abs(a.y() - b.y()) > px) return false;
        }
        return true;
    }

    /**
     * Where {@code strip}'s members are looked for: the box around their places where they were cut, grown by the
     * largest of them, so a member shifted a slot or two is still in it. Null for no strip, or members with no place:
     * anywhere.
     */
    static Rect band(Strip strip) {
        if (strip == null) return null;
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        int grow = 0;
        for (ImageTemplate member : strip.members()) {
            Rect at = member.place();
            if (at == null) continue;
            left = Math.min(left, at.x());
            top = Math.min(top, at.y());
            right = Math.max(right, at.x() + at.width());
            bottom = Math.max(bottom, at.y() + at.height());
            grow = Math.max(grow, Math.max(at.width(), at.height()));
        }
        if (grow == 0) return null;
        return new Rect(left, top, right - left, bottom - top).expand(grow);
    }
}
