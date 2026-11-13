package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.botmaker.shared.emulator.config.Sourced;
import com.botmaker.shared.emulator.config.Wanted;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a configuration view and the Configurations tab show and change, with no JavaFX: the rows of one layer's view,
 * the grid of every layer, comparing two instances and copying a layer. Every change is a function of the
 * configuration, which the window writes as the SDK's values of this PC.
 */
public final class ConfigViews {

    private ConfigViews() {}

    private static final String NONE = "—";

    // ---- what a view edits ----

    /** One layer: an emulator's, which its instances start from, or an instance's own, over it. */
    public sealed interface Target {

        Layer layer(EmulatorConfig config);

        EmulatorConfig with(EmulatorConfig config, Layer layer);

        PlatformId platform();

        String title();

        record Emulator(PlatformId platform) implements Target {
            @Override
            public Layer layer(EmulatorConfig config) {
                return config.emulator(platform);
            }

            @Override
            public EmulatorConfig with(EmulatorConfig config, Layer layer) {
                return config.withEmulator(platform, layer);
            }

            @Override
            public String title() {
                return platform.displayName() + " (emulator)";
            }
        }

        record Instance(InstanceKey key) implements Target {
            @Override
            public Layer layer(EmulatorConfig config) {
                return config.instance(key);
            }

            @Override
            public EmulatorConfig with(EmulatorConfig config, Layer layer) {
                return config.withInstance(key, layer);
            }

            @Override
            public PlatformId platform() {
                return key.platform();
            }

            @Override
            public String title() {
                return key.reference();
            }
        }
    }

    // ---- a view's rows ----

    /**
     * One field of a view.
     *
     * @param wanted the value the layer, or for an instance its emulator's under it, sets; empty when none does
     * @param own    whether this layer sets it itself
     * @param now    what the instance has; empty for an emulator, or when it was not read
     * @param status the instance's state against the wanted value, or null for an emulator or an unset field
     */
    public record SettingRow(ConfigField field, Optional<ConfigValue> wanted, boolean own, Optional<ConfigValue> now,
                             ConfigDiff.Status status) {

        /** Whether the instance is not as wanted: Apply changes it, or the user must, by hand. */
        public boolean differs() {
            return status == ConfigDiff.Status.CHANGE || status == ConfigDiff.Status.BY_HAND && now.isPresent();
        }

        public String wantedText() {
            return wanted.map(ConfigValue::said).orElse(NONE);
        }

        /** Where the wanted value comes from, in the view's words. */
        public String fromText(Target target) {
            if (wanted.isEmpty()) return "not set";
            if (own) return target instanceof Target.Instance ? "this instance" : "this emulator";
            return target.platform().displayName() + " (emulator)";
        }

        public String nowText() {
            return now.map(ConfigValue::said).orElse(NONE);
        }
    }

    /** An account row's state on the instance. */
    public enum AccountState {
        HELD("held", "signed in"),
        TO_SIGN_IN("to_sign_in", "Apply signs it in"),
        TO_REMOVE("to_remove", "signed in → Apply removes it"),
        REMOVED("removed", "removed here"),
        RECORD("record", "record only"),
        UNREAD("unread", "not read"),
        UNKNOWN("unknown", "");

        private final String id;
        private final String displayName;

        AccountState(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static AccountState fromId(String id) {
            for (AccountState state : values()) {
                if (state.id.equals(id)) return state;
            }
            return UNKNOWN;
        }
    }

    /**
     * One account of a view; a removal row ({@code removal}) is an account the emulator lists that this instance should
     * not hold.
     *
     * @param state null for an emulator's view
     */
    public record AccountRow(AccountRecord account, boolean own, boolean removal, boolean isDefault,
                             AccountState state) {

        public boolean differs() {
            return state == AccountState.TO_SIGN_IN || state == AccountState.TO_REMOVE;
        }
    }

    /** A game row's state on the instance. */
    public enum GameState {
        INSTALLED("installed", "installed"),
        MISSING("missing", "Apply installs it"),
        REMOVED("removed", "not wanted here; it stays installed"),
        UNREAD("unread", "not read"),
        UNKNOWN("unknown", "");

        private final String id;
        private final String displayName;

        GameState(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static GameState fromId(String id) {
            for (GameState state : values()) {
                if (state.id.equals(id)) return state;
            }
            return UNKNOWN;
        }
    }

