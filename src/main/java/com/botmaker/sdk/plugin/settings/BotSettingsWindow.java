package com.botmaker.sdk.plugin.settings;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.ManagedHandle;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.api.bot.BotSettings;
import com.botmaker.sdk.internal.bot.SdkValues;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.Optional;

/**
 * ⚙ Bot Settings: the bot's {@code @Managed("settings")} value — how it clicks and looks, whether it drives the
 * real mouse and keyboard, and whether it runs on a private display (2026-09-27).
 *
 * <p>It was Studio's <i>Input &amp; Clicks</i> window until then, writing eight keys into a
 * {@code botmaker-project.properties} the bot read back at run time. The settings are Java the bot compiles
 * now, so the window belongs to the plugin that owns the type, and it edits the one expression
 * {@code Sdk.settings()} returns through the host — the host writes the call, this window never sees Java.
 *
 * <p><b>A project whose {@code Sdk.java} has no {@code settings()}</b> — every project made before the method
 * existed — gets the method's text to paste, with Save off: the host writes a whole holder file when there is
 * none, and never adds a method to one the user owns. Until the method is there the bot runs on
 * {@link BotSettings#DEFAULTS}, which is what the window then shows.
 */
public final class BotSettingsWindow {

    /** The value, declared once in {@link SdkValues}. */
    public static final ManagedHandle<BotSettings> SETTINGS = ManagedHandle.of(SdkValues.SETTINGS);

    private final StudioServices services;
    private final Window owner;

    private final CheckBox realInput = new CheckBox("Drive the real mouse and keyboard (turn on for games)");
    private final CheckBox randomizeClicks = new CheckBox("Click a random point inside the match, not its centre");
    private final CheckBox debug = new CheckBox("Print the bot's debug trace");
    private final Spinner<Integer> foundDelay = new Spinner<>(0, 600_000, 500, 50);
    private final Spinner<Integer> notFoundDelay = new Spinner<>(0, 600_000, 200, 50);
    private final Spinner<Double> confidence = new Spinner<>(0.0, 1.0, 0.8, 0.05);
    private final Spinner<Double> compareMargin = new Spinner<>(0.0, 1.0, 0.05, 0.01);
    private final Spinner<Integer> maxRetryAttempts = new Spinner<>(1, 600_000, 20, 1);
    private final ComboBox<BotSettings.InputBackend> linuxInput = new ComboBox<>();
    private final CheckBox isolatedSession = new CheckBox("Run in a private display (background)");
    private final ComboBox<BotSettings.DisplayBackend> sessionBackend = new ComboBox<>();

    private BotSettingsWindow(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /** The ⚙ Bot Settings press. */
    public static void open(ActionContext context) {
        StudioServices services = context.services();
        open(services, Modals.owner(services));
    }

    /** Opens the window over {@code owner}. Call it on the JavaFX application thread. */
    public static void open(StudioServices services, Window owner) {
        new BotSettingsWindow(services, owner).show();
    }

    /**
     * The bot's settings as its Java says them, or {@link BotSettings#DEFAULTS} when it declares none this can
     * read — what the bot would run with. Never writes anything.
     */
    public static BotSettings current(StudioServices services) {
        return SETTINGS.read(services).orElse(BotSettings.DEFAULTS);
    }

    private void show() {
        // Asks the host to write Sdk.java first when the project has none; empty when there is still no method.
        Optional<ValueContext> ctx = SETTINGS.openOrCreate(services);
        BotSettings current = ctx.flatMap(SETTINGS::read).orElse(BotSettings.DEFAULTS);
        seed(current);

        Label heading = new Label("How this bot clicks and looks");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label intro = note("Written into Sdk.settings(), in your bot's own Java, and applied before its first "
                + "click — wherever the bot runs.");

        Stage stage = new Stage();
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Save");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            ctx.ifPresent(c -> c.set(collect()));
            services.status("Bot settings saved in Sdk.settings().");
            stage.close();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, spacer, cancel, save);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(14, heading, intro);
        if (ctx.isEmpty()) {
            save.setDisable(true);
            body.getChildren().add(missing());
        } else if (!SETTINGS.readable(ctx.get())) {
            save.setDisable(true);
            body.getChildren().add(note("Sdk.settings() is not a single `return BotSettings.of(…);`, so this "
                    + "window cannot rewrite it — it is your code, and stays as you wrote it. The defaults are "
                    + "shown."));
        }
        body.getChildren().addAll(new Separator(), inputPane(), new Separator(), sessionPane(), new Separator(),
                visionPane());
        body.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        VBox root = new VBox(scroll, bar);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        bar.setPadding(new Insets(10, 18, 14, 18));

        stage.setTitle("Bot Settings");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setScene(services.theme().scene(root, 620, 720));
        stage.setMinWidth(520);
        stage.setMinHeight(420);
        stage.show();
    }

