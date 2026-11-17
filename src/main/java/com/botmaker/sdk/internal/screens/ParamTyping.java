// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.api.signin.Typed;

import java.util.Optional;

/**
 * A field typing a parameter (2026-11-17, spec {@code 2026-11-17-google-sign-in-map-design.md} §2): its value as this
 * run reads it, typed as any text, or the {@link Secret} it names, typed by BotMaker behind a {@link Door}, the run
 * handing it only the secret's name and where. A parameter holding nothing presses nothing.
 */
public final class ParamTyping {

    /** Why a field typing a parameter was not pressed: the run holds no value of it. */
    public static final String NO_VALUE = "no value dealt";

    /** What types a secret: BotMaker taps {@code field} and types what {@code secret} names. Never the value. */
    @FunctionalInterface
    public interface Door {
        Typed type(Secret secret, Rect field);
    }

    private ParamTyping() {}

    /** The door of this run's host. None yet: the sign-in door takes its place (plan phase 4). */
    public static Door door() {
        return (secret, field) -> Typed.refused("nothing types this run's secrets: start it from BotMaker, which keeps \""
                + secret.name() + "\"");
    }

    /** Whether {@code button} types a parameter, its value or the secret it names. */
    public static boolean typesParameter(Button button) {
        return button.typedParameter() != null;
    }

    /**
     * The value {@code button}'s parameter holds as this run reads it; empty when it is none, its text blank (a login
     * not dealt), or it cannot be read.
     */
    public static Optional<Object> dealt(Button button) {
        try {
            return read(button);
        } catch (RuntimeException unread) {
            return Optional.empty();
        }
    }

    /**
     * Why {@code button}, a field typing a parameter, cannot be pressed now: {@link #NO_VALUE}, or the words a part
     * no parameter of the bot holds is refused with; empty when it can.
     */
    public static Optional<String> unready(Button button) {
        try {
            return read(button).isPresent() ? Optional.empty() : Optional.of(NO_VALUE);
        } catch (RuntimeException unread) {
            return Optional.of(unread.getMessage() == null ? NO_VALUE : unread.getMessage());
        }
    }

    private static Optional<Object> read(Button button) {
        @SuppressWarnings("unchecked")
        Params.Of<Object, Object> parameter = (Params.Of<Object, Object>) button.typedParameter();
        if (parameter == null) return Optional.empty();
        return Optional.ofNullable(Params.get(parameter)).filter(value -> !String.valueOf(value).isBlank());
    }

    /**
     * {@code button}, a field typing a parameter, pressed at {@code place}: clicked and its value typed, or its secret
     * handed to {@code door}, Enter pressed after when it submits. Refused, saying why, with no value dealt, a secret
     * field whose parameter is no {@link Secret}, or the door's refusal.
     */
    public static Typed press(Reach reach, Button button, Rect place, Door door) {
        Optional<String> why = unready(button);
        if (why.isPresent()) return Typed.refused(why.get());
        Optional<Object> value = dealt(button);
        if (value.isEmpty()) return Typed.refused(NO_VALUE);
        if (!button.isSecret()) {
            // Its name typed in clear into a game's field would be a password typed wrong, seen by anyone near.
            if (value.get() instanceof Secret) return Typed.refused("its parameter is a secret: typesSecret types it");
            reach.press(reach.pointOn(place), button.gesture(), button.holdTime());
            reach.pause(Reach.FIELD_FOCUS_MS);
            reach.type(String.valueOf(value.get()), button.isClearing(), button.isSubmitting());
            return Typed.typed();
        }
        if (!(value.get() instanceof Secret secret)) return Typed.refused("its parameter holds no secret");
        Typed typed = door.type(secret, place);
        if (typed.isTyped() && button.isSubmitting()) reach.key(Key.ENTER);
        return typed;
    }
}
