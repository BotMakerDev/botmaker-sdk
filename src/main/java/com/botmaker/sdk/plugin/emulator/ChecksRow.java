package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.pictures.ScreenSize;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstances;
import com.botmaker.shared.emulator.WaydroidPlatform;
import com.botmaker.shared.emulator.WaydroidResolution;
import com.botmaker.shared.emulator.checks.CheckAnswers;
import com.botmaker.shared.emulator.checks.CheckResult;
import com.botmaker.shared.emulator.checks.CheckState;
import com.botmaker.shared.emulator.checks.EmulatorCheck;
import com.botmaker.shared.emulator.checks.EmulatorChecks;
import com.botmaker.shared.emulator.checks.Fix;
import com.botmaker.shared.emulator.checks.FixRunner;
import com.botmaker.shared.emulator.checks.Fixes;
import com.botmaker.shared.emulator.checks.WaydroidChecks;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.launch.UriLauncher;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The list of {@link EmulatorChecks} on an emulator, shared by the Game dialog and the Waydroid diagnostics window:
 * a mark, the check's name and what was found per row, Fix on a failure BotMaker repairs, Undo while a translator
 * backup is left, and the commands to copy on a failure the user repairs. Nothing runs until a button is pressed.
 */
public final class ChecksRow {

    /** A session a Fix starts when the bot has no Waydroid game to start: Settings is on every Android. */
    private static final LaunchSpec ANY_WAYDROID_APP =
            LaunchSpec.parse("emu-app:com.android.settings@" + WaydroidPlatform.INSTANCE_NAME);

    /** Starts a target where ▶ Launch game would and waits; null when it started, else why not. */
    @FunctionalInterface
    public interface Starter {
        String start(LaunchSpec spec, StudioServices services, Consumer<String> words);
    }

    private final StudioServices services;
    private final Starter starter;
    private final VBox rows = new VBox(6);
    private final Label said = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
    /** What the last Fix printed: where a Google Play fix shows the ID to enter. */
    private final TextArea output = Styles.on(new TextArea(), Styles.MONO_TEXT);
    private final Button copyOutput = new Button("Copy");
    private final VBox box;
    private final HBox outputRow;
    /** The target the rows are about; {@code null} for the machine's Waydroid. */
    private LaunchSpec spec;
    private List<CheckResult> shown = List.of();
    private EmulatorInstance instance;
    private WaydroidResolution expected;
    /** The check its Fix is running, with the last thing it reported. */
    private FixRunner.Progress running;
    private EmulatorCheck runningCheck;

