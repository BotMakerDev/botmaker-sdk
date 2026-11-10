package com.botmaker.sdk.plugin.assist;

import com.botmaker.shared.emulator.GoogleAccount;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * {@code setup}'s Google sign-in (2026-11-10, the user's call), the Game dialog's account line for an assistant: it
 * opens the browser window, the user signs in there. {@link GoogleAccount#signIn} blocks for minutes, so it runs on a
 * thread of its own and {@link #status} says how it went. One sign-in waits at a time, the dialog's or this one's
 * ({@link GoogleAccount#signingIn}). What crosses back is the email and goopdl's words, never the token
 * ({@code CLAUDE.md}: no assistant session reads a secret value).
 */
final class GoogleSignIns {

    /** How long sign-out waits for a stopped sign-in to clean up its browser profile. */
    private static final Duration STOPPING = Duration.ofSeconds(10);

    /** The account the sign-ins act on: {@link GoogleAccount}'s, or a test's. */
    interface Account {
        Optional<String> signedIn();

        boolean signingIn();

        GoogleAccount.Result signIn(Consumer<String> progress);

        void cancelSignIn();

        void signOut();
    }

    /** The one this computer's assistant tools use. */
    static final GoogleSignIns SHARED = new GoogleSignIns(new Account() {
        @Override
        public Optional<String> signedIn() {
            return GoogleAccount.signedIn();
        }

        @Override
        public boolean signingIn() {
            return GoogleAccount.signingIn();
        }

        @Override
        public GoogleAccount.Result signIn(Consumer<String> progress) {
            return GoogleAccount.signIn(progress);
        }

        @Override
        public void cancelSignIn() {
            GoogleAccount.cancelSignIn();
        }

        @Override
        public void signOut() {
            GoogleAccount.signOut();
        }
    });

    private final Account account;
    /** This class's sign-in thread, or null. */
    private Thread waiting;
    /** While this class's sign-in waits, its progress; after, why it failed (empty when it worked). */
    private String said = "";

    GoogleSignIns(Account account) {
        this.account = account;
    }

    synchronized String start() {
        Optional<String> email = account.signedIn();
        if (email.isPresent()) {
            return "Already signed in to Google as " + email.get() + ". To use another account, google_sign_out "
                    + "first, then google_sign_in.";
        }
        if (mine() || account.signingIn()) return "A Google sign-in is already waiting in the browser. " + said;
        said = "Starting the sign-in…";
        Thread thread = new Thread(this::signIn, "google-sign-in");
        thread.setDaemon(true);
        waiting = thread;
        thread.start();
        return "The Google sign-in is starting: a Chrome or Edge window opens (goopdl is fetched first when it is "
                + "missing). Ask the user to sign in there, with a secondary account since Google may flag one that "
                + "downloads for many devices, and to select I agree; then setup's google_status says how it went.";
    }

    synchronized String status() {
        String signedIn = account.signedIn().map(email -> "Signed in to Google as " + email + ".")
                .orElse("No Google account is signed in.");
        if (mine()) return signedIn + " A sign-in is waiting in the browser: " + said;
        if (account.signingIn()) return signedIn + " A sign-in started in the Game dialog is waiting in the browser.";
        return said.isEmpty() ? signedIn : signedIn + " The last sign-in failed: " + said;
    }

    /** Stops a waiting sign-in, the dialog's too, or else forgets the account. */
    String signOut() {
        Thread stopping;
        synchronized (this) {
            if (!mine() && !account.signingIn()) {
                boolean was = account.signedIn().isPresent();
                account.signOut();
                said = "";
                return was ? GoogleAccount.SIGNED_OUT : "No Google account was signed in.";
            }
            stopping = waiting;
            account.cancelSignIn();
            if (stopping != null) stopping.interrupt();
        }
        // Outside the lock: the stopping thread says its end through it.
        if (stopping != null) {
            try {
                stopping.join(STOPPING.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return "Stopped the sign-in waiting in the browser; no account was signed in or out.";
    }

    private void signIn() {
        String end;
        try {
            GoogleAccount.Result result = account.signIn(this::heard);
            end = result.ok() ? "" : result.message();
        } catch (RuntimeException | Error e) {
            end = "it broke: " + e;
        }
        heard(end);
    }

    private boolean mine() {
        return waiting != null && waiting.isAlive();
    }

    private synchronized void heard(String message) {
        said = message;
    }
}
