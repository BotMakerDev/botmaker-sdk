package com.botmaker.sdk.plugin;

/**
 * The SDK's own migration rules: one {@code @Migrate} method per {@code api} element this release keeps as
 * {@code @Deprecated(forRemoval = true)}, written the way a bot's author writes one (the method's name is the old
 * member's, its parameter types pick the overload, {@code @Migrate.Self} marks the receiver, the owner named by
 * {@code value}, never {@code name}). Studio reads them from the SDK's sources jar and rewrites a bot's uses; a
 * bot's own rule for the same element wins.
 *
 * <p>Removing or reshaping an {@code api} element takes two releases: the first deprecates it and adds its rule
 * here, which this build compiles against the element still present; the next deletes both together. Empty while
 * nothing is deprecated.
 */
final class Migrations {

    private Migrations() {}
}
