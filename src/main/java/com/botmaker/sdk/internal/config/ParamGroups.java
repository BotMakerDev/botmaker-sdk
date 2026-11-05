package com.botmaker.sdk.internal.config;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.SdkValue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.stream.Stream;

/**
 * What a run reads its values from: each class {@code Bot.runFlow} is handed — {@code Sdk.class}, the bot's data —
 * and the parameter groups beside it, which {@code main} never names (2026-11-04, the user's call).
 *
 * <p>A plugin's groups mirror its holder's place: the holder {@code com.mybot.plugins.g.a.Sdk} has its parameters in
 * the package {@code com.mybot.params.g.a}, each class there marked {@code @Param} one group whose methods are this
 * computer's values ({@code Running}, {@code Game}; 2026-11-05, a plugin declares several). The groups are listed in
 * the holder's own class loader, by name, and made with their no-argument constructors; a bot without one has no
 * parameters, and each reads as nothing.
 */
public final class ParamGroups {

    /** The package segment a bot's plugin files sit under. */
    private static final String PLUGINS = ".plugins.";

    /** The package segment a bot's parameters sit under. */
    private static final String PARAMS = ".params.";

    private ParamGroups() {}

    /**
     * The value marked {@code id}: a {@code public static} method of no argument in one of {@code values}, else a
     * method of the group beside one of them; null when none marks it or it cannot be called.
     */
    public static Object marked(SdkValue.Id id, Class<?>... values) {
        if (values == null) return null;
        for (Class<?> type : values) {
            if (type == null) continue;
            Optional<Object> found = staticMarked(type, id);
            if (found.isPresent()) return found.get();
        }
        for (Object group : groups(values)) {
            Optional<Object> found = instanceMarked(group, id);
            if (found.isPresent()) return found.get();
        }
        return null;
    }

    /**
     * The parameter groups beside {@code values}, made once each, in their order and each holder's by name; none that
     * cannot be made.
     */
    public static List<Object> groups(Class<?>... values) {
        List<Object> out = new ArrayList<>();
        if (values == null) return out;
        Set<Class<?>> seen = new LinkedHashSet<>();
        for (Class<?> type : values) {
            if (type != null) seen.addAll(groupsOf(type));
        }
        for (Class<?> group : seen) {
            try {
                Constructor<?> made = group.getDeclaredConstructor();
                made.setAccessible(true);
                out.add(made.newInstance());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unmade) {
                // a group that cannot be made has no values to give
            }
        }
        return out;
    }

    /**
     * Every top-level class of the bot's {@code params} package, at any depth: the bot found from the first of
     * {@code holders} under its {@code plugins} package, in that holder's class loader. A class that cannot be loaded
     * is left out; loading initialises none.
     */
    public static List<Class<?>> allBeside(Class<?>... holders) {
        for (Class<?> holder : holders == null ? new Class<?>[0] : holders) {
            int at = holder == null ? -1 : holder.getName().lastIndexOf(PLUGINS);
            if (at < 0) continue;
            String root = (holder.getName().substring(0, at) + PARAMS.substring(0, PARAMS.length() - 1)).replace('.', '/');
            return classesIn(holder.getClassLoader(), root, true);
        }
        return List.of();
    }

    /**
     * The top-level classes of the package at resource path {@code root} in {@code loader}, by name, and of its
     * sub-packages when {@code deep}; none that cannot be loaded, and loading initialises none.
     */
    private static List<Class<?>> classesIn(ClassLoader loader, String root, boolean deep) {
        Set<String> names = new TreeSet<>();
        try {
            for (URL folder : Collections.list(loader.getResources(root))) {
                names.addAll(classesUnder(folder, root, deep));
            }
        } catch (IOException | RuntimeException unlisted) {
            return List.of();
        }
        List<Class<?>> out = new ArrayList<>();
        for (String name : names) {
            try {
                out.add(Class.forName(name, false, loader));
            } catch (ClassNotFoundException | LinkageError unloadable) {
                // not a class this run can read a value from
            }
        }
        return out;
    }

