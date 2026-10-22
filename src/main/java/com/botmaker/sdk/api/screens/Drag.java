package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.time.Duration;

/**
 * Where a {@link Button} is dragged once pressed, and how long the move takes: onto a picture found on the screen
 * (a card onto its slot), or into one of the bot's {@code Zones}, placed on the game as it shows (a slider's end, a
 * swipe on a phone). The drop is looked for on the frame the button is found on; a drop that is not there is no press
 * (2026-10-22).
 *
 * @param onto the picture it is let go on; null when it goes into {@code into}
 * @param into the area it is let go in, at its centre; null when it goes onto {@code onto}
 * @param time how long the move from the button to the drop takes; {@link Button#DRAG_TIME} when null
 */
public record Drag(ImageTemplate onto, Zone into, Duration time) {

    public Drag {
        // One drop: a picture, else a zone.
        if (onto != null) into = null;
        // The usual time is no time of its own, so it is never written.
        if (Button.DRAG_TIME.equals(time)) time = null;
    }

    /** What it is let go on, as a window or a tool names it: the picture's name, or the zone's place. */
    public String target() {
        return onto != null ? onto.id() : into != null ? "zone " + into : "?";
    }
}
