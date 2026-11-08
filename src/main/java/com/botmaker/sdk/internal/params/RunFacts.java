package com.botmaker.sdk.internal.params;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.params.Facts;
import com.botmaker.sdk.api.params.GameState;
import com.botmaker.sdk.api.params.ReadOn;
import com.botmaker.sdk.api.params.StaleAfter;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Readout;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.internal.config.ParamGroups;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.trace.Say;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

/**
 * The running bot's {@link Facts} read off the app (2026-11-04, renamed from {@code GameFacts} on 2026-11-06: a bot
 * need not be a game): each fact a screen's readout names, {@code Readout.into(Progress::gold)}, read on the frame
 * its screen is seen, when its value is unknown or stale. A fact still marked with the deprecated {@link ReadOn} is
 * read where that says, unless a readout names it, which wins. Each value read is a parameter's value like any other
 * ({@link RunParams}). A fact that cannot be read is left as it was; only a stop goes on.
 */
@SuppressWarnings("removal")
public final class RunFacts {

    /** Reads readouts of the screen the app shows now. */
    @FunctionalInterface
    interface Reader {
        /**
         * The readouts {@code fields} of the screen {@code constant}, read off one look, by field; a field read as
         * nothing is left out. Empty when that look does not show the screen.
         */
        Optional<Map<String, String>> read(String constant, List<String> fields);
    }

    /** One fact, where it is read, and how long a reading holds. */
    private record Fact(Method parameter, Class<? extends Screen> screen, String field, Duration holds) {}

    private static volatile List<Fact> facts = List.of();
    private static volatile Reader reader = RunFacts::readNow;
    private static final Set<Method> MISNAMED = new HashSet<>();
    /** Set while {@link #seen} reads, so a read that sees its screen again reads nothing more. */
    private static final ThreadLocal<Boolean> READING = ThreadLocal.withInitial(() -> false);

    private RunFacts() {}

    /**
     * Learns the facts in the {@code params} package of the bot {@code holders} belong to, and the readouts of the
     * installed map that feed them; read as screens are seen.
     */
    public static void install(Class<?>... holders) {
        use(ParamGroups.allBeside(holders), RunFacts::readNow);
    }

    /** Uses the facts among {@code groups}, fed by the installed map's readouts and read by {@code reader}. Tests too. */
    static void use(List<Class<?>> groups, Reader reader) {
        List<Fact> found = new ArrayList<>(fedByReadouts(Set.copyOf(groups)));
        Set<Method> fed = new HashSet<>();
        found.forEach(fact -> fed.add(fact.parameter()));
        for (Class<?> group : groups) {
            try {
                if (!isFactsGroup(group)) continue;
                if (!group.isAnnotationPresent(Param.class)) {
                    Say.warn(group.getSimpleName() + " is marked @Facts without @Param: its facts are not kept.");
                    continue;
                }
                for (Method method : group.getDeclaredMethods()) {
                    if (!fed.contains(method)) readOn(method).ifPresent(found::add);
                }
            } catch (RuntimeException | LinkageError unreadable) {
                Say.warn(group.getSimpleName() + "'s facts cannot be read: " + unreadable);
            }
        }
        facts = List.copyOf(found);
        RunFacts.reader = reader;
        synchronized (MISNAMED) {
            MISNAMED.clear();
        }
        ScreenMaps.onSeen(found.isEmpty() ? null : RunFacts::seen);
    }

    /** Whether {@code group} is a group of facts: marked {@link Facts}, or the deprecated {@link GameState}. */
    static boolean isFactsGroup(Class<?> group) {
        return group.isAnnotationPresent(Facts.class) || group.isAnnotationPresent(GameState.class);
    }

    /** How long a reading of {@code parameter} holds, by the readouts that feed it (the shortest); empty when none. */
    static OptionalLong holdsMs(Method parameter) {
        return facts.stream().filter(fact -> fact.parameter().equals(parameter))
                .mapToLong(fact -> fact.holds().toMillis()).min();
    }

    /** The facts of {@code groups} the installed map's readouts are read into, each on the screen showing its readout. */
    private static List<Fact> fedByReadouts(Set<Class<?>> groups) {
        ScreenGraph map = ScreenMaps.installed();
        if (map == null) return List.of();
        List<Fact> found = new ArrayList<>();
        for (Map.Entry<String, MapScreen> each : map.screens().entrySet()) {
            Optional<Screen> screen = map.instance(each.getKey());
            if (screen.isEmpty()) continue;
            each.getValue().readouts().forEach((field, readout) -> {
                if (readout.fact() != null) fedBy(screen.get(), field, readout, groups).ifPresent(fact -> {
                    if (found.stream().noneMatch(other -> other.parameter().equals(fact.parameter())
                            && other.screen() == fact.screen())) {
                        found.add(fact);
                    } else {
                        Say.warn(screen.get().getClass().getSimpleName() + "." + field + " is read into "
                                + fact.parameter().getName() + ", which another readout of that screen feeds. "
                                + "It is not read.");
                    }
                });
            });
        }
        return found;
    }

