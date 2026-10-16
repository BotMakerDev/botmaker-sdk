package com.botmaker.sdk.internal.screens;

import java.util.function.BooleanSupplier;

/**
 * A screen map's conditions asked: {@link com.botmaker.sdk.api.screens.Input#when()}, {@link
 * com.botmaker.sdk.api.screens.Way#when}, {@link com.botmaker.sdk.api.screens.ScreenVariant#when}. A run asks the
 * bot's method; Studio's tools hold only its name, which cannot be asked, so they read it as unknown, which never
 * closes a way (the user's call, 2026-10-15).
 */
public final class Conditions {

    private Conditions() {}

    /** Whether {@code condition} holds: true for none and for one that cannot answer (a name, a method that threw). */
    public static boolean holds(BooleanSupplier condition) {
        if (condition == null) return true;
        try {
            return condition.getAsBoolean();
        } catch (RuntimeException unknown) {
            return true;
        }
    }
}
