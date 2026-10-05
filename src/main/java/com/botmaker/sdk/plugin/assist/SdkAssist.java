package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.flow.FlowEdits;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.flow.FlowValue;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.screen.CaptureValue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The SDK's tools for the AI assistant that drives Studio: look at what the bot sees, make pictures, and shape
 * the activity flow.
 *
 * <p>Coordinates are the <b>screenshot's</b> pixels: {@code (0, 0)} is the top-left of what {@code screenshot}
 * returned, so the assistant crops what it was shown. Every write goes through the same path as the SDK's own
 * windows, on the FX thread ({@link FxCall}): the picture file and its {@code Pictures} constant, the flow and its
 * {@code Activities}/{@code Outcomes} constants, {@code Sdk.captureSource()}. Nothing clicks or types; the
 * assistant acts on the game only by running the bot.
 */
public final class SdkAssist {

    record Crop(@Describe("the picture's name, lowercase with underscores: collect_button") String name,
                @Describe("left edge, in the screenshot's pixels") int x,
                @Describe("top edge, in the screenshot's pixels") int y,
                @Describe("width in pixels") int width,
                @Describe("height in pixels") int height) {
    }

    record PictureName(@Describe("a picture's name, as list_pictures gives it") String picture) {
    }

    record NewActivity(@Describe("the activity's name: Collect") String name,
                       @Describe(value = "its body as a method reference, Collect::body; leave out for none yet",
                               optional = true) String body) {
    }

    record Rename(@Describe("the activity's current name") String from,
                  @Describe("its new name") String to) {
    }

    record ActivityName(@Describe("the activity's name") String name) {
    }

    record Wire(@Describe("the activity the wire leaves") String from,
                @Describe(value = "the outcome it leaves on; leave out for NEXT", optional = true) String outcome,
                @Describe("the activity it goes to") String to) {
    }

    record WindowTitle(@Describe("part of the window's title, as it appears in the taskbar") String window) {
    }

    public static final List<AssistantTool<?>> ALL = List.of(
            AssistantTool.named("screenshot")
                    .describedAs("What the bot sees now: the screen its capture source names, as an image. "
                            + "Coordinates in the other tools are this image's pixels.")
                    .takesNothing().handledBy(SdkAssist::screenshot),
            AssistantTool.named("list_pictures")
                    .describedAs("The pictures the bot can look for, each with the Pictures constant a block uses")
                    .takesNothing().handledBy(SdkAssist::listPictures),
            AssistantTool.named("crop_picture")
                    .describedAs("Cuts a box out of the last screenshot you took and saves it as a picture the "
                            + "bot can find and click, declared as a Pictures constant")
                    .takes(Crop.class).handledBy(SdkAssist::cropPicture),
            AssistantTool.named("find_picture")
                    .describedAs("Where a picture is on the screen now and how sure the match is, with the "
                            + "screenshot boxed where it matched best")
                    .takes(PictureName.class).handledBy(SdkAssist::findPicture),
            AssistantTool.named("add_activity")
                    .describedAs("Adds an activity to the bot's flow, as the Activity Flow window does")
                    .takes(NewActivity.class).handledBy(SdkAssist::addActivity),
            AssistantTool.named("rename_activity")
                    .describedAs("Renames an activity everywhere: its card, wires, presets and constant")
                    .takes(Rename.class).handledBy(SdkAssist::renameActivity),
            AssistantTool.named("remove_activity")
                    .describedAs("Removes an activity and its wires from the flow")
                    .takes(ActivityName.class).handledBy(SdkAssist::removeActivity),
            AssistantTool.named("connect")
                    .describedAs("Wires an activity's outcome to the activity that runs next, replacing the "
                            + "wire that outcome had")
                    .takes(Wire.class).handledBy(SdkAssist::connect),
            AssistantTool.named("set_capture_source")
                    .describedAs("Points the bot at a window, so it looks at and clicks in that window")
                    .takes(WindowTitle.class).handledBy(SdkAssist::setCaptureSource));

    /**
     * The last screenshot the assistant was shown. {@code crop_picture}'s coordinates are that image's pixels,
     * and the screen may have changed since, so the crop is cut from this, not from a fresh capture.
     */
    private static volatile BufferedImage shown;

