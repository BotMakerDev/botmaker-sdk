package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.vision.DigitReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A number on screen cut into digit pictures, and numbers read back with them. */
class DigitCutsTest {

    /** {@code text} in a game-like font: yellow on dark blue, not antialiased, with room around it. */
    private static BufferedImage shown(String text) {
        return shown(text, 22);
    }

    /** {@code text} at {@code points}, the image scaled to it. */
    private static BufferedImage shown(String text, int points) {
        Font font = new Font(Font.MONOSPACED, Font.BOLD, points);
        BufferedImage image = new BufferedImage(20 + text.length() * points * 16 / 22, points * 36 / 22,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setColor(new Color(0x10, 0x20, 0x50));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(new Color(0xf0, 0xd0, 0x30));
            g.setFont(font);
            g.drawString(text, 8, points * 26 / 22);
        } finally {
            g.dispose();
        }
        return image;
    }

    @Test
    void eachCharacterIsCutApartAtTheNumbersHeight(@TempDir Path resources) throws IOException {
        BufferedImage number = shown("12,50");
        List<Rect> boxes = DigitCuts.characters(number);
        assertEquals(5, boxes.size(), "four digits and the comma");
        assertTrue(boxes.stream().map(Rect::height).distinct().count() == 1, "one line height");
        List<Character> saved = DigitCuts.save(resources, "gold", number, boxes, "12,50");
        assertEquals(List.of('1', '2', ',', '5', '0'), saved, "the comma is a mark of the set");
        assertTrue(Files.isRegularFile(resources.resolve("digits/gold/5.png")));
        assertTrue(Files.isRegularFile(resources.resolve("digits/gold/comma.png")));
        assertEquals("digits/gold", DigitCuts.pathFor("gold"));
    }

    @Test
    void aDecimalAndAMultiplierAreSavedAndReadBack(@TempDir Path resources) throws IOException {
        BufferedImage all = shown("0123456789");
        DigitCuts.save(resources, "gold", all, DigitCuts.characters(all), "0123456789");
        BufferedImage marked = shown("4.5K");
        DigitCuts.save(resources, "gold", marked, DigitCuts.characters(marked), "4.5k");
        List<DigitReader.Glyph> glyphs = DigitCuts.held(resources, "gold");
        assertEquals(OptionalLong.of(37100), DigitReader.read(shown("37.1K"), 1, glyphs, 0.9, "gold"));
    }

    @Test
    void touchingCharactersAreSplitToTheCountShown() {
        // A box of two glyphs drawn touching: no empty column between them.
        BufferedImage joined = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = joined.createGraphics();
        try {
            g.setColor(new Color(0x10, 0x20, 0x50));
            g.fillRect(0, 0, 40, 20);
            g.setColor(new Color(0xf0, 0xd0, 0x30));
            g.fillRect(4, 3, 14, 14);
            g.fillRect(18, 9, 2, 2);
            g.fillRect(20, 3, 14, 14);
        } finally {
            g.dispose();
        }
        assertEquals(1, DigitCuts.characters(joined).size());
        List<Rect> two = DigitCuts.characters(joined, 2);
        assertEquals(2, two.size());
        assertTrue(two.get(0).x() + two.get(0).width() <= 21 && two.get(1).x() >= 17, "cut at the thin join");
    }

    @Test
    void numbersAreReadBackWithTheDigitsCut(@TempDir Path resources) throws IOException {
        BufferedImage all = shown("0123456789");
        DigitCuts.save(resources, "gold", all, DigitCuts.characters(all), "0123456789");
        List<DigitReader.Glyph> glyphs = DigitCuts.held(resources, "gold");
        assertEquals(10, glyphs.size());
        assertEquals(OptionalLong.of(40718), DigitReader.read(shown("40718"), 1, glyphs, 0.9, "gold"));
        assertEquals(OptionalLong.of(1111), DigitReader.read(shown("1111"), 1, glyphs, 0.9, "gold"));
        assertEquals(OptionalLong.empty(), DigitReader.read(shown(""), 1, glyphs, 0.9, "gold"));
    }

    @Test
    void digitsAFewPixelsTallAreReadToo(@TempDir Path resources) throws IOException {
        BufferedImage all = shown("0123456789", 10);
        List<Rect> boxes = DigitCuts.characters(all);
        assertEquals(10, boxes.size());
        assertTrue(boxes.getFirst().height() < 12, "small enough to be enlarged: " + boxes.getFirst());
        DigitCuts.save(resources, "tiny", all, boxes, "0123456789");
        assertEquals(OptionalLong.of(3857), DigitReader.read(shown("3857", 10), 1, DigitCuts.held(resources, "tiny"),
                0.9, "tiny"));
    }

    @Test
    void aDigitTheSetLacksLeavesTheNumberUnread(@TempDir Path resources) throws IOException {
        BufferedImage some = shown("015");
        DigitCuts.save(resources, "gold", some, DigitCuts.characters(some), "015");
        assertEquals(OptionalLong.empty(), DigitReader.read(shown("1751"), 1,DigitCuts.held(resources, "gold"),
                0.9, "gold"), "not 151");
    }
}
