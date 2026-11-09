package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.vision.TemplateNames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Moving the files a bot kept at its resources' root into the SDK's own folder, {@code plugins/<group>/<artifact>/}
 * (2026-10-30, the user's call): two plugins writing {@code images/} at the root wrote over each other's files, so
 * Studio gives each plugin its folder ({@code StudioServices.resourcesDir}). Only the SDK knows which root folders are
 * its own — {@code images/}, {@code digits/}, {@code screens/} — so the move is the SDK's, offered once a session
 * ({@link MoveOffer}).
 *
 * <p>The files move first, then every picture's and digit set's path in the Java is written the way the SDK writes
 * it now, {@code images/ore.png} for {@code src/main/resources/images/ore.png}, as one step of Studio's history.
 * Either half alone breaks nothing: a path is looked for in both places, where it was written for first
 * ({@code TemplateSource}). A file whose name the folder has already stays at the root and its path stays as written,
 * so the bot keeps the picture it had; ↶ puts back the Java only.
 */
public final class ResourceMove {

    /**
     * The SDK's folders at a bot's resources' root, as it kept them before, each with how deep its pictures sit:
     * {@code images/ore.png}, {@code digits/gold/0.png}, {@code screens/HOME.png}. Only those PNGs move; any other
     * file there is the bot's own.
     */
    static final Map<String, Integer> FOLDERS = Map.of("images", 1, DigitCuts.FOLDER, 2, "screens", 1);

    /** What {@link #moveFiles} did: how many files moved, and those left at the root, as {@code images/ore.png}. */
    record Moved(int files, Set<String> left) {}

    private static final ManagedSet<ImageTemplate> PICTURES = TemplateUses.SET;
    private static final ManagedSet<DigitSet> DIGITS = ManagedSet.of(SdkValues.DIGITS);

    private ResourceMove() {}

    /**
     * The resources' root above {@code folder}, the SDK's folder Studio gave it: {@code src/main/resources} for
     * {@code src/main/resources/plugins/<group>/<artifact>}. Empty when {@code folder} is not one, as from a Studio
     * that gives every plugin the root.
     */
    static Optional<Path> root(Path folder) {
        Path artifact = folder == null ? null : folder.toAbsolutePath().normalize();
        Path plugins = artifact == null || artifact.getParent() == null ? null : artifact.getParent().getParent();
        if (plugins == null || plugins.getFileName() == null || !plugins.getFileName().toString().equals("plugins")) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugins.getParent());
    }

    /**
     * The project's resources' root for {@code folder}, the SDK's folder or a Studio's root: what keys a project's one
     * {@code BackgroundLauncher}, as Studio keys it ({@code ProjectConfig.resourcesRoot}), so ▶ Launch's private
     * display is the one Studio hands the bot.
     */
    public static Path resourcesRoot(Path folder) {
        return root(folder).orElse(folder);
    }

    /** How many of the SDK's files are at {@code folder}'s resources' root; 0 when none or no root. Reads the disk. */
    public static int waiting(Path folder) {
        return root(folder).map(root -> old(root).size()).orElse(0);
    }

    /** Every picture of the SDK's in its old folders at {@code root}. */
    private static List<Path> old(Path root) {
        List<Path> files = new ArrayList<>();
        FOLDERS.forEach((name, depth) -> {
            Path dir = root.resolve(name);
            if (!Files.isDirectory(dir)) return;
            try (Stream<Path> walk = Files.walk(dir, depth)) {
                walk.filter(Files::isRegularFile)
                        .filter(file -> dir.relativize(file).getNameCount() == depth
                                && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                        .forEach(files::add);
            } catch (IOException | RuntimeException unreadable) {
                // A folder that cannot be walked is left at the root, where its files still load from.
            }
        });
        return files;
    }

    /**
     * Moves the SDK's pictures at {@code folder}'s resources' root into {@code folder}, each to the same place under
     * it; one whose name the folder has already is left at the root, never written over. Off the FX thread.
     */
    static Moved moveFiles(Path folder) throws IOException {
        Optional<Path> root = root(folder);
        if (root.isEmpty()) return new Moved(0, Set.of());
        int moved = 0;
        Set<String> left = new HashSet<>();
        for (Path file : old(root.get())) {
            String relative = root.get().relativize(file).toString().replace('\\', '/');
            Path to = folder.resolve(relative);
            if (Files.exists(to)) {
                left.add(relative);
                continue;
            }
            Files.createDirectories(to.getParent());
            Files.move(file, to);
            moved++;
        }
        for (String name : FOLDERS.keySet()) removeEmpty(root.get().resolve(name));
        return new Moved(moved, Set.copyOf(left));
    }

    /** {@code dir} and every folder under it that is empty now, deepest first. */
    private static void removeEmpty(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path each : walk.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).toList()) {
                try (Stream<Path> inside = Files.list(each)) {
                    if (inside.findAny().isEmpty()) Files.delete(each);
                }
            }
        } catch (IOException | RuntimeException kept) {
            // An empty folder left behind holds nothing anybody loads.
        }
    }

    /**
     * Writes each picture's and digit set's path the way the SDK writes it now, as one step of Studio's history:
     * every constant is opened before the first write, so the bot is read once, not once per constant. One whose file
     * was {@code left} at the root keeps its path, which is looked for at the root first. On the FX thread. Answers
     * how many were rewritten and why any was not.
     */
    static int rewritePaths(StudioServices services, Set<String> left, List<String> refused) {
        PluginValues values = services.pluginValues();
        if (values == null) return 0;
        List<Pending> pending = new ArrayList<>();
        pending.addAll(opened(values, PICTURES, ImageTemplate::filePath, left, (held, path) -> FrameCut.of(held)
                .onto(new ImageTemplate(path)).tags(held.tags())));
        pending.addAll(opened(values, DIGITS, DigitSet::folder, left,
                (held, path) -> new DigitSet(path).in(held.frame())));
        if (pending.isEmpty()) return 0;
        int[] written = {0};
        services.history().batch("Move the SDK's files into its folder", () -> {
            for (Pending each : pending) {
                Optional<String> not;
                try {
                    not = each.context().write(each.value());
                } catch (RuntimeException failed) {
                    not = Optional.of(String.valueOf(failed.getMessage()));
                }
                if (not.isEmpty()) written[0]++;
                else refused.add(each.name() + ": " + not.get());
            }
        });
        return written[0];
    }

    /** One constant to rewrite: its opened value, its name for a sentence and what it becomes. */
    private record Pending(String name, ValueContext context, Object value) {}

    /** Each constant of {@code set} whose path is written from the project's root, opened, with its new value. */
    private static <E> List<Pending> opened(PluginValues values, ManagedSet<E> set, Function<E, String> path,
                                            Set<String> left, Rewritten<E> rewritten) {
        List<Pending> pending = new ArrayList<>();
        for (PluginValues.Member member : set.members(values)) {
            Optional<ValueContext> context = set.open(values, member);
            if (context.isEmpty()) continue;
            Optional<E> held = set.read(context.get());
            if (held.isEmpty()) continue;
            String was = path.apply(held.get());
            String now = TemplateNames.current(was);
            if (was == null || was.equals(now) || stayed(now, left)) continue;
            pending.add(new Pending(member.toString(), context.get(), rewritten.with(held.get(), now)));
        }
        return pending;
    }

    /** Whether {@code path}, a picture or a digit set's folder, is or holds a file left at the root. */
    static boolean stayed(String path, Set<String> left) {
        return left.contains(path) || left.stream().anyMatch(file -> file.startsWith(path + "/"));
    }

    @FunctionalInterface
    private interface Rewritten<E> {
        E with(E held, String path);
    }
}
