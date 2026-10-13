package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.input.MouseButton;
import com.botmaker.sdk.api.text.OcrLanguage;
import com.botmaker.sdk.api.text.TextMatch;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.session.PointerPolicy;
import com.botmaker.session.PrivateSession;
import com.botmaker.shared.capture.GenericWindow;
import com.botmaker.shared.capture.NativeController;
import com.botmaker.shared.capture.NativeControllerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

    static final int SETTLE_MS = 800;
    static final int LONGEST_SETTLE_MS = 5_000;
    /** A channel moving less than this is the same pixel: compression, a blended edge. */
    private static final int SAME = 24;
    /** Every this many pixels is compared; a change smaller than this is not a step's answer. */
    private static final int STEP = 2;
    /** The box the text is read in grows by this much, so a line the change cut at its edge is read whole. */
    private static final int MARGIN = 12;
    private static final int MOST_LINES = 15;
    private static final int MOST_DESCRIBED = 60;
    private static final long KEY_HOLD_MS = 40;

    record Click(@Describe(value = "x, in the screenshot's pixels; with y", optional = true) Integer x,
                 @Describe(value = "y, in the screenshot's pixels", optional = true) Integer y,
                 @Describe(value = "a picture's name instead of x and y: its best match's centre, when a run "
                         + "would find it", optional = true) String picture,
                 @Describe(value = "how long the game is given to answer before the screen is read again, in "
                         + "milliseconds; 800 when left out, at most 5000", optional = true) Integer settleMs,
                 @Describe(value = "true adds a small image of the part that changed", optional = true)
                 Boolean show,
                 @Describe(value = "true to act on the user's own desktop, taking their mouse; refused there "
                         + "otherwise", optional = true) Boolean desktop) {
    }

    record Press(@Describe("the key, as printed on it or as Key names it: Escape, Q, F5, Page Up, 5") String key,
                 @Describe(value = "milliseconds before the screen is read again; 800 when left out, at most 5000",
                         optional = true) Integer settleMs,
                 @Describe(value = "true adds a small image of the part that changed", optional = true)
                 Boolean show,
                 @Describe(value = "true to act on the user's own desktop, where the key goes to whatever has "
                         + "focus; refused there otherwise", optional = true) Boolean desktop) {
    }

    record Wheel(@Describe("x to scroll at, in the screenshot's pixels") int x,
                 @Describe("y to scroll at") int y,
                 @Describe("notches: positive scrolls up or away, negative down or toward you") int amount,
                 @Describe(value = "milliseconds before the screen is read again; 800 when left out, at most 5000",
                         optional = true) Integer settleMs,
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
                    .describedAs("Scrolls the wheel over a point of the game, waits, and says what changed, as "
                            + "click")
                    .takes(Wheel.class).handledBy(ActTools::scroll));

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
            Optional<Path> resources = Frames.resources(context);
            if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
            Optional<String> base = Frames.picture(resources.get(), click.picture());
            if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(click.picture()));
            Optional<BufferedImage> frame = Frames.look(context);
            if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
            MatchResult match = LookTools.best(resources.get(), base.get(), frame.get());
            double needs = Frames.confidence(context.services());
            if (!match.isFound() || match.confidence() < needs) {
                return AgentReply.text(base.get() + " is not on the screen (best "
                        + (match.isFound() ? Frames.score(match.confidence()) : "none") + ", a run needs "
                        + Frames.score(needs) + "); nothing was clicked.");
            }
            Point at = match.rect().center();
            // The frame the picture was found on is the one the change is measured from: the click lands on it.
            return act(context, frame.get(), click.desktop(), click.settleMs(), click.show(),
                    "Clicked " + base.get() + " at " + at.x() + "," + at.y(), hands -> hands.click(at.x(), at.y()));
        }
        if (click.x() == null || click.y() == null) return AgentReply.refused("Give x and y, or a picture.");
        int x = click.x();
        int y = click.y();
        return act(context, null, click.desktop(), click.settleMs(), click.show(), "Clicked " + x + "," + y,
                hands -> hands.click(x, y));
    }

    static AgentReply key(Press press, AgentContext context) {
        Optional<Key> key = key(press.key());
        if (key.isEmpty()) {
            return AgentReply.refused("\"" + press.key() + "\" is no key; name it as printed on it (Escape, Q, F5, "
                    + "Page Up) or as Key names it (PAGE_UP).");
        }
        return act(context, null, press.desktop(), press.settleMs(), press.show(), "Pressed " + key.get().label(),
                hands -> hands.key(key.get()));
    }

    static AgentReply scroll(Wheel wheel, AgentContext context) {
        if (wheel.amount() == 0) return AgentReply.refused("Scroll by at least one notch.");
        return act(context, null, wheel.desktop(), wheel.settleMs(), wheel.show(),
                "Scrolled " + wheel.amount() + " at " + wheel.x() + "," + wheel.y(),
                hands -> hands.scroll(wheel.x(), wheel.y(), wheel.amount()));
    }

    /**
     * Does {@code action} with the hands the look tools' screen takes, waits {@code settleMs}, and says what changed
     * from {@code seen} (a frame already taken, or null to take one now).
     */
    private static AgentReply act(AgentContext context, BufferedImage seen, Boolean desktop, Integer settleMs,
                                  Boolean show, String done, Action action) {
        int settle = settleMs == null ? SETTLE_MS : settleMs;
        if (settle < 0 || settle > LONGEST_SETTLE_MS) {
            return AgentReply.refused("Settle between 0 and " + LONGEST_SETTLE_MS + " ms.");
        }
        Hands hands = hands(context);
        if (hands instanceof OnDesktop && !Boolean.TRUE.equals(desktop)) {
            return AgentReply.refused("The game is on the user's own desktop, not in a private display: this would "
                    + "take their mouse and keyboard. launch_game puts it in one; desktop: true acts anyway.");
        }
        if (hands instanceof OnDesktop && context.watchedArea().isEmpty()) {
            return AgentReply.refused("Studio watches no window or area on this desktop, so a point has no place.");
        }
        Optional<BufferedImage> before = seen != null ? Optional.of(seen) : Frames.look(context);
        if (before.isEmpty()) return AgentReply.refused(Frames.noScreen());
        // The frame and the hands each ask where the game is: a display closing or opening between them would act
        // on another surface than the one read, or than the one the desktop check approved.
        if (hands(context).getClass() != hands.getClass()) {
            return AgentReply.refused("Where the game is changed just now; nothing was done. Look again.");
        }
        try {
            action.on(hands);
        } catch (UnsupportedOperationException cannot) {
            return AgentReply.refused(cannot.getMessage());
        } catch (RuntimeException failed) {
            return AgentReply.refused("The input failed: " + failed.getMessage());
        }
        // From here the action happened: an answer that is not a refusal, so it is not tried again.
        try {
            Thread.sleep(settle);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentReply.text(done + "; stopped before the screen was read again.");
        }
        Optional<BufferedImage> after = Frames.look(context);
        if (after.isEmpty()) return AgentReply.text(done + "; the screen could not be captured after it.");
        return answer(context, done, before.get(), after.get(), Boolean.TRUE.equals(show));
    }

    /** What changed from {@code before} to {@code after}, in text, with the changed part's image when asked. */
    private static AgentReply answer(AgentContext context, String done, BufferedImage before, BufferedImage after,
                                     boolean show) {
        Optional<Rect> changed = changed(before, after);
        if (changed.isEmpty()) return AgentReply.text(done + "; nothing changed.");
        Rect box = changed.get();
        Rect read = grown(box, MARGIN, after);
        List<String> said = new ArrayList<>();
        said.add(done + "; changed " + Frames.box(box) + ".");
        said.add("Screen: " + screen(context, after));
        TextDiff diff = TextDiff.of(lines(before, read), lines(after, read));
        if (diff.appeared().isEmpty() && diff.went().isEmpty()) said.add("No text appeared or went.");
        listed(said, "Appeared:", diff.appeared());
        listed(said, "Went:", diff.went());
        AgentReply text = AgentReply.text(String.join("\n", said));
        if (!show) return text;
        BufferedImage part = Frames.cut(after, box);
        return text.and(AgentReply.image(Frames.scaled(part, Frames.fit(part.getWidth(), part.getHeight(),
                Frames.BOXED_EDGE))));
    }

    private static void listed(List<String> said, String head, List<Line> lines) {
        if (lines.isEmpty()) return;
        said.add(head);
        for (int i = 0; i < Math.min(lines.size(), MOST_LINES); i++) said.add("  " + lines.get(i).said());
        if (lines.size() > MOST_LINES) said.add("  … " + (lines.size() - MOST_LINES) + " more; describe reads them.");
    }

    /** The pictures a run would find on {@code frame}, best first, or the best that would not. */
    private static String screen(AgentContext context, BufferedImage frame) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return "no project is open.";
        List<LookTools.Scored> scored = LookTools.scores(resources.get(), frame);
        double needs = Frames.confidence(context.services());
        List<String> on = new ArrayList<>();
        for (LookTools.Scored s : scored) {
            if (s.match().confidence() >= needs) on.add(s.name() + " at " + Frames.box(s.match().rect()));
        }
        if (!on.isEmpty()) return String.join(", ", on) + ".";
        return scored.isEmpty() ? "no picture matches at all." : "none of the pictures (best " + scored.getFirst().name()
                + " " + Frames.score(scored.getFirst().match().confidence()) + ", a run needs " + Frames.score(needs)
                + ").";
    }

    /** Every line the OCR reads in {@code area} of {@code frame}, in the frame's pixels. */
    private static List<Line> lines(BufferedImage frame, Rect area) {
        List<Line> lines = new ArrayList<>();
        for (TextMatch m : LookTools.recognise(Frames.cut(frame, area), OcrLanguage.ENGLISH)) {
            String text = m.text().strip();
            if (!text.isEmpty()) {
                Rect b = m.bounds();
                lines.add(new Line(text, new Rect(b.x() + area.x(), b.y() + area.y(), b.width(), b.height())));
            }
        }
        return lines;
    }

    /** {@code box} grown by {@code by} on each side, kept inside {@code frame}. */
    private static Rect grown(Rect box, int by, BufferedImage frame) {
        int left = Math.max(0, box.x() - by);
        int top = Math.max(0, box.y() - by);
        int right = Math.min(frame.getWidth(), box.x() + box.width() + by);
        int bottom = Math.min(frame.getHeight(), box.y() + box.height() + by);
        return new Rect(left, top, right - left, bottom - top);
    }

    /**
     * The box around every pixel that moved from {@code before} to {@code after}, or empty when none did. Another
     * size is the whole of {@code after}: the window was resized or replaced.
     */
    static Optional<Rect> changed(BufferedImage before, BufferedImage after) {
        if (before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight()) {
            return Optional.of(new Rect(0, 0, after.getWidth(), after.getHeight()));
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < after.getHeight(); y += STEP) {
            for (int x = 0; x < after.getWidth(); x += STEP) {
                if (same(before.getRGB(x, y), after.getRGB(x, y))) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < 0) return Optional.empty();
        int width = Math.min(after.getWidth(), right + STEP) - left;
        int height = Math.min(after.getHeight(), bottom + STEP) - top;
        return Optional.of(new Rect(left, top, width, height));
    }

    private static boolean same(int a, int b) {
        return Math.abs((a >> 16 & 0xff) - (b >> 16 & 0xff)) < SAME
                && Math.abs((a >> 8 & 0xff) - (b >> 8 & 0xff)) < SAME
                && Math.abs((a & 0xff) - (b & 0xff)) < SAME;
    }

    /**
     * The key {@code typed} names: as {@link Key} names it ({@code PAGE_UP}, {@code page up}), as printed on it
     * ({@code Esc}, {@code [}), or a digit ({@code 5} is the top row's); empty for anything else.
     */
    static Optional<Key> key(String typed) {
        if (typed == null || typed.isBlank()) return Optional.empty();
        String text = typed.strip();
        if (text.length() == 1 && text.charAt(0) >= '0' && text.charAt(0) <= '9') return Optional.of(Key.valueOf("NUM" + text));
        String name = text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Key key : Key.values()) {
            if (key.name().equals(name) || key.label().equalsIgnoreCase(text)) return Optional.of(key);
        }
        return Optional.empty();
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

    /** Input where the look tools look, in the screenshot's pixels. */
    private sealed interface Hands permits InSession, OnEmulator, OnDesktop {
        void click(int x, int y);

        void key(Key key);

        void scroll(int x, int y, int amount);
    }

    /** The hands for where {@link Frames#look} reads, in its order. */
    private static Hands hands(AgentContext context) {
        PrivateSession session = Frames.session(context);
        if (session != null) return new InSession(session);
        CaptureSource emulator = Frames.emulator(context);
        if (emulator != null) return new OnEmulator(emulator);
        return new OnDesktop(context.watchedArea().orElse(new Area(0, 0, 0, 0)));
    }

    /** The private display's own pointer and keyboard, as a run's {@code Mouse} and {@code Keyboard} use them. */
    private record InSession(PrivateSession session) implements Hands {

        @Override
        public void click(int x, int y) {
            Point o = origin();
            PointerPolicy.click(session.controller(), session, o.x() + x, o.y() + y, MouseButton.LEFT.code());
        }

        @Override
        public void key(Key key) {
            NativeController controller = session.controller();
            GenericWindow window = session.attached();
            if (window == null) controller.keyDown(key.nativeCode());
            else controller.keyDown(window, key.nativeCode());
            hold();
            if (window == null) controller.keyUp(key.nativeCode());
            else controller.keyUp(window, key.nativeCode());
        }

        @Override
        public void scroll(int x, int y, int amount) {
            Point o = origin();
            session.controller().mouseMove(o.x() + x, o.y() + y);
            session.controller().scroll(amount);
        }

        /** Where the game's window sits in the display: the frame's (0, 0), as {@code SessionSource} says. */
        private Point origin() {
            GenericWindow window = session.attached();
            Rectangle r = window == null ? null : window.getRect();
            return r == null ? new Point(0, 0) : new Point(r.x, r.y);
        }
    }

    /** The emulator's touch: a click only. */
    private record OnEmulator(CaptureSource emulator) implements Hands {

        @Override
        public void click(int x, int y) {
            emulator.click(new Point(x, y));
        }

        @Override
        public void key(Key key) {
            throw new UnsupportedOperationException("The emulator takes clicks here; a key needs try_statement.");
        }

        @Override
        public void scroll(int x, int y, int amount) {
            throw new UnsupportedOperationException("The emulator takes clicks here; a scroll needs try_statement.");
        }
    }

    /** This desktop's pointer and keyboard, the cursor put back after a click or a scroll. */
    private record OnDesktop(Area at) implements Hands {

        @Override
        public void click(int x, int y) {
            PointerPolicy.click(NativeControllerFactory.get(), null, at.x() + x, at.y() + y, MouseButton.LEFT.code());
        }

        @Override
        public void key(Key key) {
            NativeController controller = NativeControllerFactory.get();
            controller.keyDown(key.nativeCode());
            hold();
            controller.keyUp(key.nativeCode());
        }

        @Override
        public void scroll(int x, int y, int amount) {
            NativeController controller = NativeControllerFactory.get();
            java.awt.Point back = controller.cursorPosition();
            controller.mouseMove(at.x() + x, at.y() + y);
            controller.scroll(amount);
            if (back != null) controller.mouseMove(back.x, back.y);
        }
    }

    /** A key's hold between press and release; an interrupt is kept for the settle that follows. */
    private static void hold() {
        try {
            Thread.sleep(KEY_HOLD_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
