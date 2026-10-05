package com.botmaker.sdk.plugin.pictures;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.plugin.pictures.TemplateNaming.NamedCapture;
import com.botmaker.sdk.plugin.pictures.TemplateNaming.NamedTemplate;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.CaptureSurface;
import com.botmaker.sdk.plugin.screen.CaptureSurface.Region;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.screen.ObjectCaptureSurface;
import com.botmaker.sdk.plugin.screen.OverlayStage;
import javafx.animation.PauseTransition;
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
import javafx.util.Duration;

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
 * <p>Everything it touches is this plugin's: it reads the project's capture source, grabs the pixels through
 * {@code botmaker-shared}, and writes an {@code ImageTemplate} into the project's picture folder. The host
 * answers one question — {@link StudioServices#resourcesDir() which project is open} — plus the ordinary
 * furniture of theming and a parent window.
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
     * The source every grab in this session reads, or {@code null} to read the project's own each time.
     *
     * <p>Set only by the overlay editor's own row, where the window being drawn over is the subject and the
     * project's source may be something else entirely — or nothing at all, which used to make this tool
     * refuse to open over a perfectly good window. It is not persisted: see
     * {@link EditorFrame#grabAsync(StudioServices, CaptureSource, Consumer, Consumer)}.
     */
    private final CaptureSource target;

    /**
     * The tag a batch is pre-filled with — the activity that was open when the tool was opened, or
     * {@code null}. Fixed at open time on purpose: the tool is long-lived and deliberately keeps the editor
     * out of the way, so a tag that changed underneath the user would be a worse default than the one they
     * started from.
     */
    private final String suggestedTag;

    /**
     * Run once when the tool is finished with the screen, however it ends — closed, or never opened because
     * there was no target. A caller that got out of the way to make room for it uses this to come back, and
     * it is handed the names saved meanwhile, so the picture chooser can select what was just captured.
     */
    private final Consumer<List<String>> onClosed;

    /** The names of the pictures saved while the tool was open, in the order they were saved. */
    private final List<String> saved = new ArrayList<>();

    private Stage toolbarStage;
    /** The toolbar's "▧ W×H" readout, refreshed on every grab. */
    private final Label size = new Label();
    private CaptureSurface surface;
    private ObjectCaptureSurface objectSurface;

    /** The shape the ▢/⬭ toggle currently selects for Capture one/many (object mode ignores it). */
    private CaptureSurface.Shape shape = CaptureSurface.Shape.RECT;

    /** Set the first time the tool finishes, so Esc pressed twice doesn't reopen the caller twice. */
    private boolean closed;

    private CaptureTemplates(StudioServices services, Window owner,
                             CaptureSource target, String suggestedTag, Consumer<List<String>> onClosed) {
        this.services = services;
        this.owner = owner;
        this.target = target;
        this.suggestedTag = suggestedTag;
        this.onClosed = onClosed;
    }

    /**
     * The ✂ Capture Templates press: the tool over the project's target.
     *
     * <p>The tag is not pre-filled: <em>which file the editor has open</em> is host state with no member on
     * the contract for it, and growing one is exactly the move the platform's stop condition exists to refuse.
     * The tag menu is on the naming dialog either way.
     */
    public static void open(ActionContext context) {
        StudioServices services = context.services();
        open(services, Modals.owner(services), null);
    }

    /**
     * The overlay's ✂ Picture of this press: cuts a picture out of the window the overlay is over, rather than
     * out of the project's default target.
     *
     * <p>What the overlay adds is a target for <em>this</em> session, which makes the tool usable over a
     * window the project has never heard of, including a project that names no target at all. The override
     * is not written down. Pointing the bot at that window is the button beside this one, so a user who
     * wanted a picture does not silently get a re-pointed bot.
     *
     * <p>{@link ActionContext#overBounds()} is deliberately unused: the tool re-probes and raises its target
     * at save time so a window the user has since moved is still tracked, and a rectangle captured when the
     * HUD opened would be stale exactly then.
     */
    public static void pictureHere(ActionContext context) {
        StudioServices services = context.services();
        CaptureSource target = context.overWindowTitle().map(CaptureSource::window).orElse(null);
        open(services, Modals.owner(services), target, null, () -> {});
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
        open(services, owner, null, suggestedTag, onClosed);
    }

    /**
     * As {@link #open(StudioServices, Window, String, Runnable)}, capturing from {@code target} rather than
     * from the project's default — {@code null} means the default, so this is the one implementation.
     *
     * <p>The overlay editor's row is the caller with a target of its own: the window its HUD is drawn over.
     * The override lasts as long as the tool and changes no file.
     */
    public static void open(StudioServices services, Window owner, CaptureSource target,
                            String suggestedTag, Runnable onClosed) {
        Runnable done = onClosed == null ? () -> {} : onClosed;
        open(services, owner, target, suggestedTag, names -> done.run());
    }

    /**
     * As {@link #open(StudioServices, Window, CaptureSource, String, Runnable)}, handing {@code onSaved} the
     * names of the pictures saved while the tool was open — {@code []} when it never opened (no capture
     * target, or one already up) or nothing was saved. The picture chooser's Capture new… is the caller.
     */
    public static void open(StudioServices services, Window owner, CaptureSource target,
                            String suggestedTag, Consumer<List<String>> onSaved) {
        Consumer<List<String>> done = onSaved == null ? names -> {} : onSaved;
        // Single-instance: focus the live tool instead of stacking another one.
        if (active != null && active.toolbarStage != null && active.toolbarStage.isShowing()) {
            active.toolbarStage.toFront();
            done.accept(List.of());
            return;
        }
        new CaptureTemplates(services, owner, target, suggestedTag, done).start();
    }

    /** What {@link #recapture} hands back: the new picture, the frame it was cut from, and that frame's window. */
    @FunctionalInterface
    public interface Recaptured {
        void accept(BufferedImage picture, int frameWidth, int frameHeight, String windowTitle);
    }

    /**
     * One rectangle over the project's capture source, unnamed and unsaved — Manage Pictures' replace. The
     * same surface and the same settle-then-regrab as Capture one, without the toolbar: a picture being
     * replaced already has its name and its tags. {@code onCancelled} runs when no picture comes back, Esc and
     * a failed grab included, so a caller that stepped aside always returns.
     */
    public static void recapture(StudioServices services, Window owner, Recaptured onCaptured,
                                 Runnable onCancelled) {
        new CaptureTemplates(services, owner, null, null, names -> {}).recaptureOnce(onCaptured, onCancelled);
    }

    private void recaptureOnce(Recaptured onCaptured, Runnable onCancelled) {
        Consumer<EditorFrame.Failure> failed = failure -> {
            dropSurface();
            warn(failure.headline() + "\n\n" + failure.detail());
            onCancelled.run();
        };
        grab(frame -> surface = CaptureSurface.single(frame, CaptureSurface.Shape.RECT, region -> {
            surface.hide();
            grabAfterHiding(fresh -> {
                dropSurface();
                BufferedImage cropped = crop(fresh.image(), region);
                if (cropped == null) {
                    onCancelled.run();
                    return;
                }
                onCaptured.accept(cropped, fresh.image().getWidth(), fresh.image().getHeight(), windowTitle());
            }, failed);
        }, () -> {
            dropSurface();
            onCancelled.run();
        }), failed);
    }

    /** Disposes the drawing surface, if one is up. */
    private void dropSurface() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
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

        // The size readout, so the user always knows what resolution they are capturing at. Refreshed on every
        // grab: the tool follows a window that is resized between two captures, and so must the number.
        showSize(frame);
        size.setTextFill(Color.web("#8fa3bf"));
        size.setStyle("-fx-font-size: 11px;");

        HBox bar = new HBox(8, title, shapeToggle(), one, many, object, close, size);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));
        bar.setStyle(OverlayStage.PANEL);

        toolbarStage = OverlayStage.bar(bar, frame.placement());
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
     * {@code "▧ 1600×900"} — the size, in the frame's own pixels, of what is being captured: the size a saved
     * picture records as authored.
     *
     * <p>There is no project-wide reference size to compare it with: each picture records the size it was
     * authored at in its own sidecar and the matcher rescales against that.
     */
    private void showSize(EditorFrame frame) {
        size.setText("▧ " + frame.image().getWidth() + "×" + frame.image().getHeight());
    }

    // ── Capture one ────────────────────────────────────────────────────────────────────────────────────

    private void beginSingle() {
        toolbarStage.hide();
        grab(frame -> surface = CaptureSurface.single(frame, shape, this::onSingleRegion, this::endSession),
                this::failSession);
    }

    private void onSingleRegion(Region region) {
        surface.hide();
        grabAfterHiding(frame -> {
            try {
                BufferedImage cropped = crop(frame.image(), region);
                if (cropped == null) return;
                Optional<NamedCapture> named =
                        TemplateNaming.promptNew(services, null, cropped, suggestedTag);
                if (named.isEmpty()) return;
                save(cropped, named.get().name(), frame.image().getWidth(), frame.image().getHeight());
                TemplateLibrary.applyTags(services, Map.of(named.get().name(), named.get().tags()));
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
        grab(frame -> surface = CaptureSurface.many(frame, shape, this::onManyDone, this::endSession),
                this::failSession);
    }

    private void onManyDone(List<Region> regions) {
        surface.hide();
        if (regions.isEmpty()) {
            endSession();
            return;
        }
        grabAfterHiding(frame -> {
            try {
                List<BufferedImage> crops = new ArrayList<>();
                for (Region region : regions) {
                    BufferedImage cropped = crop(frame.image(), region);
                    if (cropped != null) crops.add(cropped);
                }
                if (crops.isEmpty()) return;
                TemplateNaming.Batch batch = TemplateNaming.showBatch(services, null, crops, suggestedTag);
                List<String> written = new ArrayList<>();
                for (NamedTemplate template : batch.templates()) {
                    save(template.image(), template.name(),
                            frame.image().getWidth(), frame.image().getHeight());
                    written.add(template.name());
                }
                TemplateLibrary.applyTags(services, batch.tagsFor(written));
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
            objectSurface = ObjectCaptureSurface.open(frame, this::onObjectExtracted, this::endSession);
        }, this::failSession);
    }

    /** The full frame the in-progress object cut was taken from, for its resolution sidecar. */
    private int objectFrameWidth;
    private int objectFrameHeight;

    private void onObjectExtracted(BufferedImage cut) {
        if (objectSurface != null) objectSurface.hide();
        try {
            Optional<NamedCapture> named = TemplateNaming.promptNew(services, null, cut, suggestedTag);
            if (named.isEmpty()) return;
            save(cut, named.get().name(), objectFrameWidth, objectFrameHeight);
            TemplateLibrary.applyTags(services, Map.of(named.get().name(), named.get().tags()));
        } catch (Exception failed) {
            warn("Failed to save the object: " + failed.getMessage());
        } finally {
            endSession();
        }
    }

    // ── Shared plumbing ────────────────────────────────────────────────────────────────────────────────

    /**
     * Re-grabs the target off the FX thread — raising the window first — and delivers the frame back on it,
     * with the toolbar's size readout brought up to date.
     *
     * <p>Every step re-grabs rather than reusing the last frame, which is what lets the tool follow a window
     * the user has moved or resized between two captures.
     */
    private void grab(Consumer<EditorFrame> onFrame, Consumer<EditorFrame.Failure> onFailure) {
        EditorFrame.grabAsync(services, target, frame -> {
            if (toolbarStage != null) showSize(frame);
            onFrame.accept(frame);
        }, onFailure);
    }

    /**
     * {@link #grab} once the surface just hidden has left the screen.
     *
     * <p>The picture is cut from this grab, and a desktop or monitor source is read off the screen itself:
     * grabbing in the same instant as {@code hide()} could catch the compositor still showing the surface, and
     * save its tint and its control bar into the picture. A window source waits too, briefly, inside the raise.
     */
    private void grabAfterHiding(Consumer<EditorFrame> onFrame, Consumer<EditorFrame.Failure> onFailure) {
        PauseTransition settle = new PauseTransition(Duration.millis(SETTLE_MS));
        settle.setOnFinished(e -> grab(onFrame, onFailure));
        settle.play();
    }

    /** How long a hidden surface is given to leave the screen before the picture is grabbed. */
    private static final int SETTLE_MS = 150;

    /** Reports a failed grab and returns to the toolbar. */
    private void failSession(EditorFrame.Failure failure) {
        warn(failure.headline() + "\n\n" + failure.detail());
        endSession();
    }

    /** Disposes the active surface (if any) and brings the mini-toolbar back. */
    private void endSession() {
        dropSurface();
        if (objectSurface != null) {
            objectSurface.close();
            objectSurface = null;
        }
        if (toolbarStage != null) toolbarStage.show();
    }

    /** Closes the toolbar and any live surface, and clears the single-instance reference. */
    private void closeTool() {
        dropSurface();
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
        onClosed.accept(List.copyOf(saved));
    }

    /**
     * Writes the picture and declares its {@code Pictures} constant when it has none, so a block that picks it
     * reads {@code Pictures.ORE} rather than the path. A constant the host would not add is reported, never
     * fatal: the picture is saved either way.
     */
    private void save(BufferedImage picture, String name, int frameWidth, int frameHeight) throws Exception {
        TemplateLibrary.saveTemplate(resources(), picture, name, frameWidth, frameHeight, windowTitle());
        saved.add(name);
        TemplateUses.declare(services.pluginValues(), name).ifPresent(services::status);
    }

    private Path resources() {
        return services.resourcesDir();
    }

    /** The window title saved beside a picture, or {@code null} for a screen, desktop or emulator source. */
    private String windowTitle() {
        return CaptureLabels.windowTitle(target != null ? target : EditorFrame.defaultSource(services));
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
        return r.shape() == CaptureSurface.Shape.ELLIPSE ? oval(sub) : sub;
    }

    /**
     * {@code image} inside its inscribed oval, transparent outside it, with a smooth rim.
     *
     * <p>The oval is painted as an anti-aliased mask and the image drawn into it ({@code SRC_IN}). Clipping to
     * the oval instead would ignore anti-aliasing — a clip is all or nothing per pixel
     * — and give every oval picture a stair-stepped edge the matcher had to forgive.
     */
    static BufferedImage oval(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(new Ellipse2D.Double(0, 0, w, h));
        g.setComposite(java.awt.AlphaComposite.SrcIn);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    /**
     * A warning over the game once the tool is up — ownerless and above fullscreen windows, as the naming
     * dialogs are ({@link TemplateNaming#place}) — and owned by the editor before it, when nothing covers it.
     */
    private void warn(String message) {
        Alert alert = services.theme().alert(Alert.AlertType.WARNING, message);
        TemplateNaming.place(alert, toolbarStage == null ? owner : null);
        alert.showAndWait();
    }
}
