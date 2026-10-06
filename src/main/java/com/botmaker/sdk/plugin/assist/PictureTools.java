package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.PictureEdits;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The assistant's pictures and places: each picture a {@code Pictures} constant, each named spot a
 * {@code Points} constant and each named area a {@code Regions} constant, written through the host as the
 * SDK's own windows write them. A box is in the pixels of the last screenshot, as {@code crop_picture}'s is.
 */
final class PictureTools {

    private static final ManagedSet<Point> POINTS = ManagedSet.of(SdkValues.POINTS);
    private static final ManagedSet<Rect> REGIONS = ManagedSet.of(SdkValues.REGIONS);

    record Crop(@Describe("the picture's name, lowercase with underscores: collect_button") String name,
                @Describe("left edge, in the screenshot's pixels") int x,
                @Describe("top edge, in the screenshot's pixels") int y,
                @Describe("width in pixels") int width,
                @Describe("height in pixels") int height) {
    }

    record PictureName(@Describe("a picture's name, as list_pictures gives it") String picture) {
    }

    record Rename(@Describe("the picture's name now") String picture,
                  @Describe("its new name, lowercase with underscores") String to) {
    }

    record Delete(@Describe("the picture's name") String picture,
                  @Describe(value = "another picture its uses should look for instead, when it is still used",
                          optional = true) String pointUsesAt) {
    }

    record Replace(@Describe("the picture's name") String picture,
                   @Describe("left edge of its new image, in the last screenshot's pixels") int x,
                   @Describe("top edge") int y,
                   @Describe("width in pixels") int width,
                   @Describe("height in pixels") int height) {
    }

    record NamedPoint(@Describe("the spot's name: claim button") String name,
                      @Describe("x, in the screenshot's pixels") int x,
                      @Describe("y, in the screenshot's pixels") int y) {
    }

