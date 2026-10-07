package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.bot.BotSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The host takes a settings value apart through these and puts it back: {@code build(components(v))} is the
 * same value at every level, a call of another arity builds nothing, and {@code BotSettings.DEFAULTS} is the
 * constant a default is written as.
 */
class SettingsTypesTest {

    private static final BotSettings TUNED = BotSettings.of(BotSettings.clicks(750, 125, false),
            BotSettings.vision(0.62, 0.11), BotSettings.runIn(BotSettings.Where.MY_DESKTOP, true,
                    BotSettings.DisplayBackend.XEPHYR, BotSettings.InputBackend.UINPUT), 7, false);

    @Test
    void everyPartRoundTrips() {
        assertEquals(TUNED, roundTrip(SettingsTypes.SETTINGS, TUNED));
        assertEquals(TUNED.clicks(), roundTrip(SettingsTypes.CLICKS, TUNED.clicks()));
        assertEquals(TUNED.vision(), roundTrip(SettingsTypes.VISION, TUNED.vision()));
        assertEquals(TUNED.runIn(), roundTrip(SettingsTypes.RUN_IN, TUNED.runIn()));
    }

    @Test
    void eachFactoryTakesExactlyItsParts() {
        for (ComponentType<?> type : SettingsTypes.ALL) {
            assertEquals(type.factory().getParameterCount(), type.componentTypes().size(), type.type().getName());
        }
        assertNull(SettingsTypes.CLICKS.build(List.of(1, 2)), "another arity is not this call");
    }

    @Test
    void theDefaultsAreWrittenAsTheirConstant() {
        assertEquals(List.of("DEFAULTS"), SettingsTypes.SETTINGS.constants().stream().map(f -> f.getName()).toList());
    }

    private static <T> T roundTrip(ComponentType<T> type, T value) {
        return type.build(type.components(value));
    }
}
