package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Verdict;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Whether a run exploring on its own may press an input now (spec §4, 2026-11-09). The guards are general, not a
 * game's (the user's call): what is in front (another app, a system dialog), the field under the press (a password,
 * a card), the input's verdict, and the text around it (a currency sign, one of the author's words). Pure over what
 * it is given: the caller reads the screen.
 */
public final class Guards {

    /** Why a press is refused, or {@link #NONE}. */
    public enum Kind { NONE, FOREIGN_WINDOW, SYSTEM_DIALOG, SENSITIVE_FIELD, NO_VERDICT, NEVER, TEXT }

    /** A guard's answer: {@code kind}, and one line saying what it saw. */
    public record Refusal(Kind kind, String why) {
        public Refusal {
            Objects.requireNonNull(kind, "kind");
            why = why == null ? "" : why;
        }

        public boolean refused() {
            return kind != Kind.NONE;
        }
    }

    /** Pressed: nothing in the way. */
    public static final Refusal PRESS = new Refusal(Kind.NONE, "");

    /**
     * What is around a press now.
     *
     * @param text       the text read near the input ({@link #near})
     * @param foreground what is in front and what should be
     * @param at         the screen tree's nodes under the press point, smallest first; empty without a tree
     */
    public record Around(List<TextResult> text, Foreground foreground, List<UiNode> at) {
        public Around {
            text = text == null ? List.of() : List.copyOf(text);
            foreground = foreground == null ? Foreground.UNKNOWN : foreground;
            at = at == null ? List.of() : List.copyOf(at);
        }
    }

    /**
     * Android's own packages, which show a dialog or sheet over an app: permissions, installs, the store and its
     * billing, Google accounts, the share chooser, the system UI.
     */
    static final Set<String> SYSTEM_PACKAGES = Set.of("android", "com.android.systemui",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.vending",
            "com.google.android.gms", "com.android.intentresolver", "com.android.settings");

    /** A field's id, label or text naming payment details; read on fields only, so a game's cards are not one. */
    private static final Pattern PAYMENT = Pattern.compile(
            "card|\\bcc.?(num|no)|cvv|cvc|security.?code|iban|expir|mm\\s*/\\s*yy", Pattern.CASE_INSENSITIVE);

    /** One of the author's words, and what finds it whole. */
    private record Word(String said, Pattern whole) {}

    private final List<Word> words;

    private Guards(List<Word> words) {
        this.words = words;
    }

    /** Guards matching the author's {@code words} too ({@code GUARD_WORDS}), case-blind and whole. */
    public static Guards with(List<String> words) {
        return new Guards(words == null ? List.of() : words.stream()
                .filter(word -> word != null && !word.isBlank())
                .map(String::strip)
                .map(word -> new Word(word, Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word)
                        + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)))
                .toList());
    }

    /**
     * Whether anything may be pressed with {@code around}'s foreground: a press with no input of its own (a way back
     * by the map's close button) is guarded by this alone.
     */
    public Refusal front(Around around) {
        Foreground front = around.foreground();
        if (!front.dialog().isEmpty()) {
            return new Refusal(Kind.SYSTEM_DIALOG, "A system dialog is in front: " + front.dialog() + ".");
        }
        if (front.foreign()) {
            String now = front.inFront().isEmpty() ? "nothing readable" : front.inFront();
            return SYSTEM_PACKAGES.contains(front.inFront())
                    ? new Refusal(Kind.SYSTEM_DIALOG, "A system dialog is in front (" + now + "), not " + front.target() + ".")
                    : new Refusal(Kind.FOREIGN_WINDOW, now + " is in front, not " + front.target() + ".");
        }
        return PRESS;
    }

    /** Whether {@code input} may be pressed with {@code around} as it is, else the first guard in the way. */
    public Refusal check(Input input, Around around) {
        Refusal front = front(around);
        if (front.refused()) return front;
        Optional<String> field = around.at().stream().map(Guards::sensitive).flatMap(Optional::stream).findFirst();
        if (field.isPresent()) return new Refusal(Kind.SENSITIVE_FIELD, field.get());
        Verdict verdict = input.verdict();
        switch (verdict) {
            case Verdict.None ignored -> {
                return new Refusal(Kind.NO_VERDICT, "It has no verdict: say free, spends or never.");
            }
            case Verdict.Never(String why) -> {
                return new Refusal(Kind.NEVER, "It is never pressed: " + why.strip().replaceAll("\\.+$", "") + ".");
            }
            case Verdict.Spends ignored -> {
                return PRESS;
            }
            case Verdict.Free ignored -> {}
        }
        for (TextResult read : around.text()) {
            String text = read.text() == null ? "" : read.text();
            Optional<String> hit = hit(text);
            if (hit.isPresent()) {
                return new Refusal(Kind.TEXT, "\"" + text.strip() + "\" beside it has " + hit.get()
                        + "; it may spend: say spends or never.");
            }
        }
        return PRESS;
    }

    /** What in {@code text} refuses a free press: a currency sign, or one of the author's words. */
    Optional<String> hit(String text) {
        for (int at = 0; at < text.length(); ) {
            int c = text.codePointAt(at);
            if (Character.getType(c) == Character.CURRENCY_SYMBOL) return Optional.of("the currency sign " + Character.toString(c));
            at += Character.charCount(c);
        }
        return words.stream().filter(word -> word.whole().matcher(text).find())
                .map(word -> "the word \"" + word.said() + "\"")
                .findFirst();
    }

    /** Why {@code node} is a field nothing presses — a password, or payment details — or empty. */
    static Optional<String> sensitive(UiNode node) {
        if (node.has(Trait.PASSWORD)) return Optional.of("It is a password field.");
        String type = node.className();
        boolean field = type.endsWith("EditText") || type.endsWith("TextField") || type.endsWith("AutoCompleteTextView");
        String said = (node.id() + " " + node.label() + " " + node.text()).toLowerCase(Locale.ROOT);
        return field && PAYMENT.matcher(said).find()
                ? Optional.of("It is a payment field (" + node.shortId() + ").") : Optional.empty();
    }

    /**
     * The text of {@code read} near {@code box}: inside the box grown by half its width each side and half its height
     * above and below. A box of no size is not known: then all of {@code read}, so nothing is missed.
     */
    public static List<TextResult> near(Rect box, List<TextResult> read) {
        if (box == null || box.width() <= 0 || box.height() <= 0) return List.copyOf(read);
        Rect grown = new Rect(box.x() - box.width() / 2, box.y() - box.height() / 2, box.width() * 2, box.height() * 2);
        return read.stream().filter(text -> text.bounds() != null && grown.overlaps(text.bounds())).toList();
    }
}
