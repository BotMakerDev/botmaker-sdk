package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 * <p>A button that is dragged is pressed, moved onto a picture ({@link #dragsOnto}) or into an area ({@link
 * #dragsInto}), and let go, in place of its gesture: a card onto its slot, a slider to its end, a swipe on a phone. A
 * drop not on the screen leaves it unpressed (2026-10-22).
 *
 * <p>A button only ever found where it sits from something else is {@link #place placed}: the Claim right of a row's
 * title. A spot whose look never repeats, an avatar, has no picture of its own: it is pressed {@link #beside} a
 * stable anchor picture, at an offset in the anchor's size, and only when the anchor is found; a {@link #grid} of such
 * spots is an inventory's cells (2026-10-22).
 *
 * <p>A button of an app is an {@link Element} the screen's tree holds ({@link #of(Element)}), found by what it is
 * rather than by its picture; one the tree finds more than once and that is not {@link #repeated} stops the run
 * (2026-10-31).
 *
 * <p>A game setting the bot needs, Graphics' <i>Quality: High</i>, is a button that {@link #keeps} a look: a run
 * presses it at its start until it shows that look, and stops saying which when it cannot (2026-11-02). Every look it
 * may show on the way is one of its {@link #looks}: one the map lacks is no button found.
 *
 * <p>A button that shows only on its screen, or with others only there, tells the screen: it is {@link #marking}, found
 * by any of its looks, so a tab shown picked or dimmed still tells it. One that never shows there tells it apart from
 * a screen alike: {@link #absent} (2026-11-02).
 *
 * @param picture     the picture clicked, as the button usually looks; for a button {@link #beside} one, the anchor
 * @param isTried      whether Discovery tried it and it opened no screen
 * @param isLeaving    whether it goes to a screen mapped already that it is no way into
 * @param isInfo       whether it only tells
 * @param verdict      whether a run exploring on its own may press it; {@link Verdict#NONE} when null
 * @param isOccasional whether it shows only at times
 * @param locked       the picture proving it locked on this account; null when it is not
 * @param looks        the button's other looks, each with what it means
 * @param when         when it is there, for one that comes and goes; null when not known
 * @param repeats      whether the screen shows it more than once: copies a body picks among; a grid always does
 * @param cycles       whether each press shows its next look, {@code picture} then {@code looks} in turn
 * @param gesture      how it is pressed; {@link Gesture#CLICK} when null
 * @param hold         how long a {@link Gesture#HOLD} is held; {@link #HOLD_TIME} when null, none for any other
 * @param typing       what it types once clicked, for a text field; null when it is only clicked
 * @param drag         where it is dragged once pressed; null when it is pressed by its gesture
 * @param place        where it sits from something else the screen shows; null when anywhere
 * @param beside       where it is pressed from its anchor {@code picture}; null when on the picture itself
 * @param grid        its cells from the anchor, for a grid of spots; null when it is one
 * @param element     what finds it in the screen's tree; null for a button found by its picture
 * @param keeps        the look a run makes it show before anything else, one of its looks; null when any will do
 * @param isMarking    whether it tells its screen: shown in any of its looks while the game shows it
 * @param isAbsent     whether none of its looks shows while the game shows its screen
 */
public record Button(ImageTemplate picture, boolean isTried, boolean isLeaving, boolean isInfo, Verdict verdict,
                     boolean isOccasional, ImageTemplate locked, List<Look> looks, BooleanSupplier when,
                     boolean repeats, boolean cycles, Gesture gesture, Duration hold, Typing typing, Drag drag,
                     Place place, Beside beside, Grid grid, Element element, ImageTemplate keeps, boolean isMarking,
                     boolean isAbsent)
        implements Input {

    /** How long a button held for no time of its own is held: a long press on most phones and games. */
    public static final Duration HOLD_TIME = Duration.ofMillis(800);

    /** How long a drag of no time of its own takes from the button to its drop: slow enough for a game to follow. */
    public static final Duration DRAG_TIME = Duration.ofMillis(400);

    public Button {
        verdict = verdict == null ? Verdict.NONE : verdict;
        looks = looks == null ? List.of() : List.copyOf(looks);
        // When it is there is said only of what comes and goes.
        isOccasional = isOccasional || when != null;
        // A drag with nowhere to go is none; a drag is its own press: no gesture, nothing typed.
        if (drag != null && drag.onto() == null && drag.into() == null) drag = null;
        if (drag != null) {
            gesture = Gesture.CLICK;
            typing = null;
        }
        gesture = gesture == null ? Gesture.CLICK : gesture;
        // Only a hold lasts a time.
        if (gesture != Gesture.HOLD) hold = null;
        // A cycling button is pressed on its picture: no spot, no grid. A spot is measured from the button's picture,
        // its anchor; a grid's cells are its copies.
        if (cycles) {
            beside = null;
            grid = null;
        }
        if (beside != null && !Objects.equals(beside.other(), picture)) beside = Beside.of(picture, beside.across(),
                beside.down());
        // An element is pressed where the tree puts it: no spot, no grid, no steps through looks.
        if (element != null) {
            beside = null;
            grid = null;
            cycles = false;
        }
        repeats = repeats || grid != null;
        // A part that never shows tells nothing by showing; a spot has no look of its own to be seen by.
        if (isAbsent) isMarking = false;
        if (beside != null || grid != null) {
            isMarking = false;
            isAbsent = false;
        }
    }

    /** Whether it is found in the screen's tree, by its {@link #element}, not by its picture. */
    public boolean isElement() {
        return element != null;
    }

    /** {@code picture} clicked at its best match. */
    public static Button of(ImageTemplate picture) {
        return new Button(picture, false, false, false, null, false, null, List.of(), null, false, false, null, null,
                null, null, null, null, null, null, null, false, false);
    }

    /**
     * An app's input found in the screen's tree, pressed at its centre: {@code Button.of(Elements.BATTERY)}. Its
     * picture is the element's {@link Element#shot}, shown on 🗺 Screens and never matched; none without a shot. Its
     * looks a tree tells are read from it: greyed out when not enabled, picked when selected or checked.
     */
    public static Button of(Element element) {
        if (element == null) throw new IllegalArgumentException("A button of an element needs the element");
        return new Button(element.shot() == null ? null : new ImageTemplate(element.shot()), false, false, false,
                null, false, null, List.of(), null, false, false, null, null, null, null, null, null, null, element, null, false,
                false);
    }

    /**
     * A spot with no picture of its own, pressed {@code across} of {@code anchor}'s widths right of its centre and
     * {@code down} of its heights below, as a tree's {@link Beside} measures: Home's avatar beside its stable frame. It
     * is pressed only when {@code anchor} is found, so a wrong screen never takes a blind click.
     */
    public static Button beside(ImageTemplate anchor, double across, double down) {
        return new Button(anchor, false, false, false, null, false, null, List.of(), null, false, false, null, null,
                null, null, null, Beside.of(anchor, across, down), null, null, null, false, false);
    }

    /**
     * A button showing each of {@code looks} in turn, one more on each press and the first again after the last:
     * {@code Button.cycling(List.of(Pictures.SPEED_X1, Pictures.SPEED_X10, Pictures.SPEED_X50))}.
     */
    public static Button cycling(List<ImageTemplate> looks) {
        List<Look> rest = looks.stream().skip(1).map(look -> Look.of(look, State.NORMAL)).toList();
        return new Button(looks.isEmpty() ? null : looks.getFirst(), false, false, false, null, false, null, rest,
                null, false, true, null, null, null, null, null, null, null, null, null, false, false);
    }

    /** The looks a {@link #cycling} button shows in turn, its usual picture first. */
    public List<ImageTemplate> cycle() {
        if (picture == null) return List.of();
        List<ImageTemplate> cycle = new ArrayList<>();
        cycle.add(picture);
        looks.forEach(look -> cycle.add(look.picture()));
        return cycle;
    }

    @Override
    public Button tried() {
        return new Button(picture, true, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    @Override
    public Button leaving() {
        return new Button(picture, isTried, true, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    @Override
    @Deprecated(forRemoval = true)
    public Button leaves() {
        return leaving();
    }

    @Override
    public Button info() {
        return new Button(picture, isTried, isLeaving, true, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    @Override
    public Button never(String why) {
        return verdict(Verdict.never(why));
    }

    @Override
    public Button free() {
        return verdict(Verdict.FREE);
    }

    @Override
    public <G, T> Button spends(Params.Of<G, T> fact) {
        return verdict(new Verdict.Spends(fact));
    }

    @Override
    public Button verdict(Verdict verdict) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats,
                cycles, gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    @Override
    public Button occasional() {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, true, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    @Override
    @Deprecated(forRemoval = true)
    public Button sometimes() {
        return occasional();
    }

    @Override
    public Button locked(ImageTemplate proof) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, proof, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    @Override
    public Button when(BooleanSupplier condition) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, condition, repeats,
                cycles, gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /** This button, also looking as each of {@code looks} says. */
    @Link
    public Button looks(List<Look> looks) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /**
     * This button, a game setting the bot needs: a run presses it at its start until it shows {@code look}, one of its
     * looks (taken as {@link State#SELECTED} when it is none of them), and stops saying which when it cannot —
     * {@code .keeps(Pictures.QUALITY_HIGH)}; null: any look will do.
     */
    @Link
    public Button keeps(ImageTemplate look) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, look, isMarking, isAbsent);
    }

    /** This button, telling its screen: shown in any of its looks while the game shows it. */
    @Link
    public Button marking() {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, true, false);
    }

    /** This button, telling its screen apart from one alike by never showing on it, in any of its looks. */
    @Link
    public Button absent() {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, false, true);
    }

    /** This button, telling nothing of its screen: neither {@link #marking} nor {@link #absent}. */
    public Button unmarked() {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, false, false);
    }

    /** This button, shown more than once on its screen: Claim on each mission card. */
    public Button repeated() {
        return repeats(true);
    }

    /** This button, shown more than once on its screen or not. */
    public Button repeats(boolean repeats) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /**
     * This button, pressed by {@code gesture}: {@code .gesture(Gesture.DOUBLE)}. A hold keeps its time; a drag is
     * pressed by a gesture no more.
     */
    public Button gesture(Gesture gesture) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, null, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /** This button, held down {@code time} then let go: {@code .hold(Duration.ofSeconds(2))}. */
    @Link
    public Button hold(Duration time) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                Gesture.HOLD, time, typing, null, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /** This button, dragged onto {@code target}, a picture the screen shows: a card onto its slot. */
    public Button dragsOnto(ImageTemplate target) {
        return drag(new Drag(target, null, drag == null ? null : drag.time()));
    }

    /** This button, dragged into {@code area}, measured from a picture the screen shows: a slider to its end, a swipe. */
    public Button dragsInto(Area area) {
        return drag(new Drag(null, area, drag == null ? null : drag.time()));
    }

    /** This drag, its move taking {@code time}: {@code .dragTime(Duration.ofSeconds(1))}. A button not dragged stays. */
    public Button dragTime(Duration time) {
        return drag == null ? this : drag(new Drag(drag.onto(), drag.into(), time));
    }

    /** This button, dragged as {@code drag} says once pressed, in place of its gesture and typing; null: pressed. */
    public Button drag(Drag drag) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /** This button, a text field: clicked, then {@code text} typed into it, one of the bot's {@code Texts}. */
    @Link
    public Button types(Words text) {
        return typing(new Typing(text, isSubmitting(), isClearing()));
    }

    /** This button, a text field whose text the body gives: {@code Navigate.type(Screens.GUILD.search, name)}. */
    @Link
    public Button typed() {
        return typing(new Typing(null, isSubmitting(), isClearing()));
    }

    /** This button, a text field Enter is pressed in once its text is typed. */
    @Link
    public Button submitting() {
        return typing(new Typing(types(), true, isClearing()));
    }

    /** Was {@link #submitting()}, named as its reader {@link #isSubmitting()} is. */
    @Deprecated(forRemoval = true)
    public Button submits() {
        return submitting();
    }

    /** This button, a text field emptied before its text is typed. */
    @Link
    public Button clearing() {
        return typing(new Typing(types(), isSubmitting(), true));
    }

    /** Was {@link #clearing()}, named as its reader {@link #isClearing()} is. */
    @Deprecated(forRemoval = true)
    public Button clears() {
        return clearing();
    }

    /** This button, typing as {@code typing} says once clicked, a drag clicked again; null: typing nothing. */
    public Button typing(Typing typing) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, typing == null ? drag : null, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /** This button, only ever found where {@code place} says: the Claim right of a row's title; null: anywhere. */
    @Link
    public Button place(Place place) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when, repeats, cycles,
                gesture, hold, typing, drag, place, beside, grid, element, keeps, isMarking, isAbsent);
    }

    /**
     * This button, a grid of spots from its anchor, each a copy a {@link Pick} names: the first where its
     * {@link #beside} puts it (else on the anchor), the others as {@code grid} steps; null: one spot.
     */
    public Button grid(Grid grid) {
        return new Button(picture, isTried, isLeaving, isInfo, verdict, isOccasional, locked, looks, when,
                grid == null && this.grid != null ? false : repeats, cycles, gesture, hold, typing, drag, place,
                beside, grid, element, keeps, isMarking, isAbsent);
    }

    /** Whether it is pressed beside its picture, an anchor, not on it: a spot, or a grid's cells. */
    public boolean isBeside() {
        return beside != null || grid != null;
    }

    /**
     * Where it is pressed when its picture shows at {@code found}: there; a spot at its offset, the anchor's size; a
     * grid's cells, row by row.
     */
    public List<Rect> spots(Rect found) {
        if (grid != null) return grid.cells(found, beside);
        return List.of(beside == null ? found : beside.place(found));
    }

    /** Whether it is dragged once pressed, onto a picture or into an area. */
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
    public Words types() {
        return typing == null ? null : typing.text();
    }

    /** Was {@link #types()}, named as its link {@link #types(Words)} is. */
    @Deprecated(forRemoval = true)
    public Words text() {
        return types();
    }

    /** Whether it is a text field whose text only a body gives ({@link #typed()}): no walk goes through it. */
    public boolean isTyped() {
        return typing != null && typing.text() == null;
    }

    /** Was {@link #isTyped()}, named as its link {@link #typed()} is. */
    @Deprecated(forRemoval = true)
    public boolean typedByBody() {
        return isTyped();
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

    /**
     * Every look of it, the usual one first as {@link State#NORMAL}, and the look it {@link #keeps} last as
     * {@link State#SELECTED} when it is none of them: a press until it shows must see it, and the bot's Java keeps
     * only what its author wrote.
     */
    public List<Look> allLooks() {
        if (picture == null) return List.of();
        List<Look> all = new ArrayList<>();
        all.add(Look.of(picture, State.NORMAL));
        all.addAll(looks);
        if (keeps != null && all.stream().noneMatch(look -> look.picture().id().equals(keeps.id()))) {
            all.add(Look.of(keeps, State.SELECTED));
        }
        return all;
    }

    /** What the look {@code pictureId} means: one of its looks' state; empty for a picture that is none of them. */
    public Optional<ButtonState> stateOf(String pictureId) {
        return allLooks().stream().filter(v -> v.picture().id().equals(pictureId)).map(Look::state).findFirst();
    }

    @Override
    public String label() {
        String id = picture != null ? picture.id() : element != null ? element.shown() : "?";
        return grid != null ? "cells by " + id : beside != null ? "beside " + id : id;
    }
}
