package com.botmaker.sdk.internal.params;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.plugin.host.ClasspathGrammar;
import com.botmaker.plugin.host.grammar.JavaValue;
import com.botmaker.plugin.host.grammar.ParamValues;
import com.botmaker.plugin.host.grammar.ParamsFitter;
import com.botmaker.plugin.host.grammar.ValueGrammar;
import com.botmaker.sdk.api.params.Assumed;
import com.botmaker.sdk.api.params.GameState;
import com.botmaker.sdk.api.params.ReadOn;
import com.botmaker.sdk.api.params.StaleAfter;
import com.botmaker.sdk.internal.trace.Say;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * This run's during-a-run parameters (2026-11-04, plan {@code 2026-11-04-private-values.md} phases 6b and 7): what the
 * run set, held so it sees it at once, and each value written with its time ({@code @Param.Kept}) into the parameter's
 * group under the bot's {@code params/} folder, so the next compile reads both.
 *
 * <p>The folder is found from the run's working directory, which is the bot's. A write that cannot happen leaves the
 * value in memory, with one warning per parameter.
 */
public final class RunParams {

    private static final String SOURCES = "src/main/java";
    private static final String PARAMS_SEGMENT = "." + ParamsFitter.PARAMS + ".";
    /** Where a fit copies what it changes: the place Studio's own fit uses. */
    private static final String BACKUPS = ".botmaker/values-removed";
    /** The same value written again within this long is not written: only its time would change. */
    private static final long REWRITE_AFTER_MS = 60_000;
    /** How long a value read off a screen holds when its fact says nothing. */
    private static final long READ_HOLDS_MS = 3_600_000;

    private static final Map<Class<?>, Method> REFERENCES = new ConcurrentHashMap<>();
    private static final Map<Method, Held> SET = new HashMap<>();
    /** What this run forgot: what it was compiled with no longer counts. */
    private static final Set<Method> FORGOTTEN = new HashSet<>();
    private static final Set<Method> WARNED = new HashSet<>();
    private static final Object WRITING = new Object();

    private static Path project = Path.of(System.getProperty("user.dir", "."));
    private static ValueGrammar grammar;
    private static boolean fitted;
    private static LongSupplier clock = System::currentTimeMillis;

    /** A value this run set, and when. */
    private record Held(Object value, long at) {}

    /** A group's file: {@code relative} under the bot's {@code params} folder. */
    private record GroupFile(Path params, Path relative) {

        Path file() {
            return params.resolve(relative);
        }
    }

    private RunParams() {}

    /** Sets the parameter {@code reference} names; see {@code Params.set}. */
    public static void set(Serializable reference, Object value) {
        setParameter(parameterOf(reference), value);
    }

    /** Sets {@code parameter}, a method of a {@code @Param} class. */
    static void setParameter(Method parameter, Object value) {
        requireDuringRun(parameter, "set");
        // The generics let a String through to any parameter (T widens to Object); the grammar would write some such
        // text as Java, and the next read of the value would not be of the parameter's type.
        Class<?> type = MethodType.methodType(parameter.getReturnType()).wrap().returnType();
        if (value == null ? parameter.getReturnType().isPrimitive() : !type.isInstance(value)) {
            throw new IllegalArgumentException(name(parameter) + " holds a " + type.getSimpleName() + ", not "
                    + (value == null ? "null" : "a " + value.getClass().getSimpleName()) + ".");
        }
        synchronized (RunParams.class) {
            long now = clock.getAsLong();
            Held held = SET.get(parameter);
            if (held != null && Objects.equals(held.value(), value) && now - held.at() < REWRITE_AFTER_MS) return;
            SET.put(parameter, new Held(value, now));
            FORGOTTEN.remove(parameter);
        }
        // Apart from the values' lock: a read on another thread never waits for the disk. Whichever write comes
        // last writes the newest value.
        synchronized (WRITING) {
            try {
                Held newest = newest(parameter);
                if (newest != null) write(parameter, newest);
            } catch (IOException | RuntimeException | LinkageError unwritable) {
                warnOnce(parameter, " stays in memory for this run: " + unwritable.getMessage());
            }
        }
    }

