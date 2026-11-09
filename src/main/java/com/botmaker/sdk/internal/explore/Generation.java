package com.botmaker.sdk.internal.explore;

import com.botmaker.plugin.api.managed.ManagedValues;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.plan.Plan;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.params.RunFacts;
import com.botmaker.sdk.internal.params.RunParams;
import com.botmaker.sdk.internal.screens.ScreenMaps;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * One loading of the bot's own classes in a run exploring on its own (spec §5, 2026-11-09): a fix adds fields (a
 * readout, an input), which HotSwap cannot, so a resume compiles the bot's sources in the run's JVM into a folder of
 * its own and loads them in a new loader. Only the classes found in that folder are loaded child-first; the SDK, the
 * display, the game session and every jar come from the parent and stay. The run's own state (facts' values, tried
 * inputs, counts) is not in the bot's classes and is not touched.
 */
public final class Generation {

    /** Where the generations are compiled, under the bot's {@code target}; the trace agent knows the name. */
    public static final String FOLDER = "botmaker-explore";

    /** What {@link #next} gives: a loaded generation, or why there is none. */
    public sealed interface Result permits Ok, Refused {}

    /** The new generation, loaded; nothing is installed yet. */
    public record Ok(Generation generation) implements Result {}

    /** No new generation: the compile's errors, one a line, or why it could not compile. */
    public record Refused(List<String> errors) implements Result {
        public Refused {
            errors = List.copyOf(errors);
        }
    }

    private final int number;
    private final Path classes;
    /** The class folder (or jar) the run started with, which the parent still serves and every generation hides. */
    private final Path started;
    private final BotLoader loader;
    private final List<String> holderNames;

    private Generation(int number, Path classes, Path started, ClassLoader parent, List<String> holderNames) {
        this.number = number;
        this.classes = classes;
        this.started = started;
        this.loader = new BotLoader(classes, started, parent);
        this.holderNames = holderNames;
    }

    /** The first generation, over the class folder the first of {@code holderNames} ({@code Sdk}) came from. */
    public static Generation first(List<String> holderNames) {
        return first(holderNames, Thread.currentThread().getContextClassLoader());
    }

    /** As {@link #first(List)}, the holders found through {@code parent}. */
    static Generation first(List<String> holderNames, ClassLoader parent) {
        if (holderNames == null || holderNames.isEmpty()) throw new IllegalArgumentException("no holder named");
        Class<?> holder;
        try {
            holder = Class.forName(holderNames.getFirst(), false, parent);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(holderNames.getFirst() + " is not on the run's class path", e);
        }
        CodeSource source = holder.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new IllegalArgumentException(holder.getName() + " comes from no class folder");
        }
        Path folder;
        try {
            folder = Path.of(source.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(holder.getName() + "'s class folder is unreadable", e);
        }
        return new Generation(0, folder, folder, parent, List.copyOf(holderNames));
    }

