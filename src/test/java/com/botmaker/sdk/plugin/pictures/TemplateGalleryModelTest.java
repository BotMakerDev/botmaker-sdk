package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.plugin.pictures.TemplateGalleryModel.Facts;
import com.botmaker.sdk.plugin.pictures.TemplateGalleryModel.Show;
import com.botmaker.sdk.plugin.pictures.TemplateGalleryModel.Sort;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gallery's sort, its several-tag pick and its twins: decisions, tested without a display. */
class TemplateGalleryModelTest {

    private static final Path A = Path.of("images/apple.png");
    private static final Path B = Path.of("images/Banana.png");
    private static final Path C = Path.of("images/cherry.png");

    @Test
    void picturesSortByNameByWhenTheyWereCutAndBySize() {
        Map<Path, Facts> facts = Map.of(A, new Facts(30, 100), B, new Facts(10, 900), C, new Facts(20, 100));
        List<Path> files = List.of(C, A, B);
        assertEquals(List.of(A, B, C), TemplateGalleryModel.sorted(files, Sort.NAME_UP, facts), "case aside");
        assertEquals(List.of(C, B, A), TemplateGalleryModel.sorted(files, Sort.NAME_DOWN, facts));
        assertEquals(List.of(A, C, B), TemplateGalleryModel.sorted(files, Sort.NEWEST, facts));
        assertEquals(List.of(B, C, A), TemplateGalleryModel.sorted(files, Sort.OLDEST, facts));
        assertEquals(List.of(B, A, C), TemplateGalleryModel.sorted(files, Sort.LARGEST, facts));
        assertEquals(List.of(A, C, B), TemplateGalleryModel.sorted(files, Sort.SMALLEST, facts), "ties by name");
        assertEquals(List.of(A, B, C), TemplateGalleryModel.sorted(files, Sort.SMALLEST, Map.of()),
                "nothing known: all alike, by name");
    }

    @Test
    void aRememberedSortOrFilterComesBackAndAnUnknownOneIsTheDefault() {
        for (Sort sort : Sort.values()) assertEquals(sort, Sort.fromId(sort.id()));
        for (Show show : Show.values()) assertEquals(show, Show.fromId(show.id()));
        assertEquals(Sort.NAME_UP, Sort.fromId(null));
        assertEquals(Show.ALL, Show.fromId("gone"));
    }

    @Test
    void severalTagsShowPicturesCarryingAnyOrEveryOne() {
        Map<String, List<Path>> byTag = Map.of(TemplateManifest.ALL, List.of(A, B, C),
                "Tavern", List.of(A, B), "Buttons", List.of(B, C));
        assertEquals(List.of(A, B), TemplateGalleryModel.underTags(byTag, List.of("Tavern"), true));
        assertEquals(List.of(A, B, C), TemplateGalleryModel.underTags(byTag, List.of("Buttons", "Tavern"), false));
        assertEquals(List.of(B), TemplateGalleryModel.underTags(byTag, List.of("Tavern", "Buttons"), true));
        assertEquals(List.of(), TemplateGalleryModel.underTags(byTag, List.of("Tavern", "Gone"), true));
    }

    @Test
    void twinsComeGroupByGroupInTheOrderShown() {
        List<List<String>> groups = List.of(List.of("apple", "cherry"), List.of("Banana", "far"));
        assertEquals(List.of(B, C, A), TemplateGalleryModel.twinsFirst(List.of(B, C, Path.of("images/x.png"), A),
                groups), "a group sits where its first picture shown is; its twin outside the list still counts");
    }

    @Test
    void aRecutAFewPixelsOffIsATwinAndAnotherPictureIsNot() {
        BufferedImage screen = screen();
        BufferedImage cut = screen.getSubimage(20, 20, 60, 30);
        BufferedImage recut = screen.getSubimage(22, 19, 62, 31);
        BufferedImage other = screen.getSubimage(100, 60, 60, 30);
        assertTrue(PictureTwins.twins(cut, recut));
        assertTrue(PictureTwins.twins(cut, copy(cut)), "an exact copy");
        assertFalse(PictureTwins.twins(cut, other));
        assertFalse(PictureTwins.twins(cut, screen.getSubimage(20, 20, 30, 30)), "half of it is not it");

        Map<String, BufferedImage> pictures = new HashMap<>(Map.of("b_cut", cut, "a_recut", recut, "other", other));
        pictures.put("broken", null);
        assertEquals(List.of(List.of("a_recut", "b_cut")), PictureTwins.groups(pictures));
    }

    @Test
    void aLargePictureIsComparedShrunk() {
        BufferedImage screen = screen();
        assertTrue(PictureTwins.twins(screen.getSubimage(0, 0, 180, 110), screen.getSubimage(4, 3, 176, 107)));
    }

    /** A made-up screen: bands of colour and a few boxes, so no two places look alike. */
    private static BufferedImage screen() {
        BufferedImage image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        for (int x = 0; x < 200; x += 10) {
            g.setColor(new Color(x, 255 - x, (x * 7) % 256));
            g.fillRect(x, 0, 10, 120);
        }
        g.setColor(Color.WHITE);
        g.fillOval(30, 25, 25, 15);
        g.setColor(Color.BLACK);
        g.fillRect(110, 70, 20, 12);
        g.dispose();
        return image;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }
}
