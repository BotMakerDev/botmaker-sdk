package com.botmaker.sdk.api.screens;

/**
 * A field of a {@link Screen}: an {@link Input} acted on, a {@link Readout} read, or a {@link Mark} that tells the
 * screen. The map holds each kind by field name, each declared {@code public final} in the screen's class
 * (2026-10-22; marks 2026-11-02).
 */
public sealed interface ScreenPart permits Input, Readout, Mark {}
