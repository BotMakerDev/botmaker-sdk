// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.source;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.internal.capture.AmbientSource;
import com.botmaker.sdk.internal.capture.Monitor;
import com.botmaker.sdk.internal.capture.NamedWindow;
import com.botmaker.sdk.internal.emulator.EmulatorSource;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.shared.launch.WindowIdentity;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.Rectangle;

/**
 * The strip under the Choose capture source window's tabs, for the picked tile: a bigger preview, its size, how the
 * next run finds it, the region of it the bot reads (typed, drawn on a grab of it, or the whole) and Use.
 *
 * <p>The region is a rectangle in the <em>source's own</em> pixels, where {@code (0,0)} is its top-left, so the
 * narrowing survives the window moving. Left blank it is the whole source, which is what almost every pick means.
 */
final class SourceStrip {

    private static final double PREVIEW_W = 240;
    private static final double PREVIEW_H = 135;

    private final StackPane preview = new StackPane();
    private final Label name = Styles.on(new Label("Pick a source"), Styles.STRONG_TEXT);
    private final Label size = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final Label foundBy = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    private final TextField rx = regionField("x");
    private final TextField ry = regionField("y");
    private final TextField rw = regionField("w");
    private final TextField rh = regionField("h");
    /** Why Draw… could not draw, or what the strip is grabbing; empty otherwise. */
    private final Label note = Styles.on(new Label(), Styles.DIALOG_HINT);
    private final HBox node;

    /**
     * @param offersRegion whether the region row is shown; a caller that uses only the surface turns it off
     */
    SourceStrip(boolean offersRegion, Runnable onDraw, Runnable onUse) {
        preview.setMinSize(PREVIEW_W, PREVIEW_H);
        preview.setPrefSize(PREVIEW_W, PREVIEW_H);
        preview.setMaxSize(PREVIEW_W, PREVIEW_H);
        setPreview(null, "");
        foundBy.setWrapText(true);
        note.setWrapText(true);

        Label regionLabel = Styles.on(new Label("Region (optional):"), Styles.DIALOG_HINT);
        Button draw = new Button("Draw…");
        draw.setTooltip(new Tooltip("Grab the picked source and drag the part of it the bot should read"));
        draw.setOnAction(e -> onDraw.run());
        Button whole = new Button("Whole");
        whole.setTooltip(new Tooltip("Read all of the picked source"));
        whole.setOnAction(e -> setRegion(null));
        HBox regionRow = new HBox(6, regionLabel, rx, ry, rw, rh, draw, whole);
        regionRow.setAlignment(Pos.CENTER_LEFT);
        regionRow.setVisible(offersRegion);
        regionRow.setManaged(offersRegion);

        Button use = Styles.on(new Button("Use"), Styles.PRIMARY_BUTTON);
        use.setOnAction(e -> onUse.run());
        Region gap = new Region();
        VBox.setVgrow(gap, Priority.ALWAYS);
        VBox details = new VBox(4, name, size, foundBy, regionRow, note, gap, use);
        HBox.setHgrow(details, Priority.ALWAYS);
        node = new HBox(12, preview, details);
        node.setStyle("-fx-padding: 8 0 0 0;");
    }

    Node node() {
        return node;
    }

    /** Shows the tile picked: its name, its thumbnail, whose size is the source's, and how a run finds it. */
    void show(String title, CaptureSource target, Image image) {
        name.setText(title);
        foundBy.setText(foundBy(target));
        note.setText("");
        setPreview(image, "No preview");
    }

    /** Shows a grab of the picked source, ▶ Try's. */
    void preview(Image image) {
        setPreview(image, "Nothing to show");
    }

    void note(String text) {
        note.setText(text);
    }

    private void setPreview(Image image, String missing) {
        if (image == null) {
            preview.getChildren().setAll(Styles.on(new Label(missing), Styles.MUTED_TEXT));
            size.setText("");
            return;
        }
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(PREVIEW_W);
        view.setFitHeight(PREVIEW_H);
        preview.getChildren().setAll(view);
        size.setText("%d×%d".formatted(Math.round(image.getWidth()), Math.round(image.getHeight())));
    }

    /** The region typed, or {@code null} for the whole source. */
    Rectangle region() {
        return parseRegion(rx.getText(), ry.getText(), rw.getText(), rh.getText());
    }

    /** Fills the four fields with {@code region}, or empties them for the whole source. */
    void setRegion(Rectangle region) {
        rx.setText(region == null ? "" : String.valueOf(region.x));
        ry.setText(region == null ? "" : String.valueOf(region.y));
        rw.setText(region == null ? "" : String.valueOf(region.width));
        rh.setText(region == null ? "" : String.valueOf(region.height));
        note.setText("");
    }

    /** How the next run finds {@code target}: what the strip says under the size. */
    static String foundBy(CaptureSource target) {
        CaptureSource whole = CaptureLabels.whole(target);
        if (whole instanceof AmbientSource) return "Decided at each run from the game's target.";
        if (whole instanceof NamedWindow window) {
            boolean byTitle = WindowIdentity.isGeneric(window.windowClass());
            return "Found by its class " + window.windowClass()
                    + (byTitle ? " and its title, since that class is shared" : "") + ", wherever it moves.";
        }
        if (whole instanceof EmulatorSource emulator) return "Read from " + emulator.instanceName() + " over adb.";
        if (whole instanceof Monitor monitor) return "Screen " + (monitor.index() + 1) + " of this computer.";
        return "Every monitor of this computer together.";
    }

    /** The four fields as a rectangle, or {@code null} unless all four are whole numbers with a positive size. */
    static Rectangle parseRegion(String x, String y, String w, String h) {
        Integer px = parseInt(x), py = parseInt(y), pw = parseInt(w), ph = parseInt(h);
        if (px == null || py == null || pw == null || ph == null || px < 0 || py < 0 || pw <= 0 || ph <= 0) {
            return null;
        }
        return new Rectangle(px, py, pw, ph);
    }

    private static Integer parseInt(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** A narrow numeric field for one region coordinate. */
    private static TextField regionField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefColumnCount(3);
        Styles.on(field, Styles.SMALL_TEXT);
        return field;
    }
}
