package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.Runs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.run.RunEntry;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.internal.explore.ExploreEvent;
import com.botmaker.sdk.internal.explore.Journal;
import com.botmaker.sdk.internal.explore.JournalView;
import com.botmaker.sdk.internal.explore.Stop;
import com.botmaker.sdk.internal.explore.StopKind;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.run.SdkRuns;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 🔭 Explore, headless: its feed, the stop pinned, an empty journal, and Start asking for Keep exploring. */
class ExploreViewTest extends ApplicationTest {

    static {
        // Read when the toolkit starts, so before ApplicationTest starts it: no display needed.
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    Path folder;

    private final List<RunEntry> started = new ArrayList<>();
    /** What the fake host says of a run: the explore run's JVM up or not. */
    private boolean running;
    /** The process the fake host runs; none known by default. */
    private OptionalLong pid = OptionalLong.empty();
    private Journal journal;
    private ExploreView view;
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
    }

    @BeforeEach
    void build() {
        journal = new Journal(folder.resolve("explore"));
        interact(() -> {
            view = new ExploreView(services(), () -> ScreenGraph.of(Map.of()), journal, () -> 10_000);
            stage.setScene(new Scene((Region) view.node(), 900, 600));
            stage.show();
        });
    }

    @Test
    void feedShowsNewestFirst() {
        running = true;
        journal.append(new ExploreEvent.Began(1_000));
        journal.append(new ExploreEvent.NewScreen("HOME", null, null, 2_000));
        journal.append(new ExploreEvent.NewScreen("SHOP", "HOME", "shop", 3_000));
        journal.append(new ExploreEvent.GoalMet("GOLD", 4_000));

        interact(() -> view.show(JournalView.of(journal)));

        List<String> lines = view.feedLines();
        assertEquals(4, lines.size());
        assertTrue(lines.get(0).endsWith("🎯 GOLD met"), lines.get(0));
        assertTrue(lines.get(1).endsWith("✚ SHOP, from HOME by shop"), lines.get(1));
        assertTrue(lines.get(3).endsWith("▶ Began"), lines.get(3));
        assertTrue(view.statusText().contains("(+1 new)"), view.statusText());
        assertTrue(view.statusText().endsWith("running"), view.statusText());
    }

    @Test
    void openStopIsPinned() {
        running = true;
        Stop stop = new Stop("MISSING_FACT-1", StopKind.MISSING_FACT, "HOME", "HOME#10,10", "", List.of(), List.of(),
                Map.of(), List.of(), "the gold no readout reads", 2_000);
        journal.append(new ExploreEvent.Began(1_000));
        journal.append(new ExploreEvent.Stopped(stop, false, 2_000));
        journal.append(new ExploreEvent.Claude("crop_pictures", 3_000));

        interact(() -> view.show(JournalView.of(journal)));

        List<String> pinned = view.pinnedLines();
        assertEquals(2, pinned.size());
        assertTrue(pinned.get(0).startsWith("⏸ MISSING_FACT-1"), pinned.get(0));
        assertTrue(pinned.get(0).endsWith("the gold no readout reads"), pinned.get(0));
        assertEquals("Claude: crop_pictures", pinned.get(1));
        assertTrue(view.statusText().endsWith("⏸ waiting on Claude"), view.statusText());
        assertFalse(view.feedLines().stream().anyMatch(l -> l.contains("crop_pictures")), "Claude's calls are pinned");

        journal.append(new ExploreEvent.Fixed("MISSING_FACT-1", "a readout", 4_000));
        interact(() -> view.show(JournalView.of(journal)));
        assertEquals(List.of(), view.pinnedLines(), "a fixed stop is not pinned");
    }

    @Test
    void killedRunShowsEnded() {
        Stop stop = new Stop("GUARD-1", StopKind.GUARD, "HOME", "HOME", "", List.of(), List.of(), Map.of(), List.of(),
                "a foreign window", 2_000);
        journal.append(new ExploreEvent.Began(1_000));
        journal.append(new ExploreEvent.Stopped(stop, false, 4_000));

        // Its JVM was stopped from Studio: no Ended line, and the host runs nothing.
        interact(() -> view.show(JournalView.of(journal)));

        assertEquals("0:00:03 · 0 screens known (+0 new) · 0 stops fixed · ended", view.statusText());
        assertEquals(List.of(), view.pinnedLines(), "nothing waits on Claude");
    }

