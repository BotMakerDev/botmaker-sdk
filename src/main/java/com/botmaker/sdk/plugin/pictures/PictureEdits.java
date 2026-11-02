package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.vision.ImageFinder;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.capture.StillSource;
import com.botmaker.sdk.internal.vision.TemplateNames;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 🖼 Manage Pictures' rename, delete and replace, for a caller with no window: the assistant's picture tools.
 * Each takes the same steps in the same order as the window — the bot's Java first, so a change the host
 * refuses leaves the file where it was — and answers the sentence to show. On the FX thread, since the Java is
 * written there.
 */
public final class PictureEdits {

    private static final ManagedHandle<BotSettings> SETTINGS = ManagedHandle.of(SdkValues.SETTINGS);

    private PictureEdits() {}

    /** What a change did: whether it happened, and the sentence that says so or says why not. */
    public record Done(boolean ok, String said) {
        static Done ok(String said) {
            return new Done(true, said);
        }

        static Done refused(String said) {
            return new Done(false, said);
        }
    }

    /** Renames the picture {@code current} and every use of its constant to {@code typed}, sanitised. */
    public static Done rename(StudioServices services, String current, String typed) {
        Path resources = services.resourcesDir();
        Path file = TemplateLibrary.fileForName(resources, current);
        if (TemplateLibrary.isDefaultTemplate(file)) return Done.refused("The default picture cannot be renamed.");
        String wanted = TemplateLibrary.sanitizeName(typed == null ? "" : typed);
        if (wanted.equals(current)) return Done.refused(current + " is already its name.");
        String problem = TemplateNaming.renameProblem(resources, current, wanted);
        if (problem != null) return Done.refused(problem);
        TemplateUses.Scan scan = TemplateUses.find(services.pluginValues(), current);
        Optional<String> refused = TemplateUses.rename(services.pluginValues(), current, wanted);
        if (refused.isPresent()) return Done.refused("Not renamed: " + refused.get());
        try {
            TemplateLibrary.renameTemplate(resources, file, wanted);
        } catch (IOException e) {
            // The constant already names the new file, which is not there: put the Java back.
            Optional<String> back = TemplateUses.rename(services.pluginValues(), wanted, current);
            return Done.refused("Failed to rename: " + e.getMessage()
                    + back.map(why -> " — and the bot still names " + wanted + ": " + why).orElse(""));
        }
        return Done.ok(scan.isEmpty() ? "Renamed " + current + " to " + wanted + "."
                : "Renamed " + current + " to " + wanted + " and updated " + scan.describe() + ".");
    }

    /**
     * Deletes the picture {@code name}: its constant, then its file. One still in use is refused with where,
     * unless {@code pointUsesAt} names another picture, which its uses then look for instead — a guess, marked
     * for review as Manage Pictures marks it. With none, a picture only the bot's {@code Screens} use leaves them with
     * its file; a delete that stops puts them back.
     */
    public static Done delete(StudioServices services, String name, String pointUsesAt) {
        Path resources = services.resourcesDir();
        Path file = TemplateLibrary.fileForName(resources, name);
        if (TemplateLibrary.isDefaultTemplate(file)) return Done.refused("The default picture cannot be deleted.");
        boolean repointing = pointUsesAt != null && !pointUsesAt.isBlank();
        // Uses pointed at another picture keep their place, the map's too: a marker replaced stays a marker.
        TemplateUses.MapCut cut = repointing ? TemplateUses.MapCut.none(TemplateUses.find(services.pluginValues(), name))
                : TemplateUses.offTheMap(services.pluginValues(), name);
        TemplateUses.Scan scan = cut.scan();
        if (!scan.isEmpty()) {
            if (!repointing) {
                StringBuilder where = new StringBuilder(name + " is still used: " + scan.describe() + ".");
                scan.uses().stream().limit(10).forEach(use -> where.append("\n  ").append(use.file().getFileName())
                        .append(':').append(use.line()).append("  ").append(use.text()));
                return Done.refused(where.append("\nName pointUsesAt to move them to another picture first.")
                        .toString());
            }
            String replacement = TemplateLibrary.sanitizeName(pointUsesAt);
            if (replacement.equals(name) || !TemplateLibrary.exists(resources, replacement)) {
                return Done.refused("No other picture called \"" + pointUsesAt + "\" to point the uses at.");
            }
            Optional<String> refused = TemplateUses.repoint(services.pluginValues(), name, replacement);
            if (refused.isPresent()) return Done.refused("Nothing was deleted: " + refused.get());
        }
        Optional<String> kept = TemplateUses.forget(services.pluginValues(), name);
        if (kept.isPresent()) {
            cut.undo(services.pluginValues());
            return Done.refused("Not deleted: " + kept.get());
        }
        try {
            TemplateLibrary.deleteTemplate(file);
        } catch (IOException e) {
            return Done.refused("Failed to delete " + name + ": " + e.getMessage());
        }
        List<String> left = cut.screens();
        return Done.ok(scan.isEmpty() ? "Deleted " + name + (left.isEmpty() ? "." : "; it left the map's "
                + String.join(", ", left) + ".")
                : "Deleted " + name + " after pointing " + scan.describe() + " at " + pointUsesAt.trim()
                        + "; each is marked for review.");
    }

