package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Shade;
import com.botmaker.sdk.api.screens.State;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.input.Hands;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.screens.ShadeFit;
import com.botmaker.sdk.internal.screens.ShadeFit.Fit;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * How the game's buttons answer the bot's own input (phase 5c, 2026-11-02): {@code edit_map learn_interactions} and
 * 🗺 Screens' *How buttons answer the bot* hover two buttons of the screen shown, hold each mid-press and Tab focus,
 * each time fitting the buttons against their cuts. A change both show alike is proposed as a {@code HOVER},
 * {@code PRESS} or {@code FOCUS} shade meaning {@code Normal}, which a run applies only to the button its input is on;
 * {@code learn_shade} keeps one. Nothing is written by trying. An emulator has no pointer to hover nor keys to Tab:
 * only the press is tried there.
 */
public final class Interactions {

    /** How long a hover or a focus is given to show before the frame is read. */
    static final long SHOW_MS = 300;
    /** How long a press is held, from its button's centre to off it, and when in it the frame is read. */
    static final long HOLD_MS = 900;
    static final long MID_HOLD_MS = 300;
    /** How far past the button's box the press is released, so it presses nothing. */
    static final int PAST = 12;
    /** The Tabs pressed: two buttons focused in turn. */
    static final int TABS = 2;

    /** Each project's last try, by its resources folder: the proposals {@code learn_shade} keeps. */
    private static final Map<Path, Tried> TRIED = new HashMap<>();

    private Interactions() {}

    /** A button of the screen on the frame, shown as cut: its field, its picture and where it matched. */
    record Spot(String field, String picture, Rect at) {}

    /**
     * What one input did: of {@code cause}, the shade proposed by {@code id} (both null when none), the buttons that
     * showed it, and in words what came.
     */
    public record Answer(Shade.Cause cause, String id, Shade shade, List<String> pictures, String said) {

        /** Whether it proposes a shade to keep. */
        public boolean keeps() {
            return shade != null;
        }
    }

    /** A try on {@code screen}: one answer per cause tried or skipped, and a sentence when it stopped early. */
    public record Tried(String screen, List<Answer> answers, String stopped) {}

