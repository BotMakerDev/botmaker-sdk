// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The one prompt a Reader session is started with (spec 2026-11-24-explore-agents-design § The crew): the stop and the
 * run's goal in, its answer written through the explore tool's {@code look}, {@code apply_edit}, {@code rank_presses}
 * and {@code resume}. The text is the resource {@value #RESOURCE}; a wrong map the Evaluator found
 * ({@link StopKind#BAD_MAP}) is {@value #BAD_MAP_RESOURCE}, which redraws a screen's marks and resumes nothing; a run
 * given a goal stuck with nothing to press ({@link StopKind#STUCK}) is {@value #STUCK_RESOURCE}, which adds the
 * frame's presses toward the goal; inputs with no verdict ({@link StopKind#NO_VERDICT}) are
 * {@value #VERDICTS_RESOURCE}, which judges each against the goal and ranks them.
 */
final class ReaderPrompt {

    static final String RESOURCE = "reader-prompt.md";
    static final String BAD_MAP_RESOURCE = "reader-bad-map-prompt.md";
    static final String STUCK_RESOURCE = "reader-stuck-prompt.md";
    static final String VERDICTS_RESOURCE = "reader-verdicts-prompt.md";

    private static final String TEXT = read(RESOURCE);
    private static final String BAD_MAP_TEXT = read(BAD_MAP_RESOURCE);
    private static final String STUCK_TEXT = read(STUCK_RESOURCE);
    private static final String VERDICTS_TEXT = read(VERDICTS_RESOURCE);

    private ReaderPrompt() {}

    /** The prompt for {@code stop} of a run exploring toward {@code goal} (blank: none). */
    static String of(Stop stop, String goal) {
        boolean badMap = stop.kind() == StopKind.BAD_MAP;
        String on = stop.screen() == null || stop.screen().isBlank() ? "" : stop.screen();
        String text = switch (stop.kind()) {
            case BAD_MAP -> BAD_MAP_TEXT;
            case STUCK -> STUCK_TEXT;
            case NO_VERDICT -> VERDICTS_TEXT;
            default -> TEXT;
        };
        return text.replace("{stop}", stop.id())
                .replace("{kind}", stop.kind().displayName())
                .replace("{on}", badMap ? on : on.isEmpty() ? " on a screen the map lacks" : " on " + on)
                .replace("{why}", stop.missing())
                .replace("{goal}", goal == null || goal.isBlank() ? "none given; map what the screen is" : goal);
    }

    private static String read(String resource) {
        try (InputStream in = ReaderPrompt.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException(resource + " is not in the SDK's jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