    record NamedRegion(@Describe("the area's name: bag") String name,
                       @Describe("left edge, in the screenshot's pixels") int x,
                       @Describe("top edge") int y,
                       @Describe("width in pixels") int width,
                       @Describe("height in pixels") int height) {
    }

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("list_pictures")
                    .describedAs("The pictures the bot can look for, each with the Pictures constant a block uses")
                    .takesNothing().handledBy(PictureTools::listPictures),
            AssistantTool.named("crop_picture")
                    .describedAs("Cuts a box out of the last screenshot you took and saves it as a picture the "
                            + "bot can find and click, declared as a Pictures constant")
                    .takes(Crop.class).handledBy(PictureTools::cropPicture),
            AssistantTool.named("show_picture")
                    .describedAs("A picture's image, its size, its constant and how many places use it")
                    .takes(PictureName.class).handledBy(PictureTools::showPicture),
            AssistantTool.named("rename_picture")
                    .describedAs("Renames a picture's file, its constant and every use of it together, as "
                            + "🖼 Manage Pictures does")
                    .takes(Rename.class).handledBy(PictureTools::renamePicture),
            AssistantTool.named("delete_picture")
                    .describedAs("Deletes a picture and its constant. One still in use is refused with where, "
                            + "unless its uses are pointed at another picture first")
                    .takes(Delete.class).handledBy(PictureTools::deletePicture),
            AssistantTool.named("replace_picture")
                    .describedAs("Gives a picture a new image cut from the last screenshot, keeping its name and "
                            + "every use")
                    .takes(Replace.class).handledBy(PictureTools::replacePicture),
            AssistantTool.named("save_point")
                    .describedAs("Names a spot of the screen as a Points constant, in the pixels Mouse.click "
                            + "takes, for a block to click by name")
                    .takes(NamedPoint.class).handledBy(PictureTools::savePoint),
            AssistantTool.named("save_region")
                    .describedAs("Names an area of the screen as a Regions constant, in the capture source's own "
                            + "pixels, for a block to narrow a search to with region(…)")
                    .takes(NamedRegion.class).handledBy(PictureTools::saveRegion));

    private PictureTools() {}

    static AgentReply listPictures(AssistantTool.None none, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        List<String> lines = new ArrayList<>();
        for (Path file : TemplateLibrary.list(resources.get())) {
            if (TemplateLibrary.isUnmodifiedDefaultTemplate(file)) continue;
            String name = TemplateLibrary.baseName(file);
            String constant = TemplateNames.constantFor(name);
            lines.add(name + (constant == null ? " (no constant: rename it lowercase)" : " → "
                    + TemplateNames.CLASS_NAME + "." + constant));
        }
        return AgentReply.text(lines.isEmpty() ? "No pictures yet; crop_picture makes one." : String.join("\n", lines));
    }

    static AgentReply cropPicture(Crop crop, AgentContext context) {
        Optional<BufferedImage> frame = Frames.shown();
        if (frame.isEmpty()) return AgentReply.refused("Take a screenshot first: the box is in its pixels.");
        Optional<BufferedImage> picture = PictureCuts.crop(frame.get(), null,
                new Area(crop.x(), crop.y(), crop.width(), crop.height()));
        if (picture.isEmpty()) return AgentReply.refused("That box is outside the "
                + frame.get().getWidth() + "×" + frame.get().getHeight() + " screenshot.");
        PictureCuts.Cut saved = FxCall.call(() -> PictureCuts.save(context.services(), picture.get(), crop.name(),
                frame.get().getWidth(), frame.get().getHeight(), null));
        Frames.mark(context, new Rect(crop.x(), crop.y(), crop.width(), crop.height()), Marks.Kind.NOTE, saved.name());
        String constant = TemplateNames.constantFor(saved.name());
        return AgentReply.text("Saved " + saved.name() + (constant == null ? "" : " as " + TemplateNames.CLASS_NAME
                        + "." + constant) + saved.note().map(n -> " — " + n).orElse("") + ".")
                .and(AgentReply.image(picture.get()));
    }

    static AgentReply showPicture(PictureName name, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), name.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(name.picture()));
        BufferedImage image;
        try {
            image = ImageIO.read(TemplateLibrary.fileForName(resources.get(), base.get()).toFile());
        } catch (IOException e) {
            return AgentReply.refused("Could not read " + base.get() + ".png: " + e.getMessage());
        }
        if (image == null) return AgentReply.refused(base.get() + ".png is not an image.");
        TemplateUses.Scan scan = FxCall.call(() -> TemplateUses.find(context.services().pluginValues(), base.get()));
        String constant = TemplateNames.constantFor(base.get());
        return AgentReply.text(base.get() + ": " + image.getWidth() + "×" + image.getHeight()
                        + (constant == null ? ", no constant" : ", " + TemplateNames.CLASS_NAME + "." + constant)
                        + ", " + (scan.isEmpty() ? "unused" : scan.describe()) + ".")
                .and(AgentReply.image(image));
    }

    static AgentReply renamePicture(Rename rename, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), rename.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(rename.picture()));
        return reply(FxCall.call(() -> PictureEdits.rename(context.services(), base.get(), rename.to())));
    }

    static AgentReply deletePicture(Delete delete, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), delete.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(delete.picture()));
        return reply(FxCall.call(() -> PictureEdits.delete(context.services(), base.get(), delete.pointUsesAt())));
    }

    static AgentReply replacePicture(Replace replace, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        Optional<String> base = Frames.picture(resources.get(), replace.picture());
        if (base.isEmpty()) return AgentReply.refused(Frames.noPicture(replace.picture()));
        Optional<BufferedImage> frame = Frames.shown();
        if (frame.isEmpty()) return AgentReply.refused("Take a screenshot first: the box is in its pixels.");
        Optional<BufferedImage> picture = PictureCuts.crop(frame.get(), null,
                new Area(replace.x(), replace.y(), replace.width(), replace.height()));
        if (picture.isEmpty()) return AgentReply.refused("That box is outside the "
                + frame.get().getWidth() + "×" + frame.get().getHeight() + " screenshot.");
        PictureEdits.Done done = FxCall.call(() -> PictureEdits.replace(context.services(), base.get(), picture.get(),
                frame.get().getWidth(), frame.get().getHeight()));
        return done.ok() ? AgentReply.text(done.said()).and(AgentReply.image(picture.get()))
                : AgentReply.refused(done.said());
    }

    static AgentReply savePoint(NamedPoint point, AgentContext context) {
        String constant = FlowNames.constantFor(point.name());
        if (constant == null) return AgentReply.refused("\"" + point.name() + "\" cannot be a constant's name.");
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        if (Frames.within(frame.get(), point.x(), point.y(), 1, 1).isEmpty()) {
            return AgentReply.refused(Frames.outside(frame.get()));
        }
        Optional<Area> at = context.watchedArea();
        if (at.isEmpty()) return AgentReply.refused("Where the screen sits is not known, so the spot cannot be named.");
        Point spot = new Point(at.get().x() + point.x(), at.get().y() + point.y());
        Saved saved = FxCall.call(() -> save(context.services(), POINTS, constant, spot));
        if (!saved.ok()) return AgentReply.refused(saved.done());
        Frames.mark(context, new Rect(point.x() - 4, point.y() - 4, 9, 9), Marks.Kind.CLICK, constant);
        return AgentReply.text(saved.done() + " Points." + constant + " = " + spot.x() + "," + spot.y()
                + "; a block clicks it with Mouse.click(Points." + constant + ").");
    }

    static AgentReply saveRegion(NamedRegion region, AgentContext context) {
        String constant = FlowNames.constantFor(region.name());
        if (constant == null) return AgentReply.refused("\"" + region.name() + "\" cannot be a constant's name.");
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Optional<Rect> area = Frames.within(frame.get(), region.x(), region.y(), region.width(), region.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        Rect rect = area.get();
        Saved saved = FxCall.call(() -> save(context.services(), REGIONS, constant, rect));
        if (!saved.ok()) return AgentReply.refused(saved.done());
        Frames.mark(context, rect, Marks.Kind.NOTE, constant);
        return AgentReply.text(saved.done() + " Regions." + constant + " = " + Frames.box(rect)
                + "; a block narrows a source to it with .region(Regions." + constant + ").");
    }

    /**
     * {@code value} as the constant {@code name} of {@code set}: a new one, written after the set's class when the
     * bot has none yet, or the one already called that, moved. On the FX thread.
     *
     * @return what was done, or why nothing was
     */
    private static <T> Saved save(StudioServices services, ManagedSet<T> set, String name, T value) {
        PluginValues values = services.pluginValues();
        if (set.contains(values, name)) {
            Optional<ValueContext> held = set.open(values, name);
            if (held.isEmpty() || set.read(held.get()).isEmpty()) {
                return Saved.refused(set.value().holder() + "." + name + " is written by hand, so it is left as it is.");
            }
            return held.get().write(value).map(Saved::refused).orElse(new Saved(true, "Moved"));
        }
        Optional<String> missing = values.create(set.value().id());
        if (missing.isPresent()) return Saved.refused(missing.get());
        return set.add(values, name, value).map(Saved::refused).orElse(new Saved(true, "Saved"));
    }

    /** What {@link #save} did: {@code done} is the verb to report, or why nothing was. */
    private record Saved(boolean ok, String done) {
        static Saved refused(String why) {
            return new Saved(false, why);
        }
    }

    private static AgentReply reply(PictureEdits.Done done) {
        return done.ok() ? AgentReply.text(done.said()) : AgentReply.refused(done.said());
    }
}
