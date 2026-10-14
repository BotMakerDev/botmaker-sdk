package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.assist.Describe;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.geometry.Spot;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.text.DigitSet;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.api.vision.Vision;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.vision.DigitReader;
import com.botmaker.sdk.internal.vision.Places;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.pictures.DigitCuts;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import com.botmaker.sdk.plugin.pictures.PictureEdits;
import com.botmaker.sdk.plugin.pictures.ScreenSize;
import com.botmaker.sdk.plugin.pictures.TagCatalog;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateManifest;
import com.botmaker.sdk.plugin.pictures.TemplateUses;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The assistant's pictures and places: each picture a {@code Pictures} constant, each named spot a
 * {@code Spots} constant, each named area a {@code Zones} constant and each game font's digits a {@code Digits}
 * constant, written through the host as the
 * SDK's own windows write them. A box is in the pixels of the last screenshot, as {@code crop_picture}'s is; a
 * place is written in the bot's pixels, which the screenshot's are once the game shows the bot's screen size.
 */
final class PictureTools {

    private static final ManagedSet<Spot> SPOTS = ManagedSet.of(SdkValues.SPOTS);
    private static final ManagedSet<Zone> ZONES = ManagedSet.of(SdkValues.ZONES);
    private static final ManagedSet<DigitSet> DIGITS = ManagedSet.of(SdkValues.DIGITS);

    record Crop(@Describe("the picture's name, lowercase with underscores: collect_button") String name,
                @Describe("left edge, in the screenshot's pixels") int x,
                @Describe("top edge, in the screenshot's pixels") int y,
                @Describe("width in pixels") int width,
                @Describe("height in pixels") int height,
                @Describe(value = "tags to file it under, each declared (list_tags)", optional = true)
                List<String> tags,
                @Describe(value = IGNORE, optional = true) Integer ignoreX,
                @Describe(value = "top edge of the part to skip", optional = true) Integer ignoreY,
                @Describe(value = "width of the part to skip", optional = true) Integer ignoreWidth,
                @Describe(value = "height of the part to skip", optional = true) Integer ignoreHeight,
                @Describe(value = "the screen it is on, as its section names it: noted there as an input, untried "
                        + "until try_input tries it", optional = true) String screen,
                @Describe(value = "true when it is that screen's marker, a part no other screen shows: pays the "
                        + "marker try_input left owed", optional = true) Boolean marker) {

        /** The part to skip, when all four of its edges are given. */
        Optional<Rect> ignore() {
            return PictureTools.ignore(ignoreX, ignoreY, ignoreWidth, ignoreHeight);
        }
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
                   @Describe("height in pixels") int height,
                   @Describe(value = IGNORE, optional = true) Integer ignoreX,
                   @Describe(value = "top edge of the part to skip", optional = true) Integer ignoreY,
                   @Describe(value = "width of the part to skip", optional = true) Integer ignoreWidth,
                   @Describe(value = "height of the part to skip", optional = true) Integer ignoreHeight) {

        /** The part to skip, when all four of its edges are given. */
        Optional<Rect> ignore() {
            return PictureTools.ignore(ignoreX, ignoreY, ignoreWidth, ignoreHeight);
        }
    }

    private static final String IGNORE = "left edge of a part inside the box a run skips (a timer's digits, a count "
            + "that differs from copy to copy), in the screenshot's pixels; with ignoreY, ignoreWidth and ignoreHeight";

    private static Optional<Rect> ignore(Integer x, Integer y, Integer width, Integer height) {
        if (x == null || y == null || width == null || height == null) return Optional.empty();
        return Optional.of(new Rect(x, y, width, height));
    }

    record NamedPoint(@Describe("the spot's name: claim button") String name,
                      @Describe("x, in the screenshot's pixels") int x,
                      @Describe("y, in the screenshot's pixels") int y,
                      @Describe(value = ANCHOR, optional = true) Anchor anchor) {
    }

