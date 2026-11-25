// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.toolkit.Styles;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.nio.file.Path;

/** The Game window's tiles, one size whatever their caption: a cover, a badge naming what starts it, a name. */
final class Tiles {

    /** A Steam portrait cover's 2:3; a square icon sits centred in it. */
    private static final double COVER_WIDTH = 72;
    private static final double COVER_HEIGHT = 108;
    /** Two caption lines; a longer name ends in an ellipsis and is whole in the tooltip. */
    private static final double CAPTION_HEIGHT = 34;
    /** The tile's own padding (the {@code template-tile} style) on each side, and the cover-to-caption gap. */
    private static final double TILE_PADDING = 6;
    private static final double TILE_GAP = 4;
    private static final double TILE_WIDTH = COVER_WIDTH + 2 * TILE_PADDING;
    private static final double TILE_HEIGHT = COVER_HEIGHT + TILE_GAP + CAPTION_HEIGHT + 2 * TILE_PADDING;

    private Tiles() {}

    /**
     * A tile of {@code group} for {@code name}: its picture, or its initials, with {@code badge} in its corner
     * ({@code null}: none). Pressed twice it stays selected; {@code onPick} runs on each press.
     */
    static ToggleButton tile(ToggleGroup group, String name, Path artwork, String badge, String tooltip,
                             Runnable onPick) {
        ToggleButton button = Styles.on(new ToggleButton(), Styles.TILE);
        button.setToggleGroup(group);
        button.setGraphic(cover(name, artwork, initials(name), badge));
        fixSize(button);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> {
            button.setSelected(true);
            onPick.run();
        });
        return button;
    }

    /**
     * Every tile the same size, whatever its caption. Only a width used to be set, and a wrapping caption's
     * preferred height is measured before its width is known — a long name made a tile hundreds of pixels tall.
     */
    static void fixSize(ButtonBase tile) {
        tile.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tile.setMinSize(TILE_WIDTH, TILE_HEIGHT);
        tile.setPrefSize(TILE_WIDTH, TILE_HEIGHT);
        tile.setMaxSize(TILE_WIDTH, TILE_HEIGHT);
    }

    /** {@code name}'s cover, or {@code mark} in its place when there is no picture, with {@code badge} in its corner. */
    static Node cover(String name, Path artwork, String mark, String badge) {
        StackPane frame = new StackPane();
        frame.setPrefSize(COVER_WIDTH, COVER_HEIGHT);
        frame.setMinSize(COVER_WIDTH, COVER_HEIGHT);
        frame.setMaxSize(COVER_WIDTH, COVER_HEIGHT);
        if (artwork != null) {
            // Decoded at twice the frame so it stays sharp on a scaled display.
            ImageView view = new ImageView(new Image(artwork.toUri().toString(), 0, 2 * COVER_HEIGHT, true, true,
                    true));
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setFitWidth(COVER_WIDTH);
            view.setFitHeight(COVER_HEIGHT);
            frame.getChildren().add(view);
        } else {
            frame.getChildren().add(Styles.on(new Label(mark), Styles.CAPTION_STRONG));
        }
        if (badge != null) {
            Label chip = Styles.on(new Label(badge), Styles.CHIP);
            StackPane.setAlignment(chip, Pos.BOTTOM_LEFT);
            frame.getChildren().add(chip);
        }
        Label caption = Styles.on(new Label(name), Styles.TILE_NAME);
        caption.setWrapText(true);
        caption.setTextOverrun(OverrunStyle.ELLIPSIS);
        caption.setAlignment(Pos.TOP_CENTER);
        caption.setTextAlignment(TextAlignment.CENTER);
        caption.setMinSize(COVER_WIDTH, CAPTION_HEIGHT);
        caption.setPrefSize(COVER_WIDTH, CAPTION_HEIGHT);
        caption.setMaxSize(COVER_WIDTH, CAPTION_HEIGHT);
        VBox content = new VBox(TILE_GAP, frame, caption);
        content.setAlignment(Pos.TOP_CENTER);
        return content;
    }

    static String initials(String label) {
        if (label == null || label.isBlank()) return "?";
        StringBuilder out = new StringBuilder();
        for (String word : label.trim().split("\\s+")) {
            if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0)));
            if (out.length() >= 3) break;
        }
        return out.toString();
    }
}
