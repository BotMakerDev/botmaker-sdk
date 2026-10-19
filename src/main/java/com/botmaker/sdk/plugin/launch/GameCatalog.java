package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.config.CacheDirs;
import com.botmaker.shared.emulator.AdbTools;
import com.botmaker.shared.emulator.EmulatorAppCache;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorLiveness;
import com.botmaker.shared.emulator.EmulatorProbe;
import com.botmaker.shared.emulator.PhoneState;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.game.DesktopEntryScanner;
import com.botmaker.shared.game.EpicLibraryScanner;
import com.botmaker.shared.game.GameLibraries;
import com.botmaker.shared.game.GameLibraryProvider;
import com.botmaker.shared.game.HeroicLibraryScanner;
import com.botmaker.shared.game.InstalledGame;
import com.botmaker.shared.launch.DesktopEntries;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.platform.Os;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What {@link GameDialog} lists, as data: one section per launcher on this computer, sorted by name, one per
 * emulator instance whose apps were seen, then the Android apps Waydroid put in the menu, the menu's own games,
 * and every other app last and folded.
 *
 * <p>Separate from the dialog so the sectioning is testable without JavaFX, and because building it reads
 * libraries off disk (and runs {@code lutris} once): {@link #scan()} is called off the JavaFX thread.
 *
 * <p>A menu entry that is a launcher's shortcut for one of its own games is listed under that launcher, not
 * twice; an Epic game Heroic also lists is listed once, under Epic.
 */
public final class GameCatalog {

    /** The targets the last {@link #scan} listed, or {@code null} before one ran. */
    private static volatile Set<String> listed;
    /** Held to set {@link #listed}, so the saved list never replaces a scan's. */
    private static final Object LISTED = new Object();
    private static final AtomicBoolean SCANNING = new AtomicBoolean();

    private GameCatalog() {}

    /** What a section holds, which decides where it goes and what the dialog adds to it. */
    public enum Kind {
        /** One launcher's library; titled after the launcher. */
        LAUNCHER(""),
        /** One emulator instance's apps, as last seen on it; titled after the instance. */
        EMULATOR(""),
        /**
         * The Android apps Waydroid put in the menu; the dialog adds its emulator picker here. Off Linux there is
         * no Waydroid, and the section is only the picker's way to another emulator or a phone.
         */
        ANDROID(Os.current() == Os.LINUX ? "Android apps (Waydroid)" : "Other emulator or phone"),
        /** The menu's games that no launcher lists. */
        MENU_GAMES("Games in the app menu"),
        /** Every other app in the menu; folded, so not in the dialog's All. */
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

    /**
     * One section; a {@code folded} one has its own radio in the dialog but is left out of All. A {@code stopped}
     * one is an emulator instance that isn't running: picking one of its apps starts it first. A {@code note} is
     * shown in place of tiles: a phone whose apps can't be read yet, and why; {@code null} for every other.
     */
    public record Section(Kind kind, String title, boolean folded, boolean stopped, List<Item> items, String note) {

        public Section(Kind kind, String title, boolean folded, boolean stopped, List<Item> items) {
            this(kind, title, folded, stopped, items, null);
        }

        public Section(Kind kind, String title, boolean folded, List<Item> items) {
            this(kind, title, folded, false, items);
        }
    }

    /**
     * Every section with something in it, in display order — the launchers, the emulator instances, the app
     * menu — and the Android section always: its emulator picker reaches emulators and phones no library lists.
     * Blocking; never throws.
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
        sections.addAll(emulatorSections(emulators(), EmulatorAppCache.shared(), EmulatorLiveness::running,
                PhoneState::of));
        sections.addAll(menuSections(menu()));
        List<Section> shown = sections.stream()
                .filter(s -> !s.items().isEmpty() || s.note() != null || s.kind() == Kind.ANDROID).toList();
        // Every library whole, Heroic's copies of Epic games too: a bot may list a game under either launcher.
        List<Section> everything = new ArrayList<>(shown);
        for (int i = 0; i < providers.size(); i++) {
            everything.add(new Section(Kind.LAUNCHER, "", false, items(providers.get(i), libraries.get(i))));
        }
        Set<String> found = listedIn(everything);
        synchronized (LISTED) {
            listed = found;
        }
        save(savedFile(), shown, found);
        return shown;
    }

    /**
     * What the last {@link #scan} listed, on this computer, kept on disk: what the dialog shows as it opens, before a
     * scan answers — one runs {@code lutris}, and the dialog sat empty for seconds on each open (2026-10-19). Empty
     * when none was saved, or the file is unreadable or of another version. Blocking (one small file); never throws.
     */
    public static Optional<List<Section>> saved() {
        return load(savedFile()).map(snapshot -> {
            // Never over a scan's answer that landed while the file was read.
            synchronized (LISTED) {
                if (listed == null) listed = snapshot.listed();
            }
            return snapshot.shown();
        });
    }

    /** What {@link #save} keeps: the sections shown and every target listed, Heroic's copies of Epic games too. */
    record Snapshot(List<Section> shown, Set<String> listed) {}

    /** Bumped when the file's shape changes: an older file is then read as none. */
    static final int SAVED_VERSION = 1;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The file's shape: a picture as its path's text, a kind as its name. */
    record SavedItem(String spec, String name, String artwork) {}

    record SavedSection(String kind, String title, boolean folded, boolean stopped, List<SavedItem> items,
                        String note) {}

    record SavedScan(int version, List<SavedSection> sections, List<String> listed) {}

    private static Path savedFile() {
        return CacheDirs.cacheRoot().resolve("games.json");
    }

    /**
     * Writes {@code shown} and {@code listed} to {@code file}, whole or not at all; one writer at a time, since the
     * Runner's scan and the dialog's may finish together. Never throws.
     */
    static synchronized void save(Path file, List<Section> shown, Set<String> listed) {
        List<SavedSection> sections = shown.stream().map(s -> new SavedSection(s.kind().name(), s.title(), s.folded(),
                s.stopped(), s.items().stream().map(i -> new SavedItem(i.spec(), i.name(),
                        i.artwork() == null ? null : i.artwork().toString())).toList(), s.note())).toList();
        Path part = null;
        try {
            Files.createDirectories(file.getParent());
            part = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".part");
            JSON.writeValue(part.toFile(), new SavedScan(SAVED_VERSION, sections, List.copyOf(listed)));
            try {
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notHere) {
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            // A list not saved is scanned again on the next open.
            if (part != null) part.toFile().delete();
        }
    }

    /** What {@link #save} wrote to {@code file}; empty when there is none or it doesn't read. Never throws. */
    static Optional<Snapshot> load(Path file) {
        try {
            if (!Files.isRegularFile(file)) return Optional.empty();
            SavedScan scan = JSON.readValue(file.toFile(), SavedScan.class);
            if (scan.version() != SAVED_VERSION || scan.sections() == null) return Optional.empty();
            List<Section> shown = new ArrayList<>();
            for (SavedSection s : scan.sections()) {
                List<Item> items = s.items() == null ? List.of() : s.items().stream().map(i -> new Item(i.spec(),
                        i.name(), i.artwork() == null ? null : Path.of(i.artwork()))).toList();
                shown.add(new Section(Kind.valueOf(s.kind()), s.title(), s.folded(), s.stopped(), items, s.note()));
            }
            return Optional.of(new Snapshot(List.copyOf(shown),
                    scan.listed() == null ? Set.of() : Set.copyOf(scan.listed())));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * The targets among {@code shown} whose tile a new scan drops or changes (gone, or another name or picture):
     * the dialog keeps every other tile as it is, so a rescan that found nothing new moves nothing. Pure.
     */
    static Set<String> outdated(Map<String, Item> shown, List<Section> scanned) {
        Map<String, Item> now = new HashMap<>();
        for (Section section : scanned) {
            for (Item item : section.items()) now.putIfAbsent(item.spec(), item);
        }
        Set<String> out = new HashSet<>();
        shown.forEach((spec, item) -> {
            if (!item.equals(now.get(spec))) out.add(spec);
        });
        return out;
    }

    /**
     * Whether this computer has {@code spec}'s game, as the last {@link #scan} found it; empty before one ran. A
     * program's path is looked for on disk and a command line is taken as there, since no library lists either.
     */
    public static Optional<Boolean> installed(String spec) {
        return installed(spec, listed);
    }

    /**
     * Starts one {@link #scan} on a thread of its own when none ran or runs, so {@link #installed(String)} can answer
     * for a launcher's game: every Runner row asks, and a scan runs {@code lutris}. The saved list answers first.
     */
    public static void scanOnce() {
        if (listed != null || !SCANNING.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("game-scan").start(() -> {
            try {
                saved();
                scan();
            } finally {
                SCANNING.set(false);
            }
        });
    }

    /** {@link #installed(String)} against {@code listed}, the targets a scan listed ({@code null}: none ran). Pure but for a path. */
    static Optional<Boolean> installed(String spec, Set<String> listed) {
        LaunchSpec parsed = LaunchSpec.parse(spec);
        if (parsed == null) return Optional.of(false);
        if (parsed.kind() == LaunchKind.CLI) return Optional.of(true);
        if (parsed.kind() == LaunchKind.EXE) {
            try {
                return Optional.of(Files.isRegularFile(Path.of(parsed.token())));
            } catch (InvalidPathException notAPath) {
                return Optional.of(false);
            }
        }
        return listed == null ? Optional.empty() : Optional.of(listed.contains(parsed.spec()));
    }

    /** Every target {@code sections} list, as {@link LaunchSpec#spec()} writes it. */
    static Set<String> listedIn(List<Section> sections) {
        Set<String> out = new HashSet<>();
        for (Section section : sections) {
            for (Item item : section.items()) {
                LaunchSpec parsed = LaunchSpec.parse(item.spec());
                if (parsed != null) out.add(parsed.spec());
            }
        }
        return Set.copyOf(out);
    }

    /**
     * One section per emulator instance, holding the apps last seen on it ({@link EmulatorAppCache}): under the
     * names the apps show, with their icons, whether the instance runs or not. Waydroid's are left out, since
     * the app menu already lists them ({@link Kind#ANDROID}). A phone with no apps seen yet is a section too, its
     * note saying what it waits on ({@code phone}): a phone plugged in for the first time listed nothing at all
     * (2026-10-19). Pure but for the cache, for the tests.
     */
    static List<Section> emulatorSections(List<EmulatorInstance> instances, EmulatorAppCache cache,
                                          Predicate<EmulatorInstance> running,
                                          Function<EmulatorInstance, PhoneState> phone) {
        List<Section> sections = new ArrayList<>();
        for (EmulatorInstance instance : instances) {
            if (instance.platformId() == PlatformId.WAYDROID) continue;
            List<Item> items = new ArrayList<>();
            for (EmulatorProbe.InstalledApp app : cache.packages(instance)) {
                String spec = new LaunchSpec(LaunchKind.EMULATOR_APP, app.packageName() + "@" + instance.reference()).spec();
                items.add(new Item(spec, app.display(), cache.iconPath(instance, app.packageName())));
            }
            String note = null;
            if (items.isEmpty()) {
                if (instance.platformId() != PlatformId.PHYSICAL) continue;
                PhoneState state = phone.apply(instance);
                note = state.online() ? "Its apps are read as this dialog opens: reopen it if none appear."
                        : state.advice();
            }
            sections.add(new Section(Kind.EMULATOR, instance.caption(), false, !running.test(instance),
                    List.copyOf(items), note));
        }
        return sections;
    }

    /**
     * Asks every running emulator instance for its apps, their names and icons, and keeps them for the next
     * {@link #scan()}: what makes a just-started instance's apps appear, and a new install's. Whether anything
     * changed. Blocking (an app's name and icon are read once, out of its APK); never throws.
     */
    public static boolean refreshEmulators() {
        // One at a time: a dialog closed and reopened mid-refresh would otherwise read every new APK twice.
        if (!REFRESHING.tryLock()) return false;
        try {
            EmulatorAppCache cache = EmulatorAppCache.shared();
            // A phone on a cable is listed only by an adb server: one started now lists it, so discovery runs again.
            boolean changed = AdbTools.wake();
            for (EmulatorInstance instance : changed ? Platforms.discoverAll() : emulators()) {
                if (instance.platformId() == PlatformId.WAYDROID || !EmulatorLiveness.running(instance)) continue;
                changed |= EmulatorProbe.refresh(instance, cache);
            }
            return changed;
        } finally {
            REFRESHING.unlock();
        }
    }

    private static final ReentrantLock REFRESHING = new ReentrantLock();

    /**
     * {@code scanned} with its emulator sections read again from the cache — what {@link #refreshEmulators()}
     * changes, without looking through every launcher's library again. The new sections take the old ones' place.
     * Blocking; never throws.
     */
    public static List<Section> withEmulatorsReread(List<Section> scanned) {
        return replaceEmulators(scanned,
                emulatorSections(emulators(), EmulatorAppCache.shared(), EmulatorLiveness::running, PhoneState::of));
    }

    /** {@code scanned} with {@code emulators} in place of its emulator sections, after the launchers. Pure. */
    static List<Section> replaceEmulators(List<Section> scanned, List<Section> emulators) {
        List<Section> sections = new ArrayList<>();
        boolean placed = false;
        for (Section section : scanned) {
            if (section.kind() == Kind.EMULATOR || (!placed && section.kind() != Kind.LAUNCHER)) {
                if (!placed) sections.addAll(emulators);
                placed = true;
                if (section.kind() == Kind.EMULATOR) continue;
            }
            sections.add(section);
        }
        if (!placed) sections.addAll(emulators);
        return List.copyOf(sections);
    }

    /** The instances discovery found in the last few seconds — a picker or a refresh may have just asked. */
    private static List<EmulatorInstance> emulators() {
        try {
            return Platforms.recent(Duration.ofSeconds(10));
        } catch (RuntimeException e) {
            return List.of();
        }
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
