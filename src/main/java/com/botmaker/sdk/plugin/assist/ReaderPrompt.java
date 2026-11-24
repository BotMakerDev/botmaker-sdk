// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.Stop;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The one prompt a Reader session is started with (spec 2026-11-24-explore-agents-design § The crew): the stop and the
 * run's goal in, its answer written through the explore tool's {@code look}, {@code apply_edit}, {@code rank_presses}
 * and {@code resume}. The text is the resource {@value #RESOURCE}.
 */
final class ReaderPrompt {

    static final String RESOURCE = "reader-prompt.md";

    private static final String TEXT = read();

    private ReaderPrompt() {}

    /** The prompt for {@code stop} of a run exploring toward {@code goal} (blank: none). */
    static String of(Stop stop, String goal) {
        return TEXT.replace("{stop}", stop.id())
                .replace("{kind}", stop.kind().displayName())
                .replace("{on}", stop.screen() == null || stop.screen().isBlank() ? " on a screen the map lacks" : " on " + stop.screen())
                .replace("{goal}", goal == null || goal.isBlank() ? "none given; map what the screen is" : goal);
    }

    private static String read() {
        try (InputStream in = ReaderPrompt.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException(RESOURCE + " is not in the SDK's jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
