package com.botmaker.sdk.plugin.assist;

import com.botmaker.shared.emulator.GoogleAccount;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** setup's Google sign-in: started in the background, said by google_status, stopped or forgotten by google_sign_out. */
class GoogleSignInsTest {

    /** An account whose sign-in waits in the "browser" until {@link #answer} or a cancel. */
    private static class FakeAccount implements GoogleSignIns.Account {
        final CountDownLatch answered = new CountDownLatch(1);
        volatile Optional<String> email = Optional.empty();
        volatile Thread signer;
        volatile boolean signedOut;

        @Override
        public Optional<String> signedIn() {
            return email;
        }

        @Override
        public boolean signingIn() {
            return signer != null;
        }

        @Override
        public GoogleAccount.Result signIn(Consumer<String> progress) {
            signer = Thread.currentThread();
            try {
                progress.accept("Sign in to Google in the browser window that opened, then select I agree.");
                answered.await();
                email = Optional.of("alt@example.com");
                return new GoogleAccount.Result(email, "Signed in to Google as alt@example.com.");
            } catch (InterruptedException e) {
                return new GoogleAccount.Result(Optional.empty(), "Stopped signing in.");
            } finally {
                signer = null;
            }
        }

        @Override
        public void cancelSignIn() {
            Thread waiting = signer;
            if (waiting != null) waiting.interrupt();
        }

        @Override
        public void signOut() {
            signedOut = true;
            email = Optional.empty();
        }

        void answer() {
            answered.countDown();
        }
    }

    @Test
    void aSignInWaitsInTheBackgroundAndGoogleStatusSaysHowItWent() throws Exception {
        FakeAccount account = new FakeAccount();
        GoogleSignIns signIns = new GoogleSignIns(account);

        assertTrue(signIns.start().contains("Ask the user to sign in"));
        await(() -> signIns.status().contains("select I agree"));
        assertTrue(signIns.start().startsWith("A Google sign-in is already waiting"));
        assertTrue(signIns.status().startsWith("No Google account is signed in. A sign-in is waiting"));

        account.answer();
        await(() -> !signIns.status().contains("waiting"));
        assertEquals("Signed in to Google as alt@example.com.", signIns.status());
        assertTrue(signIns.start().startsWith("Already signed in to Google as alt@example.com"),
                "a second sign-in would replace the account unasked");
    }

    @Test
    void signOutStopsASignInWaitingAndForgetsNothing() throws Exception {
        FakeAccount account = new FakeAccount();
        GoogleSignIns signIns = new GoogleSignIns(account);
        signIns.start();
        await(() -> signIns.status().contains("select I agree"));

        assertTrue(signIns.signOut().startsWith("Stopped the sign-in"));
        assertEquals("No Google account is signed in. The last sign-in failed: Stopped signing in.", signIns.status());
        assertFalse(account.signedOut, "stopping a sign-in leaves the account as it was");
        assertTrue(signIns.start().contains("Ask the user to sign in"), "stopped, a sign-in starts again at once");
        signIns.signOut();
    }

    @Test
    void theGameDialogsSignInIsSeenAndStopped() {
        FakeAccount account = new FakeAccount();
        account.signer = new Thread(() -> {});
        GoogleSignIns signIns = new GoogleSignIns(account);

        assertTrue(signIns.start().startsWith("A Google sign-in is already waiting"));
        assertTrue(signIns.status().contains("started in the Game dialog"));
        assertTrue(signIns.signOut().startsWith("Stopped the sign-in"));
        assertFalse(account.signedOut);
    }

    @Test
    void aSignInThatBreaksSaysSoRatherThanWaitingForever() throws Exception {
        GoogleSignIns signIns = new GoogleSignIns(new FakeAccount() {
            @Override
            public GoogleAccount.Result signIn(Consumer<String> progress) {
                throw new IllegalStateException("credentials file locked");
            }
        });

        signIns.start();
        await(() -> signIns.status().contains("failed"));
        assertTrue(signIns.status().contains("credentials file locked"), signIns::status);
    }

    @Test
    void signOutForgetsTheAccount() {
        FakeAccount account = new FakeAccount();
        account.email = Optional.of("alt@example.com");
        GoogleSignIns signIns = new GoogleSignIns(account);

        assertEquals(GoogleAccount.SIGNED_OUT, signIns.signOut());
        assertTrue(account.signedOut);
        assertEquals("No Google account is signed in.", signIns.status());
        assertEquals("No Google account was signed in.", signIns.signOut());
    }

    private static void await(Supplier<Boolean> condition) throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.get()) {
            assertTrue(System.nanoTime() < end, "timed out");
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }
}
