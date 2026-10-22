package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.internal.vision.TemplateNames;
import com.botmaker.sdk.plugin.flow.FlowNames;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The game's map as the bot's {@code Screens} class holds it, read and written through the host as 🗺 Screens will
 * write it: Discovery's tools keep it there rather than in the notes (2026-10-14). A screen is a class of the set,
 * its setup the class's member and each input a field ({@code ManagedValue.ofClasses}, 2026-10-16), so an edit
 * writes only the parts it changed. Every method runs on the FX thread ({@link FxCall}).
 */
final class ScreenBook {

    private static final ManagedValue<?> SCREENS = SdkValues.SCREEN_MAP;
    private static final ManagedSet<ImageTemplate> PICTURES = ManagedSet.of(SdkValues.PICTURES);
    private static final String HOLDER = "Screens";

    private ScreenBook() {}

    /** The map, in the order the bot declares its screens; a field the grammar cannot read is left out. */
    static ScreenGraph read(StudioServices services) {
        return ScreenMapRead.read(services);
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
        return constantFor(graph, name, null);
    }

    /** As {@link #constantFor(ScreenGraph, String)}, {@code own} counted free: the constant of the screen renamed. */
    static String constantFor(ScreenGraph graph, String name, String own) {
        String base = Optional.ofNullable(FlowNames.constantFor(name)).orElse("SCREEN");
        String constant = base;
        for (int n = 2; !constant.equals(own) && graph.screens().containsKey(constant); n++) constant = base + "_" + n;
        return constant;
    }

    /**
     * Adds {@code screen} as {@code constant}: its class after every other, then each of its inputs, the class made
     * on the first. Empty when written, else why not.
     */
    static Optional<String> add(StudioServices services, String constant, MapScreen screen) {
        PluginValues values = services.pluginValues();
        if (values == null) return Optional.of("Studio has no source tree open.");
        return step(services, "Add " + screen.name(), () -> values.create(SCREENS)
                .or(() -> values.addClass(SCREENS, constant, screen.setup()))
                .or(() -> ScreenMapRead.add(values, constant, screen.parts())));
    }

    /**
     * {@code constant} as {@code change} makes it, read again first: its setup written when it changed, each field
     * gone removed, each new one added last and each changed one written. Empty when written, else why not.
     */
    static Optional<String> update(StudioServices services, String constant, UnaryOperator<MapScreen> change) {
        PluginValues values = services.pluginValues();
        Optional<MapScreen> now = values == null ? Optional.empty() : read(services).screen(constant)
                .filter(s -> ScreenMapRead.setup(values, constant).isPresent());
        if (now.isEmpty()) return Optional.of(HOLDER + "." + constant + " is gone or written by hand, so it is left as it is.");
        MapScreen next = change.apply(now.get());
        // Named for Studio's ↶; a drag or a new screen around it is one step under its own name instead.
        return step(services, "Edit " + now.get().name(),
                () -> ScreenMapRead.write(values, constant, now.get(), next));
    }

    /** {@code write} as one step of Studio's undo named {@code label}, or part of the batch already open. */
    static Optional<String> step(StudioServices services, String label, Supplier<Optional<String>> write) {
        List<Optional<String>> answer = new ArrayList<>(List.of(Optional.empty()));
        // Dropped after the write as well: a view drawn during it may have kept the map as it was.
        try {
            services.history().batch(label, () -> answer.set(0, write.get()));
        } finally {
            ScreenMapRead.forget();
        }
        return answer.getFirst();
    }

    /**
     * Renames {@code constant} to {@code renamed}, its class with it and every use of both, by binding. Empty when
     * done, else why not.
     */
    static Optional<String> renameConstant(StudioServices services, String constant, String renamed) {
        PluginValues values = services.pluginValues();
        if (values == null) return Optional.of("Studio has no source tree open.");
        return values.renameGroup(SCREENS, constant, renamed);
    }

    /** Takes {@code constant} out of the map, its class with it. Empty when done, else why not. */
    static Optional<String> remove(StudioServices services, String constant) {
        PluginValues values = services.pluginValues();
        if (values == null) return Optional.of("Studio has no source tree open.");
        return step(services, "Delete Screens." + constant, () -> values.removeGroup(SCREENS, constant));
    }

    /** Whether the bot's Pictures declare {@code name}. */
    static boolean exists(StudioServices services, String name) {
        String constant = TemplateNames.constantFor(name.toLowerCase(Locale.ROOT));
        PluginValues values = services.pluginValues();
        return constant != null && values != null && PICTURES.read(values, Member.of(constant)).isPresent();
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
