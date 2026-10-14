package com.botmaker.sdk.plugin.assist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the assistant learns about a game stays in the bot, one section at a time. */
class GameNotesTest {

    private static String read(Path bot, String section) {
        String said = GameNotes.read(bot, section);
        assertTrue(said.startsWith(GameNotes.FROM), "the notes are said to be the bot's, not orders: " + said);
        return said.substring(GameNotes.FROM.length());
    }

    @Test
    void aSectionIsAddedRewrittenAndRemovedAndTheRestKept(@TempDir Path bot) throws Exception {
        assertTrue(GameNotes.read(bot, "").startsWith("The bot has no notes"));

        assertEquals("Added Hotkeys to GAME.md.", GameNotes.write(bot, "Hotkeys", "Q opens Quests.\n"));
        assertEquals("Added Screens to GAME.md.", GameNotes.write(bot, " Screens ", "Town: the home picture."));
        assertEquals("## Hotkeys\n\nQ opens Quests.", read(bot, "hotkeys"));

        assertEquals("Rewrote Hotkeys in GAME.md.", GameNotes.write(bot, "hotkeys", "Q opens Quests.\nM opens the Map."));
        String notes = Files.readString(bot.resolve(GameNotes.FILE));
        assertTrue(notes.startsWith("# The game\n"), notes);
        assertTrue(notes.contains(GameNotes.SHAPE), "a new file says how it is laid out: " + notes);
        assertTrue(notes.contains("## Hotkeys\n\nQ opens Quests.\nM opens the Map.\n\n## Screens\n\nTown: the home"
                + " picture.\n"), "the heading kept as written, any case finds it: " + notes);
        assertTrue(notes.endsWith("picture.\n"), "one line break at the end: " + notes);

        assertEquals("Removed Hotkeys in GAME.md.", GameNotes.write(bot, "Hotkeys", ""));
        assertFalse(read(bot, "").contains("Hotkeys"));
        assertTrue(read(bot, "").contains("## Screens"));
    }

    @Test
    void codeIsNotAHeadingAndABodysHeadingStartsNoSection(@TempDir Path bot) throws Exception {
        GameNotes.write(bot, "Code", "Look:\n```\n## fenced\n```\n~~~\n## tilde\n~~~\n## demoted\nUnder\n---");
        GameNotes.write(bot, "Screens", "Town.");
        assertEquals("## Code\n\nLook:\n```\n## fenced\n```\n~~~\n## tilde\n~~~\n### demoted\n### Under",
                read(bot, "Code"));
        GameNotes.write(bot, "Code", "Shorter.");
        assertEquals("## Code\n\nShorter.", read(bot, "Code"));
        assertEquals("## Screens\n\nTown.", read(bot, "Screens"));

        // An author's own spelling: an underlined heading, and one inside a quote that is not a section.
        Files.writeString(bot.resolve(GameNotes.FILE), "# Mine\n\nKeys\n----\n\nQ.\n\n> ## quoted\n\n## Map\n\nM.\n");
        assertEquals("Keys\n----\n\nQ.\n\n> ## quoted", read(bot, "Keys"));
        assertEquals(Map.of("Keys", "Q.\n\n> ## quoted", "Map", "M."), GameNotes.sections(bot),
                "each section's text without its heading, an underlined one too");
        GameNotes.write(bot, "Keys", "Q opens Quests.");
        assertEquals("# Mine\n\n## Keys\n\nQ opens Quests.\n\n## Map\n\nM.\n",
                Files.readString(bot.resolve(GameNotes.FILE)), "every other line as the author wrote it");
    }

    @Test
    void aMissingSectionOrABadHeadingIsRefused(@TempDir Path bot) throws Exception {
        assertTrue(GameNotes.sections(bot).isEmpty(), "no notes, no section");
        GameNotes.write(bot, "Screens", "Town.");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> GameNotes.read(bot, "Hotkeys"));
        assertEquals("No section Hotkeys in GAME.md. It has: Screens.", missing.getMessage());
        assertThrows(IllegalArgumentException.class, () -> GameNotes.write(bot, "Hotkeys", ""));
        assertThrows(IllegalArgumentException.class, () -> GameNotes.write(bot, "## Hotkeys", "Q."));
        assertThrows(IllegalArgumentException.class, () -> GameNotes.write(bot, "Two\nlines", "Q."));
        assertThrows(IllegalArgumentException.class, () -> GameNotes.write(bot, "a\rb", "Q."));
        assertThrows(IllegalArgumentException.class, () -> GameNotes.write(bot, " ", "Q."));

