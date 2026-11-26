// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.capture.AutoCapture;
import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** What a run dealt nothing claims before its first click, and the words of a refusal. */
class PlaceClaimsTest {

    private static final LaunchSpec ON_PIXEL = LaunchSpec.parse("emu-app:com.game@Pixel 10");

    private static AutoCapture.Inputs inputs(String property, Object dealt) {
        return new AutoCapture.Inputs(property, ON_PIXEL, CaptureSource.automatic(), dealt, Optional.empty(),
                List::of);
    }

    private static Optional<String> claimed(AutoCapture.Inputs in) {
        return PlaceClaims.toClaim(in, AutoCapture.decide(in));
    }

    @Test
    void theKeyIsTheSdkPoolsHoldOfTheInstance() {
        // RunRegistryTest pins the core's side of the same text, Hold.key over the canonical Place.android.
        assertEquals("com.botmaker.sdk.api.bot.SdkValue$Id.PLACE", SdkValues.PLACE.id());
        assertEquals("com.github.BotMakerDev:botmaker-sdk/com.botmaker.sdk.api.bot.SdkValue$Id.PLACE"
                + "/com.botmaker.sdk.api.capture.Place.android(\"Pixel 10\")", PlaceClaims.key("Pixel 10"));
        assertEquals("\"a\\\"b\\\\c\\u00e9\"", PlaceClaims.quoted("a\"b\\cé"));
        assertEquals("com.github.BotMakerDev:botmaker-sdk/com.botmaker.sdk.api.bot.SdkValue$Id.INSTANCE"
                + "/com.botmaker.sdk.api.capture.Instance.named(\"Pixel 10\")", PlaceClaims.oldKey("Pixel 10"),
                "an SDK before places holds an instance by this key");
    }

    @Test
    void aRunDealtNothingClaimsTheInstanceItPlaysOn() {
        assertEquals(Optional.of("Pixel 10"), claimed(inputs(null, null)));
        assertEquals(Optional.of("Pixel 10"), claimed(inputs(null, Place.android(""))), "an unnamed deal is none");
        assertEquals(Optional.of("Pixel 10"), claimed(inputs(null, Place.desktop())), "a PC place names no instance");
    }

    @Test
    void aDealtRunOrOneTheHostNamedClaimsNothingMore() {
        assertEquals(Optional.empty(), claimed(inputs(null, Place.android("LDPlayer-3"))));
        assertEquals(Optional.empty(), claimed(inputs("Pixel 10", null)));
        AutoCapture.Inputs desktop = new AutoCapture.Inputs(null, LaunchSpec.parse("steam:1"),
                CaptureSource.window("c", "t"), null, Optional.empty(), List::of);
        assertEquals(Optional.empty(), claimed(desktop), "it reads no emulator");
    }

    @Test
    void aRefusedClaimStopsTheRunWithWords() {
        List<String> asked = new ArrayList<>();
        PlaceClaims.claim("Pixel 10", key -> asked.add(key));
        assertEquals(List.of(PlaceClaims.key("Pixel 10"), PlaceClaims.oldKey("Pixel 10")), asked);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> PlaceClaims.claim("Pixel 10", key -> false));
        assertThrows(IllegalStateException.class, () -> PlaceClaims.claim("Pixel 10",
                key -> !key.equals(PlaceClaims.oldKey("Pixel 10"))), "an old SDK's run holds it");
        assertEquals("Pixel 10 is played by another run, or this PC's runs could not be read; stop it, or run on"
                + " another instance", refused.getMessage());
    }
}
