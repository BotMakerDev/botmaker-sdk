package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.CaptureModel;
import com.botmaker.sdk.authoring.TemplateLibrary;
import com.botmaker.sdk.internal.plugin.capture.CaptureSurface.Region;
import com.botmaker.sdk.internal.plugin.capture.TemplateNaming.NamedCapture;
import com.botmaker.sdk.internal.plugin.capture.TemplateNaming.NamedTemplate;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * <b>Capture Templates</b> — draw a region over the game and save it as a picture the bot can match.
 *
 * <p>A small always-on-top mini-toolbar that stays out of the way: it never covers the target, so the game
 * underneath goes on taking real clicks and the user can navigate to the screen they want to capture. The
 * rubber-band surface ({@link CaptureSurface}) is shown only <em>during</em> a capture and dismissed
 * afterwards, so mouse events are grabbed only while actually drawing. Three modes:
 * <ul>
 *   <li><b>Capture one</b> — draw a region, name it, save.</li>
 *   <li><b>Capture many</b> — draw several in one pass, then name or discard them all at once.</li>
 *   <li><b>Capture object</b> — flood-select an object and cut it out with a transparent background.</li>
 * </ul>
 *
 * <h2>Why this is a plugin's tool and not the host's</h2>
 *
 * <p>Everything it touches is this plugin's: it reads the capture target and the capture size out of
 * {@code capture.json}, grabs the pixels through {@code botmaker-shared}, and writes an
 * {@code ImageTemplate} into the project's picture folder. The host answers one question —
 * {@link StudioServices#resourcesDir() which project is open} — plus the ordinary furniture of theming and a
 * parent window. Nothing was added to the contract to bring it across, which is the standing condition on
 * this whole direction.
 *
 * <p><b>The pixels are re-grabbed at save time, never taken from the frame the user drew on.</b> That is
 * what keeps the overlay's own chrome out of a saved picture, and the drawn selection (overlay-logical
 * pixels) is mapped onto the captured image (physical pixels) by the width/height ratio, which keeps the
 * crop correct under HiDPI scaling.
 */
public final class CaptureTemplates {

    /** The single live tool, so pressing the button again focuses it instead of opening a second one. */
    private static CaptureTemplates active;

    private final StudioServices services;
    private final Window owner;

    /**
     * The size a window target is snapped to before every grab, or {@code null} when the project names none.
     *
     * <p>Fixed when the tool opens rather than read per grab: it is what every picture in one session is
     * captured at, and a value that changed underneath the user mid-session would produce a batch of
     * pictures at two sizes with nothing saying so.
     */
    private final CaptureModel.Resolution referenceSize;

    /**
     * The tag a batch is pre-filled with — the activity that was open when the tool was opened, or
     * {@code null}. Fixed at open time on purpose: the tool is long-lived and deliberately keeps the editor
     * out of the way, so a tag that changed underneath the user would be a worse default than the one they
     * started from.
     */
    private final String suggestedTag;

    /**
     * Run once when the tool is finished with the screen, however it ends — closed, or never opened because
     * there was no target. A caller that got out of the way to make room for it uses this to come back.
     */
    private final Runnable onClosed;

    private Stage toolbarStage;
    private CaptureSurface surface;
    private ObjectCaptureSurface objectSurface;

    /** The shape the ▢/⬭ toggle currently selects for Capture one/many (object mode ignores it). */
    private CaptureSurface.Shape shape = CaptureSurface.Shape.RECT;

    /** Set the first time the tool finishes, so Esc pressed twice doesn't reopen the caller twice. */
    private boolean closed;

    private CaptureTemplates(StudioServices services, Window owner, CaptureModel.Resolution referenceSize,
                             String suggestedTag, Runnable onClosed) {
        this.services = services;
        this.owner = owner;
        this.referenceSize = referenceSize;
        this.suggestedTag = suggestedTag;
        this.onClosed = onClosed;
    }