    private static Optional<Fact> fedBy(Screen screen, String field, Readout readout, Set<Class<?>> groups) {
        String where = screen.getClass().getSimpleName() + "." + field;
        try {
            Method parameter = RunParams.parameterOf(readout.fact());
            Class<?> group = parameter.getDeclaringClass();
            if (!groups.contains(group) || !isFactsGroup(group) || !group.isAnnotationPresent(Param.class)) {
                Say.warn(where + " is read into " + group.getSimpleName() + "." + parameter.getName()
                        + ", which is no method of a @Param @Facts group of the bot's params. It is not read.");
                return Optional.empty();
            }
            return readable(parameter)
                    ? Optional.of(new Fact(parameter, screen.getClass(), field, holds(readout.staleAfter(), parameter)))
                    : Optional.empty();
        } catch (RuntimeException | LinkageError unreadable) {
            Say.warn(where + " is read into something that cannot be read: " + unreadable + " It is not read.");
            return Optional.empty();
        }
    }

    /** How long a reading holds: what its readout {@code said}, else {@code parameter}'s deprecated {@link StaleAfter}, else an hour. */
    private static Duration holds(Duration said, Method parameter) {
        if (said != null) return said;
        StaleAfter after = parameter.getAnnotation(StaleAfter.class);
        return after != null ? Duration.ofMinutes(after.minutes()) : Readout.READING_HOLDS;
    }

    /** The fact {@code method} is, read where its deprecated {@link ReadOn} says; empty when it has none. */
    private static Optional<Fact> readOn(Method method) {
        try {
            ReadOn on = method.getAnnotation(ReadOn.class);
            if (on == null || !readable(method)) return Optional.empty();
            return Optional.of(new Fact(method, on.screen(), on.field(), holds(null, method)));
        } catch (RuntimeException | LinkageError unreadable) {
            Say.warn(method.getDeclaringClass().getSimpleName() + "." + method.getName()
                    + "'s @ReadOn cannot be read: " + unreadable);
            return Optional.empty();
        }
    }

    /** Whether {@code method} can be read off a screen, warned of when not. */
    private static boolean readable(Method method) {
        String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        Param.Shown shown = method.getAnnotation(Param.Shown.class);
        if (shown == null || shown.changes() != Param.Changes.DURING_RUN) {
            Say.warn(name + " is read on a screen but does not change during a run: mark it "
                    + "@Param.Shown(changes = Param.Changes.DURING_RUN). It is not read.");
            return false;
        }
        Class<?> type = method.getReturnType();
        if (type == int.class || type == Integer.class || type == boolean.class || type == Boolean.class
                || type == String.class || type.isEnum()) return true;
        Say.warn(name + " cannot be read off a screen: a " + type.getSimpleName()
                + " is none of int, boolean, String or an enum.");
        return false;
    }

    /**
     * The app was just seen on {@code constant}: each fact read on that screen whose value is unknown or stale is read
     * now, all off one look of the screen as it shows.
     */
    public static void seen(String constant) {
        if (READING.get()) return;
        Optional<Screen> screen = ScreenMaps.installed().instance(constant);
        if (screen.isEmpty()) return;
        List<Fact> due = facts.stream()
                .filter(fact -> fact.screen().isInstance(screen.get()) && !fresh(fact.parameter())).toList();
        if (!due.isEmpty()) readOn(constant, due);
    }

    /** Goes to the fact's screen and answers it there, read unless arriving read it; see {@code Params.refresh}. */
    public static <G, T> Optional<T> refresh(Serializable reference, Function<G, T> method) {
        Method parameter = RunParams.parameterOf(reference);
        List<Fact> feeds = facts.stream().filter(each -> each.parameter().equals(parameter)).toList();
        if (feeds.isEmpty()) return RunParams.known(reference, method);
        ScreenGraph map = ScreenMaps.installed();
        // A fact several screens feed is answered by the first the bot can reach.
        for (Fact fact : feeds) {
            Optional<Screen> screen = map.screens().keySet().stream().flatMap(constant -> map.instance(constant).stream())
                    .filter(fact.screen()::isInstance).findFirst();
            if (screen.isEmpty() || !Navigate.to(screen.get())) continue;
            Optional<String> constant = map.constantOf(screen.get());
            if (constant.isPresent() && !fresh(parameter)) readOn(constant.get(), List.of(fact));
            if (fresh(parameter)) return RunParams.known(reference, method);
        }
        return Optional.empty();
    }

    /** The screens of the installed map whose readouts feed {@code parameter}, in the map's order; empty when none does. */
    public static List<String> screensOf(Method parameter) {
        ScreenGraph map = ScreenMaps.installed();
        List<Fact> feeds = facts.stream().filter(each -> each.parameter().equals(parameter)).toList();
        return map.screens().keySet().stream()
                .filter(constant -> map.instance(constant)
                        .filter(screen -> feeds.stream().anyMatch(fact -> fact.screen().isInstance(screen)))
                        .isPresent())
                .toList();
    }

