package com.botmaker.sdk.internal.emulator;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

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
public final class EmulatorSource implements CaptureSource {

    /** How old a scan of the emulators may be to answer whether this one is up. */
    private static final Duration RECENT = Duration.ofSeconds(2);

    /** Android's {@code KEYCODE_ENTER}. */
    public static final int KEYCODE_ENTER = 66;

    private final String instanceName;

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
        return emu == null ? null : emu.capture();
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
