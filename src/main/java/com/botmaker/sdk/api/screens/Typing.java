// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.params.Params;
import com.botmaker.sdk.api.text.Words;

/**
 * What a text field does once its {@link Button} is clicked: its text typed, after the field is cleared when it
 * {@code clears}, and Enter pressed after when it {@code submits} (2026-10-22). Its text comes from one place, never
 * two: the bot's {@code text}; a {@code parameter}'s value as this run reads it, or, when {@code secret}, the
 * {@code Secret} it names, typed by BotMaker, never by the run (2026-11-17); with none, the body's.
 *
 * @param text      what it types, one of the bot's {@code Texts}; null when a parameter or the body says
 * @param parameter the parameter whose value it types, {@code Login::email}; null for none
 * @param secret    whether the parameter is a {@code Secret} BotMaker types; false with no parameter
 * @param submits   whether Enter is pressed once it is typed
 * @param clears    whether what the field holds is selected first (Ctrl+A), so the text takes its place
 */
public record Typing(Words text, Params.Of<?, ?> parameter, boolean secret, boolean submits, boolean clears) {

    public Typing {
        // One source: a parameter over the bot's text; no secret without its parameter.
        if (parameter != null) text = null;
        else secret = false;
    }

    /** A field typing the bot's {@code text}, or the body's when null. */
    public Typing(Words text, boolean submits, boolean clears) {
        this(text, null, false, submits, clears);
    }

    /** This typing, its text kept, Enter pressed after as {@code submits} says and cleared first as {@code clears}. */
    public Typing with(boolean submits, boolean clears) {
        return new Typing(text, parameter, secret, submits, clears);
    }
}
