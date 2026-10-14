package com.botmaker.sdk.api.vision;

import java.util.Locale;
import java.util.Objects;

/**
 * A group of the bot's pictures that no activity owns — the "UI" or "Shared" of 🖼 Manage Pictures — declared as a
 * constant of the bot's {@code Tags} class and named by the pictures it holds (2026-10-14):
 *
 * <pre>{@code
 * @SdkValue(SdkValue.Id.TAGS)
 * public final class Tags {
 *     public static final PictureTag UI = PictureTag.named("UI");
 * }
 *
 * public static final ImageTemplate CLOSE_X = new ImageTemplate("…/close_x.png").tags(List.of(Tags.UI));
 * }</pre>
 *
 * <p>A picture an activity uses is filed under that activity with {@link ImageTemplate#activities}, not a tag: the
 * activity's own enum names it, so it follows a rename and goes with the activity. A screen's pictures are read off
 * the bot's {@code Screens} map and are no tag either. A run never reads a tag; it is how the bot's author finds a
 * picture.
 */
public final class PictureTag {

    private final String name;

    private PictureTag(String name) {
        this.name = name;
    }

    /** The tag called {@code name}, as 🖼 Manage Pictures shows it: words, spaces allowed. */
    public static PictureTag named(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A picture tag needs a name");
        return new PictureTag(name.trim().replaceAll("\\s+", " "));
    }

    /** The name 🖼 Manage Pictures shows. */
    public String name() {
        return name;
    }

    /** Two tags are the same when their names are, whatever their case: "UI" and "ui" are one group. */
    @Override
    public boolean equals(Object other) {
        return other instanceof PictureTag that && name.equalsIgnoreCase(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return name;
    }
}