    /** What to paste into an {@code Sdk.java} that predates the method, with a button that copies it. */
    private VBox missing() {
        String snippet = """
                @Managed("settings")
                public static BotSettings settings() {
                    return BotSettings.DEFAULTS;
                }""";
        Label says = note("This project's Sdk.java has no settings() method, so the bot runs on the defaults "
                + "below and there is nothing here to save into. Paste this into the class Sdk (importing "
                + "com.botmaker.sdk.api.bot.BotSettings), then open this window again:");
        TextArea code = new TextArea(snippet);
        code.setEditable(false);
        code.setPrefRowCount(4);
        code.setStyle("-fx-font-family: monospace;");
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(snippet);
            Clipboard.getSystemClipboard().setContent(content);
        });
        return new VBox(6, says, code, copy);
    }

    /**
     * The real-input section. The explanation is the only place that says <em>why</em> a game needs this — the
     * events BotMaker sends by default are rejected by design, and no OS reports the drop, which is why it
     * cannot be detected.
     */
    private VBox inputPane() {
        linuxInput.getItems().setAll(BotSettings.InputBackend.values());
        linuxInput.setConverter(labels(BotSettings.InputBackend::label));
        linuxInput.setMaxWidth(Double.MAX_VALUE);
        GridPane grid = grid();
        grid.addRow(0, new Label("Linux backend"), linuxInput);
        GridPane.setHgrow(linuxInput, Priority.ALWAYS);
        return new VBox(8, title("Input"), realInput,
                note("Games ignore the quiet background clicks BotMaker sends by default, so this drives the real "
                        + "mouse and keyboard instead — the pointer moves to each click and returns, and the "
                        + "game window is raised. Leave it off for an ordinary application you'd rather the bot "
                        + "never took the cursor away from."),
                grid,
                note("Which Linux backend delivers that input. Pin one only if this machine works with a "
                        + "particular one; a -Dbotmaker.linux.input on the command line still wins. Ignored on "
                        + "Windows."));
    }

    /** The private-display section, said in terms of what it decides: whether you can use the machine meanwhile. */
    private VBox sessionPane() {
        sessionBackend.getItems().setAll(BotSettings.DisplayBackend.values());
        sessionBackend.setConverter(labels(BotSettings.DisplayBackend::label));
        sessionBackend.setMaxWidth(Double.MAX_VALUE);
        sessionBackend.disableProperty().bind(isolatedSession.selectedProperty().not());
        GridPane grid = grid();
        grid.addRow(0, new Label("Display backend"), sessionBackend);
        GridPane.setHgrow(sessionBackend, Priority.ALWAYS);
        return new VBox(8, title("Session"), isolatedSession,
                note("On (the default), the bot brings up a private display of its own and launches the game "
                        + "there. The window never appears on your desktop, the bot never steals your cursor or "
                        + "focus, and your own clicks can't land in its window. Turn it off to watch the bot work "
                        + "on your real desktop. Linux only; on Windows the bot runs on the desktop either way."),
                grid,
                note("Automatic is right almost always: a game gets gamescope, which puts a real GPU inside the "
                        + "private display, and a plain command gets the lighter Xephyr. Xephyr renders in "
                        + "software, which is what makes 3D games and store launchers crash."));
    }

    /** Delays, confidence and retries — what the bot does around each match attempt. */
    private VBox visionPane() {
        for (Spinner<?> s : new Spinner<?>[] {foundDelay, notFoundDelay, confidence, compareMargin,
                maxRetryAttempts}) {
            s.setEditable(true);
            s.setPrefWidth(120);
        }
        GridPane grid = grid();
        grid.addRow(0, new Label("Pause after a match (ms)"), foundDelay,
                note("Let the game's animation finish before the next look."));
        grid.addRow(1, new Label("Pause after a miss (ms)"), notFoundDelay,
                note("How fast the bot retries when it doesn't see what it wants."));
        grid.addRow(2, new Label("Match confidence"), confidence,
                note("0–1. Lower finds more, and finds wrong things more."));
        grid.addRow(3, new Label("Compare margin"), compareMargin,
                note("How far the right template must beat a look-alike to win."));
        grid.addRow(4, new Label("Stuck after N no-progress checks"), maxRetryAttempts,
                note("When the watchdog decides the bot is stuck and restarts it."));
        return new VBox(8, title("Clicks & matching"), grid, randomizeClicks, debug);
    }

    private void seed(BotSettings s) {
        realInput.setSelected(s.input().real());
        linuxInput.setValue(s.input().linuxBackend());
        isolatedSession.setSelected(s.session().isolated());
        sessionBackend.setValue(s.session().backend());
        foundDelay.getValueFactory().setValue(s.foundDelay());
        notFoundDelay.getValueFactory().setValue(s.notFoundDelay());
        confidence.getValueFactory().setValue(s.confidence());
        compareMargin.getValueFactory().setValue(s.compareMargin());
        maxRetryAttempts.getValueFactory().setValue(s.maxRetryAttempts());
        randomizeClicks.setSelected(s.randomizeClicks());
        debug.setSelected(s.debug());
    }

    /** What the controls say, as the value {@code Sdk.settings()} will return. */
    BotSettings collect() {
        return BotSettings.of(
                BotSettings.clicks(foundDelay.getValue(), notFoundDelay.getValue(), randomizeClicks.isSelected()),
                BotSettings.vision(confidence.getValue(), compareMargin.getValue()),
                BotSettings.input(realInput.isSelected(), linuxInput.getValue()),
                BotSettings.session(isolatedSession.isSelected(), sessionBackend.getValue()),
                maxRetryAttempts.getValue(), debug.isSelected());
    }

    private static <T> StringConverter<T> labels(java.util.function.Function<T, String> label) {
        return new StringConverter<>() {
            @Override public String toString(T v) { return v == null ? "" : label.apply(v); }
            @Override public T fromString(String s) { return null; }
        };
    }

    private static Label title(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private static Label note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 11px; -fx-opacity: 0.75;");
        return l;
    }

    private static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }
}
