package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.api.bot.BotSettings;

import java.util.List;


/**
 * The call a {@code @SdkValue(SETTINGS)} value is written as: {@code BotSettings.defaults()} followed by a named
 * link for each setting that is not the default — {@code .foundDelay(400).confidence(0.85).takesOver()}.
 *
 * <p>One declaration, every setting a wither (2026-10-09). The host writes only the links whose part differs
 * from the default and reads them back in any order, so the method says what this bot changed and nothing else;
 * it used to be {@code BotSettings.of(BotSettings.clicks(500, 200, true), …, 20, true)}, every number
 * positional. A setting on by default is turned off by a link with a negative name ({@code centredClicks()},
 * {@code debugOff()}): a flag only turns a thing on. The ⚙ Bot Settings window changes the value, never the
 * Java.
 *
 * <p>A link nothing here declares is not this shape and builds nothing: the host shows it read-only, exactly as
 * written.
 */
public final class SettingsTypes implements TypeHolder {

    private SettingsTypes() {}

    @Override
    public List<PluginType<?>> types() {
        return List.of();
    }

    @Override
    public List<ComponentType<?>> components() {
        return List.of(SETTINGS);
    }

    /**
     * {@code BotSettings.defaults()} and its links: those marked {@code @Link} on {@code BotSettings} first
     * ({@code .maxRetryAttempts(…)}, then the others by name), then these, which read a part another way — a
     * negative flag, a nested part, or a flag whose reader is not {@code isX()}. The deprecated {@code debug} is
     * still one: the bot's Java writes it, and a value read must be written back.
     */
    @SuppressWarnings("deprecation")
    public static final DeclaredCall<BotSettings> SETTINGS = ComponentType.part(BotSettings.class)
            .writtenAs(BotSettings::defaults)
            .flag(BotSettings::centredClicks, settings -> !settings.randomizeClicks())
            .flag(BotSettings::takesOver, BotSettings::takeOver)
            .with(BotSettings::displayBackend, settings -> settings.runIn().displayBackend())
            .with(BotSettings::inputBackend, settings -> settings.runIn().inputBackend())
            .flag(BotSettings::debugOff, settings -> !settings.debug());
}