        Files.write(bot.resolve(GameNotes.FILE), new byte[] {'#', ' ', (byte) 0xE9, '\n'});
        assertTrue(read(bot, "").startsWith("# "), "a byte not UTF-8 still reads");
    }

    @Test
    void aLinkToAnotherFileIsNeverRead(@TempDir Path bot, @TempDir Path home) throws Exception {
        Path secret = Files.writeString(home.resolve("secret.md"), "## Key\n\nhunter2\n");
        Files.createSymbolicLink(bot.resolve(GameNotes.FILE), secret);
        assertThrows(IllegalArgumentException.class, () -> GameNotes.read(bot, ""));
        assertThrows(IllegalArgumentException.class, () -> GameNotes.sections(bot));
        assertThrows(IllegalArgumentException.class, () -> GameNotes.write(bot, "Map", "- Quests: Q."));
        assertEquals("## Key\n\nhunter2\n", Files.readString(secret), "the file it points at is left alone");
    }

    // ── a section's lines ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void aMarkerPaysTheDebtAndOtherCutsAreUntried() {
        String opened = "Entrance: home › click engineer_home\nOwed: marker";
        String marked = GameNotes.withMarker(opened, "engineer_title");
        assertEquals("Entrance: home › click engineer_home\nMarker: engineer_title", marked);
        assertEquals("Entrance: home › click engineer_home\nMarker: gear_engineer",
                GameNotes.withMarker(marked, "gear_engineer"), "one marker, the latest");
        assertEquals("Marker: a\nNotes.", GameNotes.withMarker("- Owed: marker — it animates\nNotes.", "a"),
                "a debt with a reason is paid too");
        assertEquals("Marker: a\nOwed: marker-less icons", GameNotes.withMarker("Owed: marker-less icons", "a"),
                "a debt that only starts with the word is not the marker's");

        String cut = GameNotes.withUntried(marked, "claim_engineer");
        assertEquals(marked + "\nUntried: claim_engineer", cut);
        assertEquals(marked + "\nUntried: claim_engineer, garage_engineer",
                GameNotes.withUntried(cut, "garage_engineer"));
        assertEquals(cut, GameNotes.withUntried(cut, "claim_engineer"), "a picture is listed once");
        assertEquals(List.of("claim_engineer"), GameNotes.untried(cut));
        assertEquals(Optional.of("engineer_title"), GameNotes.marker(cut));
    }

    @Test
    void anInputTriedLeavesTheUntriedLineAndTheLastSaysTheShape() {
        String text = "Marker: m\nUntried: a, b";
        String one = GameNotes.withTried(text, "a", GameNotes.OPENS + "Bag");
        assertEquals("Marker: m\nUntried: b\n- tried a: opens Bag", one);
        assertEquals(one, GameNotes.withUntried(one, "a"), "a picture tried is not untried again when recut");
        String both = GameNotes.withTried(one, "b", GameNotes.OPENS + "Party");
        assertEquals("Marker: m\n- tried a: opens Bag\n- tried b: opens Party\nShape: menu", both);
        String again = GameNotes.withTried(both, "b", GameNotes.IN_PLACE);
        assertEquals("Marker: m\n- tried a: opens Bag\n- tried b: " + GameNotes.IN_PLACE + "\nShape: activity", again,
                "tried again, its line and the shape are replaced");
        assertEquals(Optional.of("menu"), GameNotes.shapeOf("- tried a: opens Bag\n- tried g: never, spends gems"));
        assertEquals("Marker: m\n- tried key Q: opens Quests", GameNotes.withTried("Marker: m", "key Q",
                GameNotes.OPENS + "Quests"), "one hotkey on a screen with nothing cut says no shape");
    }

    @Test
    void aScreenMappedAgainLosesItsOldEntranceAndKeepsItsBulletAndWords() {
        assertEquals("- Bag: home › click bag_home. chests, daily",
                GameNotes.withMapLine("- Bag: home › key B. chests, daily", "Bag", "home › click bag_home"));
        assertEquals("1. Bag: home › key B. chests",
                GameNotes.withMapLine("1. **Bag:** chests", "Bag", "home › key B"));
        assertEquals("- Bag: home › click bag. chests", GameNotes.withMapLine("- Bag: home › key Page Down. chests",
                "Bag", "home › click bag"), "a key of two words goes whole");
        assertEquals(Optional.of("Bag"), GameNotes.mapName("1. **Bag:** chests", "bag"));
    }
}
