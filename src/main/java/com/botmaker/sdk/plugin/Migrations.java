// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin;

/**
 * The SDK's own migration rules: one {@code @Migrate} method per {@code api} element this release keeps as
 * {@code @Deprecated(forRemoval = true)}, written the way a bot's author writes one (the method's name is the old
 * member's, its parameter types pick the overload, {@code @Migrate.Self} marks the receiver, the owner named by
 * {@code value}, never {@code name}). Studio reads them from the SDK's sources jar and rewrites a bot's uses; a
 * bot's own rule for the same element wins.
 *
 * <p>Removing or reshaping an {@code api} element takes two releases: the first deprecates it and adds its rule
 * here, which this build compiles against the element still present; the next deletes both together. An element
 * nothing replaces carries {@code @Migrate.Dropped} instead and has no rule here ({@code Setup}'s old marker
 * withers, {@code SignIn}'s). A method an input record overrides takes a rule of its own beside {@code Input}'s: a
 * call binds to the record's.
 *
 * <p>2026-11-22: the rules of 2026-11-01 (an input's traits named as their readers are, {@code OcrOptions}'
 * {@code withX(…)}), 2026-11-02 (a press's {@code LOCKED}, {@code FINISHED} and {@code BUSY}) and 2026-11-06
 * ({@code @GameState}) were deleted with their elements, two releases on.
 */
final class Migrations {

    private Migrations() {}
}
