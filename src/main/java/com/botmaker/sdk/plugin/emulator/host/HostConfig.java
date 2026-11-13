package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.PluginState;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountBook;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.ConfigField;
import com.botmaker.shared.emulator.config.ConfigValue;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.botmaker.shared.tools.UserDirs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * This PC's emulator configuration, between the SDK's values of this PC ({@link SdkHostValues}) and shared's model
 * ({@link EmulatorConfig}). Reading turns the rows into layers; writing turns the layers back into rows and keeps
 * every row this build does not understand (a product, a field or an account kind a newer BotMaker wrote), so an
 * older BotMaker never erases a newer one's data.
 */
public final class HostConfig {

    private HostConfig() {}

    /** The seven values as they are written. */
    public record Rows(HostSettings emulatorSettings, HostGames emulatorGames, HostSettings instanceSettings,
                       HostGames instanceGames, HostLabels tags, HostLabels usedBy, HostAccounts accounts) {

        public static final Rows NONE = new Rows(HostSettings.none(), HostGames.none(), HostSettings.none(),
                HostGames.none(), HostLabels.none(), HostLabels.none(), HostAccounts.none());
    }

    /**
     * What the window reads.
     *
     * @param usedBy the bots seen launching each instance, in the order they were first seen
     */
    public record State(EmulatorConfig config, Map<InstanceKey, List<String>> usedBy) {

        public static final State EMPTY = new State(EmulatorConfig.EMPTY, Map.of());

        public List<String> usedBy(InstanceKey key) {
            return usedBy.getOrDefault(key, List.of());
        }
    }

    // ---- the folder ----

    /**
     * Where the window keeps what is not a value (saved filters, the applies log): {@code state/} in the SDK's folder
     * of this PC, beside the classes the host writes.
     */
    public static Path stateDir() {
        return UserDirs.config().resolve("plugins").resolve(PluginState.part("com.github.BotMakerDev"))
                .resolve(PluginState.part("botmaker-sdk")).resolve("state");
    }

    /** This PC's account book, for every emulator and every bot: {@code accounts.json} in {@link #stateDir()}. */
    public static AccountBook accounts() {
        return new AccountBook(stateDir());
    }

    // ---- through the host ----

    /** The configuration on this PC; empty when nothing was written yet or the host keeps no values of this PC. */
    public static State load(StudioServices services) {
        Rows rows = rows(services);
        return new State(config(rows), usedBy(rows.usedBy()));
    }

    /**
     * Changes the configuration as it is written now.
     *
     * @return empty when written, else why not, in the host's words
     */
    public static Optional<String> update(StudioServices services, UnaryOperator<EmulatorConfig> change) {
        return step(services, "Change this PC's emulators", () -> {
            Rows before = rows(services);
            Rows next = rows(change.apply(config(before)), before);
            return write(services, before, next);
        });
    }

    /**
     * Remembers that {@code bot} launches {@code targets}: it is added to those instances, and taken off the others
     * among {@code known}. An instance not in {@code known} (its emulator was not found this time) keeps the bot.
     * Nothing is written when that is already so.
     */
    public static Optional<String> seen(StudioServices services, String bot, Collection<InstanceKey> targets,
                                        Collection<InstanceKey> known) {
        return step(services, SEEN + bot + " launches",() -> {
            Rows before = rows(services);
            HostLabels next = seen(before.usedBy(), bot, targets, known);
            return write(services, before, new Rows(before.emulatorSettings(), before.emulatorGames(),
                    before.instanceSettings(), before.instanceGames(), before.tags(), next, before.accounts()));
        });
    }

    /**
     * Moves an instance's rows to its new name after the instance was renamed: its settings, games, accounts, tags
     * and the bots seen launching it, with the rows this build does not understand. Rows the new name had are
     * replaced.
     */
    public static Optional<String> rename(StudioServices services, InstanceKey from, InstanceKey to) {
        return step(services, RENAMED,() -> {
            Rows before = rows(services);
            Optional<String> refused = write(services, before, renamed(before, from, to));
            if (refused.isPresent()) return refused;
            HostLabels renames = renames(services);
            return write(services, SdkHostValues.INSTANCE_RENAMES, HostLabels.class, renames,
                    InstanceRenames.recorded(renames, from, to));
        });
    }

