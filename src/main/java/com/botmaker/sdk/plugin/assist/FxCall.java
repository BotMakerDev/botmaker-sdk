package com.botmaker.sdk.plugin.assist;

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
final class FxCall {

    private static final long TIMEOUT_SECONDS = 30;

    private FxCall() {}

    /**
     * {@code work}'s answer, computed on the FX thread; its exception is rethrown here. A call that times out
     * before the FX thread took it is cancelled, so it does not run late and a retry does not write twice; one
     * already running is said to be.
     */
    static <T> T call(Supplier<T> work) {
        if (Platform.isFxApplicationThread()) return work.get();
        CompletableFuture<T> answer = new CompletableFuture<>();
        // PENDING until the FX thread takes it; the waiter may cancel only work that has not started.
        AtomicInteger state = new AtomicInteger(PENDING);
        Platform.runLater(() -> {
            if (!state.compareAndSet(PENDING, RUNNING)) return;
            try {
                answer.complete(work.get());
            } catch (RuntimeException | Error e) {
                answer.completeExceptionally(e);
            }
        });
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

    private static final int PENDING = 0;
    private static final int RUNNING = 1;
    private static final int CANCELLED = 2;

    /** Cancels work the FX thread has not taken, and says which happened. */
    private static String cancel(AtomicInteger state) {
        return state.compareAndSet(PENDING, CANCELLED) ? "Nothing was written."
                : "It was already running and may still land; read the result before retrying.";
    }
}