    /** Opens the tool for the project's default capture target. Must be called on the FX thread. */
    public static void open(StudioServices services, Window owner, String suggestedTag) {
        open(services, owner, suggestedTag, () -> {});
    }

    /**
     * As {@link #open(StudioServices, Window, String)}, running {@code onClosed} once the tool is done with
     * the screen — including the two paths where it never opens at all (no capture target, or one is already
     * up), so a caller that hid itself always comes back.
     */
    public static void open(StudioServices services, Window owner, String suggestedTag, Runnable onClosed) {
        Runnable done = onClosed == null ? () -> {} : onClosed;
        // Single-instance: focus the live tool instead of stacking another one.
        if (active != null && active.toolbarStage != null && active.toolbarStage.isShowing()) {
            active.toolbarStage.toFront();
            done.run();
            return;
        }
        new CaptureTemplates(services, owner, EditorFrame.referenceSize(services), suggestedTag, done).start();
    }

    /**
     * Probes the target once up front, so the tool fails before showing anything and can place its toolbar
     * beside where the target actually is. Every later capture re-probes, so a window the user has since
     * moved is still tracked.
     */
    private void start() {
        grab(frame -> {
            active = this;
            showToolbar(frame);
        }, failure -> {
            warn(failure.headline() + "\n\n" + failure.detail());
            finish();
        });
    }

    // ── The mini-toolbar ───────────────────────────────────────────────────────────────────────────────

