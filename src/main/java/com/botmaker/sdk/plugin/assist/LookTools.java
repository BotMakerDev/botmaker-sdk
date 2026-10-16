package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.text.OcrLanguage;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.text.TextMatch;
import com.botmaker.sdk.api.vision.ColorMatch;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Pixel;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.api.vision.Vision;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.trace.MissFrames;
import com.botmaker.sdk.internal.vision.ColorText;
import com.botmaker.sdk.internal.vision.Images;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The assistant's eyes: what the bot sees, which pictures are on it, its text and its colours. Each reads a
 * fresh frame of the screen the bot watches, except {@code screenshot}, which also keeps it for
 * {@code crop_pictures}; {@code missed_frames} reads what a run saved instead. Nothing here writes or clicks;
 * {@link ActTools} acts and says what changed.
 */
final class LookTools {

    /** The longest {@code wait_for_picture} waits: a tool call holds the assistant's turn. */
    static final int LONGEST_WAIT_MS = 30_000;

    private static final int POLL_MS = 250;
    private static final int MOST_LISTED = 20;
    private static final String SHOW = "true adds the screenshot with the places boxed; the text already gives them";

    record Shot(@Describe(value = "draw a labelled grid every this many pixels, 25 or more", optional = true)
                Integer grid,
                @Describe(value = "left edge of the part to show; with y, width and height", optional = true)
                Integer x,
                @Describe(value = "top edge of the part to show", optional = true) Integer y,
                @Describe(value = "width of the part to show", optional = true) Integer width,
                @Describe(value = "height of the part to show", optional = true) Integer height,
                @Describe(value = "shrink the image, 0.1 to 1; coordinates stay the screenshot's. Left out, it "
                        + "fits 768 pixels: give 1, or a part, for detail", optional = true)
                Double scale) {
    }

    record PictureName(@Describe("a picture's name, as list_pictures gives it") String picture,
                       @Describe(value = SHOW, optional = true) Boolean show) {
    }

    record Shown(@Describe(value = SHOW, optional = true) Boolean show,
                 @Describe(value = "true looks for every picture all over the frame, slow (a second per ten "
                         + "pictures); left out, each is looked for near where it was cut", optional = true)
                 Boolean everywhere) {
    }

    record Wait(@Describe("a picture's name, as list_pictures gives it") String picture,
                @Describe("how long to wait, in milliseconds, at most 30000") int timeoutMs,
                @Describe(value = SHOW, optional = true) Boolean show) {
    }

    record ReadArea(@Describe(value = "left edge of the area to read; with y, width and height", optional = true)
                    Integer x,
                    @Describe(value = "top edge of the area", optional = true) Integer y,
                    @Describe(value = "width of the area", optional = true) Integer width,
                    @Describe(value = "height of the area", optional = true) Integer height,
                    @Describe(value = "the language the text is in; English when left out", optional = true)
                    OcrLanguage language) {
    }

    record Needle(@Describe("the text to look for, matched in any case inside a line") String text,
                  @Describe(value = SHOW, optional = true) Boolean show) {
    }

    record Spot(@Describe("x, in the screenshot's pixels") int x,
                @Describe("y, in the screenshot's pixels") int y) {
    }

    record ColorArea(@Describe("the colour: #RRGGBB, or r,g,b") String color,
                     @Describe(value = "left edge of the area to search; with y, width and height", optional = true)
                     Integer x,
                     @Describe(value = "top edge of the area", optional = true) Integer y,
                     @Describe(value = "width of the area", optional = true) Integer width,
                     @Describe(value = "height of the area", optional = true) Integer height,
                     @Describe(value = "how far a colour may be from it, as ΔE; 12 when left out", optional = true)
                     Double tolerance,
                     @Describe(value = SHOW, optional = true) Boolean show) {
    }

