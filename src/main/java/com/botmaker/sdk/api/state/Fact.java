package com.botmaker.sdk.api.state;

import com.botmaker.sdk.api.screens.Answer;
import com.botmaker.sdk.api.screens.Condition;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One thing the game shows of this account, kept between runs: the hero's level, the class picked, whether the
 * tutorial is done. A constant of the bot's {@code Facts} class beside {@code Screens} (spec {@code
 * docs/superpowers/specs/2026-11-03-screen-logic-and-game-state-design.md}):
 *
 * <pre>{@code
 * public static final Fact<Integer> LEVEL = Fact.number(Screens.Home.class, home -> home.level)
 *         .staleAfter(Duration.ofHours(6));
 * public static final Fact<Boolean> TUTORIAL_DONE = Fact.set();
 * }</pre>
 *
 * <p>The bot's Java says what the fact is and where it is read; the value is this machine's, kept in the SDK's
 * state folder of the bot by {@link GameState}. A fact is read whenever a run is on its screen and its value is
 * stale, never by going there for it ({@link GameState#refresh} does that for a flow that needs it). Its
 * screen is named by class, not by its constant, so a screen's {@code .when(Facts.LEVEL.atLeast(5))} and this
 * fact may each name the other whatever their order.
 *
 * <p>A fact never read, or read longer ago than {@link #staleAfter}, is unknown, and every condition on it says
 * {@link Answer#UNKNOWN}: a way it guards stays open.
 *
 * @param <T> what it holds: {@code Integer}, {@code Boolean}, {@code String} or a bot enum's constant
 */
public final class Fact<T> {

    /** How long a reading stays fresh unless {@link #staleAfter} says otherwise. */
    public static final Duration FRESH = Duration.ofHours(1);

    /** What a fact holds and how its readout's text becomes that. */
    public enum Kind {
        /** The words read. */
        TEXT("text", "Text"),
        /** The digits read, every other mark passed over. */
        NUMBER("number", "Number"),
        /** Whether its element is in the screen's tree. */
        SHOWN("shown", "Shown"),
        /** A yes or no a step sets; read off no screen. */
        FLAG("flag", "Flag"),
        /** The words read, as a constant of a bot enum. */
        CHOICE("choice", "Choice"),
        /** A kind this version does not know: never read, never kept. */
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** The kind of {@code id}; {@link #UNKNOWN} for one this version does not know. */
        public static Kind fromId(String id) {
            return Arrays.stream(values()).filter(k -> k.id.equals(id)).findFirst().orElse(UNKNOWN);
        }
    }

    private final Kind kind;
    private final Class<T> type;
    private final Class<? extends Screen> screen;
    private final Function<Screen, Readout> readout;
    private final Duration staleAfter;
    private final T assumed;
    private final boolean isAccount;

    private Fact(Kind kind, Class<T> type, Class<? extends Screen> screen, Function<Screen, Readout> readout,
                 Duration staleAfter, T assumed, boolean isAccount) {
        this.kind = kind;
        this.type = type;
        this.screen = screen;
        this.readout = readout;
        this.staleAfter = staleAfter;
        this.assumed = assumed;
        this.isAccount = isAccount;
    }

    /** The words {@code readout} of a {@code screen} shows: {@code Fact.read(Profile.class, p -> p.heroName)}. */
    public static <S extends Screen> Fact<String> read(Class<S> screen, Function<S, Readout> readout) {
        return new Fact<>(Kind.TEXT, String.class, screen, of(screen, readout), FRESH, null, false);
    }

    /** The number {@code readout} of a {@code screen} shows: {@code 1,250} is 1250. */
    public static <S extends Screen> Fact<Integer> number(Class<S> screen, Function<S, Readout> readout) {
        return new Fact<>(Kind.NUMBER, Integer.class, screen, of(screen, readout), FRESH, null, false);
    }

    /** Whether {@code readout}'s element of a {@code screen} is in the tree while the game shows the screen. */
    public static <S extends Screen> Fact<Boolean> shown(Class<S> screen, Function<S, Readout> readout) {
        return new Fact<>(Kind.SHOWN, Boolean.class, screen, of(screen, readout), FRESH, null, false);
    }

    /** A yes or no a step sets, {@code GameState.set(TUTORIAL_DONE, true)}; read off no screen, never stale. */
    public static Fact<Boolean> set() {
        return new Fact<>(Kind.FLAG, Boolean.class, null, null, null, null, false);
    }

    private static <S extends Screen> Function<Screen, Readout> of(Class<S> screen, Function<S, Readout> readout) {
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(readout, "readout");
        return s -> readout.apply(screen.cast(s));
    }

    /**
     * This text fact as a constant of {@code choices}, read by name: {@code "Warrior"} is {@code WARRIOR}. A text
     * no constant is named by is not kept.
     */
    public <E extends Enum<E>> Fact<E> as(Class<E> choices) {
        if (kind != Kind.TEXT) throw new IllegalStateException("Only a text fact is read as an enum's constant");
        return new Fact<>(Kind.CHOICE, Objects.requireNonNull(choices, "choices"), screen, readout, staleAfter, null,
                isAccount);
    }

    /** This fact, unknown once its reading is older than {@code fresh}; a fact a step sets is never stale. */
    public Fact<T> staleAfter(Duration fresh) {
        if (fresh == null || fresh.isNegative() || fresh.isZero()) throw new IllegalArgumentException("fresh");
        return kind == Kind.FLAG ? this : new Fact<>(kind, type, screen, readout, fresh, assumed, isAccount);
    }

    /** This fact, answering {@code value} while no reading is kept: the author's guess, which a reading replaces. */
    public Fact<T> assume(T value) {
        return new Fact<>(kind, type, screen, readout, staleAfter, value, isAccount);
    }

    /**
     * This text fact as the game account the run plays: the other facts are kept apart for each value it reads, so
     * two accounts on one emulator never mix. One per bot.
     */
    public Fact<T> account() {
        if (kind != Kind.TEXT) throw new IllegalStateException("Only a text fact names the account");
        return new Fact<>(kind, type, screen, readout, staleAfter, assumed, true);
    }

    /** Yes when the number is {@code min} or more; unknown while the fact is. */
    public Condition atLeast(long min) {
        return () -> compared(v -> v.longValue() >= min);
    }

    /** Yes when the number is {@code max} or less; unknown while the fact is. */
    public Condition atMost(long max) {
        return () -> compared(v -> v.longValue() <= max);
    }

    /** Yes when the fact holds {@code value}; unknown while the fact is. */
    public Condition is(T value) {
        return () -> GameState.get(this).map(v -> Answer.of(v.equals(value))).orElse(Answer.UNKNOWN);
    }

    /** Yes when the fact holds true; unknown while the fact is. */
    public Condition isTrue() {
        return () -> GameState.get(this).map(v -> Answer.of(Boolean.TRUE.equals(v))).orElse(Answer.UNKNOWN);
    }

    private Answer compared(Function<Number, Boolean> test) {
        Optional<T> value = GameState.get(this);
        if (value.isEmpty() || !(value.get() instanceof Number n)) return Answer.UNKNOWN;
        return Answer.of(test.apply(n));
    }

    /** What it holds and how it is read. */
    public Kind kind() {
        return kind;
    }

    /** The class of what it holds. */
    public Class<T> type() {
        return type;
    }

    /** The class of the screen it is read on; empty for a fact a step sets. */
    public Optional<Class<? extends Screen>> screen() {
        return Optional.ofNullable(screen);
    }

    /** The readout it is read from on {@code shown}, an instance of its {@link #screen}; empty for a step's fact. */
    public Optional<Readout> readoutOf(Screen shown) {
        if (readout == null || shown == null || !screen.isInstance(shown)) return Optional.empty();
        return Optional.ofNullable(readout.apply(shown));
    }

    /** How long a reading stays fresh; empty when it never goes stale. */
    public Optional<Duration> staleAfter() {
        return Optional.ofNullable(staleAfter);
    }

    /** The author's starting value, {@link #assume}; empty when there is none. */
    public Optional<T> assumed() {
        return Optional.ofNullable(assumed);
    }

    /** Whether it names the game account, {@link #account}. */
    public boolean isAccount() {
        return isAccount;
    }
}
