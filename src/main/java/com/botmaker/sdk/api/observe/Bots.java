package com.botmaker.sdk.api.observe;

import com.botmaker.sdk.api.ApiId;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and dispatcher for {@link BotObserver}s. This is the one piece of static state in the public
 * facade layer — a deliberate exception to the SDK's "stateless dispatcher" style, unavoidable for observing
 * the static vision/interaction facades. It is kept tiny and side-effect-free: the fan-out short-circuits
 * when no observer is registered, so the instrumentation costs nothing on the hot path of a normal bot run.
 *
 * <p>The {@code fire*} methods are called by the SDK's own facades; user code only uses
 * {@link #addObserver}/{@link #removeObserver}.
 */
@ApiId("bots")
public final class Bots {

    private static final List<BotObserver> OBSERVERS = new CopyOnWriteArrayList<>();

    static {
        // Optional Studio telemetry bridge: it self-installs (registers an observer) only when the bot was
        // launched by the Studio (env var BM_IPC_PORT set). Loaded by name so this neutral, public observer
        // API keeps zero compile-time dependency on the internal socket bridge — a bot never needs the Studio.
        try {
            Class.forName("com.botmaker.sdk.internal.observe.IpcObserver");
        } catch (Throwable ignored) {
        }
    }

    private Bots() {}

    /** Registers an observer to receive vision/interaction events. No-op if {@code observer} is null. */
    public static void addObserver(BotObserver observer) {
        if (observer != null) OBSERVERS.add(observer);
    }

    /** Removes a previously-registered observer. */
    public static void removeObserver(BotObserver observer) {
        OBSERVERS.remove(observer);
    }

    /** True if at least one observer is registered — the guard the facades check before building events. */
    public static boolean hasObservers() {
        return !OBSERVERS.isEmpty();
    }

    /** Dispatches a match event to every observer; a misbehaving observer cannot break the bot. */
    public static void fireMatch(MatchEvent event) {
        for (BotObserver observer : OBSERVERS) {
            try {
                observer.onMatch(event);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Dispatches a click event to every observer; a misbehaving observer cannot break the bot. */
    public static void fireClick(ClickEvent event) {
        for (BotObserver observer : OBSERVERS) {
            try {
                observer.onClick(event);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Dispatches a swipe event to every observer; a misbehaving observer cannot break the bot. */
    public static void fireSwipe(SwipeEvent event) {
        for (BotObserver observer : OBSERVERS) {
            try {
                observer.onSwipe(event);
            } catch (RuntimeException ignored) {
            }
        }
    }
}
