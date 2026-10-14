package com.botmaker.sdk.plugin.pictures;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the template gallery's tag rail shows, and which templates survive its search box — the parts of the
 * gallery that are a decision rather than a widget, kept free of JavaFX so they can be tested headlessly.
 *
 * <p>The rail is not simply "the keys of {@link TemplateLibrary#listByTag}": those keys are a flat
 * sequence, and the two kinds of tag need to read as two groups with a heading each — the same split the
 * picklist and the tag manager show, so a tag sits in the same place wherever it is met. Counts belong here
 * too, because a count is what makes an empty declared tag legible as "declared, nothing filed yet" rather
 * than as a bug.
 */
public final class TemplateGalleryModel {

    private TemplateGalleryModel() {}

    /** A rail entry: either a group heading or a selectable tag. */
    public sealed interface Row permits Heading, TagRow {}

    /** A non-selectable group label ("Activities", "Custom"). */
    public record Heading(String text) implements Row {}

    /**
     * A selectable bucket. {@code tag} is the key into {@link TemplateLibrary#listByTag}, including the
     * computed {@link TemplateManifest#ALL} and {@link TemplateManifest#UNTAGGED} — from the rail's point of
     * view those are rows like any other, which is the point of giving them names no real tag may take.
     */
    public record TagRow(String tag, int count, boolean managed) implements Row {}

    /**
     * The rail for {@code byTag} (as {@link TemplateLibrary#listByTag} returns it) over {@code catalog}:
     * All, then the activity tags under a heading, then the custom ones under theirs, then Untagged when
     * anything is untagged. A declared tag with nothing in it is still a row — it is somewhere to file to.
     */
    public static List<Row> rows(Map<String, List<Path>> byTag, TagCatalog catalog) {
        return rows(byTag, catalog, List.of());
    }

    /**
     * The same, with each screen of the bot's map under a heading of its own after the activities: {@code screens}
     * are their keys in {@code byTag} ({@link #screenKey}), read-only rows like an activity's.
     */
    public static List<Row> rows(Map<String, List<Path>> byTag, TagCatalog catalog, List<String> screens) {
        List<Row> rows = new ArrayList<>();
        rows.add(new TagRow(TemplateManifest.ALL, count(byTag, TemplateManifest.ALL), false));
        addGroup(rows, "Activities", catalog.namesOf(TagCatalog.Kind.ACTIVITY), byTag, true);
        addGroup(rows, "Screens", screens, byTag, true);
        addGroup(rows, "Custom", catalog.namesOf(TagCatalog.Kind.CUSTOM), byTag, false);
        if (byTag.containsKey(TemplateManifest.UNTAGGED)) {
            rows.add(new TagRow(TemplateManifest.UNTAGGED, count(byTag, TemplateManifest.UNTAGGED), false));
        }
        return List.copyOf(rows);
    }

    /** What starts a screen's row, so a custom tag of the same name (an older bot's screen tag) is another row. */
    private static final String SCREEN_MARK = "🗺 ";

    /** The rail key of the screen the game calls {@code name}: {@code 🗺 Campaign}. */
    public static String screenKey(String name) {
        return SCREEN_MARK + name;
    }

    /** Whether {@code key} is a screen's row, which no picture is filed under by hand. */
    public static boolean isScreenKey(String key) {
        return key != null && key.startsWith(SCREEN_MARK);
    }

    private static void addGroup(List<Row> rows, String heading, List<String> tags,
                                 Map<String, List<Path>> byTag, boolean managed) {
        if (tags.isEmpty()) return;  // no heading over nothing
        rows.add(new Heading(heading));
        for (String tag : tags) rows.add(new TagRow(tag, count(byTag, tag), managed));
    }

    private static int count(Map<String, List<Path>> byTag, String tag) {
        List<Path> files = byTag.get(tag);
        return files == null ? 0 : files.size();
    }

