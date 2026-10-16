package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What acts on the game, for whoever acts: a run ({@code RunReach}) or the assistant's tools ({@code ToolReach}).
 * Points are in the frame's pixels, its top-left (0, 0). {@link #perform} is the one place an {@link Input} is done,
 * so a picture the assistant tries is found and clicked as a run would find and click it.
 */
public interface Reach {

    /** How long a list is let settle after a scroll before it is looked at again. */
    long SCROLL_SETTLE_MS = 400;

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
     * from, when it clears this reach's confidence: an input {@link Input#within a list}. By default it is found
     * anywhere and kept only when its centre is in {@code area}; a reach that matches through the SDK's matcher looks
     * only there, scaled as the picture is.
     */
    default Optional<Rect> findWithin(ImageTemplate picture, Rect area, BufferedImage frame) {
        return find(picture, frame).filter(at -> area.contains(at.center()));
    }

    /**
     * How {@code input}'s button looks on {@code frame}: its variants looked for before its usual picture, since a
     * badge or a grey copy of a button also passes for the button itself; for one {@link Input#within a list},
     * anywhere in it. Empty for a key, or a button not there.
     */
    default Optional<Seen> seen(Input input, BufferedImage frame) {
        List<Variant> looks = input.looks();
        if (looks.isEmpty()) return Optional.empty();
        List<Variant> order = new ArrayList<>(looks.subList(1, looks.size()));
        order.add(looks.getFirst());
        List<Optional<Rect>> found = input.area() == null
                ? findEach(order.stream().map(Variant::picture).toList(), frame)
                : order.stream().map(look -> findWithin(look.picture(), input.area(), frame)).toList();
        for (int i = 0; i < order.size(); i++) {
            if (found.get(i).isPresent()) return Optional.of(new Seen(order.get(i).state(), found.get(i).get()));
        }
        return Optional.empty();
    }

    /**
     * Does {@code input} on {@code frame}: its key pressed, or its button found, in any look but a disabled one, and
     * clicked or scrolled over. A button not there is scrolled to first by the input's {@link Input#scrollTo}, up to
     * {@link Input#SCROLLS} times, the game looked at again after each. False when the button is not there or greyed
     * out, and nothing but those scrolls was done.
     */
    default boolean perform(Input input, BufferedImage frame) {
        if (input.act() == Input.Act.KEY) {
            key(input.key());
            return true;
        }
        Optional<Seen> button = seen(input, frame);
        BufferedImage now = frame;
        for (int i = 0; button.isEmpty() && input.scrollTo() != null && i < Input.SCROLLS; i++) {
            if (!perform(input.scrollTo(), now)) break;
            pause(SCROLL_SETTLE_MS);
            Optional<BufferedImage> next = look();
            if (next.isEmpty() || Thread.currentThread().isInterrupted()) break;
            now = next.get();
            button = seen(input, now);
        }
        if (button.isEmpty() || button.get().state() == State.DISABLED) return false;
        Rect place = button.get().place();
        if (input.act() == Input.Act.SCROLL) scroll(place.center(), input.notches());
        else click(pointOn(place));
        return true;
    }
}
