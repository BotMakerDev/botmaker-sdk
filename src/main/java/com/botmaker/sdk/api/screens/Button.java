package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A picture on a {@link Screen} that is clicked — {@code Button.of(Pictures.CAMPAIGN_CLAIM).purpose(Purpose.TASK)}.
 *
 * <p>A button that changes its look stays one button: each other look is one of its {@link #looks}, with what it
 * means (the user's call, 2026-10-15). Any of them finds it; a {@link State#DISABLED disabled} one is not pressed.
 *
 * <p>A button the screen shows more than once is {@link #repeated}: a body picks the copy it presses ({@link Pick},
 * along an {@link Order}), and a way through one names its copy ({@link Way#pick}). A button in a list that scrolls
 * or a column whose slots shift is a member of one of its screen's {@link Strip strips}, which say how it is
 * scrolled to.
 *
 * @param picture      the picture clicked, as the button usually looks
 * @param did          what it did when tried, opening nothing; null when untried or when it opened a screen
 * @param purpose      what it is for; null when not named yet
 * @param never        why it is never done; null when it may be
 * @param comesAndGoes whether it shows only at times
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param looks        the button's other looks, each with what it means
 * @param when         when it is there, for one that comes and goes; null when not known
 * @param repeats      whether the screen shows it more than once: copies a body picks among
 */
public record Button(ImageTemplate picture, Did did, Purpose purpose, String never, boolean comesAndGoes,
                     ImageTemplate lock, List<Look> looks, BooleanSupplier when, boolean repeats) implements Input {

    public Button {
        looks = looks == null ? List.of() : List.copyOf(looks);
        // When it is there is said only of what comes and goes.
        comesAndGoes = comesAndGoes || when != null;
    }

    /** {@code picture} clicked at its best match. */
    public static Button of(ImageTemplate picture) {
        return new Button(picture, null, null, null, false, null, List.of(), null, false);
    }

    @Override
    public Button did(Did did) {
        return new Button(picture, did, purpose, never, comesAndGoes, lock, looks, when, repeats);
    }

    @Override
    public Button purpose(Purpose purpose) {
        return new Button(picture, did, purpose, never, comesAndGoes, lock, looks, when, repeats);
    }

    @Override
    public Button never(String why) {
        return new Button(picture, did, purpose, why, comesAndGoes, lock, looks, when, repeats);
    }

    @Override
    public Button sometimes() {
        return new Button(picture, did, purpose, never, true, lock, looks, when, repeats);
    }

    @Override
    public Button locked(ImageTemplate lock) {
        return new Button(picture, did, purpose, never, comesAndGoes, lock, looks, when, repeats);
    }

    @Override
    public Button when(BooleanSupplier condition) {
        return new Button(picture, did, purpose, never, comesAndGoes, lock, looks, condition, repeats);
    }

    /** This button, also looking as each of {@code looks} says. */
    public Button looks(List<Look> looks) {
        return new Button(picture, did, purpose, never, comesAndGoes, lock, looks, when, repeats);
    }

    /** This button, shown more than once on its screen: Claim on each mission card. */
    public Button repeated() {
        return new Button(picture, did, purpose, never, comesAndGoes, lock, looks, when, true);
    }

    /** This button with {@code look} put in place of the one of the same picture, or added last. */
    public Button withLook(Look look) {
        List<Look> now = new ArrayList<>(looks);
        int at = now.stream().map(v -> v.picture().id()).toList().indexOf(look.picture().id());
        if (at >= 0) now.set(at, look);
        else now.add(look);
        return looks(now);
    }

    /** Every look of it, the usual one first as {@link State#NORMAL}. */
    public List<Look> allLooks() {
        if (picture == null) return List.of();
        List<Look> all = new ArrayList<>();
        all.add(Look.of(picture, State.NORMAL));
        all.addAll(looks);
        return all;
    }

    /** What the look {@code pictureId} means: one of its looks' state; empty for a picture that is none of them. */
    public Optional<State> stateOf(String pictureId) {
        return allLooks().stream().filter(v -> v.picture().id().equals(pictureId)).map(Look::state).findFirst();
    }

    @Override
    public String label() {
        return picture == null ? "?" : picture.id();
    }
}
