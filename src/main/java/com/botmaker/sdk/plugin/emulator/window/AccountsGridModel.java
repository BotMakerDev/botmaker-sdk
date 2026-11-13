package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.botmaker.shared.emulator.config.Sourced;
import com.botmaker.shared.emulator.config.Wanted;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the Emulators window's Accounts tab draws, with no JavaFX (board Grid, spec
 * {@code 2026-11-13-google-accounts-design.md}): a row per Google account of the book, a column per instance, each
 * cell what the configuration wants against what Android said. A tick writes the instance's own layer only, never its
 * emulator's: unticking an account the emulator lists writes <i>removed here</i>.
 */
final class AccountsGridModel {

    private AccountsGridModel() {}

    /** One cell: whether the instance should hold the account, and what it holds now. */
    enum Cell {
        SIGNED_IN("signed_in", "Signed in", true),
        TO_SIGN_IN("to_sign_in", "To sign in", true),
        NOT_READ("not_read", "Not read", true),
        TO_SIGN_OUT("to_sign_out", "To sign out", false),
        REMOVED_HERE("removed_here", "Removed here", false),
        /** Signed in on the instance though no layer lists it: Apply leaves it. */
        UNLISTED("unlisted", "Signed in, not listed", false),
        NONE("none", "", false),
        UNKNOWN("unknown", "", false);

        private final String id;
        private final String displayName;
        private final boolean ticked;

        Cell(String id, String displayName, boolean ticked) {
            this.id = id;
            this.displayName = displayName;
            this.ticked = ticked;
        }

        String id() {
            return id;
        }

        String displayName() {
            return displayName;
        }

        /** Whether the configuration wants the account on the instance. */
        boolean ticked() {
            return ticked;
        }

        static Cell fromId(String id) {
            for (Cell cell : values()) {
                if (cell.id.equals(id)) return cell;
            }
            return UNKNOWN;
        }
    }

    /**
     * Which columns and rows show.
     *
     * @param emulator one emulator's instances, or null for every one
     * @param tag      the instances carrying it, or empty for any
     * @param account  one account's row, as the Accounts window asks, or null for every one
     */
    record Filter(PlatformId emulator, String tag, AccountRecord.Key account) {

        static final Filter NONE = new Filter(null, "", null);

        Filter {
            tag = tag == null ? "" : tag.strip();
        }

        Filter withAccount(AccountRecord.Key key) {
            return new Filter(emulator, tag, key);
        }
    }

