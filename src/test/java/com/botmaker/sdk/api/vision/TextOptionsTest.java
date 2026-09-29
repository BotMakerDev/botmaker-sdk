package com.botmaker.sdk.api.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A block dropped from the palette seeded its {@code OcrOptions} with {@code null} until the SDK declared the
 * type (2026-09-29), and a bot written then still passes it. Every overload reads that as the defaults, where it
 * used to throw from {@code read} and to be caught as "no text" by every search.
 */
class TextOptionsTest {

    @Test
    void null_options_are_the_facade_defaults() {
        assertSame(Text.DEFAULT_OPTIONS, Text.orDefault(null));
    }

    @Test
    void given_options_are_kept() {
        OcrOptions digits = OcrOptions.defaults().withCharWhitelist("0123456789");
        assertSame(digits, Text.orDefault(digits));
    }
}
