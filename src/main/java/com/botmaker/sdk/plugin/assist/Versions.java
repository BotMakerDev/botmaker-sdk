package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.ButtonState;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.ShadeFit;
import com.botmaker.sdk.internal.screens.ShadeFit.Fit;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The versions of the bot's buttons Discovery deduces with no AI (2026-11-02): each button a frame shows is fitted
 * against its cut, and a fit off the cut's colours is a version of it, kept per button and frame. The same fit on most
 * of a frame's buttons is a shade over the whole screen (a tutorial's, a popup's), never a version. A fit seen alike on
 * two buttons or more is proposed as a shade, which {@code edit_map learn_shade} writes into {@code Sdk.shades()}; a
 * fit on one button only is said. A part the button shows and its cut lacks (a lock, a badge) is said with its place.
 */
public final class Versions {

    /** The most sightings kept per project, oldest dropped first. */
    static final int MOST_KEPT = 512;
    /** The fewest buttons shaded alike that tell a shade over the whole screen. */
    static final int SCREEN_LEAST = 3;
    private static final String MAGIC = "botmaker versions";
    private static final int LAYOUT = 1;
    private static final int LONGEST = 1 << 16;

    /** Each project's sightings, oldest first, read from its file the first time they are asked for. */
    private static final Map<Path, List<Sighting>> KEPT = new HashMap<>();

    private Versions() {}

    /** A button matched on a frame, at {@code at}, its shown pixels fitted against its cut. */
    record Seen(String picture, Rect at, Fit fit) {}

    /**
     * A frame read: the buttons each showing a version of its own, and the ones a shade over the whole screen changed
     * alike (none when the screen is not shaded).
     */
    record Sight(List<Seen> versions, List<Seen> screenWide) {}

    /**
     * A fit kept: on which picture, which frame (by a sample of its pixels), whether the whole screen was shaded alike.
     */
    record Sighting(String picture, int frame, boolean screen, Rect at, Fit fit) {}

    /**
     * A fit seen on {@code pictures}: the shade it makes, by the id {@code learn_shade} takes. {@code ids} holds each
     * sighting's own id too, so an id said before still names the group once its first sighting is dropped.
     */
    public record Proposal(String id, Shade shade, List<String> pictures, Set<String> ids) {

        /** Whether it was seen on two buttons or more, so it is a shade to learn. */
        public boolean shared() {
            return pictures.size() > 1;
        }
    }

    /** What 🗺 Screens' map panel lists under *Shades the game shows*: the fits proposed and the shades learned. */
    public record Shown(List<Proposal> proposed, List<Shade> learned) {}

    /** The project's proposals and learned shades, read off the FX thread (the sightings' file is read here). */
    public static Shown shown(StudioServices services) {
        Path resources = services.resourcesDir();
        if (resources == null) return new Shown(List.of(), List.of());
        List<Shade> learned = FxCall.call(() -> Frames.shades(services));
        return new Shown(proposals(kept(resources, MapAudit.store(resources, MapAudit.Kept.VERSIONS)), learned),
                learned.stream().filter(s -> s.cause() == Shade.Cause.GAME).toList());
    }

    /** {@code seen} read as one frame: the largest set of alike fits on more than half its buttons shades the screen. */
    static Sight read(List<Seen> seen) {
        List<Seen> off = seen.stream().filter(s -> s.fit().offIdentity()).toList();
        List<Seen> largest = List.of();
        for (Seen s : off) {
            List<Seen> alike = off.stream().filter(o -> o.fit().alike(s.fit())).toList();
            if (alike.size() > largest.size()) largest = alike;
        }
        if (largest.size() < SCREEN_LEAST || largest.size() * 2 <= seen.size()) return new Sight(off, List.of());
        List<Seen> wide = largest;
        return new Sight(off.stream().filter(s -> !wide.contains(s)).toList(), wide);
    }

