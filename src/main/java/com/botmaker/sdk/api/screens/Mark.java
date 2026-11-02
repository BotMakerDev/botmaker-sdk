package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.vision.ImageTemplate;

/**
 * A sign on a {@link Screen} that tells it and is never pressed: its title, a frame, words, an app's element. A field
 * of its screen beside its inputs and readouts, {@code public final Mark title = Mark.of(Pictures.SHOP_TITLE);}
 * (2026-11-02).
 *
 * <p>The game shows a screen when every part of it that tells it shows and none it is told apart by does: its marks,
 * and its inputs and readouts that are {@code .marking()} or {@code .absent()}. A part may be shared with other
 * screens, a window's frame with each of its tabs, as long as the set is the screen's own; one screen whose set holds
 * another's is told apart by a part {@link #absent} from it.
 *
 * @param picture  the picture that shows, looked for near where it was cut; null for words or an element
 * @param caption  the words read in their zone; null for a picture or an element
 * @param element  the element the app's tree holds; null for a picture or words
 * @param isAbsent whether it never shows while the game shows its screen, telling it apart from one alike
 */
public record Mark(ImageTemplate picture, Caption caption, Element element, boolean isAbsent) implements ScreenPart {

    /** {@code picture}, showing while the game shows the screen: {@code Mark.of(Pictures.SHOP_TITLE)}. */
    public static Mark of(ImageTemplate picture) {
        if (picture == null) throw new IllegalArgumentException("A mark of a picture needs the picture");
        return new Mark(picture, null, null, false);
    }

    /** {@code caption}'s words, read in their zone while the game shows the screen. */
    public static Mark of(Caption caption) {
        if (caption == null) throw new IllegalArgumentException("A mark of words needs the caption");
        return new Mark(null, caption, null, false);
    }

    /** {@code element}, in the app's tree while it shows the screen. */
    public static Mark of(Element element) {
        if (element == null) throw new IllegalArgumentException("A mark of an element needs the element");
        return new Mark(null, null, element, false);
    }

    /** This mark, telling its screen apart from one alike by never showing on it. */
    @Link
    public Mark absent() {
        return new Mark(picture, caption, element, true);
    }

    /** Whether it tells its screen by showing: every mark that is not {@link #absent}. */
    public boolean isMarking() {
        return !isAbsent;
    }

    /** What a window or a tool calls it: {@code shop_title}, {@code "Shop"} or {@code element "Battery"}, then {@code , absent}. */
    public String label() {
        String what = picture != null ? picture.id() : caption != null ? caption.said()
                : element != null ? "element " + element.shown() : "?";
        return what + (isAbsent ? ", absent" : "");
    }
}