    private SdkAssist() {}

    static AgentReply screenshot(AssistantTool.None none, AgentContext context) {
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(noScreen());
        shown = frame.get();
        String where = context.watchedArea().map(a -> " at " + a.x() + "," + a.y() + " on the desktop").orElse("");
        return AgentReply.text(frame.get().getWidth() + "×" + frame.get().getHeight() + where + ".")
                .and(AgentReply.image(frame.get()));
    }

    static AgentReply listPictures(AssistantTool.None none, AgentContext context) {
        Path resources = context.services().resourcesDir();
        if (resources == null) return AgentReply.refused("No project is open.");
        List<String> lines = new ArrayList<>();
        for (Path file : TemplateLibrary.list(resources)) {
            if (TemplateLibrary.isUnmodifiedDefaultTemplate(file)) continue;
            String name = TemplateLibrary.baseName(file);
            String constant = TemplateNames.constantFor(name);
            lines.add(name + (constant == null ? " (no constant: rename it lowercase)" : " → "
                    + TemplateNames.CLASS_NAME + "." + constant));
        }
        return AgentReply.text(lines.isEmpty() ? "No pictures yet; crop_picture makes one." : String.join("\n", lines));
    }

    static AgentReply cropPicture(Crop crop, AgentContext context) {
        Optional<BufferedImage> frame = Optional.ofNullable(shown);
        if (frame.isEmpty()) return AgentReply.refused("Take a screenshot first: the box is in its pixels.");
        Optional<BufferedImage> picture = PictureCuts.crop(frame.get(), null,
                new Area(crop.x(), crop.y(), crop.width(), crop.height()));
        if (picture.isEmpty()) return AgentReply.refused("That box is outside the "
                + frame.get().getWidth() + "×" + frame.get().getHeight() + " screenshot.");
        PictureCuts.Cut saved = FxCall.call(() -> PictureCuts.save(context.services(), picture.get(), crop.name(),
                frame.get().getWidth(), frame.get().getHeight(), null));
        context.watchedArea().ifPresent(at -> context.marks().show(new Area(at.x() + crop.x(), at.y() + crop.y(),
                crop.width(), crop.height()), Marks.Kind.NOTE, saved.name()));
        String constant = TemplateNames.constantFor(saved.name());
        return AgentReply.text("Saved " + saved.name() + (constant == null ? "" : " as " + TemplateNames.CLASS_NAME
                        + "." + constant) + saved.note().map(n -> " — " + n).orElse("") + ".")
                .and(AgentReply.image(picture.get()));
    }

    static AgentReply findPicture(PictureName name, AgentContext context) {
        Path resources = context.services().resourcesDir();
        if (resources == null) return AgentReply.refused("No project is open.");
        String base = TemplateLibrary.sanitizeName(name.picture());
        if (!TemplateLibrary.exists(resources, base)) {
            return AgentReply.refused("No picture called \"" + name.picture() + "\"; list_pictures names them.");
        }
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(noScreen());
        MatchResult best;
        try (ImageTemplate template = new ImageTemplate(TemplateLibrary.fileForName(resources, base).toString())) {
            best = ImageFinder.bestMatch(template, new Still(frame.get()));
        }
        if (!best.isFound()) return AgentReply.text(base + " could not be matched on this screen at all.");
        Rect r = best.rect();
        context.watchedArea().ifPresent(at -> context.marks().show(new Area(at.x() + r.x(), at.y() + r.y(),
                r.width(), r.height()), Marks.Kind.FOUND, base));
        return AgentReply.text(String.format(Locale.ROOT, "%s best at %d,%d (%d×%d), score %.2f; a run needs the "
                        + "project's confidence.", base, r.x(), r.y(), r.width(), r.height(), best.confidence()))
                .and(AgentReply.image(boxed(frame.get(), r)));
    }

    static AgentReply addActivity(NewActivity activity, AgentContext context) {
        return editFlow(context, flow -> FlowEdits.addActivity(flow, activity.name(), activity.body()),
                flow -> Map.of(), "Added " + activity.name() + ".");
    }