    /** @param starter how a Fix brings a target up; the launch package's, which this one may not name */
    public ChecksRow(StudioServices services, Starter starter) {
        this.services = services;
        this.starter = starter;
        Label title = Styles.on(new Label("Emulator checks"), Styles.STRONG_TEXT);
        Button again = new Button("Check again");
        again.setOnAction(e -> reload());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8, title, spacer, again);
        heading.setAlignment(Pos.CENTER_LEFT);
        said.setWrapText(true);
        output.setEditable(false);
        output.setPrefRowCount(3);
        HBox.setHgrow(output, Priority.ALWAYS);
        copyOutput.setOnAction(e -> copy(output.getText()));
        outputRow = new HBox(6, output, copyOutput);
        showOutput(List.of());
        box = new VBox(6, heading, rows, said, outputRow);
    }

    public Node node() {
        return box;
    }

    /** The rows for {@code target}, hidden when it is no emulator app or its emulator has no checks. FX thread. */
    public void show(String target) {
        LaunchSpec parsed = target == null ? null : LaunchSpec.parse(target);
        if (parsed == null || parsed.kind() != LaunchKind.EMULATOR_APP) {
            spec = null;
            visible(false);
            return;
        }
        spec = parsed;
        reload();
    }

    /** The rows for this machine's Waydroid, whichever game the bot has. FX thread. */
    public void showWaydroid() {
        String target = LaunchTargetValue.current(services);
        LaunchSpec current = target == null ? null : LaunchSpec.parse(target);
        spec = isWaydroid(current) ? current : ANY_WAYDROID_APP;
        reload();
    }

    private static boolean isWaydroid(LaunchSpec spec) {
        return spec != null && spec.kind() == LaunchKind.EMULATOR_APP
                && WaydroidPlatform.INSTANCE_NAME.equalsIgnoreCase(spec.emulatorInstance());
    }

    /** One read of what {@link #apply} shows. */
    public record Read(LaunchSpec spec, EmulatorInstance instance, WaydroidResolution expected,
                       List<CheckResult> results) {}

    /** The checks on {@code asked}'s emulator; shells out, never on the FX thread. */
    public static Read read(LaunchSpec asked, WaydroidResolution size) {
        EmulatorInstance found = EmulatorInstances.find(asked.emulatorInstance()).orElse(null);
        // A stopped Waydroid is often not discovered at all: that is a failure to show, not an emulator to skip.
        List<CheckResult> results = found == null && isWaydroid(asked) ? WaydroidChecks.run(null, size)
                : EmulatorChecks.run(found, size);
        return new Read(asked, found, size, results);
    }

    /** What a Fix on {@code read}'s rows needs. */
    public static Fixes.Context context(Read read, StudioServices services, Starter starter) {
        return new Fixes.Context(read.instance(), read.expected(),
                words -> starter.start(read.spec(), services, words));
    }

    /** Reads the checks off the FX thread; a Fix under way keeps its row until it ends. */
    private void reload() {
        if (spec == null || runningCheck != null) return;
        LaunchSpec asked = spec;
        WaydroidResolution size = ScreenSize.read(services)
                .map(screen -> new WaydroidResolution(screen.width(), screen.height())).orElse(null);
        Async.load("emulator-checks", () -> read(asked, size), this::apply,
                why -> said.setText("Couldn't read the checks: " + why));
    }

    private void apply(Read read) {
        if (read.spec() != spec || runningCheck != null) return;
        instance = read.instance();
        expected = read.expected();
        shown = read.results();
        visible(!shown.isEmpty());
        render();
    }

    private void visible(boolean on) {
        box.setVisible(on);
        box.setManaged(on);
    }

    private void render() {
        rows.getChildren().clear();
        Fixes.Context context = context();
        for (CheckResult result : shown) rows.getChildren().add(row(result, context));
    }

    private Fixes.Context context() {
        return context(new Read(spec, instance, expected, shown), services, starter);
    }

    private Node row(CheckResult result, Fixes.Context context) {
        boolean itsFix = result.check() == runningCheck;
        Optional<Fix> fix = Fixes.of(result, context);
        Label name = new Label(mark(result.state(), itsFix) + " " + result.check().displayName());
        boolean quiet = !result.failed() && !itsFix;
        if (quiet) {
            // On one line: a healthy list must leave the dialog room for the games under it.
            name.setText(name.getText() + reasonAfter(result));
            Styles.on(name, Styles.SMALL_TEXT, Styles.MUTED_TEXT);
        } else {
            Styles.on(name, Styles.SMALL_TEXT, Styles.STRONG_TEXT);
        }
        name.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(name, Priority.SOMETIMES);
        HBox line = new HBox(8, name, spacer);
        line.setAlignment(Pos.CENTER_LEFT);
        if (fix.isPresent() && !itsFix) line.getChildren().add(button("Fix", () -> run(fix.get())));
        if (result.undoable() && !itsFix) line.getChildren().add(button("Undo", this::undo));
        if (itsFix) line.getChildren().add(button("Cancel", this::cancel));
        VBox row = new VBox(2, line);
        if (itsFix) {
            ProgressBar bar = new ProgressBar(running == null ? -1 : (double) running.step() / running.of());
            bar.setMaxWidth(Double.MAX_VALUE);
            row.getChildren().addAll(bar, Styles.on(new Label(stepWords(running)), Styles.SMALL_TEXT));
            return row;
        }
        if (quiet) return row;
        Label reason = Styles.on(new Label(result.reason()), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
        reason.setWrapText(true);
        Label remedy = Styles.on(new Label(result.remedy()), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
        remedy.setWrapText(true);
        row.getChildren().add(reason);
        if (!result.remedy().isEmpty()) row.getChildren().add(remedy);
        if (!result.fixable() && !result.commands().isEmpty()) {
            TextArea commands = Styles.on(new TextArea(result.commandBlock()), Styles.MONO_TEXT);
            commands.setEditable(false);
            commands.setPrefRowCount(Math.min(result.commands().size(), 6));
            HBox.setHgrow(commands, Priority.ALWAYS);
            row.getChildren().add(new HBox(6, commands, button("Copy", () -> copy(result.commandBlock()))));
        }
        if (result.link() != null) {
            Hyperlink page = new Hyperlink(result.link());
            page.setOnAction(e -> UriLauncher.open(result.link()));
            row.getChildren().add(page);
        }
        return row;
    }

    private static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private void undo() {
        Async.load("emulator-undo", Fixes::undo, fix -> fix.ifPresentOrElse(this::run,
                () -> said.setText("No backup is left to restore.")), why -> said.setText(why));
    }

    private void cancel() {
        if (!runner().cancel()) said.setText("It can't be stopped now: a step that changes the system has started.");
    }

    private FixRunner runner() {
        return CheckAnswers.runnerOf(instance);
    }

    private void run(Fix fix) {
        showOutput(List.of());
        said.setText(fix.needsRoot() ? "Your system will ask for your password." : "");
        boolean started = runner().start(fix, progress -> Platform.runLater(() -> progress(fix, progress)));
        if (!started) {
            said.setText("A fix is already running on this emulator.");
            return;
        }
        runningCheck = fix.check();
        running = null;
        render();
    }

    private void progress(Fix fix, FixRunner.Progress progress) {
        if (!progress.done()) {
            running = progress;
            render();
            return;
        }
        running = null;
        runningCheck = null;
        said.setText(CheckAnswers.ended(fix, progress));
        showOutput(progress.output());
        reload();
    }

    private void showOutput(List<String> lines) {
        output.setText(String.join("\n", lines));
        outputRow.setVisible(!lines.isEmpty());
        outputRow.setManaged(!lines.isEmpty());
    }

    private static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static String mark(CheckState state, boolean running) {
        if (running) return "⟳";
        return switch (state) {
            case OK -> "✓";
            case FAILED -> "!";
            case AFTER_ABOVE -> "·";
            case INFO, UNKNOWN -> "○";
        };
    }

    private static String reasonAfter(CheckResult result) {
        if (result.state() == CheckState.AFTER_ABOVE) return " (after the one above)";
        return result.reason().isEmpty() ? "" : " — " + result.reason();
    }

    private static String stepWords(FixRunner.Progress progress) {
        return progress == null ? "Starting…" : "Step " + progress.step() + "/" + progress.of() + ": " + progress.words();
    }

    /** One result as its row reads, with the buttons it would offer. */
    static String line(CheckResult result, boolean canFix, FixRunner.Progress running) {
        if (running != null) {
            return mark(result.state(), true) + " " + result.check().displayName() + " — " + stepWords(running);
        }
        String text = mark(result.state(), false) + " " + result.check().displayName();
        text += result.failed() ? " — " + result.reason() : reasonAfter(result);
        if (result.failed() && canFix) text += " [Fix]";
        return result.undoable() ? text + " [Undo]" : text;
    }

    /** Every row of a list no Fix is running on, as text. */
    static List<String> lines(List<CheckResult> results) {
        return results.stream().map(result -> line(result, result.fixable(), null)).toList();
    }
}
