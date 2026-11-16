// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import com.botmaker.plugin.api.value.Link;
import com.botmaker.sdk.api.text.Words;

/**
 * An input of an app found in the screen's tree, not by its picture — {@code Element.byId("title").text(Texts.BATTERY)}:
 * a constant of the bot's {@code Elements}, as a picture is one of {@code Pictures} (2026-10-31).
 *
 * <p>An element says how it is found, and what kind of input it is only where Android states it reliably: a text
 * field or a password's ({@link #field}, {@link #password}), never a button, as a view's class cannot be trusted there
 * (a Compose screen's tabs are plain views). It is found by clues, every one it names holding of the same thing on
 * screen: its {@link #id}, its {@link #label} (what a screen reader says), its own {@link #text}, the text it is
 * {@link #holding}, the words it is {@link #containing}, whether it is a field, the element it is {@link #inside}, the
 * one it is right {@link #below}, and last its {@link #index} among what the other clues find. 🗺 Screens names a
 * picked input with the fewest clues that find only it.
 *
 * <p>Words are the bot's {@code Texts}, matched whatever their case and the spaces or marks at their ends, as a
 * {@link Caption}'s are, but letter for letter: the tree states its text, nothing is misread. {@link #containing} is
 * the one clue that matches part of a text, for a page whose sentences change around the words that tell it. Where it
 * is on screen and what its view is are never saved: they change with the device.
 *
 * <p>An element found more than once where its input is not {@code repeated} stops the run: it never takes the
 * first one.
 *
 * @param id         its resource id, without the app's own package ({@code "title"} for {@code android:id/title}); the
 *                   full id ({@code "com.lib:id/chip"}) for one of another package; null when not a clue
 * @param label      what a screen reader says for it, an icon's description; null when not a clue
 * @param text       its own text; null when not a clue
 * @param holding    a text one of its descendants shows: the row that holds the word; null when not a clue
 * @param containing words its own text or label holds, case, spaces and apostrophes aside: the words' node, or the
 *                   tappable row just around them; null when not a clue
 * @param isField    whether it is a text field Android does not mark a password's
 * @param isPassword whether it is a field Android marks a password's
 * @param inside     the element it sits in, declared above it in {@code Elements}; null when anywhere
 * @param below      the element it is the nearest one under, overlapping it across; null when anywhere
 * @param index      which of what the other clues find, in the tree's order, from 0; null when it is the one found
 * @param shot       a small picture of it, cut when it was picked, that Studio shows; a run never matches it; null for
 *                   none
 */
