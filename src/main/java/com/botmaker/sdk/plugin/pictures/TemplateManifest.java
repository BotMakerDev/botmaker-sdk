package com.botmaker.sdk.plugin.pictures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which tags each picture carries, by its words — the whole of picture "organisation", read off the bot's Java by
 * {@link PictureTags} rather than kept beside the files (2026-10-14: {@code templates.json} is gone).
 *
 * <p><b>Why not folders.</b> A template's identity is the path its {@code Pictures} constant holds
 * ({@code new ImageTemplate("src/main/resources/images/accept.png")}), so moving one into a folder rewrites
 * the bot's source. Worse, a template shared by two activities would have to live in one folder or be
 * duplicated into both — and duplicates drift. Tags are many-to-one by construction: the file never moves,
 * {@link TemplateLibrary#pathForName} never changes, and a shared template simply carries both tags. The
 * gallery's rail lists the tags, so it still <em>reads</em> like folders.
 *
 * <p><b>Assignments here, the catalog elsewhere.</b> A picture's tags are its custom tags' words and its
 * activities' labels together; {@link TagCatalog} says which of them exist, and an assignment naming a tag the
 * catalog doesn't declare is inert rather than an error.
 *
 * <p>Immutable. Names are matched case-insensitively, the same way {@link TemplateLibrary#exists} treats template
 * files.
 */
public record TemplateManifest(Map<String, SortedSet<String>> tagsByTemplate, SortedSet<String> customTags) {

    /**
     * The bucket a template with no tags is shown under. Not stored — it is computed when listing, so
     * "untagged" can never go stale relative to the tags a template actually has.
     */
    public static final String UNTAGGED = "Untagged";

    /**
     * The bucket holding every template regardless of its tags. Like {@link #UNTAGGED} it is computed, never
     * stored and never assignable: it exists so "show me everything" is a row in the same list as the tags
     * rather than a separate mode the two renderings would each have to implement.
     */
    public static final String ALL = "All";

    /** The two computed buckets, which no real tag may shadow — checked wherever a tag is named. */
    public static boolean isSyntheticTag(String tag) {
        return ALL.equalsIgnoreCase(tag) || UNTAGGED.equalsIgnoreCase(tag);
    }

    public TemplateManifest {
        Map<String, SortedSet<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (tagsByTemplate != null) {
            tagsByTemplate.forEach((name, tags) -> {
                if (name == null || name.isBlank() || tags == null) return;
                SortedSet<String> clean = cleanTags(tags);
                if (!clean.isEmpty()) copy.put(name, clean);
            });
        }
        tagsByTemplate = Map.copyOf(copy);
        customTags = Collections.unmodifiableSortedSet(cleanTags(customTags == null ? Set.of() : customTags));
    }

    private static SortedSet<String> cleanTags(Collection<String> raw) {
        SortedSet<String> clean = newTagSet();
        raw.stream().map(TemplateManifest::sanitizeTag)
                .filter(t -> !t.isBlank() && !isSyntheticTag(t))
                .forEach(clean::add);
        return clean;
    }

    public static TemplateManifest empty() {
        return new TemplateManifest(Map.of(), Collections.emptySortedSet());
    }

    /** Tags sort case-insensitively so "Mining" and "mining" can't both appear in a tree. */
    private static SortedSet<String> newTagSet() {
        return new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Normalizes a user-entered tag: trims and collapses runs of whitespace. Unlike a template name (which
     * becomes a file name and so is restricted to {@code [a-z0-9_]}), a tag is only ever a label, so it
     * may contain spaces and punctuation. Blank means "no tag" — callers drop it.
     */
    public static String sanitizeTag(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** The tags of {@code templateName} (base name, no extension) — empty when it has none. */
    public SortedSet<String> tagsOf(String templateName) {
        SortedSet<String> tags = tagsByTemplate.get(templateName);
        return tags == null ? newTagSet() : tags;
    }

    /** This manifest with {@code templateName}'s tags replaced by {@code tags} (empty ⇒ untagged). */
    public TemplateManifest withTags(String templateName, Collection<String> tags) {
        Map<String, SortedSet<String>> next = new LinkedHashMap<>(tagsByTemplate);
        next.remove(templateName);
        next.put(templateName, new TreeSet<>(tags));
        return new TemplateManifest(next, customTags);
    }

    /**
     * The grouping the UI renders: {@link #ALL} first, then every tag {@code catalog} declares — in its
     * order, activity tags before custom ones — and finally {@link #UNTAGGED}.
     *
     * <p>A declared tag is listed <b>even when empty</b>, because the point of declaring is that the tag is there
     * to file things under. An assignment to an <b>undeclared</b> tag is ignored — it does not create a row, and a
     * template carrying nothing else counts as untagged — so a deleted activity cannot leave a ghost group behind,
     * and its templates surface where someone will find them again.
     */
    public Map<String, List<String>> byTag(Collection<String> allTemplateNames, TagCatalog catalog) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(allTemplateNames);
        grouped.put(ALL, all);
        // A second view of the same lists, keyed case-insensitively: "mining" on a template has to find the
        // row declared as "Mining", the way every other name comparison here does.
        Map<String, List<String>> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String tag : catalog.names()) {
            List<String> row = new ArrayList<>();
            grouped.put(tag, row);
            byName.put(tag, row);
        }

        List<String> untagged = new ArrayList<>();
        for (String name : all) {
            boolean filed = false;
            for (String tag : tagsOf(name)) {
                List<String> row = byName.get(tag);
                if (row == null) continue;  // assigned to a tag nothing declares: inert, not an error
                row.add(name);
                filed = true;
            }
            if (!filed) untagged.add(name);
        }
        if (!untagged.isEmpty()) grouped.put(UNTAGGED, untagged);
        return grouped;
    }
}
