package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.internal.screens.PageUnknown;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnknownPagesViewTest {

    @Test
    void aNodeSaysItsClassIdTextLabelAndPlace() {
        PageUnknown.Node field = new PageUnknown.Node("identifierId", "EditText", "typed", "Email or phone", true,
                new Rect(10, 20, 300, 40), List.of());

        assertEquals("EditText #identifierId [Email or phone] field at 10,20 300×40", UnknownPagesView.line(field));
    }

    @Test
    void theOutlineKeepsTheTreesShape() {
        PageUnknown.Node next = new PageUnknown.Node("", "Button", "Next", "", false, null, List.of());
        PageUnknown page = new PageUnknown(1, List.of(new PageUnknown.Node("", "FrameLayout", "", "", false, null,
                List.of(next))));

        TreeItem<String> root = UnknownPagesView.outline(page);

        assertEquals(1, root.getChildren().size());
        assertEquals("Button \"Next\"", root.getChildren().getFirst().getChildren().getFirst().getValue());
    }

    @Test
    void theStatusSaysHowManyPages() {
        assertTrue(UnknownPagesView.said(0).contains("no page"));
        assertTrue(UnknownPagesView.said(2).startsWith("2 pages"));
    }
}
