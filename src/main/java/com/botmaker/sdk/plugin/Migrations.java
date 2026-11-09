package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.meta.Migrate;
import com.botmaker.sdk.api.geometry.Anchor;
import com.botmaker.sdk.api.geometry.Zone;
import com.botmaker.sdk.api.params.Facts;
import com.botmaker.sdk.api.params.GameState;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Pressed;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.text.OcrLanguage;
import com.botmaker.sdk.api.text.OcrOptions;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.api.text.Words;
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
 * {@code notches()} is {@code wheel()}. Then the rest of the SDK's values: a field's {@code .submits()} and
 * {@code .clears()} are {@code .submitting()} and {@code .clearing()} (read by {@code isSubmitting()},
 * {@code isClearing()}), its {@code text()} is {@code types()} and {@code typedByBody()} is {@code isTyped()}; a
 * zone's {@code anchor()} is {@code anchored()}; {@code OcrOptions}' {@code withX(…)} is {@code x(…)}; a flow
 * step's {@code description()} is {@code described()} and its {@code screen()} is {@code on()}.
 *
 * <p>2026-11-02: a button's states other than normal, selected and disabled are the bot's own ({@code ButtonState}),
 * so a press's {@code LOCKED}, {@code FINISHED} and {@code BUSY} are one {@code BLOCKED} carrying the look.
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

    @Migrate(Button.class)
    static Button submits(@Migrate.Self Button self) {
        return self.submitting();
    }

    @Migrate(Button.class)
    static Button clears(@Migrate.Self Button self) {
        return self.clearing();
    }

    @Migrate(Button.class)
    static Words text(@Migrate.Self Button self) {
        return self.types();
    }

    @Migrate(Button.class)
    static boolean typedByBody(@Migrate.Self Button self) {
        return self.isTyped();
    }

    @Migrate(Zone.class)
    static Anchor anchor(@Migrate.Self Zone self) {
        return self.anchored();
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withLanguages(@Migrate.Self OcrOptions self, String languages) {
        return self.languages(languages);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withLanguages(@Migrate.Self OcrOptions self, OcrLanguage... languages) {
        return self.languages(languages);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withPageSegMode(@Migrate.Self OcrOptions self, int pageSegMode) {
        return self.pageSegMode(pageSegMode);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withOcrEngineMode(@Migrate.Self OcrOptions self, int ocrEngineMode) {
        return self.ocrEngineMode(ocrEngineMode);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withGrayscale(@Migrate.Self OcrOptions self, boolean grayscale) {
        return self.grayscale(grayscale);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withUpscale(@Migrate.Self OcrOptions self, double upscale) {
        return self.upscale(upscale);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withBinarize(@Migrate.Self OcrOptions self, OcrOptions.BinarizeMode binarize) {
        return self.binarize(binarize);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withInvert(@Migrate.Self OcrOptions self, boolean invert) {
        return self.invert(invert);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withCharWhitelist(@Migrate.Self OcrOptions self, String charWhitelist) {
        return self.charWhitelist(charWhitelist);
    }

    @Migrate(OcrOptions.class)
    static OcrOptions withLevel(@Migrate.Self OcrOptions self, TextResult.Level level) {
        return self.level(level);
    }

    @Migrate(value = Pressed.Result.class, kind = Migrate.Kind.FIELD)
    static Pressed.Result LOCKED() {
        return Pressed.Result.BLOCKED;
    }

    @Migrate(value = Pressed.Result.class, kind = Migrate.Kind.FIELD)
    static Pressed.Result FINISHED() {
        return Pressed.Result.BLOCKED;
    }

    @Migrate(value = Pressed.Result.class, kind = Migrate.Kind.FIELD)
    static Pressed.Result BUSY() {
        return Pressed.Result.BLOCKED;
    }

    /** 2026-11-06: a group of facts is {@code @Facts}, since a bot need not be a game; only the name changes. */
    @SuppressWarnings("removal")
    @Migrate(value = GameState.class, kind = Migrate.Kind.TYPE)
    static Facts gameState() {
        return null;
    }
}
