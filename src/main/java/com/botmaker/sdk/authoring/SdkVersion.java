package com.botmaker.sdk.authoring;


import java.util.List;
import java.util.Optional;

/**
 * A released SDK version, as an authoring caller names it.
 *
 * <p>Every {@link Authoring} entry point takes one of these as its <b>first</b> argument, and that is the
 * whole point of the type: the SDK is now the generator, and a generator must be told which era's rules to
 * apply. A bot pinning 1.1.0 opened by an editor bundling 1.4.0 gets 1.1.0's project shape, not 1.4.0's —
 * the inversion's rule, that anything touching bot code takes its answer from <em>the bot's</em> SDK
 * version, is enforced by making the version unforgettable at the call site rather than by hoping a caller
 * passes it.
 *
 * <h2>The parse is total</h2>
 *
 * <p>{@link #of(String)} returns {@link Optional#empty()} for a version this build has never heard of and
 * never throws — the repo's rule for a persisted closed set. Empty is a real answer with a real meaning:
 * <em>this bot pins an SDK newer than the one doing the reading</em>, which is exactly the case
 * {@link AuthoringUnsupported} exists to report to the user. A caller that swallows the empty and guesses
 * {@link #latest()} has silently promised a bot something its own jar cannot deliver.
 *
 * <p>The constants are in release order, so {@link #latest()} is the last one and comparison is
 * {@link Enum#compareTo ordinal} comparison. The wire string is the plain semver with <b>no leading
 * {@code v}</b> — the git tag carries the {@code v}; nothing inside the API does, matching
 * {@link com.botmaker.sdk.api.meta.Since} and {@link com.botmaker.sdk.api.meta.Replaces}.
 */
public enum SdkVersion {

    /** The version the API-compatibility contract began at, and the first this enum records. */
    V1_1_0("1.1.0"),
    /** The version that introduced {@code api.authoring} — the SDK as the project's generator. */
    V1_2_0("1.2.0");

    private final String id;

    SdkVersion(String id) {
        this.id = id;
    }

    /** The stable wire form, as a pom pins it and as {@code activities.json} records it: {@code 1.2.0}. */
    public String id() {
        return id;
    }

    /** The newest version this build of the SDK knows how to generate for — itself. */
    public static SdkVersion latest() {
        SdkVersion[] all = values();
        return all[all.length - 1];
    }

    /** Every known version, oldest first. */
    public static List<SdkVersion> all() {
        return List.of(values());
    }

    /**
     * The version {@code id} names, or empty when this build does not know it.
     *
     * <p>Total: a blank, a null, a {@code 0.0.0-SNAPSHOT} and a {@code 9.9.9} all return empty rather than
     * throwing. A leading {@code v} is tolerated on the way in, because a caller reading a git tag has one.
     */
    public static Optional<SdkVersion> of(String id) {
        if (id == null) return Optional.empty();
        String trimmed = id.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) trimmed = trimmed.substring(1);
        for (SdkVersion candidate : values()) {
            if (candidate.id.equals(trimmed)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * The version a <em>pom pin</em> names — {@link #of(String)}, except that a snapshot or a blank pin
     * resolves to {@link #latest()}.
     *
     * <p>This is the method a caller reading {@code <botmaker.sdk.version>} out of a bot's pom wants. Every
     * committed pom in this repo pins {@code 0.0.0-SNAPSHOT} (the reactor value), and a locally installed
     * dev build is the version a dev-run Studio pins into every project it creates. Sending those through
     * {@link #of(String)} would answer "unknown", and creation would refuse in every development build —
     * a refusal about a version that is, by construction, this very jar.
     *
     * <p>A released pin that this build does not know still returns empty. That case is real and must stay
     * loud: the bot is newer than the reader.
     */
    public static Optional<SdkVersion> ofPin(String pin) {
        String trimmed = pin == null ? "" : pin.trim();
        if (trimmed.isEmpty() || trimmed.endsWith("-SNAPSHOT")) return Optional.of(latest());
        return of(trimmed);
    }

    /** Whether this version is at least {@code other} — release order is declaration order. */
    public boolean atLeast(SdkVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public String toString() {
        return id;
    }
}
