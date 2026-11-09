package com.botmaker.sdk.plugin.pictures;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which tags a project has — the declared set, consulted before any tag is shown or assigned.
 *
 * <p><b>Why declared.</b> Tags used to come into existence by being typed: a batch capture started inside an
 * activity pre-filled that activity's name, a free-text field accepted anything, and the set of tags was
 * whatever happened to be in {@code templates.json} — so "Minning" was a tag, "mining" and "Mining" raced to
 * be the canonical spelling, and a tag disappeared when its last template was retagged. Declaring them first
 * makes the set finite and knowable: every assignment UI is a picklist over <em>this</em>, never a text
 * field, so a tag cannot be invented by a typo and cannot vanish by accident.
 *
 * <p>Every tag is the user's own, a constant of the bot's {@code Tags} class ({@link PictureTags}), edited in one
 * place. (An activity's tag, which followed its activity, went with flows on 2026-11-09.)
 *
 * <p>Assignments to tags this catalog doesn't declare are inert rather than erroneous — see
 * {@link TemplateManifest#byTag(Collection, TagCatalog)} for what that buys.
 */
public record TagCatalog(List<Tag> tags) {

    /** One declared tag. */
    public record Tag(String name) {}

    public TagCatalog {
        tags = List.copyOf(tags == null ? List.of() : tags);
    }

    public static TagCatalog empty() {
        return new TagCatalog(List.of());
    }

    /** The catalog for a project: its declared tags alphabetically, one per spelling whatever its case. */
    public static TagCatalog of(Collection<String> customTags) {
        Set<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (customTags != null) {
            for (String tag : customTags) {
                if (tag != null && !tag.isBlank()) sorted.add(tag);
            }
        }
        return new TagCatalog(sorted.stream().map(Tag::new).toList());
    }

    /** Every declared tag name, in catalog order. */
    public List<String> names() {
        return tags.stream().map(Tag::name).toList();
    }

    /** The declared tag spelled like {@code name} (case-insensitively), or {@code null} if there is none. */
    public Tag find(String name) {
        if (name == null) return null;
        String clean = TemplateManifest.sanitizeTag(name);
        return tags.stream().filter(t -> t.name().equalsIgnoreCase(clean)).findFirst().orElse(null);
    }

    /** True when {@code name} is a tag this project has. */
    public boolean isDeclared(String name) {
        return find(name) != null;
    }

    /**
     * Why {@code tag} (sanitized) can't be declared, phrased for the user, or null when it can — the one answer
     * the new-tag and rename-tag dialogs share. {@code current} is the tag being renamed, or null: it may keep
     * its own name in another case.
     */
    public String nameProblem(String tag, String current) {
        if (tag == null || tag.isBlank()) return "Please enter a name for the tag.";
        if (current != null && tag.equalsIgnoreCase(current)) return null;
        if (TemplateManifest.isSyntheticTag(tag)) {
            return "\"" + tag + "\" is a built-in group. Choose a different name.";
        }
        if (TemplateGalleryModel.isScreenKey(tag)) {
            return "A name starting with 🗺 is a screen of the bot's map. Choose a different name.";
        }
        Tag existing = find(tag);
        return existing == null ? null : "There is already a tag called \"" + existing.name() + "\".";
    }

    /**
     * {@code names} narrowed to the tags this catalog declares, spelled the way the catalog spells them and
     * ordered the way it orders them — what every assignment path saves, so a stale selection or a
     * hand-edited picture can't have an undeclared tag written back onto it.
     */
    public List<String> declaredOnly(Collection<String> names) {
        if (names == null) return List.of();
        Set<String> wanted = new LinkedHashSet<>();
        for (String name : names) {
            Tag tag = find(name);
            if (tag != null) wanted.add(tag.name());
        }
        return names().stream().filter(wanted::contains).toList();
    }
}