public record Element(String id, Words label, Words text, Words holding, Words containing, boolean isField,
                      boolean isPassword, Element inside, Element below, Integer index, String shot) {

    public Element {
        if (id != null && id.isBlank()) id = null;
        if (id == null && label == null && text == null && holding == null && containing == null && !isField
                && !isPassword) {
            throw new IllegalArgumentException("An element needs an id, a label, words or a field to be found by");
        }
        if (isField && isPassword) {
            throw new IllegalArgumentException("An element is a text field or a password's, not both");
        }
        if (index != null && index < 0) throw new IllegalArgumentException("An element's index starts at 0");
    }

    /** The element whose resource id is {@code id}. */
    public static Element byId(String id) {
        return new Element(requiredId(id), null, null, null, null, false, false, null, null, null, null);
    }

    /** The element a screen reader calls {@code label}: an icon with no text. */
    public static Element byLabel(Words label) {
        return new Element(null, required(label, "label"), null, null, null, false, false, null, null, null, null);
    }

    /** The element whose own text is {@code text}. */
    public static Element byText(Words text) {
        return new Element(null, null, required(text, "text"), null, null, false, false, null, null, null, null);
    }

    /** The element holding {@code holding} in one of its descendants: a row, by the words in it. */
    public static Element byHolding(Words holding) {
        return new Element(null, null, null, required(holding, "holding"), null, false, false, null, null, null, null);
    }

    /** The element whose own text or label holds {@code containing}, or the tappable row just around it. */
    public static Element byContaining(Words containing) {
        return new Element(null, null, null, null, required(containing, "containing"), false, false, null, null, null,
                null);
    }

    /** A text field Android does not mark a password's: the screen's one, or told by the other clues. */
    public static Element textField() {
        return new Element(null, null, null, null, null, true, false, null, null, null, null);
    }

    /** A field Android marks a password's. */
    public static Element passwordField() {
        return new Element(null, null, null, null, null, false, true, null, null, null, null);
    }

    /** This element, with the resource id {@code id} too. */
    @Link
    public Element id(String id) {
        return new Element(requiredId(id), label, text, holding, containing, isField, isPassword, inside, below,
                index, shot);
    }

    /** This element, called {@code label} too. */
    @Link
    public Element label(Words label) {
        return new Element(id, required(label, "label"), text, holding, containing, isField, isPassword, inside,
                below, index, shot);
    }

    /** This element, showing {@code text} too. */
    @Link
    public Element text(Words text) {
        return new Element(id, label, required(text, "text"), holding, containing, isField, isPassword, inside,
                below, index, shot);
    }

    /** This element, holding {@code holding} too. */
    @Link
    public Element holding(Words holding) {
        return new Element(id, label, text, required(holding, "holding"), containing, isField, isPassword, inside,
                below, index, shot);
    }

    /** This element, its own text or label holding {@code containing} too. */
    @Link
    public Element containing(Words containing) {
        return new Element(id, label, text, holding, required(containing, "containing"), isField, isPassword,
                inside, below, index, shot);
    }

    /** This element, a text field Android does not mark a password's. */
    @Link
    public Element field() {
        return new Element(id, label, text, holding, containing, true, false, inside, below, index, shot);
    }

    /** This element, a field Android marks a password's. */
    @Link
    public Element password() {
        return new Element(id, label, text, holding, containing, false, true, inside, below, index, shot);
    }

    /**
     * This element, inside {@code container}: the "Network &amp; internet" of the list, not of the page beside it.
     * {@code container} is declared above this one: one declared below is still null here.
     */
    @Link
    public Element inside(Element container) {
        return new Element(id, label, text, holding, containing, isField, isPassword, required(container, "inside"),
                below, index, shot);
    }

    /** This element, the nearest one under {@code neighbour}, declared above this one. */
    @Link
    public Element below(Element neighbour) {
        return new Element(id, label, text, holding, containing, isField, isPassword, inside,
                required(neighbour, "below"), index, shot);
    }

    /** This element, the {@code index}-th, from 0, of what its other clues find: it breaks when the list changes. */
    @Link
    public Element index(int index) {
        return new Element(id, label, text, holding, containing, isField, isPassword, inside, below, index, shot);
    }

    /** This element, shown in Studio as the picture at {@code path}. */
    @Link
    public Element shot(String path) {
        return new Element(id, label, text, holding, containing, isField, isPassword, inside, below, index, path);
    }

    /**
     * What a window or a tool calls it: its words in quotes ({@code … "x" …} when it contains them), else its id; the
     * field it is; then where it is. Two elements that find differently are shown differently.
     */
    public String shown() {
        Words words = words();
        String kind = isPassword ? "password field" : isField ? "text field" : null;
        boolean contained = text == null && label == null && holding == null;
        String said = words != null ? (contained ? "… \""+ words.text() + "\" …" : "\"" + words.text() + "\"")
                : id;
        String shown = said == null ? kind == null ? "element" : kind : kind == null ? said : said + " " + kind;
        if (inside != null) shown += " inside " + inside.shown();
        if (below != null) shown += " below " + below.shown();
        return index == null ? shown : shown + " #" + index;
    }

    /**
     * The words that tell this element, its first words' clue: its text, else its label, what it holds, then what it
     * contains; null when none.
     */
    public Words words() {
        return text != null ? text : label != null ? label : holding != null ? holding : containing;
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