    private void showToolbar(EditorFrame frame) {
        Label title = new Label("Capture Templates");
        title.setTextFill(Color.web("#c9d4e6"));

        Button one = new Button("▢ Capture one");
        one.setOnAction(e -> beginSingle());
        Button many = new Button("▦ Capture many");
        many.setOnAction(e -> beginMany());
        Button object = new Button("◎ Capture object");
        object.setTooltip(new Tooltip("Drag a box around an object to extract it with a transparent "
                + "background; drag to add, right-drag to remove, Ctrl+Z/Y to undo/redo"));
        object.setOnAction(e -> beginObject());
        Button close = new Button("✕ Close");
        close.setOnAction(e -> closeTool());

        // The size readout, so the user always knows what resolution they are capturing at — and sees it
        // when the target is not at the size the bot will replay against, which nothing else reveals.
        Label size = new Label(readout(frame.bounds()));
        size.setTextFill(Color.web("#8fa3bf"));
        size.setStyle("-fx-font-size: 11px;");

        HBox bar = new HBox(8, title, shapeToggle(), one, many, object, close, size);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));
        bar.setStyle(OverlayStage.PANEL);

        toolbarStage = OverlayStage.bar(bar, frame.bounds());
        toolbarStage.getScene().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) closeTool();
        });
    }

    /**
     * The ▢/⬭ switch that sets {@link #shape} for Capture one/many. A rectangle captures a plain crop; an
     * ellipse captures the inscribed oval with a transparent background. Object capture ignores it.
     */
    private HBox shapeToggle() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton rect = new ToggleButton("▢");
        rect.setTooltip(new Tooltip("Rectangle crop"));
        rect.setToggleGroup(group);
        rect.setSelected(shape == CaptureSurface.Shape.RECT);
        ToggleButton ellipse = new ToggleButton("⬭");
        ellipse.setTooltip(new Tooltip("Ellipse crop (transparent outside the oval; hold Shift for a circle)"));
        ellipse.setToggleGroup(group);
        ellipse.setSelected(shape == CaptureSurface.Shape.ELLIPSE);

        rect.setOnAction(e -> { rect.setSelected(true); shape = CaptureSurface.Shape.RECT; });
        ellipse.setOnAction(e -> { ellipse.setSelected(true); shape = CaptureSurface.Shape.ELLIPSE; });

        HBox box = new HBox(0, rect, ellipse);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * {@code "▧ 1600×900"}, or {@code "▧ 1600×900  ·  ref 1920×1080 ⚠"} when the target is not at the size
     * the project captures at.
     *
     * <p>The mismatch is worth saying because nothing else reveals it, and it is not a rare state: a private
     * session's host window is deliberately never resized, and the snap of an ordinary window is
     * best-effort. A picture authored at the wrong size is matched after a rescale that loses detail.
     */
    private String readout(java.awt.Rectangle bounds) {
        String plain = "▧ " + bounds.width + "×" + bounds.height;
        if (referenceSize == null
                || (bounds.width == referenceSize.width() && bounds.height == referenceSize.height())) {
            return plain;
        }
        return plain + "  ·  ref " + referenceSize.width() + "×" + referenceSize.height() + " ⚠";
    }

    // ── Capture one ────────────────────────────────────────────────────────────────────────────────────

    private void beginSingle() {
        toolbarStage.hide();
        grab(frame -> surface = CaptureSurface.single(services, frame.bounds(), backdrop(frame), shape,
                this::onSingleRegion, this::endSession), this::failSession);
    }

    private void onSingleRegion(Region region) {
        surface.hide();
        grab(frame -> {
            try {
                BufferedImage cropped = crop(frame.image(), region);
                if (cropped == null) return;
                Optional<NamedCapture> named =
                        TemplateNaming.promptNew(services, owner, cropped, suggestedTag);
                if (named.isEmpty()) return;
                save(cropped, named.get().name(), frame.image().getWidth(), frame.image().getHeight());
                TemplateLibrary.applyTags(resources(), Map.of(named.get().name(), named.get().tags()));
            } catch (Exception failed) {
                warn("Failed to save the picture: " + failed.getMessage());
            } finally {
                endSession();
            }
        }, this::failSession);
    }

    // ── Capture many ───────────────────────────────────────────────────────────────────────────────────

    private void beginMany() {
        toolbarStage.hide();
        grab(frame -> surface = CaptureSurface.many(services, frame.bounds(), backdrop(frame), shape,
                this::onManyDone, this::endSession), this::failSession);
    }

    private void onManyDone(List<Region> regions) {
        surface.hide();
        if (regions.isEmpty()) {
            endSession();
            return;
        }
        grab(frame -> {
            try {
                List<BufferedImage> crops = new ArrayList<>();
                for (Region region : regions) {
                    BufferedImage cropped = crop(frame.image(), region);
                    if (cropped != null) crops.add(cropped);
                }
                if (crops.isEmpty()) return;
                TemplateNaming.Batch batch = TemplateNaming.showBatch(services, owner, crops, suggestedTag);
                List<String> saved = new ArrayList<>();
                for (NamedTemplate template : batch.templates()) {
                    save(template.image(), template.name(),
                            frame.image().getWidth(), frame.image().getHeight());
                    saved.add(template.name());
                }
                TemplateLibrary.applyTags(resources(), batch.tagsFor(saved));
            } catch (Exception failed) {
                warn("Failed to save the pictures: " + failed.getMessage());
            } finally {
                endSession();
            }
        }, this::failSession);
    }

    // ── Capture object ─────────────────────────────────────────────────────────────────────────────────

    private void beginObject() {
        toolbarStage.hide();
        grab(frame -> {
            // The sidecar's capture resolution is the whole frame the object was cut from — that is what the
            // bot rescales against — and not the cut-out's own size.
            objectFrameWidth = frame.image().getWidth();
            objectFrameHeight = frame.image().getHeight();
            objectSurface = ObjectCaptureSurface.open(services, frame.bounds(), frame.image(),
                    this::onObjectExtracted, this::endSession);
        }, this::failSession);
    }

    /** The full frame the in-progress object cut was taken from, for its resolution sidecar. */
    private int objectFrameWidth;
    private int objectFrameHeight;

    private void onObjectExtracted(BufferedImage cut) {
        if (objectSurface != null) objectSurface.hide();
        try {
            Optional<NamedCapture> named = TemplateNaming.promptNew(services, owner, cut, suggestedTag);
            if (named.isEmpty()) return;
            save(cut, named.get().name(), objectFrameWidth, objectFrameHeight);
            TemplateLibrary.applyTags(resources(), Map.of(named.get().name(), named.get().tags()));
        } catch (Exception failed) {
            warn("Failed to save the object: " + failed.getMessage());
        } finally {
            endSession();
        }
    }

    // ── Shared plumbing ────────────────────────────────────────────────────────────────────────────────

    /**
     * Re-grabs the target off the FX thread — raising and snapping the window first — and delivers the frame
     * back on it.
     *
     * <p>Every step re-grabs rather than reusing the last frame, which is what lets the tool follow a window
     * the user has moved or resized between two captures.
     */
    private void grab(Consumer<EditorFrame> onFrame, Consumer<EditorFrame.Failure> onFailure) {
        EditorFrame.grabAsync(services, referenceSize, onFrame, onFailure);
    }

    /**
     * The frame the rubber-band surface must paint itself, or {@code null} when the pixels really are on the
     * desktop behind it and the surface can stay transparent.
     */
    private static BufferedImage backdrop(EditorFrame frame) {
        return frame.onScreen() ? null : frame.image();
    }

    /** Reports a failed grab and returns to the toolbar. */
    private void failSession(EditorFrame.Failure failure) {
        warn(failure.headline() + "\n\n" + failure.detail());
        endSession();
    }

    /** Disposes the active surface (if any) and brings the mini-toolbar back. */
    private void endSession() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (objectSurface != null) {
            objectSurface.close();
            objectSurface = null;
        }
        if (toolbarStage != null) toolbarStage.show();
    }

    /** Closes the toolbar and any live surface, and clears the single-instance reference. */
    private void closeTool() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (objectSurface != null) {
            objectSurface.close();
            objectSurface = null;
        }
        if (toolbarStage != null) toolbarStage.close();
        finish();
    }

    private void finish() {
        if (closed) return;
        closed = true;
        if (active == this) active = null;
        onClosed.run();
    }

    private void save(BufferedImage picture, String name, int frameWidth, int frameHeight) throws Exception {
        TemplateLibrary.saveTemplate(resources(), picture, name, frameWidth, frameHeight, windowTitle());
    }

    private Path resources() {
        return services.resourcesDir();
    }

    /** The window title saved beside a picture, or {@code null} for a screen, desktop or emulator target. */
    private String windowTitle() {
        var target = EditorFrame.defaultTarget(services);
        return target == null ? null : target.windowTitle();
    }

    /**
     * Maps a drawn {@link Region} (overlay-logical pixels) onto {@code full} (physical pixels) and crops it.
     * A rectangle region is a plain subimage; an ellipse region is cropped to its bounding box and masked to
     * the inscribed oval, transparent outside it.
     */
    private static BufferedImage crop(BufferedImage full, Region r) {
        if (r.paneW() <= 0 || r.paneH() <= 0) return null;
        double scaleX = full.getWidth() / r.paneW();
        double scaleY = full.getHeight() / r.paneH();
        int x = (int) Math.round(r.x() * scaleX);
        int y = (int) Math.round(r.y() * scaleY);
        int w = (int) Math.round(r.w() * scaleX);
        int h = (int) Math.round(r.h() * scaleY);
        x = Math.max(0, Math.min(x, full.getWidth() - 1));
        y = Math.max(0, Math.min(y, full.getHeight() - 1));
        w = Math.max(1, Math.min(w, full.getWidth() - x));
        h = Math.max(1, Math.min(h, full.getHeight() - y));
        BufferedImage sub = full.getSubimage(x, y, w, h);
        if (r.shape() != CaptureSurface.Shape.ELLIPSE) return sub;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Double(0, 0, w, h));
        g.drawImage(sub, 0, 0, null);
        g.dispose();
        return out;
    }

    private void warn(String message) {
        Alert alert = services.theme().alert(Alert.AlertType.WARNING, message);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
