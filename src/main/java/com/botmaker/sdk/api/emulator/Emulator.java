package com.botmaker.sdk.api.emulator;

import com.botmaker.sdk.api.Point;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.emulator.AdbDevice;

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
    private final String name;
    private final String platformId;

    Emulator(AdbDevice device, String name, String platformId) {
        this.device = device;
        this.name = name;
        this.platformId = platformId;
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

    /** Sets this emulator as the bot's ambient {@link Source} — shorthand for {@code Source.set(this)}. */
    public void use() {
        Source.set(this);
    }

    /** The instance name (as shown in the emulator's multi-instance manager). */
    public String name() {
        return name;
    }

    /** The product key this instance belongs to, e.g. {@code "bluestacks"}. */
    public String platform() {
        return platformId;
    }

    /** Closes the underlying ADB connection. After this the emulator can no longer be captured or tapped. */
    public void disconnect() {
        device.close();
    }

    @Override
    public String toString() {
        return "Emulator[" + platformId + ":" + name + " @ " + device.endpoint() + "]";
    }
}
