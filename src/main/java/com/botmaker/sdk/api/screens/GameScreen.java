package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen of the game, a constant of the bot's {@code Screens} class: its {@link #marker}, the {@link #from way
 * in} that made it known, any {@link #alsoFrom other way}, its {@link #back way out}, its {@link #inputs}, its
 * {@link #variants looks}, and what automating it taught: its {@link #notes}, how far it got ({@link #reached}) and
 * what it still {@link #owes}.
 *
 * <pre>{@code
 * public static final GameScreen CAMPAIGN = GameScreen.named("Campaign")
 *         .marker(Pictures.CAMPAIGN_TITLE)
 *         .from(Way.of(HOME, Input.click(Pictures.CAMPAIGN_HOME)))
 *         .back(Input.click(Pictures.CLOSE_X))
 *         .inputs(List.of(Input.click(Pictures.CAMPAIGN_CLAIM).did(Did.IN_PLACE).kind(Kind.CHORE)))
 *         .notes("Loot fills over 8 h; Claim takes it. Its badge shows on Home while loot waits.")
 *         .reached(Reached.SURVEYED);
 * }</pre>
 *
 * <p>The screens are a graph, and its tree is the {@code from} of each: a screen names the one it is reached from,
 * never the ones it leads to, so no constant names one declared after it. A {@link #popup} opens on its own (a
 * reward, a level up) and is in no tree: it has a marker and a way out only.
 *
 * <p>Discovery fills it in (the assistant's try_input, or ▶ Try in 🗺 Screens); a run walks it to reach a screen.
 * The notes live here, with the screen, rather than in a file of their own (the user's call, 2026-10-15): a rename
 * or a delete takes them along. So does where its card was moved to on 🗺 Screens' map ({@link #at}), which a run
 * never reads.
 *
 * @param name     the name the game shows for it, in plain words
 * @param marker   a picture only this screen shows; null until one is cut
 * @param from     the way in that made it known; null for the first screen and for a popup
 * @param alsoFrom other ways in
 * @param back     the way out to where it was opened from; null for the default: a close button, then Escape
 * @param inputs   every input on it, tried or not
 * @param variants its looks, each with its own marker and the inputs only it has: the event screen dressed for an
 *                 event
 * @param isPopup  whether it opens on its own rather than from an input
 * @param notes    what automating it taught, in plain lines: what it spends and earns, its timers, badges, quirks;
 *                 null for none
 * @param reached  how far automating it got; null while it is not surveyed
 * @param owes     what is still to cut or find on it, one thing each: {@code marker — every icon here animates}
 * @param at       the top-left corner of its card on 🗺 Screens' map, in the map's own units; null for wherever the
 *                 map's layout puts it
 */
public record GameScreen(String name, ImageTemplate marker, Way from, List<Way> alsoFrom, Input back,
                         List<Input> inputs, List<ScreenVariant> variants, boolean isPopup, String notes,
                         Reached reached, List<String> owes, Point at) {

    public GameScreen {
        alsoFrom = alsoFrom == null ? List.of() : List.copyOf(alsoFrom);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        variants = variants == null ? List.of() : List.copyOf(variants);
        notes = notes == null || notes.isBlank() ? null : notes;
        owes = owes == null ? List.of() : List.copyOf(owes);
    }

    /** A screen called {@code name}, nothing known of it yet. */
    public static GameScreen named(String name) {
        return new GameScreen(name, null, null, List.of(), null, List.of(), List.of(), false, null, null, List.of(),
                null);
    }

    /** This screen, told apart by {@code marker}. */
    public GameScreen marker(ImageTemplate marker) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, made known by {@code way}. */
    public GameScreen from(Way way) {
        return new GameScreen(name, marker, way, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, reached by {@code ways} too. */
    public GameScreen alsoFrom(List<Way> ways) {
        return new GameScreen(name, marker, from, ways, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, left by {@code back}. */
    public GameScreen back(Input back) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, holding {@code inputs}. */
    public GameScreen inputs(List<Input> inputs) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, looking as each of {@code variants} at times. */
    public GameScreen variants(List<ScreenVariant> variants) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen with {@code variant} put in place of the one of the same name, or added last. */
    public GameScreen withVariant(ScreenVariant variant) {
        List<ScreenVariant> now = new ArrayList<>(variants);
        int at = now.stream().map(ScreenVariant::name).toList().indexOf(variant.name());
        if (at >= 0) now.set(at, variant);
        else now.add(variant);
        return variants(now);
    }

    /** Its inputs and every look's, the screen's first: what Discovery tries and a walk may take. */
    public List<Input> allInputs() {
        List<Input> all = new ArrayList<>(inputs);
        for (ScreenVariant v : variants) all.addAll(v.inputs());
        return all;
    }

    /** This screen, opening on its own: a reward, a level up, an offer. */
    public GameScreen popup() {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, true, notes, reached, owes, at);
    }

    /** This screen, a screen of the tree again: opened by an input, not on its own. */
    public GameScreen notPopup() {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, false, notes, reached, owes, at);
    }

    /** This screen, the game showing it as {@code name}. */
    public GameScreen name(String name) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, with {@code notes} on it; blank for none. */
    public GameScreen notes(String notes) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, automated as far as {@code reached}; null while it is not surveyed. */
    public GameScreen reached(Reached reached) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, still owing {@code owes}. */
    public GameScreen owes(List<String> owes) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, its card on 🗺 Screens' map with its top-left corner at {@code at}; null: where the layout puts it. */
    public GameScreen at(Point at) {
        return new GameScreen(name, marker, from, alsoFrom, back, inputs, variants, isPopup, notes, reached, owes, at);
    }
}
