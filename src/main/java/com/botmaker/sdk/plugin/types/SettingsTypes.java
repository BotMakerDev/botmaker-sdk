package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
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
public final class SettingsTypes {

    private SettingsTypes() {}

    /**
     * {@code BotSettings.defaults()} and its links, in the order they are written. The deprecated {@code debug}
     * is still one: the bot's Java writes it, and a value read must be written back.
     */
    @SuppressWarnings("deprecation")
    public static final DeclaredCall<BotSettings> SETTINGS = ComponentType.part(BotSettings.class)
            .writtenAs(BotSettings::defaults)
            .with(BotSettings::foundDelay, BotSettings::foundDelay)
            .with(BotSettings::notFoundDelay, BotSettings::notFoundDelay)
            .flag(BotSettings::centredClicks, settings -> !settings.randomizeClicks())
            .with(BotSettings::confidence, BotSettings::confidence)
            .with(BotSettings::compareMargin, BotSettings::compareMargin)
            .with(BotSettings::where, BotSettings::where)
            .flag(BotSettings::takesOver, BotSettings::takeOver)
            .with(BotSettings::displayBackend, settings -> settings.runIn().displayBackend())
            .with(BotSettings::inputBackend, settings -> settings.runIn().inputBackend())
            .with(BotSettings::maxRetryAttempts, BotSettings::maxRetryAttempts)
            .flag(BotSettings::debugOff, settings -> !settings.debug());

    /** Every call a settings value is written as. */
    public static final List<ComponentType<?>> ALL = List.of(SETTINGS);
}
