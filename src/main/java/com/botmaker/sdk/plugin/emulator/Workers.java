package com.botmaker.sdk.plugin.emulator;

/**
 * The background threads the emulator dialogs probe, poll and download on.
 *
 * <p>Daemon threads, so none of them keeps Studio running after its window closes: a start poll can wait
 * minutes for an emulator to boot, and a closed dialog is no reason to hold the JVM open for it.
 */
final class Workers {

    private Workers() {}

    /** Starts {@code work} on a named daemon thread and returns the thread. */
    static Thread start(String name, Runnable work) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