    /** The binary names of the top-level classes under {@code root}, a resource path found at {@code folder}. */
    private static List<String> classesUnder(URL folder, String root, boolean deep) throws IOException {
        if (folder.openConnection() instanceof JarURLConnection jar) {
            try (Stream<JarEntry> entries = jar.getJarFile().stream()) {
                return entries.map(JarEntry::getName).filter(name -> name.startsWith(root + "/"))
                        .filter(name -> deep || name.indexOf('/', root.length() + 1) < 0)
                        .filter(ParamGroups::isTopLevelClass).map(ParamGroups::binaryName).toList();
            }
        }
        if (!"file".equals(folder.getProtocol())) return List.of();
        Path base;
        try {
            base = Path.of(folder.toURI());
        } catch (URISyntaxException unreadable) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(base, deep ? Integer.MAX_VALUE : 1)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> root + "/" + base.relativize(file).toString().replace(File.separatorChar, '/'))
                    .filter(ParamGroups::isTopLevelClass).map(ParamGroups::binaryName).toList();
        }
    }

    private static boolean isTopLevelClass(String path) {
        return path.endsWith(".class") && !path.substring(path.lastIndexOf('/') + 1).contains("$");
    }

    private static String binaryName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    /** Each holder's groups, found once: a class's package does not change while it is loaded. */
    private static final ClassValue<List<Class<?>>> GROUPS = new ClassValue<>() {
        @Override
        protected List<Class<?>> computeValue(Class<?> holder) {
            return find(holder);
        }
    };

    /**
     * The {@code @Param} classes of {@code holder}'s plugin — the package {@link #paramsPackage} names — by name;
     * none outside {@code plugins}.
     */
    static List<Class<?>> groupsOf(Class<?> holder) {
        return GROUPS.get(holder);
    }

    /**
     * What {@link #groupsOf} lists: the package's classes, and, should its loader list no folder, the SDK's own groups
     * by their {@link SdkValue.Holder} names, which load by name from any loader.
     */
    private static List<Class<?>> find(Class<?> holder) {
        String pkg = paramsPackage(holder.getName());
        if (pkg == null) return List.of();
        ClassLoader loader = holder.getClassLoader();
        Set<Class<?>> found = new TreeSet<>(Comparator.comparing(Class::getName));
        found.addAll(classesIn(loader, pkg.replace('.', '/'), false));
        for (SdkValue.Holder each : SdkValue.Holder.values()) {
            try {
                found.add(Class.forName(pkg + "." + className(each), false, loader));
            } catch (ClassNotFoundException | LinkageError absent) {
                // not a group this bot has
            }
        }
        return found.stream().filter(group -> group.isAnnotationPresent(Param.class)).toList();
    }

    /** A holder constant's class name, {@code FLOW_LAYOUT} as {@code FlowLayout}. */
    private static String className(SdkValue.Holder holder) {
        StringBuilder out = new StringBuilder();
        for (String part : holder.name().split("_")) {
            out.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    /** The package of {@code holder}'s groups: {@code a.plugins.b.Sdk} as {@code a.params.b}; null outside plugins. */
    static String paramsPackage(String holder) {
        int at = holder.lastIndexOf(PLUGINS);
        int dot = holder.lastIndexOf('.');
        return at < 0 || dot < at + PLUGINS.length() ? null
                : holder.substring(0, at) + PARAMS + holder.substring(at + PLUGINS.length(), dot);
    }

    private static Optional<Object> staticMarked(Class<?> type, SdkValue.Id id) {
        for (Method method : methods(type)) {
            if (!Modifier.isStatic(method.getModifiers()) || !marks(method, id)) continue;
            return call(method, null);
        }
        return Optional.empty();
    }

    private static Optional<Object> instanceMarked(Object group, SdkValue.Id id) {
        for (Method method : methods(group.getClass())) {
            if (Modifier.isStatic(method.getModifiers()) || !marks(method, id)) continue;
            return call(method, group);
        }
        return Optional.empty();
    }

    private static boolean marks(Method method, SdkValue.Id id) {
        SdkValue mark = method.getAnnotation(SdkValue.class);
        // Public only, as Studio's editor counts a value: a private helper carrying the mark is no value.
        return mark != null && mark.value() == id && Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 0 && method.getReturnType() != void.class;
    }

    private static Method[] methods(Class<?> type) {
        try {
            return type.getDeclaredMethods();
        } catch (RuntimeException | LinkageError unreadable) {
            return new Method[0];
        }
    }

    /** What {@code method} returns on {@code target}, empty when it throws: a value that cannot be read is none. */
    private static Optional<Object> call(Method method, Object target) {
        try {
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
