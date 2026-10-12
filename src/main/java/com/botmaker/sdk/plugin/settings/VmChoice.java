package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.session.SessionBootstrap;

import java.util.Objects;
import java.util.Optional;

/**
 * Which game VM the open bot's game runs in: its {@code vm} parameter ({@link SdkValues#VM}) in {@code Sdk.java}, a
 * preference of the bot's, blank for the one VM there is. The bot's Java also says whether the game runs in a VM at
 * all ({@code BotSettings.Where.VM}). A {@code -Dbotmaker.session.vm} given to a bot started by hand still wins, and
 * so does the run property a computer kept before (until 2026-10-12): {@link #current} says so, a write clears it.
 */
public final class VmChoice {

    /** The VM a computer named before the bot's Java said it: passed to every run as -D, so it still wins. */
    private static final RunProperty<String> OLD_RUN_PROPERTY = RunProperty.text(SessionBootstrap.VM_PROPERTY);

    private VmChoice() {}

    /** The VM's name a run uses, or {@code null} when none is named. */
    public static String current(StudioServices services) {
        if (services == null) return null;
        Optional<String> old = services.runs().get(OLD_RUN_PROPERTY);
        if (old.isPresent()) return old.get();
        return written(services);
    }

    /**
     * Makes {@code name} the bot's VM; {@code null} or blank for the one there is. The parameter is conditional
     * ({@link SdkValues#VM}): one a bot lacks because it never ran its game in a VM is written now, which the settings
     * just switched to. Nothing is written when it already names {@code name}. A refusal goes to the status line.
     */
    public static void set(StudioServices services, String name) {
        if (services == null) return;
        services.runs().set(OLD_RUN_PROPERTY, null);
        String wanted = name == null ? "" : name.trim();
        if (Objects.equals(written(services), wanted.isEmpty() ? null : wanted)) return;
        Optional<ValueContext> ctx = open(services);
        if (ctx.isEmpty()) {
            Optional<String> refused = services.pluginValues().create(SdkValues.VM);
            if (refused.isPresent()) {
                services.status(refused.get());
                return;
            }
            ctx = open(services);
        }
        if (ctx.isEmpty()) {
            services.status("Sdk.vm is written by hand, so BotMaker leaves it alone.");
            return;
        }
        ctx.get().write(wanted).ifPresent(services::status);
    }

    /** What the bot's {@code vm} parameter names, or {@code null}. */
    private static String written(StudioServices services) {
        return open(services).flatMap(ctx -> ctx.value(String.class)).map(String::trim)
                .filter(text -> !text.isEmpty()).orElse(null);
    }

    private static Optional<ValueContext> open(StudioServices services) {
        try {
            return services == null ? Optional.empty() : services.pluginValues().open(SdkValues.VM);
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
