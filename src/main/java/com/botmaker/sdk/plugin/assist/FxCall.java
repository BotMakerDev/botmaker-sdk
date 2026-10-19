package com.botmaker.sdk.plugin.assist;

import com.botmaker.shared.Diag;
import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Runs a write on the JavaFX application thread and waits for it, for an assistant tool's handler, which the
 * host calls off that thread. The bot's Java is only written on the FX thread. The one class of this package
 * that links JavaFX, so declaring the tools links none.
 */
public final class FxCall {

    private static final long TIMEOUT_SECONDS = 30;

    private FxCall() {}

    /**
     * {@code work}'s answer, computed on the FX thread; its exception is rethrown here. A call that times out
     * before the FX thread took it is cancelled, so it does not run late and a retry does not write twice; one
     * already running is said to be. A host with no FX thread at all — a headless one, a test — runs it here.
     */
    public static <T> T call(Supplier<T> work) {
        if (Platform.isFxApplicationThread()) return work.get();
        // Reading and writing the bot through Studio is a call's part of its own (2026-10-15).
        return ToolTiming.timed(ToolTiming.Phase.STUDIO, () -> handed(work));
    }

    private static <T> T handed(Supplier<T> work) {
        CompletableFuture<T> answer = new CompletableFuture<>();
        // PENDING until the FX thread takes it; the waiter may cancel only work that has not started.
        AtomicInteger state = new AtomicInteger(PENDING);
        String caller = caller();
        long queued = System.nanoTime();
        try {
            Platform.runLater(() -> {
                if (!state.compareAndSet(PENDING, RUNNING)) return;
                long started = System.nanoTime();
                try {
                    answer.complete(work.get());
                } catch (RuntimeException | Error e) {
                    answer.completeExceptionally(e);
                } finally {
                    reportIfSlow(caller, queued, started);
                }
            });
        } catch (IllegalStateException noToolkit) {
            return work.get();
        }
        try {
            return answer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Studio did not answer within " + TIMEOUT_SECONDS + " s — is a dialog "
                    + "open? " + cancel(state));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted. " + cancel(state));
        }
    }

    /** A call that holds the FX thread longer than this is named in the debug output. */
    private static final long SLOW_MS = 50;

    /** The first frame outside this class, so a slow call says who asked for it. */
    private static String caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().equals(FxCall.class.getName()))
                .findFirst().map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":"
                        + frame.getLineNumber())
                .orElse("?"));
    }

    private static void reportIfSlow(String caller, long queued, long started) {
        long ran = (System.nanoTime() - started) / 1_000_000;
        if (ran < SLOW_MS) return;
        Diag.log("FX thread held " + ran + " ms by " + caller + " (waited " + (started - queued) / 1_000_000
                + " ms to start)");
    }

    private static final int PENDING = 0;
    private static final int RUNNING = 1;
    private static final int CANCELLED = 2;

    /** Cancels work the FX thread has not taken, and says which happened. */
    private static String cancel(AtomicInteger state) {
        return state.compareAndSet(PENDING, CANCELLED) ? "Nothing was written."
                : "It was already running and may still land; read the result before retrying.";
    }
}
