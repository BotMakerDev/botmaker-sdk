// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.internal.emulator.ReadableSource;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A form's rule checked against what its fields hold in the tree, and unknown where no tree tells. */
class FieldTest {

    private static final Supplier<Button> EMAIL = () -> Button.of(Element.byId("email"));
    private static final Supplier<Button> PASSWORD = () -> Button.of(Element.byId("password"));
    private static final Supplier<Button> AGE = () -> Button.of(Element.byId("age"));
    private static final Supplier<Button> NOWHERE = () -> Button.of(Element.byId("nowhere"));

    @AfterEach
    void unpin() {
        Source.set(null);
    }

    @Test
    void theSignUpRuleHoldsOnlyOnceTheEmailIsValidAndThePasswordLongEnough() {
        Condition enabled = Condition.all(Field.valid(EMAIL, Field.Kind.EMAIL), Field.atLeast(PASSWORD, 8));
        Condition message = Field.lacks(EMAIL, "@");

        show(form("probe.example.com", "Probe-pass-1234", ""));
        assertEquals(Answer.NO, enabled.answer());
        assertEquals(Answer.YES, message.answer());

        show(form("probe@example.com", "short", ""));
        assertEquals(Answer.NO, enabled.answer());
        assertEquals(Answer.NO, message.answer());

        show(form("probe@example.com", "••••••••", "18"));
        assertEquals(Answer.YES, enabled.answer(), "a password's bullets count as its characters");
        assertEquals(Answer.UNKNOWN, Field.holds(PASSWORD, "1").answer(), "but they are not its characters");
        assertEquals(Answer.UNKNOWN, Field.lacks(PASSWORD, "1").answer());
        assertEquals(Answer.YES, Field.inRange(AGE, 18, 120).answer());
        assertEquals(Answer.YES, Field.filled(AGE).answer());
    }

    @Test
    void aCheckIsUnknownWhereItsTextCannotBeReadAndAnUnknownClosesNothing() {
        Field filled = Field.filled(EMAIL);
        show(form("probe@example.com", "", ""));
        assertEquals(Answer.UNKNOWN, Field.filled(NOWHERE).answer(), "not on the page");
        assertEquals(Answer.NO, Field.filled(PASSWORD).answer());
        assertFalse(Field.filled(PASSWORD).getAsBoolean());
        assertTrue(Field.filled(NOWHERE).getAsBoolean(), "an unknown closes nothing");
        assertEquals(Answer.UNKNOWN, Field.filled(() -> null).answer(), "a sibling not made yet");

        Source.set(new Window());
        assertEquals(Answer.UNKNOWN, filled.answer(), "a game window shows no tree");
        show(null);
        assertEquals(Answer.UNKNOWN, filled.answer(), "the tree could not be read");
        show(new UiTree(List.of(node("", "", node("email", "a"), node("email", "b")))));
        assertEquals(Answer.UNKNOWN, filled.answer(), "found twice");
    }

    @Test
    void eachKindIsJudgedAsABrowserJudgesItsValue() {
        Field email = Field.valid(EMAIL, Field.Kind.EMAIL);
        assertEquals(Answer.YES, email.says("a@b"));
        assertEquals(Answer.NO, email.says("probe@"));
        assertEquals(Answer.NO, email.says(""), "empty is no valid email");
        assertEquals(Answer.YES, email.says(" a@b.com "), "a browser drops an email's spaces at its ends");
        Field url = Field.valid(EMAIL, Field.Kind.URL);
        assertEquals(Answer.YES, url.says("https://example.com/"));
        assertEquals(Answer.NO, url.says("probe"));
        Field number = Field.valid(AGE, Field.Kind.NUMBER);
        assertEquals(Answer.YES, number.says("-1.5e3"));
        for (String no : new String[] {"NaN", "12d", "+5", "5.", " 120 ", "1e999"}) {
            assertEquals(Answer.NO, number.says(no), no + " is no number a browser takes");
        }
        Field adult = Field.inRange(AGE, 18, Double.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> Field.inRange(AGE, 18, Double.POSITIVE_INFINITY),
                "an open side is MAX_VALUE, which the bot's Java writes back");
        assertEquals(Answer.NO, adult.says("17"));
        assertEquals(Answer.YES, adult.says("120"));
        assertEquals(Answer.NO, adult.says("eighteen"));
        assertThrows(IllegalArgumentException.class, () -> Field.valid(EMAIL, Field.Kind.UNKNOWN));
        assertThrows(IllegalArgumentException.class, () -> new Field.AtLeast(PASSWORD, 0), "the record checks too");
        assertEquals(Answer.YES, Field.atLeast(PASSWORD, 2).says("😀😀"), "characters, not UTF-16 units");
        assertEquals(Answer.NO, Field.atLeast(PASSWORD, 3).says("😀😀"));

        assertEquals(Field.Kind.EMAIL, Field.Kind.fromId(" Email "));
        assertEquals(Field.Kind.UNKNOWN, Field.Kind.fromId("unknown"));
        assertEquals(Field.Kind.UNKNOWN, Field.Kind.fromId(null));
        assertThrows(IllegalArgumentException.class, () -> Field.inRange(AGE, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> Field.holds(EMAIL, ""));
        assertThrows(IllegalArgumentException.class, () -> Field.atLeast(PASSWORD, 0));
    }

    private static UiTree form(String email, String password, String age) {
        return new UiTree(List.of(node("", "", node("email", email), node("password", password), node("age", age))));
    }

    private static UiNode node(String id, String text, UiNode... children) {
        return new UiNode(id, text, "", "web:textbox", "", new Rectangle(0, 0, 10, 10), Set.of(), List.of(children));
    }

    private static void show(UiTree tree) {
        Source.set(new Page(Optional.ofNullable(tree)));
    }

    /** A page whose tree is {@code tree}. */
    private record Page(Optional<UiTree> tree) implements CaptureSource, ReadableSource {
        @Override
        public BufferedImage capture() {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        }

        @Override
        public Point origin() {
            return new Point();
        }
    }

    /** A game window: no tree. */
    private record Window() implements CaptureSource {
        @Override
        public BufferedImage capture() {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        }

        @Override
        public Point origin() {
            return new Point();
        }
    }
}