    /**
     * Forgets an instance that was deleted: its rows, the bots seen launching it, and the renames that led to it. A
     * bot that still names it is told so by its checks ("no instance").
     */
    public static Optional<String> forget(StudioServices services, InstanceKey key) {
        return step(services, FORGOTTEN,() -> {
            Rows before = rows(services);
            Optional<String> refused = write(services, before, dropped(before, key));
            if (refused.isPresent()) return refused;
            HostLabels renames = renames(services);
            return write(services, SdkHostValues.INSTANCE_RENAMES, HostLabels.class, renames,
                    InstanceRenames.dropped(renames, key));
        });
    }

    /** The instances renamed on this PC, each row old key → new key ({@link InstanceRenames}). */
    public static HostLabels renames(StudioServices services) {
        return read(services, SdkHostValues.INSTANCE_RENAMES, HostLabels.class, HostLabels.none());
    }

    /** One change at a time in this process: each reads the values, changes them and writes them back. */
    private static final Object WRITES = new Object();

    private static final String RENAMED = "Rename an instance";
    private static final String FORGOTTEN = "Forget an instance";
    private static final String SEEN = "Note the instances ";

    /**
     * Whether the step {@code label} names followed what happened to an emulator (an instance renamed or deleted, a bot
     * seen launching one): taking it back would put the values out of step with the emulator, so ↶ stops there.
     */
    public static boolean followsTheEmulator(String label) {
        return label != null && (label.equals(RENAMED) || label.equals(FORGOTTEN) || label.startsWith(SEEN));
    }

    /**
     * {@code writes} under {@link #WRITES}, as one step of this PC's undo named {@code label}: one ↶ takes back what one
     * change did, however many values it wrote.
     */
    private static Optional<String> step(StudioServices services, String label, Supplier<Optional<String>> writes) {
        synchronized (WRITES) {
            if (services == null) return writes.get();
            AtomicReference<Optional<String>> written = new AtomicReference<>(Optional.empty());
            services.hostHistory().batch(label, () -> written.set(writes.get()));
            return written.get();
        }
    }

    private static Rows rows(StudioServices services) {
        return new Rows(read(services, SdkHostValues.EMULATOR_SETTINGS, HostSettings.class, HostSettings.none()),
                read(services, SdkHostValues.EMULATOR_GAMES, HostGames.class, HostGames.none()),
                read(services, SdkHostValues.INSTANCE_SETTINGS, HostSettings.class, HostSettings.none()),
                read(services, SdkHostValues.INSTANCE_GAMES, HostGames.class, HostGames.none()),
                read(services, SdkHostValues.INSTANCE_TAGS, HostLabels.class, HostLabels.none()),
                read(services, SdkHostValues.INSTANCE_USED_BY, HostLabels.class, HostLabels.none()),
                read(services, SdkHostValues.ACCOUNTS, HostAccounts.class, HostAccounts.none()));
    }

    private static <T> T read(StudioServices services, ManagedMethod<T> value, Class<T> type, T none) {
        try {
            return services == null ? none
                    : services.hostValues().open(value).flatMap(ctx -> ctx.value(type)).orElse(none);
        } catch (RuntimeException unreadable) {
            return none;
        }
    }

    /** Writes the values that changed, stopping at the first the host refuses. */
    private static Optional<String> write(StudioServices services, Rows before, Rows next) {
        if (services == null) return Optional.of("BotMaker keeps no values of this PC here.");
        Optional<String> refused = write(services, SdkHostValues.EMULATOR_SETTINGS, HostSettings.class,
                before.emulatorSettings(), next.emulatorSettings());
        if (refused.isEmpty()) {
            refused = write(services, SdkHostValues.EMULATOR_GAMES, HostGames.class, before.emulatorGames(),
                    next.emulatorGames());
        }
        if (refused.isEmpty()) {
            refused = write(services, SdkHostValues.INSTANCE_SETTINGS, HostSettings.class, before.instanceSettings(),
                    next.instanceSettings());
        }
        if (refused.isEmpty()) {
            refused = write(services, SdkHostValues.INSTANCE_GAMES, HostGames.class, before.instanceGames(),
                    next.instanceGames());
        }
        if (refused.isEmpty()) {
            refused = write(services, SdkHostValues.INSTANCE_TAGS, HostLabels.class, before.tags(), next.tags());
        }
        if (refused.isEmpty()) {
            refused = write(services, SdkHostValues.INSTANCE_USED_BY, HostLabels.class, before.usedBy(),
                    next.usedBy());
        }
        if (refused.isEmpty()) {
            refused = write(services, SdkHostValues.ACCOUNTS, HostAccounts.class, before.accounts(), next.accounts());
        }
        return refused;
    }

