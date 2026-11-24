// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore.tree;

import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocatorTest {

    @Test
    void anAppsRowWithNoClueOfItsOwnIsFoundByTheTextItHolds() {
        // Android's Settings: each row a clickable layout with no id, label or text, its title a child's text.
        UiNode network = row(100, "Network & internet", "Wi‑Fi, hotspot");
        UiNode accounts = row(200, "Passwords & accounts", "Saved passwords, autofill, synced accounts");
        UiNode search = new UiNode("com.android.settings:id/search_action_bar", "", "Search",
                "android.widget.LinearLayout", "com.android.settings", new Rectangle(0, 0, 300, 50),
                Set.of(Trait.CLICKABLE), List.of());
        UiTree tree = new UiTree(List.of(new UiNode("", "", "", "android.widget.FrameLayout", "com.android.settings",
                new Rectangle(0, 0, 1000, 1000), Set.of(),
                List.of(search, new UiNode("com.android.settings:id/recycler_view", "", "",
                        "androidx.recyclerview.widget.RecyclerView", "com.android.settings",
                        new Rectangle(0, 60, 1000, 900), Set.of(Trait.SCROLLABLE), List.of(network, accounts))))));

        List<Locator.Located> inputs = Locator.inputs(tree);

        assertEquals(List.of("#search_action_bar", "holding \"Network & internet\"",
                "holding \"Passwords & accounts\""), inputs.stream().map(l -> l.locator().line()).toList(),
                "every row is an input, named by its title, not only the one with an id");
        assertEquals(List.of(accounts), ElementMatch.matches(inputs.get(2).locator().element(), tree),
                "the element the bot is written with finds the row itself, the part a person taps");
    }

    @Test
    void aRowIsNamedByItsTitleNeverByAFieldsTypingOrANestedButtonsWords() {
        UiNode buy = new UiNode("", "Buy", "", "android.widget.Button", "com.game", new Rectangle(10, 10, 80, 30),
                Set.of(Trait.CLICKABLE), List.of());
        UiNode typed = new UiNode("", "wifi", "", "android.widget.EditText", "com.game", new Rectangle(10, 50, 200, 30),
                Set.of(), List.of());
        UiNode card = new UiNode("", "", "", "android.widget.LinearLayout", "com.game", new Rectangle(0, 0, 500, 200),
                Set.of(Trait.CLICKABLE), List.of(buy, typed, text("", "3 new", new Rectangle(10, 90, 60, 20)),
                text("android:id/title", "Gold pack", new Rectangle(10, 120, 200, 30))));
        UiTree tree = new UiTree(List.of(card));

        assertEquals(List.of("holding \"Gold pack\"", "text \"Buy\""),
                Locator.inputs(tree).stream().map(l -> l.locator().line()).toList(),
                "the title, not the button's word, a count before it, nor what the field holds");
    }

    private static UiNode row(int y, String title, String summary) {
        return new UiNode("", "", "", "android.widget.LinearLayout", "com.android.settings",
                new Rectangle(0, y, 1000, 80), Set.of(Trait.CLICKABLE), List.of(
                text("android:id/title", title, new Rectangle(50, y + 10, 400, 30)),
                text("android:id/summary", summary, new Rectangle(50, y + 45, 400, 25))));
    }

    private static UiNode text(String id, String text, Rectangle box) {
        return new UiNode(id, text, "", "android.widget.TextView", "com.android.settings", box, Set.of(), List.of());
    }
}
