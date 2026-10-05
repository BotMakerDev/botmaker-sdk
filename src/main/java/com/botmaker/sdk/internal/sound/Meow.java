package com.botmaker.sdk.internal.sound;

/**
 * A cat's meow, synthesised: no recording ships in the jar.
 *
 * <p>Built the way the sound is shaped, not the way a recording is played. A buzzy voiced tone (a band-limited
 * sawtooth) carries the pitch, which rises from about 500 Hz to 900 Hz and falls to 450 Hz — the "mi-AOU"
 * contour. Two resonances (formants) glide over it, from a closed, nasal "m" through a bright "i" to an open
 * "ao" and a rounded "u", which is what makes it read as a meow rather than a slide whistle. A soft attack and
 * release keep the ends free of clicks.
 *
 * <p>Pure: {@link #samples} returns the waveform and touches no audio device, so it is tested headlessly.
 */
public final class Meow {

    private Meow() {}

    /** How long the meow lasts, in seconds. */
    public static final double SECONDS = 0.62;

    /** The loudest sample, a little under full scale so a mixer adding it to something else does not clip. */
    static final double PEAK = 0.8;

    /** 16-bit signed mono samples of the meow at {@code rate} samples per second. */
    public static short[] samples(int rate) {
        int n = (int) Math.round(SECONDS * rate);
        double[] out = new double[n];
        double phase = 0;
        Resonator low = new Resonator();
        Resonator high = new Resonator();
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double pitch = pitch(t);
            phase += pitch / rate;
            phase -= Math.floor(phase);
            double source = saw(phase, pitch, rate);
            double voiced = low.next(source, formant1(t), 90, rate) + 0.6 * high.next(source, formant2(t), 120, rate);
            out[i] = voiced * envelope(t);
        }
        return normalised(out);
    }

    /** The pitch contour, in Hz, at {@code t} in [0, 1]: up to a peak a third of the way in, then down. */
    static double pitch(double t) {
        return t < 0.33 ? lerp(500, 900, smooth(t / 0.33)) : lerp(900, 450, smooth((t - 0.33) / 0.67));
    }

    /** First formant: closed "m" (300) → "i" (350) → open "ao" (800) → rounded "u" (400). */
    private static double formant1(double t) {
        if (t < 0.12) return lerp(300, 350, t / 0.12);
        if (t < 0.45) return lerp(350, 800, smooth((t - 0.12) / 0.33));
        return lerp(800, 400, smooth((t - 0.45) / 0.55));
    }

    /** Second formant: "m" (1200) → bright "i" (2300) → "ao" (1200) → "u" (800). */
    private static double formant2(double t) {
        if (t < 0.12) return lerp(1200, 2300, t / 0.12);
        if (t < 0.45) return lerp(2300, 1200, smooth((t - 0.12) / 0.33));
        return lerp(1200, 800, smooth((t - 0.45) / 0.55));
    }

    /** Fades in over the "m", holds, and fades out over the last quarter. */
    private static double envelope(double t) {
        if (t < 0.08) return smooth(t / 0.08) * 0.6;
        if (t < 0.15) return lerp(0.6, 1.0, (t - 0.08) / 0.07);
        if (t < 0.75) return 1.0;
        return 1.0 - smooth((t - 0.75) / 0.25);
    }

    /** A sawtooth with only the harmonics under the Nyquist frequency, so the high notes do not alias. */
    private static double saw(double phase, double pitch, int rate) {
        int harmonics = Math.max(1, (int) (rate / 2.0 / pitch));
        double sum = 0;
        for (int k = 1; k <= harmonics; k++) sum += Math.sin(2 * Math.PI * k * phase) / k;
        return sum * 0.5;
    }

    private static short[] normalised(double[] wave) {
        double max = 1e-9;
        for (double v : wave) max = Math.max(max, Math.abs(v));
        short[] out = new short[wave.length];
        for (int i = 0; i < wave.length; i++) {
            out[i] = (short) Math.round(wave[i] / max * PEAK * Short.MAX_VALUE);
        }
        return out;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * Math.clamp(t, 0, 1);
    }

    private static double smooth(double t) {
        double c = Math.clamp(t, 0, 1);
        return c * c * (3 - 2 * c);
    }

    /** A two-pole band-pass filter whose centre may move every sample. */
    private static final class Resonator {
        private double y1;
        private double y2;

        double next(double x, double centre, double bandwidth, int rate) {
            double r = Math.exp(-Math.PI * bandwidth / rate);
            double y = (1 - r) * x + 2 * r * Math.cos(2 * Math.PI * centre / rate) * y1 - r * r * y2;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
