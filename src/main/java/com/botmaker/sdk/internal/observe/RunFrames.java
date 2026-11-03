package com.botmaker.sdk.internal.observe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A {@link RunEvent} as the bytes the run channel carries, and back: the SDK's own telemetry format, written by the
 * bot half and read by the plugin half of the same SDK. A frame is a version byte, a type byte and the event's
 * fields; a version or type this reader does not know is {@linkplain Unreadable unreadable}, never guessed.
 */
public final class RunFrames {

    /**
     * Bumped when a frame's fields change; a frame of another version is unreadable. Starts at 3, past the versions
     * shared's {@code TelemetryEvent} wire used (1, 2), so a frame of the old wire is never read as one of these.
     */
    static final int VERSION = 3;

    private static final int MATCH = 1;
    private static final int CLICK = 2;
    private static final int REGION = 3;
    private static final int SWIPE = 4;
    private static final int STEP = 5;

    private RunFrames() {}

    /** A frame this SDK cannot read: another version, an unknown type, or cut short. */
    public static final class Unreadable extends Exception {
        Unreadable(String message) {
            super(message);
        }
    }

    /** {@code event} as a frame. */
    public static byte[] encode(RunEvent event) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeByte(VERSION);
            switch (event) {
                case RunEvent.Match m -> {
                    out.writeByte(MATCH);
                    target(out, m.target());
                    rect(out, m.region());
                    rect(out, m.rect());
                    out.writeDouble(m.confidence());
                    out.writeBoolean(m.found());
                    out.writeInt(m.line());
                }
                case RunEvent.Click c -> {
                    out.writeByte(CLICK);
                    target(out, c.target());
                    out.writeInt(c.x());
                    out.writeInt(c.y());
                    out.writeInt(c.button());
                    out.writeInt(c.line());
                }
                case RunEvent.Region r -> {
                    out.writeByte(REGION);
                    target(out, r.target());
                    rect(out, r.rect());
                    out.writeInt(r.line());
                }
                case RunEvent.Swipe s -> {
                    out.writeByte(SWIPE);
                    target(out, s.target());
                    out.writeInt(s.x1());
                    out.writeInt(s.y1());
                    out.writeInt(s.x2());
                    out.writeInt(s.y2());
                    out.writeLong(s.durationMs());
                    out.writeInt(s.line());
                }
                case RunEvent.Step s -> {
                    out.writeByte(STEP);
                    out.writeUTF(s.activity());
                    out.writeUTF(s.action());
                    out.writeInt(s.line());
                }
            }
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible); // an in-memory stream does not fail
        }
        return buffer.toByteArray();
    }

    /** The event {@code frame} carries. */
    public static RunEvent decode(byte[] frame) throws Unreadable {
        if (frame == null || frame.length < 2) throw new Unreadable("an empty frame");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            int version = in.readUnsignedByte();
            if (version != VERSION) throw new Unreadable("frame version " + version + ", this SDK reads " + VERSION);
            int type = in.readUnsignedByte();
            return switch (type) {
                case MATCH -> new RunEvent.Match(target(in), rect(in), rect(in), in.readDouble(), in.readBoolean(),
                        in.readInt());
                case CLICK -> new RunEvent.Click(target(in), in.readInt(), in.readInt(), in.readInt(), in.readInt());
                case REGION -> new RunEvent.Region(target(in), rect(in), in.readInt());
                case SWIPE -> new RunEvent.Swipe(target(in), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                        in.readLong(), in.readInt());
                case STEP -> new RunEvent.Step(in.readUTF(), in.readUTF(), in.readInt());
                default -> throw new Unreadable("frame type " + type);
            };
        } catch (IOException cut) {
            throw new Unreadable("a frame cut short: " + cut.getMessage());
        }
    }

    private static void target(DataOutputStream out, RunEvent.Target t) throws IOException {
        RunEvent.Target target = t == null ? RunEvent.NO_SURFACE : t;
        out.writeBoolean(target.title() != null);
        if (target.title() != null) out.writeUTF(target.title());
        out.writeInt(target.x());
        out.writeInt(target.y());
        out.writeInt(target.width());
        out.writeInt(target.height());
    }

    private static RunEvent.Target target(DataInputStream in) throws IOException {
        String title = in.readBoolean() ? in.readUTF() : null;
        return new RunEvent.Target(title, in.readInt(), in.readInt(), in.readInt(), in.readInt());
    }

    private static void rect(DataOutputStream out, RunEvent.Rect r) throws IOException {
        out.writeBoolean(r != null);
        if (r == null) return;
        out.writeInt(r.x());
        out.writeInt(r.y());
        out.writeInt(r.width());
        out.writeInt(r.height());
    }

    private static RunEvent.Rect rect(DataInputStream in) throws IOException {
        return in.readBoolean() ? new RunEvent.Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt()) : null;
    }
}
