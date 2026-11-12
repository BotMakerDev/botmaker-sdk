package com.botmaker.sdk.internal.emulator;

import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.internal.bot.Watchdog;
import com.botmaker.sdk.internal.observe.Bots;
import com.botmaker.sdk.internal.observe.Surface;
import com.botmaker.sdk.internal.observe.SwipeEvent;
import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLauncher;
import com.botmaker.shared.emulator.PlatformId;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A connected Android emulator, exposed as a {@link CaptureSource} so the whole vision stack
 * ({@code ImageFinder} / {@code Pixel} / {@code Text} OCR) and {@code ImageClicker} work on it
 * <em>unchanged</em>: {@link #capture()} is an ADB {@code screencap} and {@link #origin()} is {@code (0,0)},
 * so a match's coordinates are already emulator pixels, and {@link #click(Point)} taps them via ADB.
 *
 * <p>Point the whole bot at an emulator once and every no-source call follows it:
 * <pre>{@code
 * Emulator emu = Emulators.first();      // or Emulators.named("Rvc64") / connect(host, port)
 * Source.set(emu);                        // now ImageFinder.find(...), Text.read(), etc. use the emulator
 * emu.startApp("com.some.game");
 * ImageClicker.click(playButton);         // located on the emulator screen, tapped via `adb input tap`
 * }</pre>
 *
 * <p>Also exposes the native input verbs a mobile bot needs directly ({@link #tap}, {@link #swipe},
 * {@link #back}, {@link #home}, {@link #text}, {@link #key}, {@link #startApp}). Obtain instances from
 * {@link Emulators}. Not thread-safe for concurrent capture on one connection.
 */
public final class Emulator implements CaptureSource {

    // Common Android key codes for the two verbs bots reach for most.
    private static final int KEYCODE_HOME = 3;
    private static final int KEYCODE_BACK = 4;

    private final AdbDevice device;
    private final EmulatorInstance instance;

    Emulator(AdbDevice device, EmulatorInstance instance) {
        this.device = device;
        this.instance = instance;
    }

    /** The connection, for {@link EmulatorSize}'s {@code wm} commands. */
    AdbDevice device() {
        return device;
    }

    // --- CaptureSource: makes the emulator a first-class vision target ---

    @Override
    @Hidden("the CaptureSource half of this type; hidden there and hidden here, since an override "
            + "carries none of the interface's annotations")
    public BufferedImage capture() {
        BufferedImage frame = device.screencap();
        boolean black = black(frame);
        // MEmu's alone: a fade through black in any game would otherwise wait each time.
        if (black && !lastBlack && instance.platformId() == PlatformId.MEMU) {
            // MEmu's first screencap after its window was minimised or restored was black, the next fine (2026-11-11):
            // a frame that turns black is taken once more before a bot reads it.
            try {
                Thread.sleep(BLACK_AGAIN_MS);
                frame = device.screencap();
                black = black(frame);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastBlack = black;
        return Watchdog.saw(frame);
    }

    /** How long before a frame that turned black is taken again. */
    static final long BLACK_AGAIN_MS = 300;

    /** Whether the last frame captured was black: one that stays so (a loading screen) is not taken twice each time. */
    private volatile boolean lastBlack;

    /** Whether a sparse grid of {@code frame}'s pixels, about 17 a side, is all black; false for no frame. */
    static boolean black(BufferedImage frame) {
        if (frame == null) return false;
        int width = frame.getWidth();
        int height = frame.getHeight();
        int step = Math.max(1, Math.min(width, height) / 17);
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                if ((frame.getRGB(x, y) & 0x00FFFFFF) != 0) return false;
            }
        }
        return true;
    }

    /** Always {@code (0,0)}: an emulator's captured pixels <em>are</em> its coordinate space. */
    @Override
    public Point origin() {
        return new Point(0, 0);
    }

    @Override
    public boolean isPresent() {
        return device.isConnected();
    }

    /** Taps {@code p} via {@code adb input tap} — the seam that lets {@code ImageClicker} drive the emulator. */
    @Override
    public void click(Point p) {
        if (p == null) {
            return;
        }
        Debug.log("click " + p + " on " + name());
        Watchdog.acted();
        device.tap(p.x(), p.y());
    }

    // --- Native emulator input verbs ---

    /** Taps ({@code x},{@code y}) in emulator pixels. */
    public void tap(int x, int y) {
        Debug.log("tap " + x + "," + y + " on " + name());
        Watchdog.acted();
        device.tap(x, y);
    }

    /** Swipes/drags from ({@code x1},{@code y1}) to ({@code x2},{@code y2}) over {@code durationMs}. */
    public void swipe(int x1, int y1, int x2, int y2, long durationMs) {
        Debug.log("swipe " + x1 + "," + y1 + " -> " + x2 + "," + y2 + " over " + durationMs + "ms");
        Watchdog.acted();
        device.swipe(x1, y1, x2, y2, durationMs);
        // Emulator pixels are already what the pilot streams for this route (origin (0,0)), so the same
        // coordinates ADB was handed are the ones an overlay draws — no mapping, as with click().
        if (Bots.hasObservers()) {
            Bots.fireSwipe(new SwipeEvent(
                    Surface.of(this), new Point(x1, y1), new Point(x2, y2), durationMs));
        }
    }

    /** How far one wheel notch swipes, in emulator pixels: a touch screen has no wheel. */
    private static final int NOTCH_PX = 120;

    /** How long a wheel's swipe takes: slow enough that a list scrolls rather than flings. */
    private static final long NOTCH_SWIPE_MS = 300;

    @Override
    public void drag(Point from, Point to, long durationMs) {
        if (from == null || to == null) return;
        swipe(from.x(), from.y(), to.x(), to.y(), durationMs);
    }

    /** A swipe from {@code at}: up the wheel (positive) moves the content down, as the finger does. */
    @Override
    public void scroll(Point at, int notches) {
        if (at == null || notches == 0) return;
        // Kept on the screen's top edge: a swipe ending above it is dropped or flings. The bottom edge is the
        // device's, which this does not ask for.
        swipe(at.x(), at.y(), at.x(), Math.max(0, at.y() + notches * NOTCH_PX), NOTCH_SWIPE_MS);
    }

    /** Presses the Back button. */
    public void back() {
        Debug.log("back");
        Watchdog.acted();
        device.key(KEYCODE_BACK);
    }

    /** Presses the Home button. */
    public void home() {
        Debug.log("home");
        device.key(KEYCODE_HOME);
    }

    /** Types {@code text} into the focused field. */
    public void text(String text) {
        Debug.log("text \"" + text + "\"");
        Watchdog.acted();
        device.text(text);
    }

    /** Sends an Android key event by keycode. */
    public void key(int keyCode) {
        Debug.log("key " + keyCode);
        Watchdog.acted();
        device.key(keyCode);
    }

    /** Launches an installed app by package name (its launcher activity). */
    public void startApp(String packageName) {
        Debug.log("startApp " + packageName);
        device.startApp(packageName);
    }

    /** Force-stops an app by package name ({@code am force-stop}) — the "close it" half of a restart. */
    public void stopApp(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return;
        }
        Debug.log("stopApp " + packageName);
        device.shell("am force-stop " + packageName.trim());
    }

    // --- App / lifecycle queries ---

    /** The third-party (user-installed) packages on the emulator — the games/apps a bot targets. */
    public List<String> installedApps() {
        return device.installedApps();
    }

    /** Whether {@code packageName} is installed on the emulator. */
    public boolean isInstalled(String packageName) {
        return device.isInstalled(packageName);
    }

    /**
     * Whether {@code packageName} has an activity in Android's task stack, or the stack cannot be read (true: it cannot
     * tell): false once it was stopped. A dialog of another app over it (a permission, a sign-in) keeps it there. Its
     * process alone says nothing: Sword x Staff kept two after {@code am force-stop} (2026-11-02).
     */
    public boolean runs(String packageName) {
        if (packageName == null || packageName.isBlank()) return true;
        String stack;
        try {
            stack = device.shell("dumpsys activity activities");
        } catch (RuntimeException unread) {
            return true;
        }
        return stack == null || stack.isBlank() || hasActivity(stack, packageName.trim());
    }

    /**
     * Whether {@code dumpsys}'s task stack holds an activity of {@code packageName}: a {@code Hist #} line naming
     * {@code u0 <package>/<activity>}. The rest of the dump is not the stack: Android 14's rotation history names an
     * activity long after its task is gone (LDPlayer, 2026-11-12).
     */
    static boolean hasActivity(String dumpsys, String packageName) {
        String activity = " " + packageName + "/";
        return dumpsys.lines().anyMatch(line -> line.contains("Hist ") && line.contains(activity));
    }

    /** The package name of the app currently in the foreground, or {@code ""} if none/unknown. */
    public String currentApp() {
        return device.currentApp();
    }

    /**
     * The package of the window in focus — a dialog's over the app while one is up, where {@link #currentApp()} reads
     * the activity behind it — or {@code ""} when unknown.
     */
    public String focusedPackage() {
        return device.focusedPackage().orElse("");
    }

    /** How Android's own dialog in focus says itself ("isn't responding"), or {@code ""} when none is up. */
    public String systemDialog() {
        return device.systemDialog().map(shown -> shown.dialog().said()).orElse("");
    }

    /** Reboots the Android guest ({@code adb reboot}); the ADB connection drops until it comes back up. */
    public void reboot() {
        Debug.log("reboot " + name());
        device.shell("reboot");
    }

    /**
     * Stops the whole emulator from the host — the counterpart to {@link Emulators#launch(String)}. Uses the
     * product's console tool when discovery found one (e.g. {@code ldconsole quit}); otherwise best-effort
     * powers off the Android guest. Returns whether a stop was dispatched. After this the connection is dead.
     */
    public boolean stop() {
        Debug.log("stop " + name());
        if (instance.canStop() && EmulatorLauncher.stop(instance)) {
            return true;
        }
        try {
            device.shell("reboot -p"); // power off the guest when there's no host stop command
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Sets this emulator as the bot's ambient {@link Source} — shorthand for {@code Source.set(this)}. */
    public void use() {
        Debug.log("use " + this);
        Source.set(this);
    }

    /** The instance name (as shown in the emulator's multi-instance manager). */
    public String name() {
        return instance.name();
    }

    /** Whether this is the instance a launch target names by {@link EmulatorInstance#reference()}. */
    boolean is(String reference) {
        return instance.reference().equals(reference);
    }

    /** Closes the underlying ADB connection. After this the emulator can no longer be captured or tapped. */
    public void disconnect() {
        Debug.log("disconnect " + name());
        device.close();
    }

    @Override
    public String toString() {
        return "Emulator[" + instance.platformId().id() + ":" + instance.name() + " @ " + device.endpoint() + "]";
    }
}
