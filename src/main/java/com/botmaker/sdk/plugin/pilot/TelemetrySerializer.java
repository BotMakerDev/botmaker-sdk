package com.botmaker.sdk.plugin.pilot;

import com.botmaker.shared.ipc.TelemetryEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Every text message Studio sends a pilot client — {@code telemetry}, {@code state} and {@code video} — as one
 * record per shape, written by one {@link ObjectMapper}, so the wire schema has one owner.
 *
 * <p>The pilot web app's {@code types.ts} mirrors it field for field, and what keeps that true is
 * {@code TelemetryWireContractTest} plus its counterpart in the pilot repo: both read the same
 * {@code pilot-wire/wire-golden.json} corpus, and both assert its digest, so neither copy can move alone.
 *
 * <p>A null in a record is written as {@code null} — the client types {@code target}, {@code region},
 * {@code rect} and {@code codec} as nullable and reads the key. The one exception is the {@code state}
 * message's {@code reason}, which is <b>omitted</b> when there is none, so the three no-reason cases in the
 * corpus stay byte-identical to what shipped before the field existed.
 */
public final class TelemetrySerializer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TelemetrySerializer() {}

    /** Where a run stands, as the {@code state} message's {@code run} key spells it. A closed set on both ends. */
    public enum RunState {
        RUNNING("running"),
        PAUSED("paused"),
        STOPPED("stopped");

        private final String id;

        RunState(String id) {
            this.id = id;
        }

        /** The token on the wire; the phone client switches on exactly these three strings. */
        public String id() {
            return id;
        }
    }

    // --- The messages ---

    record Telemetry(String type, Event event) {}

    record State(String type, String run, boolean backgroundInput,
                 @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {}

    record Video(String type, String codec, int sx, int sy, int sw, int sh) {}

    record VideoStopped(String type, String codec) {}

    // --- The event body, one record per kind ---

    sealed interface Event permits MatchEvent, ClickEvent, RegionEvent, SwipeEvent {}

    record MatchEvent(long ts, Target target, String kind, boolean found, double confidence, Rect region,
                      Rect rect) implements Event {}

    record ClickEvent(long ts, Target target, String kind, int x, int y, int button) implements Event {}

    record RegionEvent(long ts, Target target, String kind, Rect rect) implements Event {}

    record SwipeEvent(long ts, Target target, String kind, int x1, int y1, int x2, int y2, long duration)
            implements Event {}

    /** {@link TelemetryEvent.Target} with the client's short keys. */
    record Target(String title, int x, int y, int w, int h) {
        static Target of(TelemetryEvent.Target t) {
            return t == null ? null : new Target(t.title(), t.x(), t.y(), t.width(), t.height());
        }
    }

    /** {@link TelemetryEvent.Rect} with the client's short keys. */
    record Rect(int x, int y, int w, int h) {
        static Rect of(TelemetryEvent.Rect r) {
            return r == null ? null : new Rect(r.x(), r.y(), r.width(), r.height());
        }
    }

    // --- Writers ---

    /** The full {@code telemetry} text message, as it goes out on the socket. */
    public static String telemetryJson(TelemetryEvent te) {
        return write(new Telemetry("telemetry", event(te)));
    }

    /**
     * The full {@code state} text message. {@code backgroundInput} tells the client whether Interact will
     * leave the host's real cursor alone, so it can warn before the user's pointer visibly gets hijacked.
     */
    public static String stateJson(RunState run, boolean backgroundInput) {
        return stateJson(run, backgroundInput, null);
    }

    /**
     * The {@code state} message carrying an optional {@code reason} — a short human sentence for why the client
     * is not receiving frames, so a blank canvas says something instead of nothing.
     */
    public static String stateJson(RunState run, boolean backgroundInput, String reason) {
        return write(new State("state", run.id(), backgroundInput, reason));
    }

    /**
     * The {@code video} message: "binary frames from here on are H.264 access units for this codec, covering
     * this surface rect". Sent to a client that declared H.264 support, immediately before the first packet.
     *
     * <p>The rect is here rather than on each frame, which is the point of the message existing at all. A JPEG
     * frame carries a 16-byte header because its surface can change between any two frames; a video stream is
     * one encoder on one display, so the rect changes only when the stream does.
     */
    public static String videoJson(String codec, int sx, int sy, int sw, int sh) {
        return write(new Video("video", codec, sx, sy, sw, sh));
    }

    /**
     * The {@code video} message that ends a stream — a null codec. The client tears its decoder down and goes
     * back to drawing the JPEG frames that resume in its place, so this is sent on <em>every</em> way a stream
     * can end (route change, encoder death, last H.264 client leaving) and not only on a tidy shutdown.
     */
    public static String videoStoppedJson() {
        return write(new VideoStopped("video", null));
    }

    /** The event body, stamped with the send clock: {@code ts} is when it left, not when the bot saw it. */
    static Event event(TelemetryEvent te) {
        long ts = System.currentTimeMillis();
        Target target = Target.of(te.target());
        return switch (te) {
            case TelemetryEvent.Match m -> new MatchEvent(ts, target, "Match", m.found(),
                    confidence(m.confidence()), Rect.of(m.region()), Rect.of(m.rect()));
            case TelemetryEvent.Click c -> new ClickEvent(ts, target, "Click", c.x(), c.y(), c.button());
            case TelemetryEvent.Region r -> new RegionEvent(ts, target, "Region", Rect.of(r.rect()));
            case TelemetryEvent.Swipe s -> new SwipeEvent(ts, target, "Swipe",
                    s.x1(), s.y1(), s.x2(), s.y2(), s.durationMs());
        };
    }

    /**
     * Four decimals, which is what the client shows, and {@code 0} for a confidence that never got computed:
     * JSON has no {@code NaN}, and a number field holding the string {@code "NaN"} would reach a client that
     * formats it as a number.
     */
    static double confidence(double c) {
        return Double.isFinite(c) ? Math.round(c * 10_000) / 10_000.0 : 0.0;
    }

    private static String write(Object message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // Every message here is records of strings and numbers; a failure is a bug in this class.
            throw new IllegalStateException("pilot message did not serialize: " + message, e);
        }
    }
}
