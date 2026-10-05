package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.plugin.toolkit.ZoomPan;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.screen.ScreenCapture;
import com.botmaker.shared.opencv.ColorMatcher;
import com.botmaker.shared.opencv.RawColorMatch;
import javafx.animation.PauseTransition;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

/**
 * What a {@code Precision} does to a real frame, drawn on it: every pixel within ΔE of the target
 * tinted, each blob the search keeps in a solid box and each one too small in a dashed grey one, and the
 * coverage against {@code minCount} under it. The tint is {@link ColorMatcher#matchMask} and the boxes
 * {@link ColorMatcher#findClusters} — the pass the bot runs — computed off the FX thread, debounced, stale
 * results dropped by generation. Ctrl+scroll zooms and middle-drag pans ({@link ZoomPan}); a left click in a
 * pinning mode reports the pixel under the pointer.
 */
final class MatchOverlay {

    private static final Duration DEBOUNCE = Duration.millis(120);
    /** Magenta: the one hue no game UI uses for a health bar, so the tint never reads as part of the frame. */
    private static final int TINT_ARGB = 0x73FF00C8;
    /** A noisy frame can hold thousands of one-pixel blobs; past this, boxing them is only noise on noise. */
    private static final int MAX_SMALL_BOXES = 400;
    private static final double VIEW_WIDTH = 720, VIEW_HEIGHT = 405;

    private record Pin(int x, int y, boolean good) {}

    private record Pass(WritableImage tint, List<RawColorMatch> kept, List<RawColorMatch> small, int present) {}

