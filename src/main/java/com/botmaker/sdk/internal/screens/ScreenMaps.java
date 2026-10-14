package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.GameScreen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bot's screen map at run time: each {@code static final GameScreen} of the {@code Screens} class beside a
 * holder ({@code Sdk.class}), read as {@code InstalledRun.nameFlows} reads {@code Flows}.
 */
public final class ScreenMaps {

    /** The class the screen constants live in, beside {@code Sdk}. */
    public static final String HOLDER = "Screens";

    private ScreenMaps() {}

    /**
     * The map in the {@code Screens} class beside {@code holder}; empty when the bot has none. A map whose constants
     * fail to build throws, so the bot's author sees why rather than a bot with no map.
     */
    public static Optional<ScreenGraph> beside(Class<?> holder) {
        if (holder == null) return Optional.empty();
        try {
            return Optional.of(read(Class.forName(holder.getPackageName() + "." + HOLDER, true,
                    holder.getClassLoader())));
        } catch (ClassNotFoundException none) {
            return Optional.empty();
        }
    }

    /** {@code holder}'s {@code static final GameScreen} constants, in the order it declares them. */
    public static ScreenGraph read(Class<?> holder) {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        for (Field field : holder.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() != GameScreen.class || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            try {
                if (field.get(null) instanceof GameScreen screen) screens.put(field.getName(), screen);
            } catch (IllegalAccessException | LinkageError unreadable) {
                // Not the bot's to read here: the map lacks that screen, and a walk to it says so.
            }
        }
        return ScreenGraph.of(screens);
    }
}