    /** The instances shown as columns, by emulator then name. */
    static List<EmulatorsModel.InstanceRow> columns(List<EmulatorsModel.InstanceRow> instances, Filter filter) {
        return instances.stream()
                .filter(row -> filter.emulator() == null || row.key().platform() == filter.emulator())
                .filter(row -> filter.tag().isEmpty()
                        || row.tags().stream().anyMatch(filter.tag()::equalsIgnoreCase))
                .sorted(Comparator.<EmulatorsModel.InstanceRow, Integer>comparing(row -> row.key().platform().ordinal())
                        .thenComparing(row -> row.key().reference(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The Google accounts shown as rows, by name; the filter's one only when it names one. */
    static List<AccountRecord> rows(List<AccountRecord> book, Filter filter) {
        return book.stream()
                .filter(account -> account.kind() == AccountRecord.Kind.GOOGLE)
                .filter(account -> filter.account() == null || account.key().equals(filter.account()))
                .sorted(Comparator.comparing(AccountRecord::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The cell of {@code account} on {@code row}'s instance, from the configuration and Android's last answer. */
    static Cell cell(EmulatorConfig config, EmulatorsModel.InstanceRow row, AccountRecord account) {
        AccountRecord.Key key = account.key();
        Wanted wanted = config.wanted(row.key());
        Optional<Set<String>> held = row.android().flatMap(AndroidState::googleAccounts);
        boolean holds = held.isPresent() && held.get().contains(key.name());
        if (wanted.accounts().stream().map(Sourced::value).anyMatch(a -> a.key().equals(key))) {
            return held.isEmpty() ? Cell.NOT_READ : holds ? Cell.SIGNED_IN : Cell.TO_SIGN_IN;
        }
        if (wanted.removedAccounts().contains(key)) return holds ? Cell.TO_SIGN_OUT : Cell.REMOVED_HERE;
        return holds ? Cell.UNLISTED : Cell.NONE;
    }

    /** Whether the instance's emulator lists the account, so the instance has it unless it removes it. */
    static boolean inherited(EmulatorConfig config, InstanceKey key, AccountRecord account) {
        return config.emulator(key.platform()).accounts().stream().anyMatch(a -> a.key().equals(account.key()));
    }

    /**
     * The configuration with the tick written into the instance's own layer.
     *
     * <ul>
     *   <li>Ticked: an account its emulator lists is taken back from the emulator, else it is listed here.</li>
     *   <li>Unticked: an account its emulator lists, or that the instance holds or was not read, is <i>removed
     *       here</i>, so Apply signs it out; any other is simply no longer listed.</li>
     * </ul>
     *
     * @param held the instance's Google accounts as Android last said, empty when it did not answer
     */
    static EmulatorConfig tick(EmulatorConfig config, InstanceKey key, AccountRecord account, boolean on,
                               Optional<Set<String>> held) {
        Layer own = config.instance(key);
        boolean fromEmulator = inherited(config, key, account);
        Layer next;
        if (on) {
            next = fromEmulator ? own.inheritingAccount(account.key()) : own.withAccount(account);
        } else {
            boolean mayHold = held.isEmpty() || held.get().contains(account.key().name());
            next = fromEmulator || mayHold ? own.removingAccount(account.key()) : own.inheritingAccount(account.key());
        }
        return config.withInstance(key, next);
    }

    /** How many Google accounts the instance should hold: the count row. */
    static int count(EmulatorConfig config, InstanceKey key) {
        return (int) config.wanted(key).accounts().stream()
                .filter(account -> account.value().kind() == AccountRecord.Kind.GOOGLE).count();
    }

    /**
     * The tab's Apply: per instance, only its sign-ins and sign-outs of the accounts shown, never a setting or a game,
     * which the Instances tab's Apply runs.
     */
    static List<ApplyPlan.Item> toApply(List<EmulatorsModel.InstanceRow> columns, List<AccountRecord> shown) {
        Set<AccountRecord.Key> keys = shown.stream().map(AccountRecord::key).collect(Collectors.toSet());
        List<ApplyPlan.Item> items = new ArrayList<>();
        for (EmulatorsModel.InstanceRow row : columns) {
            if (row.diff() == null) continue;
            List<ConfigDiff.AccountChange> accounts = row.diff().accounts().stream()
                    .filter(change -> keys.contains(change.account().key())).toList();
            if (!accounts.isEmpty()) items.add(new ApplyPlan.Item(row, List.of(), accounts, List.of()));
        }
        return items;
    }

    /** How many sign-ins and sign-outs the tab's Apply would run. */
    static int changes(List<ApplyPlan.Item> items) {
        return items.stream().mapToInt(item -> item.accounts().size()).sum();
    }

    /** One cell ticked: an account on an instance. */
    record Ticked(InstanceKey instance, AccountRecord.Key account) {}

    /**
     * <i>Undo ticks</i>: each ticked cell's account as the instance's own layer held it before its first tick, listed,
     * removed or neither; every other account and the rest of the layer stay as they are now.
     *
     * @param before the instance's own layer before each cell's first tick
     */
    static EmulatorConfig undo(EmulatorConfig config, Map<Ticked, Layer> before) {
        EmulatorConfig out = config;
        for (Map.Entry<Ticked, Layer> was : before.entrySet()) {
            InstanceKey key = was.getKey().instance();
            AccountRecord.Key account = was.getKey().account();
            Layer next = out.instance(key).inheritingAccount(account);
            Optional<AccountRecord> own = was.getValue().accounts().stream()
                    .filter(a -> a.key().equals(account)).findFirst();
            if (own.isPresent()) next = next.withAccount(own.get());
            else if (was.getValue().removedAccounts().contains(account)) next = next.removingAccount(account);
            out = out.withInstance(key, next);
        }
        return out;
    }
}
