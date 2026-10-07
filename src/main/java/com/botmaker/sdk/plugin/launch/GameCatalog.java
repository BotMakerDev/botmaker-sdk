package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.game.DesktopEntryScanner;
import com.botmaker.shared.game.EpicLibraryScanner;
import com.botmaker.shared.game.GameLibraries;
import com.botmaker.shared.game.GameLibraryProvider;
import com.botmaker.shared.game.HeroicLibraryScanner;
import com.botmaker.shared.game.InstalledGame;
import com.botmaker.shared.launch.DesktopEntries;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What {@link GameDialog} lists, as data: one section per launcher on this computer, sorted by name, then the
 * Android apps Waydroid put in the menu, the menu's own games, and every other app last and folded.
 *
 * <p>Separate from the dialog so the sectioning is testable without JavaFX, and because building it reads
 * libraries off disk (and runs {@code lutris} once): {@link #scan()} is called off the JavaFX thread.
 *
 * <p>A menu entry that is a launcher's shortcut for one of its own games is listed under that launcher, not
 * twice; an Epic game Heroic also lists is listed once, under Epic.
 */
public final class GameCatalog {

    private GameCatalog() {}

    /** What a section holds, which decides where it goes and what the dialog adds to it. */
    public enum Kind {
        /** One launcher's library; titled after the launcher. */
        LAUNCHER(""),
        /** The Android apps Waydroid put in the menu; the dialog adds its emulator picker here. */
        ANDROID("Android apps (Waydroid)"),
        /** The menu's games that no launcher lists. */
        MENU_GAMES("Games in the app menu"),
        /** Every other app in the menu; starts folded. */
        OTHER_APPS("Other apps");

        private final String title;

        Kind(String title) {
            this.title = title;
        }

        /** The section's heading; a launcher's is its own name. */
        public String title() {
            return title;
        }
    }

    /** One tile: the target it sets, what it is called, and its picture or {@code null}. */
    public record Item(String spec, String name, Path artwork) {}

    /** One section; {@code folded} ones start collapsed. */
    public record Section(Kind kind, String title, boolean folded, List<Item> items) {}

    /**
     * Every section with something in it, in display order, and the Android section always: its emulator
     * picker reaches emulators and phones no library lists. Blocking; never throws.
     */
    public static List<Section> scan() {
        Set<String> epicIds = new HashSet<>();
        List<GameLibraryProvider> providers = GameLibraries.all().stream()
                .filter(p -> !p.platform().equals(DesktopEntryScanner.PLATFORM)).toList();
        List<List<InstalledGame>> libraries = providers.stream().map(GameCatalog::safely).toList();
        for (int i = 0; i < providers.size(); i++) {
            if (providers.get(i).platform().equals(EpicLibraryScanner.PLATFORM)) {
                libraries.get(i).forEach(g -> epicIds.add(g.id()));
            }
        }
        List<Section> launchers = new ArrayList<>();
        for (int i = 0; i < providers.size(); i++) {
            GameLibraryProvider provider = providers.get(i);
            List<InstalledGame> games = libraries.get(i);
            // An Epic game Heroic also lists is listed once, under Epic.
            if (provider.platform().equals(HeroicLibraryScanner.PLATFORM)) {
                games = games.stream().filter(g -> !epicIds.contains(g.id())).toList();
            }
            launchers.add(new Section(Kind.LAUNCHER, provider.displayName(), false, items(provider, games)));
        }
        launchers.sort(Comparator.comparing(s -> s.title().toLowerCase()));

        List<Section> sections = new ArrayList<>(launchers);
        sections.addAll(menuSections(menu()));
        return sections.stream().filter(s -> !s.items().isEmpty() || s.kind() == Kind.ANDROID).toList();
    }

    /** Whether no section lists anything — the dialog then says so, above the emulator picker. */
    public static boolean nothingListed(List<Section> sections) {
        return sections.stream().allMatch(s -> s.items().isEmpty());
    }

    /**
     * The menu's entries as sections: Waydroid's shortcuts as {@link #ANDROID}, games, then everything else
     * folded. Other launchers' shortcuts are dropped — their launcher's section has them. Pure, for the tests.
     */
    static List<Section> menuSections(List<DesktopEntries.Entry> entries) {
        List<Item> android = new ArrayList<>();
        List<Item> games = new ArrayList<>();
        List<Item> other = new ArrayList<>();
        for (DesktopEntries.Entry entry : entries) {
            Optional<LaunchSpec> shortcut = entry.shortcutFor();
            if (shortcut.isPresent()) {
                if (shortcut.get().kind() == LaunchKind.EMULATOR_APP) {
                    android.add(new Item(shortcut.get().spec(), entry.name(), DesktopEntries.icon(entry)));
                }
                continue;
            }
            Item item = new Item(entry.spec().spec(), entry.name(), DesktopEntries.icon(entry));
            (entry.game() ? games : other).add(item);
        }
        return List.of(new Section(Kind.ANDROID, Kind.ANDROID.title(), false, android),
                new Section(Kind.MENU_GAMES, Kind.MENU_GAMES.title(), false, games),
                new Section(Kind.OTHER_APPS, Kind.OTHER_APPS.title(), true, other));
    }

    /** The target a library entry is, or empty for an entry with no id or a launcher with no kind. */
    static Optional<String> specOf(GameLibraryProvider provider, InstalledGame game) {
        if (game == null || game.id() == null || game.id().isBlank()) return Optional.empty();
        LaunchKind kind = LaunchKind.fromId(provider.platform());
        if (kind == LaunchKind.UNKNOWN) return Optional.empty();
        return Optional.of(new LaunchSpec(kind, game.id().trim()).spec());
    }

    /**
     * A typed target as the spec to store, or empty when it is not one a run can launch.
     */
    static Optional<String> typed(String text) {
        LaunchSpec spec = LaunchSpec.parse(text);
        if (spec == null || spec.kind() == LaunchKind.UNKNOWN) return Optional.empty();
        return Optional.of(spec.spec());
    }

    /** The kinds a typed target may name, for the message that refuses one. */
    static String kinds() {
        List<String> ids = new ArrayList<>();
        for (LaunchKind kind : LaunchKind.values()) if (kind != LaunchKind.UNKNOWN) ids.add(kind.id());
        return String.join(", ", ids);
    }

    private static List<Item> items(GameLibraryProvider provider, List<InstalledGame> games) {
        List<Item> items = new ArrayList<>();
        for (InstalledGame game : games) {
            specOf(provider, game).ifPresent(spec -> items.add(new Item(spec, game.name(), game.artwork())));
        }
        return List.copyOf(items);
    }

    private static List<InstalledGame> safely(GameLibraryProvider provider) {
        try {
            return provider.installedGames();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static List<DesktopEntries.Entry> menu() {
        try {
            return DesktopEntries.list();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
