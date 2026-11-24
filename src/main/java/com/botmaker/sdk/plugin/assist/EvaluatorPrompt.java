// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The one prompt an Evaluator session is started with (spec 2026-11-24-explore-agents-design § The crew): the run's
 * goal and why it is judged now in, its verdict written through the explore tool's {@code progress},
 * {@code record_finding} and {@code propose_done}. At the run's end it also grades the map. The text is the resource
 * {@value #RESOURCE}, its end-only part {@value #AT_END}.
 */
final class EvaluatorPrompt {

    static final String RESOURCE = "evaluator-prompt.md";
    static final String AT_END = "evaluator-end.md";

    private static final String TEXT = read(RESOURCE);
    private static final String END_TEXT = read(AT_END);

    private EvaluatorPrompt() {}

    /** The prompt for the evaluation {@code id} of the run {@code run}, exploring toward {@code goal}, started {@code why}. */
    static String of(String id, String run, String goal, EvaluatorTrigger.Why why) {
        return TEXT.replace("{id}", id)
                .replace("{run}", run)
                .replace("{goal}", goal == null || goal.isBlank() ? "none given" : goal)
                .replace("{why}", why.displayName())
                .replace("{end}", why == EvaluatorTrigger.Why.END ? END_TEXT : "");
    }

    private static String read(String resource) {
        try (InputStream in = EvaluatorPrompt.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException(resource + " is not in the SDK's jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }
}
