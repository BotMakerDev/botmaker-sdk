package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.function.BooleanSupplier;

/**
 * One way to act on a {@link Screen}, a field of it: a {@link Button} clicked, a {@link TextButton} clicked on its
 * words, a {@link Hotkey} pressed, a {@link Strip} of buttons that scrolls or shifts, a {@link Tree} of linked
 * buttons that scrolls in two dimensions, or the bot's own {@link Custom} Java. The traits here are each kind's.
 *
 * <p>Until it is {@link #tried}, {@link #leaving left by}, {@link #never} done or {@link #locked locked} it is
 * untried: Discovery tries every input of a screen before the screen counts as finished. An input that opened a
 * screen is tried too, and says so by the screen it opened ({@link Setup#from}). An input that is
 * {@link #occasional occasional} never holds a screen back while it is not on it; {@link #when} says when it is
 * there, as a method of the bot or of a plugin ({@code Firestone::eventOn}), and a run asks it before walking there
 * (the user's call, 2026-10-15).
 *
 * <p>Only what changes a walk or Discovery is written here: what an input did beyond that, or what it is for, is
 * not (the user's call, 2026-10-17).
 *
 * <p>A strip's traits are its members' defaults: an occasional strip is one whose members each may be absent (the
 * user's call, 2026-10-16).
 *
 * <p>Each trait is a {@link Link} written after the input's factory, named as its reader is: {@code .leaving()} is
 * read by {@link #isLeaving()}, {@code .locked(…)} by {@link #locked()} (2026-11-01).
 */
public sealed interface Input extends ScreenPart permits Button, TextButton, Hotkey, Strip, Tree, Custom {

    /** Whether Discovery tried it and it opened no screen: it changed this one, or nothing visible. */
    boolean isTried();

    /**
     * Whether it goes to a screen mapped already, one it is no way into since that screen is reached otherwise: a
     * Home button deep down. A screen whose every other input opens one is still a menu.
     */
    boolean isLeaving();

    /** Whether it only tells: help, rules, a leaderboard. Discovery tries it last, and what it opens after the rest. */
    boolean isInfo();

    /** Whether a run exploring on its own may press it: free, spending a fact, never, or none said yet. */
    Verdict verdict();

    /** Why it is never done (it spends premium currency or real money); null when it may be. */
    default String never() {
        return verdict() instanceof Verdict.Never(String why) ? why : null;
    }

    /** Whether it costs nothing the bot counts. */
    default boolean isFree() {
        return verdict() instanceof Verdict.Free;
    }

    /** The fact it spends, a method of a {@code @Facts} group ({@code Progress::gems}); null when it spends none. */
    default Params.Of<?, ?> spends() {
        return verdict() instanceof Verdict.Spends(Params.Of<?, ?> fact) ? fact : null;
    }

    /** Whether it shows only at times: a Claim when loot is ready, an event's icon. */
    boolean isOccasional();

    /** The picture proving it locked on this account; null when it is not. */
    ImageTemplate locked();

    /** When it is there, for an occasional one: a method answering whether it is; null when not known. */
    BooleanSupplier when();

    /** This input, tried, opening no screen. */
    @Link
    Input tried();

    /** This input, going to a screen mapped already that it is no way into. */
    @Link
    Input leaving();

    /** This input, only telling. */
    @Link
    Input info();

    /** This input, never done because {@code why}: it spends premium currency or real money. */
    @Link
    Input never(String why);

    /** This input, costing nothing the bot counts: a run exploring on its own may press it. */
    @Link
    Input free();

    /** This input, spending {@code fact}, a method of a {@code @Facts} group: {@code .spends(Progress::gems)}. */
    <G, T> Input spends(Params.Of<G, T> fact);

    /** This input, judged {@code verdict}. */
    Input verdict(Verdict verdict);

    /** This input, showing only at times. */
    @Link
    Input occasional();

    /** This input, proved locked on this account by {@code proof} at its place. */
    @Link
    Input locked(ImageTemplate proof);

    /** This input, occasional, there while {@code condition} answers true: {@code Firestone::eventOn}. */
    Input when(BooleanSupplier condition);

    /** Whether it shows only at times. */
    @Deprecated(forRemoval = true)
    default boolean comesAndGoes() {
        return isOccasional();
    }

    /** The picture proving it locked on this account; null when it is not. */
    @Deprecated(forRemoval = true)
    default ImageTemplate lock() {
        return locked();
    }

    /** This input, going to a screen mapped already that it is no way into. */
    @Deprecated(forRemoval = true)
    default Input leaves() {
        return leaving();
    }

    /** This input, showing only at times. */
    @Deprecated(forRemoval = true)
    default Input sometimes() {
        return occasional();
    }

    /** Whether it was tried, or needs no trying: tried, leaving, never done, or locked. One that opens a screen is too. */
    default boolean settled() {
        return isTried() || isLeaving() || never() != null || locked() != null;
    }

    /** What it acts on, in a sentence when it has no field name: {@code campaign_claim}, {@code key Q}. */
    String label();
}
