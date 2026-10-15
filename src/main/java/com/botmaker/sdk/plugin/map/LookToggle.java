package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.plugin.map.ScreenCards.ButtonLook;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/**
 * An input's button in one of its looks, with ◀ look ▶ stepping through the others: a button that changes its look
 * is one input, shown once (the user's call, 2026-10-15). Each step shows that look's picture and, when the ways tell
 * the looks apart, where it leads then.
 */
final class LookToggle {

    private LookToggle() {}

    /**
     * {@code looks}' first look drawn by {@code thumb}, a toggle under it when there are more; {@code leads} shows
     * where the look shown leads, and keeps {@code usual} for a look whose ways say nothing of their own.
     */
    static Node of(List<ButtonLook> looks, Function<String, Node> thumb, Label leads, String usual) {
        StackPane picture = new StackPane(thumb.apply(looks.isEmpty() ? null : looks.getFirst().picture()));
        if (looks.size() < 2) return picture;
        Label state = new Label();
        state.getStyleClass().add("screen-row-kind");
        int[] at = {0};
        Runnable show = () -> {
            ButtonLook look = looks.get(at[0]);
            picture.getChildren().setAll(thumb.apply(look.picture()));
            state.setText(look.state().displayName());
            leads.setText(look.leads().isEmpty() ? usual : "→ " + look.leads());
        };
        Button back = step("◀", "The look before", () -> at[0] = (at[0] + looks.size() - 1) % looks.size(), show);
        Button next = step("▶", "The next look", () -> at[0] = (at[0] + 1) % looks.size(), show);
        show.run();
        HBox toggle = new HBox(2, back, state, next);
        toggle.setAlignment(Pos.CENTER);
        VBox box = new VBox(2, picture, toggle);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Button step(String text, String tip, Runnable move, Runnable show) {
        Button button = new Button(text);
        button.getStyleClass().add("screen-look-step");
        button.setTooltip(new Tooltip(tip));
        button.setOnAction(e -> {
            move.run();
            show.run();
        });
        return button;
    }
}
