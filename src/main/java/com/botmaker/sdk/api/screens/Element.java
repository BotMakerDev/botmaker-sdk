package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.text.Words;

/**
 * An input of an app found in the screen's tree, not by its picture — {@code Element.byId("title").text(Texts.BATTERY)}:
 * a constant of the bot's {@code Elements}, as a picture is one of {@code Pictures} (2026-10-31).
 *
 * <p>An element says only how it is found, never what kind of input it is: a view's class cannot be trusted (a
 * Compose screen's tabs are plain views). It is found by clues, every one it names holding of the same thing on
 * screen: its {@link #id}, its {@link #label} (what a screen reader says), its own {@link #text}, the text it is
 * {@link #holding}, the element it is {@link #inside}, the one it is right {@link #below}, and last its {@link #index}
 * among what the other clues find. 🗺 Screens names a picked input with the fewest clues that find only it.
 *
 * <p>Words are the bot's {@code Texts}, matched whatever their case and the spaces or marks at their ends, as a
 * {@link Caption}'s are, but letter for letter: the tree states its text, nothing is misread. Where it is on screen and what its view is are never saved: they change with the device.
 *
 * <p>An element found more than once where its input is not {@code repeated} stops the run: it never takes the
 * first one.
 *
 * @param id      its resource id, without the app's own package ({@code "title"} for {@code android:id/title}); the
 *                full id ({@code "com.lib:id/chip"}) for one of another package; null when not a clue
 * @param label   what a screen reader says for it, an icon's description; null when not a clue
 * @param text    its own text; null when not a clue
 * @param holding a text one of its descendants shows: the row that holds the word; null when not a clue
 * @param inside  the element it sits in, declared above it in {@code Elements}; null when anywhere
 * @param below   the element it is the nearest one under, overlapping it across; null when anywhere
 * @param index   which of what the other clues find, in the tree's order, from 0; null when it is the one found
 * @param shot    a small picture of it, cut when it was picked, that Studio shows; a run never matches it; null for
 *                none
 */
public record Element(String id, Words label, Words text, Words holding, Element inside, Element below,
                      Integer index, String shot) {

    public Element {
        if (id != null && id.isBlank()) id = null;
        if (id == null && label == null && text == null && holding == null) {
            throw new IllegalArgumentException("An element needs an id, a label or words to be found by");
        }
        if (index != null && index < 0) throw new IllegalArgumentException("An element's index starts at 0");
    }

    /** The element whose resource id is {@code id}. */
    public static Element byId(String id) {
        return new Element(requiredId(id), null, null, null, null, null, null, null);
    }

    /** The element a screen reader calls {@code label}: an icon with no text. */
    public static Element byLabel(Words label) {
        return new Element(null, required(label, "label"), null, null, null, null, null, null);
    }

    /** The element whose own text is {@code text}. */
    public static Element byText(Words text) {
        return new Element(null, null, required(text, "text"), null, null, null, null, null);
    }

    /** The element holding {@code text} in one of its descendants: a row, by the words in it. */
    public static Element byHolding(Words text) {
        return new Element(null, null, null, required(text, "holding"), null, null, null, null);
    }

    /** This element, with the resource id {@code id} too. */
    public Element id(String id) {
        return new Element(requiredId(id), label, text, holding, inside, below, index, shot);
    }

    /** This element, called {@code label} too. */
    public Element label(Words label) {
        return new Element(id, required(label, "label"), text, holding, inside, below, index, shot);
    }

    /** This element, showing {@code text} too. */
    public Element text(Words text) {
        return new Element(id, label, required(text, "text"), holding, inside, below, index, shot);
    }

    /** This element, holding {@code text} too. */
    public Element holding(Words text) {
        return new Element(id, label, this.text, required(text, "holding"), inside, below, index, shot);
    }

    /**
     * This element, inside {@code container}: the "Network &amp; internet" of the list, not of the page beside it.
     * {@code container} is declared above this one: one declared below is still null here.
     */
    public Element inside(Element container) {
        return new Element(id, label, text, holding, required(container, "inside"), below, index, shot);
    }

    /** This element, the nearest one under {@code neighbour}, declared above this one. */
    public Element below(Element neighbour) {
        return new Element(id, label, text, holding, inside, required(neighbour, "below"), index, shot);
    }

    /** This element, the {@code index}-th, from 0, of what its other clues find: it breaks when the list changes. */
    public Element index(int index) {
        return new Element(id, label, text, holding, inside, below, index, shot);
    }

    /** This element, shown in Studio as the picture at {@code path}. */
    public Element shot(String path) {
        return new Element(id, label, text, holding, inside, below, index, path);
    }

    private static String requiredId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("An element's id is blank");
        return id;
    }

    private static <T> T required(T clue, String name) {
        if (clue == null) {
            throw new IllegalArgumentException("An element's " + name + " is null: an element it names is declared"
                    + " below it, or a text is missing");
        }
        return clue;
    }
}