    /**
     * Writes one value that changed. A value that is there but does not read is left alone, never written over as if
     * it were empty; and the write is read back, since the host says a refusal of its own (a file that would not
     * compile, a lock, an older version) only in its log.
     */
    private static <T> Optional<String> write(StudioServices services, ManagedMethod<T> value, Class<T> type,
                                              T before, T next) {
        if (next.equals(before)) return Optional.empty();
        String name = value.id().toLowerCase(Locale.ROOT).replace('_', ' ');
        try {
            Optional<ValueContext> ctx = services.hostValues().open(value);
            if (ctx.isEmpty()) {
                Optional<String> refused = services.hostValues().create(value);
                if (refused.isPresent()) return refused;
                ctx = services.hostValues().open(value);
                if (ctx.isEmpty()) return Optional.of("BotMaker could not open its values of this PC.");
            } else if (ctx.get().value(type).isEmpty()) {
                return Optional.of("This PC's " + name + " could not be read, so nothing was written over them. "
                        + "Look at the file in BotMaker's folder of this PC.");
            }
            Optional<String> refused = ctx.get().write(next);
            if (refused.isPresent()) return refused;
            boolean written = services.hostValues().open(value).flatMap(now -> now.value(type))
                    .map(next::equals).orElse(false);
            return written ? Optional.empty()
                    : Optional.of("This PC's " + name + " were not written; BotMaker's log says why.");
        } catch (RuntimeException failed) {
            return Optional.of("BotMaker could not write its values of this PC: " + failed.getMessage());
        }
    }

    // ---- rows to the model ----

    /** The layers the rows say; a row this build does not understand says nothing. */
    static EmulatorConfig config(Rows rows) {
        Map<String, Layer> layers = new LinkedHashMap<>();
        for (HostSettings.Setting row : concat(rows.emulatorSettings().list(), rows.instanceSettings().list())) {
            ConfigValue value = value(row);
            if (value != null && owner(row.of()) != null) {
                layers.merge(row.of(), Layer.EMPTY.with(ConfigField.fromId(row.field()), value),
                        (layer, one) -> layer.with(ConfigField.fromId(row.field()), value));
            }
        }
        for (HostGames.Game row : concat(rows.emulatorGames().list(), rows.instanceGames().list())) {
            if (!understood(row)) continue;
            Layer layer = layers.getOrDefault(row.of(), Layer.EMPTY);
            layers.put(row.of(), row.removed() ? layer.removingGame(row.packageName())
                    : layer.withGame(new GameRecord(row.packageName(), row.label())));
        }
        for (HostAccounts.Account row : rows.accounts().list()) {
            if (!understood(row)) continue;
            Layer layer = layers.getOrDefault(row.of(), Layer.EMPTY);
            AccountRecord account = new AccountRecord(AccountRecord.Kind.fromId(row.kind()), row.name(), row.note());
            layer = row.removed() ? layer.removingAccount(account.key()) : layer.withAccount(account);
            if (row.first() && !row.removed() && account.kind() == AccountRecord.Kind.GOOGLE) {
                layer = layer.withDefaultGoogle(account.name());
            }
            layers.put(row.of(), layer);
        }
        Map<String, List<String>> tags = new LinkedHashMap<>();
        for (HostLabels.Label row : rows.tags().list()) {
            if (understood(row)) tags.computeIfAbsent(row.instance(), key -> new ArrayList<>()).add(row.text());
        }
        tags.forEach((instance, words) -> layers.put(instance,
                layers.getOrDefault(instance, Layer.EMPTY).withTags(words)));

        Map<PlatformId, Layer> emulators = new EnumMap<>(PlatformId.class);
        Map<InstanceKey, Layer> instances = new LinkedHashMap<>();
        layers.forEach((of, layer) -> {
            if (owner(of) instanceof InstanceKey key) {
                instances.put(key, withoutEmulatorOnly(layer));
            } else if (owner(of) instanceof PlatformId platform) {
                emulators.put(platform, layer);
            }
        });
        return new EmulatorConfig(emulators, instances);
    }

