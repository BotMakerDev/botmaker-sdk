package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenList;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * What acts on the game, for whoever acts: a run ({@code RunReach}) or the assistant's tools ({@code ToolReach}).
 * Points are in the frame's pixels, its top-left (0, 0). {@link #perform} is the one place an {@link Input} is done,
 * so a picture the assistant tries is found and clicked as a run would find and click it.
 */
public interface Reach {

    /** How long a list is let settle after a scroll before it is looked at again, and between two looks after. */
    long SCROLL_SETTLE_MS = 400;

    /**
     * The longest a list is looked at after a scroll for its members to hold still: Firestone's column glides for
     * over a second, and judged before it stopped it looked at its end (2026-10-16).
     */
    long SCROLL_SETTLE_MAX_MS = 3000;

    /** Scrolls in a row the first way with no member showing before the list is scrolled the other way. */
    int BLIND_SCROLLS = 3;

    /** How long the game is let settle between two copies of a button an {@link Input#every each} input presses. */
    long CLICK_SETTLE_MS = 600;

    /** How far a list member may shift between two looks and still count as not moved: a match is found to a pixel or two. */
    int STILL_PX = 4;

    /** The game's screen now; empty when it could not be captured. */
    Optional<BufferedImage> look();

    /** Where {@code picture} is on {@code frame} when it clears this reach's confidence; empty otherwise. */
    Optional<Rect> find(ImageTemplate picture, BufferedImage frame);

    /** {@link #find} for each of {@code pictures} on one frame, in their order. */
    default List<Optional<Rect>> findEach(List<ImageTemplate> pictures, BufferedImage frame) {
        return pictures.stream().map(picture -> find(picture, frame)).toList();
    }

    /** How far from its cut place a marker is looked for, in the frame's pixels: a title moves a few pixels at most. */
    int MARKER_MARGIN = 60;

    /**
     * {@link #findEach}, each picture looked for only within {@link #MARKER_MARGIN} of its place (anywhere when it has
     * none): what tells a screen sits where it was cut. A look for every marker over the whole frame took Firestone's
     * walk 11 s at each poll (2026-10-16).
     */
    default List<Optional<Rect>> findEachNear(List<ImageTemplate> pictures, BufferedImage frame) {
        return findEach(pictures, frame);
    }

    void click(Point at);

    void key(Key key);

    void scroll(Point at, int notches);

    /** Milliseconds on a clock that only goes forward. */
    long nowMs();

    /** Waits {@code ms}; a stop or an interrupt ends it early, and the caller reads the thread's interrupt. */
    void pause(long ms);

    /** Where on {@code place} a click lands: its centre unless this reach spreads its clicks. */
    default Point pointOn(Rect place) {
        return place.center();
    }

    /** An input's button as {@code frame} shows it: which of its looks, and where. */
    record Seen(State state, Rect place) {}

    /**
     * Where {@code picture} is anywhere in {@code area} on {@code frame}, {@code area} in the frame the picture was cut
     * from, when it clears this reach's confidence: a member of a {@link ScreenList list}. By default it is found
     * anywhere and kept only when its centre is in {@code area}; a reach that matches through the SDK's matcher looks
     * only there, scaled as the picture is.
     */
    default Optional<Rect> findWithin(ImageTemplate picture, Rect area, BufferedImage frame) {
        return find(picture, frame).filter(at -> area.contains(at.center()));
    }

    /** {@link #seen(Input, ScreenList, BufferedImage)} of an input at its place, in no list. */
    default Optional<Seen> seen(Input input, BufferedImage frame) {
        return seen(input, null, frame);
    }

    /**
     * How {@code input}'s button looks on {@code frame}: its variants looked for before its usual picture, since a
     * badge or a grey copy of a button also passes for the button itself; a member of {@code list}, anywhere along
     * it. Empty for a key, or a button not there.
     */
    default Optional<Seen> seen(Input input, ScreenList list, BufferedImage frame) {
        if (input.nth() > 0 || input.every()) {
            // One copy of a repeated button: the nth in reading order; every copy starts at the first.
            List<Seen> copies = copies(input, list, frame);
            int at = input.every() ? 0 : input.nth() - 1;
            return at < copies.size() ? Optional.of(copies.get(at)) : Optional.empty();
        }
        List<Variant> order = lookOrder(input);
        if (order.isEmpty()) return Optional.empty();
        Rect band = band(list);
        List<Optional<Rect>> found = band == null
                ? findEach(order.stream().map(Variant::picture).toList(), frame)
                : order.stream().map(look -> findWithin(look.picture(), band, frame)).toList();
        for (int i = 0; i < order.size(); i++) {
            if (found.get(i).isPresent()) return Optional.of(new Seen(order.get(i).state(), found.get(i).get()));
        }
        return Optional.empty();
    }

