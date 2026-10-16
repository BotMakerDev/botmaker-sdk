package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Everything about a {@link Screen} but its inputs, as its {@link Screen#setup} returns it: its {@link #markers},
 * the {@link #from way in} that made it known, any {@link #alsoFrom other way}, its {@link #back way out}, its
 * {@link #variants looks}, and what automating it taught: its {@link #notes}, how far it got ({@link #reached}) and
 * what it still {@link #owes}.
 *
 * <pre>{@code
 * return Setup.named("Campaign").markers(List.of(Pictures.CAMPAIGN_TITLE)).from(HOME.campaign)
 *         .back(CAMPAIGN.close).notes("Loot fills over 8 h; Claim takes it.").reached(Reached.SURVEYED);
 * }</pre>
 *
 * <p>The game shows a screen when every one of its {@link #markers} shows and none of its {@link #absent} pictures
 * does. One picture is enough when only this screen shows it; a screen that shares its title or its frame with
 * another (a window's tabs, a dialog of one style) is told by a set, or by a button that is missing (the user's call,
 * 2026-10-16).
 *
 * <p>The screens are a graph, and its tree is the {@code from} of each: a screen names the input it is reached
 * through, never the screens it leads to. A {@link #popup} opens on its own (a reward, a level up) and is in no tree:
 * it has markers and a way out only.
 *
 * <p>Discovery fills it in (the assistant's try_input, or ▶ Try in 🗺 Screens); a run walks it to reach a screen.
 * The notes live here, with the screen, rather than in a file of their own (the user's call, 2026-10-15): a rename or
 * a delete takes them along. So does where its card was moved to on 🗺 Screens' map ({@link #at}), which a run never
 * reads.
 *
 * @param name     the name the game shows for it, in plain words
 * @param markers  pictures that all show while the game shows this screen; empty until one is cut
 * @param absent   pictures none of which shows while the game shows this screen
 * @param from     the way in that made it known, through an input of another screen ({@code .from(HOME.market)},
 *                 then {@code .fromPick}, {@code .fromLooks} or {@code .fromWhen} when it needs them); null for the
 *                 first screen and for a popup
 * @param alsoFrom other ways in
 * @param back     the way out to where it was opened from, usually one of its own inputs; null for the default: a
 *                 close button, then Escape
 * @param variants its looks, each with its own markers: the event screen dressed for an event
 * @param isPopup  whether it opens on its own rather than from an input
 * @param notes    what automating it taught, in plain lines; null for none
 * @param reached  how far automating it got; null while it is not surveyed
 * @param owes     what is still to cut or find on it, one thing each: {@code marker — every icon here animates}
 * @param at       the top-left corner of its card on 🗺 Screens' map, in the map's own units; null for wherever the
 *                 map's layout puts it
 */
public record Setup(String name, List<ImageTemplate> markers, List<ImageTemplate> absent, Way from,
                    List<Way> alsoFrom, Input back, List<ScreenVariant> variants, boolean isPopup, String notes,
                    Reached reached, List<String> owes, Point at) {

    public Setup {
        markers = markers == null ? List.of() : List.copyOf(markers);
        absent = absent == null ? List.of() : List.copyOf(absent);
        alsoFrom = alsoFrom == null ? List.of() : List.copyOf(alsoFrom);
        variants = variants == null ? List.of() : List.copyOf(variants);
        notes = notes == null || notes.isBlank() ? null : notes;
        owes = owes == null ? List.of() : List.copyOf(owes);
    }

    /** A screen called {@code name}, nothing known of it yet. */
    public static Setup named(String name) {
        return new Setup(name, List.of(), List.of(), null, List.of(), null, List.of(), false, null, null, List.of(),
                null);
    }

    /** This screen, the game showing it as {@code name}. */
    public Setup name(String name) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, told by {@code markers}: all of them show while the game shows it. */
    public Setup markers(List<ImageTemplate> markers) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, told apart by {@code absent}: none of them shows while the game shows it. */
    public Setup absent(List<ImageTemplate> absent) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, made known through {@code via}, an input of another screen: {@code HOME.market}. */
    public Setup from(Input via) {
        return fromWay(via == null ? null : Way.of(via));
    }

    /** This screen, made known by {@code way}; null for none. */
    public Setup fromWay(Way way) {
        return new Setup(name, markers, absent, way, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, its way in going through the copy of a repeated button {@code pick} names. */
    public Setup fromPick(Pick pick) {
        return from == null ? this : fromWay(from.pick(pick));
    }

    /** This screen, its way in taken only while its button looks as {@code state} says. */
    public Setup fromLooks(State state) {
        return from == null ? this : fromWay(from.whenLooks(state));
    }

    /** This screen, its way in taken only while {@code condition} answers true: {@code Firestone::eventOn}. */
    public Setup fromWhen(BooleanSupplier condition) {
        return from == null ? this : fromWay(from.when(condition));
    }

    /** The input its way in goes through; null for none. */
    public Input fromInput() {
        return from == null ? null : from.via();
    }

    /** This screen, reached by {@code ways} too. */
    public Setup alsoFrom(List<Way> ways) {
        return new Setup(name, markers, absent, from, ways, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, left by {@code back}: {@code MARKET.close}. */
    public Setup back(Input back) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, looking as each of {@code variants} at times. */
    public Setup variants(List<ScreenVariant> variants) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, opening on its own: a reward, a level up, an offer. */
    public Setup popup() {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, true, notes, reached, owes, at);
    }

    /** This screen, a screen of the tree again: opened by an input, not on its own. */
    public Setup notPopup() {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, false, notes, reached, owes, at);
    }

    /** This screen, with {@code notes} on it; blank for none. */
    public Setup notes(String notes) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, automated as far as {@code reached}; null while it is not surveyed. */
    public Setup reached(Reached reached) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, still owing {@code owes}. */
    public Setup owes(List<String> owes) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** This screen, its card on 🗺 Screens' map with its top-left corner at {@code at}; null: where the layout puts it. */
    public Setup at(Point at) {
        return new Setup(name, markers, absent, from, alsoFrom, back, variants, isPopup, notes, reached, owes, at);
    }

    /** Whether it has a marker to tell it by. */
    public boolean hasMarker() {
        return !markers.isEmpty();
    }

    /** Its first marker, the picture that stands for it on a card or a button; null when it has none. */
    public ImageTemplate firstMarker() {
        return markers.isEmpty() ? null : markers.getFirst();
    }

    /** Its ways in: its {@code from}, then each {@code alsoFrom}. */
    public List<Way> ways() {
        List<Way> ways = new ArrayList<>();
        if (from != null) ways.add(from);
        ways.addAll(alsoFrom);
        return ways;
    }

    /** This screen with {@code variant} put in place of the one of the same name, or added last. */
    public Setup withVariant(ScreenVariant variant) {
        List<ScreenVariant> now = new ArrayList<>(variants);
        int at = now.stream().map(ScreenVariant::name).toList().indexOf(variant.name());
        if (at >= 0) now.set(at, variant);
        else now.add(variant);
        return variants(now);
    }
}
