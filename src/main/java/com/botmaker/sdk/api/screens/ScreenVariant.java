package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One look of a {@link GameScreen} that the same ways open and the same way leaves, doing the same job: the event
 * screen dressed for Halloween — {@code ScreenVariant.named("Halloween").marker(Pictures.EVENT_HALLOWEEN)}. Its marker
 * tells it apart once the screen's own marker matched; its inputs are those only it has, beside the screen's (the
 * user's call, 2026-10-15). A screen that is reached another way, left another way or does another job is a screen of
 * its own; a list whose entries change by day is one screen whose entries come and go ({@link Input#when}).
 *
 * @param name   what it is called, in plain words
 * @param marker a picture only this look shows; null until one is cut
 * @param inputs the inputs only this look has
 * @param when   the method answering whether this look shows now; null when not known
 */
public record ScreenVariant(String name, ImageTemplate marker, List<Input> inputs, BooleanSupplier when) {

    public ScreenVariant {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    /** A look of a screen called {@code name}, nothing known of it yet. */
    public static ScreenVariant named(String name) {
        return new ScreenVariant(name, null, List.of(), null);
    }

    /** This look, told apart by {@code marker}. */
    public ScreenVariant marker(ImageTemplate marker) {
        return new ScreenVariant(name, marker, inputs, when);
    }

    /** This look, holding {@code inputs} beside the screen's. */
    public ScreenVariant inputs(List<Input> inputs) {
        return new ScreenVariant(name, marker, inputs, when);
    }

    /** This look, showing while {@code condition} answers true: {@code Firestone::halloween}. */
    public ScreenVariant when(BooleanSupplier condition) {
        return new ScreenVariant(name, marker, inputs, condition);
    }
}
