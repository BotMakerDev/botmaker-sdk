package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.RunProperty;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.api.bot.GameVm;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.session.SessionBootstrap;
import com.botmaker.shared.vm.VmRecord;

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
     * Makes {@code name} the bot's VM; {@code null} or blank for the one there is. A bot whose holder lacks
     * {@link SdkValues#VM}'s method gets it written now. Nothing is written when it already names {@code name}.
     * A refusal goes to the status line.
     *
     * @return whether the bot's Java now names {@code name}
     */
    public static boolean set(StudioServices services, String name) {
        if (services == null) return false;
        services.runs().set(OLD_RUN_PROPERTY, null);
        String wanted = name == null ? "" : name.trim();
        if (Objects.equals(written(services), wanted.isEmpty() ? null : wanted)) return true;
        Optional<ValueContext> ctx = open(services);
        if (ctx.isEmpty()) {
            Optional<String> refused = services.pluginValues().create(SdkValues.VM);
            if (refused.isPresent()) {
                services.status(refused.get());
                return false;
            }
            ctx = open(services);
        }
        if (ctx.isEmpty()) {
            services.status("Sdk.vm is written by hand, so BotMaker leaves it alone.");
            return false;
        }
        Optional<String> refused = ctx.get().write(GameVm.of(wanted));
        refused.ifPresent(services::status);
        return refused.isEmpty();
    }

    /**
     * Runs the bot's game in {@code vm}: it becomes the bot's VM, and {@code gameRunsIn()} says a VM unless it does
     * already. What a VM set up from the {@code vm()} editor does once it is ready.
     */
    static void use(StudioServices services, VmRecord vm) {
        if (!set(services, vm.name())) return; // its refusal is the status line
        BotSettings was = SettingsValue.current(services);
        Optional<String> refused = SettingsValue.write(services, was, was.where(BotSettings.Where.VM));
        services.status(refused.map(why -> "The bot's VM is " + vm.name() + ", but gameRunsIn was not set: " + why)
                .orElse("The bot's game runs in the game VM " + vm.name() + "."));
    }

    /** What the bot's {@code vm} parameter names, or {@code null}. */
    private static String written(StudioServices services) {
        return open(services).flatMap(ctx -> ctx.value(GameVm.class)).map(GameVm::name)
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
