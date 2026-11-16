// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The clues past an element's id and exact words: the words it contains, and the field it is. */
class ElementCluesTest {

    private static final String TREE = """
            <hierarchy rotation="0">
              <node text="" resource-id="" class="android.webkit.WebView" package="app" content-desc="" clickable="false" bounds="[0,0][1000,900]">
                <node text="Couldn’t sign you in" resource-id="" class="android.widget.TextView" package="app" content-desc="" clickable="false" bounds="[0,0][1000,50]" />
                <node text="" resource-id="" class="android.widget.EditText" package="app" content-desc="" clickable="true" bounds="[0,100][1000,180]" />
                <node text="" resource-id="" class="android.widget.EditText" package="app" content-desc="" clickable="true" password="true" bounds="[0,200][1000,280]" />
                <node text="" resource-id="" class="android.view.View" package="app" content-desc="" clickable="true" bounds="[0,300][1000,380]">
                  <node text="" resource-id="" class="android.view.View" package="app" content-desc="" clickable="false" bounds="[10,310][990,370]">
                    <node text="Get a verification code from the Google Authenticator app" resource-id="" class="android.widget.TextView" package="app" content-desc="" clickable="false" bounds="[20,320][900,360]" />
                  </node>
                </node>
                <node text="" resource-id="" class="android.view.View" package="app" content-desc="" clickable="true" bounds="[0,0][1000,900]">
                  <node text="Use a backup code" resource-id="" class="android.widget.TextView" package="app" content-desc="" clickable="false" bounds="[20,420][900,460]" />
                </node>
              </node>
            </hierarchy>
            """;

    private static List<UiNode> find(Element element) {
        return ElementMatch.matches(element, UiTree.parse(TREE));
    }

    @Test
    void containingFindsPartOfATextWhateverItsApostrophe() {
        assertEquals(1, find(Element.byContaining(Words.of("couldn't sign you in"))).size());
        assertEquals(List.of(), find(Element.byText(Words.of("Couldn't sign"))), "exact words still need the whole text");
        assertEquals(List.of(), find(Element.byContaining(Words.of("sign me in"))));
    }

    @Test
    void containingTapsTheRowJustAroundTheWords() {
        UiNode row = find(Element.byContaining(Words.of("google authenticator"))).getFirst();
        assertEquals(300, row.bounds().y, "the clickable row, two levels up");
        UiNode words = find(Element.byContaining(Words.of("backup code"))).getFirst();
        assertEquals("Use a backup code", words.text(), "a clickable page around the words is no row");
    }

    @Test
    void aTextFieldIsNotAPasswordsAndAPasswordIsNotATextField() {
        List<UiNode> field = find(Element.textField());
        assertEquals(1, field.size());
        assertEquals(100, field.getFirst().bounds().y);
        List<UiNode> password = find(Element.passwordField());
        assertEquals(1, password.size());
        assertEquals(200, password.getFirst().bounds().y);
        assertEquals("text field", Element.textField().shown());
    }

    @Test
    void aNodeAndItsChildSayingTheWordsAreOneRowAndANoBreakSpaceIsASpace() {
        String tree = """
                <hierarchy rotation="0">
                  <node text="" resource-id="" class="android.view.View" package="app" content-desc="Tap Yes on your phone" clickable="false" bounds="[0,0][1000,80]">
                    <node text="Tap Yes on your phone" resource-id="" class="android.widget.TextView" package="app" content-desc="" clickable="false" bounds="[10,10][990,70]" />
                  </node>
                  <node text="" resource-id="" class="android.widget.AutoCompleteTextView" package="app" content-desc="" clickable="true" bounds="[0,100][1000,180]" />
                </hierarchy>
                """;
        List<UiNode> rows = ElementMatch.matches(Element.byContaining(Words.of("tap yes on your phone")), UiTree.parse(tree));
        assertEquals(1, rows.size());
        assertEquals(0, rows.getFirst().bounds().y, "the outer one");
        assertEquals(1, ElementMatch.matches(Element.textField(), UiTree.parse(tree)).size(), "any field's class");
    }

    @Test
    void elementsThatFindDifferentlyAreShownDifferently() {
        List<String> shown = List.of(Element.byText(Words.of("a")).shown(), Element.byContaining(Words.of("a")).shown(),
                Element.textField().shown(), Element.textField().inside(Element.byId("x")).shown(),
                Element.byId("x").shown(), Element.byId("x").field().shown());
        assertEquals(shown.size(), shown.stream().distinct().count(), shown.toString());
    }

    @Test
    void anElementIsNotBothFields() {
        assertThrows(IllegalArgumentException.class, () -> new Element(null, null, null, null, null, true, true,
                null, null, null, null));
        assertEquals(true, Element.textField().password().isPassword());
        assertEquals(false, Element.textField().password().isField());
    }
}
