// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.ExploreLoop;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.explore.StopRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The run's one writer of its workers' edits (plan 2026-11-24-explore-agents, phase 5): each id written once. */
class EditWriterTest {

    @TempDir
    Path folder;

    private final List<Map<String, Object>> written = new CopyOnWriteArrayList<>();

    /** An edit_map that takes every edit but one whose action is {@code wrong}. */
    private final EditWriter.Apply apply = (tool, arguments) -> {
        written.add(arguments);
        return "wrong".equals(arguments.get("action")) ? AgentReply.refused("no such action")
                : AgentReply.text("named " + arguments.get("screen"));
    };

    private Journal journal() {
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, "", 1));
        journal.append(new ExploreEvent.Stopped(new Stop("unknown_screen-1", StopKind.UNKNOWN_SCREEN, null, "", "",
                List.of(), List.of(), Map.of(), List.of(), "a screen the map lacks", 2), false, 2));
        return journal;
    }

    private static ExploreEvent.EditQueued queued(String id, String edit) {
        return new ExploreEvent.EditQueued(id, "unknown_screen-1", "edit_map", edit, 3);
    }

    private static List<ExploreEvent.EditApplied> applied(Journal journal) {
        return journal.events().stream().filter(ExploreEvent.EditApplied.class::isInstance)
                .map(ExploreEvent.EditApplied.class::cast).toList();
    }

    @Test
    void eachQueuedEditIsWrittenOnceInOrder() {
        Journal journal = journal();
        journal.append(queued("e-1", "{\"action\":\"name_screen\",\"screen\":\"SETTINGS\"}"));
        journal.append(queued("e-2", "{\"action\":\"name_screen\",\"screen\":\"ACCOUNTS\"}"));
        journal.append(queued("e-1", "{\"action\":\"name_screen\",\"screen\":\"SETTINGS\"}"));

        assertEquals(2, EditWriter.drain(journal, JournalView.of(journal), apply, () -> 9));
        assertEquals(0, EditWriter.drain(journal, JournalView.of(journal), apply, () -> 10), "nothing is left");

        assertEquals(List.of("SETTINGS", "ACCOUNTS"), written.stream().map(a -> a.get("screen")).toList());
        assertEquals(List.of("e-1", "e-2"), applied(journal).stream().map(ExploreEvent.EditApplied::editId).toList());
        assertEquals("named SETTINGS", applied(journal).getFirst().said());
    }

    /** A core restarted between a queue and its write writes only what was not written yet. */
    @Test
    void aRestartWritesOnlyWhatWasNotWritten() {
        Journal journal = journal();
        journal.append(queued("e-1", "{\"action\":\"name_screen\",\"screen\":\"SETTINGS\"}"));
        journal.append(new ExploreEvent.EditApplied("e-1", true, "named SETTINGS", 4));
        journal.append(queued("e-2", "{\"action\":\"name_screen\",\"screen\":\"ACCOUNTS\"}"));

        // The new core's writer, reading the journal from its start.
        EditWriter.drain(new Journal(folder), JournalView.of(new Journal(folder)), apply, () -> 9);

        assertEquals(List.of("ACCOUNTS"), written.stream().map(a -> a.get("screen")).toList());
        assertEquals(List.of("e-1", "e-2"), applied(journal).stream().map(ExploreEvent.EditApplied::editId).toList());
    }

    @Test
    void aRefusedOrBrokenEditIsSaidAndNotTriedAgain() {
        Journal journal = journal();
        journal.append(queued("e-1", "{\"action\":\"wrong\"}"));
        journal.append(queued("e-2", "not json"));
        journal.append(new ExploreEvent.EditQueued("e-3", "unknown_screen-1", "launch_game", "{\"action\":\"x\"}", 3));

        EditWriter.drain(journal, JournalView.of(journal), apply, () -> 9);
        EditWriter.drain(journal, JournalView.of(journal), apply, () -> 10);

        assertEquals(1, written.size(), "only the JSON edit of a map tool reached the tool, once");
        List<ExploreEvent.EditApplied> applied = applied(journal);
        assertEquals(3, applied.size());
        assertTrue(applied.stream().noneMatch(ExploreEvent.EditApplied::ok));
        assertEquals("no such action", applied.get(0).said());
        assertEquals("the edit is not JSON", applied.get(1).said());
        assertEquals("no worker writes by launch_game", applied.get(2).said());
    }

    /** apply_edit sent twice by one id queues it once, and both answers are its one write. */
    @Test
    void applyEditByARepeatedIdIsWrittenOnce() throws Exception {
        Journal journal = journal();
        AtomicBoolean done = new AtomicBoolean();
        AtomicBoolean writing = new AtomicBoolean();
        Thread writer = Thread.ofVirtual().start(() -> {
            while (!done.get()) {
                CoordinatorHost.writeEdits(journal, JournalView.of(journal), apply, Runnable::run, writing, () -> 9);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException stopped) {
                    return;
                }
            }
        });
        try {
            String edit = "{\"action\":\"name_screen\",\"screen\":\"SETTINGS\"}";
            AgentReply first = ExploreTools.applyEdit(journal, "unknown_screen-1", "e-1", edit, Duration.ofSeconds(10));
            AgentReply again = ExploreTools.applyEdit(journal, "unknown_screen-1", "e-1", edit, Duration.ofSeconds(10));

            assertFalse(first.isRefused());
            assertEquals(first.parts().getFirst().text(), again.parts().getFirst().text());
            assertTrue(first.parts().getFirst().text().startsWith("Written e-1: named SETTINGS"));
            assertEquals(1, journal.events().stream().filter(ExploreEvent.EditQueued.class::isInstance).count());
            assertEquals(1, applied(journal).size());
            assertEquals(1, written.size());

            AgentReply other = ExploreTools.applyEdit(journal, "unknown_screen-1", "e-1",
                    "{\"action\":\"name_screen\",\"screen\":\"ACCOUNTS\"}", Duration.ofSeconds(1));
            assertTrue(other.isRefused(), "another edit by a known id is refused, not answered as the first");
            assertTrue(other.parts().getFirst().text().contains("id of its own"));
            assertEquals(1, written.size());
        } finally {
            done.set(true);
            writer.join();
        }
    }

    @Test
    void applyEditRefusesWhatIsNotAnEditOfAStop() {
        Journal journal = journal();

        assertTrue(ExploreTools.applyEdit(journal, "unknown_screen-1", "e-1", "{\"screen\":\"X\"}",
                Duration.ofSeconds(1)).isRefused(), "no action");
        assertTrue(ExploreTools.applyEdit(journal, "nope-3", "e-2", "{\"action\":\"a\"}",
                Duration.ofSeconds(1)).isRefused(), "no such stop");
        assertTrue(ExploreTools.applyEdit(journal, "unknown_screen-1", " ", "{\"action\":\"a\"}",
                Duration.ofSeconds(1)).isRefused(), "no id");
        assertTrue(journal.events().stream().noneMatch(ExploreEvent.EditQueued.class::isInstance));
    }

    /** A journal whose open stop met a frame of {@code width} saved in the run's folder (none when 0), leased to the Reader. */
    private Journal journalOnFrame(int width) throws Exception {
        String frame = "";
        if (width > 0) {
            ImageIO.write(new BufferedImage(width, 40, BufferedImage.TYPE_INT_RGB), "png",
                    folder.resolve("frame-1.png").toFile());
            frame = "frame-1.png";
        }
        Journal journal = new Journal(folder);
        journal.append(new ExploreEvent.Began(false, "", 1));
        journal.append(new ExploreEvent.Stopped(new Stop("unknown_screen-1", StopKind.UNKNOWN_SCREEN, null, "", frame,
                List.of(), List.of(), Map.of(), List.of(), "a screen the map lacks", 2), false, 2));
        journal.append(new ExploreEvent.Leased("unknown_screen-1", StopRole.CARTOGRAPHER, 1, Long.MAX_VALUE, 2));
        return journal;
    }

    /** The cut and the screen it marks are made on the stop's saved frame, which the assistant's screenshot is not. */
    @Test
    void aCutAndANewScreenAreWrittenOnTheStopsFrame() throws Exception {
        Frames.forget();
        Journal journal = journalOnFrame(123);
        List<Integer> seen = new CopyOnWriteArrayList<>();
        EditWriter.Apply onFrame = (tool, arguments) -> {
            seen.add(Frames.shown().map(BufferedImage::getWidth).orElse(-1));
            return AgentReply.text(tool + " done");
        };
        journal.append(new ExploreEvent.EditQueued("e-1", "unknown_screen-1", "crop_pictures",
                "{\"boxes\":[{\"name\":\"settings_title\",\"x\":1,\"y\":1,\"width\":20,\"height\":10}]}", 3));
        journal.append(queued("e-2", "{\"action\":\"new_screen\",\"screen\":\"HOME\",\"name\":\"SETTINGS\","
                + "\"picture\":\"settings_icon\"}"));

        assertEquals(2, EditWriter.drain(journal, JournalView.of(journal), onFrame, () -> 9));

        assertEquals(List.of(123, 123), seen, "both edits saw the stop's frame as the shown one");
        assertTrue(applied(journal).stream().allMatch(ExploreEvent.EditApplied::ok));
        assertTrue(Frames.shown().isEmpty(), "the assistant's own screenshot is left as it was");
    }

    @Test
    void aCutOfAStopWithNoFrameIsRefused() throws Exception {
        Journal journal = journalOnFrame(0);
        journal.append(new ExploreEvent.EditQueued("e-1", "unknown_screen-1", "crop_pictures",
                "{\"boxes\":[{\"name\":\"x\",\"x\":1,\"y\":1,\"width\":2,\"height\":2}]}", 3));

        EditWriter.drain(journal, JournalView.of(journal), apply, () -> 9);

        assertTrue(written.isEmpty());
        assertTrue(applied(journal).getFirst().said().startsWith("no frame to cut from"));
    }

    @Test
    void applyEditRefusesAnotherToolAndACutWithoutBoxes() throws Exception {
        Journal journal = journalOnFrame(10);

        assertTrue(ExploreTools.applyEdit(journal, "unknown_screen-1", "e-1", "launch_game", "{\"action\":\"a\"}",
                Duration.ofSeconds(1)).isRefused());
        assertTrue(ExploreTools.applyEdit(journal, "unknown_screen-1", "e-2", "crop_pictures", "{\"screen\":\"X\"}",
                Duration.ofSeconds(1)).isRefused());
        assertTrue(journal.events().stream().noneMatch(ExploreEvent.EditQueued.class::isInstance));
    }

    /** A Reader that drew nothing may not ignore a screen the map lacks: the run would stop on it again (F1's loop). */
    @Test
    void aReadersIgnoreWithNothingDrawnIsRefused() throws Exception {
        Journal journal = journalOnFrame(10);
        journal.append(queued("e-1", "{\"action\":\"wrong\"}"));
        journal.append(new ExploreEvent.EditApplied("e-1", false, "no such action", 4));
        // A cut alone draws no screen.
        journal.append(new ExploreEvent.EditQueued("e-2", "unknown_screen-1", "crop_pictures", "{\"boxes\":[]}", 4));
        journal.append(new ExploreEvent.EditApplied("e-2", true, "cut settings_title", 4));

        AgentReply refused = ExploreTools.resume(journal, "unknown_screen-1", true, Duration.ofMillis(100));

        assertTrue(refused.isRefused());
        assertEquals(ExploreTools.NOTHING_DRAWN, refused.parts().getFirst().text());
        assertTrue(journal.events().stream().noneMatch(e -> e instanceof ExploreEvent.Claude c
                && c.call().startsWith("resume")), "no resume was sent to the run");

        journal.append(queued("e-3", "{\"action\":\"new_screen\",\"screen\":\"HOME\",\"name\":\"SETTINGS\"}"));
        journal.append(new ExploreEvent.EditApplied("e-3", true, "new SETTINGS", 5));
        ExploreTools.resume(journal, "unknown_screen-1", true, Duration.ofMillis(100));
        assertTrue(journal.events().stream().anyMatch(e -> e instanceof ExploreEvent.Claude c
                && c.call().equals("resume unknown_screen-1 ignore")), "once drawn, the ignore is sent");
    }

    /** The FX thread, where edit_map writes, sees the writer's frame: a scoped frame is carried to it. */
    @Test
    void theStopsFrameIsCarriedToTheThreadThatWrites() throws Exception {
        Frames.forget();
        BufferedImage frame = new BufferedImage(77, 5, BufferedImage.TYPE_INT_RGB);
        int seen = Frames.showing(Optional.of(frame), () -> {
            Supplier<Integer> carried = Frames.carried(() -> Frames.shown().map(BufferedImage::getWidth).orElse(-1));
            try (var other = Executors.newSingleThreadExecutor()) {
                return other.submit(carried::get).get();
            } catch (Exception failed) {
                throw new IllegalStateException(failed);
            }
        });
        assertEquals(77, seen);
        assertTrue(Frames.showing(Optional.empty(), Frames::shown).isEmpty(), "a stop with no frame shows none");
    }

    /** An edit queued but never written, asked after the run ended, is refused at once rather than waited on. */
    @Test
    void anEditLeftWhenTheRunEndedIsSaidNeverWritten() {
        Journal journal = journal();
        String edit = "{\"action\":\"name_screen\",\"screen\":\"SETTINGS\"}";
        journal.append(queued("e-1", edit));
        journal.append(new ExploreEvent.Ended(ExploreLoop.End.STOPPED, 5));

        long started = System.nanoTime();
        AgentReply reply = ExploreTools.applyEdit(journal, "unknown_screen-1", "e-1", edit, Duration.ofSeconds(30));

        assertTrue(reply.isRefused());
        assertTrue(reply.parts().getFirst().text().contains("never written"));
        assertTrue(System.nanoTime() - started < Duration.ofSeconds(5).toNanos());
    }
}
