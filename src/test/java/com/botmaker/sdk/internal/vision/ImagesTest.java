// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.vision;

import com.botmaker.sdk.api.geometry.Rect;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A part of a frame cut as its own image, an image resized, and a colour as a person writes it. */
class ImagesTest {

    @Test
    void aCutIsACopyOfItsAreaAndAResizeKeepsAPixel() {
        BufferedImage frame = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(12, 7, 0xFF0000);
        assertEquals(new Rect(0, 0, 40, 20), Images.bounds(frame));
        BufferedImage part = Images.cut(frame, new Rect(10, 5, 4, 4));
        assertEquals(0xFF0000, part.getRGB(2, 2) & 0xFFFFFF);
        frame.setRGB(12, 7, 0);
        assertEquals(0xFF0000, part.getRGB(2, 2) & 0xFFFFFF, "a copy, not a view of the frame");
        BufferedImage small = Images.scaled(frame, 0.5);
        assertEquals(20, small.getWidth());
        assertEquals(10, small.getHeight());
        assertEquals(1, Images.scaled(frame, 0.001).getWidth(), "never below one pixel");
    }

    @Test
    void aColourIsWrittenAsHexOrAsThreeNumbers() {
        assertEquals(Optional.of(new Color(255, 128, 0)), ColorText.parse("#FF8000"));
        assertEquals(Optional.of(new Color(255, 128, 0)), ColorText.parse("ff8000"));
        assertEquals(Optional.of(new Color(255, 128, 0)), ColorText.parse(" 255, 128 ,0 "));
        assertEquals(Optional.empty(), ColorText.parse("256,0,0"));
        assertEquals(Optional.empty(), ColorText.parse("#FFF"));
        assertEquals(Optional.empty(), ColorText.parse("red"));
        assertEquals(Optional.empty(), ColorText.parse(null));
        assertEquals("#FF8000", ColorText.hex(new Color(255, 128, 0)));
    }

    /** nunu.ai's crops cut before their section faded in: its background, a grain of noise and nothing else. */
    @Test
    void aPictureOfOneColourWithItsGrainIsFlatAndOneWithAWordIsNot() {
        BufferedImage background = new BufferedImage(78, 30, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 78; x++) {
            for (int y = 0; y < 30; y++) background.setRGB(x, y, (x + y) % 3 == 0 ? 0x3A2A80 : 0x382878);
        }
        assertTrue(Images.flat(background), "the background's grain is no detail");
        BufferedImage word = Images.cut(background, Images.bounds(background));
        for (int x = 20; x < 50; x++) for (int y = 10; y < 18; y++) word.setRGB(x, y, 0xFFFFFF);
        assertFalse(Images.flat(word), "a word on it is");
    }

    @Test
    void aPartsPlaceGoesBackIntoTheWhole() {
        assertEquals(new Rect(15, 27, 4, 6), new Rect(5, 7, 4, 6).translate(10, 20));
    }
}
