package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.plugin.assist.ElementRows.Row;
import com.botmaker.sdk.plugin.assist.ElementSaves.As;
import com.botmaker.sdk.plugin.assist.ElementSaves.Constant;
import com.botmaker.sdk.plugin.assist.ElementSaves.Pick;
import com.botmaker.sdk.plugin.assist.ElementSaves.Planned;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.botmaker.sdk.plugin.assist.ElementRowsTest.DOWNLOADS;
import static com.botmaker.sdk.plugin.assist.ElementRowsTest.SETTINGS;
import static com.botmaker.sdk.plugin.assist.ElementRowsTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What ticked rows save: constants with shots, anchors first, the bot's own reused, each part on the screen. */
class ElementSavesTest {

    private static final Rectangle FRAME = new Rectangle(0, 0, 1920, 1080);

    private static Planned plan(UiTree tree, Map<String, Element> existing, Pick... picks) {
        return ElementSaves.plan(tree, List.of(picks), existing, Words::of, FRAME);
    }

    private static Pick pick(UiTree tree, String said, As as) {
        return new Pick(row(ElementRows.of(tree), said), as);
    }

    @Test
    void savesConstantAndShot() {
        Planned planned = plan(SETTINGS, Map.of(), pick(SETTINGS, "Button \"Battery\"", As.BUTTON));
        Constant battery = planned.constants().getFirst();
        assertEquals(1, planned.constants().size());
        assertEquals("BATTERY", battery.name());
        assertEquals(Element.byId("title").text(Words.of("Battery")).shot("elements/battery.png"), battery.element());
        assertEquals(new Rectangle(108, 306, 192, 32), battery.cut(), "the shot is cut at the node");
        assertEquals(List.of(Words.of("Battery")), planned.words(), "its words go to Texts");
        assertSame(battery.element(), planned.parts().getFirst().element());
    }

    @Test
    void twoTickedRowsSaveTogether() {
        Planned planned = plan(SETTINGS, Map.of(), pick(SETTINGS, "Button \"Battery\"", As.BUTTON),
                pick(SETTINGS, "Button \"Sound\"", As.BUTTON));
        assertEquals(List.of("BATTERY", "SOUND"), planned.constants().stream().map(Constant::name).toList());
        MapScreen settings = ElementSaves.withParts(MapScreen.of(Setup.named("Settings")), planned.parts());
        assertEquals(Button.of(planned.constants().get(0).element()), settings.inputs().get("battery"));
        assertEquals(Button.of(planned.constants().get(1).element()), settings.inputs().get("sound"));
    }

    @Test
    void byTextRowWarnsAboutLanguage() {
        Planned planned = plan(SETTINGS, Map.of(), pick(SETTINGS, "Button \"Battery\"", As.BUTTON));
        assertEquals(List.of("Button \"Battery\": " + ElementRows.byTextWarning()), planned.warnings());
        Planned byId = plan(SETTINGS, Map.of(), pick(SETTINGS, "Toggle \"Wi‑Fi\"", As.BUTTON));
        assertEquals(List.of(), byId.warnings(), "an id needs no translation");
    }

    /** A copy of a list's row is a repeated button, its list saved first as a constant with no shot. */
    @Test
    void repeatedRowSavesRepeatedButton() {
        List<Row> removes = ElementRows.of(DOWNLOADS).stream().filter(r -> r.said().equals("Button \"Remove\""))
                .toList();
        Planned planned = plan(DOWNLOADS, Map.of(), new Pick(removes.get(1), As.BUTTON));
        assertEquals(List.of("DOWNLOADS", "REMOVE"), planned.constants().stream().map(Constant::name).toList());
        assertNull(planned.constants().getFirst().element().shot(), "an anchor has no shot");
        assertNull(planned.constants().getFirst().cut());
        assertTrue(planned.parts().getFirst().repeated());
        MapScreen downloads = ElementSaves.withParts(MapScreen.of(Setup.named("Downloads")), planned.parts());
        assertEquals(Button.of(planned.constants().get(1).element()).repeated(), downloads.inputs().get("remove"));
    }

    /** A list ticked beside a row inside it gets its shot, though the row names it first. */
    @Test
    void anAnchorTickedToo() {
        List<Row> rows = ElementRows.of(DOWNLOADS);
        Row remove = rows.stream().filter(r -> r.said().equals("Button \"Remove\"")).toList().get(1);
        Row list = rows.stream().filter(r -> r.kind() == ElementKind.LIST).findFirst().orElseThrow();
        Planned planned = plan(DOWNLOADS, Map.of(), new Pick(remove, As.BUTTON), new Pick(list, As.MARKER));
        assertEquals(List.of("DOWNLOADS", "REMOVE"), planned.constants().stream().map(Constant::name).toList());
        assertEquals("elements/downloads.png", planned.constants().getFirst().element().shot());
        assertSame(planned.constants().getFirst().element(), planned.parts().get(1).element());
        assertSame(planned.constants().getFirst().element(), planned.parts().get(0).element().inside());
    }

    /** Words are the name, "Text size" too; words of no letters are an element's, never a taken name. */
    @Test
    void constantsAreNamedFromTheirWords() {
        assertEquals("TEXT_SIZE", ElementSaves.constantName(Element.byText(Words.of("Text size")), Set.of()));
        assertEquals("ELEMENT_42", ElementSaves.constantName(Element.byText(Words.of("42")), Set.of()));
        assertEquals("ELEMENT_2", ElementSaves.constantName(Element.byText(Words.of("…")), Set.of("ELEMENT")));
        assertEquals("SETTINGS_LIST", ElementSaves.constantName(Element.byId("settings_list"), Set.of()));
    }

    /** A constant the bot has with the same clues is used, its shot and all; nothing is saved twice. */
    @Test
    void theBotsOwnConstantIsReused() {
        Element held = Element.byId("title").text(Words.of("Battery")).shot("elements/old.png");
        Planned planned = plan(SETTINGS, Map.of("BATTERY", held), pick(SETTINGS, "Button \"Battery\"", As.MARKER));
        assertEquals(List.of(), planned.constants());
        assertSame(held, planned.parts().getFirst().element());
        MapScreen settings = ElementSaves.withParts(MapScreen.of(Setup.named("Settings")), planned.parts());
        assertEquals(List.of(held), settings.setup().elements(), "a marker");
        assertEquals(settings, ElementSaves.withParts(settings, planned.parts()), "told by it once");
    }

    /** No bound, class name or package is in what is saved: only clues and the shot's path. */
    @Test
    void savedJavaHasNoBounds() {
        Planned planned = plan(SETTINGS, Map.of(), pick(SETTINGS, "Button \"Battery\"", As.BUTTON),
                pick(SETTINGS, "Text \"Network & internet\"", As.READOUT));
        for (Constant constant : planned.constants()) {
            String saved = constant.element().toString();
            for (String never : List.of("108", "306", "TextView", "com.example", "android:")) {
                assertFalse(saved.contains(never), saved + " holds " + never);
            }
        }
    }
}
