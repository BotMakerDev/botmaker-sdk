package com.botmaker.sdk.api.emulator;

import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds and connects to Android emulators — the entry point to the emulator stack. Discovery reads the
 * installed products' local config (BlueStacks + LDPlayer today; MEmu/MuMu/Gameloop scaffolded) to learn each
 * instance's ADB port, then connects over dadb (pure-JVM ADB — no {@code adb.exe} to install).
 *
 * <p>Typical use:
 * <pre>{@code
 * Emulator emu = Emulators.first();   // the first running emulator
 * emu.use();                          // point the whole bot at it (Source.set)
 * }</pre>
 */
public final class Emulators {

    private Emulators() {}

    /**
     * Every discovered emulator instance that is currently <em>running</em> (its ADB port answers), each as a
     * connected {@link Emulator}. Instances that are configured but stopped are skipped. Never throws; returns
     * an empty list when nothing is installed or running.
     */
    public static List<Emulator> list() {
        List<Emulator> running = new ArrayList<>();
        for (EmulatorInstance instance : Platforms.discoverAll()) {
            tryConnect(instance).ifPresent(running::add);
        }
        return running;
    }

    /**
     * The first running emulator (across all products, discovery order).
     *
     * @throws IllegalStateException if no emulator is currently running
     */
    public static Emulator first() {
        for (EmulatorInstance instance : Platforms.discoverAll()) {
            var emu = tryConnect(instance);
            if (emu.isPresent()) {
                return emu.get();
            }
        }
        throw new IllegalStateException("No running Android emulator found. Is BlueStacks/LDPlayer started "
                + "with ADB enabled in its settings?");
    }

    /**
     * The running emulator whose instance name equals {@code name} (as shown in the emulator's multi-instance
     * manager).
     *
     * @throws IllegalArgumentException if {@code name} is null/blank
     * @throws IllegalStateException    if no running instance with that name is found
     */
    public static Emulator named(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        String needle = name.trim();
        for (EmulatorInstance instance : Platforms.discoverAll()) {
            if (needle.equals(instance.name())) {
                var emu = tryConnect(instance);
                if (emu.isPresent()) {
                    return emu.get();
                }
            }
        }
        throw new IllegalStateException("No running emulator named '" + needle
                + "'. Is that instance started with ADB enabled?");
    }

    /**
     * Connects to the first running emulator and points the whole bot at it ({@code Source.set}) in one call —
     * the shorthand for {@code Emulators.first().use()}. After this every no-source vision/click/OCR call runs
     * on the emulator. Returns the connected emulator so bot code can still hold a handle to it.
     *
     * @throws IllegalStateException if no emulator is currently running
     */
    public static Emulator use() {
        Emulator emu = first();
        emu.use();
        return emu;
    }

    /**
     * Connects to the running emulator named {@code name} and points the whole bot at it ({@code Source.set}) —
     * the shorthand for {@code Emulators.named(name).use()}. Returns the connected emulator.
     *
     * @throws IllegalArgumentException if {@code name} is null/blank
     * @throws IllegalStateException    if no running instance with that name is found
     */
    public static Emulator use(String name) {
        Emulator emu = named(name);
        emu.use();
        return emu;
    }

    /**
     * Connects directly to an emulator's ADB endpoint, bypassing discovery — for a custom port or an emulator
     * this SDK doesn't discover yet.
     *
     * @param host the ADB host (usually {@code "127.0.0.1"})
     * @param port the ADB port
     * @throws RuntimeException if the connection can't be established
     */
    public static Emulator connect(String host, int port) {
        return new Emulator(AdbDevice.connect(host, port), host + ":" + port, "adb");
    }

    private static java.util.Optional<Emulator> tryConnect(EmulatorInstance instance) {
        try {
            AdbDevice device = AdbDevice.connect(instance.host(), instance.adbPort());
            return java.util.Optional.of(new Emulator(device, instance.name(), instance.platformId()));
        } catch (Exception e) {
            // instance configured but not running / ADB port closed — skip it
            return java.util.Optional.empty();
        }
    }
}
