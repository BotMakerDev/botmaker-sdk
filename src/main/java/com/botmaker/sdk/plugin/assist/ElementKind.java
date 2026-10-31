package com.botmaker.sdk.plugin.assist;

import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;

import java.util.Optional;

/**
 * What an app's node is, as the picker lists it (spec § Kind, inferred from traits): read from what the tree states
 * of it, its class name only where no trait tells (a field, a slider, an image). A container, a layout with no trait
 * and no words, is no kind: it is never listed, only an anchor a name says an input is inside.
 */
public enum ElementKind {
    BUTTON("Button"),
    TOGGLE("Toggle"),
    TAB("Tab"),
    FIELD("Text field"),
    LIST("Scrolling list"),
    TEXT("Text"),
    ICON("Icon"),
    SLIDER("Slider");

    private final String displayName;

    ElementKind(String displayName) {
        this.displayName = displayName;
    }

    /** How a row names it: {@code Button}, {@code Scrolling list}. */
    public String displayName() {
        return displayName;
    }

    /** {@code node}'s kind; empty for a container. */
    public static Optional<ElementKind> of(UiNode node) {
        String type = simpleName(node.className());
        boolean taps = node.has(Trait.CLICKABLE) || node.has(Trait.LONG_CLICKABLE);
        if (type.endsWith("EditText") || node.has(Trait.PASSWORD)) return Optional.of(FIELD);
        if (type.endsWith("SeekBar") || type.endsWith("Slider")) return Optional.of(SLIDER);
        if (node.has(Trait.SCROLLABLE)) return Optional.of(LIST);
        if (node.has(Trait.CHECKABLE)) return Optional.of(TOGGLE);
        // A tab's class says it (TabView, TabLayout$TabView); a Compose tab is a checkable View, a toggle above.
        if (taps && type.contains("Tab") && !type.contains("Table")) return Optional.of(TAB);
        if (taps) return Optional.of(image(type) && node.text().isEmpty() ? ICON : BUTTON);
        if (!node.text().isEmpty()) return Optional.of(TEXT);
        if (image(type) && !node.label().isEmpty()) return Optional.of(ICON);
        return Optional.empty();
    }

    /** Whether a click on it is a press: what a click on the frame walks up to. */
    static boolean tappable(UiNode node) {
        return node.has(Trait.CLICKABLE) || node.has(Trait.LONG_CLICKABLE) || node.has(Trait.CHECKABLE)
                || of(node).filter(kind -> kind == FIELD || kind == SLIDER).isPresent();
    }

    private static boolean image(String type) {
        return type.endsWith("ImageView") || type.endsWith("ImageButton");
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
