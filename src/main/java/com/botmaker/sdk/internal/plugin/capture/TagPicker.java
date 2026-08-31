package com.botmaker.sdk.internal.plugin.capture;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.authoring.TagCatalog;
import com.botmaker.sdk.authoring.TemplateLibrary;
import com.botmaker.sdk.authoring.TemplateManifest;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The one way a picture is tagged: a menu of the project's declared tags, ticked on and off.
 *
 * <p>Every tag field used to be a text field — one on the batch capture dialog, one in the resource manager,
 * none at all on the single capture. Free text is what let "Minning" become a tag, and it is why the set of
 * tags was whatever happened to have been typed. This control offers {@link TagCatalog} and nothing else, so
 * the only way to add a tag to a project is the explicit {@code New tag…} entry at the bottom — which
 * declares it rather than attaching a string to one picture.
 *
 * <p><b>It is the plugin's, and the reason is that a tag is a tag <em>of a picture</em>.</b> A picture is
 * {@code ImageTemplate}'s concept and the catalog it draws is {@link TagCatalog}, read out of this plugin's
 * own manifest under {@link StudioServices#resourcesDir()}. The host is the only possible source of which
 * project is open; everything after that is vocabulary, and vocabulary on the contract is the back door the
 * platform exists to close. Studio's {@code TagPicklist} is a two-line subclass of this, so the tag manager
 * and the parameters screen — which are host work and are staying — go on offering exactly the same menu.
 *
 * <p>Activity tags are listed first under their own heading, custom ones after, matching the catalog's order
 * so the same two groups appear here, in the gallery and in the tag manager. Ticking does not close the menu
 * ({@code hideOnClick(false)}): tagging is usually plural, and a menu that shut after each tick would make
 * three tags three trips.
 */
public class TagPicker extends MenuButton {

    private final StudioServices services;
    private final Set<String> selected = new LinkedHashSet<>();
    private TagCatalog catalog;

    public TagPicker(StudioServices services) {
        this.services = services;
        this.catalog = catalogOf(services);
        getStyleClass().add("tag-picklist");
        setMaxWidth(Double.MAX_VALUE);
        rebuild();
        refreshLabel();
    }

    /** The ticked tags, in catalog order and narrowed to what the project still declares. */
    public List<String> selected() {
        return catalog.declaredOnly(selected);
    }

    /** Ticks exactly {@code tags} (undeclared names are ignored, not shown as ghosts). */
    public void select(Collection<String> tags) {
        selected.clear();
        selected.addAll(catalog.declaredOnly(tags));
        rebuild();
        refreshLabel();
    }

    /** Re-reads the catalog — for a picker that outlives a change to the project's tags. */
    public void reloadCatalog() {
        catalog = catalogOf(services);
        select(new ArrayList<>(selected));
    }

    private void rebuild() {
        getItems().clear();
        List<String> activityTags = catalog.namesOf(TagCatalog.Kind.ACTIVITY);
        List<String> customTags = catalog.namesOf(TagCatalog.Kind.CUSTOM);

        if (!activityTags.isEmpty()) {
            getItems().add(heading("Activities"));
            activityTags.forEach(tag -> getItems().add(tagItem(tag)));
        }
        if (!customTags.isEmpty()) {
            if (!activityTags.isEmpty()) getItems().add(new SeparatorMenuItem());
            getItems().add(heading("Custom"));
            customTags.forEach(tag -> getItems().add(tagItem(tag)));
        }
        if (!getItems().isEmpty()) getItems().add(new SeparatorMenuItem());

        MenuItem create = new MenuItem("New tag…");
        create.setOnAction(e -> createTag());
        getItems().add(create);
    }

    /** A non-clickable group label, so the two kinds of tag read as two groups without nesting them. */
    private static MenuItem heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("tag-picklist-heading");
        CustomMenuItem item = new CustomMenuItem(label, false);
        item.setDisable(true);
        return item;
    }

    /**
     * One tag row. A real {@link CheckBox} inside a {@link CustomMenuItem} rather than a
     * {@code CheckMenuItem}, because only {@code CustomMenuItem} can decline to close the menu — and tagging
     * is usually plural, so a menu that shut after each tick would make three tags three trips.
     */
    private CustomMenuItem tagItem(String tag) {
        CheckBox box = new CheckBox(tag);
        box.setSelected(selected.contains(tag));
        box.selectedProperty().addListener((o, was, is) -> {
            if (is) selected.add(tag);
            else selected.remove(tag);
            refreshLabel();
        });
        CustomMenuItem item = new CustomMenuItem(box, false);
        // Clicking the row (not just the box) toggles it: a 2px miss should not be a no-op.
        item.setOnAction(e -> box.setSelected(!box.isSelected()));
        return item;
    }

    private void createTag() {
        Window owner = getScene() == null ? null : getScene().getWindow();
        promptNewTag(services, owner).ifPresent(tag -> {
            catalog = TemplateLibrary.declareTag(services.resourcesDir(), tag);
            selected.add(tag);
            rebuild();
            refreshLabel();
        });
    }

    private void refreshLabel() {
        List<String> chosen = selected();
        setText(chosen.isEmpty() ? "No tags" : String.join(", ", chosen));
    }

    /**
     * Asks for a new custom tag name. Shared with the tag manager and the parameters screen so "what may a tag
     * be called" has one answer: non-blank, not one of the computed buckets, and not already declared —
     * including by an activity, whose tag is the activity's to name.
     *
     * <p><b>It says why inline, as you type.</b> The refusal used to be a second window on top of this one,
     * arriving after Create and taking the typed name away with it — so learning that a category already
     * existed cost two dialogs and a retype. Now the reason sits in red under the field and Create is simply
     * not available until the name is usable, which is the same rule the picture rename field follows.
     *
     * <p>Returns the sanitized name; the caller declares it.
     */
    public static Optional<String> promptNewTag(StudioServices services, Window owner) {
        TagCatalog catalog = catalogOf(services);
        Dialog<String> dialog = new Dialog<>();
        services.theme().apply(dialog);
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("New tag");
        dialog.setHeaderText(null);
        ButtonType ok = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);

        TextField field = new TextField();
        field.setPromptText("e.g. Shared buttons");
        Label problem = new Label();
        problem.getStyleClass().add("dialog-error-text");
        problem.setWrapText(true);
        VBox box = new VBox(8, new Label("Name this tag. It will be offered everywhere pictures "
                + "are tagged, whether or not anything carries it yet."), field, problem);
        box.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(box);

        Node create = dialog.getDialogPane().lookupButton(ok);
        Runnable validate = () -> {
            String reason = tagProblem(catalog, TemplateManifest.sanitizeTag(field.getText()));
            // Blank is a refusal too, but not a complaint: an empty field is where everyone starts.
            String typed = field.getText() == null ? "" : field.getText().trim();
            problem.setText(typed.isEmpty() ? "" : reason == null ? "" : reason);
            create.setDisable(reason != null);
        };
        field.textProperty().addListener((o, was, is) -> validate.run());
        validate.run();
        Platform.runLater(field::requestFocus);
        dialog.setResultConverter(bt -> bt == ok ? field.getText() : null);

        Optional<String> raw = dialog.showAndWait();
        if (raw.isEmpty()) return Optional.empty();
        String tag = TemplateManifest.sanitizeTag(raw.get());
        return tagProblem(catalog, tag) == null ? Optional.of(tag) : Optional.empty();
    }

    /** Why {@code tag} can't be declared, phrased for the user, or null when it can. */
    private static String tagProblem(TagCatalog catalog, String tag) {
        if (tag == null || tag.isBlank()) return "Please enter a name for the tag.";
        if (TemplateManifest.isSyntheticTag(tag)) {
            return "\"" + tag + "\" is a built-in group. Choose a different name.";
        }
        TagCatalog.Tag existing = catalog.find(tag);
        if (existing == null) return null;
        return existing.isManaged()
                ? "\"" + existing.name() + "\" is the tag of the activity of that name — it already exists."
                : "There is already a tag called \"" + existing.name() + "\".";
    }

    /**
     * The project's declared tags, or an empty catalog when there is no project.
     *
     * <p>Read on every call rather than held, for the same reason {@link EditorFrame#defaultTarget} is: a tag
     * declared in another window has to be offered here without anything being rebuilt.
     */
    private static TagCatalog catalogOf(StudioServices services) {
        Path resources = services == null ? null : services.resourcesDir();
        return resources == null ? TagCatalog.of(List.of(), List.of()) : TemplateLibrary.tagCatalog(resources);
    }
}
