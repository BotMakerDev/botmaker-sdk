package com.botmaker.sdk.plugin.setup;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.plugin.launch.LaunchTargetChooser;
import com.botmaker.sdk.plugin.launch.QuickLaunch;
import com.botmaker.sdk.plugin.pictures.CaptureTemplates;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.screen.CaptureLabels;
import com.botmaker.sdk.plugin.screen.EditorFrame;
import com.botmaker.sdk.plugin.source.SourcePicker;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;

/**
 * The <b>Project Setup</b> checklist — one window that says, for a project as it stands right now, whether it
 * has something to capture, any pictures and something to launch, and what to do about each answer that is no.
 *
 * <h2>Why this is the plugin's</h2>
 *
 * <p>Every row reads a fact that belongs to this module. The capture source is {@code Sdk.captureSource()} in
 * the bot's own Java; the pictures are the images folder, read through {@link TemplateLibrary}; the launch
 * target is this machine's {@code botmaker.launch.target} run property ({@link LaunchTargetValue}). The host
 * holds none of it. What the host supplies is the three things nobody else can: which project is open, the
 * current look, and the window this one is owned by.
 *
 * <h2>Each row opens the window that changes it</h2>
 *
 * <p>The capture row opens 🎯 Capture Source and the pictures row ✂ Capture Templates — both this plugin's
 * own, so calling them names no other plugin and no host dialog. The window is <b>not modal</b>: an
 * application-modal checklist blocked the very toolbar its rows used to send the user to, and the ownerless
 * capture tool with it.
 *
 * <h2>The launch target is optional, and picked from the launchers (2026-09-29)</h2>
 *
 * <p>A game is started by its own launcher — Faugus or Heroic on Linux, Steam or Epic on Windows — or by the
 * bot's own {@code Game} blocks, and BotMaker does not grow a second launcher UI beside them (the maintainer's
 * call, 2026-09-28). So <b>Choose…</b> ({@link LaunchTargetChooser}) lists what those launchers already have
 * and stores which one; nothing about how to start it. An emulator app picked in an Emulators block sets it
 * too, and <b>▶ Launch now</b> then starts it. A value it cannot read is shown with a Clear button, never a ✓.
 *
 * <h2>Refreshing</h2>
 *
 * <p>There is no settings-changed event on the contract, and there should not be — so the triggers are the
 * window regaining focus, a row's window closing, and the <b>Re-check</b> button.
 */
public final class ProjectSetup {

    /** What the launch row says when this computer has no launch target, which is a fine place to be. */
    static final String NO_LAUNCH_TARGET = "None on this computer, and none is needed: start your game from its "
            + "own launcher, or let the bot start it with a Game block. Choose… picks a game Steam, Epic, Heroic "
            + "or Faugus already has, so a run can start it — in its own display in background mode.";

    /** The one open instance, so pressing the toolbar button twice focuses rather than stacks. */
    private static ProjectSetup active;

    private final StudioServices services;
    private final Window owner;

    private Stage stage;
    private VBox rows;
    private Label summary;

    /**
     * Where the quick-launch button reports. Deliberately <em>not</em> {@link #summary}: a launch flips the
     * window's focus to the game, and focus coming back re-runs {@link #refresh()}, which rewrites the summary
     * — so a failure message parked there would be wiped by the very act of looking at the window again.
     */
    private Label launchStatus;

    private ProjectSetup(StudioServices services, Window owner) {
        this.services = services;
        this.owner = owner;
    }

    /** The 📋 Project Setup press. */
    public static void open(ActionContext context) {
        StudioServices services = context.services();
        open(services, Modals.owner(services));
    }

    /** Opens the checklist, or focuses the one already open. */
    public static void open(StudioServices services, Window owner) {
        if (active != null && active.stage != null && active.stage.isShowing()) {
            active.stage.toFront();
            active.stage.requestFocus();
            return;
        }
        active = new ProjectSetup(services, owner);
        active.show();
    }

    /**
     * What the launch row shows for this machine's raw spec: whether it is set, whether it is a value nothing
     * can read (no {@code kind:}, or a kind nobody knows), and the line under the title.
     */
    record LaunchRow(boolean set, boolean unreadable, String detail) {}

    static LaunchRow launchRow(String spec) {
        if (spec == null || spec.isBlank()) return new LaunchRow(false, false, NO_LAUNCH_TARGET);
        LaunchSpec parsed = LaunchSpec.parse(spec);
        if (parsed == null || parsed.kind() == LaunchKind.UNKNOWN) {
            return new LaunchRow(false, true, "\"" + spec.trim() + "\" is not a launch target this computer "
                    + "can read. Clear it, then pick the emulator app again if that is what it was.");
        }
        return new LaunchRow(true, false, parsed.describe());
    }

    private void show() {
        Label heading = new Label("Set your project up to run");
        Styles.on(heading, Styles.DIALOG_HEADING);
        Label intro = new Label("Work down the list — each row opens the window that sets it, and ticks green "
                + "once it's done.");
        intro.setWrapText(true);
        Styles.on(intro, Styles.DIALOG_HINT);

        summary = Styles.on(new Label(), Styles.DIALOG_SUBHEADING);

        launchStatus = Styles.on(new Label(), Styles.SMALL_TEXT, Styles.MUTED_TEXT);
        launchStatus.setWrapText(true);

        rows = new VBox(10);

        Button recheck = new Button("Re-check");
        recheck.setOnAction(e -> refresh());
        Button done = new Button("Done");
        done.setDefaultButton(true);
        done.setCancelButton(true);
        done.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, summary, spacer, recheck, done);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(14, heading, intro, new Separator(), rows, new Separator(), launchStatus, bar);
        root.setPadding(new Insets(18));

