package com.botmaker.sdk.internal.plugin.templates;

import com.botmaker.plugin.api.Sources;
import com.botmaker.sdk.authoring.TemplateLibrary;
import com.botmaker.sdk.authoring.TemplateNames;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a template is spelled in a bot's own source, and therefore what to search for when one is renamed,
 * deleted or found to be missing.
 *
 * <h2>Why this is the plugin's half and {@link Sources} is the host's</h2>
 *
 * <p>Until 2026-09-01 all of this was one class in the editor, {@code TemplateReferences}, and it had to be:
 * the walk it ran reaches the editor's open buffers, snapshots the project's history and writes
 * {@code @NeedsReview}, none of which a plugin can do. But it also knew that {@code ore.png} is written
 * {@code Templates.ORE}, which is a fact about this plugin's generated class and about nobody else's — so the
 * editor was holding one plugin's vocabulary on its behalf, and no second plugin could have had the same
 * service for a concept of its own.
 *
 * <p>The split is exactly the platform's capability/vocabulary line. The host takes needles and rewrites
 * source; this class is where the needles come from.
 *
 * <h2>The two spellings</h2>
 *
 * <p>A template is named either by the generated constant ({@code Templates.ORE}, possibly package-qualified)
 * or, when its name predates the lowercase rule and so has no constant, by its project-relative path as a
 * whole string literal ({@code "src/main/resources/images/ore.png"}). Both are searched, because the two kinds
 * coexist in one project — see {@link TemplateNames} for why a name that cannot be a constant simply has none
 * rather than being sanitised into one.
 *
 * <p><b>A repoint replaces each spelling with the same spelling</b>, so a qualified use keeps its qualifier
 * and a path literal stays a path literal. The one asymmetry: a template whose <em>new</em> name has no
 * constant has its constant uses rewritten to the path literal instead, which is the only thing left to write.
 */
public final class TemplateUses {

    private TemplateUses() {}

    /**
     * Everything found for one template. Empty means it can be deleted with nothing to fix.
     *
     * <p>The {@link Sources.Use}s inside are the host's own record, passed through rather than re-wrapped:
     * a file and a line are not this plugin's vocabulary, and copying them into a parallel record would be one
     * more thing to keep in step for no gain.
     */
    public record Scan(String baseName, List<Sources.Use> uses) {

        public boolean isEmpty() {
            return uses.isEmpty();
        }

        /** How many distinct files use it — what a refusal message leads with. */
        public int fileCount() {
            return (int) uses.stream().map(Sources.Use::file).distinct().count();
        }

        /** "3 uses in 2 files" — the phrase both the delete refusal and the rename report open with. */
        public String describe() {
            return uses.size() + (uses.size() == 1 ? " use" : " uses")
                    + " in " + fileCount() + (fileCount() == 1 ? " file" : " files");
        }
    }

    /**
     * Both spellings of {@code baseName}, as needles the host can match.
     *
     * <p>One entry for a name with no constant, two otherwise. Never empty: every template has a path.
     */
    public static List<String> needlesFor(String baseName) {
        String constant = TemplateNames.constantFor(baseName);
        String literal = literalFor(baseName);
        return constant == null ? List.of(literal)
                : List.of(TemplateNames.CLASS_NAME + "." + constant, literal);
    }

    /**
     * What each spelling of {@code oldName} becomes when the template is repointed at {@code newName}.
     *
     * <p>Ordered, and the order matters to the host: replacements are applied in iteration order and the first
     * to match a line wins. The constant leads because it is the spelling that can contain the other's — a
     * line holding {@code Templates.ORE} has no path literal in it, but a rewrite that ran the path first
     * would be looking at already-rewritten text.
     */
    public static Map<String, String> repointing(String oldName, String newName) {
        Map<String, String> replacements = new LinkedHashMap<>();
        String oldConstant = TemplateNames.constantFor(oldName);
        String newConstant = TemplateNames.constantFor(newName);
        if (oldConstant != null) {
            replacements.put(TemplateNames.CLASS_NAME + "." + oldConstant,
                    newConstant == null ? literalFor(newName) : TemplateNames.CLASS_NAME + "." + newConstant);
        }
        replacements.put(literalFor(oldName), literalFor(newName));
        return replacements;
    }

    /** Every use of the template called {@code baseName}, through {@code sources}. */
    public static Scan find(Sources sources, String baseName) {
        return new Scan(baseName, List.copyOf(sources.find(needlesFor(baseName))));
    }

    /**
     * Points every use of {@code oldName} at {@code newName} and returns the files that changed.
     *
     * <p>{@code reviewNote} is the caller's judgement and this class does not make it: a <b>rename</b> is
     * lossless — the same picture under a new name — and passes null, while pointing blocks at a
     * <em>different</em> picture changes what the bot watches for and must say so.
     */
    public static List<Path> repoint(Sources sources, String oldName, String newName,
                                     String historyLabel, String reviewNote) {
        return sources.replace(repointing(oldName, newName), historyLabel, reviewNote);
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

    /** The path literal, quotes included, so the host matches it as one token rather than as a dotted name. */
    private static String literalFor(String baseName) {
        return '"' + TemplateLibrary.pathForName(baseName) + '"';
    }
}
