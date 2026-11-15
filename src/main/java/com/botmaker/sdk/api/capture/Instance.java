package com.botmaker.sdk.api.capture;

/**
 * The emulator instance a run plays on, by the name a bot names it by ({@code Instance.named("LDPlayer-3")}):
 * {@code Sdk.instance()}, a parameter while the capture source is an emulator (2026-11-15, spec
 * {@code 2026-11-15-run-claims-design.md} §2). An emulator capture source reads its instance from it, so several runs
 * at once are each dealt one of this PC's instances and never share one. {@code Instance.named("")} names none: the
 * capture source's own name is used.
 */
public final class Instance {

    private final String name;

    private Instance(String name) {
        this.name = name == null ? "" : name.strip();
    }

    /** The instance named {@code name}; {@code ""} for none. */
    public static Instance named(String name) {
        return new Instance(name);
    }

    /** The instance's name, stripped; {@code ""} for none. */
    public String name() {
        return name;
    }

    /** Whether this names an instance. */
    public boolean isNamed() {
        return !name.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Instance that && name.equals(that.name);
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
