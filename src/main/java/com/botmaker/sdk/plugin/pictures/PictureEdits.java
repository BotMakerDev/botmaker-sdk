package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 🖼 Manage Pictures' rename, delete and replace, for a caller with no window: the assistant's picture tools.
 * Each takes the same steps in the same order as the window — the bot's Java first, so a change the host
 * refuses leaves the file where it was — and answers the sentence to show. On the FX thread, since the Java is
 * written there.
 */
public final class PictureEdits {

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
     * for review as Manage Pictures marks it.
     */
    public static Done delete(StudioServices services, String name, String pointUsesAt) {
        Path resources = services.resourcesDir();
        Path file = TemplateLibrary.fileForName(resources, name);
        if (TemplateLibrary.isDefaultTemplate(file)) return Done.refused("The default picture cannot be deleted.");
        TemplateUses.Scan scan = TemplateUses.find(services.pluginValues(), name);
        if (!scan.isEmpty()) {
            if (pointUsesAt == null || pointUsesAt.isBlank()) {
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
        if (kept.isPresent()) return Done.refused("Not deleted: " + kept.get());
        try {
            TemplateLibrary.deleteTemplate(resources, file);
        } catch (IOException e) {
            return Done.refused("Failed to delete " + name + ": " + e.getMessage());
        }
        return Done.ok(scan.isEmpty() ? "Deleted " + name + "."
                : "Deleted " + name + " after pointing " + scan.describe() + " at " + pointUsesAt.trim()
                        + "; each is marked for review.");
    }

    /**
     * Gives the picture {@code name} a new image, keeping its name, its tags and every use: the blocks that look
     * for it look for the new one.
     *
     * @param frameWidth the width of the frame it was cut from, for the resolution sidecar
     */
    public static Done replace(StudioServices services, String name, BufferedImage picture, int frameWidth,
                               int frameHeight) {
        Path file = TemplateLibrary.fileForName(services.resourcesDir(), name);
        if (TemplateLibrary.isDefaultTemplate(file)) return Done.refused("The default picture cannot be replaced.");
        try {
            TemplateLibrary.replaceImage(file, picture, frameWidth, frameHeight, null);
        } catch (IOException e) {
            return Done.refused("Failed to replace " + name + ": " + e.getMessage());
        }
        return Done.ok("Replaced the image of " + name + "; every block that uses it now looks for the new one.");
    }
}
