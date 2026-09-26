package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.vision.Precision;
import com.botmaker.sdk.plugin.screen.ColorSampler;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.source.SurfaceMenu;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.lang.reflect.Executable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Editor for a {@code Precision} — every knob that decides whether the {@code Pixel} facade calls something a
 * match, in one dialog because they are one SDK type.
 *
 * <p>Each of the three numbers fails silently on its own. ΔE has no obvious top and is not a percentage, and
 * being wrong about it fails as "the bot never sees the colour" rather than as an error. {@code minArea} is an
 * <em>area</em>, and the mistake it invites is reading it as a length — "at least 20 pixels" typed as
 * {@code 20} asks for a blob of about 4×5, not one 20 across. {@code minCount} is the pixels of the colour
 * present at all, clustered or not, which sounds like the same question as the area and is not.
 *
 * <p>So the editor answers each of them by showing rather than telling: the ΔE slider is laid out against the
 * SDK's own named anchors, with the darkest and lightest shades it still accepts drawn beside the target; the
 * area spinner draws the blob <b>to scale</b> over a 1:1 grid (drawing it as a radius would teach exactly the
 * wrong model); and since 2026-09-26 the dialog opens on a frozen frame of the bot's source with every match
 * drawn on it ({@link MatchOverlay}) — the target colour read from the call itself
 * ({@link SlotContext#argumentValue}) — and learns the tolerance from pins the user drops on pixels that should
 * and should not match ({@link ToleranceTeacher}). Without a frame these are all abstractions.
 *
 * <p><b>Only the knobs the call can use are shown.</b> The SDK collapsed colour and quantity into one type,
 * which means {@code matchesAt} and {@code coverage} are handed an area and a count they cannot act on, and
 * {@code findInRange} a tolerance it has no target colour to measure from. Their javadoc says so; this editor
 * enforces it, reading {@link SlotContext#enclosingExecutable()} so a slot on {@code matchesAt} offers the
 * tolerance alone. A knob that cannot change the answer should not be presented as if it could. A Parameters
 * row has no enclosing call at all, so it is offered all three — which is the honest answer there, since the
 * value it holds may be handed to any of them.
 *
 * <p>Reads and writes a {@link Precision} value; the host writes it as {@code new Precision(d, a, c)}.
 */
public final class PrecisionEditors {

    private PrecisionEditors() {}

    private static final String FQN = Precision.class.getName();

    /** The quantity gates every {@code Precision} anchor carries — must match the SDK's constants. */
    private static final int DEFAULT_AREA = 4;
    private static final int DEFAULT_COUNT = 0;
    /** What an unreadable value reads as: the SDK's own {@code DEFAULT}, never zeroes. */
    private static final double DEFAULT_DELTA_E = 12.0;

    /** Past LOOSE the match is mostly noise, but leave headroom so the slider isn't a wall at the last anchor. */
    private static final double MAX_DELTA_E = 40.0;
    /** The blob preview canvas is square; a blob larger than this is drawn clipped rather than scaled down. */
    private static final int PREVIEW_SIDE = 180;

    /** Kept in step with the SDK's {@code Precision} constants — the anchors the slider is laid out against. */
    private record Anchor(String constant, double deltaE, String meaning) {}

    private static final List<Anchor> ANCHORS = List.of(
            new Anchor("EXACT", 0.0, "only this exact colour"),
            new Anchor("TIGHT", 5.0, "this shade"),
            new Anchor("DEFAULT", 12.0, "this colour, shaded or anti-aliased"),
            new Anchor("LOOSE", 25.0, "the whole colour family"));

    /** The three knobs of the SDK type, as the editor holds them. */
    record Settings(double deltaE, int minArea, int minCount) {}

    /**
     * Which halves of {@code Precision} the enclosing call can actually act on — the SDK's per-method javadoc
     * turned into something the editor can switch on.
     */
    record Knobs(boolean tolerance, boolean quantity) {

        /** A one-line note for the dialog when the call reads only part of the type. */
        String note(String methodName) {
            if (tolerance && quantity) return null;
            if (tolerance) {
                return methodName + " tests a single point, so only the colour tolerance applies — a minimum "
                        + "blob or pixel count describes a search over an area and there is none here.";
            }
            return methodName + " takes a colour band rather than a target colour, so there is nothing for a "
                    + "ΔE tolerance to measure from — only the two quantity gates apply.";
        }
    }

    /**
     * {@code matchesAt} reads one pixel and {@code coverage} never clusters, so both use the tolerance alone;
     * {@code findInRange} is given a colour band instead of a target, so it uses only the quantity gates.
     * Everything else — {@code find}, {@code findAll}, {@code waitFor}, {@code waitForGone} — uses all three,
     * and so does an unknown method and a Parameters row that has no call at all: showing every knob is the
     * safe default when we cannot tell.
     */
    static Knobs knobsFor(String methodName) {
        if ("matchesAt".equals(methodName) || "coverage".equals(methodName)) return new Knobs(true, false);
        if ("findInRange".equals(methodName)) return new Knobs(false, true);
        return new Knobs(true, true);
    }

    /** The editor: a pill saying the whole setting, opening the dialog that explains each part of it. */
    public static Node precision(ValueContext ctx) {
        Button button = Styles.on(new Button(pillText(ctx)), Styles.PILL);
        button.setOnAction(e -> open(ctx, button::setText));
        return button;
    }

    private static void open(ValueContext ctx, Consumer<String> relabel) {
        Settings current = current(ctx);
        String methodName = ctx.slot().flatMap(SlotContext::enclosingExecutable)
                .map(Executable::getName).orElse(null);
        Knobs knobs = knobsFor(methodName);

        Slider slider = toleranceSlider(current.deltaE());
        Spinner<Integer> areaSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, 1_000_000, current.minArea(), 4));
        Spinner<Integer> countSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 10_000_000, current.minCount(), 50));

        java.awt.Color[] target = {targetOf(ctx).orElse(null)};
        List<Runnable> onTarget = new ArrayList<>();
        VBox content = new VBox(12);

        String note = knobs.note(methodName);
        if (note != null) content.getChildren().add(hint(note));

        if (knobs.tolerance()) content.getChildren().add(tolerancePane(slider, () -> target[0], onTarget));
        if (knobs.tolerance() && knobs.quantity()) content.getChildren().add(new Separator());
        if (knobs.quantity()) content.getChildren().add(quantityPane(areaSpinner, countSpinner));

        // The frame half needs a colour to measure from, so a call that takes a colour band (findInRange) has
        // no overlay; and it needs a host to grab through, which a headless context does not have.
        if (knobs.tolerance() && ctx.services() != null) {
            MatchOverlay overlay = new MatchOverlay();
            Runnable read = () -> overlay.update(target[0], read(slider, areaSpinner, countSpinner, current, knobs));
            onTarget.add(read);
            slider.valueProperty().addListener((o, a, b) -> read.run());
            areaSpinner.valueProperty().addListener((o, a, b) -> read.run());
            countSpinner.valueProperty().addListener((o, a, b) -> read.run());
            content.getChildren().addAll(new Separator(), framePane(ctx.services(), overlay, slider, target,
                    () -> onTarget.forEach(Runnable::run)));
            read.run();
        }

        Modals.form(ctx, "How exact should the match be?", content, () -> {
            commitEditor(areaSpinner);
            commitEditor(countSpinner);
            Settings chosen = read(slider, areaSpinner, countSpinner, current, knobs);
            commit(ctx, chosen);
            relabel.accept(label(chosen));
        });
    }

    /**
     * The dialog's current values — but a knob this call cannot use keeps whatever the value already said,
     * because the editor never showed it. Silently rewriting a field the user was not offered would make
     * opening a {@code matchesAt} editor and pressing OK quietly reset an area someone had set deliberately.
     */
    private static Settings read(Slider slider, Spinner<Integer> area, Spinner<Integer> count,
                                 Settings current, Knobs knobs) {
        double deltaE = knobs.tolerance() ? tolerance(slider) : current.deltaE();
        int a = knobs.quantity() ? Math.max(1, valueOf(area, current.minArea())) : current.minArea();
        int c = knobs.quantity() ? Math.max(0, valueOf(count, current.minCount())) : current.minCount();
        return new Settings(deltaE, a, c);
    }

    // ------------------------------------------------------------------
    // panes
    // ------------------------------------------------------------------

    /**
     * The tolerance, and what it accepts shown as colours: the target between the darkest and lightest shades
     * the current ΔE still takes ({@link ToleranceTeacher#boundary}). Re-drawn on every slider move and every
     * change of target ({@code onTarget}).
     */
    static Slider toleranceSlider(double deltaE) {
        Slider slider = new Slider(0, MAX_DELTA_E, clamp(deltaE));
        slider.setPrefWidth(420);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(5);
        slider.setMinorTickCount(4);
        slider.getProperties().put(EXACT, slider.getValue());
        return slider;
    }

    /** The slider's key for the value it was last set to exactly — opened with, or taught. */
    private static final String EXACT = "precision.exact";

    /** What the slider means now: {@link #tolerance} against the value it was last set to exactly. */
    private static double tolerance(Slider slider) {
        Object exact = slider.getProperties().get(EXACT);
        return tolerance(slider.getValue(), exact instanceof Double d ? d : Double.NaN);
    }

    /** Moves the slider to a value a lesson taught, keeping its tenth. */
    private static void teach(Slider slider, double deltaE) {
        double placed = clamp(deltaE);
        slider.getProperties().put(EXACT, placed);
        slider.setValue(placed);
    }

    /**
     * The tolerance a slider position means: {@code exact} — the value the dialog opened with, or the last one
     * a lesson set — keeps its tenth; anything else was dragged or clicked and lands on a whole number, so a
     * drag near an anchor is the anchor ({@code TIGHT} is 5, not 5.4).
     */
    static double tolerance(double sliderValue, double exact) {
        return Math.abs(sliderValue - exact) < 1e-9 ? round(sliderValue) : Math.round(sliderValue);
    }

    private static Node tolerancePane(Slider slider, Supplier<java.awt.Color> target, List<Runnable> onTarget) {

        Label reading = Styles.on(new Label(), Styles.CAPTION_STRONG);
        Label meaning = Styles.on(new Label(), Styles.CAPTION);

        HBox edges = new HBox(12);
        edges.setAlignment(Pos.CENTER_LEFT);

        Runnable refresh = () -> {
            double v = tolerance(slider);
            reading.setText("Colour tolerance: " + label(v));
            meaning.setText(meaningOf(v));
            renderEdges(edges, target.get(), v);
        };
        slider.valueProperty().addListener((o, a, b) -> refresh.run());
        onTarget.add(refresh);
        refresh.run();

        return new VBox(8, reading, slider, meaning, edges);
    }

    private static void renderEdges(HBox edges, java.awt.Color target, double deltaE) {
        edges.getChildren().clear();
        if (target == null) return;
        java.awt.Color[] bounds = ToleranceTeacher.boundary(target, deltaE);
        edges.getChildren().addAll(swatch(bounds[0], "darkest accepted"), swatch(target, "target"),
                swatch(bounds[1], "lightest accepted"));
    }

    private static Node swatch(java.awt.Color c, String caption) {
        Rectangle r = new Rectangle(28, 28, Color.rgb(c.getRed(), c.getGreen(), c.getBlue()));
        r.setArcWidth(6);
        r.setArcHeight(6);
        r.setStroke(Color.web("#9aa0a6"));
        Label text = Styles.on(new Label(String.format("%s%n#%02X%02X%02X", caption,
                c.getRed(), c.getGreen(), c.getBlue())), Styles.CAPTION);
        VBox cell = new VBox(4, r, text);
        cell.setAlignment(Pos.CENTER_LEFT);
        return cell;
    }

    /**
     * The frame half: a frozen frame with the matches drawn on it ({@link MatchOverlay}), a way to pick another
     * surface or another target colour, and the two pin modes that teach the tolerance by example
     * ({@link ToleranceTeacher}). Opens on the bot's own source, grabbed without raising.
     */
    private static Node framePane(StudioServices services, MatchOverlay overlay, Slider slider,
                                  java.awt.Color[] target, Runnable targetChanged) {
        List<java.awt.Color> good = new ArrayList<>();
        List<java.awt.Color> bad = new ArrayList<>();
        Label lesson = Styles.on(new Label(), Styles.CAPTION);
        lesson.setWrapText(true);
        lesson.setMaxWidth(720);

        Consumer<EditorFrame.Failure> failed =
                f -> lesson.setText(f.headline() + " Frame… picks another window, a screen or the desktop.");
        Button frameButton = new Button("Frame…");
        frameButton.setOnAction(e -> SurfaceMenu.choose(services, surface -> {
            Consumer<EditorFrame> onFrame = f -> {
                good.clear();
                bad.clear();
                lesson.setText("");
                overlay.show(f);
            };
            if (surface.botsOwn()) EditorFrame.grabAsync(services, onFrame, failed);
            else EditorFrame.grabAsync(services, surface.source(), onFrame, failed);
        }));

        Button eyedropper = new Button("Target colour…");
        eyedropper.setTooltip(new javafx.scene.control.Tooltip(
                "Preview against a colour of your choosing; it is not written into the call."));
        Consumer<EditorFrame> sampleOn = f -> ColorSampler.openOn(services, f, s -> {
            target[0] = s.color();
            targetChanged.run();
        });
        eyedropper.setOnAction(e -> overlay.frame().ifPresentOrElse(sampleOn,
                () -> EditorFrame.grabAsync(services, sampleOn, failed)));

        ToggleGroup pinMode = new ToggleGroup();
        ToggleButton shouldMatch = new ToggleButton("+ Should match");
        ToggleButton shouldNot = new ToggleButton("− Should not");
        shouldMatch.setToggleGroup(pinMode);
        shouldNot.setToggleGroup(pinMode);
        pinMode.selectedToggleProperty().addListener((o, was, now) ->
                overlay.mode(now == shouldMatch ? Boolean.TRUE : now == shouldNot ? Boolean.FALSE : null));
        Button clear = new Button("Clear pins");
        clear.setOnAction(e -> {
            good.clear();
            bad.clear();
            overlay.clearPins();
            lesson.setText("");
        });

        overlay.onPin((colour, isGood) -> {
            if (target[0] == null) {
                lesson.setText(TargetColor.describe(null));
                return;
            }
            (isGood ? good : bad).add(colour);
            ToleranceTeacher.Lesson taught = ToleranceTeacher.teach(target[0], good, bad, tolerance(slider));
            teach(slider, taught.deltaE());
            lesson.setText(lessonText(taught));
        });

        EditorFrame.grabAsync(services, overlay::show, failed);
        HBox tools = new HBox(8, frameButton, eyedropper, shouldMatch, shouldNot, clear);
        tools.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8, tools, overlay.node(), lesson);
    }

    /** What the pins taught, in a sentence: the tolerance they ask for, or the red pin it cannot keep out. */
    static String lessonText(ToleranceTeacher.Lesson taught) {
        if (!taught.conflicts().isEmpty()) {
            return String.format("Can't separate: needs ΔE ≥ %s to match, but a red pin is at %s.",
                    trim(taught.deltaE()), trim(taught.conflicts().getFirst().distance()));
        }
        if (taught.deltaE() > MAX_DELTA_E) {
            return "The green pins need ΔE " + trim(taught.deltaE()) + ", past the slider's "
                    + trim(MAX_DELTA_E) + ": they are not really one colour.";
        }
        return "ΔE " + trim(taught.deltaE()) + " takes every green pin.";
    }

    private static Node quantityPane(Spinner<Integer> area, Spinner<Integer> count) {
        area.setEditable(true);
        area.setPrefWidth(140);
        count.setEditable(true);
        count.setPrefWidth(140);

        Canvas canvas = new Canvas(PREVIEW_SIDE, PREVIEW_SIDE);
        Label areaReadout = Styles.on(new Label(), Styles.CAPTION);
        Label countReadout = Styles.on(new Label(), Styles.CAPTION);

        Runnable refresh = () -> {
            int px = valueOf(area, DEFAULT_AREA);
            drawBlob(canvas, px);
            areaReadout.setText(readoutFor(px));
            int c = valueOf(count, DEFAULT_COUNT);
            countReadout.setText(c == 0
                    ? "No total required — however much of the colour there is."
                    : String.format("At least %,d matching pixels anywhere in the frame, clustered or not.", c));
        };
        area.valueProperty().addListener((o, a, b) -> refresh.run());
        count.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        VBox areaBox = new VBox(6,
                new Label("Smallest patch that counts"),
                hint("Touching pixels of the colour needed before it counts as one match. This is an area, "
                        + "not a width — raise it to ignore stray specks and anti-aliased edges."),
                area, canvas, areaReadout);

        VBox countBox = new VBox(6,
                new Label("Enough of the colour overall"),
                hint("Matching pixels the frame must contain in total, however they clump. A health bar drawn "
                        + "as twenty separate segments passes this and fails the patch test — that is the "
                        + "difference between the two, and why they are set together."),
                count, countReadout);

        return new VBox(14, areaBox, new Separator(), countBox);
    }

    private static Label hint(String text) {
        Label l = Styles.on(new Label(text), Styles.DIALOG_HINT);
        l.setWrapText(true);
        l.setMaxWidth(440);
        return l;
    }

    // The Preview class (a "Sample from game" button and a numbers-only readout) was deleted on 2026-09-26: the
    // frame pane above draws the same pass on the frame itself, and takes the target from the call.

    // ------------------------------------------------------------------
    // the colour it is a tolerance around
    // ------------------------------------------------------------------

    /** The colour this precision is a tolerance around: the call's first {@code Color} argument, when readable. */
    static Optional<java.awt.Color> targetOf(ValueContext ctx) {
        return ctx.slot().flatMap(slot -> slot.enclosingExecutable().flatMap(call -> {
            Class<?>[] parameters = call.getParameterTypes();
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i] == java.awt.Color.class) return slot.argumentValue(i, java.awt.Color.class);
            }
            return Optional.empty();
        }));
    }

    /** The line under the overlay that says what it is previewing against. */
    static final class TargetColor {
        private TargetColor() {}

        static String describe(java.awt.Color target) {
            return target == null ? "No colour to preview against: pick one with the eyedropper."
                    : String.format("Target #%02X%02X%02X", target.getRed(), target.getGreen(), target.getBlue());
        }
    }

    // ------------------------------------------------------------------
    // reading and writing the value
    // ------------------------------------------------------------------

    // commit, current, pillText, knobsFor and readoutFor are package-private rather than
    // private for the reason ColorEditors' pair is: they are the halves of this editor that can be asserted
    // without a JavaFX toolkit, and they are the halves worth asserting — what is written is what the bot
    // compiles, and what is read is what the user sees claimed about a value they may not have set. See
    // PrecisionEditorTest.

    /**
     * Writes the setting.
     *
     * <p>The <b>value</b>, so the host spells it through this plugin's own {@code ComponentType} — the three
     * components of {@code new Precision(…)}. It used to write {@code literalFor(…)}, the shortest exact
     * Java form ({@code Precision.TIGHT.minArea(400)}), and that was the editor and the codec being two
     * writers of one file: a disagreement between them is a value that changes meaning when it is written
     * back. One writer now, and it is the host's.
     */
    static void commit(ValueContext ctx, Settings s) {
        ctx.set(new Precision(s.deltaE(), s.minArea(), s.minCount()));
    }

    /**
     * The three values the value holds, or the SDK's own {@code DEFAULT} when the host could not read it.
     *
     * <p>The host reads {@code new Precision(…)}, which is what this editor writes, and since 2026-09-23 the
     * chains a person writes by hand — {@code Precision.TIGHT.minArea(400)} — through
     * {@code SdkTypes.PRECISION_WITHERS}. Anything else it cannot read is shown as written ({@link #pillText})
     * and the dialog opens on the defaults.
     */
    static Settings current(ValueContext ctx) {
        return ctx.value(Precision.class)
                .map(p -> new Settings(p.deltaE(), p.minArea(), p.minCount()))
                .orElseGet(PrecisionEditors::defaults);
    }

    /** The whole setting when the host read one, and the source as written when it did not. */
    static String pillText(ValueContext ctx) {
        if (ctx.value(Precision.class).isPresent() || Slots.raw(ctx).isBlank()) return label(current(ctx));
        return Slots.raw(ctx);
    }

    private static Settings defaults() {
        return new Settings(DEFAULT_DELTA_E, DEFAULT_AREA, DEFAULT_COUNT);
    }

    private static String anchorFor(double deltaE) {
        for (Anchor a : ANCHORS) {
            if (a.deltaE() == deltaE) return a.constant();
        }
        return null;
    }

    /** "400 px² — about 20×20, or a circle 23 across" — the area said three ways so none of them mislead. */
    static String readoutFor(int pixels) {
        double side = Math.sqrt(pixels);
        double diameter = 2 * Math.sqrt(pixels / Math.PI);
        return String.format("%,d px² — about %.0f×%.0f, or a circle %.0f across", pixels, side, side, diameter);
    }

    // ------------------------------------------------------------------
    // drawing / labels
    // ------------------------------------------------------------------

    /**
     * Draws the area at 1:1 over a pixel grid: a filled circle of exactly {@code pixels} area, with the
     * equivalent square outlined behind it. Both shapes are the same area, which is the point — the user sees
     * how little "40 pixels" actually is before typing it into a bot that then never matches.
     */
    private static void drawBlob(Canvas canvas, int pixels) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        g.clearRect(0, 0, w, w);
        g.setFill(Color.web("#fafafa"));
        g.fillRect(0, 0, w, w);

        g.setStroke(Color.web("#e4e4e4"));
        g.setLineWidth(1);
        for (int i = 10; i < w; i += 10) {
            g.strokeLine(i, 0, i, w);
            g.strokeLine(0, i, w, i);
        }

        double side = Math.sqrt(pixels);
        double diameter = 2 * Math.sqrt(pixels / Math.PI);
        double cx = w / 2;
        double cy = w / 2;

        g.setStroke(Color.web("#9aa0a6"));
        g.setLineDashes(4);
        g.strokeRect(cx - side / 2, cy - side / 2, side, side);
        g.setLineDashes(0);

        g.setFill(Color.web("#c0392b"));
        g.fillOval(cx - diameter / 2, cy - diameter / 2, diameter, diameter);

        g.setStroke(Color.web("#b0b0b0"));
        g.strokeRect(0.5, 0.5, w - 1, w - 1);
        g.setFill(Color.web("#9aa0a6"));
        g.fillText("grid squares are 10×10 px", 8, w - 8);
    }

    /** The button face: the whole setting, so a glance at the block says what it will and will not match. */
    static String label(Settings s) {
        StringBuilder sb = new StringBuilder(label(s.deltaE()));
        if (s.minArea() != DEFAULT_AREA) sb.append(" · ").append(s.minArea()).append(" px²");
        if (s.minCount() != DEFAULT_COUNT) sb.append(" · ").append(s.minCount()).append(" px total");
        return sb.toString();
    }

    private static String label(double deltaE) {
        String anchor = anchorFor(deltaE);
        return anchor != null ? anchor + " (ΔE " + trim(deltaE) + ")" : "ΔE " + trim(deltaE);
    }

    private static String meaningOf(double deltaE) {
        Anchor nearest = ANCHORS.getFirst();
        for (Anchor a : ANCHORS) {
            if (deltaE >= a.deltaE()) nearest = a;
        }
        return (nearest.deltaE() == deltaE ? "" : "about ") + nearest.meaning();
    }

    // ------------------------------------------------------------------
    // small helpers
    // ------------------------------------------------------------------

    /** Force a typed-but-not-committed spinner value into the model before we read it. */
    private static void commitEditor(Spinner<Integer> spinner) {
        String text = spinner.getEditor().getText();
        if (text != null && !text.isBlank()) {
            try {
                SpinnerValueFactory<Integer> factory = spinner.getValueFactory();
                factory.setValue(factory.getConverter().fromString(text.trim()));
            } catch (RuntimeException ignored) {
                // keep the last valid model value when the text can't be parsed
            }
        }
    }

    private static int valueOf(Spinner<Integer> spinner, int fallback) {
        Integer v = spinner.getValue();
        return v == null ? fallback : v;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(MAX_DELTA_E, v));
    }

    /**
     * To a tenth, the step a taught tolerance is set in (2026-09-26; whole numbers before). Rounding a taught
     * 9.4 down to 9 would drop the green pin the lesson was made to take.
     */
    static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String trim(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
