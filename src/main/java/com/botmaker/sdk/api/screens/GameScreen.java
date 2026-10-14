package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.List;

/**
 * One screen of the game, a constant of the bot's {@code Screens} class: its {@link #marker}, the {@link #from way
 * in} that made it known, any {@link #alsoFrom other way}, its {@link #back way out}, and its {@link #inputs}.
 *
 * <pre>{@code
 * public static final GameScreen CAMPAIGN = GameScreen.named("Campaign")
 *         .marker(Pictures.CAMPAIGN_TITLE)
 *         .from(Way.of(HOME, Input.click(Pictures.CAMPAIGN_HOME)))
 *         .back(Input.click(Pictures.CLOSE_X))
 *         .inputs(List.of(Input.click(Pictures.CAMPAIGN_CLAIM).did(Did.IN_PLACE).kind(Kind.CHORE)));
 * }</pre>
 *
 * <p>The screens are a graph, and its tree is the {@code from} of each: a screen names the one it is reached from,
 * never the ones it leads to, so no constant names one declared after it. A {@link #popup} opens on its own (a
 * reward, a level up) and is in no tree: it has a marker and a way out only.
 *
 * <p>Discovery fills it in (the assistant's try_input, or ▶ Try in 🗺 Screens); a run walks it to reach a screen.
 *
 * @param name    the name the game shows for it, in plain words
 * @param marker  a picture only this screen shows; null until one is cut
 * @param from    the way in that made it known; null for the first screen and for a popup
 * @param alsoFrom other ways in
 * @param back    the way out to where it was opened from; null for the default: a close button, then Escape
 * @param inputs  every input on it, tried or not
 * @param isPopup whether it opens on its own rather than from an input
 */
public record GameScreen(String name, ImageTemplate marker, Way from, List<Way> alsoFrom, Input back,
                         List<Input> inputs, boolean isPopup) {

    public GameScreen {
        alsoFrom = alsoFrom == null ? List.of() : List.copyOf(alsoFrom);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    /** A screen called {@code name}, nothing known of it yet. */
    public static GameScreen named(String name) {
        return new GameScreen(name, null, null, List.of(), null, List.of(), false);
    }

    /** This screen, told apart by {@code marker}. */
    public GameScreen marker(ImageTemplate marker) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, isPopup);
    }

    /** This screen, made known by {@code way}. */
    public GameScreen from(Way way) {
        return new GameScreen(name, marker, way, alsoFrom, back, inputs, isPopup);
    }

    /** This screen, reached by {@code ways} too. */
    public GameScreen alsoFrom(List<Way> ways) {
        return new GameScreen(name, marker, from, ways, back, inputs, isPopup);
    }

    /** This screen, left by {@code back}. */
    public GameScreen back(Input back) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, isPopup);
    }

    /** This screen, holding {@code inputs}. */
    public GameScreen inputs(List<Input> inputs) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, isPopup);
    }

    /** This screen, opening on its own: a reward, a level up, an offer. */
    public GameScreen popup() {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, true);
    }
}