    private static synchronized Held newest(Method parameter) {
        return SET.get(parameter);
    }

    /** What this run set for the parameter {@code reference} names, else what {@code method} answers on its group. */
    public static <G, T> T get(Serializable reference, Function<G, T> method) {
        Method parameter = parameterOf(reference);
        synchronized (RunParams.class) {
            Held held = SET.get(parameter);
            if (held != null) {
                @SuppressWarnings("unchecked")
                T value = (T) held.value();
                return value;
            }
        }
        try {
            Constructor<?> made = parameter.getDeclaringClass().getDeclaredConstructor();
            made.setAccessible(true);
            @SuppressWarnings("unchecked")
            G group = (G) made.newInstance();
            return method.apply(group);
        } catch (ReflectiveOperationException unmade) {
            throw new IllegalStateException(name(parameter) + " cannot be read: " + unmade.getMessage(), unmade);
        }
    }

    /** The value of the parameter {@code reference} names, if it is known; see {@code Params.known}. */
    public static <G, T> Optional<T> known(Serializable reference, Function<G, T> method) {
        Method parameter = parameterOf(reference);
        if (!parameter.getDeclaringClass().isAnnotationPresent(GameState.class)) {
            return Optional.ofNullable(get(reference, method));
        }
        OptionalLong at = keptAt(parameter);
        boolean answers = at.isPresent() ? !stale(parameter, at.getAsLong()) : assumed(parameter);
        return answers ? Optional.ofNullable(get(reference, method)) : Optional.empty();
    }

    /** An assumed value answers until read, but a forgotten one compiled from a read is that read, not the assumption. */
    private static synchronized boolean assumed(Method parameter) {
        return parameter.isAnnotationPresent(Assumed.class)
                && !(FORGOTTEN.contains(parameter) && parameter.isAnnotationPresent(Param.Kept.class));
    }

    /**
     * When {@code parameter}'s value was written: by this run, else as compiled; empty when never, forgotten, or the
     * compiled time does not read.
     */
    static synchronized OptionalLong keptAt(Method parameter) {
        Held held = SET.get(parameter);
        if (held != null) return OptionalLong.of(held.at());
        Param.Kept kept = parameter.getAnnotation(Param.Kept.class);
        if (kept == null || FORGOTTEN.contains(parameter)) return OptionalLong.empty();
        try {
            return OptionalLong.of(Instant.parse(kept.at()).toEpochMilli());
        } catch (DateTimeParseException unreadable) {
            return OptionalLong.empty();
        }
    }

    /** Whether a value written at {@code at} is past what {@code parameter} says it holds. */
    static synchronized boolean stale(Method parameter, long at) {
        StaleAfter after = parameter.getAnnotation(StaleAfter.class);
        if (after == null && !parameter.isAnnotationPresent(ReadOn.class)) return false;
        return clock.getAsLong() - at > (after != null ? after.minutes() * 60_000L : READ_HOLDS_MS);
    }

    /**
     * Sets the parameter {@code reference} names back to its starting value, without a time; see {@code Params.forget}.
     * The source a run was compiled from cannot change under it: within this run the fact stays unknown, while
     * {@code get} answers the compiled body until the next run.
     */
    public static void forget(Serializable reference) {
        Method parameter = parameterOf(reference);
        requireDuringRun(parameter, "forget");
        synchronized (RunParams.class) {
            SET.remove(parameter);
            FORGOTTEN.add(parameter);
        }
        synchronized (WRITING) {
            try {
                GroupFile group = groupFile(parameter.getDeclaringClass());
                String source = Files.readString(group.file());
                String starting = Files.readString(project.resolve(ParamsFitter.STARTING).resolve(group.relative()));
                writeAtomically(group.file(), source, ParamsFitter.withValue(source, starting, parameter.getName()));
            } catch (IOException | RuntimeException | LinkageError unwritable) {
                warnOnce(parameter, " stays written for the next run: " + unwritable.getMessage());
            }
        }
    }

    private static void requireDuringRun(Method parameter, String doing) {
        Param.Shown shown = parameter.getAnnotation(Param.Shown.class);
        if (shown == null || shown.changes() != Param.Changes.DURING_RUN) {
            throw new IllegalArgumentException(name(parameter) + " changes outside a run, so a run cannot " + doing
                    + " it: mark it @Param.Shown(changes = Param.Changes.DURING_RUN).");
        }
    }

