package com.botmaker.sdk.plugin.settings;

import java.io.IOException;

/**
 * A VM call, which throws the checked exceptions, as work for the toolkit's {@code Async}, which takes a throw's
 * message as the one sentence the window shows.
 */
final class GuestCalls {

    private GuestCalls() {}

    /** A call into a VM or the hypervisor. */
    interface Call<T> {
        T call() throws IOException, InterruptedException;
    }

    /** {@code call}'s answer; its failure as an {@link IllegalStateException} carrying the same sentence. */
    static <T> T unchecked(Call<T> call) {
        try {
            return call.call();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stopped.", e);
        }
    }
}
