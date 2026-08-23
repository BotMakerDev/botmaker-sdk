package com.botmaker.sdk.api.emulator;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * A {@link CaptureSource} that targets an emulator <em>by instance name</em>, connecting lazily on first use —
 * and dispatching a launch if the instance isn't running yet. This is what makes every configured emulator
 * selectable as a capture target without pre-launching it: a project whose {@code capture.source} is
 * {@code emulator:<name>} resolves to one of these (see {@code ProjectDefaults}).
 *
 * <p>Resolution is non-blocking and idempotent. While the named instance is down, the first
 * {@link #capture()} dispatches a launch (once) and returns {@code null}; the matcher treats that as "not
 * visible yet" and retries, and a later capture connects once the instance has booted. A dropped connection
 * re-resolves on the next call.
 */
public final class EmulatorSource implements CaptureSource {

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
    public BufferedImage capture() {
        Emulator emu = resolve();
        return emu == null ? null : emu.capture();
    }

    /** An emulator's captured pixels <em>are</em> its coordinate space, so the origin is always {@code (0,0)}. */
    @Override
    public Point origin() {
        return new Point(0, 0);
    }

    @Override
    public boolean isPresent() {
        Emulator emu = connected;
        return emu != null ? emu.isPresent() : findRef().map(EmulatorRef::running).orElse(false);
    }

    @Override
    public void click(Point p) {
        Emulator emu = resolve();
        if (emu != null) {
            emu.click(p);
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

        Optional<EmulatorRef> ref = findRef();
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
        // Not running: dispatch a launch once, then let subsequent captures pick it up as it boots.
        if (!launchDispatched) {
            launchDispatched = true;
            r.launch();
        }
        return null;
    }

    /** The discovered instance whose name matches, or empty. */
    private Optional<EmulatorRef> findRef() {
        if (instanceName.isEmpty()) {
            return Optional.empty();
        }
        return Emulators.listAll().stream()
                .filter(ref -> instanceName.equals(ref.name()))
                .findFirst();
    }

    @Override
    public String toString() {
        return "EmulatorSource[" + instanceName + "]";
    }
}