    /**
     * Every place {@code picture} clears this reach's confidence on {@code frame}, in no order: the copies of a
     * repeated button. By default only its best match; a reach that matches through the SDK's matcher finds them all.
     */
    default List<Rect> findAll(ImageTemplate picture, BufferedImage frame) {
        return find(picture, frame).map(List::of).orElse(List.of());
    }

    /**
     * Every copy of {@code input}'s button on {@code frame}, in reading order ({@link #readingOrder}), each in the look
     * it shows: a copy is found by any of its looks, its variants before its usual picture, and a place another look
     * already holds is that copy. Only those along {@code list}, for a member of one.
     */
    default List<Seen> copies(Input input, ScreenList list, BufferedImage frame) {
        Rect band = band(list);
        List<Seen> all = new ArrayList<>();
        for (Variant look : lookOrder(input)) {
            for (Rect at : findAll(look.picture(), frame)) {
                if (band != null && !band.contains(at.center())) continue;
                if (all.stream().noneMatch(s -> s.place().contains(at.center()))) all.add(new Seen(look.state(), at));
            }
        }
        return readingOrder(all, Seen::place);
    }

    /** {@code input}'s looks in the order they are looked for: a badge or a grey copy also passes for the button. */
    private static List<Variant> lookOrder(Input input) {
        List<Variant> looks = input.looks();
        if (looks.isEmpty()) return List.of();
        List<Variant> order = new ArrayList<>(looks.subList(1, looks.size()));
        order.add(looks.getFirst());
        return order;
    }

    /**
     * {@code items} in reading order by their {@code place}: top to bottom, then left to right. Two whose centres
     * are less than half the row's first item's height apart are on one row.
     */
    static <T> List<T> readingOrder(List<T> items, Function<T, Rect> place) {
        List<T> byTop = new ArrayList<>(items);
        byTop.sort(Comparator.comparingInt(item -> place.apply(item).center().y()));
        List<T> ordered = new ArrayList<>();
        List<T> row = new ArrayList<>();
        for (T item : byTop) {
            if (!row.isEmpty()) {
                Rect first = place.apply(row.getFirst());
                if (Math.abs(place.apply(item).center().y() - first.center().y()) * 2 >= first.height()) {
                    row.sort(Comparator.comparingInt(r -> place.apply(r).center().x()));
                    ordered.addAll(row);
                    row.clear();
                }
            }
            row.add(item);
        }
        row.sort(Comparator.comparingInt(r -> place.apply(r).center().x()));
        ordered.addAll(row);
        return ordered;
    }

    /** {@link #perform(Input, ScreenList, BufferedImage)} of an input at its place, in no list. */
    default boolean perform(Input input, BufferedImage frame) {
        return perform(input, null, frame);
    }

    /**
     * Does {@code input} on {@code frame}: its key pressed, or its button found, in any look but a disabled one, and
     * clicked or scrolled over. A member of {@code list} not showing is scrolled to first: the wheel turns over a
     * member that shows, by the list's notches, up to {@link ScreenList#SCROLLS} times each way, the game looked at
     * again after each. A scroll after which no member moved met the list's end, and the scroll turns the other way
     * once: the members' places say it, never the pixels, since a list may sit over a moving fight. False when the
     * button is not there or greyed out, and nothing but those scrolls was done.
     *
     * <p>An {@link Input#nth nth} input acts on that copy of its button; an {@link Input#every each} one on every copy
     * the frame shows but a disabled one, the game let settle {@link #CLICK_SETTLE_MS} between them. They go last to
     * first in reading order: a card claimed and gone shifts the ones after it, never one still to press. False for
     * that one when no copy was acted on.
     */
    default boolean perform(Input input, ScreenList list, BufferedImage frame) {
        if (input.act() == Input.Act.KEY) {
            key(input.key());
            return true;
        }
        if (input.every()) return performEach(input, list, frame);
        Optional<Seen> button = inView(input, list, frame);
        if (button.isEmpty() || button.get().state() == State.DISABLED) return false;
        act(input, button.get().place());
        return true;
    }

    /** {@link #perform} of an input done on every copy of its button. */
    private boolean performEach(Input input, ScreenList list, BufferedImage frame) {
        BufferedImage on = frame;
        if (seen(input, list, frame).isEmpty()) {
            // Scrolled to, the copies are read off the list as it now stands.
            if (inView(input, list, frame).isEmpty()) return false;
            Optional<BufferedImage> now = look();
            if (now.isEmpty()) return false;
            on = now.get();
        }
        boolean any = false;
        for (Seen copy : copies(input, list, on).reversed()) {
            if (copy.state() == State.DISABLED) continue;
            if (any) {
                pause(CLICK_SETTLE_MS);
                if (Thread.currentThread().isInterrupted()) return true;
            }
            act(input, copy.place());
            any = true;
        }
        return any;
    }

