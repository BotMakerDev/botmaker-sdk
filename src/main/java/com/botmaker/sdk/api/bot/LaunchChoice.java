package com.botmaker.sdk.api.bot;

/**
 * Which of the bot's launchers a run starts its game from — {@code Sdk.launchTarget}, picked by the person running
 * the bot from the ones its author lists in {@code Sdk.launchTargets}. {@code LaunchChoice.of("")} picks none: a run
 * starts the first launcher its computer's system can launch. A pick the author no longer lists is no pick.
 *
 * <p>A type of its own rather than a {@link Launcher} so the Runner offers only the author's launchers for it, where
 * a {@code Launcher} in the author's list is picked from every game on the computer (2026-10-19).
 */
public final class LaunchChoice {

    private final String spec;

    private LaunchChoice(String spec) {
        this.spec = spec == null ? "" : spec.trim();
    }

    /** The launcher {@code spec} names, as the Runner writes it; {@code ""} for the first one this computer can launch. */
    public static LaunchChoice of(String spec) {
        return new LaunchChoice(spec);
    }

    /** The launch spec, trimmed; {@code ""} for no pick. */
    public String spec() {
        return spec;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LaunchChoice that && spec.equals(that.spec);
    }

    @Override
    public int hashCode() {
        return spec.hashCode();
    }

    @Override
    public String toString() {
        return spec;
    }
}
