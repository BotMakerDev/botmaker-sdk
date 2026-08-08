package com.botmaker.sdk.internal.observe;

import com.botmaker.sdk.api.Rect;
import com.botmaker.sdk.api.observe.BotObserver;
import com.botmaker.sdk.api.observe.Bots;
import com.botmaker.sdk.api.observe.ClickEvent;
import com.botmaker.sdk.api.observe.MatchEvent;
import com.botmaker.sdk.api.observe.Surface;
import com.botmaker.sdk.api.observe.SwipeEvent;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.shared.ipc.TelemetryClient;
import com.botmaker.shared.ipc.TelemetryEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The single piece of SDK code that knows the Studio exists — an internal, env-gated {@link BotObserver}
 * that ships {@link com.botmaker.sdk.api.observe} events over the {@code botmaker-shared} telemetry channel
 * to the Studio's live preview panel.
 *
 * <p>It self-installs from a static initializer (triggered by {@code Bots} loading the class by name), but
 * only when {@link TelemetryClient#fromEnvironment()} finds {@code BM_IPC_PORT} — i.e. only under the Studio.
 * A normal published bot never sets that env var, so no observer is registered and no socket is opened:
 * the SDK stays fully usable, with zero overhead, on its own.
 */
public final class IpcObserver implements BotObserver {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    static {
        installIfEnabled();
    }

    private final TelemetryClient client;

    // Package-private (not private) so the translation + send path can be unit-tested with a client
    // pointed at a local server, without setting process environment variables.
    IpcObserver(TelemetryClient client) {
        this.client = client;
    }

    /** Registers the bridge iff the Studio launched this bot; idempotent and a no-op otherwise. */
    public static void installIfEnabled() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        TelemetryClient client = TelemetryClient.fromEnvironment();
        if (client == null) {
            INSTALLED.set(false); // not under Studio; allow a later retry if the env appears
            return;
        }
        Bots.addObserver(new IpcObserver(client));
        Runtime.getRuntime().addShutdownHook(new Thread(client::close, "telemetry-client-close"));
    }

    @Override
    public void onMatch(MatchEvent event) {
        client.send(toTelemetry(event));
    }

    @Override
    public void onClick(ClickEvent event) {
        client.send(toTelemetry(event));
    }

    @Override
    public void onSwipe(SwipeEvent event) {
        client.send(toTelemetry(event));
    }

    // --- SDK-native events → shared wire vocabulary ---

    private static TelemetryEvent toTelemetry(MatchEvent event) {
        MatchResult result = event.result();
        boolean found = result != null && result.isFound();
        TelemetryEvent.Rect matched = found ? rect(result.getRect()) : null;
        double confidence = result != null ? result.getConfidence() : 0.0;
        return new TelemetryEvent.Match(
                target(event.surface()), rect(event.region()), matched, confidence, found, botLine());
    }

    private static TelemetryEvent toTelemetry(SwipeEvent event) {
        return new TelemetryEvent.Swipe(
                target(event.surface()),
                (int) event.start().x, (int) event.start().y,
                (int) event.end().x, (int) event.end().y,
                event.durationMs(), botLine());
    }

    private static TelemetryEvent toTelemetry(ClickEvent event) {
        return new TelemetryEvent.Click(
                target(event.surface()),
                (int) event.point().x, (int) event.point().y,
                event.button(), botLine());
    }

    /**
     * The 1-based source line of the <em>bot's</em> code that triggered this event, or {@code -1}. Found by
     * walking the current stack for the first frame that is neither SDK/shared plumbing nor JDK internals —
     * i.e. the user's own bot class — so the Studio can highlight the running block during a plain run.
     */
    private static int botLine() {
        for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            String cn = f.getClassName();
            if (cn.startsWith("com.botmaker.") || cn.startsWith("java.") || cn.startsWith("javax.")
                    || cn.startsWith("jdk.") || cn.startsWith("sun.") || cn.equals("java.lang.Thread")) {
                continue;
            }
            int line = f.getLineNumber();
            if (line > 0) return line;
        }
        return -1;
    }

    private static TelemetryEvent.Target target(Surface surface) {
        if (surface != null && surface.isWindow()) {
            Rect b = surface.bounds();
            if (b != null) {
                return new TelemetryEvent.Target(surface.title(), b.x, b.y, b.width, b.height);
            }
            return new TelemetryEvent.Target(surface.title(), 0, 0, 0, 0);
        }
        return new TelemetryEvent.Target(null, 0, 0, 0, 0); // whole screen
    }

    private static TelemetryEvent.Rect rect(Rect r) {
        return r == null ? null : new TelemetryEvent.Rect(r.x, r.y, r.width, r.height);
    }
}
