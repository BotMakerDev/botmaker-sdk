package com.botmaker.sdk.internal.trace;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.shared.Diag;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * What a run saw when a wait timed out, kept as a picture so whoever reads the trace — the user, or Claude
 * through {@code read_trace} — looks at one file instead of polling screenshots (2026-10-11).
 *
 * <p>Only a bot's run saves ({@link #start}, from {@code Bot.runFlow} and {@code Bot.trial}): a host calling the
 * finders in its own JVM saves nothing. Each run gets a folder under {@code target/botmaker-runs/} in the bot's
 * folder as it starts, so the newest folder is always the latest run's, empty when nothing failed. A timeout saves
 * the frame it looked at, the search area boxed when the source was a region, and says so in one warning line
 * naming the file, debugging on or off. Bounded: {@link #KEEP} frames per run, the oldest dropped first, the last
 * {@link #KEEP_RUNS} runs kept; one failure repeating saves once per {@link #QUIET_MS}.
 *
 * <p>Only a wait that timed out saves, never a single find or click that missed: a bot writes those as conditions
 * ({@code if (ImageFinder.find(…))}, {@code while (ImageClicker.click(…))}), and a loop ends on a miss. A wait
 * ended by the run stopping saves nothing either.
 */
public final class MissFrames {

    static final int KEEP = 20;
    static final int KEEP_RUNS = 5;
    static final long QUIET_MS = 30_000;
    /** The folder every run's frames go under, relative to the bot's folder. */
    public static final Path FOLDER = Path.of("target", "botmaker-runs");

    private static final String SOURCE = "Vision";
    private static final DateTimeFormatter RUN_NAME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Color BOX = new Color(0xe3, 0xa0, 0x08);

    private static LongSupplier clock = System::currentTimeMillis;
    private static final Map<String, Long> lastSaved = new HashMap<>();
    private static final Deque<Path> saved = new ArrayDeque<>();
    /** This run's folder; null outside a run, when nothing saves. */
    private static volatile Path run;
    private static int next;

    private MissFrames() {}

    /** Starts saving for this run, in a new folder under the bot's folder, the run's working directory. */
    public static void start() {
        start(Path.of(System.getProperty("user.dir", ".")).resolve(FOLDER), System::currentTimeMillis);
    }

    /** {@link #start()} under {@code root}, timed by {@code now}. */
    static synchronized void start(Path root, LongSupplier now) {
        if (run != null && run.getParent().equals(root)) return;
        clock = now;
        lastSaved.clear();
        saved.clear();
        next = 0;
        try {
            run = newRun(root);
        } catch (IOException e) {
            run = null;
            Diag.warn(SOURCE, "Failed frames will not be saved: " + e.getMessage());
        }
    }

    /**
     * Saves what {@code source} showed when {@code what} timed out, and warns with the file's name. {@code seen} is
     * the frame the wait last looked at, when it has one; otherwise the source is captured now. Never throws: a
     * frame that cannot be written costs only itself.
     */
    public static void missed(String what, CaptureSource source, BufferedImage seen) {
        if (run == null || source == null || Thread.currentThread().isInterrupted()) return;
        Path file;
        synchronized (MissFrames.class) {
            long now = clock.getAsLong();
            Long last = lastSaved.get(what);
            if (last != null && now - last < QUIET_MS) return;
            lastSaved.put(what, now);
            file = run.resolve(String.format("%02d-%s.png", ++next, slug(what)));
        }
        // Captured and written outside the lock: another thread's failure need not wait for this one's PNG.
        try {
            BufferedImage frame = framed(source, seen);
            if (frame == null) return;
            ImageIO.write(frame, "png", file.toFile());
        } catch (IOException | RuntimeException e) {
            Diag.warn(SOURCE, "Failed: " + what + ". Its frame could not be saved: " + e.getMessage());
            return;
        }
        synchronized (MissFrames.class) {
            saved.addLast(file);
            while (saved.size() > KEEP) {
                try {
                    Files.deleteIfExists(saved.removeFirst());
                } catch (IOException ignored) {
                    // an old frame left behind costs a file, not the run
                }
            }
        }
        Diag.warn(SOURCE, "Failed: " + what + ". Saved what it saw: " + shown(file));
    }

    /** The latest run's saved frames under {@code botFolder}, oldest first; empty when that run saved none. */
    public static List<Path> latest(Path botFolder) {
        Path runs = botFolder.resolve(FOLDER);
        if (!Files.isDirectory(runs)) return List.of();
        try (Stream<Path> dirs = Files.list(runs)) {
            Optional<Path> newest = dirs.filter(Files::isDirectory).max(Comparator.comparing(Path::getFileName));
            if (newest.isEmpty()) return List.of();
            try (Stream<Path> files = Files.list(newest.get())) {
                return files.filter(f -> f.getFileName().toString().endsWith(".png")).sorted().toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The frame to save: the whole surface with the region boxed when the source is one, else what was seen. */
    private static BufferedImage framed(CaptureSource source, BufferedImage seen) {
        Rect region = source.subRegion();
        BufferedImage whole = region != null ? source.base().capture() : null;
        BufferedImage shot = whole != null ? whole : seen != null ? seen : source.capture();
        if (shot == null) return null;
        BufferedImage copy = new BufferedImage(shot.getWidth(), shot.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(shot, 0, 0, null);
            if (whole != null) {
                g.setColor(BOX);
                g.setStroke(new BasicStroke(3));
                g.drawRect(region.x(), region.y(), region.width(), region.height());
            }
        } finally {
            g.dispose();
        }
        return copy;
    }

    /** A new run's folder, after dropping all but the newest {@code KEEP_RUNS - 1} earlier runs. */
    private static Path newRun(Path root) throws IOException {
        Files.createDirectories(root);
        try (Stream<Path> dirs = Files.list(root)) {
            List<Path> old = dirs.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName).reversed()).toList();
            for (Path dir : old.subList(Math.min(old.size(), KEEP_RUNS - 1), old.size())) delete(dir);
        }
        String name = LocalDateTime.now().format(RUN_NAME);
        Path dir = root.resolve(name);
        for (int n = 2; Files.exists(dir); n++) dir = root.resolve(name + "-" + n);
        return Files.createDirectories(dir);
    }

    private static void delete(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) Files.deleteIfExists(f);
        }
        Files.deleteIfExists(dir);
    }

    /** {@code what} as a file name: letters, digits and dashes, at most 40. */
    static String slug(String what) {
        String s = what.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("-$", "");
        return s.isEmpty() ? "miss" : s;
    }

    /** {@code file} as the trace names it: relative to the bot's folder when it is inside it. */
    private static String shown(Path file) {
        Path folder = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        Path abs = file.toAbsolutePath();
        return abs.startsWith(folder) ? folder.relativize(abs).toString() : abs.toString();
    }

    /** Stops saving. Tests only. */
    static synchronized void stop() {
        run = null;
    }
}
