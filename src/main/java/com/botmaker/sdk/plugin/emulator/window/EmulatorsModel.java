package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.botmaker.shared.emulator.config.SavedFilters;
import com.botmaker.shared.emulator.config.Sourced;
import com.botmaker.shared.emulator.config.Wanted;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the Emulators window shows, with no JavaFX: one read of this PC ({@link Snapshot}), its rows, and the sorting
 * and filtering every view does. The window draws these and holds nothing of its own.
 */
public final class EmulatorsModel {

    private EmulatorsModel() {}

    /** Where a read gets this PC's state; the window gives the live one, a test its own. */
    public interface Source {
        Platforms.DiscoveryReport discover();

        HostConfig.State configuration();

        boolean running(EmulatorInstance instance);

        /** What the instance has now; {@code last} is the previous read's, whose Android answer a stopped one keeps. */
        InstanceFacts facts(EmulatorInstance instance, InstanceFacts last);
    }

    /** One product, installed or not. */
    public record EmulatorRow(PlatformId platform, boolean installed, String status, int instances, int running,
                              Layer layer, int differing) {

        /** What its configuration holds, in a few words. */
        public String configured() {
            if (layer.isEmpty()) return "Nothing set";
            List<String> parts = new ArrayList<>();
            if (!layer.fields().isEmpty()) parts.add(count(layer.fields().size(), "setting"));
            if (!layer.accounts().isEmpty()) parts.add(count(layer.accounts().size(), "account"));
            if (!layer.games().isEmpty()) parts.add(count(layer.games().size(), "game"));
            return String.join(", ", parts);
        }
    }

    /**
     * One instance.
     *
     * @param facts what it has now, or null when it could not be read
     * @param diff  where it differs from {@code wanted}, or null with no facts
     */
    public record InstanceRow(EmulatorInstance instance, InstanceKey key, boolean running, Wanted wanted,
                              InstanceFacts facts, ConfigDiff diff, List<String> usedBy) {

        public InstanceRow {
            usedBy = List.copyOf(usedBy);
        }

        public List<String> tags() {
            return wanted.tags();
        }

        public boolean differs() {
            return diff != null && diff.differs();
        }

        /** Whether Apply has something to do on it. */
        public boolean applicable() {
            return diff != null && diff.applicable();
        }

        /** How many things Apply would change. */
        public int changes() {
            if (diff == null) return 0;
            return (int) diff.fields().stream().filter(f -> f.status() == ConfigDiff.Status.CHANGE).count()
                    + diff.accounts().size() + diff.missingGames().size();
        }

        /** The accounts it should hold, by name. */
        public List<String> accounts() {
            return wanted.accounts().stream().map(Sourced::value).map(AccountRecord::name).toList();
        }

        /** The games it should have, by label. */
        public List<String> games() {
            return wanted.games().stream().map(Sourced::value).map(GameRecord::label).toList();
        }

        Optional<AndroidState> android() {
            return facts == null ? Optional.empty() : facts.android();
        }

        Optional<ConfigValue> now(ConfigField field) {
            return facts == null ? Optional.empty() : facts.field(field).map(InstanceFacts.Read::value);
        }
    }

    /** One read of this PC. */
    public record Snapshot(List<EmulatorRow> emulators, List<InstanceRow> instances, Instant readAt) {