    /** @param state null for an emulator's view */
    public record GameRow(GameRecord game, boolean own, boolean removal, GameState state) {

        public boolean differs() {
            return state == GameState.MISSING;
        }
    }

    /** A view's field filter: the words a field's name holds, and whether only what differs is shown. */
    public record ViewFilter(String field, boolean onlyDifferences) {

        public static final ViewFilter NONE = new ViewFilter("", false);

        public ViewFilter {
            field = field == null ? "" : field.strip();
        }

        public boolean keeps(SettingRow row) {
            return (!onlyDifferences || row.differs()) && contains(row.field().displayName(), field);
        }

        public boolean keeps(AccountRow row) {
            return !onlyDifferences || row.differs();
        }

        public boolean keeps(GameRow row) {
            return !onlyDifferences || row.differs();
        }
    }

    /** Every field a layer of {@code target}'s kind may set; an instance's view leaves out the emulator-only ones. */
    public static List<SettingRow> settings(Target target, EmulatorConfig config, EmulatorsModel.InstanceRow row) {
        List<SettingRow> out = new ArrayList<>();
        Layer layer = target.layer(config);
        for (ConfigField field : ConfigField.values()) {
            if (field == ConfigField.UNKNOWN) continue;
            if (target instanceof Target.Instance instance) {
                if (field.emulatorOnly()) continue;
                Optional<ConfigValue> wanted = config.wanted(instance.key()).field(field).map(Sourced::value);
                ConfigDiff.FieldState state = row == null || row.diff() == null ? null : row.diff().fields().stream()
                        .filter(f -> f.field() == field).findFirst().orElse(null);
                Optional<ConfigValue> now = row == null ? Optional.empty() : row.now(field);
                out.add(new SettingRow(field, wanted, layer.fields().containsKey(field), now,
                        wanted.isEmpty() || state == null ? null : state.status()));
            } else {
                Optional<ConfigValue> own = Optional.ofNullable(layer.fields().get(field));
                out.add(new SettingRow(field, own, own.isPresent(), Optional.empty(), null));
            }
        }
        return out;
    }

    /** The accounts: those wanted, the default Google one marked, then for an instance those it removes. */
    public static List<AccountRow> accounts(Target target, EmulatorConfig config, EmulatorsModel.InstanceRow row) {
        List<AccountRow> out = new ArrayList<>();
        if (!(target instanceof Target.Instance instance)) {
            Layer layer = target.layer(config);
            String first = defaultOf(layer.accounts(), layer.defaultGoogle());
            for (AccountRecord account : layer.accounts()) {
                out.add(new AccountRow(account, true, false, isDefault(account, first), null));
            }
            return out;
        }
        Wanted wanted = config.wanted(instance.key());
        Optional<Set<String>> held = row == null ? Optional.empty()
                : row.android().flatMap(android -> android.googleAccounts());
        String first = wanted.defaultGoogle().orElse(null);
        for (Sourced<AccountRecord> sourced : wanted.accounts()) {
            AccountRecord account = sourced.value();
            AccountState state = !account.kind().signedInByBotMaker() ? AccountState.RECORD
                    : held.isEmpty() ? AccountState.UNREAD
                    : held.get().contains(lower(account.name())) ? AccountState.HELD : AccountState.TO_SIGN_IN;
            out.add(new AccountRow(account, sourced.source() == Sourced.Source.INSTANCE, false,
                    isDefault(account, first), state));
        }
        for (AccountRecord account : config.emulator(instance.platform()).accounts()) {
            AccountRecord.Key key = account.key();
            if (!wanted.removedAccounts().contains(key)) continue;
            AccountState state = key.kind().signedInByBotMaker() && held.isPresent() && held.get().contains(key.name())
                    ? AccountState.TO_REMOVE : AccountState.REMOVED;
            out.add(new AccountRow(account, true, true, false, state));
        }
        return out;
    }

