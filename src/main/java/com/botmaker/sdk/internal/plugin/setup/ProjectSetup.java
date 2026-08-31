package com.botmaker.sdk.internal.plugin.setup;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.CaptureModel;
import com.botmaker.sdk.authoring.CaptureTargetModel;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.sdk.authoring.TemplateLibrary;
import com.botmaker.sdk.internal.plugin.launch.QuickLaunch;
import com.botmaker.shared.config.ProjectFile;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;

/**
 * The <b>Project Setup</b> checklist — one window that says, for a project as it stands right now, whether it
 * has something to launch, something to capture, a reference resolution and any pictures, and what to do
 * about each answer that is no.
 *
 * <h2>Why this is the plugin's</h2>
 *
 * <p>Every one of the four rows reads a fact that belongs to this module. The launch target and the capture
 * size are {@code botmaker-project.properties}, read through shared's {@link ProjectFile}; the capture target
 * and the reference resolution are {@code capture.json}, read through {@link Authoring}; the pictures are the
 * images folder, read through {@link TemplateLibrary}. The host holds none of it, which is what made this a
 * checklist the editor could only answer by asking the SDK anyway. What the host supplies is the three things
 * nobody else can: which project is open, the current look, and the window this modal is owned by.
 *
 * <h2>Every row is a statement, not a button</h2>
 *
 * <p>Studio's version opened a dialog per row. This one opens nothing, and that is deliberate rather than a
 * reduction: two of the four destinations are already this plugin's own toolbar items (🎯 Capture Targets,
 * ✂ Capture Templates) and a plugin has no handle on another item, while the other two are still host dialogs
 * this window may not name. So a row reports its state and says where to change it — the precedent the
 * Capture Templates and Capture Targets steps both set. The one control that survives is <b>▶ Launch now</b>,
 * because starting the configured target is this module's own work and needs nobody's dialog.
 *
 * <h2>Refreshing</h2>
 *
 * <p>Studio's copy also subscribed to its {@code SettingsChangedEvent}. There is no such thing on the
 * contract and there should not be — so the two triggers that remain are the window regaining focus (which is
 * what happens when any of the four destinations closes) and the <b>Re-check</b> button. Between them they
 * cover every way a row's answer can change while this window is open.
 */
public final class ProjectSetup {

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

    private void show() {
        Label heading = new Label("Set your project up to run");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label intro = new Label("A new bot needs a few things wired up before it can run. Work down the list — "
                + "each row says where to set that step, and ticks green once it's done.");
        intro.setWrapText(true);
        intro.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        summary = new Label();
        summary.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        launchStatus = new Label();
        launchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        rows = new VBox(10);

        Button recheck = new Button("Re-check");
        recheck.setOnAction(e -> refresh());
        Button done = new Button("Done");
        done.setDefaultButton(true);
        done.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, summary, spacer, recheck, done);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(14, heading, intro, new Separator(), rows, new Separator(), launchStatus, bar);
        root.setPadding(new Insets(18));

        stage = new Stage();
        stage.setTitle("Project Setup");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setScene(services.theme().scene(root, 520, 460));
        stage.setMinWidth(440);
        stage.setMinHeight(360);
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
        CaptureModel capture = readCapture(resources);

        String launchSpec = ProjectFile.launchTarget(resources);
        boolean launchDone = launchSpec != null && !launchSpec.isBlank();
        boolean captureDone = captureConfigured(capture);
        boolean resolutionDone = capture.reference() != null;
        int templateCount = TemplateLibrary.list(resources).size();

        int required = 3;
        int doneCount = (launchDone ? 1 : 0) + (captureDone ? 1 : 0) + (resolutionDone ? 1 : 0);
        summary.setText(doneCount + " of " + required + " required steps done"
                + (doneCount == required ? " — you're ready to run." : ""));

        rows.getChildren().setAll(
                row(launchDone, false, "Launch target",
                        launchDone
                                ? LaunchSpec.describe(launchSpec)
                                : "Not set — pick what the bot should open, with the Launch Target button on "
                                        + "the toolbar.",
                        quickLaunchButton(resources)),
                row(captureDone, false, "Capture target",
                        describeCapture(capture) + " Choose it with 🎯 Capture Targets on the toolbar.",
                        null),
                row(resolutionDone, false, "Reference resolution",
                        resolutionDone
                                ? capture.reference().width() + "×" + capture.reference().height()
                                : "Not set — the size pictures are captured at. It is set on the first "
                                        + "capture, or in Project ▸ Project Settings.",
                        null),
                row(templateCount > 0, true, "Pictures (optional)",
                        templateCount == 0
                                ? "None yet — only needed for image-matching bots (skip for pixel/OCR/coords). "
                                        + "Capture them with ✂ Capture Templates on the toolbar."
                                : templateCount + (templateCount == 1 ? " picture saved." : " pictures saved."),
                        null));
    }

    /** The project's stored capture model, or an empty one when it cannot be read. */
    private CaptureModel readCapture(Path resources) {
        try {
            return Authoring.readCapture(SdkVersion.latest(), resources);
        } catch (Exception unreadable) {
            System.err.println("Could not read the project's capture targets: " + unreadable.getMessage());
            return CaptureModel.empty();
        }
    }

    /**
     * The launch row's control: start the configured target <em>without</em> running the bot, so the user can
     * confirm the row's ✓ means what they wanted — and so the game's window exists before they walk down to
     * the capture row, which is the next step and needs something to point at.
     *
     * <p>Rebuilt on every {@link #refresh()} rather than kept and re-bound; {@link QuickLaunch#button} re-reads
     * the target each time, so the button cannot go stale.
     */
    private Button quickLaunchButton(Path resources) {
        return QuickLaunch.button(resources, (ok, message) -> {
            launchStatus.setText(message);
            launchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (ok ? "gray" : "#c0392b") + ";");
        });
    }

    /**
     * A capture target counts as "chosen" once it is anything other than the whole-desktop seed a fresh
     * project starts with — so the row nudges the user to point at their game window or emulator, while an
     * explicit multi-target or non-desktop default still satisfies it.
     */
    private static boolean captureConfigured(CaptureModel capture) {
        CaptureTargetModel def = defaultTarget(capture);
        if (def == null) return false;
        return !def.isDesktop() || capture.targets().size() > 1;
    }

    private static CaptureTargetModel defaultTarget(CaptureModel capture) {
        Integer index = capture.defaultIndex();
        if (index == null || index < 0 || index >= capture.targets().size()) return null;
        return capture.targets().get(index);
    }

    private static String describeCapture(CaptureModel capture) {
        CaptureTargetModel def = defaultTarget(capture);
        if (def == null) return "No default set.";
        String label = def.shortLabel();
        return captureConfigured(capture) ? label : label + " (default — pick your game window or emulator).";
    }

    /**
     * One row: a ✓/✗ status glyph, a bold title with a wrapped detail line beneath, and an optional control on
     * the right. {@code optional} steps show a neutral glyph and never a red ✗.
     */
    private HBox row(boolean done, boolean optional, String title, String detail, Node control) {
        Label glyph = new Label(done ? "✓" : (optional ? "○" : "✗"));
        glyph.setMinWidth(18);
        glyph.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: "
                + (done ? "#27ae60" : (optional ? "#95a5a6" : "#e67e22")) + ";");

        Label name = new Label(title);
        name.setStyle("-fx-font-weight: bold;");
        Label sub = new Label(detail);
        sub.setWrapText(true);
        sub.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        VBox text = new VBox(2, name, sub);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, glyph, text);
        if (control != null) row.getChildren().add(control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