    private final Canvas base = new Canvas();
    private final Canvas marks = new Canvas();
    private final Label readout = Styles.on(new Label(), Styles.CAPTION);
    private final VBox node;
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);
    private final AtomicLong generation = new AtomicLong();
    private final List<Pin> pins = new ArrayList<>();
    private final Rectangle probeSwatch = new Rectangle(18, 18);
    private final Label probeLabel = Styles.on(new Label(), Styles.CAPTION);

    private EditorFrame frame;
    /** The last pixel clicked, {@code {x, y}} in image pixels, or null. */
    private int[] probed;
    private java.awt.Color target;
    private PrecisionEditors.Settings settings;
    private Pass last;
    private Boolean pinning;
    private BiPredicate<java.awt.Color, Boolean> onPin = (c, good) -> true;

    MatchOverlay() {
        Group layers = new Group(base, marks);
        Pane surface = new Pane(layers);
        surface.setPrefSize(VIEW_WIDTH, VIEW_HEIGHT);
        surface.setMinSize(VIEW_WIDTH, VIEW_HEIGHT);
        surface.setMaxSize(VIEW_WIDTH, VIEW_HEIGHT);
        surface.setClip(new Rectangle(VIEW_WIDTH, VIEW_HEIGHT));
        ZoomPan.attach(surface, layers);
        readout.setWrapText(true);
        readout.setMaxWidth(VIEW_WIDTH);
        probeSwatch.setStroke(Color.gray(0.5));
        probeSwatch.setFill(Color.TRANSPARENT);
        probeLabel.setWrapText(true);
        probeLabel.setText("Click the frame to read a pixel.");
        HBox probe = new HBox(6, probeSwatch, probeLabel);
        probe.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        node = new VBox(6, surface, hintLine(), probe, readout);
        debounce.setOnFinished(e -> run());
        // The canvas's own coordinates are image pixels whatever the zoom: ZoomPan transforms the group above it.
        // Every left click answers, pin mode or not: a crosshair where it landed, the pixel's colour beside the
        // frame, and the blob it belongs to outlined.
        marks.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || frame == null) return;
            int x = (int) e.getX(), y = (int) e.getY();
            if (x < 0 || y < 0 || x >= frame.image().getWidth() || y >= frame.image().getHeight()) return;
            java.awt.Color colour = new java.awt.Color(frame.image().getRGB(x, y));
            probed = new int[] {x, y};
            refreshProbe();
            if (pinning != null && onPin.test(colour, pinning)) pins.add(new Pin(x, y, pinning));
            draw();
        });
        // Marks are sized per screen pixel, so a zoom redraws them.
        marks.localToSceneTransformProperty().addListener((o, was, is) -> draw());
    }

    /** The swatch and sentence for the last pixel clicked, against the target and tolerance as they are now. */
    private void refreshProbe() {
        if (probed == null || frame == null) return;
        java.awt.Color colour = new java.awt.Color(frame.image().getRGB(probed[0], probed[1]));
        probeSwatch.setFill(Color.rgb(colour.getRed(), colour.getGreen(), colour.getBlue()));
        probeLabel.setText(probeText(probed[0], probed[1], colour, target,
                settings == null ? 0 : settings.deltaE()));
    }

    /**
     * A clicked pixel, in words: where, its colour as RGB and hex, and — once there is a target — how far it
     * is from it and whether the search counts it.
     */
    static String probeText(int x, int y, java.awt.Color pixel, java.awt.Color target, double deltaE) {
        String where = String.format("(%d, %d)  RGB %d, %d, %d  %s", x, y,
                pixel.getRed(), pixel.getGreen(), pixel.getBlue(), hex(pixel));
        if (target == null) return where + "  — no target colour to compare with yet";
        double distance = com.botmaker.sdk.api.vision.Pixel.distance(pixel, target);
        return where + String.format("  ΔE %s from the target — %s",
                distance < 0.05 ? "0" : String.format("%.1f", distance),
                distance <= deltaE ? "matches" : "does not match");
    }

    static String hex(java.awt.Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** How many image pixels one screen pixel spans at the current zoom, so a mark keeps its on-screen size. */
    private double perScreenPixel() {
        double scale = marks.getLocalToSceneTransform().getMxx();
        return scale > 0 ? 1 / scale : 1;
    }

    private static Label hintLine() {
        return Styles.on(new Label("Ctrl+scroll to zoom, middle-drag to pan. Tinted pixels match; solid boxes "
                + "are blobs the search keeps, dashed ones are too small."), Styles.CAPTION);
    }

    Node node() {
        return node;
    }

    /** Asked for each pin before it is drawn: {@code false} refuses it, so no pin shows that taught nothing. */
    void onPin(BiPredicate<java.awt.Color, Boolean> onPinned) {
        this.onPin = onPinned;
    }

    /** {@code true}: clicks are "should match"; {@code false}: "should not"; {@code null}: clicks do nothing. */
    void mode(Boolean shouldMatch) {
        this.pinning = shouldMatch;
    }

    /** The frame under the overlay, once one arrived. */
    java.util.Optional<EditorFrame> frame() {
        return java.util.Optional.ofNullable(frame);
    }

    void clearPins() {
        pins.clear();
        draw();
    }

    /** Puts {@code frame} under the overlay; pins taken on another frame go with it. */
    void show(EditorFrame frame) {
        this.frame = frame;
        pins.clear();
        probed = null;
        probeSwatch.setFill(Color.TRANSPARENT);
        probeLabel.setText("Click the frame to read a pixel.");
        last = null;
        Image image = ScreenCapture.toFxImage(frame.image());
        for (Canvas c : List.of(base, marks)) {
            c.setWidth(image.getWidth());
            c.setHeight(image.getHeight());
        }
        base.getGraphicsContext2D().drawImage(image, 0, 0);
        schedule();
    }

    void update(java.awt.Color target, PrecisionEditors.Settings settings) {
        this.target = target;
        this.settings = settings;
        refreshProbe();
        schedule();
    }

    private void schedule() {
        if (frame == null) {
            readout.setText("No frame yet: Frame… grabs one.");
            return;
        }
        if (target == null || settings == null) {
            generation.incrementAndGet();
            readout.setText(PrecisionEditors.TargetColor.describe(null));
            last = null;
            draw();
            return;
        }
        debounce.playFromStart();
    }

    private void run() {
        BufferedImage image = frame.image();
        java.awt.Color c = target;
        PrecisionEditors.Settings s = settings;
        long gen = generation.incrementAndGet();
        Async.load("precision-overlay", () -> search(image, c, s), pass -> {
            if (generation.get() != gen) return;
            last = pass;
            boolean found = pass.present() >= s.minCount() && !pass.kept().isEmpty();
            readout.setText(String.format("%s — %d blob%s kept, %d too small — coverage %,d px %s minCount %,d %s",
                    PrecisionEditors.TargetColor.describe(c), pass.kept().size(),
                    pass.kept().size() == 1 ? "" : "s", pass.small().size(), pass.present(),
                    pass.present() >= s.minCount() ? "≥" : "<", s.minCount(), found ? "✓" : "✗"));
            draw();
        }, why -> {
            if (generation.get() == gen) readout.setText("Could not search this frame: " + why);
        });
    }

    /**
     * The bot's own pass, off the FX thread. Every blob is asked for ({@code minArea} 1, no count gate) so the
     * too-small ones can be drawn too; the kept list is what {@code findClusters(…, minArea, 0)} returns.
     */
    private static Pass search(BufferedImage image, java.awt.Color c, PrecisionEditors.Settings s) {
        int w = image.getWidth(), h = image.getHeight();
        boolean[] mask = ColorMatcher.matchMask(image, c, s.deltaE());
        int[] argb = new int[w * h];
        for (int i = 0; i < mask.length; i++) if (mask[i]) argb[i] = TINT_ARGB;
        WritableImage tint = new WritableImage(w, h);
        tint.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        List<RawColorMatch> all = ColorMatcher.findClusters(image, c, s.deltaE(), 1, 0);
        List<RawColorMatch> kept = all.stream().filter(m -> m.pixelCount() >= s.minArea()).toList();
        List<RawColorMatch> small = all.stream().filter(m -> m.pixelCount() < s.minArea()).toList();
        return new Pass(tint, kept, small, ColorMatcher.matchCount(image, c, s.deltaE()));
    }

    private void draw() {
        GraphicsContext g = marks.getGraphicsContext2D();
        g.clearRect(0, 0, marks.getWidth(), marks.getHeight());
        if (last != null) {
            g.drawImage(last.tint(), 0, 0);
            g.setLineWidth(2);
            g.setLineDashes();
            g.setStroke(Color.LIME);
            for (RawColorMatch m : last.kept()) g.strokeRect(m.x(), m.y(), m.width(), m.height());
            g.setStroke(Color.GRAY);
            g.setLineDashes(4, 4);
            last.small().stream().limit(MAX_SMALL_BOXES)
                    .forEach(m -> g.strokeRect(m.x(), m.y(), m.width(), m.height()));
            g.setLineDashes();
        }
        // Marks keep their size on screen at any zoom: a frame is usually drawn at a fraction of its pixels.
        double unit = perScreenPixel();
        if (probed != null && last != null) {
            // The blob the clicked pixel belongs to, whichever list it is in: the match that pixel makes.
            List<RawColorMatch> all = new ArrayList<>(last.kept());
            all.addAll(last.small());
            all.stream().filter(m -> contains(m, probed[0], probed[1])).findFirst().ifPresent(m -> {
                g.setStroke(Color.YELLOW);
                g.setLineWidth(4 * unit);
                double pad = 3 * unit;
                g.strokeRect(m.x() - pad, m.y() - pad, m.width() + 2 * pad, m.height() + 2 * pad);
            });
        }
        for (Pin p : pins) {
            double r = 7 * unit;
            g.setFill(p.good() ? Color.LIMEGREEN : Color.RED);
            g.fillOval(p.x() - r, p.y() - r, 2 * r, 2 * r);
            g.setStroke(Color.WHITE);
            g.setLineWidth(2 * unit);
            g.strokeOval(p.x() - r, p.y() - r, 2 * r, 2 * r);
        }
        if (probed != null) {
            double cx = probed[0] + 0.5, cy = probed[1] + 0.5;
            double arm = 14 * unit, ring = 6 * unit;
            for (Color c : List.of(Color.BLACK, Color.WHITE)) {
                // A dark stroke under a light one: the crosshair reads on any frame.
                g.setStroke(c);
                g.setLineWidth((c == Color.BLACK ? 4 : 2) * unit);
                g.strokeLine(cx - arm, cy, cx - ring, cy);
                g.strokeLine(cx + ring, cy, cx + arm, cy);
                g.strokeLine(cx, cy - arm, cx, cy - ring);
                g.strokeLine(cx, cy + ring, cx, cy + arm);
                g.strokeOval(cx - ring, cy - ring, 2 * ring, 2 * ring);
            }
        }
    }

    private static boolean contains(RawColorMatch m, int x, int y) {
        return x >= m.x() && y >= m.y() && x < m.x() + m.width() && y < m.y() + m.height();
    }
}
