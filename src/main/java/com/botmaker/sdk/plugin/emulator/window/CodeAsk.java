package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.google.InstanceSignIn;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * A code an operation's sign-in waits for, asked in the code dialog (spec {@code 2026-11-14-google-signin-design.md},
 * mockup A): the account, the method and which try it is, with no JavaFX. The answer is the code's characters, or
 * null to leave the screen to the user; never kept as text, never logged.
 *
 * @param operation the operation's id, for the strip's <i>Type the code…</i>
 */
record CodeAsk(String operation, String email, Verification method, int attempt, CompletableFuture<char[]> answer) {

    CodeAsk(String operation, String email, Verification method, int attempt) {
        this(operation, email, method, attempt, new CompletableFuture<>());
    }

    String header() {
        return (method == Verification.BACKUP_CODE ? "Backup code" : "Verification code") + " for " + email;
    }

    /** The method's words, over the field. */
    String words() {
        return method == Verification.BACKUP_CODE ? "One of the account's backup codes. Google takes each one once."
                : "The code Google texted, or the one your authenticator app shows.";
    }

    /** Under the field: what becomes of the code, and which try this is. */
    String hint() {
        return "BotMaker types it on the instance, then clears it: it is never stored. Try " + attempt + " of "
                + InstanceSignIn.CODE_TRIES + ". Cancel leaves the screen to you.";
    }

    /**
     * Hands {@code code} to the sign-in, or null to leave the screen to the user. A code the sign-in stopped waiting
     * for (the operation cancelled) is zeroed here, as nobody else will.
     */
    void answer(char[] code) {
        if (!answer.complete(code) && code != null) Arrays.fill(code, '\0');
    }
}
