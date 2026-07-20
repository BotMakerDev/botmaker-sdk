package com.botmaker.sdk.api.emulator;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLauncher;
import com.botmaker.shared.emulator.PlatformId;

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

    // --- CaptureSource: makes the emulator a first-class vision target ---

    @Override
    public java.awt.image.BufferedImage capture() {
        return device.screencap();
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
        device.tap((int) p.x, (int) p.y);
    }

    // --- Native emulator input verbs ---

    /** Taps ({@code x},{@code y}) in emulator pixels. */
    public void tap(int x, int y) {
        device.tap(x, y);
    }

    /** Swipes/drags from ({@code x1},{@code y1}) to ({@code x2},{@code y2}) over {@code durationMs}. */
    public void swipe(int x1, int y1, int x2, int y2, long durationMs) {
        device.swipe(x1, y1, x2, y2, durationMs);
    }

    /** Presses the Back button. */
    public void back() {
        device.key(KEYCODE_BACK);
    }

    /** Presses the Home button. */
    public void home() {
        device.key(KEYCODE_HOME);
    }

    /** Types {@code text} into the focused field. */
    public void text(String text) {
        device.text(text);
    }

    /** Sends an Android key event by keycode. */
    public void key(int keyCode) {
        device.key(keyCode);
    }

    /** Launches an installed app by package name (its launcher activity). */
    public void startApp(String packageName) {
        device.startApp(packageName);
    }

    /** Force-stops an app by package name ({@code am force-stop}) — the "close it" half of a restart. */
    public void stopApp(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return;
        }
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

    /** The package name of the app currently in the foreground, or {@code ""} if none/unknown. */
    public String currentApp() {
        return device.currentApp();
    }

    /** Reboots the Android guest ({@code adb reboot}); the ADB connection drops until it comes back up. */
    public void reboot() {
        device.shell("reboot");
    }

    /**
     * Stops the whole emulator from the host — the counterpart to {@link Emulators#launch(String)}. Uses the
     * product's console tool when discovery found one (e.g. {@code ldconsole quit}); otherwise best-effort
     * powers off the Android guest. Returns whether a stop was dispatched. After this the connection is dead.
     */
    public boolean stop() {
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
        Source.set(this);
    }

    /** The instance name (as shown in the emulator's multi-instance manager). */
    public String name() {
        return instance.name();
    }

    /** Which emulator product this instance belongs to. */
    public PlatformId platform() {
        return instance.platformId();
    }

    /** Closes the underlying ADB connection. After this the emulator can no longer be captured or tapped. */
    public void disconnect() {
        device.close();
    }

    @Override
    public String toString() {
        return "Emulator[" + instance.platformId().id() + ":" + instance.name() + " @ " + device.endpoint() + "]";
    }
}
