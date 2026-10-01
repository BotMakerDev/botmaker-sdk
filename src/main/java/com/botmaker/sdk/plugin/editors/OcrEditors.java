package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.text.OcrLanguage;
import com.botmaker.sdk.api.text.OcrOptions;
import com.botmaker.sdk.api.text.OcrOptions.BinarizeMode;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.internal.ocr.OcrEngine;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Editor for an {@link OcrOptions}: a pill saying what the text reader is set to, opening a dialog with the
 * knobs a bot author actually turns — which languages, lines or words, which characters, how much to enlarge
 * and how to clean the picture up — and a <b>Try it</b> that reads the bot's source with them.
 *
 * <p>The three Tesseract internals the record also carries (page segmentation, engine mode, grayscale) are
 * not shown and are kept as the value had them, for the reason {@code PrecisionEditors} keeps a knob its call
 * cannot use: pressing OK must not reset a field the user was never offered.
 *
 * <p>The host writes the value as the record's constructor, and cannot write a {@code null} part, so a
 * whitelist of "any character" is handed back as {@code ""} — which {@code Text} reads as {@code null}.
 */
public final class OcrEditors {

    private OcrEditors() {}

    /** A whitelist a counter or a timer wants, offered as one press. */
    static final String DIGITS = "0123456789";

    /** Upscale bounds: below 1 shrinks the text Tesseract already struggles with; past 4 only costs time. */
    private static final double MIN_UPSCALE = 1.0, MAX_UPSCALE = 4.0, UPSCALE_STEP = 0.5;

    /** How much of a Try it read the dialog shows before cutting it off. */
    private static final int TRY_LIMIT = 2_000;

    /** What each clean-up does, in the words the dialog uses. */
    private static final Map<BinarizeMode, String> CLEAN_UP = new EnumMap<>(Map.of(
            BinarizeMode.NONE, "None",
            BinarizeMode.OTSU, "Even lighting",
            BinarizeMode.ADAPTIVE, "Uneven lighting"));

    /** The editor: a pill saying the whole setting, opening the dialog. */
    public static Node options(ValueContext ctx) {
        Button button = Styles.on(new Button(pillText(ctx)), Styles.PILL);
        button.setOnAction(e -> open(ctx, button::setText));
        return button;
    }

    private static void open(ValueContext ctx, Consumer<String> relabel) {
        OcrOptions current = current(ctx);

        Set<OcrLanguage> chosen = bundled(current.languages());
        List<String> others = others(current.languages());
        Map<OcrLanguage, CheckBox> languageBoxes = new EnumMap<>(OcrLanguage.class);
        FlowPane languages = new FlowPane(12, 6);
        for (OcrLanguage language : OcrLanguage.values()) {
            CheckBox box = new CheckBox(language.displayName());
            box.setSelected(chosen.contains(language));
            languageBoxes.put(language, box);
            languages.getChildren().add(box);
        }

        ToggleGroup levelGroup = new ToggleGroup();
        RadioButton lines = new RadioButton("Whole lines");
        RadioButton words = new RadioButton("Single words");
        lines.setToggleGroup(levelGroup);
        words.setToggleGroup(levelGroup);
        (current.level() == TextResult.Level.WORD ? words : lines).setSelected(true);

        TextField whitelist = new TextField(current.charWhitelist() == null ? "" : current.charWhitelist());
        whitelist.setPromptText("any character");
        whitelist.setPrefColumnCount(24);
        Button digits = new Button("Digits only");
        digits.setOnAction(e -> whitelist.setText(DIGITS));
        Button any = new Button("Any");
        any.setOnAction(e -> whitelist.clear());

        Spinner<Double> upscale = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(
                MIN_UPSCALE, Math.max(MAX_UPSCALE, current.upscale()), Math.max(MIN_UPSCALE, current.upscale()),
                UPSCALE_STEP));
        upscale.setEditable(true);
        upscale.setPrefWidth(90);