    /** The games: those wanted, then for an instance those its emulator lists that it does not want. */
    public static List<GameRow> games(Target target, EmulatorConfig config, EmulatorsModel.InstanceRow row) {
        List<GameRow> out = new ArrayList<>();
        if (!(target instanceof Target.Instance instance)) {
            for (GameRecord game : target.layer(config).games()) out.add(new GameRow(game, true, false, null));
            return out;
        }
        Wanted wanted = config.wanted(instance.key());
        Optional<Set<String>> installed = row == null ? Optional.empty()
                : row.android().flatMap(android -> android.packages());
        for (Sourced<GameRecord> sourced : wanted.games()) {
            GameRecord game = sourced.value();
            GameState state = installed.isEmpty() ? GameState.UNREAD
                    : installed.get().contains(game.packageName()) ? GameState.INSTALLED : GameState.MISSING;
            out.add(new GameRow(game, sourced.source() == Sourced.Source.INSTANCE, false, state));
        }
        for (GameRecord game : config.emulator(instance.platform()).games()) {
            if (wanted.removedGames().contains(game.packageName())) {
                out.add(new GameRow(game, true, true, GameState.REMOVED));
            }
        }
        return out;
    }

    // ---- a view's changes ----

    /** Sets the field on the layer: on an instance, its own value over its emulator's. */
    public static EmulatorConfig set(Target target, EmulatorConfig config, ConfigField field, ConfigValue value) {
        return target.with(config, target.layer(config).with(field, value));
    }

    /** Drops the layer's own value: an instance's falls back to its emulator's, an emulator's to none. */
    public static EmulatorConfig clear(Target target, EmulatorConfig config, ConfigField field) {
        return target.with(config, target.layer(config).without(field));
    }

    /** Adds the account, or changes its note. */
    public static EmulatorConfig addAccount(Target target, EmulatorConfig config, AccountRecord account) {
        return target.with(config, target.layer(config).withAccount(account));
    }

    /**
     * Names a Google account by the address Android lists it under, where the instance's layer or its emulator's names
     * it by another of its addresses ({@code typed}); its note and default stay.
     */
    public static EmulatorConfig renameAccount(EmulatorConfig config, InstanceKey key, String typed, String listed) {
        AccountRecord.Key old = new AccountRecord.Key(AccountRecord.Kind.GOOGLE, typed);
        EmulatorConfig next = config;
        for (Target target : List.of(new Target.Emulator(key.platform()), new Target.Instance(key))) {
            Layer layer = target.layer(next);
            Optional<AccountRecord> held = layer.accounts().stream().filter(a -> a.key().equals(old)).findFirst();
            if (held.isEmpty()) continue;
            Layer renamed = layer.inheritingAccount(old)
                    .withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, listed, held.get().note()));
            if (typed.equalsIgnoreCase(layer.defaultGoogle())) renamed = renamed.withDefaultGoogle(listed);
            next = target.with(next, renamed);
        }
        return next;
    }

    /**
     * Takes the account off the layer. On an instance, one its emulator lists is marked removed, so Apply signs it
     * out; one only the instance listed is simply dropped.
     */
    public static EmulatorConfig removeAccount(Target target, EmulatorConfig config, AccountRecord.Key key) {
        Layer layer = target.layer(config);
        boolean inherited = target instanceof Target.Instance
                && config.emulator(target.platform()).accounts().stream().anyMatch(a -> a.key().equals(key));
        return target.with(config, inherited ? layer.removingAccount(key) : layer.inheritingAccount(key));
    }

    /** Takes a removal back: the instance holds what its emulator lists again. */
    public static EmulatorConfig keepAccount(Target target, EmulatorConfig config, AccountRecord.Key key) {
        return target.with(config, target.layer(config).inheritingAccount(key));
    }

    public static EmulatorConfig makeDefault(Target target, EmulatorConfig config, String email) {
        return target.with(config, target.layer(config).withDefaultGoogle(email));
    }

    public static EmulatorConfig addGame(Target target, EmulatorConfig config, GameRecord game) {
        return target.with(config, target.layer(config).withGame(game));
    }

    /** As {@link #removeAccount}, for a game; Apply never uninstalls one. */
    public static EmulatorConfig removeGame(Target target, EmulatorConfig config, String packageName) {
        Layer layer = target.layer(config);
        boolean inherited = target instanceof Target.Instance && config.emulator(target.platform()).games().stream()
                .anyMatch(g -> g.packageName().equals(packageName));
        return target.with(config, inherited ? layer.removingGame(packageName) : layer.inheritingGame(packageName));
    }

    public static EmulatorConfig keepGame(Target target, EmulatorConfig config, String packageName) {
        return target.with(config, target.layer(config).inheritingGame(packageName));
    }

    // ---- typing a value ----