    /** The method {@code reference} names, which must be a parameter of a {@code @Param} class. */
    static Method parameterOf(Serializable reference) {
        Objects.requireNonNull(reference, "parameter");
        return REFERENCES.computeIfAbsent(reference.getClass(), type -> resolve(reference));
    }

    private static Method resolve(Serializable reference) {
        try {
            Method replace = reference.getClass().getDeclaredMethod("writeReplace");
            replace.setAccessible(true);
            SerializedLambda form = (SerializedLambda) replace.invoke(reference);
            String owner = form.getImplClass().replace('/', '.');
            Class<?> group = Class.forName(owner, false, reference.getClass().getClassLoader());
            Method method = group.getDeclaredMethod(form.getImplMethodName());
            if (!group.isAnnotationPresent(Param.class)) {
                throw new IllegalArgumentException(owner + "." + form.getImplMethodName() + " is not a parameter: "
                        + owner + " is not marked @Param. Name a parameter by its method reference.");
            }
            return method;
        } catch (ReflectiveOperationException | ClassCastException notAReference) {
            throw new IllegalArgumentException("Name a parameter by its method reference, Progress::level.",
                    notAReference);
        }
    }

    private static void write(Method parameter, Held value) throws IOException {
        Class<?> type = parameter.getDeclaringClass();
        GroupFile group = groupFile(type);
        Path file = group.file();
        if (!fitted) {
            fitted = true;
            ParamsFitter.onDisk(group.params(), project.resolve(ParamsFitter.STARTING), project.resolve(BACKUPS));
        }
        if (!Files.isRegularFile(file)) throw new IOException(file + " does not exist");
        JavaValue spelled = grammar().spellData(parameter.getGenericReturnType(), value.value())
                .orElseThrow(() -> new IOException("no plugin can write a " + parameter.getReturnType().getSimpleName()));
        String source = Files.readString(file);
        String written = ParamValues.withReturned(source, type.getSimpleName(), parameter.getName(), spelled,
                        Instant.ofEpochMilli(value.at()))
                .orElseThrow(() -> new IOException("its method in " + file + " is not one return"));
        writeAtomically(file, source, written);
    }

    /** Where {@code group} sits under the bot's {@code params} folder. */
    private static GroupFile groupFile(Class<?> group) throws IOException {
        String binary = group.getName();
        int at = binary.lastIndexOf(PARAMS_SEGMENT);
        if (at < 0 || group.isMemberClass()) {
            throw new IOException(binary + " is not a top-level class of a params package");
        }
        Path params = project.resolve(SOURCES).resolve(binary.substring(0, at).replace('.', '/'))
                .resolve(ParamsFitter.PARAMS);
        return new GroupFile(params, Path.of(binary.substring(at + PARAMS_SEGMENT.length()).replace('.', '/') + ".java"));
    }

    private static void writeAtomically(Path file, String source, String written) throws IOException {
        if (written.equals(source)) return;
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, written);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failed) {
            Files.deleteIfExists(temporary);
            throw failed;
        }
    }

    private static ValueGrammar grammar() {
        if (grammar == null) {
            grammar = ClasspathGrammar.of(List.of(System.getProperty("java.class.path", "").split(File.pathSeparator)),
                    RunParams.class.getClassLoader());
        }
        return grammar;
    }

    private static void warnOnce(Method parameter, String why) {
        if (WARNED.add(parameter)) Say.warn(name(parameter) + why);
    }

    private static String name(Method parameter) {
        return parameter.getDeclaringClass().getSimpleName() + "." + parameter.getName();
    }

    /** Starts over against the bot at {@code folder}: what a new run is, for a test in one JVM. */
    static synchronized void startOver(Path folder) {
        project = folder;
        fitted = false;
        clock = System::currentTimeMillis;
        SET.clear();
        FORGOTTEN.clear();
        WARNED.clear();
    }

    /** Times this run by {@code now}, for a test. */
    static synchronized void clock(LongSupplier now) {
        clock = now;
    }
}
