package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.meta.Migrate;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;

/**
 * The SDK's own migration rules: one {@code @Migrate} method per {@code api} element this release keeps as
 * {@code @Deprecated(forRemoval = true)}, written the way a bot's author writes one (the method's name is the old
 * member's, its parameter types pick the overload, {@code @Migrate.Self} marks the receiver, the owner named by
 * {@code value}, never {@code name}). Studio reads them from the SDK's sources jar and rewrites a bot's uses; a
 * bot's own rule for the same element wins.
 *
 * <p>Removing or reshaping an {@code api} element takes two releases: the first deprecates it and adds its rule
 * here, which this build compiles against the element still present; the next deletes both together. Empty while
 * nothing is deprecated. A method an input record overrides takes a rule of its own beside {@code Input}'s: a
 * call binds to the record's.
 *
 * <p>2026-11-01: an input's traits are named as their readers are, so each is a {@code @Link} the SDK declares
 * once — {@code .leaves()} is {@code .leaving()}, {@code .sometimes()} is {@code .occasional()} (read by
 * {@code isOccasional()}, was {@code comesAndGoes()}), {@code lock()} is {@code locked()}, and a tree's
 * {@code notches()} is {@code wheel()}.
 */
final class Migrations {

    private Migrations() {}

    @Migrate(Input.class)
    static Input leaves(@Migrate.Self Input self) {
        return self.leaving();
    }

    @Migrate(Input.class)
    static Input sometimes(@Migrate.Self Input self) {
        return self.occasional();
    }

    @Migrate(Input.class)
    static boolean comesAndGoes(@Migrate.Self Input self) {
        return self.isOccasional();
    }

    @Migrate(Input.class)
    static ImageTemplate lock(@Migrate.Self Input self) {
        return self.locked();
    }

    @Migrate(Button.class)
    static Button leaves(@Migrate.Self Button self) {
        return self.leaving();
    }

    @Migrate(Button.class)
    static Button sometimes(@Migrate.Self Button self) {
        return self.occasional();
    }

    @Migrate(TextButton.class)
    static TextButton leaves(@Migrate.Self TextButton self) {
        return self.leaving();
    }

    @Migrate(TextButton.class)
    static TextButton sometimes(@Migrate.Self TextButton self) {
        return self.occasional();
    }

    @Migrate(Hotkey.class)
    static Hotkey leaves(@Migrate.Self Hotkey self) {
        return self.leaving();
    }

    @Migrate(Hotkey.class)
    static Hotkey sometimes(@Migrate.Self Hotkey self) {
        return self.occasional();
    }

    @Migrate(Strip.class)
    static Strip leaves(@Migrate.Self Strip self) {
        return self.leaving();
    }

    @Migrate(Strip.class)
    static Strip sometimes(@Migrate.Self Strip self) {
        return self.occasional();
    }

    @Migrate(Tree.class)
    static Tree leaves(@Migrate.Self Tree self) {
        return self.leaving();
    }

    @Migrate(Tree.class)
    static Tree sometimes(@Migrate.Self Tree self) {
        return self.occasional();
    }

    @Migrate(Tree.class)
    static int notches(@Migrate.Self Tree self) {
        return self.wheel();
    }
}
