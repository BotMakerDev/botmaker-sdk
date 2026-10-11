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
        Font font = new Font(Font.MONOSPACED, Font.BOLD, 22);
        BufferedImage image = new BufferedImage(20 + text.length() * 16, 36, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setColor(new Color(0x10, 0x20, 0x50));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(new Color(0xf0, 0xd0, 0x30));
            g.setFont(font);
            g.drawString(text, 8, 26);
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
        List<Integer> saved = DigitCuts.save(resources, "gold", number, boxes, "12,50");
        assertEquals(List.of(1, 2, 5, 0), saved, "the comma is not a digit");
        assertTrue(Files.isRegularFile(resources.resolve("digits/gold/5.png")));
        assertEquals("src/main/resources/digits/gold", DigitCuts.pathFor("gold"));
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
    void aDigitTheSetLacksLeavesTheNumberUnread(@TempDir Path resources) throws IOException {
        BufferedImage some = shown("015");
        DigitCuts.save(resources, "gold", some, DigitCuts.characters(some), "015");
        assertEquals(OptionalLong.empty(), DigitReader.read(shown("1751"), 1,DigitCuts.held(resources, "gold"),
                0.9, "gold"), "not 151");
    }
}
