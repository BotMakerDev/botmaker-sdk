package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Look;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.Reach;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.vision.Places;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Whether each screen's shot shows what the screen names: its marker, and each input's and its way back's picture, at
 * its place, elsewhere, or not at all. read_map says what does not match; 🗺 Screens draws each box where its picture
 * is. Firestone's map had a replay screen whose shot was the VS splash and shared pictures 48 px off their place on
 * the screens that use them, and nothing said so (2026-10-16).
 */
public final class MapAudit {

    /** The most screens' audits kept: each is a match of every picture a screen names over its shot. */
    private static final int MOST = 256;

    /** Each audit by what it was made of: the shot's and each picture's file times, its place, the confidence. */
    private static final Map<String, Audit> MADE = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Audit> eldest) {
            return size() > MOST;
        }
    });

    /** The most pictures-on-a-shot kept: each a screen's marker looked for on another screen's shot. */
    private static final int MOST_FITTED = 65_536;

    /**
     * Each screen's marker on another screen's shot, by the shot's and the picture's file times, the confidence and how
     * the picture is looked for ({@link #key}): read_map looks for every screen's markers on every other shot, 43
     * screens on Sword x Staff, and matched them all again on each call, one or two a batch, 400 s (2026-11-02).
     */
    private static final Map<String, Fit> FITTED = Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Fit> eldest) {
                    return size() > MOST_FITTED;
                }
            });

    /** Pictures matched over a shot so far, each look one: what the tests count to see work is not done twice. */
    static final LongAdder SEARCHED = new LongAdder();
    /** Batches matched so far, each one shot's: the tests count them to see pictures are matched side by side. */
    static final LongAdder BATCHES = new LongAdder();

    private MapAudit() {}

    /** Where a picture is on a frame: at its place (or anywhere, for one with no place), elsewhere, or nowhere. */
    public enum Where { AT_PLACE, ELSEWHERE, MISSING }

    /** {@code picture} on a frame: where, and its box there when found. */
    public record Fit(String picture, Where where, Rect at) {
    }

    /**
     * One picture a screen names, as its shot shows it.
     *
     * @param picture its file's name
     * @param marker  whether it is one of the screen's markers
     * @param absent  whether it is one the screen never shows: found on the shot, the shot or the set is wrong
     * @param sure    whether the screen always shows it; one that comes and goes, a look's, a button's other look or a
     *                lock is not, and its absence is only a note
     * @param at      its box on the shot, in the shot's pixels; null when missing
     * @param place   where its place is on the shot, in the shot's pixels; null when it has none
     * @param area    the strip its input is anywhere in, on the shot; null when it
     *                sits at its place. Anywhere in it is at its place, and its absence is only a note: scrolled out
     */
    public record Spot(String picture, boolean marker, boolean absent, boolean sure, Where where, Rect at, Rect place,
                       Rect area) {

        /** A spot with no list or column. */
        public Spot(String picture, boolean marker, boolean absent, boolean sure, Where where, Rect at, Rect place) {
            this(picture, marker, absent, sure, where, at, place, null);
        }

        /** How far it sits from its place, across: 0 at its place or when either is unknown. */
        public int dx() {
            return at == null || place == null ? 0 : at.x() - place.x();
        }

        /** How far it sits from its place, down. */
        public int dy() {
            return at == null || place == null ? 0 : at.y() - place.y();
        }
    }

    /** A screen's shot checked: each picture it names, in the order the screen names them. */
    public record Audit(List<Spot> spots) {

        /** {@code picture}'s spot, by its id or file name; empty when the screen does not name it or it has no file. */
        public Optional<Spot> of(String picture) {
            String file = TemplateLibrary.sanitizeName(picture == null ? "" : picture);
            return spots.stream().filter(s -> s.picture().equals(file)).findFirst();
        }

        /**
         * Whether the shot shows the screen as its markers tell it: each marker found, no absent picture found; true for
         * a screen with none, which nothing tells apart.
         */
        public boolean showsMarker() {
            return spots.stream().filter(Spot::marker).allMatch(s -> s.where() != Where.MISSING)
                    && spots.stream().filter(Spot::absent).allMatch(s -> s.where() == Where.MISSING);
        }
    }

    /** The confidence a run holds a match to, which the audit matches at: the project's, else the SDK's default. */
    public static double confidence(StudioServices services) {
        return Frames.confidence(services);
    }

    /**
     * {@code screen}'s shot checked; empty while it has no shot, or it cannot be read. Made once for each shot and
     * pictures as they are on disk and each place {@code cuts} gives: a retake, a recut or a moved place makes it again.
     * Off the FX thread: it matches every picture the screen names.
     */
    public static Optional<Audit> of(Path resources, double confidence, Map<String, FrameCut> cuts, String constant,
                                     MapScreen screen) {
        if (resources == null || screen == null) return Optional.empty();
        Optional<Path> named = ScreenShots.file(resources, screen);
        if (named.isEmpty()) return Optional.empty();
        Path shotFile = named.get();
        long shotTime = modified(shotFile);
        if (shotTime == 0) return Optional.empty();
        Map<String, Check> checks = checks(resources, screen);
        StringBuilder key = new StringBuilder(resources + "|" + constant + "@" + shotTime + "|" + confidence);
        checks.values().forEach(c -> key.append('|').append(c).append('@')
                .append(modified(TemplateLibrary.fileForName(resources, c.picture()))).append(cuts.get(c.picture())));
        Audit made = MADE.get(key.toString());
        if (made != null) return Optional.of(made);
        BufferedImage shot;
        try {
            shot = ImageIO.read(shotFile.toFile());
        } catch (IOException unreadable) {
            return Optional.empty();
        }
        if (shot == null) return Optional.empty();
        List<String> names = List.copyOf(checks.keySet());
        List<Fit> fits = fits(resources, confidence, cuts, names, shot);
        Size live = new Size(shot.getWidth(), shot.getHeight());
        List<Spot> spots = new ArrayList<>();
        for (Fit fit : fits) {
            Check check = checks.get(fit.picture());
            FrameCut cut = cuts.get(fit.picture());
            Rect area = check.area() == null ? null : onShot(check.area(), cut == null ? null : cut.frame(), live);
            // Anywhere in its list is where it belongs.
            Where where = fit.where() == Where.ELSEWHERE && area != null && area.contains(fit.at().center())
                    ? Where.AT_PLACE : fit.where();
            spots.add(new Spot(fit.picture(), check.marker(), check.absent(), check.sure(), where, fit.at(),
                    placeOn(cut, live), area));
        }
        Audit audit = new Audit(List.copyOf(spots));
        MADE.put(key.toString(), audit);
        return Optional.of(audit);
    }

    /**
     * Another screen whose shot shows {@code set} as a run tells a screen, and what would tell {@code constant} apart
     * from it.
     *
     * @param screen the other screen's constant
     * @param apart  that screen's markers {@code constant}'s own shot does not show: each one, as {@code constant}'s
     *               absent picture, tells the two apart
     */
    public record Clash(String screen, List<String> apart) {
    }

    /**
     * The first screen of {@code graph} other than {@code constant} whose shot shows {@code set} (every marker found
     * near its place, as {@code cuts} gives it, no absent picture found anywhere, every one of its words read): on that
     * screen a run would take the game for {@code constant}. Rarity's title scored 0.80 on Upgrade (2026-10-16).
     * Skipped: a popup, whose shot shows the screen under it, and a screen reached from {@code constant}, which a run
     * finds over it first. Empty with no clash, or while a picture of {@code set} has no file. Off the FX thread: it
     * reads and matches every other shot.
     *
     * <p>A marker is looked for where a run looks first ({@link Markers#onEach}): one far from its place on another
     * shot tells that run nothing. A run looks over the whole frame only when no screen shows near (a window whose
     * shape changed), and a clash there is not said.
     */
    public static Optional<Clash> clash(Path resources, double confidence, Map<String, FrameCut> cuts,
                                        ScreenGraph graph, String constant, Markers set) {
        return clash(resources, confidence, cuts, graph, constant, set, screen -> shot(resources, screen));
    }

    /**
     * Every picture's cut, its place among them, as {@link #clash} looks near it, read on the FX thread: none while
     * Studio has no values or the FX thread does not answer (a dialog holds it).
     */
    public static Map<String, FrameCut> cuts(StudioServices services) {
        try {
            return FxCall.call(() -> services.pluginValues() == null ? Map.<String, FrameCut>of()
                    : TemplateUses.cuts(services.pluginValues()));
        } catch (RuntimeException unanswered) {
            return Map.of();
        }
    }

    /**
     * {@link #cuts(StudioServices)} of only the pictures {@link #clash} looks for on {@code graph}: each screen's
     * markers and absent pictures, opening their constants alone, never the bot's hundreds.
     */
    public static Map<String, FrameCut> cuts(StudioServices services, ScreenGraph graph) {
        Path resources = services.resourcesDir();
        if (resources == null) return Map.of();
        Set<String> names = new LinkedHashSet<>();
        for (MapScreen screen : graph.screens().values()) {
            Markers set = Markers.of(screen);
            names.addAll(files(resources, set.shown()));
            names.addAll(files(resources, set.absent()));
        }
        try {
            return FxCall.call(() -> services.pluginValues() == null ? Map.<String, FrameCut>of()
                    : TemplateUses.cuts(services.pluginValues(), names));
        } catch (RuntimeException unanswered) {
            return Map.of();
        }
    }

    /** {@link #clash(Path, double, Map, ScreenGraph, String, Markers)} reading each screen's shot through {@code shots}. */
    private static Optional<Clash> clash(Path resources, double confidence, Map<String, FrameCut> cuts,
                                         ScreenGraph graph, String constant, Markers set,
                                         Function<MapScreen, Optional<BufferedImage>> shots) {
        if (resources == null || !set.any()) return Optional.empty();
        List<String> shown = files(resources, set.shown());
        List<String> absent = files(resources, set.absent());
        if (shown.size() < set.shown().size()) return Optional.empty();
        List<Sought> sought = new ArrayList<>();
        shown.forEach(p -> sought.add(new Sought(p, true)));
        absent.forEach(p -> sought.add(new Sought(p, false)));
        for (Map.Entry<String, MapScreen> other : graph.screens().entrySet()) {
            String c = other.getKey();
            if (c.equals(constant) || other.getValue().isPopup() || graph.dependsOn(c).contains(constant)) continue;
            Optional<List<Fit>> fitted = fitted(resources, confidence, cuts, sought, other.getValue(), shots);
            if (fitted.isEmpty()) continue;
            List<Fit> fits = fitted.get();
            boolean all = fits.subList(0, shown.size()).stream().allMatch(f -> f.where() != Where.MISSING);
            boolean none = fits.subList(shown.size(), fits.size()).stream().allMatch(f -> f.where() == Where.MISSING);
            // Its words are read only where its pictures clash: the shot's title may still tell the two apart.
            if (all && none && set.texts().stream().allMatch(words -> shots.apply(other.getValue())
                    .flatMap(shot -> Reach.findText(words, shot, Reach::ocr)).isPresent())) {
                return Optional.of(new Clash(c, apart(resources, confidence, cuts, graph.screens().get(constant),
                        other.getValue(), shots)));
            }
        }
        return Optional.empty();
    }

    /** A picture as {@link #clash} looks for it: within {@link Reach#MARKER_MARGIN} of its place, or anywhere. */
    private record Sought(String picture, boolean near) {
    }

    /**
     * Each of {@code sought} on {@code screen}'s shot, in its order. Each is matched once for the same files and the
     * same look ({@link #FITTED}); the shot is read and the ones not matched yet are matched in one batch. Empty while
     * it has no shot.
     */
    private static Optional<List<Fit>> fitted(Path resources, double confidence, Map<String, FrameCut> cuts,
                                              List<Sought> sought, MapScreen screen,
                                              Function<MapScreen, Optional<BufferedImage>> shots) {
        Optional<Path> file = ScreenShots.file(resources, screen).filter(Files::isRegularFile);
        if (file.isEmpty()) return Optional.empty();
        String on = file.get() + "@" + modified(file.get()) + "|" + confidence + "|";
        Map<Sought, String> keys = new LinkedHashMap<>();
        for (Sought s : sought) keys.computeIfAbsent(s, k -> on + key(resources, cuts, k));
        Map<Sought, Fit> found = new LinkedHashMap<>();
        List<Sought> missing = new ArrayList<>();
        keys.forEach((s, key) -> {
            Fit made = FITTED.get(key);
            if (made != null) found.put(s, made);
            else missing.add(s);
        });
        if (!missing.isEmpty()) {
            Optional<BufferedImage> shot = shots.apply(screen);
            if (shot.isEmpty()) return Optional.empty();
            List<MatchResult> matches = batch(resources, cuts, missing.stream().map(Sought::picture).toList(),
                    shot.get(), i -> missing.get(i).near(), Reach.MARKER_MARGIN);
            for (int i = 0; i < missing.size(); i++) {
                Sought s = missing.get(i);
                Fit fit = clears(matches.get(i), confidence) ? new Fit(s.picture(), Where.AT_PLACE,
                        matches.get(i).rect()) : new Fit(s.picture(), Where.MISSING, null);
                found.put(s, fit);
                FITTED.put(keys.get(s), fit);
            }
        }
        return Optional.of(sought.stream().map(found::get).toList());
    }

    /**
     * {@code sought}'s key, past its shot's: the picture's file time and how its template is built — the frame it was
     * cut on (which scales it) and its frames, and its place when looked for near it. A retake, a recut or a moved
     * place looks again; one built alike shares the match.
     */
    private static String key(Path resources, Map<String, FrameCut> cuts, Sought sought) {
        FrameCut cut = cuts.get(sought.picture());
        String how = cut == null ? "as cut" : "cut on " + cut.frame() + " x" + cut.frames()
                + (sought.near() && cut.place() != null ? " near " + cut.place() : "");
        return how + ":" + sought.picture() + "@" + modified(TemplateLibrary.fileForName(resources, sought.picture()));
    }

    /**
     * Each screen ({@code only}, or every one) whose markers another screen's shot shows too, said as cutting one
     * says it: a run takes that other screen for it. A marker is checked against the shots there are when it is cut,
     * so a screen whose shot came later went unseen: Hades II's Options skull crowns its Save slots too, and a walk
     * from Save slots believed it was on Options (2026-10-30).
     */
    public static List<String> clashes(Path resources, double confidence, Map<String, FrameCut> cuts,
                                       ScreenGraph graph, String only) {
        List<String> lines = new ArrayList<>();
        // Each shot is read once: every screen's markers are looked for on every other screen's shot.
        Map<MapScreen, Optional<BufferedImage>> read = new IdentityHashMap<>();
        Function<MapScreen, Optional<BufferedImage>> shots = screen -> read.computeIfAbsent(screen,
                s -> shot(resources, s));
        if (only == null) {
            // Every screen's markers on each shot in one batch, whose pictures match side by side: one or two a batch
            // left the cores idle.
            Set<Sought> every = new LinkedHashSet<>();
            for (MapScreen screen : graph.screens().values()) {
                if (screen.isPopup()) continue;
                Markers set = Markers.of(screen);
                files(resources, set.shown()).forEach(p -> every.add(new Sought(p, true)));
                files(resources, set.absent()).forEach(p -> every.add(new Sought(p, false)));
            }
            List<Sought> sought = List.copyOf(every);
            for (MapScreen screen : graph.screens().values()) {
                if (!screen.isPopup()) fitted(resources, confidence, cuts, sought, screen, shots);
            }
        }
        for (Map.Entry<String, MapScreen> each : graph.screens().entrySet()) {
            String c = each.getKey();
            if (only != null && !only.equals(c) || each.getValue().isPopup()) continue;
            Markers set = Markers.of(each.getValue());
            clash(resources, confidence, cuts, graph, c, set, shots)
                    .ifPresent(clash -> lines.add(graph.shown(c) + ": " + MapTools.clashSaid(graph, c, set, clash)));
        }
        return lines;
    }

    /**
     * {@code other}'s markers not anywhere on {@code screen}'s shot, as an absent picture is looked for; empty while it
     * has none.
     */
    private static List<String> apart(Path resources, double confidence, Map<String, FrameCut> cuts,
                                      MapScreen screen, MapScreen other,
                                      Function<MapScreen, Optional<BufferedImage>> shots) {
        List<Sought> theirs = files(resources, other.markers()).stream().map(p -> new Sought(p, false)).toList();
        if (theirs.isEmpty()) return List.of();
        return fitted(resources, confidence, cuts, theirs, screen, shots).orElse(List.of()).stream()
                .filter(f -> f.where() == Where.MISSING).map(Fit::picture).toList();
    }

    /** The file names of {@code pictures} that have a file. */
    private static List<String> files(Path resources, List<ImageTemplate> pictures) {
        return pictures.stream().map(p -> Frames.picture(resources, p.id())).flatMap(Optional::stream).distinct()
                .toList();
    }

    private static Optional<BufferedImage> shot(Path resources, MapScreen screen) {
        Optional<Path> file = ScreenShots.file(resources, screen).filter(Files::isRegularFile);
        if (file.isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(ImageIO.read(file.get().toFile()));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Where each of {@code pictures} is on {@code frame}: looked for near its place first ({@link LookTools#NEAR}), as
     * the assistant's looks do; one not there is then looked for over the whole frame, unless it has no place, which the
     * first look already searched whole. Two batches, each frame prepared once.
     */
    static List<Fit> fits(Path resources, double confidence, Map<String, FrameCut> cuts, List<String> pictures,
                          BufferedImage frame) {
        List<MatchResult> near = batch(resources, cuts, pictures, frame, i -> true, LookTools.NEAR);
        List<String> away = new ArrayList<>();
        for (int i = 0; i < pictures.size(); i++) {
            if (!clears(near.get(i), confidence) && placed(cuts, pictures.get(i))) away.add(pictures.get(i));
        }
        List<MatchResult> anywhere = away.isEmpty() ? List.of() : batch(resources, cuts, away, frame, i -> false, 0);
        List<Fit> fits = new ArrayList<>();
        for (int i = 0, next = 0; i < pictures.size(); i++) {
            String picture = pictures.get(i);
            if (clears(near.get(i), confidence)) fits.add(new Fit(picture, Where.AT_PLACE, near.get(i).rect()));
            else if (!placed(cuts, picture)) fits.add(new Fit(picture, Where.MISSING, null));
            else {
                MatchResult best = anywhere.get(next++);
                fits.add(clears(best, confidence) ? new Fit(picture, Where.ELSEWHERE, best.rect())
                        : new Fit(picture, Where.MISSING, null));
            }
        }
        return fits;
    }

    /** Whether {@code picture} is looked for near a place first: {@link #batch} looks for one without over the frame. */
    private static boolean placed(Map<String, FrameCut> cuts, String picture) {
        FrameCut cut = cuts.get(picture);
        return cut != null && cut.place() != null;
    }

    private static boolean clears(MatchResult match, double confidence) {
        return match.isFound() && match.confidence() >= confidence;
    }

    /**
     * Each picture's best match in one batch: within {@code margin} of its place when {@code near} holds for its index
     * and it has one, else anywhere; not found when it has no file.
     */
    private static List<MatchResult> batch(Path resources, Map<String, FrameCut> cuts, List<String> pictures,
                                           BufferedImage frame, IntPredicate near, int margin) {
        List<ImageTemplate> templates = new ArrayList<>();
        try {
            for (int i = 0; i < pictures.size(); i++) {
                String picture = pictures.get(i);
                FrameCut cut = cuts.get(picture);
                ImageTemplate file = new ImageTemplate(TemplateLibrary.fileForName(resources, picture).toString());
                if (near.test(i) && cut != null && cut.place() != null) file = file.at(cut.place());
                if (cut != null) file = file.frames(cut.frames());
                templates.add(cut == null || cut.frame() == null ? file : file.in(cut.frame()));
            }
            StillSource still = new StillSource(frame);
            SEARCHED.add(templates.size());
            BATCHES.increment();
            // One with no place is looked for everywhere.
            return ToolTiming.timed(ToolTiming.Phase.MATCH, () -> ImageFinder.bestMatchesNear(templates, margin, still));
        } finally {
            // Released after the match: an unreleased template is native memory.
            templates.forEach(ImageTemplate::close);
        }
    }

    /**
     * A picture to look for on a screen's shot: whether it is a marker, one the screen never shows, and whether the
     * screen always shows it, and the list or column its input is anywhere in (null: none).
     */
    private record Check(String picture, boolean marker, boolean absent, boolean sure, Rect area) {
    }

    /**
     * Each picture {@code screen} names that has a file, by its file's name: its markers and absent pictures, its
     * inputs' and its way back's, then its looks' markers and inputs. A picture named twice is sure when either is.
     */
    private static Map<String, Check> checks(Path resources, MapScreen screen) {
        Map<String, Check> checks = new LinkedHashMap<>();
        for (ImageTemplate marker : screen.markers()) add(checks, resources, marker, true, true);
        for (ImageTemplate absent : screen.absent()) {
            Frames.picture(resources, absent.id()).filter(f -> !checks.containsKey(f))
                    .ifPresent(f -> checks.put(f, new Check(f, false, true, false, null)));
        }
        // The inputs only a look shows are no sure part of the shot.
        Set<Input> looksOnly = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ScreenVariant look : screen.variants()) {
            for (ImageTemplate marker : look.markers()) add(checks, resources, marker, false, false, null);
            for (Input named : look.inputs()) {
                Optional<String> field = Optional.ofNullable(BotConstants.inputName(named)).map(n -> n.field())
                        .or(() -> screen.fieldOf(named));
                field.flatMap(screen::input).ifPresent(looksOnly::add);
            }
        }
        List<Input> named = new ArrayList<>(screen.inputs().values());
        if (screen.back() != null && BotConstants.inputName(screen.back()) == null
                && screen.fieldOf(screen.back()).isEmpty()) {
            named.add(screen.back());
        }
        for (Input input : named) {
            // One in a strip may be scrolled out of the shot, and is anywhere along it.
            Rect band = Reach.band(screen.stripOf(input).orElse(null));
            // A node of a tree that scrolls may be anywhere off the shot.
            boolean scrolled = screen.treeOf(input).filter(Tree::scrolls).isPresent();
            Input its = screen.effective(input);
            boolean sure = !looksOnly.contains(input) && !its.isOccasional() && its.locked() == null && band == null
                    && !scrolled;
            if (input instanceof Button button) {
                for (Look look : button.allLooks()) {
                    add(checks, resources, look.picture(), false, sure && button.looks().isEmpty(), band);
                }
            }
            if (input instanceof Strip strip && strip.members().size() == 1) {
                // A panel that scrolls: the picture it is scrolled over.
                add(checks, resources, strip.members().getFirst(), false, sure, null);
            }
            if (its.locked() != null) add(checks, resources, its.locked(), false, false, null);
        }
        return checks;
    }

    private static void add(Map<String, Check> checks, Path resources, ImageTemplate picture, boolean marker,
                            boolean sure) {
        add(checks, resources, picture, marker, sure, null);
    }

    private static void add(Map<String, Check> checks, Path resources, ImageTemplate picture, boolean marker,
                            boolean sure, Rect area) {
        Optional<String> file = Frames.picture(resources, picture.id());
        if (file.isEmpty()) return;
        Check was = checks.get(file.get());
        checks.put(file.get(), was == null ? new Check(file.get(), marker, false, sure, area)
                : new Check(file.get(), was.marker() || marker, was.absent(), was.sure() || sure,
                was.area() == null ? area : was.area()));
    }

    /** {@code cut}'s place on a shot of {@code live}, scaled from the frame it was cut on; null when it has none. */
    private static Rect placeOn(FrameCut cut, Size live) {
        if (cut == null || cut.place() == null) return null;
        return onShot(cut.place(), cut.frame(), live);
    }

    /** {@code rect} of a frame of {@code frame} on a shot of {@code live}; as it is when {@code frame} is null. */
    private static Rect onShot(Rect rect, Size frame, Size live) {
        if (frame == null) return rect;
        return Places.inSource(new Zone(rect.x(), rect.y(), rect.width(), rect.height()), frame, live);
    }

    private static long modified(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
        } catch (IOException gone) {
            return 0;
        }
    }
}