    static Map<InstanceKey, List<String>> usedBy(HostLabels rows) {
        Map<InstanceKey, List<String>> out = new LinkedHashMap<>();
        for (HostLabels.Label row : rows.list()) {
            if (!understood(row)) continue;
            List<String> bots = out.computeIfAbsent(InstanceKey.parse(row.instance()), key -> new ArrayList<>());
            if (!bots.contains(row.text())) bots.add(row.text());
        }
        return out;
    }

    // ---- the model to rows ----

    /** {@code config} as rows, then every row of {@code before} this build does not understand, kept. */
    static Rows rows(EmulatorConfig config, Rows before) {
        List<HostSettings.Setting> emulatorSettings = new ArrayList<>();
        List<HostGames.Game> emulatorGames = new ArrayList<>();
        List<HostSettings.Setting> instanceSettings = new ArrayList<>();
        List<HostGames.Game> instanceGames = new ArrayList<>();
        List<HostLabels.Label> tags = new ArrayList<>();
        List<HostAccounts.Account> accounts = new ArrayList<>();
        config.emulators().forEach((platform, layer) ->
                rows(platform.id(), layer, emulatorSettings, emulatorGames, accounts));
        config.instances().forEach((key, layer) -> {
            rows(key.text(), layer, instanceSettings, instanceGames, accounts);
            layer.tags().forEach(tag -> tags.add(new HostLabels.Label(key.text(), tag)));
        });
        before.emulatorSettings().list().stream().filter(row -> owner(row.of()) == null || value(row) == null)
                .forEach(emulatorSettings::add);
        before.instanceSettings().list().stream().filter(row -> owner(row.of()) == null || value(row) == null)
                .forEach(instanceSettings::add);
        before.emulatorGames().list().stream().filter(row -> !understood(row)).forEach(emulatorGames::add);
        before.instanceGames().list().stream().filter(row -> !understood(row)).forEach(instanceGames::add);
        before.tags().list().stream().filter(row -> !understood(row)).forEach(tags::add);
        before.accounts().list().stream().filter(row -> !understood(row)).forEach(accounts::add);
        return new Rows(new HostSettings(emulatorSettings), new HostGames(emulatorGames),
                new HostSettings(instanceSettings), new HostGames(instanceGames), new HostLabels(tags),
                before.usedBy(), new HostAccounts(accounts));
    }

    private static void rows(String of, Layer layer, List<HostSettings.Setting> settings, List<HostGames.Game> games,
                             List<HostAccounts.Account> accounts) {
        layer.fields().forEach((field, value) ->
                settings.add(new HostSettings.Setting(of, field.id(), value.written())));
        layer.games().forEach(game -> games.add(new HostGames.Game(of, game.packageName(), game.label(), false)));
        layer.removedGames().forEach(packageName -> games.add(new HostGames.Game(of, packageName, "", true)));
        String first = layer.defaultGoogle();
        boolean firstWritten = first == null;
        for (AccountRecord account : layer.accounts()) {
            boolean isFirst = account.kind() == AccountRecord.Kind.GOOGLE && account.name().equalsIgnoreCase(first);
            firstWritten |= isFirst;
            accounts.add(new HostAccounts.Account(of, account.kind().id(), account.name(), account.note(), false,
                    isFirst));
        }
        // the first Google account is one the emulator's layer lists: it is listed here too, to carry the mark
        if (!firstWritten) {
            accounts.add(new HostAccounts.Account(of, AccountRecord.Kind.GOOGLE.id(), first, "", false, true));
        }
        layer.removedAccounts().forEach(key ->
                accounts.add(new HostAccounts.Account(of, key.kind().id(), key.name(), "", true, false)));
    }

    /**
     * {@code rows} with {@code bot} on {@code targets} and off the other instances of {@code known}; its rows on
     * instances not in {@code known}, and the other bots' rows, as they were.
     */
    static HostLabels seen(HostLabels rows, String bot, Collection<InstanceKey> targets,
                           Collection<InstanceKey> known) {
        List<HostLabels.Label> next = new ArrayList<>();
        Set<String> wanted = new LinkedHashSet<>();
        targets.forEach(key -> wanted.add(key.text()));
        Set<String> found = new LinkedHashSet<>();
        known.forEach(key -> found.add(key.text()));
        for (HostLabels.Label row : rows.list()) {
            if (!row.text().equals(bot) || wanted.remove(row.instance()) || !found.contains(row.instance())) {
                next.add(row);
            }
        }
        wanted.forEach(instance -> next.add(new HostLabels.Label(instance, bot)));
        return new HostLabels(next);
    }

