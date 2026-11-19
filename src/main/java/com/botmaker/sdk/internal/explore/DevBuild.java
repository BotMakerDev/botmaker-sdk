// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

/**
 * Whether the run plays on an SDK under development (2026-11-19): its own version, stamped in the jar's manifest, ends
 * in {@code -SNAPSHOT}, which only a dev-mode project loads. Only then is a Discover fault raised and held: a bot's
 * author, on a released SDK, is never told to edit the SDK.
 */
final class DevBuild {

    private DevBuild() {}

    /** Whether this SDK is a {@code -SNAPSHOT} build. */
    static boolean on() {
        return version().endsWith("-SNAPSHOT");
    }

    /** This SDK's version as its jar says it; blank when no manifest says it (classes run from a folder). */
    static String version() {
        String version = DevBuild.class.getPackage().getImplementationVersion();
        return version == null ? "" : version;
    }
}
