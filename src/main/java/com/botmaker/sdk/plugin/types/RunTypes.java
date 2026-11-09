package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Held;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.api.bot.Run;
import com.botmaker.sdk.internal.screens.NamedMethod;

import java.util.List;

/**
 * The shape of the {@code RUN} value, {@code Sdk.run()}: the game's home and setup, which no plan owns. It was the
 * flow declarations' until 2026-11-09, when flows were deleted; a part, never picked on its own.
 */
public final class RunTypes implements TypeHolder {

    private RunTypes() {
    }

    @Override
    public List<PluginType<?>> types() {
        return List.of();
    }

    @Override
    public List<ComponentType<?>> components() {
        return List.of(RUN_SHAPE);
    }

    /** The run's home and setup as the text they are written as. */
    private static final class Parts {
        static final Held<Run, String> HOME = Held.as(String.class, run -> sourceOf(run.home()));
        static final Held<Run, String> SETUP = Held.as(String.class, run -> sourceOf(run.setup()));
    }

    /**
     * {@code Run.of()} and its {@code .home(…)} and {@code .setup(…)} links, each written only when the run has
     * one. Each method crosses as the text it is written as, {@code MyBot::goHome}.
     */
    public static final DeclaredCall<Run> RUN_SHAPE = ComponentType.part(Run.class)
            .writtenAs(Run::of)
            .<Runnable>with(Run::home, Parts.HOME.part(RunTypes::method))
            .<Runnable>with(Run::setup, Parts.SETUP.part(RunTypes::method))
            .constants(Run.NONE);

    /**
     * The run's home or setup that only knows what it is written as — {@code MyBot::goHome} — or {@code null}.
     */
    public static Runnable method(String source) {
        return source == null || source.isBlank() ? null : new NamedMethod(source);
    }

    /** How the run's home or setup is written, or {@code ""} for none or a real one. */
    public static String sourceOf(Runnable method) {
        return method instanceof NamedMethod(String source) ? source : "";
    }
}
