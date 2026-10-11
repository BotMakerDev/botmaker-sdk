package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.Runs;
import com.botmaker.plugin.api.TraceLine;
import com.botmaker.sdk.internal.bot.SetupOnce;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A run's "setup done" line becomes this computer's run properties; any other line changes nothing. */
class SetupRecorderTest {

    private final Map<String, String> properties = new HashMap<>();

    private final Runs runs = new Runs() {
        @Override public void start() { }
        @Override public void stop() { }
        @Override public boolean isRunning() { return false; }
        @Override public OptionalLong pid() { return OptionalLong.empty(); }
        @Override public AutoCloseable onStateChanged(Consumer<Boolean> listener) { return () -> { }; }
        @Override public AutoCloseable onTelemetry(Consumer<byte[]> listener) { return () -> { }; }
        @Override public String property(String name) { return properties.get(name); }
        @Override public void setProperty(String name, String value) { properties.put(name, value); }
    };

    private static TraceLine line(String writer, String text) {
        return new TraceLine(Instant.EPOCH, TraceLine.Level.INFO, "Setup", text, 1, writer, "done", "",
                OptionalInt.empty(), Optional.empty());
    }

    @Test
    void aSetupDoneLineSwitchesTheSetupOffAndKeepsItsHash() {
        SetupRecorder.record(runs, line(SetupOnce.class.getName(), "The setup is done. [setup 0123456789abcdef]"));
        assertEquals(Map.of(SetupOnce.PROPERTY, "off", SetupOnce.PARAMS_PROPERTY, "0123456789abcdef"), properties);
    }

    @Test
    void anyOtherLineIsIgnored() {
        SetupRecorder.record(runs, line("com.example.Gamebot", "The setup is done. [setup 0123456789abcdef]"));
        SetupRecorder.record(runs, line(SetupOnce.class.getName(), "setup"));
        SetupRecorder.record(runs, null);
        assertTrue(properties.isEmpty());
    }
}
