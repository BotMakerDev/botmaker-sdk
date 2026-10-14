package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.vision.ImageTemplate;

/**
 * One way to act on a {@link GameScreen}: a picture clicked, a key pressed, or a list scrolled over its picture —
 * {@code Input.click(Pictures.CAMPAIGN_CLAIM).did(Did.IN_PLACE).kind(Kind.CHORE)}.
 *
 * <p>With no {@link #did}, no {@link #never} and no {@link #locked} it is untried: Discovery tries every input of a
 * screen before the screen counts as finished. An input that opened a screen is tried too, and says so by the
 * screen it opened ({@link GameScreen#from}). An input that {@link #sometimes comes and goes} never holds a screen
 * back while it is not on it.
 *
 * @param act          what is done
 * @param picture      the picture clicked or scrolled over; null for a key
 * @param key          the key pressed; null for a picture
 * @param notches      how far a scroll goes: positive up or away, negative down or toward you; 0 otherwise
 * @param did          what it did when tried, opening nothing; null when untried or when it opened a screen
 * @param kind         what it is for; null when not named yet
 * @param never        why it is never done (it spends premium currency or real money); null when it may be
 * @param comesAndGoes whether it shows only at times: a Claim when loot is ready, an event's icon
 * @param lock         the picture proving it locked on this account; null when it is not
 */
public record Input(Act act, ImageTemplate picture, Key key, int notches, Did did, Kind kind, String never,
                    boolean comesAndGoes, ImageTemplate lock) {

    /** What an input does. */
    public enum Act { CLICK, KEY, SCROLL }

    /** {@code picture} clicked at its best match. */
    public static Input click(ImageTemplate picture) {
        return new Input(Act.CLICK, picture, null, 0, null, null, null, false, null);
    }

    /** {@code key} pressed. */
    public static Input key(Key key) {
        return new Input(Act.KEY, null, key, 0, null, null, null, false, null);
    }

    /** {@code notches} scrolled over {@code picture}'s best match: a list or a panel that scrolls. */
    public static Input scroll(ImageTemplate picture, int notches) {
        return new Input(Act.SCROLL, picture, null, notches, null, null, null, false, null);
    }

    /** This input, tried, having done {@code did}. */
    public Input did(Did did) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock);
    }

    /** This input, named a {@code kind}. */
    public Input kind(Kind kind) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock);
    }

    /** This input, never done because {@code why}: it spends premium currency or real money. */
    public Input never(String why) {
        return new Input(act, picture, key, notches, did, kind, why, comesAndGoes, lock);
    }

    /** This input, showing only at times. */
    public Input sometimes() {
        return new Input(act, picture, key, notches, did, kind, never, true, lock);
    }

    /** This input, proved locked on this account by {@code lock} at its place. */
    public Input locked(ImageTemplate lock) {
        return new Input(act, picture, key, notches, did, kind, never, comesAndGoes, lock);
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
