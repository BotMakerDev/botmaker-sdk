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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        Path shotFile = ScreenShots.file(resources, constant);
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
     * anywhere on it, no absent picture found): on that screen a run would take the game for {@code constant}. Rarity's
     * title scored 0.80 on Upgrade (2026-10-16). Skipped: a popup, whose shot shows the screen under it, and a screen
     * reached from {@code constant}, which a run finds over it first. Empty with no clash, or while a picture of
     * {@code set} has no file. Off the FX thread: it reads and matches every other shot.
     */
    public static Optional<Clash> clash(Path resources, double confidence, ScreenGraph graph, String constant,
                                        Markers set) {
        if (resources == null || !set.any()) return Optional.empty();
        List<String> shown = files(resources, set.shown());
        List<String> absent = files(resources, set.absent());
        if (shown.size() < set.shown().size()) return Optional.empty();
        List<String> names = new ArrayList<>(shown);
        names.addAll(absent);
        for (Map.Entry<String, MapScreen> other : graph.screens().entrySet()) {
            String c = other.getKey();
            if (c.equals(constant) || other.getValue().isPopup() || graph.dependsOn(c).contains(constant)) continue;
            Optional<BufferedImage> shot = shot(resources, c);
            if (shot.isEmpty()) continue;
            List<Fit> fits = fits(resources, confidence, Map.of(), names, shot.get());
            boolean all = fits.subList(0, shown.size()).stream().allMatch(f -> f.where() != Where.MISSING);
            boolean none = fits.subList(shown.size(), fits.size()).stream().allMatch(f -> f.where() == Where.MISSING);
            if (all && none) return Optional.of(new Clash(c, apart(resources, confidence, constant, other.getValue())));
        }
        return Optional.empty();
    }

    /** {@code other}'s markers not on {@code constant}'s shot; empty while it has none. */
    private static List<String> apart(Path resources, double confidence, String constant, MapScreen other) {
        Optional<BufferedImage> own = shot(resources, constant);
        List<String> theirs = files(resources, other.markers());
        if (own.isEmpty() || theirs.isEmpty()) return List.of();
        return fits(resources, confidence, Map.of(), theirs, own.get()).stream()
                .filter(f -> f.where() == Where.MISSING).map(Fit::picture).toList();
    }

    /** The file names of {@code pictures} that have a file. */
    private static List<String> files(Path resources, List<ImageTemplate> pictures) {
        return pictures.stream().map(p -> Frames.picture(resources, p.id())).flatMap(Optional::stream).distinct()
                .toList();
    }

    private static Optional<BufferedImage> shot(Path resources, String constant) {
        Path file = ScreenShots.file(resources, constant);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.ofNullable(ImageIO.read(file.toFile()));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * Where each of {@code pictures} is on {@code frame}: looked for near its place first ({@link LookTools#NEAR}), as
     * the assistant's looks do; one not there is then looked for over the whole frame. Two batches, each frame prepared
     * once.
     */
    static List<Fit> fits(Path resources, double confidence, Map<String, FrameCut> cuts, List<String> pictures,
                          BufferedImage frame) {
        List<MatchResult> near = batch(resources, cuts, pictures, frame, true);
        List<String> away = new ArrayList<>();
        for (int i = 0; i < pictures.size(); i++) {
            if (!clears(near.get(i), confidence)) away.add(pictures.get(i));
        }
        List<MatchResult> anywhere = away.isEmpty() ? List.of() : batch(resources, cuts, away, frame, false);
        List<Fit> fits = new ArrayList<>();
        for (int i = 0, next = 0; i < pictures.size(); i++) {
            String picture = pictures.get(i);
            if (clears(near.get(i), confidence)) fits.add(new Fit(picture, Where.AT_PLACE, near.get(i).rect()));
            else {
                MatchResult best = anywhere.get(next++);
                fits.add(clears(best, confidence) ? new Fit(picture, Where.ELSEWHERE, best.rect())
                        : new Fit(picture, Where.MISSING, null));
            }
        }
        return fits;
    }

    private static boolean clears(MatchResult match, double confidence) {
        return match.isFound() && match.confidence() >= confidence;
    }

    /** Each picture's best match, near its place ({@code near}) or anywhere; not found when it has no file. */
    private static List<MatchResult> batch(Path resources, Map<String, FrameCut> cuts, List<String> pictures,
                                           BufferedImage frame, boolean near) {
        List<ImageTemplate> templates = new ArrayList<>();
        try {
            for (String picture : pictures) {
                FrameCut cut = cuts.get(picture);
                ImageTemplate file = new ImageTemplate(TemplateLibrary.fileForName(resources, picture).toString());
                if (near && cut != null && cut.place() != null) file = file.at(cut.place());
                templates.add(cut == null || cut.frame() == null ? file : file.in(cut.frame()));
            }
            StillSource still = new StillSource(frame);
            return ToolTiming.timed(ToolTiming.Phase.MATCH, () -> near
                    ? ImageFinder.bestMatchesNear(templates, LookTools.NEAR, still)
                    : ImageFinder.bestMatches(templates, still));
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
            boolean sure = !looksOnly.contains(input) && !its.comesAndGoes() && its.lock() == null && band == null
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
            if (its.lock() != null) add(checks, resources, its.lock(), false, false, null);
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
