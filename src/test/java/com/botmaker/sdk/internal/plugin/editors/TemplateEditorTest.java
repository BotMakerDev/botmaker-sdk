package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.vision.ImageTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the picture editor reads out of a value and what it writes back — the half that needs no JavaFX
 * toolkit, and the half a wrong answer in would silently rewrite somebody's bot.
 *
 * <p>The reader is the part the port had to rebuild: Studio read the current value off a JDT syntax tree, and
 * the contract hands a plugin source text, so it is a brace-and-quote-aware read of the constructor's one
 * argument instead.
 */
class TemplateEditorTest {

    @Test
    void aSlotReadsItsPictureOutOfTheConstructor() {
        assertEquals("gold", TemplateEditors.nameOf(
                TestContexts.typedSlot("com.botmaker.sdk.api.vision.ImageTemplate",
                        "new ImageTemplate(\"src/main/resources/images/gold.png\")")));
    }

    @Test
    void aValueWithNoCallSiteIsReadAsJavaToo() {
        // It held the bare name "gold" until 2026-09-20, when a Parameters row stopped being a stored string
        // and became the same expression a slot holds. One shape to write, one shape to read.
        assertEquals("gold", TemplateEditors.nameOf(
                TestContexts.row("IMAGE_TEMPLATE", "new ImageTemplate(\"images/gold.png\")")));
        assertEquals("", TemplateEditors.nameOf(TestContexts.row("IMAGE_TEMPLATE", "")));
    }

    @Test
    void aFullyQualifiedConstructorIsStillRead() {
        assertEquals("ore", TemplateEditors.nameOfSource(
                "new com.botmaker.sdk.api.vision.ImageTemplate(\"src/main/resources/images/ore.png\")"));
    }

    @Test
    void anythingButAConstructorReadsAsNoPicture() {
        // A variable, a constant or a call is a reference the editor cannot represent — and must not
        // overwrite. Reading it as "no picture" is what keeps the pill from claiming a value nobody set.
        assertEquals("", TemplateEditors.nameOfSource("TEMPLATES.gold"));
        assertEquals("", TemplateEditors.nameOfSource("chooseTemplate()"));
        assertEquals("", TemplateEditors.nameOfSource(""));
        assertNull(TemplateEditors.pathOf("ImageTemplate.of(\"gold.png\")"));
    }

    @Test
    void aConstructorWithNoPathReadsAsNoPicture() {
        assertEquals("", TemplateEditors.nameOfSource("new ImageTemplate(\"\")"));
        assertEquals("", TemplateEditors.nameOfSource("new ImageTemplate(path)"));
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
                "com.botmaker.sdk.api.vision.ImageTemplate", "new ImageTemplate(\"\")");
        TemplateEditors.commit(ctx, "gold");

        assertEquals("src/main/resources/images/gold.png",
                ((ImageTemplate) ctx.value()).filePath());
    }

    @Test
    void writingAValueWithNoCallSiteCarriesTheSamePath() {
        TestContexts.Recording ctx = TestContexts.row("com.botmaker.sdk.api.vision.ImageTemplate", "");
        TemplateEditors.commit(ctx, "gold");
        assertEquals("src/main/resources/images/gold.png",
                ((ImageTemplate) ctx.value()).filePath());
    }

    @Test
    void aNameSurvivesTheRoundTrip() {
        String literal = TemplateEditors.literalFor("gold_ore 2");
        assertEquals("gold_ore 2", TemplateEditors.nameOfSource(literal));
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
