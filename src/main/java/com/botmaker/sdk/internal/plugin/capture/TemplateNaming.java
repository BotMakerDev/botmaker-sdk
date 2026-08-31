package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.TemplateLibrary;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Naming a freshly captured picture — one crop, or a whole batch of them at once.
 *
 * <p>Two dialogs and one set of rules. What may a picture be called is {@link TemplateLibrary}'s question
 * (sanitized, non-blank, not reserved, not already on disk), what it may be tagged with is
 * {@link TagPicker}'s, and both are this plugin's own vocabulary read out of {@link
 * StudioServices#resourcesDir()} — which is why the step is here rather than in the host. The host is the
 * only possible source of <em>which project is open</em>; the rest is files.
 *
 * <p>The single and the batch form deliberately share this class rather than living apart as they did in the
 * editor: the batch dialog had a tag field and the single one had none, so a picture captured on its own
 * could only be filed later, from the resource manager. They enforce the same three refusals now because
 * they are written next to each other.
 */
public final class TemplateNaming {

    private TemplateNaming() {}

    /** A picture about to be saved: its validated name and the declared tags chosen for it. */
    public record NamedCapture(String name, List<String> tags) {}

    /**
     * A crop the user chose to keep, paired with its validated (sanitized, unique) name and its tags.
     *
     * <p>{@code index} is the crop's position in the list handed to {@link #showBatch} — carried through
     * because only the kept rows come back, so a caller that keyed something else off that list (the "Pick
     * all" session keys an <em>argument slot</em>) cannot recover it positionally once a row is discarded.
     */
    public record NamedTemplate(int index, String name, BufferedImage image, List<String> tags) {}

    /**
     * A batch's whole result: the kept pictures, each carrying its own tags.
     *
     * <p>The tags ride along rather than being applied here because the pictures do not exist yet — the
     * caller is what saves them, so it is also what tags them, after the save succeeds.
     */
    public record Batch(List<NamedTemplate> templates) {

        static Batch none() {
            return new Batch(List.of());
        }

        /**
         * {@code name → tags} for the rows in {@code saved} — what {@code TemplateLibrary.applyTags} takes.
         * Narrowed to the names that reached the disk, since a picture whose save failed must not leave an
         * assignment behind for a file that isn't there.
         */
        public Map<String, List<String>> tagsFor(Collection<String> saved) {
            Map<String, List<String>> byName = new LinkedHashMap<>();
            for (NamedTemplate t : templates) {
                if (saved.contains(t.name())) byName.put(t.name(), t.tags());
            }
            return byName;
        }
    }

    // ── One picture ────────────────────────────────────────────────────────────────────────────────────

    /**
     * The naming step for a single freshly captured picture: a thumbnail of {@code preview} so the user sees
     * what they are naming, the name field, and the tag picker.
     *
     * <p>{@code suggestedTag} is preselected when the project declares it (the open activity's tag,
     * normally); it is a selection over declared tags, never a new one. ARGB (ellipse/object) crops preview
     * with their transparency.
     *
     * <p>Only ever reached for a <em>new</em> picture: renaming is inline in the resource manager, under the
     * picture, where the name that is already taken is on screen next to the field — a dialog that accepts a
     * name and then refuses it was the wrong shape for the one operation whose answer depends on what else
     * the library holds. So this loops rather than returning a refusal.
     */
    public static Optional<NamedCapture> promptNew(StudioServices services, Window owner,
                                                   BufferedImage preview, String suggestedTag) {
        TagPicker tags = new TagPicker(services);
        if (suggestedTag != null) tags.select(List.of(suggestedTag));
        while (true) {
            Dialog<String> dialog = new Dialog<>();
            services.theme().apply(dialog);
            if (owner != null) dialog.initOwner(owner);
            dialog.setTitle("Picture name");
            dialog.setHeaderText(null);
            ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

            TextField field = new TextField();
            field.setPromptText("picture name");

            VBox content = new VBox(10);
            content.setPadding(new Insets(10));
            if (preview != null) {
                ImageView thumb = new ImageView(ScreenCapture.toFxImage(preview));
                thumb.setPreserveRatio(true);
                thumb.setFitWidth(180);
                thumb.setFitHeight(140);
                HBox thumbRow = new HBox(thumb);
                thumbRow.setAlignment(Pos.CENTER);
                content.getChildren().add(thumbRow);
            }
            HBox nameRow = new HBox(8, new Label("Name:"), field);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(field, Priority.ALWAYS);
            content.getChildren().add(nameRow);
            HBox tagRow = new HBox(8, new Label("Tags:"), tags);
            tagRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(tags, Priority.ALWAYS);
            content.getChildren().add(tagRow);
            dialog.getDialogPane().setContent(content);
            Platform.runLater(field::requestFocus);
            dialog.setResultConverter(bt -> bt == ok ? field.getText() : null);

            Optional<String> raw = dialog.showAndWait();
            if (raw.isEmpty()) return Optional.empty(); // cancelled
            Path resources = services.resourcesDir();
            String name = TemplateLibrary.sanitizeName(raw.get());
            String problem = nameProblem(resources, name, "Please enter a name for the picture.");
            if (problem != null) {
                warn(services, owner, problem);
                continue;
            }
            return Optional.of(new NamedCapture(name, tags.selected()));
        }
    }

    // ── A batch ────────────────────────────────────────────────────────────────────────────────────────

    private record Row(BufferedImage image, TextField name, CheckBox discard, TagPicker tags) {}

    /**
     * Shows the modal naming dialog for {@code crops} and returns the kept, named pictures (empty if the user
     * cancelled or discarded them all). Must be called on the FX thread.
     *
     * <p>{@code suggestedTag} seeds every row's tags — the open activity's tag when the capture started from
     * one, since that is the grouping the user would otherwise choose by hand. It is only a default: each
     * row's picker can be changed, and the "Tag them all" control at the bottom re-applies a selection across
     * every kept row for the case where the whole batch belongs together.
     */
    public static Batch showBatch(StudioServices services, Window owner, List<BufferedImage> crops,
                                  String suggestedTag) {
        Dialog<Batch> dialog = new Dialog<>();
        services.theme().apply(dialog);
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Name captured pictures");
        dialog.setHeaderText("Name each picture, or tick Discard to skip it.");

        ButtonType saveAll = new ButtonType("Save all", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveAll, ButtonType.CANCEL);

        List<String> seedTags = suggestedTag == null ? List.of() : List.of(suggestedTag);
        List<Row> rows = new ArrayList<>();
        VBox list = new VBox(8);
        list.setPadding(new Insets(12));
        for (int i = 0; i < crops.size(); i++) {
            BufferedImage crop = crops.get(i);
            TagPicker tags = new TagPicker(services);
            tags.select(seedTags);
            Row row = new Row(crop, new TextField(), new CheckBox("Discard"), tags);
            row.name().setPromptText("picture name");
            rows.add(row);
            list.getChildren().add(buildRow(services, i + 1, row));
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(420, 8 + crops.size() * 88));

        TagPicker batchTags = new TagPicker(services);
        batchTags.select(seedTags);
        Button applyToAll = new Button("Apply to all");
        applyToAll.setOnAction(e -> {
            List<String> chosen = batchTags.selected();
            for (Row row : rows) {
                row.tags().reloadCatalog();   // a tag declared in the batch picker has to reach the rows
                row.tags().select(chosen);
            }
        });
        HBox tagRow = new HBox(8, new Label("Tag them all as:"), batchTags, applyToAll);
        tagRow.setAlignment(Pos.CENTER_LEFT);
        tagRow.setPadding(new Insets(4, 12, 0, 12));
        HBox.setHgrow(batchTags, Priority.ALWAYS);

        VBox pane = new VBox(8, scroll, tagRow);
        dialog.getDialogPane().setContent(pane);

        // Intercept "Save all" so validation failures keep the dialog open instead of closing it.
        dialog.getDialogPane().lookupButton(saveAll).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (validate(services, owner, rows) == null) e.consume();
        });

        dialog.setResultConverter(bt -> {
            if (bt != saveAll) return Batch.none();
            List<NamedTemplate> kept = validate(services, owner, rows);
            return kept == null ? Batch.none() : new Batch(kept);
        });
        return dialog.showAndWait().orElse(Batch.none());
    }

    private static HBox buildRow(StudioServices services, int index, Row row) {
        Label badge = new Label(String.valueOf(index));
        badge.setMinWidth(20);
        badge.setAlignment(Pos.CENTER);

        ImageView thumb = new ImageView(ScreenCapture.toFxImage(row.image()));
        thumb.setPreserveRatio(true);
        thumb.setFitWidth(96);
        thumb.setFitHeight(72);

        HBox.setHgrow(row.name(), Priority.ALWAYS);
        // Discarded rows grey out both editors — there is nothing to name or file.
        row.name().disableProperty().bind(row.discard().selectedProperty());
        row.tags().disableProperty().bind(row.discard().selectedProperty());

        HBox box = new HBox(10, badge, thumb, row.name(), row.tags(), row.discard());
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4));
        box.setStyle("-fx-border-color: #d0d0d0; -fx-border-radius: 4; -fx-background-radius: 4;");
        box.setMinHeight(Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * Validates the kept rows and returns the resulting pictures, or {@code null} (after warning) if any kept
     * name is blank, collides with an existing picture, is reserved, or duplicates another kept name.
     */
    private static List<NamedTemplate> validate(StudioServices services, Window owner, List<Row> rows) {
        Path resources = services.resourcesDir();
        List<NamedTemplate> kept = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.discard().isSelected()) continue;
            String name = TemplateLibrary.sanitizeName(row.name().getText());
            String lower = name.toLowerCase(Locale.ROOT);
            if (seen.contains(lower) && !name.isBlank()) {
                return fail(services, owner,
                        "Row " + (i + 1) + ": the name \"" + name + "\" is used more than once.");
            }
            String problem = nameProblem(resources, name, "Please enter a name (or tick Discard).");
            if (problem != null) return fail(services, owner, "Row " + (i + 1) + ": " + problem);
            seen.add(lower);
            kept.add(new NamedTemplate(i, name, row.image(), row.tags().selected()));
        }
        return kept;
    }

    /**
     * Why {@code name} cannot be used, phrased for the user, or {@code null} when it can.
     *
     * <p>One method because the single dialog and the batch enforced the same three rules in two places, and
     * had already drifted in their wording. It is deliberately not asked of a discarded row: a row nobody is
     * keeping has no name to be wrong.
     *
     * <p>{@code blankReason} is the one sentence the two callers cannot share — a single capture has no
     * Discard box to be pointed at.
     */
    private static String nameProblem(Path resources, String name, String blankReason) {
        if (name == null || name.isBlank()) return blankReason;
        if (resources != null && TemplateLibrary.exists(resources, name)) {
            return "A picture named \"" + name + "\" already exists. Choose a different name.";
        }
        if (TemplateLibrary.isReservedName(name)) {
            return "\"" + name + "\" is reserved — choose another name.";
        }
        return null;
    }

    private static List<NamedTemplate> fail(StudioServices services, Window owner, String message) {
        warn(services, owner, message);
        return null;
    }

    private static void warn(StudioServices services, Window owner, String message) {
        Alert alert = services.theme().alert(Alert.AlertType.WARNING, message);
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }
}