        ToggleGroup cleanGroup = new ToggleGroup();
        Map<BinarizeMode, RadioButton> cleanButtons = new EnumMap<>(BinarizeMode.class);
        HBox clean = new HBox(12);
        for (BinarizeMode mode : BinarizeMode.values()) {
            RadioButton button = new RadioButton(CLEAN_UP.get(mode));
            button.setToggleGroup(cleanGroup);
            button.setSelected(mode == current.binarize());
            cleanButtons.put(mode, button);
            clean.getChildren().add(button);
        }
        CheckBox invert = new CheckBox("Light text on a dark background");
        invert.setSelected(current.invert());

        Supplier<OcrOptions> read = () -> {
            commitEditor(upscale);
            Set<OcrLanguage> picked = EnumSet.noneOf(OcrLanguage.class);
            languageBoxes.forEach((language, box) -> {
                if (box.isSelected()) picked.add(language);
            });
            BinarizeMode mode = cleanButtons.entrySet().stream().filter(e -> e.getValue().isSelected())
                    .map(Map.Entry::getKey).findFirst().orElse(current.binarize());
            return current
                    .withLanguages(spec(picked, others))
                    .withLevel(words.isSelected() ? TextResult.Level.WORD : TextResult.Level.LINE)
                    .withCharWhitelist(whitelist.getText())
                    .withUpscale(upscale.getValue() == null ? current.upscale() : upscale.getValue())
                    .withBinarize(mode)
                    .withInvert(invert.isSelected());
        };

        VBox content = new VBox(10);
        content.getChildren().addAll(
                heading("Languages"), languages);
        if (!others.isEmpty()) {
            content.getChildren().add(caption("Also kept, not bundled with the SDK: " + String.join(", ", others)
                    + ". Tesseract reads them only where they are installed."));
        }
        content.getChildren().addAll(
                heading("Read as"), new HBox(12, lines, words),
                heading("Only these characters"), new HBox(8, whitelist, digits, any),
                caption("Empty reads every character. A counter or a timer reads better as digits only."),
                new Separator(),
                heading("Enlarge first"), new HBox(8, upscale, caption("times. 2 to 3 helps small game fonts.")),
                heading("Clean up the picture"), clean, invert);
        if (ctx.services() != null) content.getChildren().addAll(new Separator(), tryPane(ctx.services(), read));

