package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.SlotContext;
import com.botmaker.plugin.api.SlotRun;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Slots;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.plugin.toolkit.Thumbnail;
import com.botmaker.plugin.toolkit.Values;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.ImageTemplateGroup;
import com.botmaker.sdk.authoring.TemplateLibrary;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The editors for a <b>named picture</b> — this plugin's {@code ImageTemplate}, in both places the host edits
 * one, plus the tile shown beside a declared choice.
 *
 * <p>It replaces two controls that had drifted apart in the way this whole platform exists to stop. A block's
 * slot got a thumbnail button opening Studio's own gallery; a Parameters row got a different thumbnail button
 * opening the same gallery through a different dialog, with its own idea of what a missing file looks like.
 * Both of the host's dispatch arms are gone — the {@code PickerRegistry} entry and the {@code IMAGE_TEMPLATE}
 * case — because <b>a type the host answers is a type no plugin is ever offered</b>.
 *
 * <p><b>The two places disagree about what a picture is called, and that is on purpose.</b> A slot holds Java
 * ({@code new ImageTemplate("src/main/resources/images/gold.png")}) and a project file holds the base name
 * ({@code gold}), because a stored path would break the moment the images folder moved and a stored constructor
 * would be Java in a file that holds none. {@link Slots#write} takes both and picks, so this editor never asks
 * which place it is in.
 *
 * <p>The pictures come from {@link TemplateLibrary} over {@link StudioServices#resourcesDir()} — the folder is
 * this plugin's, and the one thing only the host could tell it is which project is open. Nothing here reaches
 * the contract for a picture, which is what the deletion of {@code StudioServices.assets()} was about.
 */
final class TemplateEditors {

    /** The frame a picture is shown in on a block, and twice that is what is decoded for it. */
    private static final double CHIP = 34;
    /** The tile beside a declared choice — smaller, because it sits in a list of them. */
    private static final double TILE = 48;

    private TemplateEditors() {}

    /**
     * A pill naming the current picture, with its thumbnail, that opens the project's gallery.
     *
     * <p>A menu rather than a plain button because <em>clear</em> has to be reachable: a slot with no picture
     * in it is a legal state (a freshly inserted block), and an editor that can only ever set one makes the
     * empty state unreachable once it has been left.
     */
    static Node template(ValueContext ctx) {
        MenuButton pill = Pills.bare(label(nameOf(ctx)));
        ImageView thumb = new ImageView();
        thumb.setFitWidth(CHIP);
        thumb.setFitHeight(CHIP);
        thumb.setPreserveRatio(true);
        showPicture(thumb, ctx.services(), nameOf(ctx), CHIP);
        pill.setGraphic(thumb);

        Pills.onOpen(pill, () -> List.of(
                Pills.item("Choose a picture…", () -> choose(ctx, picked -> {
                    commit(ctx, picked);
                    pill.setText(label(picked));
                    showPicture(thumb, ctx.services(), picked, CHIP);
                })),
                Pills.separator(),
                Pills.item("Clear", () -> {
                    Slots.write(ctx, "new " + ImageTemplate.class.getSimpleName() + "(\"\")", "",
                            ImageTemplate.class.getName());
                    pill.setText(label(""));
                    showPicture(thumb, ctx.services(), "", CHIP);
                })));
        return pill;
    }

    /**
     * The picture itself, beside a declared choice — the host's {@code preview} hook.
     *
     * <p>A choice of this type is stored as a name, and a name is not a picture: listing what an author picked
     * from a gallery as raw text puts the decoding back on the person the choices exist for. A name that no
     * longer resolves falls back to {@code null}, which the host draws as the plain label — the honest reading
     * of "this picture was deleted", and the same answer the pill above gives.
     */
    static Node preview(ValueContext ctx) {
        String name = nameOf(ctx);
        Image picture = picture(ctx.services(), name, TILE);
        if (picture == null) return null;
        ImageView view = new ImageView(picture);
        view.setFitWidth(TILE);
        view.setFitHeight(TILE);
        view.setPreserveRatio(true);
        return Styles.on(new HBox(view), Styles.TILE);
    }

    // ── several pictures ────────────────────────────────────────────────────────────────────────────────

    /**
     * A row of picture chips standing in for <b>several</b> pictures — the multi-picture counterpart of
     * {@link #template}, and the editor the contract's {@link SlotRun} was added for.
     *
     * <p>It draws the same row over two different shapes, which is the whole reason it is one editor:
     *
     * <ul>
     *   <li><b>An {@code ImageTemplateGroup} slot.</b> One slot holding {@code ImageTemplateGroup.of(a, b)},
     *       whose elements are the arguments of its own expression — read with {@link Slots#arguments} and
     *       written back as a whole new call. No {@code SlotRun} involved: the slot is one slot.</li>
     *   <li><b>A run of {@code ImageTemplate} arguments.</b> {@code found.hasAny(coin, gem)} and a
     *       {@code Matches} case are several sibling slots, and only the host can say so — which is what
     *       {@link SlotContext#run()} answers.</li>
     * </ul>
     *
     * <p>Before this, a varargs slot rendered one single-picture picker per argument that already existed, so
     * {@code found.hasAny(coin)} could never become {@code found.hasAny(coin, gem)}: each picker worked, and
     * there was simply no affordance that added a second one. Handing back the whole list is what fixes that,
     * and it is why {@code SlotRun.replace} takes a list rather than an index.
     */
    static Node group(ValueContext ctx) {
        HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        rebuild(row, ctx);
        return row;
    }

    /**
     * Redraws the whole row from the value.
     *
     * <p>Rebuilding rather than patching: every edit rewrites the source, the source is re-read, and a chip
     * holding an index into the old list would be pointing at the wrong picture after a removal. The same
     * reasoning that makes {@code HostSlotContext} resolve its slot on every call.
     */
    private static void rebuild(HBox row, ValueContext ctx) {
        List<String> elements = elementsOf(ctx);
        SlotRun run = runOf(ctx);
        int floor = run == null ? 0 : run.minimum();

        row.getChildren().clear();
        for (int i = 0; i < elements.size(); i++) {
            row.getChildren().add(chip(row, ctx, elements, i, floor));
        }
        row.getChildren().add(addButton(row, ctx, elements));
    }

    /** One picture: its thumbnail and name, with a menu to change or remove it. */
    private static MenuButton chip(HBox row, ValueContext ctx, List<String> elements, int index, int floor) {
        String name = nameOfSource(elements.get(index));
        MenuButton pill = Pills.bare(Values.labelOr(name, "?"));
        ImageView thumb = new ImageView();
        thumb.setFitWidth(24);
        thumb.setFitHeight(24);
        thumb.setPreserveRatio(true);
        showPicture(thumb, ctx.services(), name, 24);
        pill.setGraphic(thumb);

        Pills.onOpen(pill, () -> {
            MenuItem change = Pills.item("Change…", () -> choose(ctx, picked ->
                    write(row, ctx, replace(elements, index, literalFor(picked)))));
            // Disabled rather than absent at the floor: the row still shows that removal exists, and the
            // label says why this one cannot go. Silently omitting it reads as a missing feature.
            MenuItem remove = elements.size() <= floor
                    ? disabled("Remove (this branch needs at least " + floor
                               + (floor == 1 ? " picture)" : " pictures)"))
                    : Pills.item("Remove", () -> write(row, ctx, without(elements, index)));
            return List.of(change, remove);
        });
        return pill;
    }

    /** A menu entry that says what it would do and why it cannot. */
    private static MenuItem disabled(String label) {
        MenuItem item = new MenuItem(label);
        item.setDisable(true);
        return item;
    }

    /** The trailing add button — {@code Choose pictures…} while the row is empty, {@code ＋} after that. */
    private static Node addButton(HBox row, ValueContext ctx, List<String> elements) {
        return Pills.button(elements.isEmpty() ? "Choose pictures…" : "＋",
                () -> choose(ctx, picked -> {
                    List<String> next = new ArrayList<>(elements);
                    next.add(literalFor(picked));
                    write(row, ctx, next);
                }));
    }

    /** Writes the whole list back — through the run when there is one, as one call when there is not. */
    private static void write(HBox row, ValueContext ctx, List<String> elements) {
        SlotRun run = runOf(ctx);
        if (run != null) {
            run.replace(elements, ImageTemplate.class.getName());
        } else {
            SlotContext slot = ctx.asSlot();
            if (slot == null) return;
            slot.replaceWith(ImageTemplateGroup.class.getSimpleName() + ".of("
                             + String.join(", ", elements) + ")",
                    ImageTemplateGroup.class.getName(), ImageTemplate.class.getName());
        }
        rebuild(row, ctx);
    }

    /**
     * The pictures the value currently names, as the expressions that name them.
     *
     * <p>An element this editor cannot read is kept exactly as it stands rather than dropped — a group
     * holding a constant is a real thing to have written, and every write here hands back the whole list, so
     * dropping one would delete it on the strength of not understanding it.
     */
    static List<String> elementsOf(ValueContext ctx) {
        SlotRun run = runOf(ctx);
        if (run != null) return run.elements();
        SlotContext slot = ctx.asSlot();
        if (slot == null) return List.of();
        String source = slot.currentSource();
        return source != null && source.contains(".of(") ? Slots.arguments(source) : List.of();
    }

    /** This slot's run, or {@code null} — the question only a host can answer. */
    private static SlotRun runOf(ValueContext ctx) {
        SlotContext slot = ctx.asSlot();
        return slot == null ? null : slot.run();
    }

    /** Whether this is one argument of a run of pictures, which is what makes {@link #group} claim it. */
    static boolean isRunOfPictures(ValueContext ctx) {
        return ctx.type().is(ImageTemplate.class) && runOf(ctx) != null;
    }

    private static List<String> replace(List<String> base, int index, String element) {
        List<String> copy = new ArrayList<>(base);
        copy.set(index, element);
        return copy;
    }

    private static List<String> without(List<String> base, int index) {
        List<String> copy = new ArrayList<>(base);
        copy.remove(index);
        return copy;
    }

    // ── the gallery ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * Opens the project's pictures and hands back the base name of the one chosen.
     *
     * <p>The listing runs off the JavaFX thread, which {@link Modals#gallery} arranges: walking the images
     * folder and decoding every picture in it is proportional to how much the user has captured, so doing it
     * while drawing freezes the editor for exactly the people with the most templates.
     */
    private static void choose(ValueContext ctx, java.util.function.Consumer<String> onPicked) {
        StudioServices services = ctx.services();
        SlotRun run = runOf(ctx);
        List<String> only = run == null ? null : namesOf(run.allowed());
        Modals.gallery(ctx,
                Modals.Gallery.pictures("Choose a picture",
                        only == null || !only.isEmpty()
                                ? "No pictures yet — capture some with the toolbar's Capture Templates."
                                : "This branch can only test pictures its find call was given, and none of "
                                  + "them can be read from the code around it."),
                () -> narrow(items(services), only),
                picked -> onPicked.accept(picked.value()));
    }

    /**
     * The pictures a narrowed row may offer, or all of them when {@code only} is {@code null}.
     *
     * <p>A {@code Matches} case can only ever test pictures its enclosing find call was given, so offering
     * the whole library there lets somebody write a branch that is dead by construction. The host works the
     * set out from the code around the run and hands it over as **element sources**, which is the only form
     * it could hand over without knowing what a picture is; decoding them back to names is this plugin's job
     * and is exactly what it is qualified to do.
     */
    private static List<Thumbnail> narrow(List<Thumbnail> all, List<String> only) {
        if (only == null) return all;
        List<Thumbnail> out = new ArrayList<>();
        for (Thumbnail item : all) {
            if (only.contains(item.value())) out.add(item);
        }
        return out;
    }

    /** Element sources as the picture names they spell, dropping any that name none. */
    private static List<String> namesOf(List<String> sources) {
        if (sources == null) return null;
        List<String> names = new ArrayList<>();
        for (String source : sources) {
            String name = nameOfSource(source);
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    /** Every picture in the project, as gallery cells keyed by base name. Called off the FX thread. */
    static List<Thumbnail> items(StudioServices services) {
        Path resources = resourcesDir(services);
        if (resources == null) return List.of();
        // The placeholder is made here rather than at project creation (2026-09-01): a project created in an
        // editor that never loaded this plugin has no use for a picture, and this is the first moment one is
        // asked for. Best-effort — an unwritable folder is a gallery with one fewer cell, not a refusal.
        try {
            TemplateLibrary.ensurePlaceholder(resources);
        } catch (java.io.IOException ignored) {
            // nothing to say: the list below simply will not include it
        }
        List<Thumbnail> out = new ArrayList<>();
        for (Path file : TemplateLibrary.list(resources)) {
            String name = TemplateLibrary.baseName(file);
            out.add(new Thumbnail(name, name, decode(file, 96)));
        }
        return out;
    }

    // ── reading and writing the value ───────────────────────────────────────────────────────────────────

    /**
     * The base name the value currently names, or {@code ""}.
     *
     * <p>Two readings, because the two places store different things: a row holds the name already, and a slot
     * holds {@code new ImageTemplate("…/gold.png")}, whose path is read out with {@link Slots#arguments} and
     * {@link Slots#stringLiteral} rather than with a parser — the contract hands over source text and the
     * toolkit depends on no parsing library. Anything else (a variable, a constant, a call) reads as no name,
     * which is right: the editor cannot represent it and must not overwrite it silently.
     */
    static String nameOf(ValueContext ctx) {
        if (ctx.asSlot() == null) return ctx.single().trim();
        return nameOfSource(Slots.raw(ctx));
    }

    /** {@link #nameOf} over one expression, so a run of them can be read the same way. */
    static String nameOfSource(String source) {
        String path = pathOf(source);
        return path == null ? "" : baseNameOf(path);
    }

    /** The path inside {@code new ImageTemplate("…")}, or {@code null} for any other expression. */
    static String pathOf(String source) {
        String s = source == null ? "" : source.trim();
        if (!s.startsWith("new ") || !s.contains(ImageTemplate.class.getSimpleName())) return null;
        List<String> args = Slots.arguments(s);
        if (args.size() != 1) return null;
        String path = Slots.stringLiteral(args.getFirst());
        return path == null || path.isBlank() ? null : path;
    }

    /** Writes the picture called {@code baseName} — Java in a slot, the name itself in a row. */
    static void commit(ValueContext ctx, String baseName) {
        Slots.write(ctx, literalFor(baseName), baseName == null ? "" : baseName,
                ImageTemplate.class.getName());
    }

    /** {@code new ImageTemplate("src/main/resources/images/<name>.png")}. */
    static String literalFor(String baseName) {
        return "new " + ImageTemplate.class.getSimpleName() + "("
               + Slots.quote(baseName == null || baseName.isBlank() ? "" : TemplateLibrary.pathForName(baseName))
               + ")";
    }

    /** The base name in a stored path, whatever folder it names — {@code images/gold.png} is {@code gold}. */
    static String baseNameOf(String path) {
        String p = path == null ? "" : path.trim();
        int slash = p.lastIndexOf('/');
        String file = slash < 0 ? p : p.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        return dot <= 0 ? file : file.substring(0, dot);
    }

    // ── pictures ────────────────────────────────────────────────────────────────────────────────────────

    private static String label(String baseName) {
        return Values.labelOr(baseName, "Choose a picture…");
    }

    /** Points {@code view} at the picture for {@code baseName}, or hides it when there is none to show. */
    private static void showPicture(ImageView view, StudioServices services, String baseName, double size) {
        Image picture = picture(services, baseName, size);
        view.setImage(picture);
        view.setVisible(picture != null);
        view.setManaged(picture != null);
    }

    /**
     * The picture for {@code baseName}, or {@code null}.
     *
     * <p>{@code null} for a name nothing resolves — a picture the user deleted, or a value written before it
     * was captured. Every caller shows the name alone in that case rather than a broken frame, because "this
     * picture is gone" is information and an empty box is not.
     */
    private static Image picture(StudioServices services, String baseName, double size) {
        Path resources = resourcesDir(services);
        if (resources == null || baseName == null || baseName.isBlank()) return null;
        return decode(TemplateLibrary.fileForName(resources, baseName), size);
    }

    private static Image decode(Path file, double size) {
        try {
            if (file == null || !Files.exists(file)) return null;
            return new Image(file.toUri().toString(), size * 2, size * 2, true, true);
        } catch (RuntimeException e) {
            // A file that is not a picture at all, or one deleted between the listing and the decode. Both are
            // ordinary; neither is worth losing the editor over. (Rule 2: nothing throws while building a node.)
            return null;
        }
    }

    private static Path resourcesDir(StudioServices services) {
        try {
            return services == null ? null : services.resourcesDir();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
