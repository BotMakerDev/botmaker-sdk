package com.botmaker.sdk.api.signin;

/**
 * What the host answered when the run asked it to type a value: typed, or refused with why. Never the value.
 *
 * @param isTyped whether the host tapped the field and typed it
 * @param why     why it refused, empty when typed
 */
public record Typed(boolean isTyped, String why) {

    public Typed {
        why = why == null ? "" : why;
    }

    /** The host typed the value. */
    public static Typed typed() {
        return new Typed(true, "");
    }

    /** The host typed nothing, {@code why} saying so. */
    public static Typed refused(String why) {
        return new Typed(false, why);
    }
}
