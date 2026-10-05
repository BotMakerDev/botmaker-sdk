package com.botmaker.sdk.plugin.launch;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Thumbnail;
import com.botmaker.sdk.plugin.settings.LaunchTargetValue;
import com.botmaker.shared.game.GameLibraries;
import com.botmaker.shared.game.GameLibraryProvider;
import com.botmaker.shared.game.InstalledGame;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * <b>Choose…</b> on Project Setup's launch row: the games the launchers on this computer already have — Steam,
 * Epic, Heroic and Faugus — in one searchable grid, and a pick sets this machine's {@code botmaker.launch.target}.
 *
 * <p>It builds nothing. The launcher already knows how to start its game (Proton version, prefix, arguments),
 * so a target is only which launcher and which of its entries, {@code faugus:battlenet}; the maintainer decided
 * that BotMaker grows no launch UI of its own beside the launchers. What this adds over a Game block
 * is that a run knows the target, so background mode can start a PC game inside its own display.
 */
public final class LaunchTargetChooser {

    private LaunchTargetChooser() {}

    /** How tall a cover is loaded; the grid fits it into its own frame. */
    private static final double COVER_HEIGHT = 160;

    /**
     * Opens the grid; a pick is written as this machine's launch target and reported to {@code onSet} as its
     * description, a typed target that does not parse to {@code onRefused} as the reason.
     */
    public static void choose(StudioServices services, Consumer<String> onSet, Consumer<String> onRefused) {
        Modals.gallery(services,
                Modals.Gallery.covers("Choose what this computer launches",
                        "No launcher on this computer lists a game: Steam, Epic, Heroic and Faugus were looked "
                                + "for. Type a target below instead.",
                        "…or type a target, e.g. faugus:battlenet, steam:620, exe:/path/to/game"),
                LaunchTargetChooser::targets,
                chosen -> typed(chosen.value()).ifPresentOrElse(spec -> {
                    LaunchTargetValue.set(services, spec);
                    onSet.accept(LaunchSpec.describe(spec));
                }, () -> onRefused.accept("\"" + chosen.value() + "\" is not a launch target: write it as "
                        + "kind:id, where kind is one of " + kinds() + ".")));
    }

    /** Every game every launcher lists, labelled with its launcher. Off the JavaFX thread: it walks libraries. */
    private static List<Thumbnail> targets() {
        List<Thumbnail> out = new ArrayList<>();
        for (GameLibraryProvider provider : GameLibraries.all()) {
            for (InstalledGame game : provider.installedGames()) {
                specOf(game).ifPresent(spec -> out.add(new Thumbnail(spec,
                        game.name() + " — " + provider.displayName(), cover(game))));
            }
        }
        return out;
    }

    /** The launch target a library entry is: its launcher's kind and its id. Empty for a launcher with no kind. */
    static Optional<String> specOf(InstalledGame game) {
        if (game == null || game.id() == null || game.id().isBlank()) return Optional.empty();
        LaunchKind kind = LaunchKind.fromId(game.platform());
        if (kind == LaunchKind.UNKNOWN) return Optional.empty();
        return Optional.of(new LaunchSpec(kind, game.id().trim()).spec());
    }

    /**
     * A typed or picked target as the spec to store, or empty when it is not one a run can launch. A grid pick
     * is already a spec, so both arrive here.
     */
    static Optional<String> typed(String text) {
        LaunchSpec spec = LaunchSpec.parse(text);
        if (spec == null || spec.kind() == LaunchKind.UNKNOWN) return Optional.empty();
        return Optional.of(spec.spec());
    }

    private static String kinds() {
        List<String> ids = new ArrayList<>();
        for (LaunchKind kind : LaunchKind.values()) if (kind != LaunchKind.UNKNOWN) ids.add(kind.id());
        return String.join(", ", ids);
    }

    private static Image cover(InstalledGame game) {
        return game.artwork() == null ? null
                : new Image(game.artwork().toUri().toString(), 0, COVER_HEIGHT, true, true, true);
    }
}
