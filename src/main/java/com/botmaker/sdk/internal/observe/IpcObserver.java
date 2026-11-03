package com.botmaker.sdk.internal.observe;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.MatchResult;
import com.botmaker.sdk.internal.screens.ScreenStep;
import com.botmaker.sdk.internal.trace.SdkRun;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The SDK's telemetry: a {@link BotObserver} that sends each event as one of the SDK's own frames ({@link RunFrames})
 * on the run channel its host handed it, which the host relays whole to the SDK's plugin half (spec {@code
 * docs/superpowers/specs/2026-11-03-run-reports-design.md} §7).
 *
 * <p>Installed only when a host started the run ({@link SdkRun#started}); a run without one registers no observer and
 * encodes nothing.
 */
public final class IpcObserver implements BotObserver {

    private static final AtomicReference<IpcObserver> INSTALLED = new AtomicReference<>();

    /** The activity the flow last entered, which a screen reached is a step of; empty before the first. */
    private volatile String activity = "";

    IpcObserver() {}

    /** Registers the observer, once: it sends on whichever channel the run holds ({@link SdkRun#channel()}). */
    public static void install() {
        IpcObserver observer = new IpcObserver();
        if (INSTALLED.compareAndSet(null, observer)) Bots.addObserver(observer);
    }

    /** Removes the observer, if one is registered. */
    public static void uninstall() {
        Bots.removeObserver(INSTALLED.getAndSet(null));
    }

    @Override
    public void onMatch(MatchEvent event) {
        send(toEvent(event));
    }

    @Override
    public void onClick(ClickEvent event) {
        send(new RunEvent.Click(target(event.surface()), event.point().x(), event.point().y(), event.button(),
                botLine()));
    }

    @Override
    public void onSwipe(SwipeEvent event) {
        send(new RunEvent.Swipe(target(event.surface()), event.start().x(), event.start().y(), event.end().x(),
                event.end().y(), event.durationMs(), botLine()));
    }

    /** Where the bot is in its flow, for the host's run overlay: the activity, with no action yet. */
    @Override
    public void onActivity(String label) {
        activity = label;
        send(new RunEvent.Step(label, "", -1));
    }

    /** The screen a walk of the map reached, as a step of the activity it is in ({@link ScreenStep}). */
    @Override
    public void onScreen(String constant, String name) {
        send(new RunEvent.Step(activity, ScreenStep.action(constant, name), -1));
    }

    /** Sends {@code event}; one too long for a frame (a text over 64 KB) is dropped, never thrown at the bot. */
    private void send(RunEvent event) {
        byte[] frame;
        try {
            frame = RunFrames.encode(event);
        } catch (UncheckedIOException tooLong) {
            return;
        }
        SdkRun.channel().frame(frame);
    }

    private static RunEvent toEvent(MatchEvent event) {
        MatchResult result = event.result();
        boolean found = result != null && result.isFound();
        RunEvent.Rect matched = found ? rect(result.rect()) : null;
        double confidence = result != null ? result.confidence() : 0.0;
        return new RunEvent.Match(target(event.surface()), rect(event.region()), matched, confidence, found,
                botLine());
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
     * by {@code com.botmaker.}: a bot may live under it ({@code com.botmaker.farm}), and skipping the whole
     * prefix would find no line in it at all. {@code com.botmaker.studio.} is the trace agent Studio
     * runs a bot with, whose frames sit between a traced call and the bot line that made it.
     */
    public static Optional<StackWalker.StackFrame> botFrame() {
        return SdkRun.first(f -> isLibrary(f.getClassName()) || f.getLineNumber() <= 0);
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

    private static RunEvent.Target target(Surface surface) {
        if (surface != null && surface.isWindow()) {
            Rect b = surface.bounds();
            if (b != null) {
                return new RunEvent.Target(surface.title(), b.x(), b.y(), b.width(), b.height());
            }
            return new RunEvent.Target(surface.title(), 0, 0, 0, 0);
        }
        return RunEvent.NO_SURFACE; // whole screen
    }

    private static RunEvent.Rect rect(Rect r) {
        return r == null ? null : new RunEvent.Rect(r.x(), r.y(), r.width(), r.height());
    }
}