    /**
     * Gives the picture {@code name} a new image, keeping its name, its tags and every use: the blocks that look
     * for it look for the new one. Its constant is rewritten as cut at {@code cut}.
     */
    public static Done replace(StudioServices services, String name, BufferedImage picture, FrameCut cut) {
        Path file = TemplateLibrary.fileForName(services.resourcesDir(), name);
        if (TemplateLibrary.isDefaultTemplate(file)) return Done.refused("The default picture cannot be replaced.");
        try {
            TemplateLibrary.replaceImage(file, picture);
        } catch (IOException e) {
            return Done.refused("Failed to replace " + name + ": " + e.getMessage());
        }
        String note = TemplateUses.cut(services, name, cut).map(n -> " " + n).orElse("");
        return Done.ok("Replaced the image of " + name + "; every block that uses it now looks for the new one."
                + note);
    }

    /**
     * Adds {@code frame} as the last frame of the picture {@code name}: a pose its watch missed. Refused when it is
     * not the picture's frame size, when the picture holds {@link FrameStrip#MOST} already, or when a frame it holds
     * matches it at the bot's confidence — it shows nothing new. The constant is written first, as everywhere here.
     */
    public static Done addFrame(StudioServices services, String name, BufferedImage frame) {
        Path file = TemplateLibrary.fileForName(services.resourcesDir(), name);
        if (TemplateLibrary.isDefaultTemplate(file)) return Done.refused("The default picture cannot move.");
        Optional<FrameCut> held = TemplateUses.cut(services.pluginValues(), name);
        if (held.isEmpty()) return Done.refused("Pictures." + TemplateNames.constantFor(name)
                + " is not written as one of ours, so no frame can be added to it.");
        int count = held.get().frames();
        if (count >= FrameStrip.MOST) {
            return Done.refused(name + " holds " + count + " frames already, the most a picture holds.");
        }
        List<BufferedImage> frames;
        try {
            BufferedImage strip = ImageIO.read(file.toFile());
            if (strip == null) return Done.refused(name + ".png is not an image.");
            frames = FrameStrip.split(strip, count);
        } catch (IOException | IllegalArgumentException e) {
            return Done.refused("Could not read " + name + ".png: " + e.getMessage());
        }
        BufferedImage kept = frames.getFirst();
        if (frame.getWidth() != kept.getWidth() || frame.getHeight() != kept.getHeight()) {
            return Done.refused("A frame of " + name + " is " + kept.getWidth() + "×" + kept.getHeight() + ", not "
                    + frame.getWidth() + "×" + frame.getHeight() + ": cut it in the picture's own box.");
        }
        double confidence = SETTINGS.read(services).orElse(BotSettings.DEFAULTS).confidence();
        for (int i = 0; i < frames.size(); i++) {
            if (shows(frames.get(i), frame, confidence)) {
                return Done.refused("It shows nothing new: frame " + (i + 1) + " of " + name + " already matches it.");
            }
        }
        List<BufferedImage> grown = new ArrayList<>(frames);
        grown.add(frame);
        Optional<String> refused = TemplateUses.cut(services, name, held.get().withFrames(count + 1));
        if (refused.isPresent()) return Done.refused("No frame added: " + refused.get());
        try {
            TemplateLibrary.replaceImage(file, FrameStrip.join(grown));
        } catch (IOException e) {
            Optional<String> back = TemplateUses.cut(services, name, held.get());
            return Done.refused("Failed to add the frame: " + e.getMessage()
                    + back.map(why -> " — and the bot still says " + (count + 1) + " frames: " + why).orElse(""));
        }
        return Done.ok("Added frame " + (count + 1) + " to " + name + "; a run finds it on any of them.");
    }

    /** Whether {@code kept} is found on {@code frame}, of its size, at {@code confidence}. */
    private static boolean shows(BufferedImage kept, BufferedImage frame, double confidence) {
        try (ImageTemplate template = ImageTemplate.of("kept", kept)) {
            MatchResult match = ImageFinder.bestMatch(template, new StillSource(frame));
            return match.isFound() && match.confidence() >= confidence;
        }
    }
}
