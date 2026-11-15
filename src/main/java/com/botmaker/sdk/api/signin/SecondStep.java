package com.botmaker.sdk.api.signin;

import java.util.Arrays;

/**
 * How the account of a {@link SignIn} request answers Google's second step, as its owner chose it in BotMaker's
 * Accounts window. The method only, never a code or a key.
 */
public enum SecondStep {
    /** Google's "Tap Yes on your phone": the user approves it. */
    PHONE("phone", "Phone prompt"),
    /** A texted or authenticator code, typed by the host ({@link SignIn#typeCode}). */
    CODE("code", "Code"),
    /** The authenticator app's code, made by the host from the key it holds ({@link SignIn#typeCode}). */
    AUTHENTICATOR("authenticator", "Authenticator"),
    /** One of the account's backup codes, typed by the host ({@link SignIn#typeCode}). */
    BACKUP_CODE("backup-code", "Backup code"),
    /** No preference: the second step is the user's. */
    ASK("ask", "Ask me"),
    /** One a newer BotMaker sent; followed as {@link #ASK}. */
    UNKNOWN("unknown", "Unknown");

    private final String id;
    private final String displayName;

    SecondStep(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Its id on the wire between the run and its host. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** The method {@code id} names; {@link #UNKNOWN} for any other, never a throw. */
    public static SecondStep fromId(String id) {
        return Arrays.stream(values()).filter(step -> step.id.equals(id)).findFirst().orElse(UNKNOWN);
    }
}
