package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.vision.TemplateNames;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A picture as the bot's Java names it — the constant {@code Pictures.ORE} in the open set
 * {@code @SdkValue(SdkValue.Id.PICTURES)} — and the four changes the picture library makes to it.
 *
 * <h2>The host changes the Java, by binding; this class says which constant</h2>
 *
 * <p>A picture's uses are what javac resolves to its constant — not tokens found by text, which miss a static
 * import, a class the user renamed, and the declaration itself —
 * ({@link PluginValues#uses}), and a rename, repoint or remove is the host's, compiled as the whole bot before
 * it lands. What stays here is the mapping from a file name to a constant ({@link TemplateNames#constantFor})
 * and the order of steps.
 *
 * <p><b>A path literal the user wrote in their own code is theirs</b>: nothing matches it any more. A
 * picture whose name predates the constant rule has no constant, so it has no uses to find — and every
 * picture captured or imported since is {@linkplain #declare declared} as one.
 */
public final class TemplateUses {

    /** The open set the pictures are: {@code Pictures.java}. */
    static final ManagedSet<ImageTemplate> SET = ManagedSet.of(SdkValues.PICTURES);

    private TemplateUses() {}

    /**
     * Everything found for one template. Empty means it can be deleted with nothing to fix.
     *
     * <p>The {@link PluginValues.Use}s inside are the host's own record, passed through rather than re-wrapped:
     * a file and a line are not this plugin's vocabulary.
     */
    public record Scan(String baseName, List<PluginValues.Use> uses) {

        public boolean isEmpty() {
            return uses.isEmpty();
        }

        /** How many distinct files use it — what a refusal message leads with. */
        public int fileCount() {
            return (int) uses.stream().map(PluginValues.Use::file).distinct().count();
        }

        /** "3 uses in 2 files" — the phrase both the delete refusal and the rename report open with. */
        public String describe() {
            return uses.size() + (uses.size() == 1 ? " use" : " uses")
                    + " in " + fileCount() + (fileCount() == 1 ? " file" : " files");
        }
    }

    /** Every use of the template called {@code baseName}'s constant; empty when it has none. */
    public static Scan find(PluginValues values, String baseName) {
        String constant = declared(values, baseName);
        return new Scan(baseName, constant == null ? List.of() : List.copyOf(SET.uses(values, constant)));
    }

    /**
     * Declares {@code Pictures.<NAME>} for a picture that can have a constant and has none yet — what a
     * capture and an import do, so the canvas writes {@code Pictures.ORE} rather than the path.
     *
     * @return empty when it is declared now or cannot be; else the host's sentence
     */
    public static Optional<String> declare(PluginValues values, String baseName) {
        String constant = TemplateNames.constantFor(baseName);
        if (constant == null || SET.contains(values, constant)) return Optional.empty();
        return SET.add(values, constant, picture(baseName));
    }

    /**
     * Writes {@code Pictures.<NAME>} as cut at {@code cut} — {@code .at(Rect)}, and {@code .in(Size)} when its frame
     * is not the bot's screen size — declaring it when it has no constant yet. What a capture and a replacement do,
     * so where a picture came from is in the bot's Java. On the FX thread.
     *
     * @return empty when written or when the picture can have no constant; else the host's sentence
     */
    public static Optional<String> cut(StudioServices services, String baseName, FrameCut cut) {
        PluginValues values = services.pluginValues();
        String constant = TemplateNames.constantFor(baseName);
        if (constant == null) return Optional.empty();
        try {
            if (!SET.contains(values, constant)) {
                return SET.add(values, constant, ScreenSize.cut(services, picture(baseName), cut));
            }
            Optional<ValueContext> ctx = SET.open(values, constant);
            Optional<ImageTemplate> held = ctx.flatMap(SET::read);
            if (held.isEmpty()) {
                return Optional.of("Pictures." + constant + " is written by hand; it was left as it is.");
            }
            // Its own path, which the user may have pointed elsewhere: only where it was cut changes.
            ctx.get().set(ScreenSize.cut(services, new ImageTemplate(held.get().filePath()), cut));
            return Optional.empty();
        } catch (RuntimeException refused) {
            return Optional.of("Pictures." + constant + " was not rewritten: " + refused.getMessage());
        }
    }

    /**
     * Renames {@code oldName}'s constant and every use to {@code newName}'s, then points it at the new file.
     * Lossless — the same picture under a new name — so nothing is marked. A picture with no constant yet is
     * declared under the new name instead.
     *
     * @return empty when done; else why nothing was renamed
     */
    public static Optional<String> rename(PluginValues values, String oldName, String newName) {
        String from = declared(values, oldName);
        if (from == null) return declare(values, newName);
        String to = TemplateNames.constantFor(newName);
        if (to == null) return Optional.of("\"" + newName + "\" cannot be a constant's name, so "
                + TemplateNames.CLASS_NAME + "." + from + " cannot follow it.");
        Optional<String> refused = SET.rename(values, from, to);
        if (refused.isPresent()) return refused;
        return SET.open(values, to).map(value -> {
            // Where it was cut stays: the same picture under a new name.
            ImageTemplate held = SET.read(value).orElse(null);
            ImageTemplate renamed = picture(newName);
            value.set(held == null ? renamed : renamed.at(held.place()).in(held.frame()));
            return Optional.<String>empty();
        }).orElse(Optional.of(TemplateNames.CLASS_NAME + "." + to + " was renamed, but its path could not be "
                + "rewritten."));
    }

    /**
     * Points every use of {@code oldName}'s constant at {@code replacement}'s, declaring that one first when
     * it has none. A guess — the bot now watches for a different picture — so each function it touched is
     * marked with {@link #repointNote}. Empty when there was nothing to point.
     */
    public static Optional<String> repoint(PluginValues values, String oldName, String replacement) {
        String from = declared(values, oldName);
        if (from == null || SET.uses(values, from).isEmpty()) return Optional.empty();
        Optional<String> undeclared = declare(values, replacement);
        if (undeclared.isPresent()) return undeclared;
        String to = TemplateNames.constantFor(replacement);
        if (to == null) return Optional.of("\"" + replacement + "\" has no constant to point "
                + TemplateNames.CLASS_NAME + "." + from + "'s uses at.");
        return SET.repoint(values, from, to, repointNote(oldName, replacement));
    }

    /**
     * Removes {@code baseName}'s constant, as its file goes. Refused by the host while anything uses it; empty
     * when it has none.
     */
    public static Optional<String> forget(PluginValues values, String baseName) {
        String constant = declared(values, baseName);
        return constant == null ? Optional.empty() : SET.remove(values, constant);
    }

    /**
     * Every picture whose file is gone while something still names it — a tag in the manifest, or a
     * {@code Pictures} constant — sorted. A file deleted in a file manager or lost to a checkout left both
     * behind, and an untagged picture was invisible to a manifest-only check until a run failed to load it.
     *
     * <p>Only a constant written as one of ours counts ({@code ORE = new ImageTemplate(".../ore.png")}): one the
     * user pointed at some other path is theirs, and nothing here answers for it.
     */
    public static List<String> missing(PluginValues values, Path resourcesDir) {
        Set<String> gone = new TreeSet<>(TemplateLibrary.missingTemplates(resourcesDir));
        for (String member : SET.members(values)) {
            String baseName = TemplateNames.baseNameFor(member);
            if (baseName == null || TemplateLibrary.exists(resourcesDir, baseName)) continue;
            String path = SET.read(values, member)
                    .map(ImageTemplate::filePath)
                    .orElse(null);
            if (TemplateNames.pathFor(baseName).equals(path)) gone.add(baseName);
        }
        return List.copyOf(gone);
    }

    /**
     * What a repointed block leaves the user to check.
     *
     * <p>Named rather than inlined because both places that repoint — deleting a template that is in use, and
     * repairing one whose file has gone — owe the same sentence: the blocks compile and run, and they are now
     * looking for a different picture.
     */
    public static String repointNote(String oldName, String replacement) {
        return "this looked for the template \"" + oldName + "\", which is gone — it now looks for \""
                + replacement + "\", which may not be what it should be watching for.";
    }

    /** {@code baseName}'s constant when the open set declares it, else null. */
    private static String declared(PluginValues values, String baseName) {
        String constant = TemplateNames.constantFor(baseName);
        return constant != null && SET.contains(values, constant) ? constant : null;
    }

    private static ImageTemplate picture(String baseName) {
        return new ImageTemplate(TemplateLibrary.pathForName(baseName));
    }
}
