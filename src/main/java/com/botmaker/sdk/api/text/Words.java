package com.botmaker.sdk.api.text;

/**
 * A text of the bot's — {@code Words.of("Dragons")}, a constant of its {@code Texts} class as a picture is one of
 * {@code Pictures} (the user's call, 2026-10-22). A text field of the map types it ({@code Button.types(Texts.GUILD)}).
 */
public final class Words {

    private final String text;

    private Words(String text) {
        if (text == null) throw new IllegalArgumentException("Words need their text");
        this.text = text;
    }

    /** {@code text}, as it is typed. */
    public static Words of(String text) {
        return new Words(text);
    }

    /** The text, as it is typed. */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Words other && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return "Words{\"" + text + "\"}";
    }
}
