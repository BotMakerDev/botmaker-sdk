package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TemplateManifest} — the tag model behind "organise templates into categories", as {@link PictureTags} reads
 * it off the bot's Java.
 *
 * <p>Tested at this level because the tree in the resource manager and the submenus in the picker are both
 * renderings of {@link TemplateManifest#byTag}, and the property that matters (a template can be in two groups
 * without existing twice) is a property of this model, not of a JavaFX control.
 */
class TemplateManifestTest {

    private static TemplateManifest manifest(Map<String, List<String>> tags, String... custom) {
        TemplateManifest manifest = new TemplateManifest(Map.of(), new TreeSet<>(List.of(custom)));
        for (Map.Entry<String, List<String>> entry : tags.entrySet()) {
            manifest = manifest.withTags(entry.getKey(), entry.getValue());
        }
        return manifest;
    }

    @Test
    void aTemplateCanCarryTwoTagsAndIsStillOneTemplate() {
        TemplateManifest manifest = manifest(Map.of("gold_ore", List.of("Mining", "Shared")), "Shared");
        Map<String, List<String>> byTag = manifest.byTag(List.of("gold_ore"),
                TagCatalog.of(List.of("Mining"), manifest.customTags()));

        assertEquals(List.of("gold_ore"), byTag.get("Mining"));
        assertEquals(List.of("gold_ore"), byTag.get("Shared"));
        assertEquals(List.of("gold_ore"), byTag.get(TemplateManifest.ALL), "one template, listed once in All");
    }

    @Test
    void theListingIsOverTheDeclaredSetNotOverWhatWasAssigned() {
        TagCatalog catalog = TagCatalog.of(null, List.of("Shared", "Empty"));
        TemplateManifest manifest = manifest(Map.of("gold_ore", List.of("Shared"),
                // "Mining" was an activity that has since been removed: still on the picture, no longer declared.
                "orphan", List.of("Mining")), "Shared", "Empty");

        Map<String, List<String>> byTag = manifest.byTag(List.of("gold_ore", "orphan"), catalog);

        assertEquals(List.of(TemplateManifest.ALL, "Empty", "Shared", TemplateManifest.UNTAGGED),
                List.copyOf(byTag.keySet()), "All first, then the declared tags, then the leftovers");
        assertEquals(List.of("gold_ore", "orphan"), byTag.get(TemplateManifest.ALL));
        assertEquals(List.of(), byTag.get("Empty"), "a declared tag is a row even with nothing in it");
        assertEquals(List.of("gold_ore"), byTag.get("Shared"));
        // The orphan surfaces where someone will find it again, rather than under a tag that isn't there.
        assertEquals(List.of("orphan"), byTag.get(TemplateManifest.UNTAGGED));
    }

    @Test
    void anAssignmentFindsItsRowWhateverTheSpelling() {
        TagCatalog catalog = TagCatalog.of(null, List.of("Shared"));
        TemplateManifest manifest = manifest(Map.of("a", List.of("SHARED")), "Shared");

        assertEquals(List.of("a"), manifest.byTag(List.of("a"), catalog).get("Shared"));
    }

    @Test
    void tagsAreMatchedCaseInsensitivelySoATreeCannotShowBoth() {
        SortedSet<String> tags = manifest(Map.of("a", List.of("Mining", "mining"))).tagsOf("a");
        assertEquals(1, tags.size(), tags.toString());
    }

    @Test
    void theComputedBucketsCannotBeUsedAsRealTags() {
        // Otherwise a tag called "All" would compete with the row that means "everything".
        assertTrue(manifest(Map.of("a", List.of("All", "Untagged"))).tagsOf("a").isEmpty());
        assertTrue(manifest(Map.of(), "All").customTags().isEmpty());
    }

    @Test
    void tagsAreTrimmedAndBlankOnesAreNotTags() {
        assertEquals("Mining Camp", TemplateManifest.sanitizeTag("  Mining   Camp "));
        assertTrue(manifest(Map.of("a", List.of("  ", ""))).tagsOf("a").isEmpty());
        assertEquals(Set.of("Mining Camp"), Set.copyOf(manifest(Map.of("a", List.of(" Mining  Camp"))).tagsOf("a")));
    }

    @Test
    void aTagsWordsBecomeItsConstantsName() {
        assertEquals("ARENA_OF_KINGS", PictureTags.constantFor("Arena of Kings"));
        assertEquals("UI", PictureTags.constantFor("ui"));
        assertEquals("TAG_2X_SPEED", PictureTags.constantFor("2x speed!"));
        assertEquals("POP_UPS", PictureTags.constantFor("  pop-ups  "));
    }
}
