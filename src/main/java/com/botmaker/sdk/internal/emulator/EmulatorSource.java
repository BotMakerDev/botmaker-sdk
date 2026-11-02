package com.botmaker.sdk.internal.emulator;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.bot.Watchdog;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.QuietRun;
import com.botmaker.shared.emulator.tree.TreeReader;
import com.botmaker.shared.emulator.tree.TreeReaders;
import com.botmaker.shared.emulator.tree.UiTree;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A {@link CaptureSource} that targets an emulator <em>by instance name</em>, connecting lazily on first use —
 * and dispatching a launch if the instance isn't running yet. This is what makes every configured emulator
 * selectable as a capture target without pre-launching it: a project whose {@code capture.source} is
 * {@code emulator:<name>} resolves to one of these.
 *
 * <p>Resolution is non-blocking and idempotent. While the named instance is down, the first
 * {@link #capture()} dispatches a launch (once) and returns {@code null}; the matcher treats that as "not
 * visible yet" and retries, and a later capture connects once the instance has booted. A dropped connection
 * re-resolves on the next call.
 */
public final class EmulatorSource implements CaptureSource, ReadableSource {

    /** How old a scan of the emulators may be to answer whether this one is up. */
    private static final Duration RECENT = Duration.ofSeconds(2);

    /** Android's {@code KEYCODE_ENTER}. */
    public static final int KEYCODE_ENTER = 66;

    private final String instanceName;

    /**
     * The tree reader of each instance, by its name, shared by every source naming it. Android grants one
     * UiAutomation at a time, and a reader's process holds it until the reader closes: Studio builds a new source
     * from {@code Sdk.java} for each tool call, so a reader per source left the last one holding it and the next read
     * failed until a collection closed it (measured on Waydroid, 2026-11-01).
     */
    private static final Map<String, TreeLease> READERS = new ConcurrentHashMap<>();

    private volatile Emulator connected;
    private volatile boolean launchDispatched;

    public EmulatorSource(String instanceName) {
        this.instanceName = instanceName == null ? "" : instanceName.trim();
    }

    /** The emulator instance name this source targets. */
    public String instanceName() {
        return instanceName;
    }

    @Override
    @Hidden("the CaptureSource half of this type; hidden there and hidden here, since an override "
            + "carries none of the interface's annotations")
    public BufferedImage capture() {
        Emulator emu = resolve();
        if (emu == null) return null;
        // A run answers Android's own dialogs over the game; Studio's look tools never do.
        if (Watchdog.isEnabled()) SystemDialogs.watch(emu, () -> lease().read(emu, false));
        return emu.capture();
    }

    /** The emulator a capture has already connected to, or null: never connects or launches one. */
    Emulator connected() {
        Emulator cached = connected;
        return cached != null && cached.isPresent() ? cached : null;
    }

    /** An emulator's captured pixels <em>are</em> its coordinate space, so the origin is always {@code (0,0)}. */
    @Override
    public Point origin() {
        return new Point(0, 0);
    }

    @Override
    public boolean isPresent() {
        Emulator emu = connected;
        return emu != null ? emu.isPresent() : findRef(RECENT).map(EmulatorRef::running).orElse(false);
    }

    @Override
    public void click(Point p) {
        Emulator emu = resolve();
        if (emu != null) {
            emu.click(p);
        }
    }

    @Override
    public void drag(Point from, Point to, long durationMs) {
        Emulator emu = resolve();
        if (emu != null) emu.drag(from, to, durationMs);
    }

    @Override
    public void scroll(Point at, int notches) {
        Emulator emu = resolve();
        if (emu != null) emu.scroll(at, notches);
    }

    /** Types {@code text} into the emulator's focused field. */
    public void text(String text) {
        Emulator emu = resolve();
        if (emu != null) emu.text(text);
    }

    /** Sends the Android key event {@code keyCode}: {@link #KEYCODE_ENTER} once a field's text is typed. */
    public void key(int keyCode) {
        Emulator emu = resolve();
        if (emu != null) emu.key(keyCode);
    }

    /**
     * Keeps other apps off {@code packageName} for the rest of the run (the user's call, 2026-11-01): every
     * permission it asks for granted (2026-11-02), and Do Not Disturb on until this JVM ends, then put back as it
     * was. Once per instance whatever the restarts; the permissions again each time, which is cheap.
     */
    public void quiet(String packageName) {
        Emulator emu = resolve();
        if (emu == null) return;
        QuietRun.grantRequested(emu.device(), packageName);
        // The device asked outside the map's lock: a slow ADB must not hold up another instance's entry.
        if (QUIET.containsKey(instanceName)) return;
        // A run that joins a game already up never went through the launcher, which shuts the popups off.
        QuietRun.calm(emu.device());
        QuietRun.Was was = QuietRun.start(emu.device());
        if (QUIET.putIfAbsent(instanceName, was) != null) return;
        Debug.log("Do Not Disturb on " + instanceName + " while the bot runs");
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            if (emu.isPresent()) QuietRun.end(emu.device(), was);
        }));
    }

    /** Do Not Disturb as each instance had it before this run quieted it. */
    private static final Map<String, QuietRun.Was> QUIET = new ConcurrentHashMap<>();

    /**
     * The focused app's tree, read by the instance's one {@link TreeReader}, which a new connection closes; the
     * reader's process on the device ends with this JVM. The focused package is asked while the tree is read, so the
     * read takes no longer than the slower of the two.
     */
    @Override
    @Hidden("what Screens finds a bot's Elements in; a bot names an element, never a tree")
    public Optional<UiTree> tree() {
        Emulator emu = resolve();
        if (emu == null) return Optional.empty();
        return lease().read(emu, true);
    }

    private TreeLease lease() {
        return READERS.computeIfAbsent(instanceName, name -> new TreeLease());
    }

    /**
     * One instance's tree reader and the connection it reads through; reads one at a time. The reader stays while its
     * connection is up, whichever source asks: each source connects on its own, and a reader opened per connection
     * would start the device's reader again on every tool call. Unread for {@link #IDLE}, it is closed: its process
     * holds the device's one UiAutomation, which a bot's run, another tool or {@code uiautomator dump} then could not
     * get while Studio stayed open (2026-11-01).
     */
    private static final class TreeLease {
        private static final Duration IDLE = Duration.ofSeconds(30);
        private static final ScheduledExecutorService CLOSER = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("tree-reader-idle").factory());

        private TreeReader reader;
        private Emulator of;
        private long readAt;

        /** {@code app}: only the focused app's windows; else the whole tree, Android's own dialogs and all. */
        synchronized Optional<UiTree> read(Emulator emu, boolean app) {
            if (reader == null || !of.isPresent()) {
                if (reader != null) reader.close();
                reader = TreeReaders.open(emu.device());
                of = emu;
            }
            long now = System.nanoTime();
            readAt = now;
            CLOSER.schedule(() -> closeIfIdleSince(now), IDLE.toMillis(), TimeUnit.MILLISECONDS);
            if (!app) return reader.read();
            Emulator through = of;
            try (ExecutorService both = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<Optional<String>> focused = both.submit(() -> through.device().focusedPackage());
                Optional<UiTree> tree = reader.read();
                Optional<String> focusedApp = focused.get();
                return tree.map(read -> focusedApp.map(read::app).orElse(read));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (ExecutionException e) {
                return Optional.empty();
            }
        }

        /** Closes the reader when no read came after the one at {@code since}; the next read opens one again. */
        private synchronized void closeIfIdleSince(long since) {
            if (readAt != since || reader == null) return;
            reader.close();
            reader = null;
        }
    }

    /**
     * The live emulator, or {@code null} while it isn't reachable yet. Reuses a healthy cached connection;
     * otherwise looks the instance up, connects if it's running, or dispatches a one-time launch if it isn't.
     */
    private Emulator resolve() {
        Emulator cached = connected;
        if (cached != null && cached.isPresent()) {
            return cached;
        }
        connected = null;

        Optional<EmulatorRef> ref = findRef(RECENT);
        if (ref.isEmpty()) {
            return null;
        }
        EmulatorRef r = ref.get();
        if (r.running()) {
            try {
                Emulator emu = r.connect();
                connected = emu;
                return emu;
            } catch (Exception e) {
                return null;
            }
        }
        // Not running: dispatch a launch once, then let subsequent captures pick it up as it boots. Asked afresh
        // first: a scan a second old can miss an instance that has just come up, and a second launch of it is not
        // harmless on every product.
        if (!launchDispatched) dispatchLaunch();
        return null;
    }

    /** Launches the instance when a fresh scan still finds it down; once, whichever capture thread gets here first. */
    private synchronized void dispatchLaunch() {
        if (launchDispatched) return;
        Optional<EmulatorRef> now = findRef(Duration.ZERO);
        if (now.isPresent() && !now.get().running()) {
            launchDispatched = true;
            now.get().launch();
        }
    }

    /**
     * The discovered instance this source names, or empty: by its reference first — a phone's serial, which a
     * picker writes since 2026-10-19 — then by name, which a phone that was saved before still matches. From a scan
     * at most {@code maxAge} old ({@link Platforms#recent}): a capture loop waiting on a booting emulator asks several
     * times a second, and each scan runs every product's tools.
     */
    private Optional<EmulatorRef> findRef(Duration maxAge) {
        if (instanceName.isEmpty()) {
            return Optional.empty();
        }
        List<EmulatorInstance> all = Platforms.recent(maxAge);
        return all.stream().filter(instance -> instanceName.equals(instance.reference())).findFirst()
                .or(() -> all.stream().filter(instance -> instanceName.equals(instance.name())).findFirst())
                .map(EmulatorRef::new);
    }

    @Override
    public String toString() {
        return "EmulatorSource[" + instanceName + "]";
    }
}
