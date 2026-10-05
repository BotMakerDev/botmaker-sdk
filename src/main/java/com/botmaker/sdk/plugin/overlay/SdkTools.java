package com.botmaker.sdk.plugin.overlay;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.overlay.Marks;
import com.botmaker.plugin.api.overlay.OverlayTool;
import com.botmaker.plugin.api.overlay.OverlayToolContext;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.api.toolbar.ActionContext.Area;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.input.Mouse;
import com.botmaker.sdk.api.vision.ImageClicker;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.plugin.flow.ActivityFlowDialog;
import com.botmaker.sdk.plugin.pictures.PictureCuts;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.util.Optional;

/**
 * The SDK's tabs in the overlay editor's tool panes.
 *
 * <ul>
 *   <li><b>Picture:</b> drag a box on the game, name it, and it is a {@code Pictures} constant. Optionally a
 *       click on it is inserted at the caret.</li>
 *   <li><b>Point:</b> click a spot on the game, and {@code Mouse.click(new Point(x, y))} is inserted.</li>
 *   <li><b>Flow:</b> opens 🔀 Activity Flow.</li>
 * </ul>
 *
 * <p>Each tab is built on the FX thread each time the panel opens. It writes nothing but the picture file and
 * what the host's insert writes.
 */
public final class SdkTools {

    public static final OverlayTool PICTURE = OverlayTool.id("picture").named("Picture").pane(SdkTools::picture);
    public static final OverlayTool POINT = OverlayTool.id("point").named("Point").pane(SdkTools::point);
    public static final OverlayTool FLOW = OverlayTool.id("flow").named("Flow").pane(SdkTools::flow);

    private SdkTools() {}

    static Node picture(OverlayToolContext context) {
        TextField name = new TextField();
        name.setPromptText("picture name, e.g. collect_button");
        CheckBox click = new CheckBox("Insert a click on it");
        click.setSelected(true);
        Label status = new Label();
        status.setWrapText(true);
        Button cut = new Button("✂ Cut from the screen");
        cut.setMaxWidth(Double.MAX_VALUE);
        cut.setOnAction(e -> context.pickRegion("Drag around the picture").thenAccept(region -> region.ifPresent(
                area -> status.setText(cut(context, area, name.getText(), click.isSelected())))));
        return column(new Label("Cut a picture out of what the bot sees."), name, click, cut, status);
    }

    /** Cuts, saves and optionally inserts; answers the line to show. */
    static String cut(OverlayToolContext context, Area region, String typed, boolean insertClick) {
        Optional<BufferedImage> frame = context.frame();
        if (frame.isEmpty()) return "The watched screen cannot be captured.";
        Area at = context.watchedArea().orElse(null);
        Optional<BufferedImage> picture = PictureCuts.crop(frame.get(), at, region);
        if (picture.isEmpty()) return "That box is outside the watched screen.";
        PictureCuts.Cut saved;
        try {
            saved = PictureCuts.save(context.services(), picture.get(), typed, frame.get().getWidth(),
                    frame.get().getHeight(), null);
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        context.marks().show(region, Marks.Kind.NOTE, saved.name());
        String said = "Saved " + saved.name() + saved.note().map(n -> " — " + n).orElse("") + ".";
        if (!insertClick) return said;
        return context.insert(ImageClicker::click, new ImageTemplate(saved.path()))
                .map(refused -> said + " Not inserted: " + refused)
                .orElse(said + " Click inserted.");
    }

    static Node point(OverlayToolContext context) {
        Label status = new Label();
        status.setWrapText(true);
        Button pick = new Button("⌖ Click a point on the screen");
        pick.setMaxWidth(Double.MAX_VALUE);
        pick.setOnAction(e -> context.pickPoint("Click where the bot should click").thenAccept(spot -> spot.ifPresent(p -> {
            context.marks().show(new Area(p.x() - 4, p.y() - 4, 9, 9), Marks.Kind.CLICK, p.x() + "," + p.y());
            status.setText(context.insertVoid(Mouse::click, new Point(p.x(), p.y()))
                    .map(refused -> "Not inserted: " + refused)
                    .orElse("Inserted a click at " + p.x() + "," + p.y() + "."));
        })));
        return column(new Label("A click at a fixed screen point. Prefer a picture when the thing has one."), pick,
                status);
    }

    static Node flow(OverlayToolContext context) {
        Button open = new Button("🔀 Open Activity Flow");
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(e -> ActivityFlowDialog.open(actions(context.services())));
        return column(new Label("Add, rename and wire activities. Each activity is a chip above."), open);
    }

    private static ActionContext actions(StudioServices services) {
        return () -> services;
    }

    private static VBox column(Node... children) {
        VBox box = new VBox(8, children);
        box.setFillWidth(true);
        return box;
    }
}