        public static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), Instant.EPOCH);

        public Snapshot {
            emulators = List.copyOf(emulators);
            instances = List.copyOf(instances);
        }

        public int running() {
            return (int) instances.stream().filter(InstanceRow::running).count();
        }

        public Optional<InstanceRow> instance(InstanceKey key) {
            return instances.stream().filter(row -> row.key().equals(key)).findFirst();
        }

        /** Every tag any instance carries, sorted. */
        public List<String> tags() {
            Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            instances.forEach(row -> tags.addAll(row.tags()));
            return List.copyOf(tags);
        }

        /** The instances of {@code platform} Apply would change. */
        public List<InstanceRow> applicable(PlatformId platform) {
            return instances.stream().filter(row -> row.key().platform() == platform && row.applicable()).toList();
        }
    }

    /** Reads this PC; {@code last} gives each stopped instance its Android answer back. Runs off the UI thread. */
    public static Snapshot read(Source source, Snapshot last, Instant now) {
        Platforms.DiscoveryReport report = source.discover();
        HostConfig.State state = source.configuration();
        List<InstanceRow> instances = new ArrayList<>();
        for (EmulatorInstance instance : report.instances()) {
            // a phone on a cable is no emulator's instance: nothing here starts, stops or configures it
            if (instance.platformId() == PlatformId.UNKNOWN || instance.platformId() == PlatformId.PHYSICAL) continue;
            InstanceKey key = InstanceKey.of(instance);
            Wanted wanted = state.config().wanted(key);
            boolean running = false;
            InstanceFacts facts = null;
            try {
                running = source.running(instance);
                facts = source.facts(instance, last.instance(key).map(InstanceRow::facts).orElse(null));
            } catch (RuntimeException unread) {
                // the row says "not read"; one instance that fails to answer hides no other
            }
            instances.add(new InstanceRow(instance, key, running, wanted, facts,
                    facts == null ? null : ConfigDiff.of(wanted, facts), state.usedBy(key)));
        }
        List<EmulatorRow> emulators = new ArrayList<>();
        for (Platforms.PlatformStatus status : report.statuses()) {
            PlatformId platform = status.platformId();
            if (platform == PlatformId.UNKNOWN || platform == PlatformId.PHYSICAL) continue;
            List<InstanceRow> own = instances.stream().filter(row -> row.key().platform() == platform).toList();
            emulators.add(new EmulatorRow(platform, status.installed(), status.statusLine(), own.size(),
                    (int) own.stream().filter(InstanceRow::running).count(), state.config().emulator(platform),
                    (int) own.stream().filter(InstanceRow::differs).count()));
        }
        return new Snapshot(emulators, instances, now);
    }

    // ---- columns ----

    /** The Instances tab's columns: what each shows for a row, and how it sorts. */
    public enum Column {
        NAME("name", "Instance", true),
        EMULATOR("emulator", "Emulator", true),
        STATE("state", "State", true),
        DIFFERS("differs", "Configuration", true),
        ACCOUNTS("accounts", "Accounts", true),
        GAMES("games", "Games", true),
        TAGS("tags", "Tags", true),
        USED_BY("used_by", "Used by bots", true),
        ANDROID("android", "Android", false),
        SCREEN("screen", "Screen", false),
        MEMORY("memory", "Memory", false),
        ROOT("root", "Root", false),
        PORT("port", "ADB address", false),
        UNKNOWN("unknown", "Unknown", false);

        private final String id;
        private final String displayName;
        private final boolean shownAtFirst;

        Column(String id, String displayName, boolean shownAtFirst) {
            this.id = id;
            this.displayName = displayName;
            this.shownAtFirst = shownAtFirst;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        /** Whether the column shows before the user chose any. */
        public boolean shownAtFirst() {
            return shownAtFirst;
        }

        public static Column fromId(String id) {
            for (Column column : values()) {
                if (column.id.equals(id)) return column;
            }
            return UNKNOWN;
        }

        /** The columns a table offers. */
        public static List<Column> offered() {
            return List.of(values()).stream().filter(column -> column != UNKNOWN).toList();
        }

        /** What the cell says. */
        public String text(InstanceRow row) {
            return switch (this) {
                case NAME -> row.instance().name();
                case EMULATOR -> row.key().platform().displayName();
                case STATE -> row.running() ? "Running" : "Stopped";
                case DIFFERS -> row.diff() == null ? "Not read"
                        : row.changes() > 0 ? count(row.changes(), "change") + " to apply"
                        : row.differs() ? "Differs, set by hand" : "As configured";
                case ACCOUNTS -> String.join(", ", row.accounts());
                case GAMES -> String.join(", ", row.games());
                case TAGS -> String.join(", ", row.tags());
                case USED_BY -> String.join(", ", row.usedBy());
                case ANDROID -> row.android().map(AndroidState::android).orElse("");
                case SCREEN -> row.now(ConfigField.SCREEN).map(ConfigValue::said).orElse("");
                case MEMORY -> row.now(ConfigField.MEMORY).map(ConfigValue::said).orElse("");
                case ROOT -> row.now(ConfigField.ROOT).map(ConfigValue::said).orElse("");
                case PORT -> row.instance().endpoint();
                case UNKNOWN -> "";
            };
        }

        /** The column's order, rising; rows that tie keep the emulator-then-name order. */
        public Comparator<InstanceRow> order() {
            Comparator<InstanceRow> own = switch (this) {
                case STATE -> Comparator.comparing(row -> !row.running());
                case DIFFERS -> Comparator.comparingInt(row -> -row.changes());
                case MEMORY -> Comparator.comparingInt(row -> row.now(ConfigField.MEMORY)
                        .map(value -> ((ConfigValue.Megabytes) value).value()).orElse(0));
                default -> Comparator.comparing(this::text, String.CASE_INSENSITIVE_ORDER);
            };
            return own.thenComparing(row -> row.key().platform().displayName(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(row -> row.instance().name(), String.CASE_INSENSITIVE_ORDER);
        }
    }

    /** The rows in {@code column}'s order, or the other way round. */
    public static List<InstanceRow> sorted(List<InstanceRow> rows, Column column, boolean rising) {
        Comparator<InstanceRow> order = column.order();
        return rows.stream().sorted(rising ? order : order.reversed()).toList();
    }

    // ---- filters ----

    /** Which instances a filter keeps by their state. */
    public enum Running {
        ANY("any", "Any state"),
        RUNNING("running", "Running"),
        STOPPED("stopped", "Stopped");

        private final String id;
        private final String displayName;

        Running(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static Running fromId(String id) {
            for (Running running : values()) {
                if (running.id.equals(id)) return running;
            }
            return ANY;
        }
    }

    /**
     * The Instances tab's filter; every part narrows, and an empty part keeps everything.
     *
     * @param text     words found in the instance's name, case aside
     * @param emulator the product, or null for any
     * @param account  words found in an account it should hold
     * @param app      words found in a game it should have, its label or its package
     * @param tag      a tag it carries, exactly, case aside
     * @param differs  only the instances that differ from their configuration
     */
    public record Filter(String text, PlatformId emulator, Running state, String account, String app, String tag,
                         boolean differs) {

        public static final Filter NONE = new Filter("", null, Running.ANY, "", "", "", false);

        public Filter {
            text = clean(text);
            emulator = emulator == PlatformId.UNKNOWN ? null : emulator;
            state = state == null ? Running.ANY : state;
            account = clean(account);
            app = clean(app);
            tag = clean(tag);
        }

        public boolean keeps(InstanceRow row) {
            if (!contains(row.instance().name(), text)) return false;
            if (emulator != null && row.key().platform() != emulator) return false;
            if (state == Running.RUNNING && !row.running() || state == Running.STOPPED && row.running()) return false;
            if (!account.isEmpty() && row.accounts().stream().noneMatch(name -> contains(name, account))) return false;
            if (!app.isEmpty() && row.wanted().games().stream().map(Sourced::value)
                    .noneMatch(game -> contains(game.label(), app) || contains(game.packageName(), app))) return false;
            if (!tag.isEmpty() && row.tags().stream().noneMatch(tag::equalsIgnoreCase)) return false;
            return !differs || row.differs();
        }

        /** The filter as a saved filter keeps it: only the parts that narrow. */
        public Map<String, String> values() {
            Map<String, String> out = new LinkedHashMap<>();
            if (!text.isEmpty()) out.put("text", text);
            if (emulator != null) out.put("emulator", emulator.id());
            if (state != Running.ANY) out.put("state", state.id());
            if (!account.isEmpty()) out.put("account", account);
            if (!app.isEmpty()) out.put("app", app);
            if (!tag.isEmpty()) out.put("tag", tag);
            if (differs) out.put("differs", "true");
            return out;
        }

        /** The filter {@link #values()} wrote; a key or value this build does not know narrows nothing. */
        public static Filter of(Map<String, String> values) {
            return new Filter(values.get("text"), values.containsKey("emulator")
                    ? PlatformId.fromId(values.get("emulator")) : null, Running.fromId(values.get("state")),
                    values.get("account"), values.get("app"), values.get("tag"),
                    Boolean.parseBoolean(values.get("differs")));
        }

        public SavedFilters.SavedFilter saved(String name) {
            return new SavedFilters.SavedFilter(name, SavedFilters.Tab.INSTANCES, values());
        }

        private static String clean(String words) {
            return words == null ? "" : words.strip();
        }

        private static boolean contains(String in, String words) {
            return words.isEmpty() || in.toLowerCase(Locale.ROOT).contains(words.toLowerCase(Locale.ROOT));
        }
    }

    public static List<InstanceRow> filtered(List<InstanceRow> rows, Filter filter) {
        return rows.stream().filter(filter::keeps).toList();
    }

    // ---- tags ----

    /** {@code typed} as tags: split at commas, blanks dropped, each once, case aside, in the order typed. */
    public static List<String> tags(String typed) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String tag : (typed == null ? "" : typed).split(",")) {
            String one = tag.strip();
            if (!one.isEmpty() && seen.add(one.toLowerCase(Locale.ROOT))) out.add(one);
        }
        return out;
    }

    static String count(int n, String thing) {
        return n + " " + thing + (n == 1 ? "" : "s");
    }
}
