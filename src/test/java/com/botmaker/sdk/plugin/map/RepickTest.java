package com.botmaker.sdk.plugin.map;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pick restyles the shot drawn, a strip's members too, as a drawing made with that pick styles it. */
class RepickTest {

    private static final String BOX = "screen-shot-box-picked";
    private static final String MEMBER = "screen-strip-member-picked";

    @Test
    void aPickMovesTheStyleToItsBoxAndItsStripMember() {
        Rectangle buy = new Rectangle();
        Rectangle sell = new Rectangle();
        ScreenCanvas.pickable(buy, "buy", BOX, "buy");
        ScreenCanvas.pickable(sell, "sell", BOX, "buy");
        StackPane first = new StackPane();
        StackPane second = new StackPane();
        ScreenCanvas.pickable(first, "first", MEMBER, "buy");
        ScreenCanvas.pickable(second, "second", MEMBER, "buy");
        // Drawn in a scroll pane on the board, which needs the toolkit these tests do not start.
        HBox strip = new HBox(first, second);
        StackPane shot = new StackPane(new Pane(buy, sell, strip));
        assertTrue(buy.getStyleClass().contains(BOX), "drawn picked");

        ScreenCanvas.repick(shot, "second");
        assertFalse(buy.getStyleClass().contains(BOX));
        assertFalse(sell.getStyleClass().contains(BOX));
        assertTrue(second.getStyleClass().contains(MEMBER), "a member in a strip is reached");

        ScreenCanvas.repick(shot, "sell");
        ScreenCanvas.repick(shot, "sell");
        assertEquals(1, sell.getStyleClass().stream().filter(BOX::equals).count(), "picked twice, styled once");
        assertFalse(second.getStyleClass().contains(MEMBER));

        ScreenCanvas.repick(shot, null);
        assertFalse(sell.getStyleClass().contains(BOX), "none picked");
    }
}
