package com.botmaker.sdk.authoring;


/**
 * The one checked exception {@link Authoring} throws: this jar cannot author for the version it was asked
 * about, or cannot author the model it was handed.
 *
 * <p>Checked on purpose. Every caller is an editor with a user in front of it, and the only correct response
 * is to <em>say so</em> — the message is written for that user, not for a log line. An unchecked exception
 * here would be caught by whatever generic handler the editor already has and reported as an internal error,
 * which is precisely the wrong story: nothing is broken, the two versions simply do not line up.
 *
 * <h2>The message is the product</h2>
 *
 * <p>{@link #getMessage()} is shown verbatim. Write it as a sentence naming both ends and the way out —
 * "This bot pins SDK 1.9.0; this build of BotMaker knows up to 1.2.0. Update BotMaker to open it." —
 * never as a stack-trace fragment, and never as a bare version number.
 *
 * <p>It is <b>not</b> the exception for a malformed file or a missing directory. Those are
 * {@link java.io.IOException} and stay that way: the caller can retry them, and a user can fix them.
 */
public class AuthoringUnsupported extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthoringUnsupported(String message) {
        super(message);
    }

    public AuthoringUnsupported(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * The standard refusal for a pin this build does not know — the overwhelmingly common case, phrased
     * once so every caller reports it the same way.
     */
    public static AuthoringUnsupported unknownVersion(String pin) {
        return new AuthoringUnsupported("This bot pins SDK " + pin + ", which this build of BotMaker does "
                + "not know how to work with — it knows up to " + SdkVersion.latest().id()
                + ". Update BotMaker, or pin the bot to an SDK it knows.");
    }
}
