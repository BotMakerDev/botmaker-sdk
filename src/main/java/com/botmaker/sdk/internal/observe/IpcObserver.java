package com.botmaker.sdk.internal.observe;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.shared.Diag;
import com.botmaker.shared.ipc.TelemetryClient;
import com.botmaker.shared.ipc.TelemetryEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The single piece of SDK code that knows the Studio exists — an internal, env-gated {@link BotObserver}
 * that ships this package's events over the {@code botmaker-shared} telemetry channel
 * to the Studio's live preview panel.
 *
 * <p>It self-installs from a static initializer (triggered by {@code Bots} loading the class by name), but
 * only when {@link TelemetryClient#fromEnvironment()} finds {@code BM_IPC_PORT} — i.e. only under the Studio.
 * A normal published bot never sets that env var, so no observer is registered and no socket is opened:
 * the SDK stays fully usable, with zero overhead, on its own.
 *
 * <p>It is also {@code Diag}'s sink under Studio, so every debug line the bot prints crosses as a
 * {@link TelemetryEvent.Log} as well.
 */
public final class IpcObserver implements BotObserver {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    static {
        installIfEnabled();
    }

    /** The channel this run opened, for {@link #client()}; null when not under Studio. */
    private static volatile TelemetryClient installed;

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
        IpcObserver observer = new IpcObserver(client);
        installed = client;
        Bots.addObserver(observer);
        Diag.setSink(observer::onLog);
        Runtime.getRuntime().addShutdownHook(new Thread(client::close, "telemetry-client-close"));
    }

    /**
     * The channel to the Studio that launched this bot, or empty when none did: what {@code Ask} sends its
     * question on, so a run has one socket and the answer comes back on it.
     */
    public static Optional<TelemetryClient> client() {
        installIfEnabled();
        return Optional.ofNullable(installed);
    }

    /** The bot's own source line running now, or {@code -1}: where a question was asked from. */
    public static int callerLine() {
        return botLine();
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

    /**
     * Ships one debug line ({@code Diag}'s sink), attributed to the bot's own line that printed it, so the host's
     * trace can reveal the block ({@code docs/refactor/40-run-trace.md}).
     */
    void onLog(TelemetryEvent.Log line) {
        client.send(botFrame().map(at -> line.at(at.getClassName(), at.getLineNumber())).orElse(line));
    }

    // --- SDK-native events → shared wire vocabulary ---

    private static TelemetryEvent toTelemetry(MatchEvent event) {
        MatchResult result = event.result();
        boolean found = result != null && result.isFound();
        TelemetryEvent.Rect matched = found ? rect(result.rect()) : null;
        double confidence = result != null ? result.confidence() : 0.0;
        return new TelemetryEvent.Match(
                target(event.surface()), rect(event.region()), matched, confidence, found, botLine());
    }

    private static TelemetryEvent toTelemetry(SwipeEvent event) {
        return new TelemetryEvent.Swipe(
                target(event.surface()),
                event.start().x(), event.start().y(),
                event.end().x(), event.end().y(),
                event.durationMs(), botLine());
    }

    private static TelemetryEvent toTelemetry(ClickEvent event) {
        return new TelemetryEvent.Click(
                target(event.surface()),
                event.point().x(), event.point().y(),
                event.button(), botLine());
    }

    /**
     * The 1-based source line of the <em>bot's</em> code that triggered this event, or {@code -1}. Found by
     * walking the current stack for the first frame that is neither SDK/shared plumbing nor JDK internals —
     * i.e. the user's own bot class — so the Studio can highlight the running block during a plain run.
     */
    private static int botLine() {
        return botFrame().map(StackWalker.StackFrame::getLineNumber).orElse(-1);
    }

    /**
     * The first frame of the bot's own code, or empty. Our libraries are skipped by their packages rather than
     * by {@code com.botmaker.}: the worked template is {@code com.botmaker.gamebot}, and skipping the whole
     * prefix would find no line in it at all. {@code com.botmaker.studio.} is the trace agent Studio
     * runs a bot with, whose frames sit between a traced call and the bot line that made it.
     */
    static Optional<StackWalker.StackFrame> botFrame() {
        return Diag.Callers.first(f -> isLibrary(f.getClassName()) || f.getLineNumber() <= 0);
    }

    private static final List<String> LIBRARY_PACKAGES = List.of(
            "com.botmaker.sdk.", "com.botmaker.shared.", "com.botmaker.session.", "com.botmaker.plugin.",
            "com.botmaker.basics.", "com.botmaker.studio.", "java.", "javax.", "jdk.", "sun.");

    static boolean isLibrary(String className) {
        for (String prefix : LIBRARY_PACKAGES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private static TelemetryEvent.Target target(Surface surface) {
        if (surface != null && surface.isWindow()) {
            Rect b = surface.bounds();
            if (b != null) {
                return new TelemetryEvent.Target(surface.title(), b.x(), b.y(), b.width(), b.height());
            }
            return new TelemetryEvent.Target(surface.title(), 0, 0, 0, 0);
        }
        return new TelemetryEvent.Target(null, 0, 0, 0, 0); // whole screen
    }

    private static TelemetryEvent.Rect rect(Rect r) {
        return r == null ? null : new TelemetryEvent.Rect(r.x(), r.y(), r.width(), r.height());
    }
}
