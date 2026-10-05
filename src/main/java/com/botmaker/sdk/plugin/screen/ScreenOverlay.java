package com.botmaker.sdk.plugin.screen;

import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * <b>What the user does with the pixels</b> — one half of editor-time capture.
 *
 * <p>Given a frame, it shows a borderless overlay of it and lets the user rubber-band a region, click a
 * point, or sample a colour under a magnifier; it also runs the multi-monitor screen chooser and refuses to
 * overlay a blank grab. Every overlay shows what to do in a corner, says the numbers it will report while the
 * pointer moves, and is cancelled by Esc or a right-click.
 *
 * <p><b>It never asks where the frame came from.</b> A {@link ScreenShot} carries pixels, bounds and two
 * flags, and this class names no capture target, no window handle and no emulator. Which pixels to grab is
 * {@link ShotSource}'s question: {@link DesktopSource} for a pick over this machine's screens,
 * {@link FrameShotSource} for a pick on a frame {@link EditorFrame} already grabbed.
 *
 * <p>Capturing the frame <em>before</em> the overlay appears is deliberate: it avoids any overlay-in-shot
 * timing problem and lets the selection map 1:1 against real pixels. The grab is strictly off the FX thread —
 * native focus, a sleep for the compositor and a Robot or CLI capture all block, and running them on the FX
 * thread froze the whole machine while a modal overlay was already up.
 *
 * <p><b>A pick reports inside the frame it showed, then through its {@link PickSpace}</b> — see
 * {@link #framePoint(int, int, double, double, double, double)}. The readout and the result go through the same mapping, so the number on screen is the
 * number written.
 */
public final class ScreenOverlay {

    /** Where the multi-monitor chooser remembers the last pick. Per-user, JDK-owned, no host involved. */
    private static final Preferences PREFS = Preferences.userNodeForPackage(ScreenOverlay.class);
    private static final String LAST_SCREEN = "captureScreen";

    /** A drag smaller than this, in overlay pixels, is a slip rather than a region. */
    private static final double MIN_DRAG = 3;

    private static final String READOUT_STYLE = "-fx-background-color: rgba(0,0,0,0.75); -fx-text-fill: white;"
            + " -fx-padding: 2 6 2 6; -fx-font-family: monospace;";
    private static final String HINT_STYLE = "-fx-background-color: rgba(0,0,0,0.8); -fx-text-fill: white;"
            + " -fx-padding: 6 10 6 10; -fx-background-radius: 6; -fx-font-size: 13px;";
    private static final String CANCEL = " Esc or right-click cancels.";

    private final ShotSource source;
    private final PickSpace space;
    private final java.awt.Rectangle origin;

    /**
     * An overlay whose picks are reported as the frame's own pixels.
     *
     * @param source where frames come from; never {@code null}
     */
    public ScreenOverlay(ShotSource source) {
        this(source, PickSpace.RELATIVE, null);
    }

    /**
     * An overlay whose picks, and the readout that previews them, are mapped through {@code space}: a pick in
     * {@link PickSpace#ABSOLUTE} space has {@code origin}, the frame's place on the desktop, added back.
     *
     * @param origin where the frame's top-left is on the desktop, or {@code null} when it is nowhere
     */
    public ScreenOverlay(ShotSource source, PickSpace space, java.awt.Rectangle origin) {
        this.source = source;
        this.space = space;
        this.origin = origin;
    }

    /** What a {@link #pickColor} overlay reports: where the click landed, and the pixel that was under it. */
    public record ScreenPick(int x, int y, java.awt.Color color) {}

    /**
     * Interactive rubber-band selection reporting {@code [x, y, width, height]} in the frame's pixels, then
     * this overlay's {@link PickSpace}. Does nothing if the user cancels or capture is unavailable.
     */
    public void selectRegion(Window owner, Consumer<int[]> onSelected) {
        grabAsync(owner, shot -> showRegion(owner, shot, "Drag to select a region.", onSelected));
    }

    /**
     * Interactive point pick: a magnified close-up follows the cursor with a live readout of the coordinate;
     * a left-click sets it. Reports {@code [x, y]} in the frame's pixels, then this
     * overlay's {@link PickSpace}.
     */
    public void pickPoint(Window owner, Consumer<int[]> onPicked) {
        grabAsync(owner, shot -> showPointOverlay(owner, shot, false,
                pick -> onPicked.accept(new int[]{pick.x(), pick.y()})));
    }

    /**
     * The same magnified overlay as {@link #pickPoint}, reporting the <em>colour</em> under the cursor rather
     * than the coordinate — the readout shows the hex, and the lens is what makes a one-pixel target hittable.
     *
     * <p>It reads the pixel out of the frozen screenshot rather than off the live screen, so the colour
     * reported is exactly the one the user was looking at when they clicked, even if the game repainted in
     * between.
     */
    public void pickColor(Window owner, Consumer<ScreenPick> onPicked) {
        grabAsync(owner, shot -> showPointOverlay(owner, shot, true, onPicked));
    }

    /**
     * Grabs the target's pixels <b>off the FX thread</b>, then hops back to the FX thread to (optionally) show
     * the screen chooser and hand the finished {@link ScreenShot} to {@code onShot}.
     */
    private void grabAsync(Window owner, Consumer<ScreenShot> onShot) {
        Async.load("screen-capture-grab", () -> {
            try {
                return source.grab(owner);
            } catch (Throwable ex) {
                System.err.println("Screen capture failed: " + ex.getMessage());
                return Grab.failed();
            }
        }, grab -> finishGrab(owner, grab, onShot));
    }

    /**
     * FX-thread completion of {@link #grabAsync}: runs the screen chooser if one is pending, refuses a blank
     * grab so the user is never trapped behind a uniform full-screen overlay, and hands the shot on.
     */
    private void finishGrab(Window owner, Grab grab, Consumer<ScreenShot> onShot) {
        ScreenShot shot = grab.shot();
        if (shot == null && grab.desktopForChooser() != null) {
            BufferedImage desktop = grab.desktopForChooser();
            List<Screen> screens = Screen.getScreens();
            Screen screen = chooseScreen(owner, screens, desktop);
            if (screen == null) return; // chooser cancelled
            BufferedImage cropped = Screens.cropToScreen(desktop, screens, screen);
            shot = new ScreenShot(cropped, screen.getBounds(), true, !EditorFrame.usable(cropped));
        }
        if (shot == null || shot.blank()) {
            showBlankWarning(owner);
            return;
        }
        onShot.accept(shot);
    }

    /**
     * Shown instead of an overlay when the grab failed or came back uniform. On a Wayland session that means
     * no screenshot program shared could use was installed; anywhere else, that the screen could not be read.
     */
    private static void showBlankWarning(Window owner) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.getDialogPane().getStyleClass().add(Styles.UNTHEMED);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Screen capture unavailable");
        alert.setHeaderText("Couldn't capture the screen");
        alert.setContentText(System.getenv("WAYLAND_DISPLAY") != null
                ? "The capture came back empty. On a Wayland session BotMaker reads the screen through a "
                  + "screenshot program: install spectacle (KDE), grim (Sway, Hyprland) or gnome-screenshot "
                  + "(GNOME), or log in to an Xorg session, then try again."
                : "The capture came back empty. Make sure the screen is not locked or asleep, then try again.");
        alert.showAndWait();
    }

    /**
     * Asks the user which screen to capture, showing each monitor's details and a preview thumbnail (cropped
     * from {@code desktop}). The row matching the remembered default is preselected and the chosen index is
     * saved back. Returns the chosen {@link Screen}, or {@code null} if cancelled.
     *
     * <p>{@link Preferences} is the JDK's own per-user store: <em>which monitor this user last captured</em>
     * is not something only the host can know, so the host is not asked to keep it.
     */
    private static Screen chooseScreen(Window owner, List<Screen> screens, BufferedImage desktop) {
        ToggleGroup group = new ToggleGroup();
        VBox rows = new VBox(8);
        rows.setPadding(new Insets(12));

        int saved = PREFS.getInt(LAST_SCREEN, -1);
        int preselect = (saved >= 0 && saved < screens.size()) ? saved : 0;

        for (int i = 0; i < screens.size(); i++) {
            Screen screen = screens.get(i);
            Rectangle2D b = screen.getBounds();

            ImageView thumb = new ImageView(toFxImage(Screens.cropToScreen(desktop, screens, screen)));
            thumb.setPreserveRatio(true);
            thumb.setFitWidth(240);

            StringBuilder detail = new StringBuilder(String.format(
                    "Screen %d — %d×%d  @ (%d, %d)", i + 1,
                    (int) b.getWidth(), (int) b.getHeight(), (int) b.getMinX(), (int) b.getMinY()));
            if (screen.equals(Screen.getPrimary())) detail.append("  •  Primary");
            if (screen.getOutputScaleX() != 1.0) detail.append(String.format("  •  scale ×%.2f", screen.getOutputScaleX()));

            RadioButton radio = new RadioButton();
            radio.setToggleGroup(group);
            radio.setUserData(i);
            if (i == preselect) radio.setSelected(true);

            VBox cell = new VBox(4, thumb, new Label(detail.toString()));
            HBox row = new HBox(8, radio, cell);
            row.setOnMouseClicked(e -> radio.setSelected(true));
            rows.getChildren().add(row);
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getStyleClass().add(Styles.UNTHEMED);
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Capture screen");
        dialog.setHeaderText("Which screen do you want to capture?");
        dialog.getDialogPane().setContent(new ScrollPane(rows));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return null;
        int chosen = (group.getSelectedToggle() != null)
                ? (Integer) group.getSelectedToggle().getUserData() : preselect;
        PREFS.putInt(LAST_SCREEN, chosen);
        return screens.get(chosen);
    }

    /**
     * The one rubber-band overlay. While dragging, a readout beside the band says the region it will report;
     * on release a region at least {@link #MIN_DRAG} pixels each way is handed on, clamped to the frame and
     * mapped through this overlay's {@link PickSpace}.
     */
    private void showRegion(Window owner, ScreenShot shot, String hint, Consumer<int[]> onRegion) {
        Pane pane = new Pane(background(shot));

        Rectangle selection = new Rectangle();
        selection.setFill(Color.color(0.3, 0.6, 1.0, 0.25));
        selection.setStroke(Color.web("#2f80ed"));
        selection.setStrokeWidth(1.5);
        selection.setVisible(false);
        selection.setMouseTransparent(true);
        Label readout = readout();
        pane.getChildren().addAll(selection, readout);

        Stage stage = overlayStage(owner, shot, pane, hint);

        final double[] start = new double[2];
        pane.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            start[0] = e.getX();
            start[1] = e.getY();
            selection.setX(e.getX());
            selection.setY(e.getY());
            selection.setWidth(0);
            selection.setHeight(0);
            selection.setVisible(true);
        });
        pane.setOnMouseDragged(e -> {
            if (!selection.isVisible()) return;
            double x = clamp(e.getX(), 0, pane.getWidth());
            double y = clamp(e.getY(), 0, pane.getHeight());
            selection.setX(Math.min(start[0], x));
            selection.setY(Math.min(start[1], y));
            selection.setWidth(Math.abs(x - start[0]));
            selection.setHeight(Math.abs(y - start[1]));
            int[] r = space.region(sourceRect(shot, pane, selection), origin);
            readout.setText("%d, %d   %d × %d".formatted(r[0], r[1], r[2], r[3]));
            double rx = selection.getX() + selection.getWidth() + 6;
            double ry = selection.getY() + selection.getHeight() + 6;
            readout.relocate(rx + 160 > pane.getWidth() ? selection.getX() : rx,
                    ry + 24 > pane.getHeight() ? selection.getY() - 26 : ry);
            readout.setVisible(true);
        });
        pane.setOnMouseReleased(e -> {
            if (!selection.isVisible() || e.getButton() != MouseButton.PRIMARY) return;
            stage.close();
            if (selection.getWidth() < MIN_DRAG || selection.getHeight() < MIN_DRAG) return;
            onRegion.accept(space.region(sourceRect(shot, pane, selection), origin));
        });
        stage.show();
    }

    /**
     * Point-pick overlay: a zoomed close-up follows the cursor with a crosshair and a live readout; left-click
     * reports the point, together with the pixel that was under it.
     *
     * <p>{@code showColor} only changes what the readout says — coordinates for a point pick, the hex value
     * for a colour pick. Both picks want the same lens, and one overlay is what keeps them from drifting.
     */
    private void showPointOverlay(Window owner, ScreenShot shot, boolean showColor, Consumer<ScreenPick> onPicked) {
        BufferedImage screenshot = shot.image();
        ImageView background = background(shot);
        Image fxImage = background.getImage();
        Pane pane = new Pane(background);

        // Mutable so the scroll wheel can change it: an array rather than a field, since the overlay is a
        // local scene that lives only as long as the pick.
        final double[] zoom = {16.0};
        final double lensSize = 220;
        ImageView lens = new ImageView(fxImage);
        lens.setManaged(false);
        lens.setFitWidth(lensSize);
        lens.setFitHeight(lensSize);
        lens.setPreserveRatio(false);
        // The whole point of a loupe is to see *one* pixel. Smoothing blends it into its neighbours, which is
        // exactly the question being asked — so off, and the pixels read as the squares they are.
        lens.setSmooth(false);
        lens.setVisible(false);
        Rectangle lensBorder = new Rectangle(lensSize, lensSize);
        lensBorder.setManaged(false);
        lensBorder.setFill(Color.TRANSPARENT);
        lensBorder.setStroke(Color.web("#2f80ed"));
        lensBorder.setStrokeWidth(2);
        lensBorder.setVisible(false);
        // The one pixel under the cursor, boxed at the lens centre — without it the magnified field is all
        // equally in focus and "which square am I about to take" is a guess.
        Rectangle crosshair = new Rectangle();
        crosshair.setManaged(false);
        crosshair.setFill(Color.TRANSPARENT);
        crosshair.setStroke(Color.WHITE);
        crosshair.setStrokeWidth(1);
        crosshair.setVisible(false);
        Rectangle crosshairOutline = new Rectangle();
        crosshairOutline.setManaged(false);
        crosshairOutline.setFill(Color.TRANSPARENT);
        crosshairOutline.setStroke(Color.BLACK);
        crosshairOutline.setStrokeWidth(1);
        crosshairOutline.setVisible(false);
        Label readout = readout();
        Rectangle swatch = new Rectangle(14, 14);
        swatch.setStroke(Color.web("#ffffff", 0.6));
        if (showColor) readout.setGraphic(swatch);
        for (javafx.scene.Node n : List.of(lens, lensBorder, crosshairOutline, crosshair)) n.setMouseTransparent(true);
        pane.getChildren().addAll(lens, lensBorder, crosshairOutline, crosshair, readout);

        Stage stage = overlayStage(owner, shot, pane, showColor
                ? "Click the colour you want. Scroll to zoom."
                : "Click to set the point. Scroll to zoom.");

        // The last cursor position, so a scroll can re-draw the lens where the pointer already is instead of
        // waiting for the next move.
        final double[] at = {-1, -1};
        Runnable place = () -> {
            if (at[0] < 0) return;
            double mx = at[0], my = at[1];
            double sx = scaleX(screenshot, pane), sy = scaleY(screenshot, pane);
            double px = mx * sx, py = my * sy;
            double viewW = lensSize / zoom[0], viewH = lensSize / zoom[0];
            double vx = clamp(px - viewW / 2, 0, Math.max(0, screenshot.getWidth() - viewW));
            double vy = clamp(py - viewH / 2, 0, Math.max(0, screenshot.getHeight() - viewH));
            lens.setViewport(new Rectangle2D(vx, vy, viewW, viewH));
            // Place lens near the cursor without covering it.
            double lx = mx + 16, ly = my + 16;
            if (lx + lensSize > pane.getWidth()) lx = mx - lensSize - 16;
            if (ly + lensSize + 26 > pane.getHeight()) ly = my - lensSize - 16 - 26;
            lens.relocate(lx, ly);
            lensBorder.relocate(lx, ly);

            // The square the cursor is over, in lens coordinates. Derived from the viewport rather than
            // assumed to be the centre, which it is not once the lens is clamped against an edge.
            double cell = zoom[0];
            double cx = lx + (Math.floor(px) - vx) * cell;
            double cy = ly + (Math.floor(py) - vy) * cell;
            crosshair.setWidth(cell);
            crosshair.setHeight(cell);
            crosshair.relocate(cx, cy);
            crosshairOutline.setWidth(cell + 2);
            crosshairOutline.setHeight(cell + 2);
            crosshairOutline.relocate(cx - 1, cy - 1);

            if (showColor) {
                java.awt.Color c = pixelAt(screenshot, pane, mx, my);
                readout.setText("#%02X%02X%02X".formatted(c.getRed(), c.getGreen(), c.getBlue()));
                swatch.setFill(Color.rgb(c.getRed(), c.getGreen(), c.getBlue()));
            } else {
                // The number the pick will report, not the frame's — the readout is a preview of the value.
                int[] under = space.point(sourcePoint(shot, pane, mx, my), origin);
                readout.setText(under[0] + ", " + under[1]);
            }
            readout.relocate(lx, ly + lensSize + 2);
            lens.setVisible(true);
            lensBorder.setVisible(true);
            crosshair.setVisible(true);
            crosshairOutline.setVisible(true);
            readout.setVisible(true);
        };
        pane.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            at[0] = e.getX();
            at[1] = e.getY();
            place.run();
        });
        // Scroll to magnify. 4× still shows context, 64× is one pixel filling a third of the lens.
        pane.setOnScroll(e -> {
            double next = zoom[0] * (e.getDeltaY() > 0 ? 1.5 : 1 / 1.5);
            zoom[0] = clamp(next, 4, 64);
            place.run();
        });
        pane.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            stage.close();
            int[] picked = space.point(sourcePoint(shot, pane, e.getX(), e.getY()), origin);
            onPicked.accept(new ScreenPick(picked[0], picked[1], pixelAt(screenshot, pane, e.getX(), e.getY())));
        });
        stage.show();
    }

    /** The frozen frame, stretched over the whole overlay so pane coordinates map predictably onto it. */
    private static ImageView background(ScreenShot shot) {
        ImageView background = new ImageView(toFxImage(shot.image()));
        background.setPreserveRatio(false);
        background.setMouseTransparent(true);
        return background;
    }

    private static Label readout() {
        Label readout = new Label();
        readout.setManaged(false);
        readout.setMouseTransparent(true);
        readout.setStyle(READOUT_STYLE);
        readout.setVisible(false);
        return readout;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(v, max));
    }

    // =========================================================================
    // What a pick reports: the frame's own pixel space, then the overlay's PickSpace
    // =========================================================================

    /**
     * A point on an overlay, as a coordinate <b>inside the frame that was captured</b> — the top-left of the
     * chosen screen, window or virtual desktop being {@code 0,0}. {@link PickSpace} adds the desktop origin
     * back for a call that reads desktop pixels.
     *
     * <p>A bot reads one capture source and works in that source's space ({@code docs/display-pipeline.md}
     * §6–7), so a point picked on a screen whose origin is {@code 1920,0} and reported desktop-absolute was a
     * whole monitor to the right of where it was clicked, the moment a source-relative call used it.
     *
     * <p>Scaled through the image rather than taken as logical pixels, for the same reason a crop is: on a
     * HiDPI screen the grab is in device pixels and the overlay is laid out in logical ones, and it is the grab
     * a template is matched against.
     */
    private static int[] sourcePoint(ScreenShot shot, Pane pane, double x, double y) {
        return framePoint(shot.image(), pane, x, y);
    }

    private static int[] framePoint(BufferedImage image, Pane pane, double x, double y) {
        return framePoint(image.getWidth(), image.getHeight(), pane.getWidth(), pane.getHeight(), x, y);
    }

    /**
     * The pixel under {@code (x, y)} on an overlay {@code paneW}×{@code paneH} showing a
     * {@code imageW}×{@code imageH} frame: floored, because the pixel a pointer is over is the one whose square
     * contains it — the square the lens's crosshair boxes — and clamped to the frame.
     */
    static int[] framePoint(int imageW, int imageH, double paneW, double paneH, double x, double y) {
        return new int[]{
                (int) clamp(Math.floor(x * scale(imageW, paneW)), 0, imageW - 1),
                (int) clamp(Math.floor(y * scale(imageH, paneH)), 0, imageH - 1)};
    }

    private static int[] sourceRect(ScreenShot shot, Pane pane, Rectangle selection) {
        BufferedImage image = shot.image();
        return frameRect(image.getWidth(), image.getHeight(), pane.getWidth(), pane.getHeight(),
                selection.getX(), selection.getY(), selection.getWidth(), selection.getHeight());
    }

    /**
     * A selection rectangle in the frame's pixels — {@code [x, y, w, h]} — clamped to the frame, so a band
     * dragged past the overlay's edge never reports a region the frame does not have, and never an empty one.
     */
    static int[] frameRect(int imageW, int imageH, double paneW, double paneH,
                           double x, double y, double w, double h) {
        double sx = scale(imageW, paneW);
        double sy = scale(imageH, paneH);
        int left = (int) clamp(Math.round(x * sx), 0, imageW - 1);
        int top = (int) clamp(Math.round(y * sy), 0, imageH - 1);
        int right = (int) clamp(Math.round((x + w) * sx), left + 1, imageW);
        int bottom = (int) clamp(Math.round((y + h) * sy), top + 1, imageH);
        return new int[]{left, top, right - left, bottom - top};
    }

    /** Frame pixels per overlay pixel; 1.0 before the overlay has been laid out, which is the honest fallback. */
    private static double scale(int image, double pane) {
        return pane <= 0 ? 1 : image / pane;
    }

    private static double scaleX(BufferedImage image, Pane pane) {
        return scale(image.getWidth(), pane.getWidth());
    }

    private static double scaleY(BufferedImage image, Pane pane) {
        return scale(image.getHeight(), pane.getHeight());
    }

    /**
     * The pixel of {@code screenshot} under a mouse position given in {@code pane}'s coordinates, read out of
     * the frozen screenshot so what the readout previewed and what the click commits are the same colour —
     * and the same pixel a point pick names.
     */
    private static java.awt.Color pixelAt(BufferedImage screenshot, Pane pane, double mouseX, double mouseY) {
        int[] p = framePoint(screenshot, pane, mouseX, mouseY);
        return new java.awt.Color(screenshot.getRGB(p[0], p[1]), false);
    }

    /**
     * A borderless, modal overlay stage over the shot, with {@code pane} as its content, a hint in a corner,
     * and Esc or a right-click to cancel.
     *
     * <p>For a screen it opens true-fullscreen on that monitor; for a frame it is placed and sized exactly over
     * the frame's rectangle and promoted above fullscreen windows ({@link OverlayStage}) — a plain
     * always-on-top stage loses to a fullscreen game, which is exactly what a pick on a game frame sits over.
     *
     * <p>The hint moves to the bottom while the pointer is near the top, so it never hides what the user is
     * aiming at.
     */
    private static Stage overlayStage(Window owner, ScreenShot shot, Pane pane, String hint) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        Rectangle2D bounds = shot.bounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        if (shot.fullScreen()) {
            stage.setFullScreen(true);
            stage.setFullScreenExitHint("");
            stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        } else {
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            stage.setAlwaysOnTop(true);
            OverlayStage.promoteAboveFullscreen(stage);
        }

        Label tip = new Label(hint + CANCEL);
        tip.setManaged(false);
        tip.setMouseTransparent(true);
        tip.setStyle(HINT_STYLE);
        tip.relocate(12, 12);
        pane.getChildren().add(tip);
        pane.addEventHandler(MouseEvent.MOUSE_MOVED, e -> placeTip(tip, pane, e.getY()));
        pane.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> placeTip(tip, pane, e.getY()));

        Scene scene = new Scene(pane);
        if (pane.getChildren().getFirst() instanceof ImageView background) {
            background.fitWidthProperty().bind(scene.widthProperty());
            background.fitHeightProperty().bind(scene.heightProperty());
        }
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) stage.close(); });
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.SECONDARY) return;
            e.consume();
            stage.close();
        });
        stage.setScene(scene);
        return stage;
    }

    private static void placeTip(Label tip, Pane pane, double pointerY) {
        double top = 12;
        double bottom = pane.getHeight() - tip.getHeight() - 12;
        tip.relocate(12, pointerY < tip.getHeight() + 40 && bottom > top ? bottom : top);
    }

    /**
     * {@code image} as a JavaFX {@link Image}, copied pixel for pixel. Returns {@code null} for a {@code null}
     * image, so a caller feeding it a best-effort grab can pass the result straight through.
     *
     * <p>It used to encode a PNG and decode it again, which on a 4K desktop took the better part of a second
     * before every overlay appeared; one array copy is the same pixels without the round trip, and still needs
     * no {@code javafx-swing}.
     */
    public static Image toFxImage(BufferedImage image) {
        if (image == null) return null;
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        WritableImage fx = new WritableImage(w, h);
        fx.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        return fx;
    }
}
