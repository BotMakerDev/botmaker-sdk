package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import com.botmaker.sdk.api.vision.OcrLanguage;
import com.botmaker.sdk.api.vision.OcrOptions;
import com.botmaker.sdk.api.vision.Text;
import com.botmaker.sdk.api.vision.TextResult;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the OCR editor writes, what it reads back, and how it says a setting — none of which needs a JavaFX
 * toolkit.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OcrEditorsTest {

    @Test
    void any_character_is_written_as_an_empty_whitelist_the_host_can_write() {
        TestContexts.Recording slot = TestContexts.typedSlot(OcrOptions.class, "");

        OcrEditors.commit(slot, OcrOptions.defaults());

        assertEquals(OcrOptions.defaults().withCharWhitelist(""), slot.value());
    }

    @Test
    void a_picked_setting_round_trips_wherever_the_value_is() {
        OcrOptions picked = Text.DEFAULT_OPTIONS.withCharWhitelist(OcrEditors.DIGITS).withUpscale(3.0);

        TestContexts.Recording slot = TestContexts.typedSlot(OcrOptions.class, "");
        OcrEditors.commit(slot, picked);
        assertEquals(picked, OcrEditors.current(slot));

        TestContexts.Recording row = TestContexts.row(OcrOptions.class, "");
        OcrEditors.commit(row, picked);
        assertEquals(picked, OcrEditors.current(row));
    }

    @Test
    void a_value_the_host_could_not_read_is_shown_as_written_and_opens_on_what_text_reads_with() {
        TestContexts.Recording slot = TestContexts.typedSlot(OcrOptions.class, "Text.DEFAULT_OPTIONS");

        assertEquals("Text.DEFAULT_OPTIONS", OcrEditors.pillText(slot));
        assertEquals(Text.DEFAULT_OPTIONS, OcrEditors.current(slot));
    }

    @Test
    void the_label_says_the_setting_in_words() {
        assertEquals("English, lines, ×2", OcrEditors.label(Text.DEFAULT_OPTIONS));
        assertEquals("English + Japanese, words, digits only, ×2.5",
                OcrEditors.label(OcrOptions.defaults().withLanguages(OcrLanguage.ENGLISH, OcrLanguage.JAPANESE)
                        .withCharWhitelist(OcrEditors.DIGITS).withUpscale(2.5)));
        assertEquals("English + fra, lines, only \"ABC\", ×1",
                OcrEditors.label(Text.DEFAULT_OPTIONS.withLanguages("eng+fra").withCharWhitelist("ABC")
                        .withUpscale(1.0).withLevel(TextResult.Level.LINE)));
    }

    /** A language the SDK does not bundle is kept as written: a bot may have installed it system-wide. */
    @Test
    void a_language_the_sdk_does_not_bundle_survives_an_edit() {
        String spec = "eng+fra+jpn";
        assertEquals(EnumSet.of(OcrLanguage.ENGLISH, OcrLanguage.JAPANESE), OcrEditors.bundled(spec));
        assertEquals(List.of("fra"), OcrEditors.others(spec));

        assertEquals("kor+fra", OcrEditors.spec(Set.of(OcrLanguage.KOREAN), OcrEditors.others(spec)));
    }

    @Test
    void nothing_ticked_reads_english_since_tesseract_refuses_no_language() {
        assertEquals("eng", OcrEditors.spec(Set.of(), List.of()));
        assertEquals("fra", OcrEditors.spec(Set.of(), List.of("fra")));
    }

    @Test
    void ticked_languages_are_written_in_the_bundled_order() {
        assertEquals("eng+chi_sim",
                OcrEditors.spec(Set.of(OcrLanguage.SIMPLIFIED_CHINESE, OcrLanguage.ENGLISH), List.of()));
    }
}
