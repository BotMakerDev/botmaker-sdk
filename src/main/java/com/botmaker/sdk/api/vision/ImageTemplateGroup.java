package com.botmaker.sdk.api.vision;


import com.botmaker.plugin.api.palette.Hidden;
import com.botmaker.plugin.api.palette.Palette;

import java.util.List;

/**
 * An ordered collection of {@link ImageTemplate}s treated as a single value.
 *
 * <p>Lets the finder/clicker act on several templates at once: {@code find}/{@code click} over a
 * group keep first-match semantics (the first template, in order, that clears the confidence
 * threshold wins — cheap short-circuit), while {@link ImageFinder#findBest(ImageTemplateGroup)} /
 * {@link ImageClicker#clickBest(ImageTemplateGroup)} evaluate every template and pick the single
 * highest-scoring match. A group also serves as the "good" or "bad" set for the compare API
 * ({@link ImageFinder#findCompare}).
 *
 * <p><b>An empty group is legal and means "nothing to look for".</b> Every operation over one reports
 * not-found without taking a capture, and the lambda helpers ({@code ifFind*}, {@code whileFind*},
 * {@code untilFind*}) return immediately without running their action — including the {@code …All}
 * ones, where "all of nothing is present" would otherwise be vacuously true and loop forever. This is
 * what lets a generated scaffold ship the real loop with the group still to be filled in: the bot
 * behaves as if the block weren't there until the first template is added. Constructing one used to
 * throw {@link IllegalArgumentException}, which made that scaffold impossible.
 *
 * <p>Immutable: the backing list is copied and unmodifiable.
 */
// The scaffold's Popups declares one (POPUPS) — see the paragraph above about the empty group.
//
// Curated for the palette: both `of` factories and `isEmpty` are offered; `toArray` is not. It exists so the
// varargs matchers can be reached from a group, which is plumbing between two SDK classes — the palette has no
// reason to teach a bot author to hold an ImageTemplate[]. It stays public for the one who wants it.
@Palette(category = "vision", categoryLabel = "Vision", order = 96)
@Hidden("a value type: a bot holds a group and passes it on, it does not build one from a menu")
public record ImageTemplateGroup(List<ImageTemplate> templates) {

    public ImageTemplateGroup {
        templates = List.copyOf(templates); // rejects null (list and elements), produces an unmodifiable copy
    }

    public static ImageTemplateGroup of(ImageTemplate... templates) {
        return new ImageTemplateGroup(List.of(templates));
    }

    public static ImageTemplateGroup of(List<ImageTemplate> templates) {
        return new ImageTemplateGroup(templates);
    }

    /** Whether this group holds no templates — see the class note on what every operation then does. */
    public boolean isEmpty() {
        return templates.isEmpty();
    }

    /** The templates as an array, for delegating to the {@code ImageTemplate...} finder methods. */
    public ImageTemplate[] toArray() {
        return templates.toArray(new ImageTemplate[0]);
    }
}
