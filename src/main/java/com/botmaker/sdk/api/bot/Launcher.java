package com.botmaker.sdk.api.bot;

/**
 * One launcher a bot can start its game from — a launch spec: {@code steam:1145350}, {@code heroic:43d4…}, an
 * emulator's app, a program's path. {@code Sdk.launchTargets} lists them, preferred first, and a run starts the
 * first one its computer's system can launch.
 *
 * <p>A type of its own rather than a {@code String} so the Runner and the Parameters window offer the game picker
 * for it (🎮 Game), not a text field (2026-10-13).
 */
public final class Launcher {

    private final String spec;

    private Launcher(String spec) {
        this.spec = spec == null ? "" : spec.trim();
    }

    /** The launcher {@code spec} names, as 🎮 Game writes it. */
    public static Launcher of(String spec) {
        return new Launcher(spec);
    }

    /** The launch spec, trimmed; {@code ""} for none. */
    public String spec() {
        return spec;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Launcher that && spec.equals(that.spec);
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