    private static final Pattern SCREEN = Pattern.compile(
            "(\\d+)\\s*[x×]\\s*(\\d+)\\s*(?:@|,)\\s*(\\d+)\\s*(?:dpi)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIZE = Pattern.compile("(\\d+)\\s*(gb|g|mb|m)?", Pattern.CASE_INSENSITIVE);

    /** The value typed for a field of {@code kind}, as the window shows one or as it is written; empty if not one. */
    public static Optional<ConfigValue> typed(ConfigField.Kind kind, String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String typed = text.strip();
        try {
            return switch (kind) {
                case SCREEN -> {
                    Matcher m = SCREEN.matcher(typed);
                    yield m.matches() ? Optional.of(new ConfigValue.Screen(Integer.parseInt(m.group(1)),
                            Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))) : Optional.empty();
                }
                case MEGABYTES -> {
                    Matcher m = SIZE.matcher(typed);
                    if (!m.matches()) yield Optional.empty();
                    int n = Integer.parseInt(m.group(1));
                    boolean gigabytes = m.group(2) != null && m.group(2).toLowerCase(Locale.ROOT).startsWith("g");
                    yield Optional.of(new ConfigValue.Megabytes(gigabytes ? Math.multiplyExact(n, 1024) : n));
                }
                case COUNT -> Optional.of(new ConfigValue.Count(Integer.parseInt(typed)));
                case TOGGLE -> switch (typed.toLowerCase(Locale.ROOT)) {
                    case "on", "yes", "true" -> Optional.of(new ConfigValue.Toggle(true));
                    case "off", "no", "false" -> Optional.of(new ConfigValue.Toggle(false));
                    default -> Optional.empty();
                };
                case TEXT -> Optional.of(new ConfigValue.Text(typed));
            };
        } catch (IllegalArgumentException | ArithmeticException notOne) {
            return Optional.empty();
        }
    }

    /** How a value of {@code kind} is typed. */
    public static String hint(ConfigField.Kind kind) {
        return switch (kind) {
            case SCREEN -> "width x height @ dpi, e.g. 1080x1920@320";
            case MEGABYTES -> "e.g. 4 GB or 3072 MB";
            case COUNT -> "a whole number, e.g. 4";
            case TOGGLE -> "on or off";
            case TEXT -> "e.g. en-US, or Europe/Paris";
        };
    }

    // ---- the Configurations tab ----

    /** Where a grid row is listed, and what the section filter picks. */
    public enum GridSection {
        SETTINGS("settings", "Settings"),
        LOCALE("locale", "Locale"),
        QUIET("quiet", "Quiet runs"),
        PRODUCT("product", "The emulator program"),
        ACCOUNTS("accounts", "Accounts"),
        GAMES("games", "Games"),
        TAGS("tags", "Tags"),
        UNKNOWN("unknown", "Unknown");

        private final String id;
        private final String displayName;

        GridSection(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        static GridSection of(ConfigField.Section section) {
            return switch (section) {
                case SETTINGS -> SETTINGS;
                case LOCALE -> LOCALE;
                case QUIET -> QUIET;
                case PRODUCT -> PRODUCT;
            };
        }

        public static GridSection fromId(String id) {
            for (GridSection section : values()) {
                if (section.id.equals(id)) return section;
            }
            return UNKNOWN;
        }
    }

    /**
     * One column of the grid.
     *
     * @param found for an instance, whether this read found it; one only the configuration names is still listed
     */
    public record GridColumn(Target target, String title, boolean found, List<String> tags) {

        public GridColumn {
            tags = List.copyOf(tags);
        }

        public boolean emulator() {
            return target instanceof Target.Emulator;
        }
    }

    /**
     * A cell: its words, whether the column's own layer sets it (an instance's own value is shown strong), and whether
     * it only says the value is inherited or unset (shown muted).
     */
    public record Cell(String text, boolean own, boolean placeholder) {

        public Cell(String text, boolean own) {
            this(text, own, false);
        }

        static Cell inherits() {
            return new Cell("inherits", false, true);
        }

        static Cell none() {
            return new Cell(NONE, false, true);
        }
    }

    /** One row of the grid: a field, an account kind, the games or the tags; {@code field} null but for a field. */
    public record GridRow(GridSection section, String name, ConfigField field, List<Cell> cells) {

        public GridRow {
            cells = List.copyOf(cells);
        }
    }

