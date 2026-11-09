package com.botmaker.sdk.api.bot;

/**
 * The game VM the bot's game runs in when its {@code gameRunsIn()} setting is {@link BotSettings.Where#VM}, by name —
 * {@code Sdk.vm()}; {@code GameVm.of("")} for the one VM there is.
 *
 * <p>A type of its own rather than a {@code String} so Studio draws it as this computer's VMs, with their setup and
 * their screen beside it (2026-11-09, when ⚙ Bot Settings, which held them, went).
 */
public final class GameVm {

    private final String name;

    private GameVm(String name) {
        this.name = name == null ? "" : name.trim();
    }

    /** The VM named {@code name}; {@code ""} for the one VM there is. */
    public static GameVm of(String name) {
        return new GameVm(name);
    }

    /** The VM's name, trimmed; {@code ""} for the one there is. */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GameVm that && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
