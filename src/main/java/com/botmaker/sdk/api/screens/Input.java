package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * One way to act on a {@link GameScreen}: a picture clicked, a key pressed, or a list scrolled over its picture —
 * {@code Input.click(Pictures.CAMPAIGN_CLAIM).did(Did.IN_PLACE).kind(Kind.CHORE)}.
 *
 * <p>With no {@link #did}, no {@link #never} and no {@link #locked} it is untried: Discovery tries every input of a
 * screen before the screen counts as finished. An input that opened a screen is tried too, and says so by the
 * screen it opened ({@link GameScreen#from}). An input that {@link #sometimes comes and goes} never holds a screen
 * back while it is not on it; {@link #when} says when it is there, as a method of the bot or of a plugin
 * ({@code Firestone::eventOn}), and a run asks it before walking there (the user's call, 2026-10-15).
 *
 * <p>A button that changes its look stays one input: each other look is one of its {@link #variants}, with what it
 * means (the user's call, 2026-10-15). Any of them finds it; a {@link State#DISABLED disabled} one is not pressed.
 *
 * <p>A button in a list that scrolls, or in a column whose slots shift, is a member of one of its screen's
 * {@link GameScreen#lists lists}: the list says where it is looked for and how it is scrolled to.
 *
 * @param act          what is done
 * @param picture      the picture clicked or scrolled over, as the button usually looks; null for a key
 * @param key          the key pressed; null for a picture
 * @param notches      how far a scroll goes: positive up or away, negative down or toward you; 0 otherwise
 * @param did          what it did when tried, opening nothing; null when untried or when it opened a screen
 * @param kind         what it is for; null when not named yet
 * @param never        why it is never done (it spends premium currency or real money); null when it may be
 * @param comesAndGoes whether it shows only at times: a Claim when loot is ready, an event's icon
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param variants     the button's other looks, each with what it means
 * @param when         when it is there, for one that comes and goes: a method answering whether it is; null when
 *                     not known
 */
public record Input(Act act, ImageTemplate picture, Key key, int notches, Did did, Kind kind, String never,
                    boolean comesAndGoes, ImageTemplate lock, List<Variant> variants, BooleanSupplier when) {

    /** What an input does. */
    public enum Act { CLICK, KEY, SCROLL }

    public Input {
        variants = variants == null ? List.of() : List.copyOf(variants);
        // When it is there is said only of what comes and goes.
        comesAndGoes = comesAndGoes || when != null;
    }

    /** {@code picture} clicked at its best match. */
    public static Input click(ImageTemplate picture) {
        return new Input(Act.CLICK, picture, null, 0, null, null, null, false, null, null, null);
    }

    /** {@code key} pressed. */
    public static Input key(Key key) {
        return new Input(Act.KEY, null, key, 0, null, null, null, false, null, null, null);
    }

    /** {@code notches} scrolled over {@code picture}'s best match: a list or a panel that scrolls. */
    public static Input scroll(ImageTemplate picture, int notches) {
        return new Input(Act.SCROLL, picture, null, notches, null, null, null, false, null, null, null);
    }

    /** This input, tried, having done {@code did}. */
    public Input did(Did did) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock, variants, when);
    }

    /** This input, named a {@code kind}. */
    public Input kind(Kind kind) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock, variants, when);
    }

    /** This input, never done because {@code why}: it spends premium currency or real money. */
    public Input never(String why) {
        return new Input(act, picture, key, notches, did, kind, why, comesAndGoes, lock, variants, when);
    }

    /** This input, showing only at times. */
    public Input sometimes() {
        return new Input(act, picture, key, notches, did, kind, never, true, lock, variants, when);
    }

    /** This input, proved locked on this account by {@code lock} at its place. */
    public Input locked(ImageTemplate lock) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock, variants, when);
    }

    /** This input, its button also looking as each of {@code variants} says. */
    public Input variants(List<Variant> variants) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock, variants, when);
    }

    /** This input, coming and going, there while {@code condition} answers true: {@code Firestone::eventOn}. */
    public Input when(BooleanSupplier condition) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock, variants, condition);
    }

    /** This input with {@code variant} put in place of the one of the same picture, or added last. */
    public Input withVariant(Variant variant) {
        List<Variant> now = new ArrayList<>(variants);
        int at = now.stream().map(v -> v.picture().id()).toList().indexOf(variant.picture().id());
        if (at >= 0) now.set(at, variant);
        else now.add(variant);
        return variants(now);
    }

    /** Every look of its button, the usual one first as {@link State#NORMAL}; none for a key. */
    public List<Variant> looks() {
        if (picture == null) return List.of();
        List<Variant> all = new ArrayList<>();
        all.add(Variant.of(picture, State.NORMAL));
        all.addAll(variants);
        return all;
    }

    /** What the look {@code pictureId} means: one of its looks' state; empty for a picture that is none of them. */
    public Optional<State> stateOf(String pictureId) {
        return looks().stream().filter(v -> v.picture().id().equals(pictureId)).map(Variant::state).findFirst();
    }

    /** Whether it was tried, or needs no trying: done, never done, or locked. An input that opened a screen is tried too. */
    public boolean settled() {
        return did != null || never != null || lock != null;
    }

    /** Whether {@code other} acts the same way: the same act on the same picture or key, the same notches. */
    public boolean sameAs(Input other) {
        return other != null && act == other.act && key == other.key && notches == other.notches
                && (picture == null ? other.picture == null : other.picture != null
                && picture.id().equals(other.picture.id()));
    }

    /** How it reads in a sentence: {@code click campaign_claim}, {@code key Escape}, {@code scroll list -3}. */
    public String label() {
        return switch (act) {
            case CLICK -> "click " + (picture == null ? "?" : picture.id());
            case KEY -> "key " + (key == null ? "?" : key.label());
            case SCROLL -> "scroll " + (picture == null ? "?" : picture.id()) + " " + notches;
        };
    }
}
