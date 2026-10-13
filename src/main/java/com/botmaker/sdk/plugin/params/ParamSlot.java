package com.botmaker.sdk.plugin.params;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;

import java.util.Optional;

/**
 * One of the SDK's {@code @Param} values in the open project, opened to read or write: the toolkit's
 * {@code ManagedHandle} takes methods only. The launchers and the capture source use it (2026-10-13).
 */
public final class ParamSlot {

    private ParamSlot() {}

    /** {@code value}'s field in the open project, or empty when there is none to edit. */
    public static Optional<ValueContext> open(StudioServices services, ManagedValue<?> value) {
        try {
            return services == null ? Optional.empty() : services.pluginValues().open(value);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * {@link #open}, asking the host to write the field first when the project lacks it.
     *
     * @param field how a sentence names it, {@code Sdk.launchTargets}
     * @return the field to edit, else the sentence saying why there is none
     */
    public static Opened openOrCreate(StudioServices services, ManagedValue<?> value, String field) {
        if (services == null) return new Opened(Optional.empty(), "No project is open.");
        Optional<ValueContext> ctx = open(services, value);
        if (ctx.isPresent()) return new Opened(ctx, null);
        try {
            Optional<String> refused = services.pluginValues().create(value);
            if (refused.isPresent()) return new Opened(Optional.empty(), refused.get());
        } catch (RuntimeException refused) {
            return new Opened(Optional.empty(), "BotMaker could not write " + field + ": " + refused.getMessage());
        }
        ctx = open(services, value);
        return ctx.isPresent() ? new Opened(ctx, null)
                : new Opened(ctx, field + " is written by hand, so BotMaker leaves it alone.");
    }

    /** The field opened, or why not: {@code refused} is null exactly when {@code ctx} is present. */
    public record Opened(Optional<ValueContext> ctx, String refused) {}
}
