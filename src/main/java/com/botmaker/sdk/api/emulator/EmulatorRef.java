package com.botmaker.sdk.api.emulator;

import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLauncher;
import com.botmaker.shared.emulator.PlatformId;


/**
 * A lightweight, <em>unconnected</em> handle to a discovered emulator instance — what
 * {@link Emulators#listAll()} returns. Unlike {@link Emulator} (a live ADB connection), an {@code EmulatorRef}
 * describes an instance whether it's running or not, so every configured instance is selectable without first
 * launching it. Query {@link #running()} for liveness, {@link #launch()}/{@link #stop()} to control it, and
 * {@link #connect()} to open an {@link Emulator} once it's up.
 */
public final class EmulatorRef {

    private final EmulatorInstance instance;

    EmulatorRef(EmulatorInstance instance) {
        this.instance = instance;
    }

    /** The instance name (as shown in the emulator's multi-instance manager). */
    public String name() {
        return instance.name();
    }

    /** Which emulator product this instance belongs to. */
    public PlatformId platform() {
        return instance.platformId();
    }

    /** Where this instance's ADB is — {@code host:port}, or a device serial (whether or not it's up). */
    public String endpoint() {
        return instance.endpoint();
    }

    /** Whether this SDK knows a host command to {@link #launch()} this instance. */
    public boolean canLaunch() {
        return instance.canLaunch();
    }

    /**
     * A quick liveness probe — {@code true} if something is answering. Cheaper than a full ADB handshake; a
     * {@code true} means the instance is (very likely) running, not that ADB is authorized.
     *
     * <p>What "answering" means depends on where the instance is, which is why this delegates rather than
     * opening a socket: a TCP connect settles it for an emulator, but a phone on a cable is only visible as an
     * online serial in the host adb server's list. See {@code AdbEndpoint}.
     */
    public boolean running() {
        return instance.reachable();
    }

    /** Starts this instance via its host console tool. {@code false} if unsupported or the spawn fails. */
    public boolean launch() {
        return EmulatorLauncher.launch(instance);
    }

    /** Stops this instance via its host console tool. {@code false} if unsupported or the spawn fails. */
    public boolean stop() {
        return EmulatorLauncher.stop(instance);
    }

    /**
     * Opens a live {@link Emulator} on this instance. The instance must already be running with ADB enabled.
     *
     * @throws RuntimeException if the connection can't be established
     */
    public Emulator connect() {
        return new Emulator(AdbDevice.connect(instance.adb()), instance);
    }

    @Override
    public String toString() {
        return "EmulatorRef[" + instance.platformId().id() + ":" + instance.name() + " @ " + instance.endpoint() + "]";
    }
}