    /**
     * The fits of {@code kept} that no shade of {@code learned} names, grouped alike, each with the shade it would
     * learn; a screen-wide one is never proposed. The shade is the group's first fit's, so its id holds while more
     * sightings come.
     */
    static List<Proposal> proposals(List<Sighting> kept, List<Shade> learned) {
        List<List<Sighting>> groups = new ArrayList<>();
        for (Sighting s : kept) {
            if (s.screen() || ShadeFit.closest(s.fit(), learned, Set.of(Shade.Cause.GAME)).isPresent()) continue;
            groups.stream().filter(g -> g.getFirst().fit().alike(s.fit())).findFirst()
                    .ifPresentOrElse(g -> g.add(s), () -> groups.add(new ArrayList<>(List.of(s))));
        }
        return groups.stream().map(g -> {
            Shade shade = learned(g.getFirst().fit());
            Set<String> ids = new LinkedHashSet<>();
            g.forEach(s -> ids.add(id(learned(s.fit()))));
            return new Proposal(id(shade), shade, g.stream().map(Sighting::picture).distinct().toList(), Set.copyOf(ids));
        }).toList();
    }

    private static Shade learned(Fit fit) {
        return fit.learned(Shade.Cause.GAME, meaning(fit.kind()));
    }

    /**
     * What a version of {@code kind} means until pressing it says otherwise: a dimmed, greyed or faded button is
     * {@code Disabled}, a brighter or tinted one {@code Selected}.
     */
    static State meaning(Shade.Kind kind) {
        return switch (kind) {
            case DIM, GREY, FADE -> State.DISABLED;
            default -> State.SELECTED;
        };
    }

    /** {@code shade}'s id: its kind and four hex digits of its numbers, {@code dim-3fa2}. */
    static String id(Shade shade) {
        int hash = 17;
        for (int c = 0; c < 3; c++) {
            hash = hash * 31 + Long.hashCode(Math.round(shade.gain().get(c) * 100));
            hash = hash * 31 + Long.hashCode(Math.round(shade.offset().get(c) * 100));
        }
        return String.format(Locale.ROOT, "%s-%04x", shade.kind().name().toLowerCase(Locale.ROOT), hash & 0xFFFF);
    }

