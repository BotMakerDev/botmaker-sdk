package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.text.Words;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link SdkTypes#ELEMENT}: an element is written by its first clue's factory, and its parts build it back. */
class ElementTypeTest {

    private static final Element LIST = Element.byId("list_pane");

    private static List<ComponentType<Element>> declarations() {
        List<ComponentType<Element>> all = new ArrayList<>();
        all.add(SdkTypes.ELEMENT);
        all.addAll(SdkTypes.ELEMENT_BY_OTHER_CLUES);
        return all;
    }

    /** The first declaration that writes it, as the host picks one. */
    private static ComponentType<Element> writer(Element element) {
        return declarations().stream().filter(d -> d.writes(element)).findFirst().orElseThrow();
    }

    @Test
    void eachElementIsWrittenByItsFirstCluesFactoryAndBuildsBack() {
        List<Element> elements = List.of(
                Element.byId("title").text(Words.of("Battery")).shot("elements/battery.png"),
                Element.byLabel(Words.of("Share")).inside(LIST),
                Element.byText(Words.of("Network")).below(LIST).index(1),
                Element.byHolding(Words.of("Apps")).inside(LIST));
        List<String> factories = new ArrayList<>();
        for (Element element : elements) {
            ComponentType<Element> writer = writer(element);
            factories.add(((Method) writer.factory()).getName());
            assertEquals(element, writer.build(writer.components(element)), element.toString());
        }
        assertEquals(List.of("byId", "byLabel", "byText", "byHolding"), factories);
    }

    @Test
    void onlyOneDeclarationWritesEachElement() {
        for (Element element : List.of(Element.byId("a"), Element.byLabel(Words.of("a")),
                Element.byText(Words.of("a")), Element.byHolding(Words.of("a")))) {
            assertEquals(1, declarations().stream().filter(d -> d.writes(element)).count(), element.toString());
        }
    }
}