    /** {@code rows} with every row of {@code from} now of {@code to}, and the rows {@code to} had gone. */
    static Rows renamed(Rows rows, InstanceKey from, InstanceKey to) {
        if (from.equals(to)) return rows;
        String old = from.text();
        String now = to.text();
        return new Rows(rows.emulatorSettings(), rows.emulatorGames(),
                new HostSettings(moved(rows.instanceSettings().list(), HostSettings.Setting::of, old, now,
                        row -> new HostSettings.Setting(now, row.field(), row.value()))),
                new HostGames(moved(rows.instanceGames().list(), HostGames.Game::of, old, now,
                        row -> new HostGames.Game(now, row.packageName(), row.label(), row.removed()))),
                new HostLabels(moved(rows.tags().list(), HostLabels.Label::instance, old, now,
                        row -> new HostLabels.Label(now, row.text()))),
                new HostLabels(moved(rows.usedBy().list(), HostLabels.Label::instance, old, now,
                        row -> new HostLabels.Label(now, row.text()))),
                new HostAccounts(moved(rows.accounts().list(), HostAccounts.Account::of, old, now,
                        row -> new HostAccounts.Account(now, row.kind(), row.name(), row.note(), row.removed(),
                                row.first()))));
    }

    /** {@code rows} with every row of {@code key} gone. */
    static Rows dropped(Rows rows, InstanceKey key) {
        String gone = key.text();
        return new Rows(rows.emulatorSettings(), rows.emulatorGames(),
                new HostSettings(without(rows.instanceSettings().list(), HostSettings.Setting::of, gone)),
                new HostGames(without(rows.instanceGames().list(), HostGames.Game::of, gone)),
                new HostLabels(without(rows.tags().list(), HostLabels.Label::instance, gone)),
                new HostLabels(without(rows.usedBy().list(), HostLabels.Label::instance, gone)),
                new HostAccounts(without(rows.accounts().list(), HostAccounts.Account::of, gone)));
    }

    private static <T> List<T> without(List<T> rows, Function<T, String> of, String gone) {
        return rows.stream().filter(row -> !of.apply(row).equals(gone)).toList();
    }

    private static <T> List<T> moved(List<T> rows, Function<T, String> of, String old, String now,
                                     UnaryOperator<T> renamed) {
        List<T> out = new ArrayList<>();
        for (T row : rows) {
            if (of.apply(row).equals(old)) {
                out.add(renamed.apply(row));
            } else if (!of.apply(row).equals(now)) {
                out.add(row);
            }
        }
        return out;
    }

    // ---- what this build understands ----

    /** The emulator ({@link PlatformId}) or instance ({@link InstanceKey}) a row is of, or null for neither. */
    private static Object owner(String of) {
        if (of.contains("/")) return InstanceKey.parse(of);
        PlatformId platform = PlatformId.fromId(of);
        return platform != PlatformId.UNKNOWN && platform.id().equals(of) ? platform : null;
    }

    private static ConfigValue value(HostSettings.Setting row) {
        ConfigField field = ConfigField.fromId(row.field());
        return field == ConfigField.UNKNOWN ? null : ConfigValue.read(field.kind(), row.value());
    }

    private static boolean understood(HostGames.Game row) {
        return owner(row.of()) != null && !row.packageName().isBlank();
    }

    private static boolean understood(HostAccounts.Account row) {
        return owner(row.of()) != null && !row.name().isBlank()
                && AccountRecord.Kind.fromId(row.kind()) != AccountRecord.Kind.UNKNOWN;
    }

    private static boolean understood(HostLabels.Label row) {
        return InstanceKey.parse(row.instance()) != null && !row.text().isBlank();
    }

    private static Layer withoutEmulatorOnly(Layer layer) {
        Layer out = layer;
        for (ConfigField field : layer.fields().keySet()) {
            if (field.emulatorOnly()) out = out.without(field);
        }
        return out;
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> out = new ArrayList<>(first);
        out.addAll(second);
        return out;
    }
}
