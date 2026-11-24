// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.overlay.Area;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * The one writer of a run's edits (spec 2026-11-24-explore-agents-design § Coordination rules): the explore crew's
 * workers queue their map edits in the journal ({@link ExploreEvent.EditQueued}) and this writer, on the Coordinator's
 * thread alone, carries each out once, in order, and records it ({@link ExploreEvent.EditApplied}). Two workers so never
 * race on the bot's map, and a core restarted between a queue and its write writes only what was not written yet.
 */
final class EditWriter {

    /** The map tools whose edits a worker may queue. */
    static final Set<String> TOOLS = Set.of("edit_map");

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Carries out one queued edit: the tool's answer. */
    @FunctionalInterface
    interface Apply {
        AgentReply apply(String tool, Map<String, Object> arguments);
    }

    private EditWriter() {}

    /** The edits into the bot {@code services} has open, by the SDK's own map tools. */
    static Apply into(StudioServices services) {
        AgentContext context = new Context(services);
        return (tool, arguments) -> SdkAssist.ALL.stream().filter(t -> t.name().equals(tool)).findFirst()
                .map(t -> t.invoke(arguments, context))
                .orElseGet(() -> AgentReply.refused("No map tool " + tool + "."));
    }

    /**
     * Carries out every edit {@code view} holds queued and not yet carried out, by {@code apply}, each recorded in
     * {@code journal} at the clock's time, taken or refused: a refused edit is not tried again.
     *
     * @return how many were carried out
     */
    static int drain(Journal journal, JournalView view, Apply apply, LongSupplier clock) {
        List<ExploreEvent.EditQueued> queued = view.unapplied();
        for (ExploreEvent.EditQueued edit : queued) {
            journal.append(write(edit, apply, clock.getAsLong()));
        }
        return queued.size();
    }

    private static ExploreEvent.EditApplied write(ExploreEvent.EditQueued edit, Apply apply, long at) {
        if (!TOOLS.contains(edit.tool())) {
            return new ExploreEvent.EditApplied(edit.editId(), false, "no worker writes by " + edit.tool(), at);
        }
        Optional<Map<String, Object>> arguments = arguments(edit.edit());
        if (arguments.isEmpty()) return new ExploreEvent.EditApplied(edit.editId(), false, "the edit is not JSON", at);
        AgentReply reply;
        try {
            reply = apply.apply(edit.tool(), arguments.get());
        } catch (RuntimeException failed) {
            return new ExploreEvent.EditApplied(edit.editId(), false, String.valueOf(failed.getMessage()), at);
        }
        return new ExploreEvent.EditApplied(edit.editId(), !reply.isRefused(), said(reply), at);
    }

    /** {@code json} as a tool's arguments: an object; empty when it is not one. */
    static Optional<Map<String, Object>> arguments(String json) {
        try {
            Map<String, Object> read = JSON.readValue(json, new TypeReference<>() {});
            return Optional.ofNullable(read);
        } catch (IOException | RuntimeException notAnObject) {
            return Optional.empty();
        }
    }

    /** What {@code reply} says in text, its pictures left out. */
    private static String said(AgentReply reply) {
        return reply.parts().stream().map(AgentReply.Part::text).filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));
    }

    /** A map tool's context with no game on screen: a worker's edit writes the bot's map, it acts on no game. */
    private record Context(StudioServices services) implements AgentContext {
        @Override
        public Optional<BufferedImage> frame() {
            return Optional.empty();
        }

        @Override
        public Optional<Area> watchedArea() {
            return Optional.empty();
        }
    }
}
