package com.botmaker.sdk.internal.params;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.plugin.host.ClasspathGrammar;
import com.botmaker.plugin.host.grammar.JavaValue;
import com.botmaker.plugin.host.grammar.ParamValues;
import com.botmaker.plugin.host.grammar.ParamsFitter;
import com.botmaker.plugin.host.grammar.ValueGrammar;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * This run's during-a-run parameters (2026-11-04, plan {@code 2026-11-04-private-values.md} phase 6b): what the run
 * set, held so it sees it at once, and each value written into the parameter's group under the bot's {@code params/}
 * folder, so the next compile reads it.
 *
 * <p>The folder is found from the run's working directory, which is the bot's. A write that cannot happen leaves the
 * value in memory, with one warning per parameter.
 */
public final class RunParams {

    private static final String SOURCES = "src/main/java";
    private static final String PARAMS_SEGMENT = "." + ParamsFitter.PARAMS + ".";
    /** Where a fit copies what it changes: the place Studio's own fit uses. */
    private static final String BACKUPS = ".botmaker/values-removed";

    private static final Map<Class<?>, Method> REFERENCES = new ConcurrentHashMap<>();
    private static final Map<Method, Object> SET = new HashMap<>();
    private static final Set<Method> WARNED = new HashSet<>();
    private static final Object WRITING = new Object();

    private static Path project = Path.of(System.getProperty("user.dir", "."));
    private static ValueGrammar grammar;
    private static boolean fitted;

    private RunParams() {}

    /** Sets the parameter {@code reference} names; see {@code Params.set}. */
    public static void set(Serializable reference, Object value) {
        Method parameter = parameterOf(reference);
        Param.Shown shown = parameter.getAnnotation(Param.Shown.class);
        if (shown == null || shown.changes() != Param.Changes.DURING_RUN) {
            throw new IllegalArgumentException(name(parameter) + " changes outside a run, so a run cannot set it: mark "
                    + "it @Param.Shown(changes = Param.Changes.DURING_RUN).");
        }
        // The generics let a String through to any parameter (T widens to Object); the grammar would write some such
        // text as Java, and the next read of the value would not be of the parameter's type.
        Class<?> type = MethodType.methodType(parameter.getReturnType()).wrap().returnType();
        if (value == null ? parameter.getReturnType().isPrimitive() : !type.isInstance(value)) {
            throw new IllegalArgumentException(name(parameter) + " holds a " + type.getSimpleName() + ", not "
                    + (value == null ? "null" : "a " + value.getClass().getSimpleName()) + ".");
        }
        synchronized (RunParams.class) {
            boolean same = SET.containsKey(parameter) && Objects.equals(SET.get(parameter), value);
            SET.put(parameter, value);
            if (same) return;
        }
        // Apart from the values' lock: a read on another thread never waits for the disk. Whichever write comes
        // last writes the newest value.
        synchronized (WRITING) {
            try {
                write(parameter, newest(parameter));
            } catch (IOException | RuntimeException | LinkageError unwritable) {
                if (WARNED.add(parameter)) {
                    Say.warn(name(parameter) + " stays in memory for this run: " + unwritable.getMessage());
                }
            }
        }
    }

    private static synchronized Object newest(Method parameter) {
        return SET.get(parameter);
    }

    /** What this run set for the parameter {@code reference} names, else what {@code method} answers on its group. */
    public static <G, T> T get(Serializable reference, Function<G, T> method) {
        Method parameter = parameterOf(reference);
        synchronized (RunParams.class) {
            if (SET.containsKey(parameter)) {
                @SuppressWarnings("unchecked")
                T held = (T) SET.get(parameter);
                return held;
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

    private static void write(Method parameter, Object value) throws IOException {
        Class<?> group = parameter.getDeclaringClass();
        String binary = group.getName();
        int at = binary.lastIndexOf(PARAMS_SEGMENT);
        if (at < 0 || group.isMemberClass()) {
            throw new IOException(binary + " is not a top-level class of a params package");
        }
        Path params = project.resolve(SOURCES).resolve(binary.substring(0, at).replace('.', '/'))
                .resolve(ParamsFitter.PARAMS);
        Path file = params.resolve(binary.substring(at + PARAMS_SEGMENT.length()).replace('.', '/') + ".java");
        if (!fitted) {
            fitted = true;
            ParamsFitter.onDisk(params, project.resolve(ParamsFitter.STARTING), project.resolve(BACKUPS));
        }
        if (!Files.isRegularFile(file)) throw new IOException(file + " does not exist");
        JavaValue spelled = grammar().spellData(parameter.getGenericReturnType(), value)
                .orElseThrow(() -> new IOException("no plugin can write a " + parameter.getReturnType().getSimpleName()));
        String source = Files.readString(file);
        String written = ParamValues.withReturned(source, group.getSimpleName(), parameter.getName(), spelled)
                .orElseThrow(() -> new IOException("its method in " + file + " is not one return"));
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

    private static String name(Method parameter) {
        return parameter.getDeclaringClass().getSimpleName() + "." + parameter.getName();
    }

    /** Starts over against the bot at {@code folder}: what a new run is, for a test in one JVM. */
    static synchronized void startOver(Path folder) {
        project = folder;
        fitted = false;
        SET.clear();
        WARNED.clear();
    }
}
