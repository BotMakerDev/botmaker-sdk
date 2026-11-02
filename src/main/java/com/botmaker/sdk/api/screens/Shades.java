package com.botmaker.sdk.api.screens;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The {@link Shade}s this bot learned on its game, its {@code @SdkValue(SdkValue.Id.SHADES)} method in {@code Sdk.java}
 * — {@code Shades.of(new Shade(…))}. None: a run sees every button as its looks were cut (2026-11-02).
 *
 * @param list the shades, the first that fits a match winning a tie
 */
public record Shades(List<Shade> list) {

    public Shades {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static Shades of(Shade... shades) {
        return new Shades(shades == null ? List.of() : Arrays.asList(shades));
    }

    /** No shade learned. */
    public static Shades none() {
        return new Shades(List.of());
    }
}