        stage = Modals.window(services, owner, Modals.Frame.modeless("Project Setup", 540, 480, 440, 380), root);
        stage.setOnHidden(e -> active = null);
        stage.focusedProperty().addListener((obs, was, focused) -> {
            if (focused && stage.isShowing()) refresh();
        });

        refresh();
        stage.show();
    }

    /** Re-reads every step's state from the project and rebuilds the rows. */
    private void refresh() {
        Path resources = services.resourcesDir();
        CaptureSource source = EditorFrame.defaultSource(services);

        LaunchRow launch = launchRow(LaunchTargetValue.current(services));
        boolean captureDone = captureConfigured(source);
        int templateCount = TemplateLibrary.list(resources).size();

        summary.setText(!captureDone ? "Choose a capture source to run."
                : launch.unreadable() ? "Ready to run — clear the unreadable launch target."
                : "You're ready to run.");

        Button chooseSource = new Button("Choose…");
        chooseSource.setOnAction(e -> {
            SourcePicker.choose(services);
            refresh();
        });
        Button capture = new Button("Capture…");
        capture.setOnAction(e -> CaptureTemplates.open(services, stage, null, this::refresh));

        rows.getChildren().setAll(
                row(captureDone, false, "Capture source", describeCapture(source), chooseSource),
                row(templateCount > 0, true, "Pictures (optional)",
                        templateCount == 0
                                ? "None yet — only needed for image-matching bots (skip for pixel/OCR/coords)."
                                : templateCount + (templateCount == 1 ? " picture saved." : " pictures saved."),
                        capture),
                row(launch.set(), !launch.unreadable(), "Launch target (optional)", launch.detail(),
                        new HBox(8, chooseLaunchButton(),
                                launch.unreadable() ? clearLaunchButton() : quickLaunchButton())));
    }

    /** Picks what this computer launches from the games its launchers already list. */
    private Button chooseLaunchButton() {
        Button choose = new Button("Choose…");
        choose.setOnAction(e -> LaunchTargetChooser.choose(services, described -> {
            report(true, "Launch target on this computer: " + described + ".");
            refresh();
        }, reason -> report(false, reason)));
        return choose;
    }

    private void report(boolean ok, String message) {
        launchStatus.setText(message);
        Styles.pick(launchStatus, ok ? Styles.MUTED_TEXT : Styles.ERROR_TEXT, Styles.MUTED_TEXT, Styles.ERROR_TEXT);
    }

    /**
     * The launch row's control: start the configured target <em>without</em> running the bot, so the user can
     * confirm the row's ✓ means what they wanted. Disabled, saying why, when this computer has none.
     *
     * <p>Rebuilt on every {@link #refresh()} rather than kept and re-bound; {@link QuickLaunch#button} re-reads
     * the target each time, so the button cannot go stale.
     */
    private Button quickLaunchButton() {
        return QuickLaunch.button(services, this::report);
    }

    /** Forgets a launch target nothing can read, so a run stops being handed it. */
    private Button clearLaunchButton() {
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            LaunchTargetValue.set(services, null);
            launchStatus.setText("Launch target cleared on this computer.");
            refresh();
        });
        return clear;
    }

    /**
     * A capture source counts as "chosen" once it is anything other than the whole desktop a fresh project
     * starts with — so the row nudges the user to point at their game window or emulator.
     *
     * <p>It also covers the source being unreadable, which is the same nudge: no {@code Sdk.java}, a body
     * the host will not read, or an expression the user wrote themselves.
     */
    private static boolean captureConfigured(CaptureSource source) {
        return source != null && !CaptureLabels.isDesktop(source);
    }

    private static String describeCapture(CaptureSource source) {
        if (source == null) return "Not set.";
        String label = CaptureLabels.shortLabel(source);
        return captureConfigured(source) ? label : label + " — pick your game window or emulator.";
    }

    /**
     * One row: a ✓/✗ status glyph, a bold title with a wrapped detail line beneath, and an optional control on
     * the right. {@code optional} steps show a neutral glyph and never a red ✗.
     */
    private HBox row(boolean done, boolean optional, String title, String detail, Node control) {
        Label glyph = new Label(done ? "✓" : (optional ? "○" : "✗"));
        glyph.setMinWidth(18);
        glyph.setStyle("-fx-font-size: 15px;");
        Styles.on(glyph, Styles.STRONG_TEXT,
                done ? Styles.OK_TEXT : (optional ? Styles.MUTED_TEXT : Styles.WARNING_TEXT));

        Label name = Styles.on(new Label(title), Styles.STRONG_TEXT);
        Label sub = new Label(detail);
        sub.setWrapText(true);
        Styles.on(sub, Styles.DIALOG_HINT);
        VBox text = new VBox(2, name, sub);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, glyph, text);
        if (control != null) row.getChildren().add(control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
