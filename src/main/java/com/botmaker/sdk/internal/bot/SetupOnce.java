package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.Setup;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.config.ProjectDefaults;
import com.botmaker.shared.Diag;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The bot's setup runs once per computer, not at every launch.
 *
 * <p>The run property {@value #PROPERTY} says whether it is due: on, or absent, it runs at the run's first start of
 * the game; when it returns, the run reports it done on its trace and the host that started the run sets
 * {@value #PROPERTY}{@code =off}, with {@value #PARAMS_PROPERTY} holding a hash of the values of the bot's
 * {@code @Param}s in {@link Setup#CATEGORY}. A setup that fails ({@code Setup.fail}, or a throw) is not reported,
 * so it stays due. It comes back on by itself when one of those values changes, and otherwise when the person
 * running the bot ticks it.
 */
public final class SetupOnce {

    /** The run property saying whether the setup is due on this computer: {@code on} (or absent) or {@code off}. */
    public static final String PROPERTY = "botmaker.setup";

    /** The run property holding the hash of the Setup parameters' values the setup last ran with. */
    public static final String PARAMS_PROPERTY = "botmaker.setup.params";

    private static final String SOURCE = "Setup";

    /** The end of the line {@link #done} traces, carrying the hash. */
    private static final Pattern DONE = Pattern.compile("\\[setup ([0-9a-f]{16})]$");

    private SetupOnce() {}

    /**
     * {@code setup} as this run owes it — reporting itself done when it returns — or null when there is none or it
     * is done on this computer.
     *
     * @param bot    the bot's class, the one whose {@code main} runs it; its {@code @Param}s are in its package or
     *               below
     * @param values the bot's values classes, as {@code Bot.runFlow} takes them; the bot's classes are compiled
     *               beside them
     */
    public static Runnable owed(Runnable setup, Class<?> bot, Class<?>... values) {
        if (setup == null) return null;
        String hash = paramsHash(classesBeside(bot, values));
        String said = System.getProperty(PROPERTY);
        String stored = System.getProperty(PARAMS_PROPERTY);
        if (!due(said, stored, hash)) {
            Debug.log("The setup is done on this computer, so it does not run. It runs again when a Setup parameter "
                    + "changes, or when it is ticked in the runner.");
            // Switched off by hand before it ever ran: what it is off for is these values, so a change re-ticks it.
            if (stored == null || stored.isBlank()) {
                Diag.info(SOURCE, "The setup is off on this computer; it runs again when a Setup parameter changes. "
                        + "[setup " + hash + "]");
            }
            return null;
        }
        if (Boolean.FALSE.equals(ProjectDefaults.parseBoolean(said))) {
            Debug.log("A Setup parameter changed since the setup last ran on this computer, so it runs again.");
        }
        return () -> {
            setup.run();
            done(hash);
        };
    }

    /**
     * Whether the setup is due: {@code said} is not {@code off}, or the Setup parameters' {@code hash} differs from
     * the {@code stored} one. An {@code off} with nothing stored stays off: the person running the bot unticked it,
     * and the run reports the values it is off for, which the host stores.
     */
    static boolean due(String said, String stored, String hash) {
        if (!Boolean.FALSE.equals(ProjectDefaults.parseBoolean(said))) return true;
        return stored != null && !stored.isBlank() && !stored.strip().equals(hash);
    }

    /** Traces that the setup is done, whether debugging is on or off: the host records it from this line. */
    static void done(String hash) {
        Diag.info(SOURCE, "The setup is done on this computer; it runs again when a Setup parameter changes. "
                + "[setup " + hash + "]");
    }

    /**
     * The hash a trace line written by {@link #done} reports, or empty when the line is another.
     *
     * @param writerClass the binary name of the class that wrote the line
     * @param text        the line
     */
    public static Optional<String> reported(String writerClass, String text) {
        if (!SetupOnce.class.getName().equals(writerClass) || text == null) return Optional.empty();
        Matcher matcher = DONE.matcher(text.strip());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /**
     * A hash of the values of every {@code static} {@code @Param} in {@link Setup#CATEGORY} among {@code classes}:
     * sixteen hex digits, the same for the same values.
     */
    static String paramsHash(Collection<Class<?>> classes) {
        Map<String, String> values = new TreeMap<>();
        for (Class<?> type : classes) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (RuntimeException | LinkageError unreadable) {
                continue;
            }
            for (Field field : fields) {
                Param param = field.getAnnotation(Param.class);
                if (param == null || !Setup.CATEGORY.equals(param.category())
                        || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String key = type.getName() + "#" + field.getName();
                try {
                    field.trySetAccessible();
                    values.put(key, text(field.get(null)));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
                    values.put(key, "?");
                }
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(values.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is missing from this Java", absent);
        }
    }

    private static String text(Object value) {
        if (value != null && value.getClass().isArray()) return Arrays.deepToString(new Object[]{value});
        return String.valueOf(value);
    }

    /**
     * Every class of {@code bot}'s package or below compiled beside {@code values}, in the same folder or jar, loaded
     * without being initialised. The package keeps a jar holding the libraries too from being read whole; a class
     * that will not load is left out.
     */
    static List<Class<?>> classesBeside(Class<?> bot, Class<?>... values) {
        List<Class<?>> found = new ArrayList<>();
        if (bot == null || values == null) return found;
        String botPackage = bot.getPackageName();
        Set<Path> roots = new LinkedHashSet<>();
        for (Class<?> type : values) {
            if (type == null) continue;
            Path root = root(type);
            if (root == null || !roots.add(root)) continue;
            for (String name : classNames(root)) {
                // A bot in the default package has its classes there, and only there: not the libraries' packages.
                if (botPackage.isEmpty() ? name.contains(".") : !name.startsWith(botPackage + ".")) continue;
                try {
                    found.add(Class.forName(name, false, type.getClassLoader()));
                } catch (ClassNotFoundException | LinkageError unloadable) {
                    // Not a class this bot can load: it holds no parameter the bot reads.
                }
            }
        }
        return found;
    }

    private static Path root(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            return source == null || source.getLocation() == null ? null : Path.of(source.getLocation().toURI());
        } catch (URISyntaxException | RuntimeException unreadable) {
            return null;
        }
    }

    /** The binary names of the classes in {@code root}, a folder of classes or a jar. */
    private static List<String> classNames(Path root) {
        List<String> names = new ArrayList<>();
        try {
            if (Files.isDirectory(root)) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(path -> path.toString().endsWith(".class"))
                            .forEach(path -> add(names, root.relativize(path).toString().replace('\\', '/')));
                }
            } else if (Files.isRegularFile(root)) {
                try (JarFile jar = new JarFile(root.toFile())) {
                    jar.stream().forEach(entry -> add(names, entry.getName()));
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            Debug.log("The bot's classes in " + root + " could not be listed, so no Setup parameter is read from "
                    + "them: " + unreadable);
        }
        return names;
    }

    private static void add(List<String> names, String path) {
        if (!path.endsWith(".class") || path.startsWith("META-INF/")) return;
        String name = path.substring(0, path.length() - ".class".length()).replace('/', '.');
        if (name.endsWith("module-info") || name.endsWith("package-info")) return;
        names.add(name);
    }
}