    /** The grid: its columns, and its rows with a cell per column. */
    public record Grid(List<GridColumn> columns, List<GridRow> rows) {

        public Grid {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
        }
    }

    /** The grid's filters; null emulator and section mean any. */
    public record GridFilter(String text, GridSection section, PlatformId emulator, String tag, boolean overriddenOnly) {

        public static final GridFilter NONE = new GridFilter("", null, null, "", false);

        public GridFilter {
            text = text == null ? "" : text.strip();
            tag = tag == null ? "" : tag.strip();
        }
    }

    /**
     * Every layer: per emulator, its column then its instances'. An emulator is listed when it is installed or has a
     * configuration; an instance when this read found it or the configuration names it.
     */
    public static List<GridColumn> columns(EmulatorsModel.Snapshot snapshot) {
        EmulatorConfig config = snapshot.config();
        Set<PlatformId> platforms = new LinkedHashSet<>();
        snapshot.emulators().stream().filter(row -> row.installed() || !config.emulator(row.platform()).isEmpty())
                .forEach(row -> platforms.add(row.platform()));
        platforms.addAll(config.emulators().keySet());
        snapshot.instances().forEach(row -> platforms.add(row.key().platform()));
        config.instances().keySet().forEach(key -> platforms.add(key.platform()));

        List<GridColumn> out = new ArrayList<>();
        for (PlatformId platform : platforms) {
            Target.Emulator emulator = new Target.Emulator(platform);
            out.add(new GridColumn(emulator, emulator.title(), true, List.of()));
            Set<InstanceKey> listed = new LinkedHashSet<>();
            snapshot.instances().stream().filter(row -> row.key().platform() == platform)
                    .sorted(Comparator.comparing(row -> row.key().reference(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(row -> {
                        listed.add(row.key());
                        out.add(new GridColumn(new Target.Instance(row.key()), row.key().reference(), true,
                                row.tags()));
                    });
            config.instances().forEach((key, layer) -> {
                if (key.platform() == platform && !listed.contains(key)) {
                    out.add(new GridColumn(new Target.Instance(key), key.reference() + " (not found)", false,
                            layer.tags()));
                }
            });
        }
        return out;
    }

    /** The grid, its columns and rows filtered. */
    public static Grid grid(EmulatorsModel.Snapshot snapshot, GridFilter filter) {
        EmulatorConfig config = snapshot.config();
        List<GridColumn> columns = columns(snapshot).stream()
                .filter(column -> filter.emulator() == null || column.target().platform() == filter.emulator())
                .filter(column -> filter.tag().isEmpty() || column.emulator()
                        || column.tags().stream().anyMatch(filter.tag()::equalsIgnoreCase))
                .toList();

        List<GridRow> rows = new ArrayList<>();
        for (ConfigField field : ConfigField.values()) {
            if (field == ConfigField.UNKNOWN) continue;
            rows.add(new GridRow(GridSection.of(field.section()), field.displayName(), field,
                    cells(columns, column -> fieldCell(config, column.target(), field))));
        }
        for (AccountRecord.Kind kind : List.of(AccountRecord.Kind.GOOGLE, AccountRecord.Kind.FACEBOOK,
                AccountRecord.Kind.MAIL)) {
            rows.add(new GridRow(GridSection.ACCOUNTS, kind.displayName(), null,
                    cells(columns, column -> accountCell(config, column.target(), kind))));
        }
        rows.add(new GridRow(GridSection.GAMES, "Games", null,
                cells(columns, column -> gameCell(config, column.target()))));
        rows.add(new GridRow(GridSection.TAGS, "Tags", null, cells(columns, column -> {
            List<String> tags = column.target().layer(config).tags();
            return tags.isEmpty() ? Cell.none() : new Cell(String.join(", ", tags), true);
        })));

        List<GridRow> kept = rows.stream()
                .filter(row -> filter.section() == null || row.section() == filter.section())
                .filter(row -> filter.text().isEmpty() || contains(row.name(), filter.text())
                        || row.cells().stream().anyMatch(cell -> contains(cell.text(), filter.text())))
                .filter(row -> !filter.overriddenOnly() || overridden(row, columns))
                .toList();
        return new Grid(columns, kept);
    }

    private static boolean overridden(GridRow row, List<GridColumn> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (!columns.get(i).emulator() && row.cells().get(i).own()) return true;
        }
        return false;
    }

    private static List<Cell> cells(List<GridColumn> columns, Function<GridColumn, Cell> cell) {
        return columns.stream().map(cell).toList();
    }

    private static Cell fieldCell(EmulatorConfig config, Target target, ConfigField field) {
        ConfigValue own = target.layer(config).fields().get(field);
        if (own != null) return new Cell(own.said(), true);
        if (target instanceof Target.Instance && !field.emulatorOnly()
                && config.emulator(target.platform()).fields().containsKey(field)) {
            return Cell.inherits();
        }
        return Cell.none();
    }

    private static Cell accountCell(EmulatorConfig config, Target target, AccountRecord.Kind kind) {
        Layer layer = target.layer(config);
        List<String> parts = new ArrayList<>();
        boolean instance = target instanceof Target.Instance;
        layer.accounts().stream().filter(a -> a.kind() == kind)
                .forEach(a -> parts.add((instance ? "+ " : "") + a.name()));
        layer.removedAccounts().stream().filter(k -> k.kind() == kind)
                .filter(k -> config.emulator(target.platform()).accounts().stream().anyMatch(a -> a.key().equals(k)))
                .forEach(k -> parts.add("− " + k.name()));
        if (!parts.isEmpty()) return new Cell(String.join(", ", parts), true);
        boolean inherits = instance && config.emulator(target.platform()).accounts().stream()
                .anyMatch(a -> a.kind() == kind);
        return inherits ? Cell.inherits() : Cell.none();
    }

    private static Cell gameCell(EmulatorConfig config, Target target) {
        Layer layer = target.layer(config);
        List<String> parts = new ArrayList<>();
        boolean instance = target instanceof Target.Instance;
        layer.games().forEach(g -> parts.add((instance ? "+ " : "") + g.label()));
        config.emulator(target.platform()).games().stream()
                .filter(g -> instance && layer.removedGames().contains(g.packageName()))
                .forEach(g -> parts.add("− " + g.label()));
        if (!parts.isEmpty()) return new Cell(String.join(", ", parts), true);
        boolean inherits = instance && !config.emulator(target.platform()).games().isEmpty();
        return inherits ? Cell.inherits() : Cell.none();
    }

    // ---- compare and copy ----

    /** One thing two instances are configured differently in. */
    public record Difference(String what, String first, String second) {
    }

    /** Where what two instances should be differs: each field, each kind of account, the games and the tags. */
    public static List<Difference> compare(EmulatorConfig config, InstanceKey first, InstanceKey second) {
        Wanted a = config.wanted(first);
        Wanted b = config.wanted(second);
        List<Difference> out = new ArrayList<>();
        for (ConfigField field : ConfigField.values()) {
            if (field == ConfigField.UNKNOWN || field.emulatorOnly()) continue;
            String one = a.field(field).map(s -> s.value().said()).orElse(NONE);
            String two = b.field(field).map(s -> s.value().said()).orElse(NONE);
            if (!one.equals(two)) out.add(new Difference(field.displayName(), one, two));
        }
        for (AccountRecord.Kind kind : List.of(AccountRecord.Kind.GOOGLE, AccountRecord.Kind.FACEBOOK,
                AccountRecord.Kind.MAIL)) {
            String one = names(a.accounts(), kind);
            String two = names(b.accounts(), kind);
            if (!one.equals(two)) out.add(new Difference(kind.displayName() + " accounts", one, two));
        }
        String gamesA = joined(a.games().stream().map(s -> s.value().label()).sorted().toList());
        String gamesB = joined(b.games().stream().map(s -> s.value().label()).sorted().toList());
        if (!gamesA.equals(gamesB)) out.add(new Difference("Games", gamesA, gamesB));
        String tagsA = joined(a.tags());
        String tagsB = joined(b.tags());
        if (!tagsA.equals(tagsB)) out.add(new Difference("Tags", tagsA, tagsB));
        return out;
    }

    private static String names(List<Sourced<AccountRecord>> accounts, AccountRecord.Kind kind) {
        return joined(accounts.stream().map(Sourced::value).filter(a -> a.kind() == kind)
                .map(a -> lower(a.name())).sorted().toList());
    }

    private static String joined(List<String> parts) {
        return parts.isEmpty() ? NONE : String.join(", ", parts);
    }

    /**
     * {@code to}'s layer made a copy of {@code from}'s settings, accounts and games. An instance onto another of the same
     * emulator copies its own layer, removals included, since both inherit the same; any other copy carries what
     * {@code from} should be in full, its inherited values too, and no removal. An instance keeps its own tags, an
     * emulator its own emulator-only fields when the copy comes from an instance, which has none.
     */
    public static EmulatorConfig copy(EmulatorConfig config, Target from, Target to) {
        if (from.equals(to)) throw new IllegalArgumentException("a column is copied to another one");
        if (from instanceof Target.Instance && to instanceof Target.Instance && from.platform() == to.platform()) {
            Layer source = from.layer(config);
            return to.with(config, new Layer(source.fields(), source.accounts(), source.removedAccounts(),
                    source.defaultGoogle(), source.games(), source.removedGames(), to.layer(config).tags()));
        }
        Layer whole = whole(config, from);
        Map<ConfigField, ConfigValue> fields = new EnumMap<>(ConfigField.class);
        whole.fields().forEach((field, value) -> {
            if (to instanceof Target.Emulator || !field.emulatorOnly()) fields.put(field, value);
        });
        if (to instanceof Target.Emulator && from instanceof Target.Instance) {
            to.layer(config).fields().forEach((field, value) -> {
                if (field.emulatorOnly()) fields.put(field, value);
            });
        }
        List<String> tags = to instanceof Target.Instance ? to.layer(config).tags() : List.of();
        return to.with(config, new Layer(fields, whole.accounts(), Set.of(), whole.defaultGoogle(), whole.games(),
                Set.of(), tags));
    }

    /** Whether {@code name} names an account of {@code kind}: a Facebook name is any words, the others an email address. */
    public static boolean accountName(AccountRecord.Kind kind, String name) {
        if (name == null || name.isBlank()) return false;
        return kind == AccountRecord.Kind.FACEBOOK || name.strip().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    /**
     * How a value {@code target} sets of its own is dropped: "Use emulator's" for an instance whose emulator sets the
     * field, else "Clear"; empty when it sets none.
     */
    public static Optional<String> dropLabel(Target target, EmulatorConfig config, ConfigField field) {
        if (!target.layer(config).fields().containsKey(field)) return Optional.empty();
        boolean inherited = target instanceof Target.Instance
                && config.emulator(target.platform()).fields().containsKey(field);
        return Optional.of(inherited ? "Use emulator's" : "Clear");
    }

    /** The value {@code target} should have for {@code field}: its own, else, for an instance, its emulator's. */
    public static Optional<ConfigValue> current(Target target, EmulatorConfig config, ConfigField field) {
        Optional<ConfigValue> own = Optional.ofNullable(target.layer(config).fields().get(field));
        if (own.isPresent() || !(target instanceof Target.Instance) || field.emulatorOnly()) return own;
        return Optional.ofNullable(config.emulator(target.platform()).fields().get(field));
    }

    /** What {@code target} should be in full: an emulator's own layer, an instance's wanted values, no removal. */
    private static Layer whole(EmulatorConfig config, Target target) {
        if (!(target instanceof Target.Instance(var key))) return target.layer(config);
        Wanted wanted = config.wanted(key);
        Map<ConfigField, ConfigValue> fields = new EnumMap<>(ConfigField.class);
        wanted.fields().forEach((field, value) -> fields.put(field, value.value()));
        return new Layer(fields, wanted.accounts().stream().map(Sourced::value).toList(), Set.of(),
                wanted.defaultGoogle().orElse(null), wanted.games().stream().map(Sourced::value).toList(), Set.of(),
                List.of());
    }

    // ---- words ----

    private static String defaultOf(List<AccountRecord> accounts, String chosen) {
        List<String> google = accounts.stream().filter(a -> a.kind() == AccountRecord.Kind.GOOGLE)
                .map(AccountRecord::name).toList();
        if (chosen != null) {
            for (String name : google) if (name.equalsIgnoreCase(chosen)) return name;
        }
        return google.isEmpty() ? null : google.getFirst();
    }

    private static boolean isDefault(AccountRecord account, String first) {
        return account.kind() == AccountRecord.Kind.GOOGLE && first != null && account.name().equalsIgnoreCase(first);
    }

    private static boolean contains(String in, String words) {
        return words.isEmpty() || in.toLowerCase(Locale.ROOT).contains(words.toLowerCase(Locale.ROOT));
    }

    private static String lower(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }
}
