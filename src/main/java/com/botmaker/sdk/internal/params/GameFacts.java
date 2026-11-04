package com.botmaker.sdk.internal.params;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.params.GameState;
import com.botmaker.sdk.api.params.ReadOn;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.internal.config.ParamGroups;
import com.botmaker.sdk.internal.flow.FlowWalker;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.screens.ScreenWalk;
import com.botmaker.sdk.internal.trace.Say;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The running bot's {@link GameState} facts read off the game (2026-11-04): each method marked {@link ReadOn}, read on
 * the frame its screen is seen, when its value is unknown or stale. Each value read is a parameter's value like any
 * other ({@link RunParams}). A fact that cannot be read is left as it was; only a stop goes on.
 */
public final class GameFacts {

    /** Reads readouts of the screen the game shows now. */
    @FunctionalInterface
    interface Reader {
        /**
         * The readouts {@code fields} of the screen {@code constant}, read off one look, by field; a field read as
         * nothing is left out. Empty when that look does not show the screen.
         */
        Optional<Map<String, String>> read(String constant, List<String> fields);
    }

    /** One fact and where it is read. */
    private record Fact(Method parameter, Class<? extends Screen> screen, String field) {}

    private static volatile List<Fact> facts = List.of();
    private static volatile Reader reader = GameFacts::readNow;
    private static final Set<Method> MISNAMED = new HashSet<>();
    /** Set while {@link #seen} reads, so a read that sees its screen again reads nothing more. */
    private static final ThreadLocal<Boolean> READING = ThreadLocal.withInitial(() -> false);

    private GameFacts() {}

    /** Learns the facts in the {@code params} package of the bot {@code holders} belong to; read as screens are seen. */
    public static void install(Class<?>... holders) {
        use(ParamGroups.allBeside(holders), GameFacts::readNow);
    }

    /** Uses the {@link GameState} facts among {@code groups}, read by {@code reader}. Tests too. */
    static void use(List<Class<?>> groups, Reader reader) {
        List<Fact> found = new ArrayList<>();
        for (Class<?> group : groups) {
            try {
                if (!group.isAnnotationPresent(GameState.class)) continue;
                if (!group.isAnnotationPresent(Param.class)) {
                    Say.warn(group.getSimpleName() + " is marked @GameState without @Param: its facts are not kept.");
                    continue;
                }
                for (Method method : group.getDeclaredMethods()) factOf(method).ifPresent(found::add);
            } catch (RuntimeException | LinkageError unreadable) {
                Say.warn(group.getSimpleName() + "'s facts cannot be read: " + unreadable);
            }
        }
        facts = List.copyOf(found);
        GameFacts.reader = reader;
        synchronized (MISNAMED) {
            MISNAMED.clear();
        }
        ScreenMaps.onSeen(found.isEmpty() ? null : GameFacts::seen);
    }

    private static Optional<Fact> factOf(Method method) {
        String name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        try {
            ReadOn on = method.getAnnotation(ReadOn.class);
            if (on == null) return Optional.empty();
            Param.Shown shown = method.getAnnotation(Param.Shown.class);
            if (shown == null || shown.changes() != Param.Changes.DURING_RUN) {
                Say.warn(name + " is read on a screen but does not change during a run: mark it "
                        + "@Param.Shown(changes = Param.Changes.DURING_RUN). It is not read.");
                return Optional.empty();
            }
            if (!readable(method.getReturnType())) {
                Say.warn(name + " cannot be read off a screen: a " + method.getReturnType().getSimpleName()
                        + " is none of int, boolean, String or an enum.");
                return Optional.empty();
            }
            return Optional.of(new Fact(method, on.screen(), on.field()));
        } catch (RuntimeException | LinkageError unreadable) {
            Say.warn(name + "'s @ReadOn cannot be read: " + unreadable);
            return Optional.empty();
        }
    }

    private static boolean readable(Class<?> type) {
        return type == int.class || type == Integer.class || type == boolean.class || type == Boolean.class
                || type == String.class || type.isEnum();
    }

    /**
     * The game was just seen on {@code constant}: each fact read on that screen whose value is unknown or stale is read
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
        Optional<Fact> fact = facts.stream().filter(each -> each.parameter().equals(parameter)).findFirst();
        if (fact.isEmpty()) return RunParams.known(reference, method);
        ScreenGraph map = ScreenMaps.installed();
        Optional<Screen> screen = map.screens().keySet().stream().flatMap(constant -> map.instance(constant).stream())
                .filter(fact.get().screen()::isInstance).findFirst();
        if (screen.isEmpty() || !Navigate.to(screen.get())) return Optional.empty();
        Optional<String> constant = map.constantOf(screen.get());
        if (constant.isPresent() && !fresh(parameter)) readOn(constant.get(), List.of(fact.get()));
        return fresh(parameter) ? RunParams.known(reference, method) : Optional.empty();
    }

    private static boolean fresh(Method parameter) {
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
                    .flatMap(GameFacts::integer);
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