    /**
     * The templates in {@code files} whose name contains {@code query}, case-insensitively; a blank query
     * keeps everything. Matching the <em>name</em> rather than the path on purpose — the path is an
     * implementation detail the user never typed, and every template shares most of it.
     */
    public static List<Path> matching(List<Path> files, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.copyOf(files);
        return files.stream()
                .filter(f -> TemplateLibrary.baseName(f).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    /** The order the grid shows its pictures in. */
    public enum Sort {
        NAME_UP("Name A–Z"),
        NAME_DOWN("Name Z–A"),
        NEWEST("Newest cut"),
        OLDEST("Oldest cut"),
        LARGEST("Largest"),
        SMALLEST("Smallest");

        private final String displayName;

        Sort(String displayName) {
            this.displayName = displayName;
        }

        /** The key it is remembered by between sessions. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The sort remembered as {@code id}; Name A–Z for none or one this version does not have. */
        public static Sort fromId(String id) {
            for (Sort sort : values()) if (sort.id().equals(id)) return sort;
            return NAME_UP;
        }

        public String displayName() {
            return displayName;
        }

        @Override public String toString() {
            return displayName;
        }
    }

    /** Which pictures of the rail's tags the grid shows. */
    public enum Show {
        ALL("All pictures"),
        UNUSED("Unused"),
        TWINS("Duplicates and near twins");

        private final String displayName;

        Show(String displayName) {
            this.displayName = displayName;
        }

        /** The key it is remembered by between sessions. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The filter remembered as {@code id}; All pictures for none or one this version does not have. */
        public static Show fromId(String id) {
            for (Show show : values()) if (show.id().equals(id)) return show;
            return ALL;
        }

        public String displayName() {
            return displayName;
        }

        @Override public String toString() {
            return displayName;
        }
    }

    /** What a sort reads off a picture: when it was last written, and its pixel count. */
    public record Facts(long modified, long area) {}

    /**
     * {@code files} in {@code sort}'s order, ties by name. A file {@code facts} does not know sorts as the
     * oldest and smallest.
     */
    public static List<Path> sorted(List<Path> files, Sort sort, Map<Path, Facts> facts) {
        Comparator<Path> byName = Comparator.comparing(f -> TemplateLibrary.baseName(f).toLowerCase(Locale.ROOT));
        Facts none = new Facts(0, 0);
        Comparator<Path> order = switch (sort) {
            case NAME_UP -> byName;
            case NAME_DOWN -> byName.reversed();
            case NEWEST -> Comparator.<Path>comparingLong(f -> facts.getOrDefault(f, none).modified()).reversed()
                    .thenComparing(byName);
            case OLDEST -> Comparator.<Path>comparingLong(f -> facts.getOrDefault(f, none).modified())
                    .thenComparing(byName);
            case LARGEST -> Comparator.<Path>comparingLong(f -> facts.getOrDefault(f, none).area()).reversed()
                    .thenComparing(byName);
            case SMALLEST -> Comparator.<Path>comparingLong(f -> facts.getOrDefault(f, none).area())
                    .thenComparing(byName);
        };
        return files.stream().sorted(order).toList();
    }

    /**
     * The pictures under {@code tags}: those carrying any of them, or with {@code every} those carrying all of
     * them, in {@link TemplateManifest#ALL}'s order. One tag is that tag's list.
     */
    public static List<Path> underTags(Map<String, List<Path>> byTag, List<String> tags, boolean every) {
        if (tags.size() == 1) return byTag.getOrDefault(tags.getFirst(), List.of());
        List<Set<Path>> sets = tags.stream().map(tag -> Set.copyOf(byTag.getOrDefault(tag, List.of()))).toList();
        return byTag.getOrDefault(TemplateManifest.ALL, List.of()).stream()
                .filter(file -> every ? sets.stream().allMatch(s -> s.contains(file))
                        : sets.stream().anyMatch(s -> s.contains(file)))
                .toList();
    }

    /**
     * The twins among {@code files}, one group after another: a group's place is its first picture's in
     * {@code files}, and its pictures keep {@code files}' order. A picture whose twin is outside {@code files} is
     * still shown, so a twin under another tag is not hidden.
     */
    public static List<Path> twinsFirst(List<Path> files, List<List<String>> groups) {
        Map<String, Integer> groupOf = new HashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            for (String name : groups.get(i)) groupOf.put(name, i);
        }
        Map<Integer, List<Path>> byGroup = new LinkedHashMap<>();
        for (Path file : files) {
            Integer group = groupOf.get(TemplateLibrary.baseName(file));
            if (group != null) byGroup.computeIfAbsent(group, g -> new ArrayList<>()).add(file);
        }
        return byGroup.values().stream().flatMap(List::stream).toList();
    }
}