    /**
     * Compiles the bot under {@code project} ({@code src/main/java}, its resources copied) into
     * {@code target/botmaker-explore/gen-<number>}, against the run's class path without the bot's own class folders,
     * and loads it over this generation's parent. This generation keeps serving, whatever the answer.
     */
    public Result next(Path project, int number) {
        if (number <= this.number) {
            return new Refused(List.of("Generation " + number + " is not after " + this.number + ": its folder may be in use."));
        }
        Path sources = project.resolve("src/main/java");
        if (!Files.isDirectory(sources)) return new Refused(List.of("The bot has no sources at " + sources + "."));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new Refused(List.of("This JVM has no Java compiler (a JRE): the bot cannot be reloaded in the run."));
        }
        Path out = project.resolve("target").resolve(FOLDER).resolve("gen-" + number);
        List<Path> files;
        try {
            clear(out);
            Files.createDirectories(out);
            try (Stream<Path> walk = Files.walk(sources)) {
                files = walk.filter(file -> file.toString().endsWith(".java") && Files.isRegularFile(file)).toList();
            }
            copyResources(project.resolve("src/main/resources"), out);
        } catch (IOException | UncheckedIOException e) {
            return new Refused(List.of("The new classes' folder cannot be written: " + e.getMessage()));
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compiled;
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT,
                StandardCharsets.UTF_8)) {
            List<String> options = List.of("-d", out.toString(), "-classpath", classPath(project, started),
                    "-parameters", "-g",
                    "-proc:none", "-encoding", "UTF-8", "-Xlint:none", "-nowarn");
            compiled = compiler.getTask(null, manager, diagnostics, options, null,
                    manager.getJavaFileObjectsFromPaths(files)).call();
        } catch (IOException | RuntimeException e) {
            return new Refused(List.of("The compiler failed: " + e));
        }
        if (!compiled) {
            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(found -> found.getKind() == Diagnostic.Kind.ERROR).map(Generation::said).toList();
            return new Refused(errors.isEmpty() ? List.of("The bot did not compile.") : errors);
        }
        return new Ok(new Generation(number, out, started, loader.getParent(), holderNames));
    }

    /**
     * Makes this generation the one the run serves: the managed values, the game's settings (the capture source is
     * left as it is), the map, the facts, during-a-run parameters moved to the new classes, and the thread's loader.
     * Re-read the plan with {@link #plan()} after. Called on the run's loop thread while nothing of the old generation
     * runs (a run paused on a stop); another thread keeps its own context loader.
     */
    public void install() {
        Class<?>[] holders = holders();
        Thread.currentThread().setContextClassLoader(loader);
        // The run's values first, so nothing installed after reads a fact as compiled rather than as the run knows it.
        RunParams.newGeneration(loader);
        ManagedValues.install(holders);
        InstalledGame.settings(holders);
        ScreenMaps.install(holders);
        RunFacts.install(holders);
    }

    /** The plan the bot's {@code plan} parameter picks in this generation; null when none, or it has no goals. */
    public Plan plan() {
        return InstalledGame.marked(SdkValue.Id.PLAN, holders()) instanceof Plan plan && !plan.tiers().isEmpty()
                ? plan : null;
    }

    /** The holders ({@code Sdk.class}) as this generation loads them; a holder its classes lack is left out. */
    public Class<?>[] holders() {
        List<Class<?>> found = new ArrayList<>();
        for (String name : holderNames) {
            try {
                found.add(Class.forName(name, true, loader));
            } catch (ClassNotFoundException | LinkageError gone) {
                // a holder the bot no longer has
            }
        }
        return found.toArray(Class<?>[]::new);
    }

    /**
     * 0 for the run's first loading, over the class folder it started with (a copy of its own: the run's loop calls the
     * bot through a generation from the start), then the number {@link #next} was given.
     */
    public int number() {
        return number;
    }

    /** The folder this generation's classes come from. */
    public Path classes() {
        return classes;
    }

    /** What loads this generation's classes. */
    public ClassLoader loader() {
        return loader;
    }

    /** {@code path:line: message}, the way a compiler's error reads. */
    private static String said(Diagnostic<? extends JavaFileObject> found) {
        String where = found.getSource() == null ? "" : Path.of(found.getSource().toUri()).getFileName()
                + (found.getLineNumber() > 0 ? ":" + found.getLineNumber() : "") + ": ";
        return where + found.getMessage(Locale.ROOT);
    }

    /**
     * The run's class path, a manifest-only jar's {@code Class-Path} opened out (surefire's), without the bot's own
     * class folders: a class the new sources deleted must not resolve from the old ones.
     */
    static String classPath(Path project, Path started) {
        Path target = project.resolve("target").toAbsolutePath().normalize();
        Path old = started == null ? null : started.toAbsolutePath().normalize();
        Set<String> entries = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.isBlank()) continue;
            entries.add(entry);
            entries.addAll(manifestClassPath(Path.of(entry)));
        }
        return String.join(File.pathSeparator, entries.stream().filter(entry -> {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            return !path.equals(old) && !(path.startsWith(target) && Files.isDirectory(path));
        }).toList());
    }

    private static List<String> manifestClassPath(Path jar) {
        if (!jar.toString().endsWith(".jar") || !Files.isRegularFile(jar)) return List.of();
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            String listed = manifest == null ? null : manifest.getMainAttributes().getValue("Class-Path");
            if (listed == null || listed.isBlank()) return List.of();
            List<String> out = new ArrayList<>();
            for (String url : listed.trim().split("\\s+")) {
                try {
                    out.add(Path.of(jar.toUri().resolve(url)).toString());
                } catch (IllegalArgumentException unreadable) {
                    // an entry that is no file
                }
            }
            return out;
        } catch (IOException unreadable) {
            return List.of();
        }
    }

    private static void copyResources(Path resources, Path out) throws IOException {
        if (!Files.isDirectory(resources)) return;
        try (Stream<Path> walk = Files.walk(resources)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                Path to = out.resolve(resources.relativize(file).toString());
                Files.createDirectories(to.getParent());
                Files.copy(file, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void clear(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    /**
     * Loads the classes and resources found in {@code folder} itself before asking the parent, and everything else
     * from the parent: the SDK's statics stay the run's own, whichever generation calls them. What the parent would
     * serve from {@code started}, the bot's old classes, it never serves: a class the new sources deleted is gone.
     */
    static final class BotLoader extends URLClassLoader {

        static {
            ClassLoader.registerAsParallelCapable();
        }

        /** How every URL the parent gives from the old classes starts ({@code file:…/classes/}, {@code jar:file:…}). */
        private final String hidden;

        BotLoader(Path folder, Path started, ClassLoader parent) {
            super("bot-" + folder.getFileName(), new URL[] {url(folder)}, parent);
            this.hidden = url(started).toString();
        }

        private boolean hides(URL found) {
            String said = found.toString();
            return said.startsWith(hidden) || said.startsWith("jar:" + hidden);
        }

        private static URL url(Path folder) {
            try {
                return folder.toUri().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(folder + " is no folder a loader reads", e);
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                String file = name.replace('.', '/') + ".class";
                if (loaded == null && findResource(file) != null) loaded = findClass(name);
                if (loaded == null) {
                    URL inherited = parent().getResource(file);
                    if (inherited != null && hides(inherited)) throw new ClassNotFoundException(name);
                    return super.loadClass(name, resolve);
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        private ClassLoader parent() {
            return Objects.requireNonNullElse(getParent(), getPlatformClassLoader());
        }

        @Override
        public URL getResource(String name) {
            URL own = findResource(name);
            if (own != null) return own;
            URL inherited = parent().getResource(name);
            return inherited == null || hides(inherited) ? null : inherited;
        }

        /** This folder's, then the parent's but for the old classes': those would list classes this one dropped. */
        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> all = new ArrayList<>(Collections.list(findResources(name)));
            for (URL inherited : Collections.list(parent().getResources(name))) {
                if (!hides(inherited)) all.add(inherited);
            }
            return Collections.enumeration(all);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            URL found = getResource(name);
            if (found == null) return null;
            try {
                return found.openStream();
            } catch (IOException unreadable) {
                return null;
            }
        }
    }

    @Override
    public String toString() {
        return "Generation " + number + " " + classes;
    }
}