    /**
     * Tries the bot's input on the screen {@code screen} the game shows, off the FX thread; {@code desktop} allows
     * acting on the user's own desktop (▶'s hand path; the assistant may not). Refused (a {@code Tried} with no
     * answers and why in {@code stopped}) when nothing could be tried.
     */
    public static Tried tryOn(AgentContext context, String screen, boolean desktop) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return refused(screen, Frames.noProject());
        Hands hands = ActTools.hands(context);
        Optional<AgentReply> refusal = ActTools.desktopRefusal(context, hands, desktop);
        if (refusal.isPresent()) {
            // The assistant's call takes no desktop flag: on the user's desktop it is the author's, by hand.
            return refused(screen, hands instanceof Hands.OnDesktop && !desktop ? "The game is on the user's own "
                    + "desktop: learn_interactions would take their mouse and keyboard. launch_game puts it in a "
                    + "private display, or the author presses Try on this screen in 🗺 Screens. Nothing was done."
                    : refusal.get().parts().getFirst().text());
        }
        ScreenGraph graph = FxCall.call(() -> ScreenBook.read(context.services()));
        Optional<String> constant = ScreenBook.find(graph, screen);
        if (constant.isEmpty()) return refused(screen, "No screen " + screen + " in the bot's Screens.");
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return refused(constant.get(), Frames.noScreen());
        List<Spot> spots = spots(context, resources.get(), graph.screen(constant.get()).orElseThrow(), frame.get());
        if (spots.size() < 2) {
            return refused(constant.get(), "Two buttons of " + graph.shown(constant.get()) + " shown as cut are "
                    + "needed, " + spots.size() + " found on the game: go_to_screen it first. Nothing was done.");
        }
        boolean emulator = hands instanceof Hands.OnEmulator;
        List<Answer> answers = new ArrayList<>();
        String stopped = null;
        for (Shade.Cause cause : List.of(Shade.Cause.HOVER, Shade.Cause.PRESS, Shade.Cause.FOCUS)) {
            if (emulator && cause != Shade.Cause.PRESS) {
                answers.add(new Answer(cause, null, null, List.of(), "skipped: an emulator has no "
                        + (cause == Shade.Cause.HOVER ? "pointer to hover" : "keys to Tab")));
                continue;
            }
            List<Versions.Seen> seen;
            try {
                seen = acted(context, resources.get(), hands, cause, spots, frame.get().getWidth());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopped = "Stopped while trying.";
                break;
            } catch (RuntimeException failed) {
                // As ActTools says a failed input: what the other causes answered is kept all the same.
                answers.add(new Answer(cause, null, null, List.of(), "not tried: " + failed.getMessage()));
                continue;
            }
            if (seen == null) {
                stopped = "The screen changed while trying " + label(cause).toLowerCase(Locale.ROOT)
                        + ": the game took it as a press. What came before is kept to learn.";
                break;
            }
            answers.add(answer(cause, seen));
        }
        Tried tried = new Tried(constant.get(), List.copyOf(answers), stopped);
        synchronized (TRIED) {
            TRIED.put(resources.get(), tried);
        }
        return tried;
    }

    /**
     * {@code cause} done on {@code spots} in turn, each fitted while it is on; null when the first two spots are not
     * found again after it (the game answered as to a press).
     */
    private static List<Versions.Seen> acted(AgentContext context, Path resources, Hands hands, Shade.Cause cause,
                                             List<Spot> spots, int width) throws InterruptedException {
        List<Versions.Seen> seen = new ArrayList<>();
        // Parked off every button, so a hover a press left on a neighbour is not taken for a focus.
        if (cause == Shade.Cause.FOCUS) hands.press(0, 0, Gesture.HOVER, 0);
        switch (cause) {
            case HOVER -> {
                for (Spot spot : spots.subList(0, 2)) {
                    hands.press(centreX(spot.at()), centreY(spot.at()), Gesture.HOVER, 0);
                    Thread.sleep(SHOW_MS);
                    fitted(context, resources, spot).ifPresent(seen::add);
                }
                // Parked off both, so no hover lingers on what follows.
                hands.press(0, 0, Gesture.HOVER, 0);
            }
            case PRESS -> {
                for (Spot spot : spots.subList(0, 2)) {
                    Rect at = spot.at();
                    int off = at.x() + at.width() + PAST < width ? at.x() + at.width() + PAST
                            : Math.max(0, at.x() - PAST);
                    RuntimeException[] failed = {null};
                    Thread held = Thread.ofVirtual().name("interaction-press").start(() -> {
                        try {
                            hands.drag(centreX(at), centreY(at), off, centreY(at), HOLD_MS);
                        } catch (RuntimeException e) {
                            failed[0] = e;
                        }
                    });
                    try {
                        Thread.sleep(MID_HOLD_MS);
                        fitted(context, resources, spot).ifPresent(seen::add);
                    } finally {
                        // Never left holding the mouse: the drag ends before anything else, a stop too.
                        held.join();
                    }
                    if (failed[0] != null) throw failed[0];
                    if (!stays(context, resources, spots)) return null;
                }
            }
            case FOCUS -> {
                for (int tab = 0; tab < TABS; tab++) {
                    hands.key(Key.TAB);
                    Thread.sleep(SHOW_MS);
                    if (!stays(context, resources, spots)) return null;
                    // The focused one is whichever shows off its cut, if any does.
                    for (Spot spot : spots) {
                        Optional<Versions.Seen> fit = fitted(context, resources, spot)
                                .filter(f -> f.fit().offIdentity());
                        if (fit.isPresent()) {
                            seen.add(fit.get());
                            break;
                        }
                    }
                }
            }
            case GAME -> {
            }
        }
        return stays(context, resources, spots) ? seen : null;
    }

    /** The screen's picture buttons found on {@code frame} and shown as cut, in the order the screen declares them. */
    static List<Spot> spots(AgentContext context, Path resources, MapScreen screen, BufferedImage frame) {
        List<Spot> spots = new ArrayList<>();
        double needs = Frames.confidence(context.services());
        for (Map.Entry<String, Input> input : screen.inputs().entrySet()) {
            if (!(input.getValue() instanceof Button button) || button.picture() == null) continue;
            Optional<String> base = Frames.picture(resources, button.picture().id());
            if (base.isEmpty() || spots.stream().anyMatch(s -> s.picture().equals(base.get()))) continue;
            MatchResult match = LookTools.best(context, resources, base.get(), frame);
            if (!match.isFound() || match.confidence() < needs) continue;
            Spot spot = new Spot(input.getKey(), base.get(), match.rect());
            if (fit(context, resources, spot, frame).filter(Fit::asCut).isPresent()) spots.add(spot);
        }
        return spots;
    }

    /** {@code spot} fitted on a frame read now. */
    private static Optional<Versions.Seen> fitted(AgentContext context, Path resources, Spot spot) {
        return Frames.look(context).flatMap(frame -> fit(context, resources, spot, frame))
                .map(fit -> new Versions.Seen(spot.picture(), spot.at(), fit));
    }

    private static Optional<Fit> fit(AgentContext context, Path resources, Spot spot, BufferedImage frame) {
        ImageTemplate picture = new ImageTemplate(TemplateLibrary.fileForName(resources, spot.picture()).toString())
                .frames(Frames.frames(context, spot.picture()));
        return ShadeFit.fit(picture, spot.at(), frame);
    }

    /**
     * Whether the game stayed on the screen: one of the first two of {@code spots} found as a run finds it where it
     * was, the other best matched there too. The other may show a focus or press look, so its score is not held to.
     */
    private static boolean stays(AgentContext context, Path resources, List<Spot> spots) {
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return false;
        double needs = Frames.confidence(context.services());
        int sure = 0;
        for (Spot spot : spots.subList(0, 2)) {
            MatchResult match = LookTools.best(context, resources, spot.picture(), frame.get());
            if (!match.isFound() || !near(match.rect(), spot.at())) return false;
            if (match.confidence() >= needs) sure++;
        }
        return sure > 0;
    }

    private static boolean near(Rect a, Rect b) {
        return Math.abs(a.x() - b.x()) <= Versions.OWN_MARGIN && Math.abs(a.y() - b.y()) <= Versions.OWN_MARGIN;
    }

    /**
     * What {@code cause} showed on {@code seen}: a shade meaning {@code Normal} when two different buttons changed
     * alike; else said why none is proposed.
     */
    static Answer answer(Shade.Cause cause, List<Versions.Seen> seen) {
        List<Versions.Seen> changed = seen.stream().filter(s -> s.fit().offIdentity()).toList();
        if (changed.isEmpty()) return new Answer(cause, null, null, List.of(), "no button changed");
        for (Versions.Seen first : changed) {
            List<String> alike = changed.stream().filter(s -> s.fit().alike(first.fit())).map(Versions.Seen::picture)
                    .distinct().toList();
            if (alike.size() > 1) {
                Shade shade = first.fit().learned(cause, State.NORMAL);
                return new Answer(cause, Versions.id(shade).replaceFirst("^[a-z]+", cause.name()
                        .toLowerCase(Locale.ROOT)), shade, alike, shade.kind().displayName() + " on "
                        + String.join(", ", alike));
            }
        }
        List<String> pictures = changed.stream().map(Versions.Seen::picture).distinct().toList();
        return new Answer(cause, null, null, pictures, pictures.size() == 1 ? "only " + pictures.getFirst()
                + " changed" : String.join(" and ", pictures) + " changed unlike");
    }

    /** The answer {@code id} of the project's last try, a shade to keep; empty when no try proposed it. */
    static Optional<Answer> proposed(Path resources, String id) {
        synchronized (TRIED) {
            Tried tried = TRIED.get(resources);
            if (tried == null || id == null) return Optional.empty();
            return tried.answers().stream().filter(a -> a.keeps() && a.id().equals(id.strip())).findFirst();
        }
    }

    /** The project's last try, for 🗺 Screens; empty when none ran since Studio started. */
    public static Optional<Tried> last(Path resources) {
        synchronized (TRIED) {
            return Optional.ofNullable(resources == null ? null : TRIED.get(resources));
        }
    }

    /** The shades of the bot's own input it learned, for 🗺 Screens. On the FX thread. */
    public static List<Shade> learned(StudioServices services) {
        return Frames.shades(services).stream().filter(s -> s.cause() != Shade.Cause.GAME).toList();
    }

    /** Forgets every try: tests, and a project closed. */
    static void forget() {
        synchronized (TRIED) {
            TRIED.clear();
        }
    }

    /** Stores {@code tried} as {@code resources}' last try: tests. */
    static void remember(Path resources, Tried tried) {
        synchronized (TRIED) {
            TRIED.put(resources, tried);
        }
    }

    /** {@code tried} in words, for the assistant: each cause on its line, with the learn_shade call to keep it. */
    static String said(Tried tried) {
        if (tried.answers().isEmpty()) return tried.stopped();
        List<String> lines = new ArrayList<>();
        for (Answer answer : tried.answers()) {
            lines.add(label(answer.cause()) + ": " + answer.said() + (answer.keeps() ? " — learn_shade fit "
                    + answer.id() + " keeps it (Normal)" : ""));
        }
        if (tried.stopped() != null) lines.add(tried.stopped());
        lines.add("Nothing was written; ask the author which to keep, a change on one button may be an animation.");
        return String.join("\n", lines);
    }

    /** {@code cause} as 🗺 Screens and the assistant say it. */
    public static String label(Shade.Cause cause) {
        return switch (cause) {
            case HOVER -> "Hover";
            case PRESS -> "Press";
            case FOCUS -> "Focus";
            case GAME -> "The game";
        };
    }

    private static Tried refused(String screen, String why) {
        return new Tried(screen, List.of(), why);
    }

    private static int centreX(Rect r) {
        return r.x() + r.width() / 2;
    }

    private static int centreY(Rect r) {
        return r.y() + r.height() / 2;
    }
}
