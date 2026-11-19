// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.internal.emulator.ReadableSource;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;

import java.util.List;
import java.util.Optional;

/** What a text field holds now, for a {@link com.botmaker.sdk.api.screens.Field} check: read off the run's tree. */
public final class FieldText {

    /**
     * A field's text as the tree states it.
     *
     * @param text   its text, a website's field its value
     * @param masked whether it is a password's bullets, as many as its characters but not them
     */
    public record Read(String text, boolean masked) {}

    private FieldText() {}

    /**
     * {@code input}'s text in the tree the run's source shows now; empty when the input is found by no element, the
     * source shows no tree, the tree could not be read, or the element is not found there exactly once.
     */
    public static Optional<Read> now(Button input) {
        Element element = input == null ? null : input.element();
        if (element == null) return Optional.empty();
        if (!(Source.current().base() instanceof ReadableSource source)) return Optional.empty();
        return source.tree().flatMap(tree -> in(tree, element));
    }

    /** {@code element}'s text in {@code tree}; empty unless it is found exactly once. */
    public static Optional<Read> in(UiTree tree, Element element) {
        List<UiNode> found = ElementMatch.matches(element, tree);
        if (found.size() != 1) return Optional.empty();
        UiNode node = found.getFirst();
        return Optional.of(new Read(node.text(), node.has(Trait.PASSWORD) || bullets(node.text())));
    }

    /** Whether {@code text} is only a password's bullets, as a page's tree states a password field's value. */
    private static boolean bullets(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> c == '•' || c == '●' || c == '∙');
    }
}
