// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.internal.trace.SdkRun;
import com.botmaker.shared.emulator.AdbDevice;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An emulator's display brought to the bot's screen size (2026-10-11): {@code wm size} at that size and
 * {@code wm density} scaled with it, so the game lays itself out as it did, only at the bot's pixels. A run sets it at
 * its start and puts both back when the bot's process ends ({@link #showAt}); the look and act tools set it before
 * they read or press, and put both back when the project closes ({@link #holdAt}, {@link #releaseAll}, 2026-11-27:
 * a picture-only game was read at another density than its pictures were cut at). A run that starts meanwhile finds
 * the size already set. A bot killed outright leaves them, and {@code adb shell wm size reset} and
 * {@code wm density reset} undo them by hand.
 */
public final class EmulatorSize {

    private static final String SOURCE = "Screen";
    private static final Pattern SIZE = Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)");
    private static final Pattern DENSITY = Pattern.compile("Physical density:\\s*(\\d+)");
    static final Pattern OVERRIDE_SIZE = Pattern.compile("Override size:\\s*(\\d+x\\d+)");
    static final Pattern OVERRIDE_DENSITY = Pattern.compile("Override density:\\s*(\\d+)");
    /** The devices whose restore already waits for the bot's end: a restart sizing one again adds none. */
    private static final Set<String> HOOKED = ConcurrentHashMap.newKeySet();
    /** What the tools set, by emulator, and what goes back when the project closes. */
    private static final Map<String, Held> HELD = new ConcurrentHashMap<>();
    /** How long a display just resized is given to lay itself out again before the tool reads it. */
    private static final long SETTLE_MS = 500;

    private EmulatorSize() {}

    /** What sizing needs of a device: its shell, a frame, its address. A test fakes it. */
    interface Display {
        String shell(String command);

        BufferedImage screencap();

        String endpoint();

        static Display of(AdbDevice device) {
            return new Display() {
                @Override
                public String shell(String command) {
                    return device.shell(command);
                }

                @Override
                public BufferedImage screencap() {
                    return device.screencap();
                }

                @Override
                public String endpoint() {
                    return device.endpoint();
                }
            };
        }
    }

    /** What a display was set to, {@code asked} at {@code dpi}, and the two commands that put the user's own back. */
    record Shown(Size asked, int dpi, String sizeBack, String densityBack) {}

    /** What the tools set on {@code display}, at the bot's {@code size}, and what goes back on close. */
    private record Held(Display display, Size size, String sizeBack, String densityBack) {}

    /**
     * Shows {@code source}'s display at {@code size} for a run, when it is an emulator, until the bot's process ends;
     * false when it is not one, or the guest did not say its size.
     */
    public static boolean showAt(CaptureSource source, Size size) {
        Emulator emulator = source instanceof Emulator e ? e
                : source instanceof EmulatorSource s ? s.connected() : null;
        if (emulator == null) return false;
        Display display = Display.of(emulator.device());
        try {
            Optional<Shown> shown = show(display, size);
            if (shown.isEmpty()) return false;
            // What the user had set is put back, read before the first change: a restart finds the bot's own.
            if (HOOKED.add(display.endpoint())) {
                Shown back = shown.get();
                Runtime.getRuntime().addShutdownHook(new Thread(
                        () -> restore(display, back.sizeBack(), back.densityBack()), "emulator-size-reset"));
            }
            SdkRun.debug(SOURCE, "The emulator shows " + size + " for this run, at " + shown.get().dpi()
                    + " dpi; it is reset when the bot ends.");
            return true;
        } catch (RuntimeException refused) {
            SdkRun.warn(SOURCE, "Could not set the emulator's size: " + refused.getMessage());
            return false;
        }
    }

    /**
     * Shows {@code source}'s display at the bot's {@code size} for the look and act tools, when it is an emulator,
     * until {@link #releaseAll}: the {@code wm} commands are said once per emulator and size, never on each look.
     * False when it is not one, it is down (never launched here), or the guest did not say its size. Blocking: it may
     * connect to the emulator.
     */
    public static boolean holdAt(CaptureSource source, Size size) {
        if (size == null || size.empty() || source == null) return false;
        CaptureSource base = source.base();
        String key = base instanceof EmulatorSource s ? s.instanceName() : null;
        Held held = key == null ? null : HELD.get(key);
        if (held != null && held.size().equals(size)) return true;
        Emulator emulator = base instanceof EmulatorSource s ? s.reachedIfUp() : base instanceof Emulator e ? e : null;
        if (emulator == null) return false;
        return hold(key == null ? emulator.device().endpoint() : key, Display.of(emulator.device()), size);
    }

    /**
     * {@link #holdAt} on {@code display}, kept under {@code key}. A second size of one emulator keeps the first
     * restore, so the close puts back the user's own rather than the tools' earlier size.
     */
    static synchronized boolean hold(String key, Display display, Size size) {
        Held held = HELD.get(key);
        if (held != null && held.size().equals(size)) return true;
        try {
            Optional<Shown> shown = show(display, size);
            if (shown.isEmpty()) return false;
            HELD.put(key, held != null ? new Held(display, size, held.sizeBack(), held.densityBack())
                    : new Held(display, size, shown.get().sizeBack(), shown.get().densityBack()));
            Thread.sleep(SETTLE_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    /** Puts back every display the tools set, as the user had it: the project closes. */
    public static synchronized void releaseAll() {
        HELD.values().forEach(held -> restore(held.display(), held.sizeBack(), held.densityBack()));
        HELD.clear();
    }

    /**
     * Sets {@code display} to {@code size}, turned as the game shows, at the density scaled with it; empty when the
     * guest did not say its size or density, or gave no frame. The commands back are read before the change.
     */
    static Optional<Shown> show(Display display, Size size) {
        String sizeAnswer = display.shell("wm size");
        String densityAnswer = display.shell("wm density");
        Size natural = physical(sizeAnswer).orElse(null);
        OptionalInt density = density(densityAnswer);
        BufferedImage frame = display.screencap();
        if (natural == null || density.isEmpty() || frame == null) return Optional.empty();
        // wm size names the display unturned: a game turned across a portrait phone is asked for as portrait.
        boolean turned = frame.getWidth() > frame.getHeight() != natural.width() > natural.height();
        Size asked = turned ? new Size(size.height(), size.width()) : size;
        int dpi = scaledDensity(density.getAsInt(), natural, asked);
        String sizeBack = override(OVERRIDE_SIZE, sizeAnswer).map(s -> "wm size " + s).orElse("wm size reset");
        String densityBack = override(OVERRIDE_DENSITY, densityAnswer).map(d -> "wm density " + d)
                .orElse("wm density reset");
        display.shell("wm size " + asked.width() + "x" + asked.height());
        display.shell("wm density " + dpi);
        return Optional.of(new Shown(asked, dpi, sizeBack, densityBack));
    }

    private static void restore(Display display, String size, String density) {
        try {
            display.shell(size);
            display.shell(density);
        } catch (RuntimeException gone) {
            // The emulator closed first: nothing is left to reset.
        }
    }

    /** The override {@code wm} reports beside the physical value, as it is written back. */
    static Optional<String> override(Pattern pattern, String answer) {
        Matcher m = pattern.matcher(answer == null ? "" : answer);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** The display's own size in {@code wm size}'s answer. */
    static Optional<Size> physical(String answer) {
        Matcher m = SIZE.matcher(answer == null ? "" : answer);
        return m.find() ? Optional.of(new Size(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))))
                : Optional.empty();
    }

    /** The display's own density in {@code wm density}'s answer. */
    static OptionalInt density(String answer) {
        Matcher m = DENSITY.matcher(answer == null ? "" : answer);
        return m.find() ? OptionalInt.of(Integer.parseInt(m.group(1))) : OptionalInt.empty();
    }

    /** {@code density} scaled as the display is, by the smaller side's ratio: the same layout in fewer pixels. */
    static int scaledDensity(int density, Size natural, Size asked) {
        double ratio = Math.min(asked.width() / (double) natural.width(), asked.height() / (double) natural.height());
        return Math.max(72, (int) Math.round(density * ratio));
    }
}
