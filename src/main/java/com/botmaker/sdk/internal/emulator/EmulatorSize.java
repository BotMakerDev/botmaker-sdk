package com.botmaker.sdk.internal.emulator;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.shared.Diag;
import com.botmaker.shared.emulator.AdbDevice;

import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An emulator's display brought to the bot's screen size at a run's start (2026-10-11): {@code wm size} at that
 * size and {@code wm density} scaled with it, so the game lays itself out as it did, only at the bot's pixels. Both
 * go back to what they were when the bot's process ends; a bot killed outright leaves them, and
 * {@code adb shell wm size reset} and {@code wm density reset} undo them by hand.
 */
public final class EmulatorSize {

    private static final String SOURCE = "Screen";
    private static final Pattern SIZE = Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)");
    private static final Pattern DENSITY = Pattern.compile("Physical density:\\s*(\\d+)");
    static final Pattern OVERRIDE_SIZE = Pattern.compile("Override size:\\s*(\\d+x\\d+)");
    static final Pattern OVERRIDE_DENSITY = Pattern.compile("Override density:\\s*(\\d+)");
    /** The devices whose restore already waits for the bot's end: a restart sizing one again adds none. */
    private static final Set<String> HOOKED = ConcurrentHashMap.newKeySet();

    private EmulatorSize() {}

    /**
     * Shows {@code source}'s display at {@code size}, when it is an emulator; false when it is not one, or the guest
     * did not say its size.
     */
    public static boolean showAt(CaptureSource source, Size size) {
        Emulator emulator = source instanceof Emulator e ? e
                : source instanceof EmulatorSource s ? s.connected() : null;
        if (emulator == null) return false;
        AdbDevice device = emulator.device();
        try {
            String sizeAnswer = device.shell("wm size");
            String densityAnswer = device.shell("wm density");
            Size natural = physical(sizeAnswer).orElse(null);
            OptionalInt density = density(densityAnswer);
            BufferedImage shown = device.screencap();
            if (natural == null || density.isEmpty() || shown == null) return false;
            // wm size names the display unturned: a game turned across a portrait phone is asked for as portrait.
            boolean turned = shown.getWidth() > shown.getHeight() != natural.width() > natural.height();
            Size asked = turned ? new Size(size.height(), size.width()) : size;
            int dpi = scaledDensity(density.getAsInt(), natural, asked);
            // What the user had set is put back, read before the first change: a restart finds the bot's own.
            if (HOOKED.add(device.endpoint())) {
                String size0 = override(OVERRIDE_SIZE, sizeAnswer).map(s -> "wm size " + s).orElse("wm size reset");
                String density0 = override(OVERRIDE_DENSITY, densityAnswer).map(d -> "wm density " + d)
                        .orElse("wm density reset");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> restore(device, size0, density0),
                        "emulator-size-reset"));
            }
            device.shell("wm size " + asked.width() + "x" + asked.height());
            device.shell("wm density " + dpi);
            Diag.log(SOURCE, "The emulator shows " + size + " for this run, at " + dpi + " dpi; it is reset when the"
                    + " bot ends.", 0, null);
            return true;
        } catch (RuntimeException refused) {
            Diag.warn(SOURCE, "Could not set the emulator's size: " + refused.getMessage());
            return false;
        }
    }

    private static void restore(AdbDevice device, String size, String density) {
        try {
            device.shell(size);
            device.shell(density);
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
