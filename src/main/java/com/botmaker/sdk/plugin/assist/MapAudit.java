package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Variant;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.capture.StillSource;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * @param marker  whether it is the screen's marker
     * @param sure    whether the screen always shows it; one that comes and goes, a look's, a button's other look or a
     *                lock is not, and its absence is only a note
     * @param at      its box on the shot, in the shot's pixels; null when missing
     * @param place   where its place is on the shot, in the shot's pixels; null when it has none
     */
    public record Spot(String picture, boolean marker, boolean sure, Where where, Rect at, Rect place) {

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

        /** Whether the shot shows the screen's marker; true for a screen with none, which nothing tells apart. */
        public boolean showsMarker() {
            return spots.stream().filter(Spot::marker).allMatch(s -> s.where() != Where.MISSING);
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
                                     GameScreen screen) {
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
            spots.add(new Spot(fit.picture(), check.marker(), check.sure(), fit.where(), fit.at(),
                    placeOn(cuts.get(fit.picture()), live)));
        }
        Audit audit = new Audit(List.copyOf(spots));
        MADE.put(key.toString(), audit);
        return Optional.of(audit);
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

    /** A picture to look for on a screen's shot: whether it is the marker, and whether the screen always shows it. */
    private record Check(String picture, boolean marker, boolean sure) {
    }

    /**
     * Each picture {@code screen} names that has a file, by its file's name: its markers, its inputs' and its way
     * back's, then its looks' markers and inputs. A picture named twice is sure when either is. An absent picture is
     * not looked for: the shot not showing it is what it says.
     */
    private static Map<String, Check> checks(Path resources, GameScreen screen) {
        Map<String, Check> checks = new LinkedHashMap<>();
        for (ImageTemplate marker : screen.markers()) add(checks, resources, marker, true, true);
        List<Input> named = new ArrayList<>(screen.inputs());
        if (screen.back() != null) named.add(screen.back());
        for (Input input : named) {
            boolean sure = !input.comesAndGoes() && input.lock() == null && input.variants().isEmpty();
            for (Variant look : input.looks()) add(checks, resources, look.picture(), false, sure);
            if (input.lock() != null) add(checks, resources, input.lock(), false, false);
        }
        for (ScreenVariant look : screen.variants()) {
            for (ImageTemplate marker : look.markers()) add(checks, resources, marker, false, false);
            for (Input input : look.inputs()) {
                for (Variant button : input.looks()) add(checks, resources, button.picture(), false, false);
            }
        }
        return checks;
    }

    private static void add(Map<String, Check> checks, Path resources, ImageTemplate picture, boolean marker,
                            boolean sure) {
        Optional<String> file = Frames.picture(resources, picture.id());
        if (file.isEmpty()) return;
        Check was = checks.get(file.get());
        checks.put(file.get(), was == null ? new Check(file.get(), marker, sure)
                : new Check(file.get(), was.marker() || marker, was.sure() || sure));
    }

    /** {@code cut}'s place on a shot of {@code live}, scaled from the frame it was cut on; null when it has none. */
    private static Rect placeOn(FrameCut cut, Size live) {
        if (cut == null || cut.place() == null) return null;
        Rect place = cut.place();
        if (cut.frame() == null) return place;
        return Places.inSource(new Zone(place.x(), place.y(), place.width(), place.height()), cut.frame(), live);
    }

    private static long modified(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
        } catch (IOException gone) {
            return 0;
        }
    }
}
