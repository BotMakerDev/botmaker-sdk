package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.shared.Diag;
import com.botmaker.shared.vm.VmAutoStop;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;
import com.botmaker.shared.vm.VmSetup;
import javafx.application.Platform;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Applies each game VM's {@link VmAutoStop} while Studio has a project open, on Windows: once a minute it makes
 * sure a VM that shuts down with Studio has its watcher, and shuts down a VM nothing has used for as long as its
 * settings allow. A VM set up but not running is left alone, and so is one still installing Windows.
 *
 * <p>Bound to the open project ({@code SdkPlugin.projectOpened}/{@code projectClosing}), as the plugin's classes
 * are: the watcher it starts is a process of its own, which outlives this class's loader and Studio.
 */
public final class VmKeeper {

    private static final long EVERY_SECONDS = 60;

    private static ScheduledExecutorService running;
    private static volatile StudioServices services;
    /** When each VM was first seen unused, by name. */
    private static final Map<String, Long> unusedSince = new ConcurrentHashMap<>();

    private VmKeeper() {}

    /** Starts keeping, for the project {@code project} opened. */
    public static synchronized void start(StudioServices project) {
        stop();
        services = project;
        running = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vm-keeper");
            t.setDaemon(true);
            return t;
        });
        running.scheduleWithFixedDelay(VmKeeper::check, 5, EVERY_SECONDS, TimeUnit.SECONDS);
    }

    /** Stops keeping; the watchers already started go on watching Studio. */
    public static synchronized void stop() {
        if (running != null) running.shutdownNow();
        running = null;
        services = null;
    }

    /** Applies settings just saved, rather than at the next minute. */
    static synchronized void checkNow() {
        if (running != null) running.execute(VmKeeper::check);
    }

    private static void check() {
        try {
            for (VmRecord vm : VmInventory.list()) {
                if (vm.stage() == VmRecord.Stage.READY) keep(vm);
            }
        } catch (RuntimeException e) {
            Diag.error("[VM] keeping the game VMs: " + e, e);
        }
    }

    private static void keep(VmRecord vm) {
        VmAutoStop settings = VmAutoStop.load(vm);
        if (!VmInventory.running(vm)) {
            unusedSince.remove(vm.name());
            VmAutoStop.stopWatching(vm);
            return;
        }
        if (settings.withStudio()) {
            try {
                VmAutoStop.watchStudio(vm, ProcessHandle.current().pid());
            } catch (IOException e) {
                Diag.error("[VM] " + vm.name() + ": couldn't watch for Studio closing: " + e.getMessage());
            }
        } else {
            VmAutoStop.stopWatching(vm);
        }
        if (settings.idleMinutes() == 0 || !VmAutoStop.unwatched(vm)) {
            unusedSince.remove(vm.name());
            return;
        }
        long now = System.nanoTime();
        long since = unusedSince.computeIfAbsent(vm.name(), n -> now);
        if (now - since < TimeUnit.MINUTES.toNanos(settings.idleMinutes())) return;
        unusedSince.remove(vm.name());
        say("Shutting the game VM " + vm.name() + " down: nothing has used it for " + settings.idleMinutes()
                + " minutes.");
        // Up to three minutes while Windows shuts down: on a thread of its own, so the other VMs and a setting
        // just saved don't wait for it.
        Thread.ofPlatform().daemon().name("vm-idle-shut-down-" + vm.name()).start(() -> {
            try {
                VmSetup.shutDown(vm);
                say("The game VM " + vm.name() + " is shut down; the next run starts it again.");
            } catch (IOException e) {
                say("The game VM " + vm.name() + " didn't shut down: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static void say(String sentence) {
        Diag.log("[VM] " + sentence);
        StudioServices to = services;
        if (to != null) Platform.runLater(() -> to.status(sentence));
    }
}
