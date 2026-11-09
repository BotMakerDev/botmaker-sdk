package com.botmaker.sdk.internal.bot;

import com.botmaker.sdk.internal.screens.ScreenMaps;
import com.botmaker.sdk.internal.vision.TemplateNames;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every bot already has its SDK values in these classes, so a holder constant's spelling never moves one: the names
 * the enum derives are pinned here, beside the literals the SDK's other code still names a class by (2026-11-04).
 */
class SdkHoldersTest {

    @Test
    void eachHolderNamesTheClassBotsAlreadyHave() {
        Map<String, String> names = Map.ofEntries(
                Map.entry("Sdk", SdkValues.RUN.holder()),
                Map.entry(TemplateNames.CLASS_NAME, SdkValues.PICTURES.holder()),
                Map.entry("Tags", SdkValues.TAGS.holder()),
                Map.entry("Digits", SdkValues.DIGITS.holder()),
                Map.entry("Texts", SdkValues.TEXTS.holder()),
                Map.entry("Elements", SdkValues.ELEMENTS.holder()),
                Map.entry("States", SdkValues.STATES.holder()),
                Map.entry(ScreenMaps.HOLDER, SdkValues.SCREEN_MAP.holder()));

        names.forEach((expected, actual) -> assertEquals(expected, actual));
    }
}
