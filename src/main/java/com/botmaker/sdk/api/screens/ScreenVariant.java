package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One look of a {@link GameScreen} that the same ways open and the same way leaves, doing the same job: the event
 * screen dressed for Halloween — {@code ScreenVariant.named("Halloween").markers(List.of(Pictures.EVENT_HALLOWEEN))}.
 * Its markers tell it apart once the screen's own matched, as a screen's do: all of {@link #markers} show and none of
 * {@link #absent}. Its inputs are those only it has, beside the screen's (the user's call, 2026-10-15). A screen that
 * is reached another way, left another way or does another job is a screen of its own; a list whose entries change by
 * day is one screen whose entries come and go ({@link Input#when}).
 *
 * @param name    what it is called, in plain words
 * @param markers pictures that all show while the screen has this look; empty until one is cut
 * @param absent  pictures none of which shows while the screen has this look
 * @param inputs  the inputs only this look has
 * @param when    the method answering whether this look shows now; null when not known
 */
public record ScreenVariant(String name, List<ImageTemplate> markers, List<ImageTemplate> absent, List<Input> inputs,
                            BooleanSupplier when) {

    public ScreenVariant {
        markers = markers == null ? List.of() : List.copyOf(markers);
        absent = absent == null ? List.of() : List.copyOf(absent);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    /** A look of a screen called {@code name}, nothing known of it yet. */
    public static ScreenVariant named(String name) {
        return new ScreenVariant(name, List.of(), List.of(), List.of(), null);
    }

    /** This look, told apart by {@code markers}: all of them show while the screen has it. */
    public ScreenVariant markers(List<ImageTemplate> markers) {
        return new ScreenVariant(name, markers, absent, inputs, when);
    }

    /** This look, told apart by {@code absent}: none of them shows while the screen has it. */
    public ScreenVariant absent(List<ImageTemplate> absent) {
        return new ScreenVariant(name, markers, absent, inputs, when);
    }

    /** Whether it has a marker to tell it by. */
    public boolean hasMarker() {
        return !markers.isEmpty();
    }

    /** Its first marker, the picture that stands for it; null when it has none. */
    public ImageTemplate firstMarker() {
        return markers.isEmpty() ? null : markers.getFirst();
    }

    /** This look, holding {@code inputs} beside the screen's. */
    public ScreenVariant inputs(List<Input> inputs) {
        return new ScreenVariant(name, markers, absent, inputs, when);
    }

    /** This look, showing while {@code condition} answers true: {@code Firestone::halloween}. */
    public ScreenVariant when(BooleanSupplier condition) {
        return new ScreenVariant(name, markers, absent, inputs, condition);
    }
}