    static AgentReply renameActivity(Rename rename, AgentContext context) {
        // Keyed by the label the flow holds and the label the rename makes, which is what the constants'
        // plan looks up: "collect" typed for the activity "Collect" still renames Activities.COLLECT.
        return editFlow(context, flow -> FlowEdits.renameActivity(flow, rename.from(), rename.to()),
                flow -> FlowEdits.labelOf(flow, rename.from()).<Map<String, String>>map(
                        held -> Map.of(held, FlowNames.label(rename.to()))).orElse(Map.of()),
                "Renamed " + rename.from() + " to " + rename.to() + ".");
    }

    static AgentReply removeActivity(ActivityName name, AgentContext context) {
        return editFlow(context, flow -> FlowEdits.removeActivity(flow, name.name()), flow -> Map.of(),
                "Removed " + name.name() + ".");
    }

    static AgentReply connect(Wire wire, AgentContext context) {
        String outcome = wire.outcome() == null || wire.outcome().isBlank() ? "NEXT" : wire.outcome();
        return editFlow(context, flow -> FlowEdits.connect(flow, wire.from(), wire.outcome(), wire.to()),
                flow -> Map.of(), wire.from() + " on " + outcome + " now goes to " + wire.to() + ".");
    }

    static AgentReply setCaptureSource(WindowTitle title, AgentContext context) {
        if (title.window() == null || title.window().isBlank()) return AgentReply.refused("Name a window.");
        String refused = FxCall.call(() -> CaptureValue.CAPTURE.write(context.services(),
                CaptureSource.window(title.window().trim())));
        return refused == null ? AgentReply.text("The bot now looks at the window \"" + title.window().trim() + "\".")
                : AgentReply.refused(refused);
    }

    /** Reads the flow, changes it, and saves it as 🔀 Activity Flow does — all on the FX thread. */
    private static AgentReply editFlow(AgentContext context, UnaryOperator<Flow> change,
                                       Function<Flow, Map<String, String>> activityRenames, String done) {
        StudioServices services = context.services();
        return FxCall.call(() -> {
            Optional<ValueContext> value = FlowValue.FLOW.open(services);
            if (value.isEmpty()) return AgentReply.refused("This project has no Sdk.flow() to write to.");
            // A flow the user wrote by hand reads as none, and writing over it would lose it: 🔀 Activity Flow
            // opens read-only for the same reason.
            if (!FlowValue.FLOW.readable(value.get())) {
                return AgentReply.refused("Sdk.flow() was written by hand, so it is left as it is. Edit it in the "
                        + "code, or rewrite it as Flow.of(…) for the tools to edit.");
            }
            Flow before = FlowValue.read(value.get());
            Flow after;
            try {
                after = change.apply(before);
            } catch (IllegalArgumentException e) {
                return AgentReply.refused(e.getMessage());
            }
            List<String> notes = new ArrayList<>();
            String refused = FlowEdits.save(services.pluginValues(), value.get(), after, FlowEdits.Saved.of(before),
                    new LinkedHashMap<>(activityRenames.apply(before)), new LinkedHashMap<>(), notes);
            if (refused != null) return AgentReply.refused(refused);
            return AgentReply.text(done + (notes.isEmpty() ? "" : " " + String.join(" ", notes))
                    + " Activities: " + FlowEdits.labels(after) + ".");
        });
    }

    private static String noScreen() {
        return "The screen the bot watches cannot be captured — set_capture_source names a window.";
    }

    private static BufferedImage boxed(BufferedImage frame, Rect r) {
        BufferedImage copy = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(frame, 0, 0, null);
            g.setColor(new Color(0x3f, 0xb9, 0x50));
            g.setStroke(new BasicStroke(3));
            g.drawRect(r.x(), r.y(), r.width(), r.height());
        } finally {
            g.dispose();
        }
        return copy;
    }

    /** The screenshot as a source with its own top-left at {@code (0, 0)}, so matches are in screenshot pixels. */
    private record Still(BufferedImage image) implements CaptureSource {

        @Override
        public BufferedImage capture() {
            return image;
        }

        @Override
        public Point origin() {
            return new Point(0, 0);
        }

        @Override
        public void click(Point p) {
        }
    }
}
