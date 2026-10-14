package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * What acts on the game, for whoever acts: a run ({@code RunReach}) or the assistant's tools ({@code ToolReach}).
 * Points are in the frame's pixels, its top-left (0, 0). {@link #perform} is the one place an {@link Input} is done,
 * so a picture the assistant tries is found and clicked as a run would find and click it.
 */
public interface Reach {

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

    /**
     * Does {@code input} on {@code frame}: its key pressed, or its picture found and clicked or scrolled over. False
     * when the picture is not there, and nothing was done.
     */
    default boolean perform(Input input, BufferedImage frame) {
        if (input.act() == Input.Act.KEY) {
            key(input.key());
            return true;
        }
        Optional<Rect> place = input.picture() == null ? Optional.empty() : find(input.picture(), frame);
        if (place.isEmpty()) return false;
        if (input.act() == Input.Act.SCROLL) scroll(place.get().center(), input.notches());
        else click(pointOn(place.get()));
        return true;
    }
}
