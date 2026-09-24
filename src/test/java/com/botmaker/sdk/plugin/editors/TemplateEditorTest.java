package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.SlotRun;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the picture editor reads out of a value and what it writes back — the half that needs no JavaFX
 * toolkit, and the half a wrong answer in would silently rewrite somebody's bot.
 *
 * <p>The host reads the Java and hands the editor an {@link ImageTemplate}, or nothing.
 */
class TemplateEditorTest {

    @Test
    void aSlotIsNamedByThePictureTheHostRead() {
        assertEquals("gold", TemplateEditors.nameOf(
                TestContexts.typedSlot(ImageTemplate.class, "Pictures.GOLD")
                        .withValue(new ImageTemplate("src/main/resources/images/gold.png"))));
    }

    @Test
    void somethingTheHostCouldNotReadIsNoPicture() {
        // A variable or a call is a reference the editor cannot represent — and must not overwrite.
        assertEquals("", TemplateEditors.nameOf(
                TestContexts.typedSlot(ImageTemplate.class, "chooseTemplate()")));
        assertEquals("", TemplateEditors.nameOf(TestContexts.row(null, "")));
    }

    @Test
    void aRunElementIsNamedByItsValueAndHasNoNameWithout() {
        assertEquals("ore", TemplateEditors.nameOf(
                new SlotRun.Element(new ImageTemplate("images/ore.png"), "Pictures.ORE")));
        assertEquals("", TemplateEditors.nameOf(new SlotRun.Element(null, "somePicture")));
    }

    @Test
    void aGroupSlotListsTheGroupsPictures() {
        TestContexts.Recording group = TestContexts.typedSlot(ImageTemplateGroup.class,
                "ImageTemplateGroup.of(…)").withValue(ImageTemplateGroup.of(
                new ImageTemplate("images/gold.png"), new ImageTemplate("images/ore.png")));

        List<SlotRun.Element> elements = TemplateEditors.elementsOf(group);

        assertEquals(List.of("gold", "ore"), elements.stream().map(TemplateEditors::nameOf).toList());
    }

    @Test
    void aGroupTheHostCouldNotReadListsNothing() {
        assertTrue(TemplateEditors.elementsOf(TestContexts.typedSlot(
                ImageTemplateGroup.class, "myGroup()")).isEmpty());
    }

    /**
     * A pick writes the picture itself, and the path it carries is the project-relative one.
     *
     * <p>It asserted the spelled constructor — {@code new ImageTemplate("src/…/gold.png")} — until
     * 2026-09-22. This editor spells nothing now: it hands the host an {@link ImageTemplate} and the host
     * writes it through this plugin's own {@code ComponentType}, whose one component is the path. So what
     * is asserted is the path, which is the part this editor decides and the part a wrong answer would send
     * the bot looking in the wrong place with.
     */
    @Test
    void writingASlotCarriesTheProjectRelativePath() {
        TestContexts.Recording ctx = TestContexts.typedSlot(
                ImageTemplate.class, "new ImageTemplate(\"\")");
        TemplateEditors.commit(ctx, "gold");

        assertEquals("src/main/resources/images/gold.png",
                ((ImageTemplate) ctx.value()).filePath());
    }

    @Test
    void writingAValueWithNoCallSiteCarriesTheSamePath() {
        TestContexts.Recording ctx = TestContexts.row(ImageTemplate.class, "");
        TemplateEditors.commit(ctx, "gold");
        assertEquals("src/main/resources/images/gold.png",
                ((ImageTemplate) ctx.value()).filePath());
    }

    @Test
    void aNameSurvivesTheRoundTrip() {
        ImageTemplate picked = TemplateEditors.templateFor("gold_ore 2");
        assertEquals("gold_ore 2", TemplateEditors.baseNameOf(picked.filePath()));
    }

    @Test
    void aPathInAnotherFolderStillYieldsItsBaseName() {
        // Reading is deliberately more permissive than writing: a bot written by hand, or by an older
        // version, may spell the path differently, and the name is still what the pill should say.
        assertEquals("gold", TemplateEditors.baseNameOf("images/gold.png"));
        assertEquals("gold", TemplateEditors.baseNameOf("gold.png"));
        assertEquals("gold", TemplateEditors.baseNameOf("gold"));
    }
}
