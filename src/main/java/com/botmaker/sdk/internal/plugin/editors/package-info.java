/**
 * The SDK's slot editors — the thirteen bot-first controls that stand in for a typed-out expression: a
 * region dragged on the screen instead of {@code new Rect(12, 40, 300, 80)}, a game picked from a cover-art
 * grid instead of a Steam app id, a colour sampled off the running game instead of a hex triple.
 *
 * <p>They were Studio's until 2026-08-27, and their move here is the whole point of the plugin platform:
 * they are editors for <em>the SDK's own types</em>, so the SDK is the thing that should own them. What the
 * host keeps is the parts no plugin can supply — the overlay, the window list, the theme, the naming rules
 * for a project's pictures — and those arrive through {@link com.botmaker.plugin.api.StudioServices}. The
 * test of the split is that nothing here is reached by a back door: every one of these is an ordinary
 * {@link com.botmaker.plugin.api.SlotEditor} that a second plugin could have written.
 *
 * <p><b>Never loaded on a bot's classpath.</b> JavaFX and the widget toolkit are
 * {@code <optional>true</optional>} in this module's pom for exactly that reason — a generated bot is a
 * headless program, and resolving JavaFX for it would fail on a machine that has none.
 *
 * <p>Each editor serves both places the host edits a value, which is what {@link Slots} exists for.
 */
@Internal("editors for the SDK's types, loaded by the editor and never by a bot")
package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.meta.Internal;
