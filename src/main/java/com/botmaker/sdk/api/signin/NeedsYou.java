package com.botmaker.sdk.api.signin;

import java.util.Arrays;

/** What a sign-in run asks of the user through its host ({@link SignIn#needsYou}). */
public enum NeedsYou {
    /** Approve the sign-in on the phone Google sent a prompt to. */
    PHONE("phone", "Approve the sign-in on your phone: Google sent it a prompt."),
    /** Type the code Google asks, in BotMaker's prompt. */
    CODE("code", "Type the code Google asks for, in BotMaker's prompt."),
    /** Solve the captcha Google shows, on the instance's screen. */
    CAPTCHA("captcha", "Google shows a captcha: answer it on the instance's screen."),
    /** Look at a page the bot doesn't know, on the instance's screen. */
    LOOK("look", "A screen BotMaker doesn't know: answer it on the instance's screen."),
    /** One a newer bot sent; shown as {@link #LOOK}. */
    UNKNOWN("unknown", "A screen BotMaker doesn't know: answer it on the instance's screen.");

    private final String id;
    private final String displayName;

    NeedsYou(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** Its id on the wire between the run and its host. */
    public String id() {
        return id;
    }

    /** The words the host shows the user. */
    public String displayName() {
        return displayName;
    }

    /** What {@code id} names; {@link #UNKNOWN} for any other, never a throw. */
    public static NeedsYou fromId(String id) {
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst().orElse(UNKNOWN);
    }
}
