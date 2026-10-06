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
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The assistant's eyes: what the bot sees, which pictures are on it, its text and its colours. Each reads a
 * fresh frame of the screen the bot watches, except {@code screenshot}, which also keeps it for
 * {@code crop_picture}. Nothing here writes or clicks.
 */
final class LookTools {

    /** The longest {@code wait_for_picture} waits: a tool call holds the assistant's turn. */
    static final int LONGEST_WAIT_MS = 30_000;

    private static final int POLL_MS = 250;
    private static final int MOST_LISTED = 20;

    record Shot(@Describe(value = "draw a labelled grid every this many pixels, 25 or more", optional = true)
                Integer grid,
                @Describe(value = "left edge of the part to show; with y, width and height", optional = true)
                Integer x,
                @Describe(value = "top edge of the part to show", optional = true) Integer y,
                @Describe(value = "width of the part to show", optional = true) Integer width,
                @Describe(value = "height of the part to show", optional = true) Integer height,
                @Describe(value = "shrink the image, 0.1 to 1; coordinates stay the screenshot's", optional = true)
                Double scale) {
    }

    record PictureName(@Describe("a picture's name, as list_pictures gives it") String picture) {
    }

    record Wait(@Describe("a picture's name, as list_pictures gives it") String picture,
                @Describe("how long to wait, in milliseconds, at most 30000") int timeoutMs) {
    }

    record ReadArea(@Describe(value = "left edge of the area to read; with y, width and height", optional = true)
                    Integer x,
                    @Describe(value = "top edge of the area", optional = true) Integer y,
                    @Describe(value = "width of the area", optional = true) Integer width,
                    @Describe(value = "height of the area", optional = true) Integer height,
                    @Describe(value = "the language the text is in; English when left out", optional = true)
                    OcrLanguage language) {
    }

