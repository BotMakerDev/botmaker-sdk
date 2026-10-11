package com.botmaker.sdk.internal.bot;

import com.botmaker.plugin.api.params.Param;
import com.botmaker.sdk.api.bot.Setup;
import com.botmaker.shared.Diag;
import com.botmaker.shared.ipc.TelemetryEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The setup runs once per computer, and again when a Setup parameter's value changes. */
class SetupOnceTest {

    /** A bot's parameters: two the setup reads, one it does not. */
    static final class Parameters {
        @Param(category = Setup.CATEGORY)
        static String language = "English";

        @Param(category = Setup.CATEGORY)
        static int[] resolution = {1280, 720};

        @Param(category = "Timing")
        static int restSeconds = 60;
    }

    @AfterEach
    void tearDown() {
        Diag.setSink(null);
        Parameters.language = "English";
        Parameters.restSeconds = 60;
    }

    @Test
    void theSetupIsDueUntilItIsOffAndItsParametersAreUnchanged() {
        assertTrue(SetupOnce.due(null, null, "a"), "absent is on");
        assertTrue(SetupOnce.due("on", "a", "a"));
        assertTrue(SetupOnce.due("whatever", "a", "a"), "unreadable is on");
        assertFalse(SetupOnce.due("off", "a", "a"));
        assertTrue(SetupOnce.due("off", "a", "b"), "a Setup parameter changed");
        assertFalse(SetupOnce.due("off", null, "b"), "unticked by hand, with nothing stored");
    }

    @Test
    void onlyASetupParameterChangesTheHash() {
        List<Class<?>> classes = List.of(Parameters.class);
        String before = SetupOnce.paramsHash(classes);
        assertEquals(16, before.length());
        assertEquals(before, SetupOnce.paramsHash(classes), "the same values, the same hash");

        Parameters.restSeconds = 5;
        assertEquals(before, SetupOnce.paramsHash(classes), "a Timing parameter is not the setup's");
        Parameters.language = "French";
        assertNotEquals(before, SetupOnce.paramsHash(classes));
        assertNotEquals(SetupOnce.paramsHash(List.of()), before);
    }

    @Test
    void theBotsClassesAreThoseCompiledBesideItsValues() {
        List<Class<?>> beside = SetupOnce.classesBeside(SetupOnceTest.class, SetupOnceTest.class);
        assertTrue(beside.contains(Parameters.class));
        assertTrue(beside.stream().allMatch(c -> c.getPackageName().startsWith(SetupOnceTest.class.getPackageName())),
                "only the bot's own package: the folder holds other packages too");
        assertTrue(SetupOnce.classesBeside(SetupOnceTest.class, (Class<?>[]) null).isEmpty());
    }

    @Test
    void theDoneLineCarriesTheHashToTheHost() {
        List<TelemetryEvent.Log> lines = new ArrayList<>();
        Diag.setSink(lines::add);
        boolean wasOn = Diag.isEnabled();
        Diag.set(false);
        try {
            SetupOnce.done("0123456789abcdef");
        } finally {
            Diag.set(wasOn);
        }
        assertEquals(1, lines.size(), "traced with debugging off");
        TelemetryEvent.Log line = lines.getFirst();
        assertEquals(TelemetryEvent.Log.INFO, line.level());
        assertEquals(Optional.of("0123456789abcdef"), SetupOnce.reported(line.writerClass(), line.text()));
        assertEquals(Optional.empty(), SetupOnce.reported("com.example.Bot", line.text()), "another writer");
        assertEquals(Optional.empty(), SetupOnce.reported(SetupOnce.class.getName(), "setup"));
    }

    /** Unticked before it ever ran: the run reports the values it is off for, so a change to one re-ticks it. */
    @Test
    void aSetupSwitchedOffWithNoHashReportsTheValuesItIsOffFor() {
        List<TelemetryEvent.Log> lines = new ArrayList<>();
        Diag.setSink(lines::add);
        System.setProperty(SetupOnce.PROPERTY, "off");
        try {
            assertNull(SetupOnce.owed(() -> { }, SetupOnceTest.class, SetupOnceTest.class));
        } finally {
            System.clearProperty(SetupOnce.PROPERTY);
        }
        String hash = SetupOnce.paramsHash(SetupOnce.classesBeside(SetupOnceTest.class, SetupOnceTest.class));
        assertTrue(lines.stream().anyMatch(l -> SetupOnce.reported(l.writerClass(), l.text())
                .filter(hash::equals).isPresent()), lines.toString());
    }

    @Test
    void anOwedSetupReportsItselfDoneOnlyWhenItReturns() {
        assertNull(SetupOnce.owed(null, SetupOnceTest.class, SetupOnceTest.class));
        List<TelemetryEvent.Log> lines = new ArrayList<>();
        Diag.setSink(lines::add);
        Runnable owed = SetupOnce.owed(() -> { }, SetupOnceTest.class, SetupOnceTest.class);
        owed.run();
        assertTrue(lines.stream().anyMatch(l -> SetupOnce.reported(l.writerClass(), l.text()).isPresent()));

        lines.clear();
        Runnable failing = SetupOnce.owed(() -> { throw new IllegalStateException("no"); }, SetupOnceTest.class,
                SetupOnceTest.class);
        try {
            failing.run();
        } catch (IllegalStateException expected) {
            // the setup failed
        }
        assertTrue(lines.stream().noneMatch(l -> SetupOnce.reported(l.writerClass(), l.text()).isPresent()));
    }
}