    /** {@code input}'s click or scroll on its button at {@code place}. */
    private void act(Input input, Rect place) {
        if (input.act() == Input.Act.SCROLL) scroll(place.center(), input.notches());
        else click(pointOn(place));
    }

    /**
     * {@code input}'s button on {@code frame}, or once {@code list} is scrolled to it, as {@link #perform} scrolls;
     * empty when neither way brought it. Nothing but scrolls is done: the assistant's try_input brings a member into
     * view by it before clicking it as a run would.
     */
    default Optional<Seen> inView(Input input, ScreenList list, BufferedImage frame) {
        Optional<Seen> button = seen(input, list, frame);
        return button.isPresent() || list == null || list.notches() == 0 ? button : scrolledTo(input, list, frame);
    }

    /**
     * {@code input}'s button once {@code list} is scrolled to it; empty when neither way brought it. The way back
     * gets the scrolls the first way took on top of its own, so it searches as far past the start as the first did.
     */
    private Optional<Seen> scrolledTo(Input input, ScreenList list, BufferedImage frame) {
        Map<String, Rect> members = members(list, frame);
        // Scrolled past every member, the wheel stays where the last one showed: a point in the list. With none
        // showing yet (scrolled to an end of icons the list does not name), it turns over the middle of the band.
        Rect band = band(list);
        Point over = band == null ? null : band.center();
        int done = 0;
        boolean first = true;
        boolean shown = !members.isEmpty();
        for (int notches : new int[] {list.notches(), -list.notches()}) {
            int most = ScreenList.SCROLLS + done;
            done = 0;
            int blind = 0;
            for (int i = 0; i < most; i++) {
                if (!members.isEmpty()) over = members.values().iterator().next().center();
                if (over == null) return Optional.empty();
                scroll(over, notches);
                done++;
                Optional<Settled> next = settled(list, members);
                if (next.isEmpty()) return Optional.empty();
                Optional<Seen> button = seen(input, list, next.get().frame());
                if (button.isPresent()) return button;
                Map<String, Rect> now = next.get().members();
                boolean end = !now.isEmpty() && still(members, now);
                // No member shown yet, the first way: an end of icons the list does not name, scrolled further in.
                shown |= !now.isEmpty();
                blind = shown ? 0 : blind + 1;
                members = now;
                if (end || first && blind >= BLIND_SCROLLS) break;
            }
            first = false;
        }
        return Optional.empty();
    }

    /** A frame of a list that held still, and where its members show on it. */
    record Settled(BufferedImage frame, Map<String, Rect> members) {
    }

    /**
     * {@code list} once it holds still after a scroll: looked at every {@link #SCROLL_SETTLE_MS} until two looks in a
     * row show its members at the same places, for up to {@link #SCROLL_SETTLE_MAX_MS}. Members still where they
     * were {@code before} the scroll are looked at to the end of that time: a game may start gliding late, and only
     * then is the list at its end. Empty when the game could not be captured or the wait was stopped.
     */
    private Optional<Settled> settled(ScreenList list, Map<String, Rect> before) {
        long until = nowMs() + SCROLL_SETTLE_MAX_MS;
        Settled last = null;
        while (true) {
            pause(SCROLL_SETTLE_MS);
            Optional<BufferedImage> frame = look();
            if (frame.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
            Settled now = new Settled(frame.get(), members(list, frame.get()));
            boolean held = last != null && still(last.members(), now.members())
                    && (before.isEmpty() || !still(before, now.members()));
            if (held || nowMs() >= until) return Optional.of(now);
            last = now;
        }
    }

    /** Where each member of {@code list} that shows is on {@code frame}, by its id, in the list's order. */
    private Map<String, Rect> members(ScreenList list, BufferedImage frame) {
        Rect band = band(list);
        Map<String, Rect> found = new LinkedHashMap<>();
        for (ImageTemplate member : list.members()) {
            (band == null ? find(member, frame) : findWithin(member, band, frame))
                    .ifPresent(at -> found.put(member.id(), at));
        }
        return found;
    }

    /** Whether the same members show at the same places, to {@link #STILL_PX}: the list did not move. */
    static boolean still(Map<String, Rect> before, Map<String, Rect> after) {
        if (!before.keySet().equals(after.keySet())) return false;
        for (Map.Entry<String, Rect> was : before.entrySet()) {
            Point a = was.getValue().center();
            Point b = after.get(was.getKey()).center();
            if (Math.abs(a.x() - b.x()) > STILL_PX || Math.abs(a.y() - b.y()) > STILL_PX) return false;
        }
        return true;
    }

    /**
     * Where {@code list}'s members are looked for: the box around their places where they were cut, grown by the
     * largest of them, so a member shifted a slot or two is still in it. Null for no list, or members with no place:
     * anywhere.
     */
    static Rect band(ScreenList list) {
        if (list == null) return null;
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        int grow = 0;
        for (ImageTemplate member : list.members()) {
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
