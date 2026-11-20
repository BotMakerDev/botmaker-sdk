// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.explore;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Combo;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Gesture;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.Reach;
import com.botmaker.shared.emulator.tree.UiNode;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Where the guards look for the text beside a button: a tree element's node, never the whole page. */
class LivePlayBoxTest {

    private static final BufferedImage FRAME = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    /** A page whose tree holds {@code nodes} for every element, or no tree at all. */
    private record Page(boolean trees, List<UiNode> nodes) implements Reach {

        @Override
        public boolean readsTrees() {
            return trees;
        }

        @Override
        public List<UiNode> elements(Element element, BufferedImage frame) {
            return nodes;
        }

        @Override
        public Optional<BufferedImage> look() {
            return Optional.of(FRAME);
        }

        @Override
        public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
            return Optional.empty();
        }

        @Override
        public void press(Point at, Gesture gesture, Duration hold) {}

        @Override
        public void keys(Combo combo) {}

        @Override
        public void type(String text, boolean clears, boolean submits) {}

        @Override
        public void scroll(Point at, int notches) {}

        @Override
        public void drag(Point from, Point to, Duration time) {}

        @Override
        public long nowMs() {
            return 0;
        }

        @Override
        public void pause(long ms) {}
    }

    private static UiNode link(int x, int y) {
        return new UiNode("", "Blog", "Blog", "web:link", "", new Rectangle(x, y, 40, 16), Set.of(), List.of());
    }

    /** A page's footer link is judged by the words around it alone, not by a price elsewhere on the page. */
    @Test
    void aTreeElementIsBoxedByItsNode() {
        Button blog = Button.of(Element.byRole(Role.LINK, Words.of("Blog")));

        assertEquals(new Rect(20, 900, 40, 16), LivePlay.box(new Page(true, List.of(link(20, 900), link(80, 40))),
                blog, FRAME));
    }

    /** One the tree does not show, or a target with no tree, is unboxed: the guards then read every word. */
    @Test
    void anElementNotFoundIsUnboxed() {
        Button blog = Button.of(Element.byRole(Role.LINK, Words.of("Blog")));

        assertNull(LivePlay.box(new Page(true, List.of()), blog, FRAME));
        assertNull(LivePlay.box(new Page(false, List.of(link(20, 900))), blog, FRAME));
    }
}
