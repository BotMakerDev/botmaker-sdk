package com.botmaker.sdk.plugin.pictures;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which tags a project has. The point of this type is that the answer is <em>finite and declared</em> rather
 * than "whatever was typed into a text field", so the tests are about which spellings are one tag and what
 * may be declared.
 */
class TagCatalogTest {

    @Test
    void tagsAreListedAlphabeticallyOncePerSpelling() {
        TagCatalog catalog = TagCatalog.of(List.of("Shared", "Popups", "shared"));

        assertEquals(List.of("Popups", "Shared"), catalog.names(), "alphabetically, one row whatever the case");
        assertTrue(catalog.isDeclared("popups"), "spelling is not what identifies a tag");
    }

    @Test
    void whatIsNotDeclaredIsNotATag() {
        TagCatalog catalog = TagCatalog.of(List.of("Mining"));

        assertFalse(catalog.isDeclared("Minning"), "the typo that free text used to make a real tag");
        assertNull(catalog.find("Minning"));
    }

    @Test
    void aSelectionIsNarrowedToDeclaredTagsAndRespelledTheirWay() {
        TagCatalog catalog = TagCatalog.of(List.of("Mining", "Shared"));

        // Whatever order a dialog hands back, what gets saved is catalog order, catalog spelling, and only
        // tags that exist — so a stale selection can't put a dead tag back into the manifest.
        assertEquals(List.of("Mining", "Shared"),
                catalog.declaredOnly(List.of("shared", "gone", "  MINING  ")));
        assertEquals(List.of(), catalog.declaredOnly(List.of()));
    }

    @Test
    void aProjectWithNoTagsHasNoTags() {
        assertEquals(List.of(), TagCatalog.of(List.of()).names());
        assertEquals(List.of(), TagCatalog.of(null).names());
        assertEquals(List.of(), TagCatalog.empty().names());
    }

    @Test
    void aNewTagMayNotBeBlankBuiltInOrTaken() {
        TagCatalog catalog = TagCatalog.of(List.of("Shared"));

        assertTrue(catalog.nameProblem("", null).contains("enter a name"));
        assertTrue(catalog.nameProblem("all", null).contains("built-in group"));
        assertTrue(catalog.nameProblem("SHARED", null).contains("already a tag called \"Shared\""));
        assertNull(catalog.nameProblem("Loot", null));
    }

    @Test
    void aRenamedTagMayKeepItsOwnNameInAnotherCase() {
        TagCatalog catalog = TagCatalog.of(List.of("shared", "Loot"));

        assertNull(catalog.nameProblem("Shared", "shared"));
        assertTrue(catalog.nameProblem("loot", "shared").contains("already"));
    }
}
