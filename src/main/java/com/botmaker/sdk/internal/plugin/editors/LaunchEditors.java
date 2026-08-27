package com.botmaker.sdk.internal.plugin.editors;

import com.botmaker.plugin.api.ValueContext;
import com.botmaker.plugin.toolkit.Fields;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Pills;
import com.botmaker.plugin.toolkit.Thumbnail;
import com.botmaker.shared.game.GameLibraryProvider;
import com.botmaker.shared.game.InstalledGame;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The editors for the three arguments of a launch call that are all, as far as the compiler is concerned, the
 * same {@code String}: which game, which program, and what to pass it on the command line.
 *
 * <p><b>These are the package's exception to matching on the type</b> (see {@link SdkEditors}). Nothing about
 * {@code String} says whether it holds a Steam app id, a path to an executable or {@code --fullscreen}; only
 * the call around it does, which is what {@link CallSites} reads and why these editors are absent from the
 * Parameters window rather than misfiring in it.
 *
 * <p>The libraries are read through {@code botmaker-shared}, which is published — so a third-party plugin can
 * offer exactly this, and none of it required asking the host for anything. That is the host-only rule doing
 * its job: the host was never the source of a game, it was only the thing that happened to be written first.
 */
final class LaunchEditors {

    /** How tall the cover thumbnail on the closed pill is; portrait art keeps its own ratio inside it. */
    private static final double PILL_ART_HEIGHT = 28;

    private LaunchEditors() {}

    /**
     * The launch id of a store launch call — a grid of installed cover art, or the id typed by hand.
     *
     * <p>The pill resolves the id it already holds in the background, so a slot saying {@code "620"} reads
     * <i>Portal 2</i> rather than <i>Steam game: 620</i> once the library has been walked. It is the id that
     * is written either way: the name is what a person recognises, the id is what the bot launches.
     *
     * @param provider a fresh provider per scan — they are stateless, best-effort readers of a local library,
     *                 and its {@code displayName()} is what every label in the editor is worded around
     */
    static Node game(ValueContext ctx, Supplier<GameLibraryProvider> provider) {
        String launcher = provider.get().displayName();
        String id = Slots.stringLiteral(Slots.raw(ctx));

        Node[] pill = new Node[1];
        pill[0] = Pills.button(idLabel(launcher, id), () -> Modals.gallery(ctx,
                Modals.Gallery.covers("Choose a " + launcher + " game",
                        "No installed " + launcher + " games found. Enter an id below.",
                        "…or enter a " + launcher + " id manually"),
                () -> covers(provider.get()),
                chosen -> {
                    if (chosen.value().isBlank()) return;
                    Slots.writeText(ctx, chosen.value());
                    relabel(pill[0], chosen.label());
                }));
        resolveName(pill[0], launcher, id, provider);
        return pill[0];
    }

    /** Every installed game as one cell — its name, and its cover art when the launcher cached any. */
    private static List<Thumbnail> covers(GameLibraryProvider provider) {
        List<Thumbnail> out = new ArrayList<>();
        for (InstalledGame game : provider.installedGames()) {
            out.add(new Thumbnail(game.id(), game.name(), artwork(game)));
        }
        return out;
    }

    /**
     * Names the game the slot already holds, off the JavaFX thread.
     *
     * <p>Walking a library takes long enough to be visible, and this runs while a block is being drawn — so
     * the pill shows the id immediately and improves itself when the answer arrives. An id that is not
     * installed here simply keeps its id, which is the honest label for it.
     */
    private static void resolveName(Node pill, String launcher, String id,
                                    Supplier<GameLibraryProvider> provider) {
        if (id == null || id.isBlank()) return;
        Thread worker = new Thread(() -> provider.get().findById(id).ifPresent(game -> Platform.runLater(() -> {
            boolean named = game.name() != null && !game.name().isBlank() && !game.name().equals(game.id());
            relabel(pill, named ? game.name() : idLabel(launcher, game.id()));
            Image art = artwork(game);
            if (art != null && pill instanceof javafx.scene.control.Labeled labeled) {
                javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(art);
                view.setPreserveRatio(true);
                view.setFitHeight(PILL_ART_HEIGHT);
                labeled.setGraphic(view);
            }
        })), "sdk-game-resolve");
        worker.setDaemon(true);
        worker.start();
    }

    /** Cover art as an image, or null — a game whose launcher cached none is an ordinary case. */
    private static Image artwork(InstalledGame game) {
        Path art = game.artwork();
        return art == null ? null : new Image(art.toUri().toString(), 0, PILL_ART_HEIGHT, true, true, true);
    }

    private static void relabel(Node pill, String text) {
        if (pill instanceof javafx.scene.control.Labeled labeled) labeled.setText(text);
    }

    private static String idLabel(String launcher, String id) {
        return id == null || id.isBlank() ? "Choose " + launcher + " game…" : launcher + " game: " + id;
    }

    /**
     * The program a launch call runs — the OS file chooser, or a typed path.
     *
     * <p>Typed matters as much as browsed: a launch target is frequently a command that is not a file on this
     * machine at all, and a chooser alone would make those unsayable. Browsing goes through
     * {@link Modals#program}, which is where the "a native dialog blocks its thread" trap is answered once for
     * every plugin rather than once per editor.
     *
     * <p>The label is the file's own name and not the whole path: a slot on a block is a few centimetres wide,
     * and {@code C:\Program Files (x86)\…\game.exe} elided in the middle says less than {@code game.exe}.
     */
    static Node program(ValueContext ctx) {
        String current = Slots.stringLiteral(Slots.raw(ctx));
        javafx.scene.control.MenuButton pill = Pills.bare(fileLabel(current));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Browse for program…", () -> Modals.program(ctx, parentOf(
                        Slots.stringLiteral(Slots.raw(ctx))), path -> {
                    Slots.writeText(ctx, path.toString());
                    pill.setText(fileLabel(path.toString()));
                })),
                Pills.separator(),
                Pills.item("Enter path…", () -> {
                    String now = Slots.stringLiteral(Slots.raw(ctx));
                    TextField field = Fields.committing(now == null ? "" : now, "Path or command", null);
                    field.setPrefColumnCount(40);
                    Modals.form(ctx, "Program path", field, () -> {
                        String typed = field.getText() == null ? "" : field.getText().trim();
                        if (typed.isEmpty()) return;
                        Slots.writeText(ctx, typed);
                        pill.setText(fileLabel(typed));
                    });
                })));
        return pill;
    }

    /** The folder a path sits in, for the chooser to open on; null when there is no usable path yet. */
    private static Path parentOf(String path) {
        try {
            return path == null || path.isBlank() ? null : Path.of(path).getParent();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String fileLabel(String path) {
        if (path == null || path.isBlank()) return "Choose program…";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    /**
     * A command-line flag passed to the launched program.
     *
     * <p>A plain field, and deliberately so — a flag is whatever that program accepts, and nothing here could
     * offer a list of them. What the prompt does is stop it reading as a second program to run, which is the
     * only thing about this slot a person gets wrong.
     */
    static Node option(ValueContext ctx) {
        String current = Slots.stringLiteral(Slots.raw(ctx));
        TextField field = Fields.committing(current == null ? "" : current,
                "launch option (e.g. --fullscreen)", typed -> Slots.writeText(ctx, typed));
        field.setPrefColumnCount(14);
        return field;
    }
}
