package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.flow.FlowNames;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The game's map as the bot's {@code Screens} class holds it, read and written through the host as 🗺 Screens will
 * write it: Discovery's tools keep it there rather than in the notes (2026-10-14). Every method runs on the FX thread
 * ({@link FxCall}).
 */
final class ScreenBook {

    static final ManagedSet<GameScreen> SCREENS = ManagedSet.of(SdkValues.SCREEN_MAP);
    private static final ManagedSet<ImageTemplate> PICTURES = ManagedSet.of(SdkValues.PICTURES);
    private static final String HOLDER = "Screens";

    private ScreenBook() {}

    /** The map, in the order the bot declares its screens; a constant the grammar cannot read is left out. */
    static ScreenGraph read(StudioServices services) {
        PluginValues values = services.pluginValues();
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        if (values != null) {
            for (Member member : SCREENS.members(values)) {
                SCREENS.read(values, member).ifPresent(screen -> screens.put(member.name(), screen));
            }
        }
        return ScreenGraph.of(screens);
    }

    /** The constant {@code typed} names: the constant itself, or the name the game shows, in any case. */
    static Optional<String> find(ScreenGraph graph, String typed) {
        if (typed == null || typed.isBlank()) return Optional.empty();
        String wanted = typed.strip();
        Optional<String> constant = graph.screens().keySet().stream().filter(c -> c.equalsIgnoreCase(wanted))
                .findFirst();
        return constant.or(() -> graph.screens().keySet().stream()
                .filter(c -> graph.shown(c).equalsIgnoreCase(wanted)).findFirst());
    }

    /** A free constant for a screen the game calls {@code name}: {@code Guild hall} is {@code GUILD_HALL}. */
    static String constantFor(ScreenGraph graph, String name) {
        String base = Optional.ofNullable(FlowNames.constantFor(name)).orElse("SCREEN");
        String constant = base;
        for (int n = 2; graph.screens().containsKey(constant); n++) constant = base + "_" + n;
        return constant;
    }

    /**
     * Adds {@code screen} as {@code constant}, below every other, the class made on the first. Empty when written,
     * else why not.
     */
    static Optional<String> add(StudioServices services, String constant, GameScreen screen) {
        PluginValues values = services.pluginValues();
        if (values == null) return Optional.of("Studio has no source tree open.");
        Optional<String> missing = values.create(SCREENS.value());
        if (missing.isPresent()) return missing;
        return SCREENS.add(values, Member.of(constant), screen);
    }

    /** {@code constant} as {@code change} makes it, read again first. Empty when written, else why not. */
    static Optional<String> update(StudioServices services, String constant, UnaryOperator<GameScreen> change) {
        PluginValues values = services.pluginValues();
        Optional<ValueContext> held = values == null ? Optional.empty() : SCREENS.open(values, Member.of(constant));
        Optional<GameScreen> now = held.flatMap(SCREENS::read);
        if (now.isEmpty()) return Optional.of(HOLDER + "." + constant + " is gone or written by hand, so it is left as it is.");
        return held.get().write(change.apply(now.get()));
    }

    /** Declares {@code constant} just above {@code before}, so {@code before} may name it. Empty when done. */
    static Optional<String> moveBefore(StudioServices services, String constant, String before) {
        PluginValues values = services.pluginValues();
        if (values == null) return Optional.of("Studio has no source tree open.");
        return SCREENS.moveBefore(values, Member.of(constant), Member.of(before));
    }

    /**
     * The bot's picture {@code name} as its {@code Pictures} constant holds it, so the host writes the constant; the
     * file's own when there is none.
     */
    static ImageTemplate picture(StudioServices services, String name) {
        String constant = TemplateNames.constantFor(name.toLowerCase(Locale.ROOT));
        PluginValues values = services.pluginValues();
        Optional<ImageTemplate> held = constant == null || values == null ? Optional.empty()
                : PICTURES.read(values, Member.of(constant));
        return held.orElseGet(() -> new ImageTemplate(TemplateNames.pathFor(name)));
    }
}