    /**
     * Each of {@code found} — a picture's file name and where it matched — fitted on {@code frame}, read, kept, and said
     * for which_screen: blank when nothing is off its cut.
     */
    static String seen(AgentContext context, Path resources, Map<String, Rect> found, BufferedImage frame,
                       List<Shade> learned) {
        if (found.isEmpty()) return "";
        Map<String, Integer> frames;
        try {
            // A moving picture's frames side by side are not one wide cut.
            frames = Frames.frames(context, found.keySet());
        } catch (IllegalStateException busy) {
            return "";
        }
        List<Seen> seen = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, Rect> each : found.entrySet()) {
            ImageTemplate picture = new ImageTemplate(TemplateLibrary.fileForName(resources, each.getKey()).toString())
                    .frames(frames.getOrDefault(each.getKey(), 1));
            Optional<Fit> fit = ShadeFit.fit(picture, each.getValue(), frame);
            fit.ifPresent(f -> seen.add(new Seen(each.getKey(), each.getValue(), f)));
            if (fit.isEmpty() || !fit.get().offIdentity()) {
                ShadeFit.addition(picture, each.getValue(), frame).ifPresent(a -> added.add(each.getKey()
                        + " shows a part its cut lacks at " + Frames.box(a.area()) + " (a lock, a badge): a version of"
                        + " its own, cut once pressing it tells what it means."));
            }
        }
        Sight sight = read(seen);
        Path store = MapAudit.store(resources, MapAudit.Kept.VERSIONS);
        record(resources, store, frame, sight);
        return said(sight, seen.size(), proposals(kept(resources, store), learned), learned, added);
    }

    /** {@code sight} of a frame of {@code buttons} buttons as sentences, {@code added} after; blank for nothing. */
    static String said(Sight sight, int buttons, List<Proposal> proposals, List<Shade> learned, List<String> added) {
        List<String> lines = new ArrayList<>();
        if (!sight.screenWide().isEmpty()) {
            lines.add("The screen is " + sight.screenWide().getFirst().fit().kind().displayName() + " alike on "
                    + sight.screenWide().size() + " of " + buttons + " buttons: a shade over it (a tutorial's, a "
                    + "popup's), not a version of each.");
        }
        for (Seen version : sight.versions()) {
            if (ShadeFit.closest(version.fit(), learned, Set.of(Shade.Cause.GAME)).isPresent()) continue;
            Optional<Proposal> of = proposals.stream().filter(p -> p.pictures().contains(version.picture())
                    && p.shade().kind() == version.fit().kind() && fitsAlike(p, version.fit())).findFirst();
            String kind = version.fit().kind().displayName();
            lines.add(of.filter(Proposal::shared).map(p -> version.picture() + " shows " + kind + ", as "
                            + String.join(", ", p.pictures().stream().filter(n -> !n.equals(version.picture()))
                            .toList()) + " did: edit_map learn_shade fit " + p.id() + " keeps it, " + kind + " → "
                            + p.shade().state().displayName() + " for every button.")
                    .orElse(version.picture() + " shows " + kind + ", seen on no other button yet: a shade is "
                            + "learned once two buttons show it."));
        }
        lines.addAll(added);
        return String.join("\n", lines);
    }

    private static boolean fitsAlike(Proposal proposal, Fit fit) {
        Fit of = new Fit(proposal.shade().kind() == Shade.Kind.GREY, proposal.shade().gain(),
                proposal.shade().offset(), 0, null);
        return of.alike(fit);
    }

    /**
     * Writes the shade of the proposal {@code id} into {@code Sdk.shades()}, said in a sentence beginning
     * {@link ScreenEdits#NOT_WRITTEN} when it was not. On the FX thread.
     */
    static String learn(StudioServices services, String id) {
        return learn(services, id, null);
    }

    /**
     * {@link #learn(StudioServices, String)}, the shade meaning {@code meaning} in place of what its kind means
     * ({@link #meaning}); null keeps that. On the FX thread.
     */
    public static String learn(StudioServices services, String id, ButtonState meaning) {
        Path resources = services.resourcesDir();
        if (resources == null) return ScreenEdits.NOT_WRITTEN + Frames.noProject();
        List<Shade> learned = Frames.shades(services);
        String typed = id == null ? "" : id.strip();
        // A shared group first: two groups' ids may share their four digits.
        Optional<Proposal> proposal = proposals(kept(resources, MapAudit.store(resources, MapAudit.Kept.VERSIONS)),
                learned).stream().filter(p -> p.ids().contains(typed))
                .min(Comparator.comparing(p -> !p.shared()));
        if (proposal.isEmpty()) {
            return ScreenEdits.NOT_WRITTEN + "no fit " + id + " is proposed; which_screen names the fits it saw.";
        }
        if (!proposal.get().shared()) {
            return ScreenEdits.NOT_WRITTEN + "fit " + id + " was seen on " + proposal.get().pictures().getFirst()
                    + " only; a shade is learned once two buttons show it.";
        }
        Shade proposed = proposal.get().shade();
        Shade shade = meaning == null ? proposed : new Shade(proposed.kind(), proposed.gain(), proposed.offset(),
                proposed.tolerance(), proposed.cause(), meaning, proposed.geometry());
        List<Shade> all = new ArrayList<>(learned);
        all.add(shade);
        return Frames.writeShades(services, all).map(why -> ScreenEdits.NOT_WRITTEN + why)
                .orElse("Learned: every button shown " + shade.kind().displayName() + " is "
                        + shade.state().displayName() + " (seen on " + String.join(", ", proposal.get().pictures())
                        + "). Nothing else is cut.");
    }

    /**
     * Takes {@code shade} out of {@code Sdk.shades()}, so its fit is proposed again; said in a sentence beginning
     * {@link ScreenEdits#NOT_WRITTEN} when it was not. On the FX thread.
     */
    public static String unlearn(StudioServices services, Shade shade) {
        List<Shade> learned = new ArrayList<>(Frames.shades(services));
        if (!learned.remove(shade)) return ScreenEdits.NOT_WRITTEN + "that shade is learned no more.";
        return Frames.writeShades(services, learned).map(why -> ScreenEdits.NOT_WRITTEN + why)
                .orElse("Forgot: a button shown " + shade.kind().displayName() + " is no longer "
                        + shade.state().displayName() + ".");
    }

    /** Reads {@code resources}' sightings from their file now, off the FX thread, so {@link #learn} finds them read. */
    static void warm(Path resources) {
        if (resources != null) kept(resources, MapAudit.store(resources, MapAudit.Kept.VERSIONS));
    }

    /** {@code resources}' sightings, read from {@code store} the first time. */
    static synchronized List<Sighting> kept(Path resources, Path store) {
        return List.copyOf(KEPT.computeIfAbsent(resources, r -> new ArrayList<>(load(store))));
    }

    /** Keeps {@code sight} of {@code frame}, each button once per frame, and writes the project's file. */
    static synchronized void record(Path resources, Path store, BufferedImage frame, Sight sight) {
        if (sight.versions().isEmpty() && sight.screenWide().isEmpty()) return;
        List<Sighting> kept = KEPT.computeIfAbsent(resources, r -> new ArrayList<>(load(store)));
        int sample = sample(frame);
        boolean added = false;
        for (Seen s : sight.versions()) added |= add(kept, new Sighting(s.picture(), sample, false, s.at(), s.fit()));
        for (Seen s : sight.screenWide()) added |= add(kept, new Sighting(s.picture(), sample, true, s.at(), s.fit()));
        if (!added) return;
        while (kept.size() > MOST_KEPT) kept.removeFirst();
        try {
            save(store, kept);
        } catch (IOException unwritten) {
            // Kept in memory still: the next sighting writes them.
        }
    }

    /** Adds {@code sighting}, in place of the same button's on the same frame (a recut fits otherwise). */
    private static boolean add(List<Sighting> kept, Sighting sighting) {
        for (int i = 0; i < kept.size(); i++) {
            Sighting k = kept.get(i);
            if (!k.picture().equals(sighting.picture()) || k.frame() != sighting.frame()) continue;
            if (k.equals(sighting)) return false;
            kept.set(i, sighting);
            return true;
        }
        return kept.add(sighting);
    }

    /** Forgets every project's sightings, as a Studio started again: the tests start from it. */
    static synchronized void forget() {
        KEPT.clear();
    }

    /** A hash of 64 of {@code frame}'s pixels on a grid: the same frame read twice is one. */
    private static int sample(BufferedImage frame) {
        int hash = frame.getWidth() * 31 + frame.getHeight();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash = hash * 31 + frame.getRGB((2 * x + 1) * frame.getWidth() / 16,
                        (2 * y + 1) * frame.getHeight() / 16);
            }
        }
        return hash;
    }

    private static void save(Path file, List<Sighting> kept) throws IOException {
        Files.createDirectories(file.getParent());
        Path part = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".part");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(part)))) {
                out.writeUTF(MAGIC);
                out.writeInt(LAYOUT);
                out.writeInt(kept.size());
                for (Sighting s : kept) {
                    out.writeUTF(s.picture());
                    out.writeInt(s.frame());
                    out.writeBoolean(s.screen());
                    out.writeInt(s.at().x());
                    out.writeInt(s.at().y());
                    out.writeInt(s.at().width());
                    out.writeInt(s.at().height());
                    out.writeBoolean(s.fit().grey());
                    for (int c = 0; c < 3; c++) out.writeDouble(s.fit().gain().get(c));
                    for (int c = 0; c < 3; c++) out.writeDouble(s.fit().offset().get(c));
                    out.writeDouble(s.fit().error());
                }
            }
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /** What {@code file} holds; none when it is absent, broken or of another layout. */
    private static List<Sighting> load(Path file) {
        if (!Files.isRegularFile(file)) return List.of();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (!MAGIC.equals(in.readUTF()) || in.readInt() != LAYOUT) return List.of();
            int n = in.readInt();
            if (n < 0 || n > LONGEST) return List.of();
            List<Sighting> kept = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String picture = in.readUTF();
                int frame = in.readInt();
                boolean screen = in.readBoolean();
                Rect at = new Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt());
                boolean grey = in.readBoolean();
                Shade.Channels gain = new Shade.Channels(in.readDouble(), in.readDouble(), in.readDouble());
                Shade.Channels offset = new Shade.Channels(in.readDouble(), in.readDouble(), in.readDouble());
                kept.add(new Sighting(picture, frame, screen, at, new Fit(grey, gain, offset, in.readDouble(), null)));
            }
            return kept;
        } catch (IOException | RuntimeException broken) {
            return List.of();
        }
    }
}