    record Missed(@Describe(value = "a saved frame's file name, as the list gives it; the list when left out",
            optional = true) String name) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("screenshot")
                    .describedAs("What the bot sees now: the screen its capture source names, as an image. "
                            + "Coordinates in the other tools are this screenshot's pixels, whatever part or "
                            + "scale is shown. A grid labels them.")
                    .takes(Shot.class).handledBy(LookTools::screenshot),
            AssistantTool.named("find_picture")
                    .describedAs("Where a picture is on the screen now and how sure the match is")
                    .takes(PictureName.class).handledBy(LookTools::findPicture),
            AssistantTool.named("which_screen")
                    .describedAs("Which screen of the bot's Screens the game shows, by their markers, then every "
                            + "picture scored near where it was cut on that frame, best first, the ones a run would "
                            + "find ticked; everywhere true scores each all over the frame")
                    .takes(Shown.class).handledBy(LookTools::whichScreen),
            AssistantTool.named("wait_for_picture")
                    .describedAs("Waits until a picture is on the screen, as ImageFinder.waitFor would, and says "
                            + "where and after how long; or that it did not come")
                    .takes(Wait.class).handledBy(LookTools::waitForPicture),
            AssistantTool.named("read_text")
                    .describedAs("The text on the screen or in one area of it, line by line, each with where it "
                            + "is and how sure the reading is")
                    .takes(ReadArea.class).handledBy(LookTools::readText),
            AssistantTool.named("find_text")
                    .describedAs("Where a piece of text is on the screen now")
                    .takes(Needle.class).handledBy(LookTools::findText),
            AssistantTool.named("pixel_color")
                    .describedAs("The colour of one pixel of the screen now")
                    .takes(Spot.class).handledBy(LookTools::pixelColor),
            AssistantTool.named("find_color")
                    .describedAs("Where a colour is on the screen or in one area of it: each patch of it, "
                            + "largest first")
                    .takes(ColorArea.class).handledBy(LookTools::findColor),
            AssistantTool.named("missed_frames")
                    .describedAs("What the latest run saw when a wait timed out: the frames it saved, "
                            + "listed, or one of them as an image, its search area boxed. The trace names each.")
                    .takes(Missed.class).handledBy(LookTools::missedFrames));

    private LookTools() {}

    static AgentReply missedFrames(Missed missed, AgentContext context) {
        Path folder = context.services().projectDir();
        if (folder == null) return AgentReply.refused(Frames.noProject());
        List<Path> frames = MissFrames.latest(folder);
        if (frames.isEmpty()) return AgentReply.text("The latest run saved no frame: no wait timed out.");
        if (missed.name() == null || missed.name().isBlank()) {
            List<String> names = frames.stream().map(f -> f.getFileName().toString()).toList();
            return AgentReply.text("Saved by the latest run, oldest first:\n" + String.join("\n", names));
        }
        // Only a name the list gives: a path the assistant typed never reaches the disk.
        Optional<Path> file = frames.stream().filter(f -> f.getFileName().toString().equals(missed.name().trim()))
                .findFirst();
        if (file.isEmpty()) return AgentReply.refused("No saved frame called \"" + missed.name() + "\"; "
                + "missed_frames without a name lists them.");
        BufferedImage image;
        try {
            image = ImageIO.read(file.get().toFile());
        } catch (IOException e) {
            image = null;
        }
        if (image == null) return AgentReply.refused(missed.name() + " could not be read.");
        double scale = Frames.fit(image.getWidth(), image.getHeight(), Frames.LOOK_EDGE);
        StringBuilder said = new StringBuilder(missed.name() + ", " + image.getWidth() + "×" + image.getHeight());
        if (scale < 1) {
            image = Images.scaled(image, scale);
            said.append(", shrunk to ").append(image.getWidth()).append('×').append(image.getHeight())
                    .append(": multiply what you see by ").append(Frames.undo(scale));
        }
        return AgentReply.text(said.append('.').toString()).and(AgentReply.image(image));
    }

    static AgentReply screenshot(Shot shot, AgentContext context) {
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        BufferedImage full = frame.get();
        Optional<Rect> part = Frames.within(full, shot.x(), shot.y(), shot.width(), shot.height());
        if (part.isEmpty()) return AgentReply.refused(Frames.outside(full));
        if (shot.grid() != null && shot.grid() < 25) return AgentReply.refused("A grid is 25 pixels or more.");
        if (shot.scale() != null && !(shot.scale() >= 0.1 && shot.scale() <= 1)) {
            return AgentReply.refused("The scale is between 0.1 and 1.");
        }
        // Kept only once it is shown: a refused call must not swap the frame a crop is cut from.
        Frames.show(full);
        Rect area = part.get();
        BufferedImage image = Images.cut(full, area);
        if (shot.grid() != null) grid(image, area, shot.grid());
        // A grid is there to be read off, so a grid asked for without a scale is shown whole.
        double scale = shot.scale() != null ? shot.scale()
                : shot.grid() != null ? 1 : Frames.fit(area.width(), area.height(), Frames.LOOK_EDGE);
        if (scale < 1) image = Images.scaled(image, scale);
        StringBuilder said = new StringBuilder(full.getWidth() + "×" + full.getHeight()).append(Frames.place(context))
                .append('.');
        if (area.width() != full.getWidth() || area.height() != full.getHeight()) {
            said.append(" Showing ").append(Frames.box(area)).append(" of it.");
        }
        if (scale < 1) {
            said.append(" Shrunk to ").append(image.getWidth()).append('×').append(image.getHeight())
                    .append(": multiply what you see by ").append(Frames.undo(scale)).append('.');
        }
        return AgentReply.text(said.toString()).and(AgentReply.image(image));
    }

    static AgentReply findPicture(PictureName name, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), name.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(name.picture()));
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        MatchResult best = best(resources.get(), base.get(), frame.get());
        if (!best.isFound()) return AgentReply.text(base.get() + " could not be matched on this screen at all.");
        double needs = Frames.confidence(context.services());
        boolean found = best.confidence() >= needs;
        Rect r = best.rect();
        if (found) Frames.mark(context, r, Marks.Kind.FOUND, base.get());
        return Frames.found(base.get() + " best at " + Frames.box(r) + ", score " + Frames.score(best.confidence())
                        + "; a run needs " + Frames.score(needs) + ", so it " + (found ? "finds it." : "would not."),
                frame.get(), List.of(r), found ? Frames.FOUND : Frames.NOTE, name.show());
    }

    static AgentReply whichScreen(Shown shown, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        String onMap = MapTools.where(context, frame.get()).map(line -> line + "\n").orElse("");
        // Near each picture's place by default: 170 full-frame matches took 27 s on Firestone (2026-10-15).
        List<Scored> scored = Boolean.TRUE.equals(shown.everywhere()) ? scores(resources.get(), frame.get(),
                cuts(context)) : scoresNear(resources.get(), frame.get(), cuts(context));
        if (scored.isEmpty()) return AgentReply.text(onMap + "No picture matched at all; crop_pictures makes one.");
        double needs = Frames.confidence(context.services());
        List<String> lines = new ArrayList<>();
        List<Rect> found = new ArrayList<>();
        for (Scored s : scored) {
            boolean on = s.match().confidence() >= needs;
            if (on) {
                found.add(s.match().rect());
                Frames.mark(context, s.match().rect(), Marks.Kind.FOUND, s.name());
            }
            if (lines.size() < MOST_LISTED) {
                lines.add((on ? "✓ " : "  ") + s.name() + " " + Frames.score(s.match().confidence()) + " at "
                        + Frames.box(s.match().rect()));
            }
        }
        String head = found.isEmpty() ? "None of the pictures is on screen (a run needs " + Frames.score(needs) + ")."
                : found.size() + " on screen (✓, a run needs " + Frames.score(needs) + "):";
        return Frames.found(onMap + head + "\n" + String.join("\n", lines), frame.get(), found, Frames.FOUND,
                shown.show());
    }

    static AgentReply waitForPicture(Wait wait, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), wait.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(wait.picture()));
        if (wait.timeoutMs() < 0 || wait.timeoutMs() > LONGEST_WAIT_MS) {
            return AgentReply.refused("Wait between 0 and " + LONGEST_WAIT_MS + " ms; take a screenshot to look again.");
        }
        double needs = Frames.confidence(context.services());
        long start = System.nanoTime();
        long deadline = start + wait.timeoutMs() * 1_000_000L;
        double best = 0;
        try (ImageTemplate template = new ImageTemplate(TemplateLibrary.fileForName(resources.get(), base.get())
                .toString())) {
            while (true) {
                Optional<BufferedImage> frame = Frames.look(context);
                if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
                MatchResult match = ToolTiming.timed(ToolTiming.Phase.MATCH,
                        () -> ImageFinder.bestMatch(template, new StillSource(frame.get())));
                if (match.isFound() && match.confidence() >= needs) {
                    long ms = (System.nanoTime() - start) / 1_000_000L;
                    Frames.mark(context, match.rect(), Marks.Kind.FOUND, base.get());
                    return Frames.found(base.get() + " is there after " + ms + " ms, at " + Frames.box(match.rect())
                                    + ", score " + Frames.score(match.confidence()) + ".",
                            frame.get(), List.of(match.rect()), Frames.FOUND, wait.show());
                }
                if (match.isFound()) best = Math.max(best, match.confidence());
                if (System.nanoTime() >= deadline) break;
                ToolTiming.timed(ToolTiming.Phase.WAIT, () -> {
                    Thread.sleep(POLL_MS);
                    return null;
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentReply.refused("Stopped waiting.");
        }
        return AgentReply.text(base.get() + " did not come in " + wait.timeoutMs() + " ms; best score "
                + Frames.score(best) + ", a run needs " + Frames.score(needs) + ".");
    }

    static AgentReply readText(ReadArea read, AgentContext context) {
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Optional<Rect> area = Frames.within(frame.get(), read.x(), read.y(), read.width(), read.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        OcrLanguage language = read.language() == null ? OcrLanguage.ENGLISH : read.language();
        List<TextMatch> lines = recognise(Images.cut(frame.get(), area.get()), language);
        if (lines.isEmpty()) return AgentReply.text("No text could be read there.");
        List<String> said = new ArrayList<>();
        Rect at = area.get();
        for (TextMatch line : lines) {
            said.add("\"" + line.text() + "\" at " + Frames.box(line.bounds().translate(at.x(), at.y())) + ", "
                    + Frames.score(line.confidence() / 100.0));
        }
        return AgentReply.text(String.join("\n", said));
    }

    static AgentReply findText(Needle needle, AgentContext context) {
        if (needle.text() == null || needle.text().isBlank()) return AgentReply.refused("Name the text to look for.");
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        String wanted = needle.text().trim().toLowerCase(Locale.ROOT);
        List<Rect> hits = new ArrayList<>();
        List<String> said = new ArrayList<>();
        for (TextMatch line : recognise(frame.get(), OcrLanguage.ENGLISH)) {
            if (!line.text().toLowerCase(Locale.ROOT).contains(wanted)) continue;
            hits.add(line.bounds());
            said.add("\"" + line.text() + "\" at " + Frames.box(line.bounds()));
            Frames.mark(context, line.bounds(), Marks.Kind.FOUND, needle.text().trim());
        }
        if (hits.isEmpty()) return AgentReply.text("\"" + needle.text().trim() + "\" is not on the screen as text.");
        return Frames.found(String.join("\n", said), frame.get(), hits, Frames.FOUND, needle.show());
    }

    static AgentReply pixelColor(Spot spot, AgentContext context) {
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        BufferedImage image = frame.get();
        if (spot.x() < 0 || spot.y() < 0 || spot.x() >= image.getWidth() || spot.y() >= image.getHeight()) {
            return AgentReply.refused(spot.x() + "," + spot.y() + " is outside the " + image.getWidth() + "×"
                    + image.getHeight() + " screenshot.");
        }
        Color color = new Color(image.getRGB(spot.x(), spot.y()), false);
        return AgentReply.text(spot.x() + "," + spot.y() + " is " + ColorText.hex(color) + " (" + color.getRed() + ","
                + color.getGreen() + "," + color.getBlue() + ").");
    }

    static AgentReply findColor(ColorArea search, AgentContext context) {
        Optional<Color> color = ColorText.parse(search.color());
        if (color.isEmpty()) return AgentReply.refused("\"" + search.color() + "\" is not a colour: #RRGGBB, or r,g,b.");
        double tolerance = search.tolerance() == null ? Precision.DEFAULT.deltaE() : search.tolerance();
        if (!(tolerance >= 0 && tolerance <= 100)) return AgentReply.refused("The tolerance is between 0 and 100.");
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Optional<Rect> area = Frames.within(frame.get(), search.x(), search.y(), search.width(), search.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        List<ColorMatch> patches;
        try {
            Pixel.findAll(color.get(), new StillSource(Images.cut(frame.get(), area.get())), Precision.of(tolerance));
            patches = List.copyOf(Vision.lastColorMatchList());
        } finally {
            Vision.clearLastColorMatch();
        }
        if (patches.isEmpty()) return AgentReply.text(ColorText.hex(color.get()) + " is not on the screen there.");
        List<Rect> boxes = new ArrayList<>();
        List<String> said = new ArrayList<>();
        for (ColorMatch patch : patches) {
            Rect r = patch.bounds().translate(area.get().x(), area.get().y());
            boxes.add(r);
            if (said.size() < MOST_LISTED) {
                Point c = patch.center();
                said.add(patch.pixelCount() + " px at " + Frames.box(r) + ", centre " + (c.x() + area.get().x())
                        + "," + (c.y() + area.get().y()));
            }
            Frames.mark(context, r, Marks.Kind.FOUND, ColorText.hex(color.get()));
        }
        return Frames.found(patches.size() + " patch" + (patches.size() == 1 ? "" : "es") + " of "
                + ColorText.hex(color.get()) + ", largest first:\n" + String.join("\n", said), frame.get(), boxes, Frames.FOUND, search.show());
    }

    /** A picture's best match on one frame. */
    record Scored(String name, MatchResult match) {
    }

    /** How far from where it was cut {@link #scoresNear} looks for a picture, in the frame's pixels. */
    static final int NEAR = 48;

    /**
     * Where each of the bot's pictures was cut, by name, as its constant says ({@link TemplateUses#cuts}); none when
     * the FX thread does not answer (a dialog holds it), so the pictures are looked for everywhere rather than an
     * action's answer lost.
     */
    static Map<String, FrameCut> cuts(AgentContext context) {
        try {
            return FxCall.call(() -> TemplateUses.cuts(context.services().pluginValues()));
        } catch (RuntimeException unanswered) {
            return Map.of();
        }
    }

    /**
     * Every picture's best match on {@code frame}, best first; a picture that matched nowhere is left out. All of them
     * in one {@link ImageFinder#bestMatches}: the frame prepared once, the pictures matched side by side. Each is
     * scaled from the frame {@code cuts} says it was cut from.
     */
    static List<Scored> scores(Path resources, BufferedImage frame, Map<String, FrameCut> cuts) {
        return scores(resources, frame, cuts, false);
    }

    /**
     * {@link #scores}, each picture whose place {@code cuts} knows looked for only {@link #NEAR} it
     * ({@link ImageFinder#bestMatchesNear}); one with no place is looked for everywhere.
     */
    static List<Scored> scoresNear(Path resources, BufferedImage frame, Map<String, FrameCut> cuts) {
        return scores(resources, frame, cuts, true, true);
    }

    /**
     * {@link #scoresNear} of the pictures whose place is known only, for the line every act tool answers with. A
     * picture with no place is matched over the whole frame, masked when it is transparent: three such placeholders
     * took 1.1 s of the 1.2 s a Firestone screen's 128 pictures cost (2026-10-16), paid on each answer.
     */
    static List<Scored> scoresPlaced(Path resources, BufferedImage frame, Map<String, FrameCut> cuts) {
        return scores(resources, frame, cuts, true, false);
    }

    private static boolean placed(FrameCut cut) {
        return cut != null && cut.place() != null;
    }

    private static List<Scored> scores(Path resources, BufferedImage frame, Map<String, FrameCut> cuts, boolean near) {
        return scores(resources, frame, cuts, near, true);
    }

    private static List<Scored> scores(Path resources, BufferedImage frame, Map<String, FrameCut> cuts, boolean near,
                                       boolean unplaced) {
        List<String> names = new ArrayList<>();
        List<ImageTemplate> templates = new ArrayList<>();
        try {
            for (Path file : TemplateLibrary.list(resources)) {
                if (TemplateLibrary.isUnmodifiedDefaultTemplate(file)) continue;
                String name = TemplateLibrary.baseName(file);
                if (!unplaced && !placed(cuts.get(name))) continue;
                names.add(name);
                templates.add(cutAt(new ImageTemplate(file.toString()), cuts.get(name)));
            }
            StillSource still = new StillSource(frame);
            List<MatchResult> bests = ToolTiming.timed(ToolTiming.Phase.MATCH, () -> near
                    ? ImageFinder.bestMatchesNear(templates, NEAR, still) : ImageFinder.bestMatches(templates, still));
            List<Scored> scored = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                if (bests.get(i).isFound()) scored.add(new Scored(names.get(i), bests.get(i)));
            }
            scored.sort(Comparator.comparingDouble((Scored s) -> s.match().confidence()).reversed());
            return scored;
        } finally {
            // Released after the match: an unreleased template is native memory.
            templates.forEach(ImageTemplate::close);
        }
    }

    /** {@code template} as cut at {@code cut}; as it is when the cut is not known. */
    private static ImageTemplate cutAt(ImageTemplate template, FrameCut cut) {
        if (cut == null) return template;
        ImageTemplate placed = cut.place() == null ? template : template.at(cut.place());
        return cut.frame() == null ? placed : placed.in(cut.frame());
    }

    /** The best match of the picture {@code name} on {@code frame}, in the frame's pixels. */
    static MatchResult best(Path resources, String name, BufferedImage frame) {
        return ToolTiming.timed(ToolTiming.Phase.MATCH, () -> {
            // Released after each match: an unreleased template is native memory.
            try (ImageTemplate template = new ImageTemplate(TemplateLibrary.fileForName(resources, name).toString())) {
                return ImageFinder.bestMatch(template, new StillSource(frame));
            }
        });
    }

    /** Every line the SDK's OCR reads on {@code image}, in its pixels; the thread's last match is left clear. */
    static List<TextMatch> recognise(BufferedImage image, OcrLanguage language) {
        return ToolTiming.timed(ToolTiming.Phase.TEXT, () -> {
            try {
                Text.readAll(new StillSource(image), Text.DEFAULT_OPTIONS.withLanguages(language));
                return List.copyOf(Text.lastMatchList());
            } finally {
                Text.clearLastMatch();
            }
        });
    }

    /** Lines every {@code step} screenshot pixels over {@code image}, which shows {@code area}, each labelled. */
    private static void grid(BufferedImage image, Rect area, int step) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            for (int x = (area.x() / step + 1) * step; x < area.x() + area.width(); x += step) {
                line(g, x - area.x(), 0, x - area.x(), image.getHeight());
                label(g, String.valueOf(x), x - area.x() + 2, 12);
            }
            for (int y = (area.y() / step + 1) * step; y < area.y() + area.height(); y += step) {
                line(g, 0, y - area.y(), image.getWidth(), y - area.y());
                label(g, String.valueOf(y), 2, y - area.y() - 2);
            }
        } finally {
            g.dispose();
        }
    }

    private static void line(Graphics2D g, int x1, int y1, int x2, int y2) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.setColor(Color.MAGENTA);
        g.drawLine(x1, y1, x2, y2);
    }

    private static void label(Graphics2D g, String text, int x, int y) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(Color.BLACK);
        g.drawString(text, x + 1, y + 1);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }

}
