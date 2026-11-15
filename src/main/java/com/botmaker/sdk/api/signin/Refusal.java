package com.botmaker.sdk.api.signin;

import java.util.Arrays;

/** Why Google ended a sign-in short of signing in, as a run tells its host ({@link SignIn#refused}). */
public enum Refusal {
    /** Google has no account with the address typed. */
    NO_SUCH_ACCOUNT("no-such-account", "Google has no such account: check the address in BotMaker's Accounts window."),
    /** Google refused the password typed. */
    WRONG_PASSWORD("wrong-password", "Google refused the password: change it in BotMaker's Accounts window."),
    /** Google refused to sign in on this device at all. */
    REFUSED("refused", "Google refused the sign-in on this device. Try again later, or sign in by hand."),
    /** One a newer bot sent; read as {@link #REFUSED}. */
    UNKNOWN("unknown", "Google refused the sign-in.");

    private final String id;
    private final String displayName;

    Refusal(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Its id on the wire between the run and its host. */
    public String id() {
        return id;
    }

    /** The words the user is shown, which name no account. */
    public String displayName() {
        return displayName;
    }

    /** What {@code id} names; {@link #UNKNOWN} for any other, never a throw. */
    public static Refusal fromId(String id) {
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst().orElse(UNKNOWN);
    }
}
