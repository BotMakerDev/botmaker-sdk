// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.ParamScope;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.capture.CaptureSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the bot's {@code instance()} parameter is declared: while its run plays on an emulator instance. */
class PlaysOnEmulatorTest {

    private static ParamScope scope(CaptureSource capture, String... targets) {
        Map<ManagedValue<?>, Object> values = new HashMap<>();
        if (capture != null) values.put(SdkValues.CAPTURE, capture);
        values.put(SdkValues.LAUNCH_TARGETS, List.of(targets).stream().map(Launcher::of).toList());
        return new ParamScope() {
            @Override
            @SuppressWarnings("unchecked")
            public <V> Optional<V> value(ManagedValue<V> value) {
                return Optional.ofNullable((V) values.get(value));
            }
        };
    }

    @Test
    void anEmulatorCaptureDeclaresIt() {
        assertTrue(SdkValues.playsOnEmulator(scope(CaptureSource.emulator("Main"))));
    }

    @Test
    void automaticOnAnEmulatorAppDeclaresIt() {
        assertTrue(SdkValues.playsOnEmulator(scope(CaptureSource.automatic(), "emu-app:com.foo@Main")));
        assertTrue(SdkValues.playsOnEmulator(scope(CaptureSource.automatic(), "emu-app:com.foo")));
    }

    @Test
    void automaticOnADesktopGameOrNoneDoesNot() {
        assertFalse(SdkValues.playsOnEmulator(scope(CaptureSource.automatic(), "steam:440")));
        assertFalse(SdkValues.playsOnEmulator(scope(CaptureSource.automatic())));
        assertFalse(SdkValues.playsOnEmulator(ParamScope.NONE));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void aGameOfThisPcIsDealtAPlaceTooAndNoGameIsNot() {
        assertTrue(SdkValues.playsSomewhere(scope(CaptureSource.automatic(), "steam:440")));
        assertFalse(SdkValues.playsSomewhere(scope(CaptureSource.window("game", ""), "steam:440")),
                "a window set by hand is read on this desktop: no other place would show it");
        assertTrue(SdkValues.playsSomewhere(scope(CaptureSource.automatic(), "emu-app:com.foo")));
        assertFalse(SdkValues.playsSomewhere(scope(CaptureSource.automatic())));
        assertFalse(SdkValues.playsSomewhere(ParamScope.NONE));
    }

    @Test
    void aWindowSetByHandDoesNot() {
        assertFalse(SdkValues.playsOnEmulator(scope(CaptureSource.window("game", ""), "emu-app:com.foo@Main")));
    }
}
