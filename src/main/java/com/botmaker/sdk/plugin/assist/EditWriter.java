// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.plugin.api.overlay.Area;
import com.botmaker.sdk.internal.explore.EditTool;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * The one writer of a run's edits (spec 2026-11-24-explore-agents-design § Coordination rules): the explore crew's
 * workers queue their map edits and picture cuts in the journal ({@link ExploreEvent.EditQueued}) and this writer, on the
 * Coordinator's thread alone, carries each out once, in order, on its stop's saved frame, and records it
 * ({@link ExploreEvent.EditApplied}). Two workers so never race on the bot's map, and a core restarted between a queue
 * and its write writes only what was not written yet.
 */
final class EditWriter {

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
        // Each stop's frame read once a drain, however many of its edits are queued.
        Map<String, Optional<BufferedImage>> frames = new HashMap<>();
        for (ExploreEvent.EditQueued edit : queued) {
            Optional<BufferedImage> frame = frames.computeIfAbsent(edit.stopId(),
                    id -> view.stopOf(id).flatMap(stop -> frame(journal, stop.framePath())));
            List<String> elements = view.stopOf(edit.stopId()).map(Stop::elements).orElse(List.of());
            journal.append(write(edit, apply, frame, elements, clock.getAsLong()));
        }
        return queued.size();
    }

    /**
     * Carries {@code edit} out with {@code frame}, its stop's saved frame, as the shown screenshot: a box of a cut or of
     * an added text is in that frame's pixels, never the live screen's, which may have moved on since. An element is
     * saved only when {@code elements}, the stop's tree as it read, holds its words: the tree is read live, and a game
     * that moved on gives another screen's rows.
     */
    static ExploreEvent.EditApplied write(ExploreEvent.EditQueued edit, Apply apply, Optional<BufferedImage> frame,
                                          List<String> elements, long at) {
        EditTool tool = EditTool.fromId(edit.tool());
        if (tool == EditTool.UNKNOWN) {
            return new ExploreEvent.EditApplied(edit.editId(), false, "no worker writes by " + edit.tool(), at);
        }
        if (tool == EditTool.CROP_PICTURES && frame.isEmpty()) {
            return new ExploreEvent.EditApplied(edit.editId(), false, "no frame to cut from: the stop saved none", at);
        }
        Optional<Map<String, Object>> arguments = arguments(edit.edit());
        if (arguments.isEmpty()) return new ExploreEvent.EditApplied(edit.editId(), false, "the edit is not JSON", at);
        if (tool == EditTool.SAVE_ELEMENT) {
            Object words = arguments.get().get("words");
            if (elements.isEmpty()) {
                return new ExploreEvent.EditApplied(edit.editId(), false,
                        "the stop read no tree: mark it by a picture", at);
            }
            if (!(words instanceof String typed) || !inTree(elements, typed)) {
                return new ExploreEvent.EditApplied(edit.editId(), false, "no row \"" + words
                        + "\" among the stop's elements: give one as look says it", at);
            }
        }
        AgentReply reply;
        try {
            // Bound even when empty: a box of a stop with no frame is refused, never measured on another screenshot.
            reply = Frames.showing(frame, () -> apply.apply(tool.id(), arguments.get()));
        } catch (RuntimeException failed) {
            return new ExploreEvent.EditApplied(edit.editId(), false, String.valueOf(failed.getMessage()), at);
        }
        return new ExploreEvent.EditApplied(edit.editId(), !reply.isRefused(), said(reply), at);
    }

    /** The frame saved at {@code path}, as its stop names it (in the run's folder when relative); empty when unread. */
    static Optional<BufferedImage> frame(Journal journal, String path) {
        if (path == null || path.isBlank()) return Optional.empty();
        Path file = Path.of(path);
        if (!file.isAbsolute()) file = journal.folder().resolve(file);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            return Optional.ofNullable(ImageIO.read(file.toFile()));
        } catch (IOException unread) {
            return Optional.empty();
        }
    }

    /**
     * Whether {@code elements}, a stop's tree as its lines read ({@code "Wi-Fi" tappable (0,80 540x60)}), holds a row
     * of {@code words}, any case: its whole words, or their start, as list_elements shortens a long row.
     */
    static boolean inTree(List<String> elements, String words) {
        String typed = words.strip().toLowerCase(Locale.ROOT);
        if (typed.isEmpty()) return false;
        for (String line : elements) {
            int end = line.startsWith("\"") ? line.indexOf('"', 1) : -1;
            String read = (end > 0 ? line.substring(1, end) : line).strip().toLowerCase(Locale.ROOT);
            if (read.equals(typed) || read.startsWith(typed)) return true;
        }
        return false;
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
