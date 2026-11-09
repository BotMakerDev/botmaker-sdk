package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The general guards a run exploring on its own checks before each press. */
class GuardsTest {

    private static final ImageTemplate OK = new ImageTemplate("images/ok.png");
    private static final String GAME = "com.example.game";
    private static final Foreground IN_GAME = new Foreground(GAME, GAME);
    private static final Guards GUARDS = Guards.with(List.of("Delete", "Acheter"));

    private static Guards.Kind check(Input input, String... near) {
        List<TextResult> text = Arrays.stream(near)
                .map(said -> new TextResult(said, new Rect(0, 0, 10, 10), 90, TextResult.Level.LINE)).toList();
        return GUARDS.check(input, new Guards.Around(text, IN_GAME, List.of())).kind();
    }

    private static UiNode field(String id, Set<Trait> traits) {
        return new UiNode(GAME + ":id/" + id, "", "", "android.widget.EditText", GAME, new Rectangle(0, 0, 10, 10),
                traits, List.of());
    }

    @Test
    void untaggedIsRefused() {
        assertEquals(Guards.Kind.NO_VERDICT, check(Button.of(OK)));
    }

    @Test
    void neverIsRefused() {
        assertEquals(Guards.Kind.NEVER, check(Button.of(OK).never("spends money")));
    }

    @Test
    void authorWordRefusesFree() {
        assertEquals(Guards.Kind.TEXT, check(Button.of(OK).free(), "delete save"));
        assertEquals(Guards.Kind.TEXT, check(Button.of(OK).free(), "ACHETER"));
        assertEquals(Guards.Kind.NONE, check(Button.of(OK).free(), "Continue"));
    }

    @Test
    void currencyPassesSpends() {
        assertEquals(Guards.Kind.NONE, check(Button.of(OK).spends(ScreenTypes.fact("Progress::coins")), "€ 4.99"));
    }

    @Test
    void yenSymbolRefuses() {
        assertEquals(Guards.Kind.TEXT, check(Button.of(OK).free(), "¥ 980"));
        assertEquals(Guards.Kind.TEXT, check(Button.of(OK).free(), "₹99"), "any currency sign, not a listed few");
    }

    @Test
    void wordsAreWhole() {
        assertEquals(Guards.Kind.NONE, check(Button.of(OK).free(), "Undeleted"));
        assertEquals(Guards.Kind.NONE, Guards.with(List.of()).check(Button.of(OK).free(),
                new Guards.Around(List.of(new TextResult("Delete", new Rect(0, 0, 1, 1), 90, TextResult.Level.WORD)),
                        IN_GAME, List.of())).kind(), "no word is listed until the author lists it");
    }

    @Test
    void foreignPackageRefuses() {
        Guards.Refusal refusal = GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), new Foreground("com.android.chrome", GAME), List.of()));
        assertEquals(Guards.Kind.FOREIGN_WINDOW, refusal.kind());
        assertEquals(Guards.Kind.FOREIGN_WINDOW, GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), new Foreground("", GAME), List.of())).kind(), "unread is not the game");
        assertFalse(GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), Foreground.UNKNOWN, List.of())).refused(), "no target, nothing to compare");
    }

    @Test
    void storeSheetIsSystemDialog() {
        assertEquals(Guards.Kind.SYSTEM_DIALOG, GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), new Foreground("com.android.vending", GAME), List.of())).kind());
    }

    @Test
    void androidDialogOverTheGameRefuses() {
        assertEquals(Guards.Kind.SYSTEM_DIALOG, GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), new Foreground(GAME, GAME, "isn't responding"), List.of())).kind(),
                "an ANR's focus names the game, the dialog still stops the press");
    }

    @Test
    void neverSaysItsReasonOnce() {
        assertEquals("It is never pressed: real money.", GUARDS.check(Button.of(OK).never("real money."),
                new Guards.Around(List.of(), IN_GAME, List.of())).why());
    }

    @Test
    void passwordFieldRefuses() {
        assertEquals(Guards.Kind.SENSITIVE_FIELD, GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), IN_GAME, List.of(field("pin", Set.of(Trait.PASSWORD))))).kind());
    }

    @Test
    void cardFieldRefuses() {
        assertEquals(Guards.Kind.SENSITIVE_FIELD, GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), IN_GAME, List.of(field("card_number", Set.of())))).kind());
        assertEquals(Guards.Kind.SENSITIVE_FIELD, GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), IN_GAME, List.of(field("cc_no", Set.of())))).kind());
        assertEquals(Guards.Kind.NONE, GUARDS.check(Button.of(OK).free(),
                new Guards.Around(List.of(), IN_GAME, List.of(field("player_name", Set.of())))).kind());
    }

    @Test
    void textAwayFromTheInputIsIgnored() {
        TextResult close = new TextResult("€ 1", new Rect(110, 100, 20, 10), 90, TextResult.Level.LINE);
        TextResult far = new TextResult("€ 2", new Rect(400, 400, 20, 10), 90, TextResult.Level.LINE);
        assertEquals(List.of(close), Guards.near(new Rect(100, 100, 40, 20), List.of(close, far)));
        assertEquals(List.of(close, far), Guards.near(new Rect(), List.of(close, far)), "an unknown box misses nothing");
    }
}
