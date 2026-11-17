// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.api.signin.Typed;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.params.RecordPartTest.Login;
import com.botmaker.sdk.internal.params.RecordPartTest.SignInParams;
import com.botmaker.sdk.internal.params.RunParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A field typing a parameter: a value typed by the run as text, a secret only ever handed to the door by name. */
class ParamTypingTest {

    private static final Rect FIELD = new Rect(10, 10, 40, 10);

    /** A game noting every press and every string typed into it. */
    private static final class Field implements Reach {
        final List<String> done = new ArrayList<>();

        @Override
        public Optional<BufferedImage> look() {
            return Optional.empty();
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage shown) {
            return Optional.empty();
        }

        @Override
        public void drag(Point from, Point to, Duration time) {
            done.add("drag");
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {
            done.add("press");
        }

        @Override
        public void keys(Combo combo) {
            done.add("keys " + combo);
        }

        @Override
        public void type(String text, boolean clears, boolean submits) {
            done.add("type " + text + (submits ? " ⏎" : ""));
        }

        @Override
        public void scroll(Point at, int notches) {
            done.add("scroll");
        }

        @Override
        public long nowMs() {
            return 0;
        }

        @Override
        public void pause(long ms) {
        }
    }

    private final Field game = new Field();
    private final List<String> doorAsked = new ArrayList<>();
    private final ParamTyping.Door door = (secret, field) -> {
        doorAsked.add(secret.name() + " at " + field);
        return Typed.typed();
    };

    @BeforeEach
    void dealt() {
        RunParams.groups(List.of(SignInParams.class));
        SignInParams.dealt = new Login("farm@example.com", Secret.of("farm password"), Secret.of(""));
    }

    @AfterEach
    void forget() {
        SignInParams.dealt = null;
        RunParams.groups(List.of());
    }

    @Test
    void aValueIsTypedAsText() {
        Button email = Button.of(new ImageTemplate("images/email.png")).typesParameter(Login::email).submitting();
        assertTrue(ParamTyping.press(game, email, FIELD, door).isTyped());
        assertEquals(List.of("press", "type farm@example.com ⏎"), game.done);
        assertEquals(List.of(), doorAsked);
    }

    @Test
    void aSecretOnlyGoesThroughTheDoorByName() {
        Button password = Button.of(new ImageTemplate("images/password.png")).typesSecret(Login::password).submitting();
        assertTrue(ParamTyping.press(game, password, FIELD, door).isTyped());
        assertEquals(List.of("farm password at " + FIELD), doorAsked);
        assertTrue(game.done.stream().noneMatch(each -> each.startsWith("type")), "the run types no secret: " + game.done);
        assertTrue(game.done.getLast().startsWith("keys"), "Enter after: " + game.done);
    }

    @Test
    void aBlankValuePressesNothing() {
        Button code = Button.of(new ImageTemplate("images/code.png")).typesSecret(Login::code);
        Typed typed = ParamTyping.press(game, code, FIELD, door);
        assertFalse(typed.isTyped());
        assertEquals(ParamTyping.NO_VALUE, typed.why());
        SignInParams.dealt = null;
        Button email = Button.of(new ImageTemplate("images/email.png")).typesParameter(Login::email);
        assertEquals(ParamTyping.NO_VALUE, ParamTyping.press(game, email, FIELD, door).why());
        assertEquals(List.of(), game.done);
        assertEquals(List.of(), doorAsked);
    }

    @Test
    void aSecretIsNeverTypedAsText() {
        Button password = Button.of(new ImageTemplate("images/password.png")).typesParameter(Login::password);
        Typed typed = ParamTyping.press(game, password, FIELD, door);
        assertFalse(typed.isTyped());
        assertTrue(typed.why().contains("typesSecret"), typed.why());
        assertEquals(List.of(), game.done);
    }

    @Test
    void aPartNoParameterHoldsIsRefusedWithItsWords() {
        RunParams.groups(List.of());
        Button email = Button.of(new ImageTemplate("images/email.png")).typesParameter(Login::email);
        Typed typed = ParamTyping.press(game, email, FIELD, door);
        assertTrue(typed.why().contains("no parameter of that type"), typed.why());
        assertTrue(ParamTyping.dealt(email).isEmpty(), "Discovery leaves it untried");
        assertEquals(List.of(), game.done);
    }

    @Test
    void theDoorsRefusalIsTheFieldsAndEnterIsNotPressed() {
        Button password = Button.of(new ImageTemplate("images/password.png")).typesSecret(Login::password).submitting();
        Typed typed = ParamTyping.press(game, password, FIELD, ParamTyping.door());
        assertFalse(typed.isTyped());
        assertTrue(typed.why().contains("farm password"), typed.why());
        assertEquals(List.of(), game.done);
    }
}
