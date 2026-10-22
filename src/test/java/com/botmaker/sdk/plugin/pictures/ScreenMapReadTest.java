package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The map read once until the bot's code changes: the live feed asks every second, each card at each draw. */
class ScreenMapReadTest {

    @Test
    void oneReadServesEveryAskUntilAHistoryStepOrAWrite() {
        Values values = new Values();
        Steps history = new Steps();
        StudioServices services = services(values, history);

        ScreenMapRead.forget();
        for (int i = 0; i < 5; i++) ScreenMapRead.read(services);
        assertEquals(1, values.reads, "unchanged code is read once");

        history.step();
        ScreenMapRead.orEmpty(services);
        ScreenMapRead.read(services);
        assertEquals(2, values.reads, "a history step drops the read kept");

        ScreenMapRead.forget();
        ScreenMapRead.read(services);
        assertEquals(3, values.reads, "a write drops it too");
    }

    @Test
    void sameTextReadsOnce(@TempDir Path project) throws IOException {
        Path folder = Files.createDirectories(project.resolve("src/main/java/com/bot/plugins/sdk"));
        Path screens = folder.resolve("Screens.java");
        Path pictures = folder.resolve("Pictures.java");
        Files.writeString(screens, "class Screens {}");
        Files.writeString(pictures, "class Pictures {}");
        Values values = new Values();
        Steps history = new Steps();
        StudioServices services = services(values, history, project);

        ScreenMapRead.forget();
        ScreenMapRead.read(services);
        history.step();
        ScreenMapRead.read(services);
        assertEquals(1, values.reads, "a step that left the map's files as they were reads nothing");

        Files.writeString(pictures, "class Pictures { int recut; }");
        history.step();
        ScreenMapRead.read(services);
        assertEquals(2, values.reads, "a file beside Screens.java is part of what the map reads");

        Files.writeString(screens, "class Screens { int home; }");
        history.step();
        ScreenMapRead.read(services);
        Files.writeString(screens, "class Screens { int home; }");
        ScreenMapRead.forget();
        ScreenMapRead.read(services);
        assertEquals(3, values.reads, "rewritten to the same text, the map is not read again");
    }

    @Test
    void aHostWithNoHistoryKeepsNothing() {
        Values values = new Values();
        StudioServices services = services(values, History.NONE);

        ScreenMapRead.read(services);
        ScreenMapRead.read(services);
        assertEquals(2, values.reads);
    }

    @Test
    void aClassThatWillNotReadFailsOnceUntilItChanges() {
        Values values = new Values();
        values.broken = true;
        Steps history = new Steps();
        StudioServices services = services(values, history);

        ScreenMapRead.forget();
        assertThrows(IllegalStateException.class, () -> ScreenMapRead.read(services));
        assertEquals(0, ScreenMapRead.orEmpty(services).screens().size());
        assertEquals(1, values.reads, "the failure is kept like a read");

        values.broken = false;
        history.step();
        ScreenMapRead.read(services);
        assertEquals(2, values.reads);
    }

    /** A bot's values with no screens, counting the reads of the map. */
    private static final class Values implements PluginValues {
        int reads;
        boolean broken;

        @Override
        public List<String> ids() {
            return List.of();
        }

        @Override
        public Optional<ValueContext> open(ManagedValue<?> value) {
            return Optional.empty();
        }

        @Override
        public List<String> groups(ManagedValue<?> set) {
            reads++;
            if (broken) throw new IllegalStateException("Screens.java does not parse");
            return List.of();
        }
    }

    /** A history whose steps the test takes. */
    private static final class Steps implements History {
        private final List<Runnable> listeners = new ArrayList<>();

        void step() {
            listeners.forEach(Runnable::run);
        }

        @Override
        public boolean canUndo() {
            return false;
        }

        @Override
        public boolean canRedo() {
            return false;
        }

        @Override
        public String undoLabel() {
            return "";
        }

        @Override
        public String redoLabel() {
            return "";
        }

        @Override
        public void undo() {}

        @Override
        public void redo() {}

        @Override
        public AutoCloseable onChanged(Runnable listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        @Override
        public void batch(String label, Runnable writes) {
            writes.run();
        }
    }

    private static StudioServices services(PluginValues values, History history) {
        return services(values, history, null);
    }

    private static StudioServices services(PluginValues values, History history, Path project) {
        return new StudioServices() {
            @Override
            public Path projectDir() {
                return project;
            }

            @Override
            public Path resourcesDir() {
                return null;
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
                return values;
            }

            @Override
            public History history() {
                return history;
            }
        };
    }
}
