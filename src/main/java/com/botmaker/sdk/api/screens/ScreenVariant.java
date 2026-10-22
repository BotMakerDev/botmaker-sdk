package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One look of a {@link Screen} that the same ways open and the same way leaves, doing the same job: the event screen
 * dressed for Halloween — {@code ScreenVariant.named("Halloween").markers(List.of(Pictures.EVENT_HALLOWEEN))}. Its
 * markers tell it apart once the screen's own matched, as a screen's do: all of {@link #markers} show and none of
 * {@link #absent}. Its inputs are those of the screen's fields only it shows, named as fields ({@code EVENT.pumpkin};
 * the user's call, 2026-10-15). A screen that is reached another way, left another way or does another job is a
 * screen of its own; a list whose entries change by day is one screen whose entries come and go ({@link Input#when}).
 *
 * @param name    what it is called, in plain words
 * @param markers pictures that all show while the screen has this look; empty until one is cut
 * @param absent  pictures none of which shows while the screen has this look
 * @param inputs  the screen's inputs only this look shows
 * @param when    the method answering whether this look shows now; null when not known
 * @param shot    its picture of the whole screen in this look, a path in the bot's resources
 *                ({@code "screens/EVENT@halloween.png"}); null while it has none, and the screen's own shows
 */
public record ScreenVariant(String name, List<ImageTemplate> markers, List<ImageTemplate> absent, List<Input> inputs,
                            BooleanSupplier when, String shot) {

    public ScreenVariant {
        markers = markers == null ? List.of() : List.copyOf(markers);
        absent = absent == null ? List.of() : List.copyOf(absent);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        shot = shot == null || shot.isBlank() ? null : shot;
    }

    /** A look of a screen called {@code name}, nothing known of it yet. */
    public static ScreenVariant named(String name) {
        return new ScreenVariant(name, List.of(), List.of(), List.of(), null, null);
    }

    /** This look, told apart by {@code markers}: all of them show while the screen has it. */
    public ScreenVariant markers(List<ImageTemplate> markers) {
        return new ScreenVariant(name, markers, absent, inputs, when, shot);
    }

    /** This look, told apart by {@code absent}: none of them shows while the screen has it. */
    public ScreenVariant absent(List<ImageTemplate> absent) {
        return new ScreenVariant(name, markers, absent, inputs, when, shot);
    }

    /** Whether it has a marker to tell it by. */
    public boolean hasMarker() {
        return !markers.isEmpty();
    }

    /** Its first marker, the picture that stands for it; null when it has none. */
    public ImageTemplate firstMarker() {
        return markers.isEmpty() ? null : markers.getFirst();
    }

    /** This look, showing the screen's {@code inputs} that only it shows. */
    public ScreenVariant inputs(List<Input> inputs) {
        return new ScreenVariant(name, markers, absent, inputs, when, shot);
    }

    /** This look, showing while {@code condition} answers true: {@code Firestone::halloween}. */
    public ScreenVariant when(BooleanSupplier condition) {
        return new ScreenVariant(name, markers, absent, inputs, condition, shot);
    }

    /** This look, its picture of the whole screen at {@code shot} in the bot's resources; null for none. */
    public ScreenVariant shot(String shot) {
        return new ScreenVariant(name, markers, absent, inputs, when, shot);
    }
}