    /**
     * Reads again each of {@code parameters} that the screen {@code constant} feeds, fresh or not, off one look of it:
     * after a press, what it changed.
     */
    public static void readAgain(String constant, Set<Method> parameters) {
        Optional<Screen> screen = ScreenMaps.installed().instance(constant);
        if (screen.isEmpty()) return;
        List<Fact> due = facts.stream()
                .filter(fact -> parameters.contains(fact.parameter()) && fact.screen().isInstance(screen.get())).toList();
        if (!due.isEmpty()) readOn(constant, due);
    }

    /**
     * Reads again every fact the screen {@code constant} feeds that was not read at or after {@code since}, fresh or
     * not, off one look of it: a plan's idle looking at the game.
     */
    public static void readEvery(String constant, Instant since) {
        Optional<Screen> screen = ScreenMaps.installed().instance(constant);
        if (screen.isEmpty()) return;
        List<Fact> due = facts.stream().filter(fact -> fact.screen().isInstance(screen.get())
                && readAt(fact.parameter()).filter(at -> !at.isBefore(since)).isEmpty()).toList();
        if (!due.isEmpty()) readOn(constant, due);
    }

    /** When {@code parameter}'s reading was taken; empty when it holds none. */
    public static Optional<Instant> readAt(Method parameter) {
        OptionalLong at = RunParams.keptAt(parameter);
        return at.isPresent() ? Optional.of(Instant.ofEpochMilli(at.getAsLong())) : Optional.empty();
    }

    /** Whether {@code parameter} holds a reading that is not stale. */
    public static boolean fresh(Method parameter) {
        var at = RunParams.keptAt(parameter);
        return at.isPresent() && !RunParams.stale(parameter, at.getAsLong());
    }

    /** Reads {@code due}, all of the screen {@code constant}, off one look, and keeps what reads. */
    private static void readOn(String constant, List<Fact> due) {
        READING.set(true);
        try {
            Set<String> present = ScreenMaps.installed().screen(constant)
                    .map(screen -> screen.readouts().keySet()).orElse(Set.of());
            List<Fact> named = due.stream().filter(fact -> named(fact, constant, present)).toList();
            if (named.isEmpty()) return;
            Optional<Map<String, String>> read = reader.read(constant, named.stream().map(Fact::field).distinct().toList());
            if (read.isEmpty()) return;
            for (Fact fact : named) {
                parse(fact.parameter().getReturnType(), Optional.ofNullable(read.get().get(fact.field())))
                        .ifPresent(value -> RunParams.setParameter(fact.parameter(), value));
            }
        } catch (RuntimeException unreadable) {
            for (Throwable at = unreadable; at != null; at = at.getCause() == at ? null : at.getCause()) {
                if (FlowWalker.isStop(at)) throw (RuntimeException) at;
            }
            Debug.log("The facts on " + constant + " were not read: " + unreadable);
        } finally {
            READING.set(false);
        }
    }

    /** Whether the screen has the fact's readout: a name it lacks is no read at all, never a boolean's "not shown". */
    private static boolean named(Fact fact, String constant, Set<String> present) {
        if (present.contains(fact.field())) return true;
        synchronized (MISNAMED) {
            if (MISNAMED.add(fact.parameter())) {
                Say.warn(fact.parameter().getName() + " is read from the readout \"" + fact.field() + "\", but "
                        + constant + " has none of that name. It is not read.");
            }
        }
        return false;
    }

    /**
     * The text a readout showed on its screen, as {@code type}; empty when it reads as none. A boolean is whether
     * anything was read: the look showed the screen, so nothing read is its element not there.
     */
    static Optional<Object> parse(Class<?> type, Optional<String> text) {
        if (type == boolean.class || type == Boolean.class) return Optional.of(text.isPresent());
        if (type == int.class || type == Integer.class) {
            return text.map(shown -> shown.replaceAll("\\D", "")).filter(digits -> !digits.isEmpty())
                    .flatMap(RunFacts::integer);
        }
        if (type == String.class) return text.map(String::strip).filter(shown -> !shown.isEmpty()).map(Object.class::cast);
        return text.flatMap(shown -> constant(type, shown));
    }

    private static Optional<Object> integer(String digits) {
        try {
            return Optional.of(Integer.parseInt(digits));
        } catch (NumberFormatException tooLong) {
            return Optional.empty();
        }
    }

    /** {@code type}'s constant {@code text} names, read loosely: {@code "Fire mage"} is {@code FIRE_MAGE}. */
    private static Optional<Object> constant(Class<?> type, String text) {
        String wanted = text.strip().replaceAll("[^\\p{Alnum}]+", "_").replaceAll("^_|_$", "");
        for (Object each : type.getEnumConstants()) {
            if (((Enum<?>) each).name().equalsIgnoreCase(wanted)) return Optional.of(each);
        }
        return Optional.empty();
    }

    private static Optional<Map<String, String>> readNow(String constant, List<String> fields) {
        return new ScreenWalk(ScreenMaps.installed(), new RunReach(), ScreenWalk.STEP_MS, null).readAll(constant, fields);
    }
}
