package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.internal.trace.SdkRun;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The bot's screen map at run time: each {@code static final} {@link Screen} of the {@code Screens} class beside a
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
        lost();
        // The run's last lines: one per screen it came to, which only a local run's reports read.
        SdkRun.atEnd(ScreenMaps::sayTime);
    }

    /** How long the run spent on each screen, and how often it came to it, from {@link #seen}. */
    private static final ScreenTime TIME = new ScreenTime(System::currentTimeMillis);

    /** One INFO line per screen the run came to, with its {@code visits} and {@code millis}. */
    private static void sayTime() {
        SdkRun.Origin origin = new SdkRun.Origin("Screens", ScreenMaps.class.getName(), "sayTime");
        for (ScreenTime.Spent s : TIME.spent()) {
            SdkRun.info(origin, "On " + s.screen() + ": " + s.visits() + (s.visits() == 1 ? " visit, " : " visits, ")
                    + Math.round(s.millis() / 1000.0) + " s in all.", Map.of(ScreenValues.SCREEN, s.screen(),
                    ScreenValues.VISITS, Integer.toString(s.visits()), ScreenValues.MILLIS, Long.toString(s.millis())));
        }
    }

    /** Walks {@code map} from now on, as {@link #install} would learn it; {@code null} is the empty map. */
    public static void use(ScreenGraph map) {
        installed = map == null ? ScreenGraph.of(Map.of()) : map;
        last = null;
        reached = null;
        lost();
    }

    /**
     * The game is on no screen of the map now, as far as the run knows: later lines carry none and no screen's time
     * runs. The screen last seen is kept, as where a walk looks first.
     */
    public static void lost() {
        SdkRun.screen(null);
        TIME.off();
    }

    /** The screen of the installed map the game was last seen on: a walk looks for its neighbours first. */
    private static volatile String last;

    public static String lastSeen() {
        return last;
    }

    /**
     * Remembers {@code constant} as the screen the game was last seen on; a popup is never one. The run's later lines
     * carry it ({@link ScreenValues#SCREEN}), and its time is counted from now.
     */
    public static void seen(String constant) {
        ScreenGraph map = installed;
        if (constant == null || map.screen(constant).filter(s -> !s.isPopup()).isEmpty()) return;
        last = constant;
        String shown = map.shown(constant);
        SdkRun.screen(shown);
        TIME.on(shown);
        Consumer<String> listener = onSeen;
        if (listener != null) listener.accept(constant);
    }

    /** Told each screen {@link #seen}: the game facts read on it ({@code KeptFacts}). */
    private static volatile Consumer<String> onSeen;

    /** Tells {@code listener} each screen the game is seen on from now; {@code null} tells nobody. */
    public static void onSeen(Consumer<String> listener) {
        onSeen = listener;
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

    /**
     * {@code holder}'s {@code static final} {@link Screen} constants, in the order it declares them, each read once
     * every constant is set: a setup names any screen's inputs.
     */
    public static ScreenGraph read(Class<?> holder) {
        Map<String, Screen> screens = new LinkedHashMap<>();
        for (Field field : holder.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Screen.class.isAssignableFrom(field.getType()) || !Modifier.isStatic(modifiers)
                    || !Modifier.isFinal(modifiers)) {
                continue;
            }
            try {
                if (field.get(null) instanceof Screen screen) screens.put(field.getName(), screen);
            } catch (IllegalAccessException | LinkageError unreadable) {
                // Not the bot's to read here: the map lacks that screen, and a walk to it says so.
            }
        }
        return ScreenGraph.ofScreens(screens);
    }
}
