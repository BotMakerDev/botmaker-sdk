package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.text.Words;

/**
 * What a text field does once its {@link Button} is clicked: its text typed, after the field is cleared when it
 * {@code clears}, and Enter pressed after when it {@code submits} (2026-10-22).
 *
 * @param text    what it types, one of the bot's {@code Texts}; null when the body says, {@code Navigate.type}
 * @param submits whether Enter is pressed once it is typed
 * @param clears  whether what the field holds is selected first (Ctrl+A), so the text takes its place
 */
public record Typing(Words text, boolean submits, boolean clears) {
}
