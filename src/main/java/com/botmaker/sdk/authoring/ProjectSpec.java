package com.botmaker.sdk.authoring;

import com.botmaker.sdk.api.geometry.Size;

/**
 * What a caller must decide before a project can be created — the arguments to
 * {@link Authoring#createProject}, gathered into one value so the call site cannot silently omit one.
 *
 * <p>Deliberately small. It holds what the <em>generated files</em> depend on and nothing else: an editor's
 * toolchain paths, its projects root, its window state and its own settings file are not here, because none
 * of them changes a byte the SDK writes.
 *
 * @param projectName      the project's own name — the Maven {@code artifactId} and the directory name
 * @param packageName      the package every generated class is declared in
 * @param entryClassName   the class carrying {@code main}; also the Maven {@code mainClass}
 * @param kind             which shape of project to generate
 * @param sdkPin           the SDK version the generated {@code pom.xml} pins; blank ⇒ the generating SDK
 * @param referenceSize    the resolution captures are authored against, or {@code null} to leave it unset
 *                         and let the first capture seed it
 */
public record ProjectSpec(String projectName, String packageName, String entryClassName, Kind kind,
                          String sdkPin, Size referenceSize) {

    /**
     * The shapes of project the generator can produce.
     *
     * <p>Two, and the difference is not cosmetic: a {@link #GAME_BOT} has activities, a flow, a driver and an
     * {@code activities.json}; an {@link #EMPTY} project has a {@code main} and nothing else, and must not be
     * given an {@code activities.json} it never asked for and would carry forever.
     */
    public enum Kind {
        /** Supervised loop, activity flow, go-home recovery hook. */
        GAME_BOT,
        /** A bare {@code main()} — start from scratch. */
        EMPTY
    }

    public ProjectSpec {
        if (projectName == null) projectName = "";
        if (packageName == null) packageName = "";
        if (entryClassName == null) entryClassName = "";
        if (kind == null) kind = Kind.EMPTY;
        if (sdkPin == null) sdkPin = "";
    }
}
