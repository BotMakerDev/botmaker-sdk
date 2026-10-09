package com.botmaker.sdk.plugin.types;

import com.botmaker.sdk.api.bot.BotSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The host takes a settings value apart through this and puts it back: {@code build(components(v))} is the same
 * value with every setting changed or none, and {@code BotSettings.defaults()} with no part is the defaults.
 */
class SettingsTypesTest {

    @SuppressWarnings("deprecation")
    private static final BotSettings TUNED = BotSettings.defaults().foundDelay(750).notFoundDelay(125)
            .centredClicks().confidence(0.62).compareMargin(0.11).where(BotSettings.Where.MY_DESKTOP).takesOver()
            .displayBackend(BotSettings.DisplayBackend.XEPHYR).inputBackend(BotSettings.InputBackend.UINPUT)
            .maxRetryAttempts(7).debugOff();

    @Test
    void everySettingRoundTrips() {
        assertEquals(TUNED, roundTrip(TUNED));
        assertEquals(BotSettings.DEFAULTS, roundTrip(BotSettings.DEFAULTS));
        assertEquals(BotSettings.DEFAULTS.confidence(0.9), roundTrip(BotSettings.DEFAULTS.confidence(0.9)));
    }

    @Test
    void everySettingIsALinkAfterTheDefaults() {
        assertEquals(0, SettingsTypes.SETTINGS.factory().getParameterCount());
        assertEquals(11, SettingsTypes.SETTINGS.withers().size());
        assertEquals(SettingsTypes.SETTINGS.withers().size(), SettingsTypes.SETTINGS.componentTypes().size());
        assertEquals(BotSettings.DEFAULTS, SettingsTypes.SETTINGS.build(List.of()), "no part: the defaults");
        assertNull(SettingsTypes.SETTINGS.build(List.of(1, 2)), "neither no part nor every part: not this call");
        assertTrue(SettingsTypes.SETTINGS.withers().stream().filter(w -> w.flag())
                .map(w -> w.method().getName()).toList()
                .containsAll(List.of("centredClicks", "takesOver", "debugOff")));
    }

    private static BotSettings roundTrip(BotSettings value) {
        return SettingsTypes.SETTINGS.build(SettingsTypes.SETTINGS.components(value));
    }
}
