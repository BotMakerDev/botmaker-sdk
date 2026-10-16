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

    /** The map the running bot holds, set at its start; empty before. */
    private static volatile ScreenGraph installed = ScreenGraph.of(Map.of());

    private ScreenMaps() {}

    /**
     * Learns the running bot's map: the {@code Screens} class beside the first of {@code holders} ({@code Sdk.class})
     * that has one. A bot with none walks an empty map.
     */
    public static void install(Class<?>... holders) {
        ScreenGraph found = ScreenGraph.of(Map.of());
        for (Class<?> holder : holders == null ? new Class<?>[0] : holders) {
            Optional<ScreenGraph> map = beside(holder);
            if (map.isPresent()) {
                found = map.get();
                break;
            }
        }
        installed = found;
        last = null;
        reached = null;
    }

    /** Walks {@code map} from now on, as {@link #install} would learn it; {@code null} is the empty map. */
    public static void use(ScreenGraph map) {
        installed = map == null ? ScreenGraph.of(Map.of()) : map;
        last = null;
        reached = null;
    }

    /** The screen of the installed map the game was last seen on: a walk looks for its neighbours first. */
    private static volatile String last;

    public static String lastSeen() {
        return last;
    }

    /** Remembers {@code constant} as the screen the game was last seen on; a popup is never one. */
    public static void seen(String constant) {
        if (constant != null && installed.screen(constant).filter(s -> !s.isPopup()).isPresent()) last = constant;
    }

    /**
     * The screen the running activity's last {@code Navigate.to} arrived on: where the run walks back to when a step of
     * the activity gets stuck. Cleared as each activity starts.
     */
    private static volatile String reached;

    public static String reached() {
        return reached;
    }

    /** Remembers {@code constant} as the screen the running activity reached; {@code null} forgets it. */
    public static void reached(String constant) {
        reached = constant;
    }

    /** The running bot's map, as {@link #install} learned it. */
    public static ScreenGraph installed() {
        return installed;
    }

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
