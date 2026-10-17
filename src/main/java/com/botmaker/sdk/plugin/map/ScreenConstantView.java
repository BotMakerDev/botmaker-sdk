package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.sdk.api.screens.ScreenVariant;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenDesk;
import com.botmaker.sdk.plugin.assist.ScreenShots;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.types.ScreenTypes;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@code Screens} class as the bot's {@code Screens.java} shows its setup: one line with its card as 🗺 Screens
 * draws it (marker, name, how far Discovery got) and a button opening the map on it (the user's call, 2026-10-15),
 * which its ▸ opens onto its shot, what each input does, what it owes and its notes (2026-10-17). The file is the plugin's, read-only in the editor; the map and the
 * assistant's tools edit it.
 */
public final class ScreenConstantView {

    private static final double THUMB = 40;
    private static final double SHOT = 360;

    private ScreenConstantView() {}

    /** Whether {@code ctx} is a {@code Screens} class's setup, the value its {@code setup()} returns. */
    public static boolean isScreenConstant(ValueContext ctx) {
        return ctx.type().is(Setup.class) && constantOf(read(ctx), ctx.value(Setup.class)).isPresent();
    }

    /**
     * The screen's line (marker, name, how far Discovery got, the button opening the map on it) and, once its ▸ opens
     * it, its shot, inputs, debts and notes: a file of fifty screens reads as a list (2026-10-17).
     */
    public static Node node(ValueContext ctx) {
        StudioServices services = ctx.services();
        // The map read once, and safely: a map being written draws the slot as written, never a broken editor.
        ScreenGraph graph = read(ctx);
        Optional<String> found = constantOf(graph, ctx.value(Setup.class));
        if (found.isEmpty()) return new Label(ctx.source());
        String constant = found.get();
        Card card = ScreenCards.card(graph, constant, ScreenDesk.stages(graph).getOrDefault(constant, ""));
        String key = key(services.projectDir(), constant);

        Label name = new Label(card.name());
        name.setStyle("-fx-font-weight: bold;");
        Label state = new Label(String.join(" · ", ScreenCards.pills(card).stream().map(ScreenCards.Pill::text)
                .toList()) + "  (" + card.tried() + "/" + card.inputs() + " tried)");
        Button toggle = new Button();
        toggle.setFocusTraversable(false);
        toggle.setStyle("-fx-background-color: transparent; -fx-padding: 0 4 0 0;");
        Button open = new Button("Open in 🗺");
        open.setOnAction(e -> ScreenMap.open(services, constant));
        HBox head = new HBox(8, toggle);
        head.setAlignment(Pos.CENTER_LEFT);
        thumb(services.resourcesDir(), card.marker()).ifPresent(head.getChildren()::add);
        head.getChildren().addAll(name, state, open);
        // A narrow pane cuts the pills short, never the button.
        HBox.setHgrow(state, Priority.SOMETIMES);
        open.setMinWidth(Region.USE_PREF_SIZE);

        VBox box = new VBox(6, head);
        box.setPadding(new Insets(2, 4, 2, 4));
        // Built on its first opening only: the shot and the rows are most of what a card costs.
        VBox[] body = {null};
        Runnable show = () -> {
            boolean shown = isOpen(key);
            toggle.setText(shown ? "▾" : "▸");
            if (shown && body[0] == null) body[0] = body(services, graph, constant);
            if (shown && !box.getChildren().contains(body[0])) box.getChildren().add(body[0]);
            if (!shown && body[0] != null) box.getChildren().remove(body[0]);
        };
        toggle.setOnAction(e -> {
            setOpen(key, !isOpen(key));
            show.run();
        });
        name.setOnMouseClicked(e -> toggle.fire());
        show.run();
        return box;
    }

    /** What the line opens onto: the shot, the way back, the looks, each input, the debts and the notes. */
    private static VBox body(StudioServices services, ScreenGraph graph, String constant) {
        MapScreen screen = graph.screens().get(constant);
        VBox box = new VBox(6);
        box.setPadding(new Insets(0, 0, 4, 20));
        shot(services.resourcesDir(), constant).ifPresent(box.getChildren()::add);
        graph.backOf(constant).filter(back -> back.via() != null)
                .ifPresent(back -> box.getChildren().add(new Label("Way back: " + back.label())));
        for (ScreenVariant look : screen.variants()) {
            String when = ScreenTypes.sourceOf(look.when());
            HBox line = new HBox(8, new Label("Look " + look.name() + (when.isEmpty() ? "" : ", while " + when)));
            line.setAlignment(Pos.CENTER_LEFT);
            thumb(services.resourcesDir(), look.hasMarker() ? look.firstMarker().id() : null)
                    .ifPresent(line.getChildren()::addFirst);
            box.getChildren().add(line);
        }
        for (Row row : ScreenCards.rows(graph, constant)) {
            Label leads = new Label(row.result());
            Node picture = LookToggle.of(row.looks(), id -> thumb(services.resourcesDir(), id)
                    .orElseGet(Region::new), leads, row.result());
            String about = (row.kind().isEmpty() ? "" : " (" + row.kind() + ")")
                    + (row.traits().isEmpty() ? "" : " {" + row.traits() + "}")
                    + (row.look().isEmpty() ? "" : " [look " + row.look() + "]")
                    + (row.when().isEmpty() ? "" : " while " + row.when());
            HBox line = new HBox(8, picture, new Label(row.label() + about + ":"), leads);
            line.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().add(line);
        }
        for (String what : screen.owes()) box.getChildren().add(new Label("Owes: " + what));
        if (screen.notes() != null) {
            Label notes = new Label(screen.notes());
            notes.setWrapText(true);
            notes.setMaxWidth(720);
            box.getChildren().add(notes);
        }
        return box;
    }

    /**
     * The screens opened this session, by project and constant: kept across the redraws an edit makes, gone when
     * Studio closes. Shut is the default, so a new screen draws as a line.
     */
    private static final Set<String> OPEN = ConcurrentHashMap.newKeySet();

    static String key(Path project, String constant) {
        return (project == null ? "" : project.toAbsolutePath().normalize()) + "#" + constant;
    }

    static boolean isOpen(String key) {
        return OPEN.contains(key);
    }

    static void setOpen(String key, boolean open) {
        if (open) OPEN.add(key);
        else OPEN.remove(key);
    }

    private static Optional<Node> thumb(Path resources, String picture) {
        if (resources == null || picture == null) return Optional.empty();
        Path file = TemplateLibrary.fileForName(resources, picture);
        if (!Files.isRegularFile(file)) return Optional.empty();
        // In the background, as the shot: a file of fifty screens draws at once, each picture as it arrives.
        ImageView view = new ImageView(new Image(file.toUri().toString(), THUMB, THUMB, true, true, true));
        return Optional.of(view);
    }

    private static Optional<Node> shot(Path resources, String constant) {
        if (resources == null) return Optional.empty();
        Path file = ScreenShots.file(resources, constant);
        if (!Files.isRegularFile(file)) return Optional.empty();
        ImageView view = new ImageView(new Image(file.toUri().toString(), SHOT * 2, 0, true, true, true));
        view.setFitWidth(SHOT);
        view.setPreserveRatio(true);
        return Optional.of(view);
    }

    /** The map, one read for every card the tab draws in one go ({@link ScreenMapRead#orEmpty}). */
    private static ScreenGraph read(ValueContext ctx) {
        return ScreenMapRead.orEmpty(ctx.services() == null ? null : ctx.services().pluginValues());
    }

    /** The constant of {@code graph} whose setup is {@code held}; empty for none, and when the map could not be read. */
    static Optional<String> constantOf(ScreenGraph graph, Optional<Setup> held) {
        return held.flatMap(setup -> graph.screens().entrySet().stream()
                .filter(e -> e.getValue().setup().equals(setup)).map(Map.Entry::getKey).findFirst());
    }
}
