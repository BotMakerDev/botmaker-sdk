package com.botmaker.sdk.api.sound;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.console.Debug;
import com.botmaker.sdk.internal.sound.Meow;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Sounds a bot can play (2026-09-26).
 *
 * <p>Each one plays <em>until done</em>, as Scratch's "play sound until done" block does, so the next statement
 * runs after the sound rather than over it. A machine with no audio device — a server, a container — plays
 * nothing and says so once in the trace: a bot never stops over a sound.
 */
@Palette(category = "sound", categoryLabel = "Sound", icon = "🔊")
public final class Sound {

    private static final int RATE = 44_100;

    private static volatile boolean warned;

    private Sound() {}

    /** Meows like a cat — the Scratch cat's sound, synthesised — and waits until it is done. */
    public static void miaou() {
        play(Meow.samples(RATE));
    }

    private static void play(short[] samples) {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        ByteBuffer bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : samples) bytes.putShort(s);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            line.write(bytes.array(), 0, bytes.capacity());
            line.drain();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            if (!warned) {
                warned = true;
                Debug.log("No audio output here, so sounds are skipped: " + e.getMessage());
            }
        }
    }
}
