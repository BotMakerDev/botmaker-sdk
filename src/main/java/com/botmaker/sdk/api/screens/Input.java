package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.function.BooleanSupplier;

/**
 * One way to act on a {@link Screen}, a field of it: a {@link Button} clicked, a {@link TextButton} clicked on its
 * words, a {@link Hotkey} pressed, a {@link Strip} of buttons that scrolls or shifts, or a {@link Tree} of linked
 * buttons that scrolls in two dimensions. The traits here are each kind's.
 *
 * <p>Until it is {@link #tried}, {@link #leaves left by}, {@link #never} done or {@link #lock locked} it is untried:
 * Discovery tries every input of a screen before the screen counts as finished. An input that opened a screen is
 * tried too, and says so by the screen it opened ({@link Setup#from}). An input that {@link #comesAndGoes comes and
 * goes} never holds a screen back while it is not on it; {@link #when} says when it is there, as a method of the bot
 * or of a plugin ({@code Firestone::eventOn}), and a run asks it before walking there (the user's call, 2026-10-15).
 *
 * <p>Only what changes a walk or Discovery is written here: what an input did beyond that, or what it is for, is
 * not (the user's call, 2026-10-17).
 *
 * <p>A strip's traits are its members' defaults: a strip that {@link #comesAndGoes comes and goes} is one whose
 * members each may be absent (the user's call, 2026-10-16).
 */
public sealed interface Input extends ScreenPart permits Button, TextButton, Hotkey, Strip, Tree {

    /** Whether Discovery tried it and it opened no screen: it changed this one, or nothing visible. */
    boolean isTried();

    /**
     * Whether it goes to a screen mapped already, one it is no way into since that screen is reached otherwise: a
     * Home button deep down. A screen whose every other input opens one is still a menu.
     */
    boolean isLeaving();

    /** Whether it only tells: help, rules, a leaderboard. Discovery tries it last, and what it opens after the rest. */
    boolean isInfo();

    /** Why it is never done (it spends premium currency or real money); null when it may be. */
    String never();

    /** Whether it shows only at times: a Claim when loot is ready, an event's icon. */
    boolean comesAndGoes();

    /** The picture proving it locked on this account; null when it is not. */
    ImageTemplate lock();

    /** When it is there, for one that comes and goes: a method answering whether it is; null when not known. */
    BooleanSupplier when();

    /** This input, tried, opening no screen. */
    Input tried();

    /** This input, going to a screen mapped already that it is no way into. */
    Input leaves();

    /** This input, only telling. */
    Input info();

    /** This input, never done because {@code why}: it spends premium currency or real money. */
    Input never(String why);

    /** This input, showing only at times. */
    Input sometimes();

    /** This input, proved locked on this account by {@code lock} at its place. */
    Input locked(ImageTemplate lock);

    /** This input, coming and going, there while {@code condition} answers true: {@code Firestone::eventOn}. */
    Input when(BooleanSupplier condition);

    /** Whether it was tried, or needs no trying: tried, leaving, never done, or locked. One that opens a screen is too. */
    default boolean settled() {
        return isTried() || isLeaving() || never() != null || lock() != null;
    }

    /** What it acts on, in a sentence when it has no field name: {@code campaign_claim}, {@code key Q}. */
    String label();
}
