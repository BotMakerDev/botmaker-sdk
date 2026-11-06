package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.function.BooleanSupplier;

/**
 * The bot's own Java as an input of a {@link Screen}, for what is no press: a puzzle solved, a swipe computed from what
 * the screen shows. {@code Custom.of(Puzzles::solve)}, a field of its screen as a button is; a run does it by running
 * the method on the run's own thread, whatever the method throws ending the run as any of the bot's code does.
 *
 * <p>Discovery never tries one, since it cannot know what the method spends: it is {@link #isTried tried} from the moment
 * it is written, and says nothing of its screen's shape. What it opens is said by the screen it opens ({@link Setup#from}).
 *
 * @param method       what its press runs
 * @param isLeaving    whether it goes to a screen mapped already that it is no way into
 * @param isInfo       whether it only tells
 * @param never        why it is never done; null when it may be
 * @param isOccasional whether it works only at times
 * @param locked       the picture proving it locked on this account; null when it is not
 * @param when         when it works, for one that comes and goes; null when not known
 */
public record Custom(Runnable method, boolean isLeaving, boolean isInfo, String never, boolean isOccasional,
                     ImageTemplate locked, BooleanSupplier when) implements Input {

    public Custom {
        isOccasional = isOccasional || when != null;
    }

    /** {@code method} run as its press: {@code Puzzles::solve}. */
    public static Custom of(Runnable method) {
        return new Custom(method, false, false, null, false, null, null);
    }

    /** Always: no one but the bot's author knows what it does, so it is never Discovery's to try. */
    @Override
    public boolean isTried() {
        return true;
    }

    @Override
    public Custom tried() {
        return this;
    }

    @Override
    public Custom leaving() {
        return new Custom(method, true, isInfo, never, isOccasional, locked, when);
    }

    @Override
    public Custom info() {
        return new Custom(method, isLeaving, true, never, isOccasional, locked, when);
    }

    @Override
    public Custom never(String why) {
        return new Custom(method, isLeaving, isInfo, why, isOccasional, locked, when);
    }

    @Override
    public Custom occasional() {
        return new Custom(method, isLeaving, isInfo, never, true, locked, when);
    }

    @Override
    public Custom locked(ImageTemplate proof) {
        return new Custom(method, isLeaving, isInfo, never, isOccasional, proof, when);
    }

    @Override
    public Custom when(BooleanSupplier condition) {
        return new Custom(method, isLeaving, isInfo, never, isOccasional, locked, condition);
    }

    @Override
    public String label() {
        return "custom code";
    }
}
