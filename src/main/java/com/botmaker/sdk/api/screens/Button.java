package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.time.Duration;
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
 * <p>A button is clicked unless its {@link #gesture} says otherwise: double-clicked, right-clicked, held a time
 * ({@link #hold(Duration)}), or only pointed at (2026-10-22).
 *
 * <p>A text field is a button that {@link #types} once clicked: one of the bot's {@code Texts}
 * ({@code .types(Texts.GUILD)}), which a walk types on its way through, or the text a body gives
 * ({@link #typed()}, {@code Navigate.type}), which no walk goes through (2026-10-22).
 *
 * <p>A button that is dragged is pressed, moved onto a picture ({@link #dragsOnto}) or into a zone ({@link
 * #dragsInto}), and let go, in place of its gesture: a card onto its slot, a slider to its end, a swipe on a phone. A
 * drop not on the screen leaves it unpressed (2026-10-22).
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
 * @param gesture      how it is pressed; {@link Gesture#CLICK} when null
 * @param hold         how long a {@link Gesture#HOLD} is held; {@link #HOLD_TIME} when null, none for any other
 * @param typing       what it types once clicked, for a text field; null when it is only clicked
 * @param drag         where it is dragged once pressed; null when it is pressed by its gesture
 */
public record Button(ImageTemplate picture, boolean isTried, boolean isLeaving, boolean isInfo, String never,
                     boolean comesAndGoes, ImageTemplate lock, List<Look> looks, BooleanSupplier when,
                     boolean repeats, boolean cycles, Gesture gesture, Duration hold, Typing typing, Drag drag)
        implements Input {

    /** How long a button held for no time of its own is held: a long press on most phones and games. */
    public static final Duration HOLD_TIME = Duration.ofMillis(800);

    /** How long a drag of no time of its own takes from the button to its drop: slow enough for a game to follow. */
    public static final Duration DRAG_TIME = Duration.ofMillis(400);

    public Button {
        looks = looks == null ? List.of() : List.copyOf(looks);
        // When it is there is said only of what comes and goes.
        comesAndGoes = comesAndGoes || when != null;
        // A drag with nowhere to go is none; a drag is its own press: no gesture, nothing typed.
        if (drag != null && drag.onto() == null && drag.into() == null) drag = null;
        if (drag != null) {
            gesture = Gesture.CLICK;
            typing = null;
        }
        gesture = gesture == null ? Gesture.CLICK : gesture;
        // Only a hold lasts a time.
        if (gesture != Gesture.HOLD) hold = null;
    }

    /** {@code picture} clicked at its best match. */
    public static Button of(ImageTemplate picture) {
        return new Button(picture, false, false, false, null, false, null, List.of(), null, false, false, null, null,
                null, null);
    }

    /**
     * A button showing each of {@code looks} in turn, one more on each press and the first again after the last:
     * {@code Button.cycling(List.of(Pictures.SPEED_X1, Pictures.SPEED_X10, Pictures.SPEED_X50))}.
     */
    public static Button cycling(List<ImageTemplate> looks) {
        List<Look> rest = looks.stream().skip(1).map(look -> Look.of(look, State.NORMAL)).toList();
        return new Button(looks.isEmpty() ? null : looks.getFirst(), false, false, false, null, false, null, rest,
                null, false, true, null, null, null, null);
    }

    /** The looks a {@link #cycling} button shows in turn, its usual picture first. */
    public List<ImageTemplate> cycle() {
        return allLooks().stream().map(Look::picture).toList();
    }

    @Override
    public Button tried() {
        return new Button(picture, true, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    @Override
    public Button leaves() {
        return new Button(picture, isTried, true, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    @Override
    public Button info() {
        return new Button(picture, isTried, isLeaving, true, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    @Override
    public Button never(String why) {
        return new Button(picture, isTried, isLeaving, isInfo, why, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    @Override
    public Button sometimes() {
        return new Button(picture, isTried, isLeaving, isInfo, never, true, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    @Override
    public Button locked(ImageTemplate lock) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    @Override
    public Button when(BooleanSupplier condition) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, condition, repeats,
                cycles, gesture, hold, typing, drag);
    }

    /** This button, also looking as each of {@code looks} says. */
    public Button looks(List<Look> looks) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    /** This button, shown more than once on its screen: Claim on each mission card. */
    public Button repeated() {
        return repeats(true);
    }

    /** This button, shown more than once on its screen or not. */
    public Button repeats(boolean repeats) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    /**
     * This button, pressed by {@code gesture}: {@code .gesture(Gesture.DOUBLE)}. A hold keeps its time; a drag is
     * pressed by a gesture no more.
     */
    public Button gesture(Gesture gesture) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, null);
    }

    /** This button, held down {@code time} then let go: {@code .hold(Duration.ofSeconds(2))}. */
    public Button hold(Duration time) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                Gesture.HOLD, time, typing, null);
    }

    /** This button, dragged onto {@code target}, a picture the screen shows: a card onto its slot. */
    public Button dragsOnto(ImageTemplate target) {
        return drag(new Drag(target, null, drag == null ? null : drag.time()));
    }

    /** This button, dragged into {@code zone}, one of the bot's {@code Zones}: a slider to its end, a swipe. */
    public Button dragsInto(Zone zone) {
        return drag(new Drag(null, zone, drag == null ? null : drag.time()));
    }

    /** This drag, its move taking {@code time}: {@code .dragTime(Duration.ofSeconds(1))}. A button not dragged stays. */
    public Button dragTime(Duration time) {
        return drag == null ? this : drag(new Drag(drag.onto(), drag.into(), time));
    }

    /** This button, dragged as {@code drag} says once pressed, in place of its gesture and typing; null: pressed. */
    public Button drag(Drag drag) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, drag);
    }

    /** This button, a text field: clicked, then {@code text} typed into it, one of the bot's {@code Texts}. */
    public Button types(Words text) {
        return typing(new Typing(text, isSubmitting(), isClearing()));
    }

    /** This button, a text field whose text the body gives: {@code Navigate.type(Screens.GUILD.search, name)}. */
    public Button typed() {
        return typing(new Typing(null, isSubmitting(), isClearing()));
    }

    /** This button, a text field Enter is pressed in once its text is typed. */
    public Button submits() {
        return typing(new Typing(text(), true, isClearing()));
    }

    /** This button, a text field emptied before its text is typed. */
    public Button clears() {
        return typing(new Typing(text(), isSubmitting(), true));
    }

    /** This button, typing as {@code typing} says once clicked, a drag clicked again; null: typing nothing. */
    public Button typing(Typing typing) {
        return new Button(picture, isTried, isLeaving, isInfo, never, comesAndGoes, lock, looks, when, repeats, cycles,
                gesture, hold, typing, typing == null ? drag : null);
    }

    /** Whether it is dragged once pressed, onto a picture or into a zone. */
    public boolean isDrag() {
        return drag != null;
    }

    /** How long its drag's move takes: its own time, else {@link #DRAG_TIME}; null when it is not dragged. */
    public Duration dragTime() {
        return drag == null ? null : drag.time() == null ? DRAG_TIME : drag.time();
    }

    /** Whether it is a text field, typed into once clicked. */
    public boolean isField() {
        return typing != null;
    }

    /** What it types, one of the bot's {@code Texts}; null when it is no field, or its body gives the text. */
    public Words text() {
        return typing == null ? null : typing.text();
    }

    /** Whether it is a text field whose text only a body gives: no walk goes through it. */
    public boolean typedByBody() {
        return typing != null && typing.text() == null;
    }

    /** Whether Enter is pressed once its text is typed. */
    public boolean isSubmitting() {
        return typing != null && typing.submits();
    }

    /** Whether the field is emptied before its text is typed. */
    public boolean isClearing() {
        return typing != null && typing.clears();
    }

    /** How long it is held when its gesture is a hold: its own time, else {@link #HOLD_TIME}. */
    public Duration holdTime() {
        return hold == null ? HOLD_TIME : hold;
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
