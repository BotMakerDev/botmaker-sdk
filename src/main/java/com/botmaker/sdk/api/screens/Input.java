package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.function.BooleanSupplier;

/**
 * One way to act on a {@link Screen}, a field of it: a {@link Button} clicked, a {@link Hotkey} pressed, or a
 * {@link Strip} of buttons that scrolls or shifts. The traits here are each kind's.
 *
 * <p>With no {@link #did}, no {@link #never} and no {@link #lock} it is untried: Discovery tries every input of a
 * screen before the screen counts as finished. An input that opened a screen is tried too, and says so by the screen
 * it opened ({@link Setup#from}). An input that {@link #comesAndGoes comes and goes} never holds a screen back while
 * it is not on it; {@link #when} says when it is there, as a method of the bot or of a plugin
 * ({@code Firestone::eventOn}), and a run asks it before walking there (the user's call, 2026-10-15).
 *
 * <p>A strip's traits are its members' defaults: a strip that {@link #comesAndGoes comes and goes} is one whose
 * members each may be absent (the user's call, 2026-10-16).
 */
public sealed interface Input permits Button, Hotkey, Strip {

    /** What it did when tried, opening nothing; null when untried or when it opened a screen. */
    Did did();

    /** What it is for; null when not named yet. */
    Purpose purpose();

    /** Why it is never done (it spends premium currency or real money); null when it may be. */
    String never();

    /** Whether it shows only at times: a Claim when loot is ready, an event's icon. */
    boolean comesAndGoes();

    /** The picture proving it locked on this account; null when it is not. */
    ImageTemplate lock();

    /** When it is there, for one that comes and goes: a method answering whether it is; null when not known. */
    BooleanSupplier when();

    /** This input, tried, having done {@code did}. */
    Input did(Did did);

    /** This input, named for {@code purpose}. */
    Input purpose(Purpose purpose);

    /** This input, never done because {@code why}: it spends premium currency or real money. */
    Input never(String why);

    /** This input, showing only at times. */
    Input sometimes();

    /** This input, proved locked on this account by {@code lock} at its place. */
    Input locked(ImageTemplate lock);

    /** This input, coming and going, there while {@code condition} answers true: {@code Firestone::eventOn}. */
    Input when(BooleanSupplier condition);

    /** Whether it was tried, or needs no trying: done, never done, or locked. One that opened a screen is tried too. */
    default boolean settled() {
        return did() != null || never() != null || lock() != null;
    }

    /** What it acts on, in a sentence when it has no field name: {@code campaign_claim}, {@code key Q}. */
    String label();
}