    record NamedRegion(@Describe("the area's name: bag") String name,
                       @Describe("left edge, in the screenshot's pixels") int x,
                       @Describe("top edge") int y,
                       @Describe("width in pixels") int width,
                       @Describe("height in pixels") int height,
                       @Describe(value = ANCHOR, optional = true) Anchor anchor) {
    }

    record NamedDigits(@Describe("the digit set's name, lowercase with underscores: gold") String name,
                       @Describe("left edge of a box around one number the game shows, in the screenshot's pixels")
                       int x,
                       @Describe("top edge") int y,
                       @Describe("width in pixels") int width,
                       @Describe("height in pixels") int height,
                       @Describe("the number exactly as the game shows it, separators included: 12,500")
                       String shows) {
    }

    private static final String ANCHOR = "the part of the screen it stays with when the game shows another shape: "
            + "a corner or edge for a HUD on it, center (the default) for the game's middle";

    static final List<AssistantTool<?>> TOOLS = List.of(
            AssistantTool.named("list_pictures")
                    .describedAs("The pictures the bot can look for, each with the Pictures constant a block uses")
                    .takesNothing().handledBy(PictureTools::listPictures),
            AssistantTool.named("crop_picture")
                    .describedAs("Cuts a box out of the last screenshot you took and saves it as a picture the "
                            + "bot can find and click, declared as a Pictures constant; a part given to ignore "
                            + "is left out of matching, so copies that differ only there all match")
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
                            + "every use; a part given to ignore is left out of matching, as crop_picture's")
                    .takes(Replace.class).handledBy(PictureTools::replacePicture),
            AssistantTool.named("save_point")
                    .describedAs("Names a spot of the game as a Spots constant, in the bot's pixels, for a block to "
                            + "click by name with Mouse.click(Spots.…), wherever the window sits and at any size")
                    .takes(NamedPoint.class).handledBy(PictureTools::savePoint),
            AssistantTool.named("save_region")
                    .describedAs("Names an area of the game as a Zones constant, in the bot's pixels, for a block to "
                            + "narrow a search to with .region(Zones.…), wherever the window sits and at any size")
                    .takes(NamedRegion.class).handledBy(PictureTools::saveRegion),
            AssistantTool.named("save_digits")
                    .describedAs("Cuts the digits of a number the game shows, in a font OCR misreads, into a Digits "
                            + "constant, for a block to read numbers with Text.readDigits(source, Digits.…). Box one "
                            + "number with a little plain background around it; call again on numbers showing the "
                            + "digits it lacks")
                    .takes(NamedDigits.class).handledBy(PictureTools::saveDigits));

    private PictureTools() {}

    static AgentReply listPictures(AssistantTool.None none, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        List<String> lines = new ArrayList<>();
        TemplateManifest manifest = TemplateLibrary.manifest(resources.get());
        TagCatalog catalog = FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        for (Path file : TemplateLibrary.list(resources.get())) {
            if (TemplateLibrary.isUnmodifiedDefaultTemplate(file)) continue;
            String name = TemplateLibrary.baseName(file);
            String constant = TemplateNames.constantFor(name);
            List<String> tags = catalog.declaredOnly(manifest.tagsOf(name));
            lines.add(name + (constant == null ? " (no constant: rename it lowercase)" : " → "
                    + TemplateNames.CLASS_NAME + "." + constant) + (tags.isEmpty() ? "" : " [" + String.join(", ", tags)
                    + "]"));
        }
        return AgentReply.text(lines.isEmpty() ? "No pictures yet; crop_picture makes one." : String.join("\n", lines));
    }

    static AgentReply cropPicture(Crop crop, AgentContext context) {
        Optional<BufferedImage> frame = Frames.shown();
        if (frame.isEmpty()) return AgentReply.refused("Take a screenshot first: the box is in its pixels.");
        Optional<PictureCuts.Piece> picture = PictureCuts.crop(frame.get(), null,
                new Area(crop.x(), crop.y(), crop.width(), crop.height()))
                .map(piece -> crop.ignore().map(part -> PictureCuts.masked(piece, part)).orElse(piece));
        if (picture.isEmpty()) return AgentReply.refused("That box is outside the "
                + frame.get().getWidth() + "×" + frame.get().getHeight() + " screenshot.");
        List<String> typed = crop.tags() == null ? List.of() : crop.tags();
        TagCatalog catalog = typed.isEmpty() ? null : FxCall.call(() -> TemplateLibrary.tagCatalog(context.services()));
        Optional<String> undeclared = typed.stream().filter(t -> !catalog.isDeclared(t)).findFirst();
        if (undeclared.isPresent()) {
            return AgentReply.refused("No tag called \"" + undeclared.get() + "\"; list_tags names them and "
                    + "declare_tag makes one. Nothing was saved.");
        }
        List<String> tags = catalog == null ? List.of() : catalog.declaredOnly(typed);
        boolean marker = Boolean.TRUE.equals(crop.marker());
        String screen = crop.screen() == null || crop.screen().isBlank() ? null : crop.screen().strip();
        if (marker && screen == null) return AgentReply.refused("A marker is a screen's: give screen too.");
        Optional<String> section = Optional.empty();
        if (screen != null) {
            Path project = context.services().projectDir();
            if (project == null) return AgentReply.refused(Frames.noProject());
            try {
                section = MapTools.section(project, screen);
            } catch (IllegalArgumentException | UncheckedIOException e) {
                return AgentReply.refused(e.getMessage());
            }
            if (section.isEmpty()) {
                return AgentReply.refused("No section " + screen + " in " + GameNotes.FILE + ": try_input opens "
                        + "one, or write_notes it. Nothing was saved.");
            }
        }
        Optional<String> moving = moving(context, frame.get(), new Rect(crop.x(), crop.y(), crop.width(),
                crop.height()));
        if (moving.isPresent()) return AgentReply.refused(moving.get());
        String alike = lookAlikes(context, frame.get(), picture.get().image(),
                new Rect(crop.x(), crop.y(), crop.width(), crop.height())).map(a -> " " + a).orElse("");
        PictureCuts.Cut saved = FxCall.call(() -> {
            PictureCuts.Cut cut = PictureCuts.save(context.services(), picture.get(), crop.name(), frame.get());
            if (!tags.isEmpty()) TemplateLibrary.applyTags(context.services(), Map.of(cut.name(), tags));
            return cut;
        });
        Frames.mark(context, new Rect(crop.x(), crop.y(), crop.width(), crop.height()), Marks.Kind.NOTE, saved.name());
        String constant = TemplateNames.constantFor(saved.name());
        String noted = "";
        if (section.isPresent()) {
            try {
                noted = MapTools.noteCut(context.services().projectDir(), section.get(), saved.name(), marker);
            } catch (IllegalArgumentException | UncheckedIOException e) {
                noted = " It could not be noted in " + section.get() + ": " + e.getMessage();
            }
        }
        return AgentReply.text("Saved " + saved.name() + (constant == null ? "" : " as " + TemplateNames.CLASS_NAME
                        + "." + constant) + (tags.isEmpty() ? "" : ", tagged " + String.join(", ", tags))
                        + saved.note().map(n -> " — " + n).orElse("") + "." + noted + alike)
                .and(AgentReply.image(picture.get().image()));
    }

    /** How far from its own box a match still counts as the cut itself, in pixels. */
    private static final int SELF_SLACK = 8;

    /**
     * Where else on {@code shown} a run would find {@code cut}, saved from {@code asked}, said as a warning; empty
     * when nowhere. Matching ignores colour, so a lit button's greyed-out twin scores just over the bot's
     * confidence and a run clicks it forever: cutting the twin as a compare's bad picture is the cure. A twin
     * scoring between the run's default confidence and a lower one the project sets goes unsaid.
     */
    private static Optional<String> lookAlikes(AgentContext context, BufferedImage shown, BufferedImage cut,
                                               Rect asked) {
        Optional<Rect> inside = Frames.within(shown, asked.x(), asked.y(), asked.width(), asked.height());
        if (inside.isEmpty()) return Optional.empty();
        Rect box = inside.get();
        double needs = Frames.confidence(context.services());
        Path file;
        try {
            file = Files.createTempFile("botmaker-alike-", ".png");
            ImageIO.write(cut, "png", file.toFile());
        } catch (IOException e) {
            return Optional.empty();
        }
        try (ImageTemplate template = new ImageTemplate(file.toString())) {
            ImageFinder.findAll(template, new Frames.Still(shown));
            List<String> elsewhere = Vision.lastMatchList().stream()
                    .filter(m -> m.confidence() >= needs)
                    .filter(m -> Math.abs(m.rect().x() - box.x()) > SELF_SLACK
                            || Math.abs(m.rect().y() - box.y()) > SELF_SLACK)
                    .map(m -> m.rect().x() + "," + m.rect().y() + " (" + Frames.score(m.confidence()) + ")")
                    .toList();
            if (elsewhere.isEmpty()) return Optional.empty();
            return Optional.of("A run also finds it at " + String.join(", ", elsewhere) + ", over the "
                    + Frames.score(needs) + " it needs: matching ignores colour, so a greyed-out twin counts. "
                    + "If those are not the same thing, cut one as its look-alike and act with "
                    + "ImageFinder.whileFindCompare or ImageClicker.clickCompare, this as good and it as bad.");
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // a temp file left behind is the system's to clear
            }
        }
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
        Optional<PictureCuts.Piece> picture = PictureCuts.crop(frame.get(), null,
                new Area(replace.x(), replace.y(), replace.width(), replace.height()))
                .map(piece -> replace.ignore().map(part -> PictureCuts.masked(piece, part)).orElse(piece));
        if (picture.isEmpty()) return AgentReply.refused("That box is outside the "
                + frame.get().getWidth() + "×" + frame.get().getHeight() + " screenshot.");
        Optional<String> moving = moving(context, frame.get(), new Rect(replace.x(), replace.y(), replace.width(),
                replace.height()));
        if (moving.isPresent()) return AgentReply.refused(moving.get());
        PictureEdits.Done done = FxCall.call(() -> PictureEdits.replace(context.services(), base.get(),
                picture.get().image(), FrameCut.of(frame.get().getWidth(), frame.get().getHeight(),
                        picture.get().place())));
        return done.ok() ? AgentReply.text(done.said()).and(AgentReply.image(picture.get().image()))
                : AgentReply.refused(done.said());
    }

    static AgentReply savePoint(NamedPoint point, AgentContext context) {
        String constant = FlowNames.constantFor(point.name());
        if (constant == null) return AgentReply.refused("\"" + point.name() + "\" cannot be a constant's name.");
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        if (Frames.within(frame.get(), point.x(), point.y(), 1, 1).isEmpty()) {
            return AgentReply.refused(Frames.outside(frame.get()));
        }
        Size live = new Size(frame.get().getWidth(), frame.get().getHeight());
        Placed<Spot> placed = FxCall.call(() -> place(context.services(), SPOTS, constant, live,
                bot -> Places.inBot(point.x(), point.y(), point.anchor(), bot, live)));
        if (!placed.saved().ok()) return AgentReply.refused(placed.saved().done());
        Frames.mark(context, new Rect(point.x() - 4, point.y() - 4, 9, 9), Marks.Kind.CLICK, constant);
        Spot spot = placed.value();
        return AgentReply.text(placed.saved().done() + " Spots." + constant + " = " + spot.x() + "," + spot.y()
                + placed.of() + "; a block clicks it with Mouse.click(Spots." + constant + ").");
    }

    static AgentReply saveRegion(NamedRegion region, AgentContext context) {
        String constant = FlowNames.constantFor(region.name());
        if (constant == null) return AgentReply.refused("\"" + region.name() + "\" cannot be a constant's name.");
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return AgentReply.refused(Frames.noScreen());
        Optional<Rect> area = Frames.within(frame.get(), region.x(), region.y(), region.width(), region.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        Rect rect = area.get();
        Size live = new Size(frame.get().getWidth(), frame.get().getHeight());
        Placed<Zone> placed = FxCall.call(() -> place(context.services(), ZONES, constant, live,
                bot -> Places.inBot(rect, region.anchor(), bot, live)));
        if (!placed.saved().ok()) return AgentReply.refused(placed.saved().done());
        Frames.mark(context, rect, Marks.Kind.NOTE, constant);
        Zone zone = placed.value();
        return AgentReply.text(placed.saved().done() + " Zones." + constant + " = "
                + Frames.box(new Rect(zone.x(), zone.y(), zone.width(), zone.height())) + placed.of()
                + "; a block narrows a source to it with .region(Zones." + constant + ").");
    }

    static AgentReply saveDigits(NamedDigits digits, AgentContext context) {
        Optional<Path> resources = Frames.resources(context);
        if (resources.isEmpty()) return AgentReply.refused(Frames.noProject());
        String name = TemplateLibrary.sanitizeName(digits.name());
        String constant = FlowNames.constantFor(name);
        if (name.isEmpty() || constant == null) {
            return AgentReply.refused("\"" + digits.name() + "\" cannot be a constant's name.");
        }
        String shows = digits.shows() == null ? "" : digits.shows().replace(" ", "");
        if (shows.chars().noneMatch(Character::isDigit)) {
            return AgentReply.refused("Give the number the box shows, as the game writes it: 12,500.");
        }
        Optional<BufferedImage> frame = Frames.shown();
        if (frame.isEmpty()) return AgentReply.refused("Take a screenshot first: the box is in its pixels.");
        Optional<Rect> area = Frames.within(frame.get(), digits.x(), digits.y(), digits.width(), digits.height());
        if (area.isEmpty()) return AgentReply.refused(Frames.outside(frame.get()));
        BufferedImage number = Frames.cut(frame.get(), area.get());
        List<Rect> boxes = DigitCuts.characters(number);
        AgentReply seen = AgentReply.image(enlarged(number, boxes));
        if (boxes.size() != shows.length()) {
            return AgentReply.refused("That box holds " + boxes.size() + " characters apart, but \"" + shows + "\" has "
                    + shows.length() + ": box the number alone, with a pixel or two of plain background around it.")
                    .and(seen);
        }
        Size live = new Size(frame.get().getWidth(), frame.get().getHeight());
        DigitSet set = new DigitSet(DigitCuts.pathFor(name));
        // Every digit of a set is scaled by one frame: one cut from another is refused, not mixed in.
        Optional<DigitSet> before = FxCall.call(() -> DIGITS.open(context.services().pluginValues(),
                        Member.of(constant))
                .flatMap(DIGITS::read));
        Size bot = FxCall.call(() -> ScreenSize.read(context.services())).orElse(live);
        DigitSet wanted = bot.equals(live) ? set : set.in(live);
        if (before.isPresent() && !before.get().equals(wanted) && !DigitCuts.held(resources.get(), name).isEmpty()) {
            Size cut = before.get().frame() != null ? before.get().frame() : bot;
            return AgentReply.refused("Digits." + constant + " was cut from a " + cut.width() + "×" + cut.height()
                    + " frame and this screenshot is " + live.width() + "×" + live.height() + ": save_digits at that"
                    + " size, or under another name.");
        }
        // The constant first: a refused one leaves the set's pictures as they were.
        Placed<DigitSet> placed = FxCall.call(() -> place(context.services(), DIGITS, constant, live,
                at -> at.equals(live) ? set : set.in(live)));
        if (!placed.saved().ok()) return AgentReply.refused(placed.saved().done()).and(seen);
        List<Integer> saved;
        try {
            saved = DigitCuts.save(resources.get(), name, number, boxes, shows);
        } catch (IOException e) {
            return AgentReply.refused("Could not save the digits: " + e.getMessage());
        }
        List<DigitReader.Glyph> held = DigitCuts.held(resources.get(), name);
        Frames.mark(context, area.get(), Marks.Kind.NOTE, constant);
        List<Integer> has = held.stream().map(DigitReader.Glyph::digit).toList();
        List<Integer> lacks = IntStream.rangeClosed(0, 9).boxed().filter(d -> !has.contains(d)).toList();
        String expected = shows.replaceAll("\\D", "").replaceFirst("^0+(?=.)", "");
        OptionalLong read = DigitReader.read(number, 1, held, Frames.confidence(context.services()), name);
        String check = read.isEmpty() ? "It cannot read this box back: box it again, with more plain background."
                : String.valueOf(read.getAsLong()).equals(expected) ? "It reads this box as " + expected + "."
                : "It reads this box as " + read.getAsLong() + ", not " + expected + ": box it again, with more plain"
                + " background.";
        return AgentReply.text("Saved digits " + digitList(saved) + " into Digits." + constant + " ("
                        + set.folder() + ")" + placed.of() + ". It holds " + digitList(has)
                        + (lacks.isEmpty() ? "" : "; it lacks " + digitList(lacks) + ": save_digits on numbers that"
                        + " show them") + ". " + check + " A block reads one with Text.readDigits(source, Digits."
                        + constant + ").")
                .and(seen);
    }

    /** How long a box is watched before it is saved, and how many live frames that takes. */
    static final long STILL_MS = 1000;
    private static final int STILL_LOOKS = 3;
    /** How far around its box a cut is looked for on a live frame: a few pixels of the game's own jitter. */
    private static final int STILL_SLACK = 8;

    /**
     * Why {@code asked} is no picture to cut, or empty when a run would find it: the cut is matched, as a run
     * matches a picture ({@link ImageFinder#bestMatch}), around its own place on {@value #STILL_LOOKS} live frames
     * over {@value #STILL_MS} ms, and a score under the bot's confidence on any of them means an animation, a
     * counter, a sparkle passing, or the screen moved since the screenshot. Such a picture matches only the
     * moment it was cut at. With no live frame of the screenshot's size the check is skipped. Blocking.
     */
    private static Optional<String> moving(AgentContext context, BufferedImage shown, Rect asked) {
        Optional<Rect> inside = Frames.within(shown, asked.x(), asked.y(), asked.width(), asked.height());
        if (inside.isEmpty()) return Optional.empty();
        Rect box = inside.get();
        Optional<Rect> around = Frames.within(shown, Math.max(0, box.x() - STILL_SLACK),
                Math.max(0, box.y() - STILL_SLACK),
                Math.min(shown.getWidth() - Math.max(0, box.x() - STILL_SLACK), box.width() + 2 * STILL_SLACK),
                Math.min(shown.getHeight() - Math.max(0, box.y() - STILL_SLACK), box.height() + 2 * STILL_SLACK));
        if (around.isEmpty()) return Optional.empty();
        double needs = Frames.confidence(context.services());
        Path file;
        try {
            file = Files.createTempFile("botmaker-still-", ".png");
            ImageIO.write(Frames.cut(shown, box), "png", file.toFile());
        } catch (IOException e) {
            return Optional.empty();
        }
        try (ImageTemplate cut = new ImageTemplate(file.toString())) {
            for (int look = 0; look < STILL_LOOKS; look++) {
                if (look > 0) Thread.sleep(STILL_MS / (STILL_LOOKS - 1));
                Optional<BufferedImage> live = Frames.look(context);
                if (live.isEmpty() || live.get().getWidth() != shown.getWidth()
                        || live.get().getHeight() != shown.getHeight()) {
                    return Optional.empty();
                }
                MatchResult best = ImageFinder.bestMatch(cut, new Frames.Still(Frames.cut(live.get(), around.get())));
                double score = best.isFound() ? best.confidence() : 0;
                if (score < needs) {
                    return Optional.of("That box changes: " + (look * STILL_MS / (STILL_LOOKS - 1)) + " ms later "
                            + "a run would score it " + Frames.score(score) + " where it was cut, under the "
                            + Frames.score(needs) + " it needs. It is animated, or the screen moved since the "
                            + "screenshot: take a new one and cut a part that holds still. Nothing was saved.");
                }
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // a temp file left behind is the system's to clear
            }
        }
    }

    /** {@code 0 1 5}. */
    private static String digitList(List<Integer> digits) {
        return digits.stream().sorted().map(String::valueOf).collect(Collectors.joining(" "));
    }

    /** A number cut from the screen at a size the assistant can see it, each character it was cut into boxed. */
    private static BufferedImage enlarged(BufferedImage number, List<Rect> boxes) {
        double scale = Math.max(1, Math.min(8, 480.0 / number.getWidth()));
        List<Rect> scaled = boxes.stream().map(r -> new Rect((int) Math.round(r.x() * scale),
                (int) Math.round(r.y() * scale), (int) Math.round(r.width() * scale),
                (int) Math.round(r.height() * scale))).toList();
        return Frames.boxed(Frames.scaled(number, scale), scaled, Frames.FOUND);
    }

    /**
     * A place cut from a screenshot of {@code live} size, written in the bot's pixels as the constant {@code name}.
     * A bot naming no screen size takes the screenshot's, once the place is written: a refused save changes
     * nothing. One whose {@code screen()} is written by hand is refused, since its pixels cannot be read. On the
     * FX thread.
     */
    private static <T> Placed<T> place(StudioServices services, ManagedSet<T> set, String name, Size live,
                                       Function<Size, T> inBot) {
        Optional<Size> named = ScreenSize.read(services);
        if (named.isEmpty() && ScreenSize.handWritten(services)) {
            return new Placed<>(Saved.refused("Sdk.screen() is written by hand, so the bot's pixels are not known:"
                    + " write it as new Size(width, height) first."), null, live);
        }
        Size bot = named.orElse(live);
        T value = inBot.apply(bot);
        Saved saved = save(services, set, name, value);
        if (saved.ok() && named.isEmpty()) ScreenSize.adopt(services, live);
        return new Placed<>(saved, value, bot);
    }

    /** What {@link #save} did with a place, and the bot's screen it was written against. */
    private record Placed<T>(Saved saved, T value, Size bot) {
        /** {@code " of the bot's 1280×720 screen"}. */
        String of() {
            return " of the bot's " + bot.width() + "×" + bot.height() + " screen";
        }
    }

    /**
     * {@code value} as the constant {@code name} of {@code set}: a new one, written after the set's class when the
     * bot has none yet, or the one already called that, moved. On the FX thread.
     *
     * @return what was done, or why nothing was
     */
    private static <T> Saved save(StudioServices services, ManagedSet<T> set, String name, T value) {
        PluginValues values = services.pluginValues();
        Member member = Member.of(name);
        if (set.contains(values, member)) {
            Optional<ValueContext> held = set.open(values, member);
            if (held.isEmpty() || set.read(held.get()).isEmpty()) {
                return Saved.refused(set.value().holder() + "." + name + " is written by hand, so it is left as it is.");
            }
            return held.get().write(value).map(Saved::refused).orElse(new Saved(true, "Moved"));
        }
        Optional<String> missing = values.create(set.value());
        if (missing.isPresent()) return Saved.refused(missing.get());
        return set.add(values, member, value).map(Saved::refused).orElse(new Saved(true, "Saved"));
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
