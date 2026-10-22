package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.text.OcrLanguage;
import com.botmaker.sdk.api.text.TextMatch;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.input.Hands;
import com.botmaker.sdk.internal.input.Hands.InSession;
import com.botmaker.sdk.internal.input.Hands.OnDesktop;
import com.botmaker.sdk.internal.input.Hands.OnEmulator;
import com.botmaker.sdk.internal.screens.Markers;
import com.botmaker.sdk.internal.screens.Reach;
import com.botmaker.sdk.internal.vision.FrameChange;
import com.botmaker.sdk.internal.vision.FrameChange.Clock;
import com.botmaker.sdk.internal.vision.FrameChange.Motion;
import com.botmaker.sdk.internal.vision.Images;
import com.botmaker.session.PrivateSession;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The assistant's hands: a click, a key or a scroll on the game the look tools see, each answered in text — the
 * pictures on the screen after it, and the text that appeared or went where the screen changed — so a step is
 * tried without editing the bot and without an image. {@code describe} is the same look without an action.
 *
 * <p>They act where {@link Frames#look} looks: the private display this project's launch brought up, else the
 * emulator the capture source names. On the user's own desktop only when the call says {@code desktop}: that is
 * the user's mouse and keyboard, and a tool must not take them unannounced.
 */
final class ActTools {

    static final int SETTLE_MS = 5_000;
    static final int LONGEST_SETTLE_MS = 10_000;
    /**
     * How long what moved on its own on a screen, read before one of try_input's inputs, stands for the next ones on
     * that screen: reading it again took half a second before each (2026-10-14).
     */
    static final long MOTION_REUSE_MS = 30_000;
    /** The box the text is read in grows by this much, so a line the change cut at its edge is read whole. */
    private static final int MARGIN = 12;
    private static final int MOST_LINES = 15;
    private static final int MOST_DESCRIBED = 60;
    private static final String SETTLE = "the longest the game is given to answer, in milliseconds: the screen is "
            + "read until it changes and holds still; 5000 when left out, at most 10000, 0 reads it at once";

    record Click(@Describe(value = "x, in the screenshot's pixels; with y", optional = true) Integer x,
                 @Describe(value = "y, in the screenshot's pixels", optional = true) Integer y,
                 @Describe(value = "a picture's name instead of x and y: its best match's centre, when a run "
                         + "would find it", optional = true) String picture,
                 @Describe(value = SETTLE, optional = true) Integer settleMs,
                 @Describe(value = "true adds a small image of the part that changed", optional = true)
                 Boolean show,
                 @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                         + "otherwise", optional = true) Boolean desktop) {
    }

    record Press(@Describe("the key, as printed on it or as Key names it: Escape, Q, F5, Page Up, 5") String key,
                 @Describe(value = SETTLE, optional = true) Integer settleMs,
                 @Describe(value = "true adds a small image of the part that changed", optional = true)
                 Boolean show,
                 @Describe(value = "true to act on the user's own desktop, where the key goes to whatever has "
                         + "focus; refused there otherwise", optional = true) Boolean desktop) {
    }

    record Wheel(@Describe(value = "x to scroll at, in the screenshot's pixels; with y", optional = true) Integer x,
                 @Describe(value = "y to scroll at", optional = true) Integer y,
                 @Describe(value = "a picture's name instead of x and y: scrolls over its best match, when a run "
                         + "would find it", optional = true) String picture,
                 @Describe("notches: positive scrolls up or away, negative down or toward you") int amount,
                 @Describe(value = SETTLE, optional = true) Integer settleMs,
                 @Describe(value = "true adds a small image of the part that changed", optional = true)
                 Boolean show,
                 @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                         + "otherwise", optional = true) Boolean desktop) {
    }

    record Part(@Describe(value = "left edge of the part to read; with y, width and height", optional = true)
                Integer x,
                @Describe(value = "top edge of the part", optional = true) Integer y,
                @Describe(value = "width of the part", optional = true) Integer width,
                @Describe(value = "height of the part", optional = true) Integer height) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("describe")
                    .describedAs("The screen as text: the pictures a run would find on it, and every line of "
                            + "text with where it is. The cheap way to look; a screenshot only to cut a picture")
                    .takes(Part.class).handledBy(ActTools::describe),
            AssistantTool.named("click")
                    .describedAs("Clicks the game at a point or on a picture, waits for it to answer, and says "
                            + "what changed: the pictures on screen after, the text that appeared and went")
                    .takes(Click.class).handledBy(ActTools::click),
            AssistantTool.named("key")
                    .describedAs("Taps a key on the game, waits for it to answer, and says what changed, as click")
                    .takes(Press.class).handledBy(ActTools::key),
            AssistantTool.named("scroll")
                    .describedAs("Scrolls the wheel over a point or a picture of the game, waits, and says what "
                            + "changed, as click")
                    .takes(Wheel.class).handledBy(ActTools::scroll));

    /** The motion last read before an input of try_input's, on {@code screen} at {@code atMs}. */
    record Ambient(String screen, Motion motion, long atMs) {

        /** Whether it stands for {@code screen} at {@code nowMs}, on a frame {@code width}×{@code height}. */
        boolean fits(String screen, long nowMs, int width, int height) {
            return this.screen.equals(screen) && nowMs - atMs < MOTION_REUSE_MS
                    && (motion == Motion.NONE || motion.width() == width && motion.height() == height);
        }
    }

    private static volatile Ambient ambient;

    private ActTools() {}

    static AgentReply describe(Part part, AgentContext context) {
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Optional<Rect> area = Frames.within(frame.get(), part.x(), part.y(), part.width(), part.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        List<String> said = new ArrayList<>();
        said.add("Screen: " + screen(context, frame.get()));
        List<Line> lines = lines(frame.get(), area.get());
        if (lines.isEmpty()) said.add("No text could be read there.");
        for (int i = 0; i < Math.min(lines.size(), MOST_DESCRIBED); i++) said.add(lines.get(i).said());
        if (lines.size() > MOST_DESCRIBED) said.add("… " + (lines.size() - MOST_DESCRIBED) + " more; give a part.");
        return AgentReply.text(String.join("\n", said));
    }

    static AgentReply click(Click click, AgentContext context) {
        if (click.picture() != null && !click.picture().isBlank()) {
            if (click.x() != null || click.y() != null) return AgentReply.refused("Give a picture or x and y, not both.");
            Spotted spotted = spot(click.picture(), "clicked", context);
            if (spotted.refusal() != null) return spotted.refusal();
            Point at = spotted.at();
            // The frame the picture was found on is the one the change is measured from: the click lands on it.
            return act(context, spotted.frame(), at, click.desktop(), click.settleMs(), click.show(),
                    "Clicked " + spotted.name() + " at " + at.x() + "," + at.y(), hands -> hands.click(at.x(), at.y()));
        }
        if (click.x() == null || click.y() == null) return AgentReply.refused("Give x and y, or a picture.");
        int x = click.x();
        int y = click.y();
        return act(context, null, new Point(x, y), click.desktop(), click.settleMs(), click.show(), "Clicked " + x + "," + y,
                hands -> hands.click(x, y));
    }

    static AgentReply key(Press press, AgentContext context) {
        Optional<Key> key = Key.named(press.key());
        if (key.isEmpty()) {
            return AgentReply.refused("\"" + press.key() + "\" is no key; name it as printed on it (Escape, Q, F5, "
                    + "Page Up) or as Key names it (PAGE_UP).");
        }
        return act(context, null, null, press.desktop(), press.settleMs(), press.show(), "Pressed " + key.get().label(),
                hands -> hands.key(key.get()));
    }

    static AgentReply scroll(Wheel wheel, AgentContext context) {
        if (wheel.amount() == 0) return AgentReply.refused("Scroll by at least one notch.");
        if (wheel.picture() != null && !wheel.picture().isBlank()) {
            if (wheel.x() != null || wheel.y() != null) return AgentReply.refused("Give a picture or x and y, not both.");
            Spotted spotted = spot(wheel.picture(), "scrolled", context);
            if (spotted.refusal() != null) return spotted.refusal();
            Point at = spotted.at();
            return act(context, spotted.frame(), at, wheel.desktop(), wheel.settleMs(), wheel.show(),
                    "Scrolled " + wheel.amount() + " at " + spotted.name() + " (" + at.x() + "," + at.y() + ")",
                    hands -> hands.scroll(at.x(), at.y(), wheel.amount()));
        }
        if (wheel.x() == null || wheel.y() == null) return AgentReply.refused("Give x and y, or a picture.");
        int x = wheel.x();
        int y = wheel.y();
        return act(context, null, new Point(x, y), wheel.desktop(), wheel.settleMs(), wheel.show(),
                "Scrolled " + wheel.amount() + " at " + x + "," + y, hands -> hands.scroll(x, y, wheel.amount()));
    }

    /** Where a picture's best match is on a fresh look, the frame it was found on, or the answer saying why not. */
    private record Spotted(BufferedImage frame, Point at, String name, AgentReply refusal) {
        static Spotted no(AgentReply refusal) {
            return new Spotted(null, null, null, refusal);
        }
    }

    /** {@code picture}'s best match, when a run would find it; {@code acted} says what was not done otherwise. */
    private static Spotted spot(String picture, String acted, AgentContext context) {
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return Spotted.no(AgentReply.refused(Frames.noProject()));
        Optional<String> base = Frames.picture(reach.get().resources(), picture);
        if (base.isEmpty()) return Spotted.no(AgentReply.refused(Frames.noPicture(picture)));
        Optional<BufferedImage> frame = reach.get().look();
        if (frame.isEmpty()) return Spotted.no(AgentReply.refused(Frames.noScreen()));
        MatchResult match = reach.get().best(base.get(), frame.get());
        if (!reach.get().clears(match)) {
            return Spotted.no(AgentReply.text(base.get() + " is not on the screen (best "
                    + (match.isFound() ? Frames.score(match.confidence()) : "none") + ", a run needs "
                    + Frames.score(reach.get().confidence()) + "); nothing was " + acted + "."));
        }
        return new Spotted(frame.get(), reach.get().pointOn(match.rect()), base.get(), null);
    }

    /**
     * The copy of {@code copy}'s button {@code pick} names, counted as a run counts them ({@link Reach#copies}: any
     * of its looks, along {@code strip} for a member of one), when a run would find that many; {@code picture} names
     * it in the reply.
     */
    private static Spotted spotCopy(String picture, Button copy, Pick pick, Strip strip, String acted,
                                    AgentContext context) {
        Optional<ToolReach> reach = ToolReach.of(context);
        if (reach.isEmpty()) return Spotted.no(AgentReply.refused(Frames.noProject()));
        Optional<String> base = Frames.picture(reach.get().resources(), picture);
        if (base.isEmpty()) return Spotted.no(AgentReply.refused(Frames.noPicture(picture)));
        Optional<BufferedImage> frame = reach.get().look();
        if (frame.isEmpty()) return Spotted.no(AgentReply.refused(Frames.noScreen()));
        int nth = Math.max(1, pick.nth());
        List<Rect> copies = reach.get().copies(copy, strip, pick.order(), frame.get()).stream()
                .map(Reach.Seen::place).toList();
        if (copies.size() < nth) {
            return Spotted.no(AgentReply.text(base.get() + " shows " + (copies.isEmpty() ? "nowhere" : copies.size()
                    + (copies.size() == 1 ? " time" : " times")) + " at a run's score " + Frames.score(reach.get()
                    .confidence()) + ", so it has no copy " + nth + "; nothing was " + acted + "."));
        }
        Rect at = copies.get(nth - 1);
        return new Spotted(frame.get(), reach.get().pointOn(at), base.get() + " #" + nth, null);
    }

    /** Why the game is not on {@code screen}, when its {@code marker} is not on a fresh look: try_input refuses. */
    static Optional<AgentReply> notOn(AgentContext context, String marker, String screen) {
        Spotted spotted = spot(marker, "done", context);
        if (spotted.refusal() == null) return Optional.empty();
        if (spotted.refusal().isRefused()) return Optional.of(spotted.refusal());
        return Optional.of(AgentReply.refused("The game is not on " + screen + ": " + spotted.refusal().parts()
                .getFirst().text() + " Go back to " + screen + " first."));
    }

    /** Whether the bot has a picture called {@code picture}. */
    static boolean exists(AgentContext context, String picture) {
        return Frames.resources(context).flatMap(r -> Frames.picture(r, picture)).isPresent();
    }

    /** Whether a run would tell {@code markers}' screen on {@code frame}: every marker found, no absent one. */
    static boolean shows(AgentContext context, Markers markers, BufferedImage frame) {
        return markers.on(p -> shows(context, p.id(), frame));
    }

    /** Whether a run would find {@code picture} on {@code frame}. */
    static boolean shows(AgentContext context, String picture, BufferedImage frame) {
        Optional<Path> resources = Frames.resources(context);
        Optional<String> base = resources.flatMap(r -> Frames.picture(r, picture));
        if (base.isEmpty()) return false;
        MatchResult match = LookTools.best(resources.get(), base.get(), frame);
        return match.isFound() && match.confidence() >= Frames.confidence(context.services());
    }

    /**
     * What an action did: the answer as click or key says it, and when it happened and the screen was read after
     * it, the frames either side and what moved on its own; {@code how} says it in the Map's words
     * ({@code click engineer_home}, {@code key Q}) and {@code done} in the answer's ({@code Clicked … at x,y}).
     */
    record Entered(AgentReply reply, BufferedImage before, BufferedImage after, Motion motion, String how,
                   String done) {
        static Entered no(AgentReply reply) {
            return new Entered(reply, null, null, Motion.NONE, null, null);
        }

        /** The share of the screen the action changed; 0 when it was not done or not read after. */
        double share() {
            return after == null ? 0 : FrameChange.share(before, after, motion);
        }
    }

    /**
     * Clicks {@code picture} (by {@code copy}'s gesture when it is a button's), scrolls {@code notches} over it when
     * they are not 0, or presses {@code keys} for try_input
     * on {@code screen}, waiting up to {@code settleMs} for a change as big as {@code share} of the screen, and
     * {@link FrameChange#QUIET_MS} when nothing moves at all. A {@code pick} clicks that copy of {@code copy}'s button
     * along {@code strip}. What moves on its own there is read again only past {@link #MOTION_REUSE_MS}.
     */
    static Entered enter(AgentContext context, String screen, String picture, int notches, Button copy, Pick pick,
                         Strip strip, Combo keys, Boolean desktop, int settleMs, double share) {
        if (keys != null) {
            return acted(context, screen, null, null, desktop, settleMs, false, "Pressed " + keys,
                    "key " + keys.caps(), share, hands -> hands.keys(keys));
        }
        String verb = notches == 0 ? "clicked" : "scrolled";
        Spotted spotted = copy == null || pick == null ? spot(picture, verb, context)
                : spotCopy(picture, copy, pick, strip, verb, context);
        if (spotted.refusal() != null) return Entered.no(spotted.refusal());
        Point at = spotted.at();
        if (notches != 0) {
            return acted(context, screen, spotted.frame(), at, desktop, settleMs, false, "Scrolled " + notches
                    + " at " + spotted.name() + " (" + at.x() + "," + at.y() + ")", "scroll " + spotted.name() + " "
                    + notches, share, hands -> hands.scroll(at.x(), at.y(), notches));
        }
        // A button of the map is pressed by its gesture, as a run presses it.
        Gesture gesture = copy == null ? Gesture.CLICK : copy.gesture();
        // A text field of the map types its text once clicked, as a run types it; one whose body gives it is clicked.
        if (copy != null && copy.text() != null) {
            String text = copy.text().text();
            return acted(context, screen, spotted.frame(), at, desktop, settleMs, false, "Typed \"" + text + "\" in "
                    + spotted.name() + " at " + at.x() + "," + at.y(), "type " + spotted.name(), share, hands -> {
                hands.press(at.x(), at.y(), gesture, copy.holdTime().toMillis());
                try {
                    Thread.sleep(Reach.FIELD_FOCUS_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                hands.type(text, copy.isClearing(), copy.isSubmitting());
            });
        }
        if (gesture != Gesture.CLICK) {
            long holdMs = copy.holdTime().toMillis();
            return acted(context, screen, spotted.frame(), at, desktop, settleMs, false, gesture.displayName() + " on "
                    + spotted.name() + " at " + at.x() + "," + at.y(), gesture.id() + " " + spotted.name(), share,
                    hands -> hands.press(at.x(), at.y(), gesture, holdMs));
        }
        return acted(context, screen, spotted.frame(), at, desktop, settleMs, false, "Clicked " + spotted.name()
                + " at " + at.x() + "," + at.y(), "click " + spotted.name(), share, hands -> hands.click(at.x(), at.y()));
    }

    /**
     * Does {@code action} with the hands the look tools' screen takes, waits {@code settleMs}, and says what changed
     * from {@code seen} (a frame already taken, or null to take one now). {@code at}, the point acted on, must be
     * on the frame; null for a key.
     */
    private static AgentReply act(AgentContext context, BufferedImage seen, Point at, Boolean desktop, Integer settleMs,
                                  Boolean show, String done, Action action) {
        return acted(context, null, seen, at, desktop, settleMs, show, done, null, FrameChange.ANSWER_SHARE, action)
                .reply();
    }

    /** As {@link #act}; {@code screen}, try_input's (null for the other tools), reuses its motion and waits less. */
    private static Entered acted(AgentContext context, String screen, BufferedImage seen, Point at, Boolean desktop,
                                 Integer settleMs, Boolean show, String done, String how, double share,
                                 Action action) {
        int settle = settleMs == null ? SETTLE_MS : settleMs;
        if (settle < 0 || settle > LONGEST_SETTLE_MS) {
            return Entered.no(AgentReply.refused("Settle between 0 and " + LONGEST_SETTLE_MS + " ms."));
        }
        Hands hands = hands(context);
        Optional<AgentReply> desktopRefused = desktopRefusal(context, hands, desktop);
        if (desktopRefused.isPresent()) return Entered.no(desktopRefused.get());
        Optional<BufferedImage> before = seen != null ? Optional.of(seen) : Frames.look(context);
        if (before.isEmpty()) return Entered.no(AgentReply.refused(Frames.noScreen()));
        // A point off the frame is off the game: on the desktop it would land on another window.
        if (at != null && (at.x() < 0 || at.y() < 0 || at.x() >= before.get().getWidth()
                || at.y() >= before.get().getHeight())) {
            return Entered.no(AgentReply.refused(at.x() + "," + at.y() + " is outside the "
                    + before.get().getWidth() + "×" + before.get().getHeight() + " screenshot; nothing was done."));
        }
        // The frame and the hands each ask where the game is: a display closing or opening between them would act
        // on another surface than the one read, or than the one the desktop check approved.
        if (hands(context).getClass() != hands.getClass()) {
            return Entered.no(AgentReply.refused("Where the game is changed just now; nothing was done. Look again."));
        }
        Ambient kept = ambient;
        long now = Clock.REAL.nowMs();
        Motion motion;
        BufferedImage last;
        if (screen != null && kept != null && kept.fits(screen, now, before.get().getWidth(), before.get().getHeight())) {
            motion = kept.motion();
            last = before.get();
        } else {
            List<BufferedImage> still = new ArrayList<>(List.of(before.get()));
            try {
                still.addAll(ToolTiming.timed(ToolTiming.Phase.MOTION, () -> FrameChange.ambient(
                        () -> Frames.look(context), Clock.REAL)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Entered.no(AgentReply.text("Stopped before acting; nothing was done."));
            }
            motion = Motion.of(still);
            last = still.getLast();
            if (screen != null) ambient = new Ambient(screen, motion, now);
        }
        int quiet = screen == null ? 0 : FrameChange.QUIET_MS;
        try {
            action.on(hands);
        } catch (UnsupportedOperationException cannot) {
            return Entered.no(AgentReply.refused(cannot.getMessage()));
        } catch (RuntimeException failed) {
            return Entered.no(AgentReply.refused("The input failed: " + failed.getMessage()));
        }
        // From here the action happened: an answer that is not a refusal, so it is not tried again.
        Optional<BufferedImage> after;
        try {
            after = ToolTiming.timed(ToolTiming.Phase.SETTLE,
                    () -> FrameChange.answered(last, motion, () -> Frames.look(context), settle, share, quiet,
                            Clock.REAL));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Entered.no(AgentReply.text(done + "; stopped before the screen was read again."));
        }
        if (after.isEmpty()) return Entered.no(AgentReply.text(done + "; the screen could not be captured after it."));
        // A screen the input changed may move otherwise now (a tab, a toggle): its motion is read again next time.
        if (screen != null && FrameChange.box(last, after.get(), motion).isPresent()) ambient = null;
        return new Entered(answer(context, done, last, after.get(), motion, Boolean.TRUE.equals(show)), last,
                after.get(), motion, how, done);
    }

    /**
     * {@code entered} read on for up to {@code capMs}, for a screen slow to draw: a small first change (a button's
     * press, a background swaying past the motion it was seen with) ends the wait before the screen it opens shows.
     * The later frame and its answer once {@code share} of the screen changed; else {@code entered} as it was; no
     * frame when stopped meanwhile.
     */
    static Entered later(AgentContext context, Entered entered, int capMs, double share) {
        try {
            return ToolTiming.timed(ToolTiming.Phase.SETTLE, () -> later(entered, () -> Frames.look(context), capMs,
                    share, Clock.REAL, after -> answer(context, entered.done(), entered.before(), after,
                            entered.motion(), false)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Entered.no(AgentReply.text(entered.done() + "; stopped before the screen was read again."));
        }
    }

    static Entered later(Entered entered, Supplier<Optional<BufferedImage>> look, int capMs, double share, Clock clock,
                         Function<BufferedImage, AgentReply> answer) throws InterruptedException {
        Optional<BufferedImage> after = FrameChange.answered(entered.before(), entered.motion(), look, capMs, share,
                clock);
        if (after.isEmpty() || FrameChange.share(entered.before(), after.get(), entered.motion()) < share) {
            return entered;
        }
        return new Entered(answer.apply(after.get()), entered.before(), after.get(), entered.motion(), entered.how(),
                entered.done());
    }

    /** {@code entered} read again on {@code after}, a later look: its answer said anew from that frame. */
    static Entered again(AgentContext context, Entered entered, BufferedImage after) {
        return new Entered(answer(context, entered.done(), entered.before(), after, entered.motion(), false),
                entered.before(), after, entered.motion(), entered.how(), entered.done());
    }

    /** What changed from {@code before} to {@code after}, in text, with the changed part's image when asked. */
    private static AgentReply answer(AgentContext context, String done, BufferedImage before, BufferedImage after,
                                     Motion motion, boolean show) {
        Optional<Rect> changed = FrameChange.box(before, after, motion);
        if (changed.isEmpty()) return AgentReply.text(done + "; nothing changed.");
        Rect box = changed.get();
        Rect read = box.expand(MARGIN).intersection(Images.bounds(after));
        List<String> said = new ArrayList<>();
        said.add(done + "; changed " + Frames.box(box) + ".");
        said.add("Screen: " + screen(context, after));
        TextDiff diff = TextDiff.of(lines(before, read), lines(after, read));
        if (diff.appeared().isEmpty() && diff.went().isEmpty()) said.add("No text appeared or went.");
        listed(said, "Appeared:", diff.appeared());
        listed(said, "Went:", diff.went());
        AgentReply text = AgentReply.text(String.join("\n", said));
        if (!show) return text;
        BufferedImage part = Images.cut(after, box);
        return text.and(AgentReply.image(Images.scaled(part, Frames.fit(part.getWidth(), part.getHeight(),
                Frames.BOXED_EDGE))));
    }

    private static void listed(List<String> said, String head, List<Line> lines) {
        if (lines.isEmpty()) return;
        said.add(head);
        for (int i = 0; i < Math.min(lines.size(), MOST_LINES); i++) said.add("  " + lines.get(i).said());
        if (lines.size() > MOST_LINES) said.add("  … " + (lines.size() - MOST_LINES) + " more; describe reads them.");
    }

    /**
     * The pictures a run would find on {@code frame}, best first, or the best that would not. Each is looked for near
     * where it was cut ({@link LookTools#scoresNear}), so one away from its place is not listed; which_screen looks
     * everywhere.
     */
    private static String screen(AgentContext context, BufferedImage frame) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return "no project is open.";
        // The pictures with a known place only: one with none is a whole-frame match, too dear for every answer.
        List<LookTools.Scored> scored = LookTools.scoresPlaced(resources.get(), frame, LookTools.cuts(context));
        double needs = Frames.confidence(context.services());
        List<String> on = new ArrayList<>();
        for (LookTools.Scored s : scored) {
            if (s.match().confidence() >= needs) on.add(s.name() + " at " + Frames.box(s.match().rect()));
        }
        if (!on.isEmpty()) return String.join(", ", on) + ".";
        return scored.isEmpty() ? "no picture matches at all." : "none of the pictures where they were cut"
                + " (best " + scored.getFirst().name() + " " + Frames.score(scored.getFirst().match().confidence())
                + ", a run needs " + Frames.score(needs) + "; which_screen with everywhere true looks all over).";
    }

    /** Every line the OCR reads in {@code area} of {@code frame}, in the frame's pixels. */
    private static List<Line> lines(BufferedImage frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        for (TextMatch m : LookTools.recognise(Images.cut(frame, area), OcrLanguage.ENGLISH)) {
            String text = m.text().strip();
            if (!text.isEmpty()) {
                Rect b = m.bounds();
                lines.add(new Line(text, new Rect(b.x() + area.x(), b.y() + area.y(), b.width(), b.height())));
            }
        }
        return lines;
    }

    /** One line of text read on the screen, in screenshot pixels. */
    record Line(String text, Rect bounds) {
        String said() {
            return "\"" + text + "\" at " + Frames.box(bounds);
        }
    }

    /**
     * The lines read after an action that were not there before, and the ones gone, each counted by its words: a
     * line that only moved is neither, and of two equal lines where one was, one appeared. Case and spacing are
     * not compared, since two readings of the same line differ in them.
     */
    record TextDiff(List<Line> appeared, List<Line> went) {

        static TextDiff of(List<Line> before, List<Line> after) {
            return new TextDiff(missing(after, before), missing(before, after));
        }

        /** The lines of {@code from} that {@code in} lacks, as many times as it lacks them. */
        private static List<Line> missing(List<Line> from, List<Line> in) {
            Map<String, Integer> left = new HashMap<>();
            for (Line line : in) left.merge(words(line), 1, Integer::sum);
            List<Line> out = new ArrayList<>();
            for (Line line : from) {
                if (left.merge(words(line), -1, Integer::sum) < 0) out.add(line);
            }
            return out;
        }

        private static String words(Line line) {
            return line.text().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }
    }

    @FunctionalInterface
    private interface Action {
        void on(Hands hands);
    }

    /** Why {@code hands} may not act: on the user's own desktop unless {@code desktop} says so, or with no place. */
    static Optional<AgentReply> desktopRefusal(AgentContext context, Hands hands, Boolean desktop) {
        if (hands instanceof OnDesktop && !Boolean.TRUE.equals(desktop)) {
            return Optional.of(AgentReply.refused("The game is on the user's own desktop, not in a private display: "
                    + "this would take their mouse and keyboard. launch_game puts it in one; desktop: true acts "
                    + "anyway."));
        }
        if (hands instanceof OnDesktop && context.watchedArea().isEmpty()) {
            return Optional.of(AgentReply.refused("Studio watches no window or area on this desktop, so a point has "
                    + "no place."));
        }
        return Optional.empty();
    }

    /** The hands for where {@link Frames#look} reads, in its order. */
    static Hands hands(AgentContext context) {
        PrivateSession session = Frames.session(context);
        if (session != null) return new InSession(session);
        CaptureSource emulator = Frames.emulator(context);
        if (emulator != null) return new OnEmulator(emulator);
        return new OnDesktop(context.watchedArea().map(at -> new Point(at.x(), at.y())).orElse(new Point(0, 0)));
    }
}
