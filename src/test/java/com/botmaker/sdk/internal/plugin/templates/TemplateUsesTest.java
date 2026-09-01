package com.botmaker.sdk.internal.plugin.templates;

import com.botmaker.plugin.api.Sources;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spellings half of a template rewrite — pure string work, which is the point of it being separate.
 *
 * <p>Nothing here needs a host: matching a needle and writing a file are {@code Sources}' job and are tested
 * in the editor's own module. What is tested here is the only thing this plugin knows that the host cannot —
 * that {@code ore.png} is written two ways, and which one a repoint should produce.
 */
class TemplateUsesTest {

    @Test
    void aLowercaseNameHasBothSpellings() {
        assertEquals(List.of("Templates.ORE", "\"src/main/resources/images/ore.png\""),
                TemplateUses.needlesFor("ore"));
    }

    @Test
    void aNameThatCannotBeAConstantHasOnlyItsPath() {
        assertAll(
                () -> assertEquals(List.of("\"src/main/resources/images/Gold-Ore.png\""),
                        TemplateUses.needlesFor("Gold-Ore"),
                        "mixed case and a dash: no constant is generated, so there is none to search for"),
                () -> assertEquals(List.of("\"src/main/resources/images/gold_ore.png\""),
                        TemplateUses.needlesFor("gold_ore").subList(1, 2),
                        "an underscore is fine in an identifier, so this one does have both"));
    }

    @Test
    void aRepointReplacesEachSpellingWithTheSameSpelling() {
        Map<String, String> repointing = TemplateUses.repointing("ore", "gold");

        assertEquals(Map.of(
                        "Templates.ORE", "Templates.GOLD",
                        "\"src/main/resources/images/ore.png\"", "\"src/main/resources/images/gold.png\""),
                repointing);
    }

    @Test
    void theConstantLeadsSoItIsAppliedFirst() {
        assertEquals("Templates.ORE", TemplateUses.repointing("ore", "gold").keySet().iterator().next(),
                "the host applies these in iteration order, first match wins");
    }

    /**
     * The one asymmetry, and the only case where a repoint changes how a template is spelled rather than which
     * one is named: the new name has no constant, so there is nothing to point the old constant at but the
     * path.
     */
    @Test
    void aConstantRepointedAtANameWithNoConstantBecomesThePath() {
        assertEquals("\"src/main/resources/images/Gold-Ore.png\"",
                TemplateUses.repointing("ore", "Gold-Ore").get("Templates.ORE"));
    }

    @Test
    void aScanCountsUsesAndFiles() {
        TemplateUses.Scan scan = new TemplateUses.Scan("ore", List.of(
                new Sources.Use(Path.of("/bot/Main.java"), 4, "find(Templates.ORE);"),
                new Sources.Use(Path.of("/bot/Main.java"), 9, "find(Templates.ORE);"),
                new Sources.Use(Path.of("/bot/Mining.java"), 2, "find(Templates.ORE);")));

        assertAll(
                () -> assertEquals(2, scan.fileCount()),
                () -> assertEquals("3 uses in 2 files", scan.describe()));
    }

    @Test
    void oneUseIsSingularOnBothCounts() {
        assertEquals("1 use in 1 file", new TemplateUses.Scan("ore",
                List.of(new Sources.Use(Path.of("/bot/Main.java"), 4, "find(Templates.ORE);"))).describe());
    }

    @Test
    void aRepointNoteNamesBothTemplates() {
        String note = TemplateUses.repointNote("ore", "gold");

        assertAll(
                () -> assertTrue(note.contains("\"ore\"")),
                () -> assertTrue(note.contains("\"gold\"")),
                () -> assertTrue(note.contains("may not be what it should be watching for"),
                        "the sentence says what is left to check, not merely what happened"));
    }
}