    record Needle(@Describe("the text to look for, matched in any case inside a line") String text) {
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
                     Double tolerance) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("screenshot")
                    .describedAs("What the bot sees now: the screen its capture source names, as an image. "
                            + "Coordinates in the other tools are this screenshot's pixels, whatever part or "
                            + "scale is shown. A grid labels them.")
                    .takes(Shot.class).handledBy(LookTools::screenshot),
            AssistantTool.named("find_picture")
                    .describedAs("Where a picture is on the screen now and how sure the match is, with the "
                            + "screenshot boxed where it matched best")
                    .takes(PictureName.class).handledBy(LookTools::findPicture),
            AssistantTool.named("which_screen")
                    .describedAs("Every picture scored on one frame, best first, with the ones a run would find "
                            + "boxed: which screen of the game this is")
                    .takesNothing().handledBy(LookTools::whichScreen),
            AssistantTool.named("wait_for_picture")
                    .describedAs("Waits until a picture is on the screen, as ImageFinder.waitFor would, and says "
                            + "where and after how long; or that it did not come")
                    .takes(Wait.class).handledBy(LookTools::waitForPicture),
            AssistantTool.named("read_text")
                    .describedAs("The text on the screen or in one area of it, line by line, each with where it "
                            + "is and how sure the reading is")
                    .takes(ReadArea.class).handledBy(LookTools::readText),
            AssistantTool.named("find_text")
                    .describedAs("Where a piece of text is on the screen now, with the screenshot boxed there")
                    .takes(Needle.class).handledBy(LookTools::findText),
            AssistantTool.named("pixel_color")
                    .describedAs("The colour of one pixel of the screen now")
                    .takes(Spot.class).handledBy(LookTools::pixelColor),
            AssistantTool.named("find_color")
                    .describedAs("Where a colour is on the screen or in one area of it: each patch of it, "
                            + "largest first, boxed on the screenshot")
                    .takes(ColorArea.class).handledBy(LookTools::findColor));

    private LookTools() {}

    static AgentReply screenshot(Shot shot, AgentContext context) {
        Optional<BufferedImage> frame = context.frame();
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
        BufferedImage image = Frames.cut(full, area);
        if (shot.grid() != null) grid(image, area, shot.grid());
        double scale = shot.scale() == null ? 1 : shot.scale();
        if (scale < 1) image = scaled(image, scale);
        StringBuilder said = new StringBuilder(full.getWidth() + "×" + full.getHeight());
        context.watchedArea().ifPresent(a -> said.append(" at ").append(a.x()).append(',').append(a.y())
                .append(" on the desktop"));
        said.append('.');
        if (area.width() != full.getWidth() || area.height() != full.getHeight()) {
            said.append(" Showing ").append(Frames.box(area)).append(" of it.");
        }
        if (scale < 1) said.append(" Shrunk ×").append(Frames.score(scale)).append(": divide what you see by it.");
        return AgentReply.text(said.toString()).and(AgentReply.image(image));
    }

    static AgentReply findPicture(PictureName name, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), name.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(name.picture()));
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        MatchResult best = best(resources.get(), base.get(), frame.get());
        if (!best.isFound()) return AgentReply.text(base.get() + " could not be matched on this screen at all.");
        double needs = Frames.confidence(context.services());
        boolean found = best.confidence() >= needs;
        Rect r = best.rect();
        if (found) Frames.mark(context, r, Marks.Kind.FOUND, base.get());
        return AgentReply.text(base.get() + " best at " + Frames.box(r) + ", score " + Frames.score(best.confidence())
                        + "; a run needs " + Frames.score(needs) + ", so it " + (found ? "finds it." : "would not."))
                .and(AgentReply.image(Frames.boxed(frame.get(), List.of(r), found ? Frames.FOUND : Frames.NOTE)));
    }

    static AgentReply whichScreen(AssistantTool.None none, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        record Scored(String name, MatchResult match) {}
        List<Scored> scored = new ArrayList<>();
        for (Path file : TemplateLibrary.list(resources.get())) {
            if (TemplateLibrary.isUnmodifiedDefaultTemplate(file)) continue;
            String name = TemplateLibrary.baseName(file);
            MatchResult best = best(resources.get(), name, frame.get());
            if (best.isFound()) scored.add(new Scored(name, best));
        }
        if (scored.isEmpty()) return AgentReply.text("No picture matched at all; crop_picture makes one.");
        scored.sort(Comparator.comparingDouble((Scored s) -> s.match().confidence()).reversed());
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
        return AgentReply.text(head + "\n" + String.join("\n", lines))
                .and(AgentReply.image(Frames.boxed(frame.get(), found, Frames.FOUND)));
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
                Optional<BufferedImage> frame = context.frame();
                if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
                MatchResult match = ImageFinder.bestMatch(template, new Frames.Still(frame.get()));
                if (match.isFound() && match.confidence() >= needs) {
                    long ms = (System.nanoTime() - start) / 1_000_000L;
                    Frames.mark(context, match.rect(), Marks.Kind.FOUND, base.get());
                    return AgentReply.text(base.get() + " is there after " + ms + " ms, at " + Frames.box(match.rect())
                                    + ", score " + Frames.score(match.confidence()) + ".")
                            .and(AgentReply.image(Frames.boxed(frame.get(), List.of(match.rect()), Frames.FOUND)));
                }
                if (match.isFound()) best = Math.max(best, match.confidence());
                if (System.nanoTime() >= deadline) break;
                Thread.sleep(POLL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AgentReply.refused("Stopped waiting.");
        }
        return AgentReply.text(base.get() + " did not come in " + wait.timeoutMs() + " ms; best score "
                + Frames.score(best) + ", a run needs " + Frames.score(needs) + ".");
    }

    static AgentReply readText(ReadArea read, AgentContext context) {
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Optional<Rect> area = Frames.within(frame.get(), read.x(), read.y(), read.width(), read.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        OcrLanguage language = read.language() == null ? OcrLanguage.ENGLISH : read.language();
        List<TextMatch> lines = recognise(Frames.cut(frame.get(), area.get()), language);
        if (lines.isEmpty()) return AgentReply.text("No text could be read there.");
        List<String> said = new ArrayList<>();
        for (TextMatch line : lines) {
            said.add("\"" + line.text() + "\" at " + Frames.box(moved(line.bounds(), area.get())) + ", "
                    + Frames.score(line.confidence() / 100.0));
        }
        return AgentReply.text(String.join("\n", said));
    }

    static AgentReply findText(Needle needle, AgentContext context) {
        if (needle.text() == null || needle.text().isBlank()) return AgentReply.refused("Name the text to look for.");
        Optional<BufferedImage> frame = context.frame();
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
        return AgentReply.text(String.join("\n", said))
                .and(AgentReply.image(Frames.boxed(frame.get(), hits, Frames.FOUND)));
    }

    static AgentReply pixelColor(Spot spot, AgentContext context) {
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        BufferedImage image = frame.get();
        if (spot.x() < 0 || spot.y() < 0 || spot.x() >= image.getWidth() || spot.y() >= image.getHeight()) {
            return AgentReply.refused(spot.x() + "," + spot.y() + " is outside the " + image.getWidth() + "×"
                    + image.getHeight() + " screenshot.");
        }
        Color color = new Color(image.getRGB(spot.x(), spot.y()), false);
        return AgentReply.text(spot.x() + "," + spot.y() + " is " + hex(color) + " (" + color.getRed() + ","
                + color.getGreen() + "," + color.getBlue() + ").");
    }

    static AgentReply findColor(ColorArea search, AgentContext context) {
        Optional<Color> color = color(search.color());
        if (color.isEmpty()) return AgentReply.refused("\"" + search.color() + "\" is not a colour: #RRGGBB, or r,g,b.");
        double tolerance = search.tolerance() == null ? Precision.DEFAULT.deltaE() : search.tolerance();
        if (!(tolerance >= 0 && tolerance <= 100)) return AgentReply.refused("The tolerance is between 0 and 100.");
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Optional<Rect> area = Frames.within(frame.get(), search.x(), search.y(), search.width(), search.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        List<ColorMatch> patches;
        try {
            Pixel.findAll(color.get(), new Frames.Still(Frames.cut(frame.get(), area.get())), Precision.of(tolerance));
            patches = List.copyOf(Vision.lastColorMatchList());
        } finally {
            Vision.clearLastColorMatch();
        }
        if (patches.isEmpty()) return AgentReply.text(hex(color.get()) + " is not on the screen there.");
        List<Rect> boxes = new ArrayList<>();
        List<String> said = new ArrayList<>();
        for (ColorMatch patch : patches) {
            Rect r = moved(patch.bounds(), area.get());
            boxes.add(r);
            if (said.size() < MOST_LISTED) {
                Point c = patch.center();
                said.add(patch.pixelCount() + " px at " + Frames.box(r) + ", centre " + (c.x() + area.get().x())
                        + "," + (c.y() + area.get().y()));
            }
            Frames.mark(context, r, Marks.Kind.FOUND, hex(color.get()));
        }
        return AgentReply.text(patches.size() + " patch" + (patches.size() == 1 ? "" : "es") + " of " + hex(color.get())
                        + ", largest first:\n" + String.join("\n", said))
                .and(AgentReply.image(Frames.boxed(frame.get(), boxes, Frames.FOUND)));
    }

    /** The best match of the picture {@code name} on {@code frame}, in the frame's pixels. */
    private static MatchResult best(Path resources, String name, BufferedImage frame) {
        // Released after each match: an unreleased template is native memory.
        try (ImageTemplate template = new ImageTemplate(TemplateLibrary.fileForName(resources, name).toString())) {
            return ImageFinder.bestMatch(template, new Frames.Still(frame));
        }
    }

    /** Every line the SDK's OCR reads on {@code image}, in its pixels; the thread's last match is left clear. */
    private static List<TextMatch> recognise(BufferedImage image, OcrLanguage language) {
        try {
            Text.readAll(new Frames.Still(image), Text.DEFAULT_OPTIONS.withLanguages(language));
            return List.copyOf(Text.lastMatchList());
        } finally {
            Text.clearLastMatch();
        }
    }

    private static Rect moved(Rect r, Rect by) {
        return new Rect(r.x() + by.x(), r.y() + by.y(), r.width(), r.height());
    }

    static String hex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** {@code #RRGGBB}, {@code RRGGBB} or {@code r,g,b}; empty for anything else. */
    static Optional<Color> color(String typed) {
        if (typed == null) return Optional.empty();
        String text = typed.trim();
        try {
            if (text.contains(",")) {
                String[] parts = text.split(",");
                if (parts.length != 3) return Optional.empty();
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                if ((r | g | b) < 0 || r > 255 || g > 255 || b > 255) return Optional.empty();
                return Optional.of(new Color(r, g, b));
            }
            String digits = text.startsWith("#") ? text.substring(1) : text;
            if (digits.length() != 6) return Optional.empty();
            return Optional.of(new Color(Integer.parseInt(digits, 16)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
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

    private static BufferedImage scaled(BufferedImage image, double scale) {
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return small;
    }
}
