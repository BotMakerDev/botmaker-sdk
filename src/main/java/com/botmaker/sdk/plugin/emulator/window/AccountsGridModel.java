// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.sdk.plugin.emulator.host.HostBook;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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

    /**
     * The cell of {@code account} on {@code row}'s instance, from the configuration and Android's last answer; a
     * layer or Android naming any of the account's addresses names the account.
     */
    static Cell cell(EmulatorConfig config, EmulatorsModel.InstanceRow row, AccountRecord account) {
        Wanted wanted = config.wanted(row.key());
        Optional<Set<String>> held = row.android().flatMap(AndroidState::googleAccounts);
        boolean holds = held.isPresent() && holds(held.get(), account);
        if (wanted.accounts().stream().map(Sourced::value).anyMatch(a -> answers(account, a.key()))) {
            return held.isEmpty() ? Cell.NOT_READ : holds ? Cell.SIGNED_IN : Cell.TO_SIGN_IN;
        }
        if (wanted.removedAccounts().stream().anyMatch(k -> answers(account, k))) {
            return holds ? Cell.TO_SIGN_OUT : Cell.REMOVED_HERE;
        }
        return holds ? Cell.UNLISTED : Cell.NONE;
    }

    /** Whether {@code key} is one of {@code account}'s addresses, of its kind. */
    private static boolean answers(AccountRecord account, AccountRecord.Key key) {
        return key.kind() == account.kind() && account.answersTo(key.name());
    }

    /** Whether Android's list, lower case, holds any of the account's addresses. */
    private static boolean holds(Set<String> held, AccountRecord account) {
        return account.addresses().stream().map(a -> a.toLowerCase(Locale.ROOT)).anyMatch(held::contains);
    }

    /**
     * Whether the instance's emulator lists the account by any of its addresses, so the instance has it unless it
     * removes it.
     */
    static boolean inherited(EmulatorConfig config, InstanceKey key, AccountRecord account) {
        return config.emulator(key.platform()).accounts().stream().anyMatch(a -> answers(account, a.key()));
    }

    /**
     * The address a tick of {@code account} on the instance writes: the one its layers name it by (the instance's own,
     * then its emulator's), else the one Android holds it under, else its main one. Writing another would have Apply
     * sign it in a second time.
     */
    static AccountRecord.Key named(EmulatorConfig config, InstanceKey key, AccountRecord account,
                                   Optional<Set<String>> held) {
        Layer own = config.instance(key);
        Layer emulator = config.emulator(key.platform());
        return Stream.of(own.accounts().stream().map(AccountRecord::key), own.removedAccounts().stream(),
                        emulator.accounts().stream().map(AccountRecord::key))
                .flatMap(keys -> keys).filter(k -> answers(account, k)).findFirst()
                .or(() -> held.flatMap(names -> account.addresses().stream()
                                .filter(a -> names.contains(a.toLowerCase(Locale.ROOT))).findFirst())
                        .map(address -> new AccountRecord.Key(account.kind(), address)))
                .orElse(account.key());
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
        AccountRecord.Key named = named(config, key, account, held);
        Layer next;
        if (on) {
            next = fromEmulator ? own.inheritingAccount(named)
                    : own.withAccount(named.equals(account.key()) ? account
                    : new AccountRecord(account.kind(), named.name()));
        } else {
            boolean mayHold = held.isEmpty() || holds(held.get(), account);
            next = fromEmulator || mayHold ? own.removingAccount(named) : own.inheritingAccount(named);
        }
        return config.withInstance(key, next);
    }

    /** One cell to tick or untick, as a row's, a column's or <i>One account per instance</i>'s shortcut asks. */
    record Change(EmulatorsModel.InstanceRow column, AccountRecord account, boolean on) {}

    /** {@code account} ticked or unticked on every column shown; cells already so are left out. */
    static List<Change> row(EmulatorConfig config, List<EmulatorsModel.InstanceRow> columns, AccountRecord account,
                            boolean on) {
        return columns.stream().filter(column -> cell(config, column, account).ticked() != on)
                .map(column -> new Change(column, account, on)).toList();
    }

    /** Every account shown ticked or unticked on {@code column}; cells already so are left out. */
    static List<Change> column(EmulatorConfig config, EmulatorsModel.InstanceRow column, List<AccountRecord> rows,
                               boolean on) {
        return rows.stream().filter(account -> cell(config, column, account).ticked() != on)
                .map(account -> new Change(column, account, on)).toList();
    }

    /**
     * <i>One account per instance</i>: each column shown gets one account shown, no two the same, the rest of the shown
     * accounts unticked on it. A column that already has an account no column before it took keeps it, so as few
     * sign-ins as can be are asked; the others get the next free account in row order. Columns past the last free
     * account are left as they are.
     */
    static List<Change> spread(EmulatorConfig config, List<EmulatorsModel.InstanceRow> columns,
                               List<AccountRecord> rows) {
        Map<InstanceKey, AccountRecord> paired = new LinkedHashMap<>();
        Set<AccountRecord.Key> taken = new HashSet<>();
        for (EmulatorsModel.InstanceRow column : columns) {
            rows.stream().filter(account -> cell(config, column, account).ticked())
                    .filter(account -> !taken.contains(account.key())).findFirst().ifPresent(kept -> {
                        paired.put(column.key(), kept);
                        taken.add(kept.key());
                    });
        }
        List<AccountRecord> free = new ArrayList<>(rows.stream().filter(a -> !taken.contains(a.key())).toList());
        for (EmulatorsModel.InstanceRow column : columns) {
            if (!paired.containsKey(column.key()) && !free.isEmpty()) paired.put(column.key(), free.removeFirst());
        }
        List<Change> out = new ArrayList<>();
        for (EmulatorsModel.InstanceRow column : columns) {
            AccountRecord mine = paired.get(column.key());
            if (mine == null) continue;
            for (AccountRecord account : rows) {
                boolean on = account.key().equals(mine.key());
                if (cell(config, column, account).ticked() != on) out.add(new Change(column, account, on));
            }
        }
        return out;
    }

    /** The configuration with each change ticked in turn ({@link #tick}). */
    static EmulatorConfig tick(EmulatorConfig config, List<Change> changes) {
        EmulatorConfig out = config;
        for (Change change : changes) {
            out = tick(out, change.column().key(), change.account(), change.on(),
                    change.column().android().flatMap(AndroidState::googleAccounts));
        }
        return out;
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
        List<ApplyPlan.Item> items = new ArrayList<>();
        for (EmulatorsModel.InstanceRow row : columns) {
            if (row.diff() == null) continue;
            List<ConfigDiff.AccountChange> accounts = row.diff().accounts().stream()
                    .filter(change -> shown.stream().anyMatch(one -> answers(one, change.account().key()))).toList();
            if (!accounts.isEmpty()) items.add(new ApplyPlan.Item(row, List.of(), accounts, List.of()));
        }
        return items;
    }

    /** How many sign-ins and sign-outs the tab's Apply would run: its button's N. */
    static int applyCount(List<ApplyPlan.Item> items) {
        return items.stream().mapToInt(item -> item.accounts().size()).sum();
    }

    /** What the tab's Apply would run, counted for the inspector. */
    record Pending(int signIns, int signOuts, int instances) {

        String said() {
            if (signIns + signOuts == 0) return "Nothing to apply";
            return EmulatorsModel.count(signIns, "sign-in") + ", " + EmulatorsModel.count(signOuts, "sign-out")
                    + " across " + EmulatorsModel.count(instances, "instance");
        }
    }

    /** The sign-ins, sign-outs and instances of the tab's Apply ({@link #toApply}). */
    static Pending pending(List<ApplyPlan.Item> items) {
        int in = 0;
        int out = 0;
        for (ApplyPlan.Item item : items) {
            for (ConfigDiff.AccountChange change : item.accounts()) {
                if (change.op() == ConfigDiff.AccountChange.Op.ADD) in++;
                else out++;
            }
        }
        return new Pending(in, out, items.size());
    }

    /** How a cell is drawn: its mark and the words its tooltip and the legend give. */
    record Glyph(String mark, String words) {}

    /** {@code cell}'s glyph; its style class is {@link #styleClass}. */
    static Glyph glyph(Cell cell) {
        return switch (cell) {
            case SIGNED_IN -> new Glyph("✓", "Wanted and signed in");
            case TO_SIGN_IN -> new Glyph("+", "Wanted; Apply signs it in");
            case NOT_READ -> new Glyph("✓", "Wanted; the instance was not read");
            case TO_SIGN_OUT -> new Glyph("−", "Apply signs it out");
            case REMOVED_HERE -> new Glyph("−", "Removed here, on this instance only; Apply signs it out if it is"
                    + " there");
            case UNLISTED -> new Glyph("●", "Signed in, not listed: Apply leaves it");
            case NONE, UNKNOWN -> new Glyph("", "Not on it");
        };
    }

    /** The sheet's class for {@code cell}'s glyph. */
    static String styleClass(Cell cell) {
        return "em-glyph-" + cell.id().replace('_', '-');
    }

    /** One instance an account is ticked on, and what is still to know or do there. */
    record On(EmulatorsModel.InstanceRow column, Cell cell) {

        /** The chip's words after the instance's name: Apply signs it in, or the instance was not read. */
        String mark() {
            return switch (cell) {
                case TO_SIGN_IN -> " · at Apply";
                case NOT_READ -> " · not read";
                default -> "";
            };
        }
    }

    /** The columns shown on which {@code account} is ticked: the account inspector's <i>Ticked on</i>. */
    static List<On> tickedOn(EmulatorConfig config, List<EmulatorsModel.InstanceRow> columns, AccountRecord account) {
        List<On> out = new ArrayList<>();
        for (EmulatorsModel.InstanceRow column : columns) {
            Cell cell = cell(config, column, account);
            if (cell.ticked()) out.add(new On(column, cell));
        }
        return out;
    }

    /**
     * Whether {@code account} is shared on {@code instance}: it holds the account through one address while another
     * instance holds it through another ({@link HostBook#shared}).
     */
    static Optional<HostBook.Shared> shared(List<HostBook.Shared> shared, AccountRecord account, InstanceKey instance) {
        return shared.stream().filter(one -> one.account().key().equals(account.key()))
                .filter(one -> instance == null || one.through().containsKey(instance)).findFirst();
    }

    /** The warning a shared account's cell and inspector give: how many instances, through which addresses. */
    static String sharedWords(HostBook.Shared shared) {
        long addresses = shared.through().values().stream().map(a -> a.toLowerCase(Locale.ROOT)).distinct().count();
        return "Shared: " + EmulatorsModel.count(shared.through().size(), "instance") + " hold it through "
                + addresses + " of its addresses, and Google sees one account signed in on each";
    }

    /** One cell ticked: an account, by the address the tick wrote ({@link #named}), on an instance. */
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