        Modals.form(ctx, "How should the text be read?", content, () -> {
            OcrOptions picked = read.get();
            commit(ctx, picked);
            relabel.accept(label(picked));
        });
    }

    /**
     * <b>Try it</b>: the bot's own source grabbed, and read with the dialog's current settings, off the JavaFX
     * thread. Tesseract's natives may be missing on this machine, so a link failure is said, not thrown.
     */
    private static Node tryPane(StudioServices services, Supplier<OcrOptions> read) {
        TextArea result = new TextArea();
        result.setEditable(false);
        result.setWrapText(true);
        result.setPrefRowCount(5);
        result.setPromptText("What the bot would read from its source.");
        Button tryIt = new Button("Try it");
        tryIt.setOnAction(e -> {
            OcrOptions opts = read.get();
            tryIt.setDisable(true);
            result.setText("Reading…");
            EditorFrame.grabAsync(services, frame -> {
                Thread worker = new Thread(() -> {
                    String text = readText(frame, opts);
                    Platform.runLater(() -> {
                        result.setText(text);
                        tryIt.setDisable(false);
                    });
                }, "sdk-ocr-try");
                worker.setDaemon(true);
                worker.start();
            }, failure -> {
                result.setText(failure.headline() + " " + failure.detail());
                tryIt.setDisable(false);
            });
        });
        return new VBox(6, new HBox(8, tryIt, caption("Reads the bot's capture source once, with these settings.")),
                result);
    }

    private static String readText(EditorFrame frame, OcrOptions opts) {
        try {
            String text = OcrEngine.text(frame.image(), opts).strip();
            if (text.isEmpty()) return "Nothing was read from " + frame.label() + ".";
            return text.length() > TRY_LIMIT ? text.substring(0, TRY_LIMIT) + "…" : text;
        } catch (RuntimeException | LinkageError e) {
            return "Text recognition could not start on this computer: " + e.getMessage();
        }
    }

    private static Label heading(String text) {
        return Styles.on(new Label(text), Styles.CAPTION_STRONG);
    }

    private static Label caption(String text) {
        Label label = Styles.on(new Label(text), Styles.CAPTION);
        label.setWrapText(true);
        label.setMaxWidth(520);
        return label;
    }

    private static void commitEditor(Spinner<Double> spinner) {
        String text = spinner.getEditor().getText();
        if (text == null || text.isBlank()) return;
        try {
            double typed = Double.parseDouble(text.strip());
            spinner.getValueFactory().setValue(Math.max(MIN_UPSCALE, typed));
        } catch (NumberFormatException ignored) {
            spinner.getEditor().setText(String.valueOf(spinner.getValue()));
        }
    }

    // commit, current, pillText, label and the language helpers are package-private: they are the halves of
    // this editor a test can assert without a JavaFX toolkit. See OcrEditorsTest.

    /** Writes the setting, with "any character" as {@code ""}: the host has no way to write a {@code null}. */
    static void commit(ValueContext ctx, OcrOptions options) {
        ctx.set(options.charWhitelist() == null ? options.withCharWhitelist("") : options);
    }

    /** The value the host read, or what {@code Text} reads with when it could not read one. */
    static OcrOptions current(ValueContext ctx) {
        return ctx.value(OcrOptions.class).orElse(Text.DEFAULT_OPTIONS);
    }

    /** The whole setting when the host read one, and the source as written when it did not. */
    static String pillText(ValueContext ctx) {
        if (ctx.value(OcrOptions.class).isPresent() || Slots.raw(ctx).isBlank()) return label(current(ctx));
        return Slots.raw(ctx);
    }

    /** {@code English, lines, digits only, ×2}: what a person would say the setting is. */
    static String label(OcrOptions options) {
        List<String> parts = new ArrayList<>();
        String languages = Stream.concat(
                        bundled(options.languages()).stream().map(OcrLanguage::displayName),
                        others(options.languages()).stream())
                .collect(Collectors.joining(" + "));
        parts.add(languages.isEmpty() ? "No language" : languages);
        parts.add(options.level() == TextResult.Level.WORD ? "words" : "lines");
        String whitelist = options.charWhitelist();
        if (DIGITS.equals(whitelist)) parts.add("digits only");
        else if (whitelist != null && !whitelist.isEmpty()) parts.add("only \"" + whitelist + "\"");
        parts.add("×" + trim(options.upscale()));
        return String.join(", ", parts);
    }

    /** The bundled languages a {@code eng+jpn} spec names. */
    static Set<OcrLanguage> bundled(String spec) {
        Set<OcrLanguage> out = EnumSet.noneOf(OcrLanguage.class);
        for (String code : codes(spec)) {
            OcrLanguage language = OcrLanguage.fromCode(code);
            if (language != null) out.add(language);
        }
        return out;
    }

    /** The codes a spec names that the SDK does not bundle, kept as written: a bot may have installed them. */
    static List<String> others(String spec) {
        return codes(spec).stream().filter(code -> OcrLanguage.fromCode(code) == null).toList();
    }

    /**
     * The spec for the ticked languages, followed by the ones kept from the value. Nothing ticked and nothing
     * kept is English: Tesseract refuses an empty language.
     */
    static String spec(Collection<OcrLanguage> picked, List<String> others) {
        List<String> codes = new ArrayList<>();
        for (OcrLanguage language : OcrLanguage.values()) if (picked.contains(language)) codes.add(language.code());
        codes.addAll(others);
        return codes.isEmpty() ? OcrLanguage.ENGLISH.code() : String.join("+", codes);
    }

    private static List<String> codes(String spec) {
        if (spec == null || spec.isBlank()) return List.of();
        return Arrays.stream(spec.split("\\+")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
