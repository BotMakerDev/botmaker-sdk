package com.botmaker.sdk.api.screens;

/**
 * A field of a {@link Screen}: an {@link Input} acted on, or a {@link Readout} read. The map holds both kinds by field
 * name, each declared {@code public final} in the screen's class (2026-10-22).
 */
public sealed interface ScreenPart permits Input, Readout {}
