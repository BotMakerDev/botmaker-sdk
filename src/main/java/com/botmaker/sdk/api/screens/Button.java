package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A picture on a {@link Screen} that is clicked — {@code Button.of(Pictures.CAMPAIGN_CLAIM)}.
 *
 * <p>A button that changes its look stays one button: each other look is one of its {@link #looks}, with what it
 * means (the user's call, 2026-10-15). Any of them finds it; a {@link State#DISABLED disabled} one is not pressed. A
 * button that steps through its looks on each press, x1 → x10 → x50, is {@link #cycling}: {@link Pick#until} presses
 * it until it shows the look a body wants (2026-10-17).
 *
 * <p>A button the screen shows more than once is {@link #repeated}: a body picks the copy it presses ({@link Pick},
 * along an {@link Order}), and a way through one names its copy ({@link Way#pick}). A button in a list that scrolls
 * or a column whose slots shift is a member of one of its screen's {@link Strip strips}, which say how it is
 * scrolled to.
 *
 * @param picture      the picture clicked, as the button usually looks
 * @param isTried      whether Discovery tried it and it opened no screen
 * @param isLeaving    whether it goes to a screen mapped already that it is no way into
 * @param isInfo       whether it only tells
 * @param never        why it is never done; null when it may be
 * @param comesAndGoes whether it shows only at times
 * @param lock         the picture proving it locked on this account; null when it is not
 * @param looks        the button's other looks, each with what it means
 * @param when         when it is there, for one that comes and goes; null when not known
 * @param repeats      whether the screen shows it more than once: copies a body picks among
 * @param cycles       whether each press shows its next look, {@code picture} then {@code looks} in turn
 */
public record Button(ImageTemplate picture, boolean isTried, boolean isLeaving, boolean isInfo, String never,
                     boolean comesAndGoes, ImageTemplate lock, List<Look> looks, BooleanSupplier when,
                     boolean repeats, boolean cycles) implements Input {

    public Button {
        looks = looks == null ? List.of() : List.copyOf(looks);
        // When it is there is said only of what comes and goes.
        comesAndGoes = comesAndGoes || when != null;
    }

    /** {@code picture} clicked at its best match. */
    public static Button of(ImageTemplate picture) {
        return new Button(picture, false, false, false, null, false, null, List.of(), null, false, false);
    }

    /**
     * A button showing each of {@code looks} in turn, one more on each press and the first again after the last:
     * {@code Button.cycling(List.of(Pictures.SPEED_X1, Pictures.SPEED_X10, Pictures.SPEED_X50))}.
     */
    public static Button cycling(List<ImageTemplate> looks) {
        List<Look> rest = looks.stream().skip(1).map(look -> Look.of(look, State.NORMAL)).toList();
        return new Button(looks.isEmpty() ? null : looks.getFirst(), false, false, false, null, false, null, rest,
                null, false, true);
    }

    /** The looks a {@link #cycling} button shows in turn, its usual picture first. */
    public List<ImageTemplate> cycle() {
        return allLooks().stream().map(Look::picture).toList();
    }

    @Override
    public Button tried() {
        return new Button(picture, true, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles);
    }

    @Override
    public Button leaves() {
        return new Button(picture, isTried, true, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles);
    }

    @Override
    public Button info() {
        return new Button(picture, isTried, isLeaving, true, never, comesAndGoes, lock, looks, when, repeats, cycles);
    }

    @Override
    public Button never(String why) {
        return new Button(picture, isTried, isLeaving, isInfo, why, comesAndGoes, lock, looks, when, repeats, cycles);
    }

    @Override
    public Button sometimes() {
        return new Button(picture, isTried, isLeaving, isInfo, never, true, lock, looks, when, repeats, cycles);
    }

    @Override
    public Button locked(ImageTemplate lock) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles);
    }

    @Override
    public Button when(BooleanSupplier condition) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, condition, repeats,
                cycles);
    }

    /** This button, also looking as each of {@code looks} says. */
    public Button looks(List<Look> looks) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles);
    }

    /** This button, shown more than once on its screen: Claim on each mission card. */
    public Button repeated() {
        return repeats(true);
    }

    /** This button, shown more than once on its screen or not. */
    public Button repeats(boolean repeats) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles);
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