    @Test
    void laterRunIsNotTheExploreRun() {
        Stop stop = new Stop("GUARD-1", StopKind.GUARD, "HOME", "HOME", "", List.of(), List.of(), Map.of(), List.of(),
                "a foreign window", 2_000);
        journal.append(new ExploreEvent.Began(1_000));
        journal.append(new ExploreEvent.Stopped(stop, false, 4_000));

        // The explore run was killed; the host now runs a process that began long after it: the bot's own run.
        running = true;
        pid = OptionalLong.of(ProcessHandle.current().pid());
        interact(() -> view.show(JournalView.of(journal)));

        assertTrue(view.statusText().endsWith("ended"), view.statusText());
        assertEquals(List.of(), view.pinnedLines());
        assertTrue(view.stopButton().isDisabled(), "Stop never ends the bot's own run");
    }

    @Test
    void claudeBeforeTheStopIsNotPinned() {
        running = true;
        Stop first = new Stop("MISSING_FACT-1", StopKind.MISSING_FACT, "HOME", "HOME#10,10", "", List.of(), List.of(),
                Map.of(), List.of(), "the gold no readout reads", 2_000);
        Stop second = new Stop("MISSING_FACT-2", StopKind.MISSING_FACT, "SHOP", "SHOP#5,5", "", List.of(), List.of(),
                Map.of(), List.of(), "the gems no readout reads", 5_000);
        journal.append(new ExploreEvent.Began(1_000));
        journal.append(new ExploreEvent.Stopped(first, false, 2_000));
        journal.append(new ExploreEvent.Claude("edit_map", 3_000));
        journal.append(new ExploreEvent.Fixed("MISSING_FACT-1", "a readout", 4_000));
        journal.append(new ExploreEvent.Stopped(second, false, 5_000));

        interact(() -> view.show(JournalView.of(journal)));

        assertEquals("Claude: not on it yet", view.pinnedLines().get(1));
        assertFalse(view.stopButton().isDisabled(), "the explore run's Stop");
    }

    @Test
    void noJournalShowsEmpty() {
        // The scene's tick read the journal folder, which no run ever made.
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> view.show(JournalView.of(journal)));

        assertEquals(List.of(), view.feedLines());
        assertEquals(List.of(), view.pinnedLines());
        assertTrue(view.statusText().startsWith("No explore run yet"), view.statusText());
        assertFalse(view.startButton().isDisabled(), "Start enabled");
    }

    @Test
    void startAsksForKeepExploring() {
        interact(() -> view.startButton().fire());

        assertEquals(List.of(SdkRuns.KEEP_EXPLORING), started.stream().map(RunEntry::title).toList());
        assertFalse(started.getFirst().onBar());
    }

    private StudioServices services() {
        Runs runs = new Runs() {
            @Override
            public void start() {
            }

            @Override
            public void start(RunEntry entry) {
                started.add(entry);
            }

            @Override
            public void stop() {
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public OptionalLong pid() {
                return pid;
            }

            @Override
            public AutoCloseable onStateChanged(Consumer<Boolean> listener) {
                return () -> {
                };
            }

            @Override
            public AutoCloseable onTelemetry(Consumer<byte[]> listener) {
                return () -> {
                };
            }
        };
        return new StudioServices() {
            @Override
            public Path projectDir() {
                return folder;
            }

            @Override
            public Path resourcesDir() {
                return folder;
            }

            @Override
            public Theme theme() {
                return null;
            }

            @Override
            public Dialogs dialogs() {
                return null;
            }

            @Override
            public PluginValues pluginValues() {
                return null;
            }

            @Override
            public History history() {
                return History.NONE;
            }

            @Override
            public Runs runs() {
                return runs;
            }
        };
    }
}
