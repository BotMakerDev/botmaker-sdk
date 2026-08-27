package com.botmaker.sdk.api.emulator;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.util.Debug;
import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.AdbEndpoint;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.EmulatorLauncher;
import com.botmaker.shared.emulator.Platforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 *
 * <p><b>Curated for the palette</b> (see {@code @Palette}): four of the nine are offered, and this facade is
 * where the sweep's return-value rule finally bites hard enough to be worth stating as a rule.
 *
 * <p><b>A call whose only product is a handle the editor cannot hold is not a menu entry.</b> Neither
 * {@link Emulator} nor {@link EmulatorRef} is a declarable variable type in Studio — the editor's own type
 * list says in as many words that they "come from {@code Emulators.named(…)}" — so
 * {@link #first()}, {@link #named(String)} and {@link #connect(String, int)} are hidden: inserted from a menu
 * they stand as a statement that connects to an emulator and then discards it. {@link #list()} and
 * {@link #listAll()} are hidden for the stronger form of the same fact, a {@code List<…>} being something the
 * editor cannot declare at all; {@code listAll} is in any case documented as the feed for a picker rather than
 * as bot vocabulary.
 *
 * <p>What is offered is what survives that test, and the pleasing part is that the SDK had <em>already</em>
 * shipped a palette-shaped spelling of each: {@link #use()} and {@link #use(String)} are exactly
 * {@code first().use()} and {@code named(name).use()} written as one statement, so they change global state
 * ({@code Source.set}) and the discarded return value costs nothing; {@link #launch(String)} and
 * {@link #stop(String)} act on the world and answer with a {@code boolean}. The hidden five stay public and
 * fully supported for a hand-written bot that holds the handle — this is the {@code Mouse.scroll(int)} shape
 * again, the menu pointing at the spelling the author had already marked as preferred.
 *
 * <p><b>This is the verdict that decides {@code Emulator} and {@code EmulatorRef}.</b> With every
 * handle-producing method hidden, neither type is reachable from a menu, so neither is worth curating for a
 * member menu it can never open. If Studio ever makes {@code Emulator} declarable, {@code first} and
 * {@code named} earn their annotation that day — an addition, which stays free for the SDK's whole life.
 */
@Palette(category = "emulator", categoryLabel = "Emulator", icon = "📱", order = 50)
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
        Debug.log("[Emulator] list: " + running.size() + " running");
        return running;
    }

    /**
     * Every configured emulator instance across every installed product — running <em>or not</em> — as
     * lightweight {@link EmulatorRef}s that carry name/platform/endpoint without opening an ADB connection.
     * This is the "show me every instance I could pick" list (a picker/target chooser); call
     * {@link EmulatorRef#running()} for liveness and {@link EmulatorRef#connect()} to go live. Never throws;
     * empty when nothing is installed.
     */
    public static List<EmulatorRef> listAll() {
        List<EmulatorRef> all = new ArrayList<>();
        for (EmulatorInstance instance : Platforms.discoverAll()) {
            all.add(new EmulatorRef(instance));
        }
        Debug.log("[Emulator] listAll: " + all.size() + " configured");
        return all;
    }

    /**
     * Starts the configured instance named {@code name} via its product's console tool (no connection needed
     * — the instance can be stopped). Returns whether a launch was dispatched; {@code false} if there's no such
     * instance or the product exposes no launch command. Poll {@link EmulatorRef#running()} / retry
     * {@link #named(String)} for readiness afterwards.
     */
    public static boolean launch(String name) {
        boolean dispatched = findInstance(name).map(EmulatorLauncher::launch).orElse(false);
        Debug.log("[Emulator] launch '" + name + "' -> " + (dispatched ? "dispatched" : "no such instance"));
        return dispatched;
    }

    /**
     * Stops the configured instance named {@code name} via its product's console tool. Returns whether a stop
     * was dispatched; {@code false} if there's no such instance or the product exposes no stop command.
     */
    public static boolean stop(String name) {
        boolean dispatched = findInstance(name).map(EmulatorLauncher::stop).orElse(false);
        Debug.log("[Emulator] stop '" + name + "' -> " + (dispatched ? "dispatched" : "no such instance"));
        return dispatched;
    }

    /**
     * The first running emulator (across all products, discovery order).
     *
     * @throws IllegalStateException if no emulator is currently running
     */
    public static Emulator first() {
        Debug.log("[Emulator] first: scanning for a running instance");
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
        Debug.log("[Emulator] named '" + needle + "'");
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
        Debug.log("[Emulator] connect " + host + ":" + port);
        // Recover the real product identity when this endpoint matches a discovered instance; otherwise stamp
        // an UNKNOWN descriptor (no launch/stop commands) rather than mislabeling it a specific product.
        EmulatorInstance instance = findInstanceByEndpoint(host, port)
                .orElseGet(() -> new EmulatorInstance(PlatformId.UNKNOWN, host + ":" + port, host, port));
        return new Emulator(AdbDevice.connect(host, port), instance);
    }

    private static Optional<Emulator> tryConnect(EmulatorInstance instance) {
        try {
            AdbDevice device = AdbDevice.connect(instance.adb());
            return Optional.of(new Emulator(device, instance));
        } catch (Exception e) {
            // instance configured but not running / ADB port closed — skip it
            return Optional.empty();
        }
    }

    /** The first discovered instance whose name matches {@code name} (trimmed), or empty. */
    private static Optional<EmulatorInstance> findInstance(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String needle = name.trim();
        for (EmulatorInstance instance : Platforms.discoverAll()) {
            if (needle.equals(instance.name())) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    /** The first discovered instance whose ADB endpoint matches {@code host:port}, or empty. */
    private static Optional<EmulatorInstance> findInstanceByEndpoint(String host, int port) {
        String wanted = new AdbEndpoint.Tcp(host, port).label();
        for (EmulatorInstance instance : Platforms.discoverAll()) {
            if (wanted.equals(instance.endpoint())) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }
}
