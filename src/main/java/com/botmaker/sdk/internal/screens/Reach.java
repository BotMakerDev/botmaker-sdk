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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What acts on the game, for whoever acts: a run ({@code RunReach}) or the assistant's tools ({@code ToolReach}).
 * Points are in the frame's pixels, its top-left (0, 0). {@link #perform} is the one place an {@link Input} is done,
 * so a picture the assistant tries is found and clicked as a run would find and click it.
 */
public interface Reach {

    /** How long a list is let settle after a scroll before it is looked at again. */
    long SCROLL_SETTLE_MS = 400;

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
        List<Variant> looks = input.looks();
        if (looks.isEmpty()) return Optional.empty();
        List<Variant> order = new ArrayList<>(looks.subList(1, looks.size()));
        order.add(looks.getFirst());
        Rect band = band(list);
        List<Optional<Rect>> found = band == null
                ? findEach(order.stream().map(Variant::picture).toList(), frame)
                : order.stream().map(look -> findWithin(look.picture(), band, frame)).toList();
        for (int i = 0; i < order.size(); i++) {
            if (found.get(i).isPresent()) return Optional.of(new Seen(order.get(i).state(), found.get(i).get()));
        }
        return Optional.empty();
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
     */
    default boolean perform(Input input, ScreenList list, BufferedImage frame) {
        if (input.act() == Input.Act.KEY) {
            key(input.key());
            return true;
        }
        Optional<Seen> button = inView(input, list, frame);
        if (button.isEmpty() || button.get().state() == State.DISABLED) return false;
        Rect place = button.get().place();
        if (input.act() == Input.Act.SCROLL) scroll(place.center(), input.notches());
        else click(pointOn(place));
        return true;
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
        // Scrolled past every member, the wheel stays where the last one showed: a point in the list.
        Point over = null;
        int done = 0;
        for (int notches : new int[] {list.notches(), -list.notches()}) {
            int most = ScreenList.SCROLLS + done;
            done = 0;
            for (int i = 0; i < most; i++) {
                if (!members.isEmpty()) over = members.values().iterator().next().center();
                if (over == null) return Optional.empty();
                scroll(over, notches);
                done++;
                pause(SCROLL_SETTLE_MS);
                Optional<BufferedImage> next = look();
                if (next.isEmpty() || Thread.currentThread().isInterrupted()) return Optional.empty();
                Optional<Seen> button = seen(input, list, next.get());
                if (button.isPresent()) return button;
                Map<String, Rect> now = members(list, next.get());
                boolean end = !now.isEmpty() && still(members, now);
                members = now;
                if (end) break;
            }
        }
        return Optional.empty();
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
