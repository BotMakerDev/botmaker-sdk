package com.botmaker.sdk.internal.trace;

import com.botmaker.plugin.api.TraceLine;
import com.botmaker.plugin.api.run.RunChannel;

import java.util.ArrayList;
import java.util.List;

/** A run channel that keeps every line it is handed, with its own debug switch; tests start a run on it. */
public final class RecordingChannel implements RunChannel {

    public final List<TraceLine> lines = new ArrayList<>();
    private volatile boolean debug;

    public RecordingChannel(boolean debug) {
        this.debug = debug;
    }

    /** Starts the SDK's run on a fresh channel and answers it. */
    public static RecordingChannel start(boolean debug) {
        RecordingChannel channel = new RecordingChannel(debug);
        SdkRun.started(channel);
        return channel;
    }

    /** Puts the run back on the console. */
    public static void stop() {
        SdkRun.ended();
    }

    @Override
    public void line(TraceLine line, Throwable cause) {
        lines.add(line);
    }

    @Override
    public boolean debug() {
        return debug;
    }

    @Override
    public void debug(boolean on) {
        debug = on;
    }
}
