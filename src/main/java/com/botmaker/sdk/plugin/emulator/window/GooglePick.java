package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.config.AccountBook;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.google.PasswordStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What + Google offers, with no JavaFX (board A): a row per Google account of the book that the layer does not list yet,
 * the PC's own first, then the ones on no instance, then by name; and below them <i>Another account</i>.
 */
final class GooglePick {

    private GooglePick() {}

    /** Whether the Apply that signs the account in asks its password. */
    enum Pill {
        REMEMBERED("Password remembered"),
        ASKED("Asked at Apply");

        private final String displayName;

        Pill(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    /**
     * One account offered.
     *
     * @param detail its note, and the instances it is on, for the user's eyes only
     */
    record Choice(AccountRecord account, String detail, Pill pill) {
    }

    /**
     * The book's Google accounts not in {@code here}.
     *
     * @param onInstances which instances each account is wanted on ({@link AccountBook#onInstances})
     * @param remembered  the accounts this PC remembers the password of, by {@link PasswordStore#key}
     * @param pcGoogle    the PC's own Google account, listed first
     * @param here        the accounts the layer already lists
     */
    static List<Choice> choices(List<AccountRecord> book, Map<AccountRecord.Key, List<InstanceKey>> onInstances,
                                Set<String> remembered, Optional<String> pcGoogle, Collection<AccountRecord.Key> here) {
        List<AccountRecord> ordered = new ArrayList<>(AccountBook.sorted(offered(book, pcGoogle, here), onInstances));
        pcGoogle.flatMap(pc -> ordered.stream().filter(a -> a.name().equalsIgnoreCase(pc.strip())).findFirst())
                .ifPresent(pc -> {
                    ordered.remove(pc);
                    ordered.addFirst(pc);
                });
        return ordered.stream().map(account -> new Choice(account, detail(account, onInstances.get(account.key())),
                remembered.contains(PasswordStore.key(account.name())) ? Pill.REMEMBERED : Pill.ASKED)).toList();
    }

    /** The keys of the accounts {@link #choices} offers: what the store is asked about before the dialog opens. */
    static Set<String> keys(List<AccountRecord> book, Optional<String> pcGoogle, Collection<AccountRecord.Key> here) {
        return offered(book, pcGoogle, here).stream().map(account -> PasswordStore.key(account.name()))
                .collect(Collectors.toSet());
    }

    /** The book's Google accounts not in {@code here}, with the PC's own even when the book has lost it. */
    private static List<AccountRecord> offered(List<AccountRecord> book, Optional<String> pcGoogle,
                                               Collection<AccountRecord.Key> here) {
        List<AccountRecord> google = new ArrayList<>(book.stream()
                .filter(account -> account.kind() == AccountRecord.Kind.GOOGLE).toList());
        pcGoogle.map(String::strip).filter(pc -> !pc.isEmpty())
                .map(pc -> new AccountRecord(AccountRecord.Kind.GOOGLE, pc, AccountBook.PC_ACCOUNT_NOTE))
                .filter(pc -> google.stream().noneMatch(account -> account.key().equals(pc.key())))
                .ifPresent(google::add);
        return google.stream().filter(account -> !here.contains(account.key())).toList();
    }

    private static String detail(AccountRecord account, List<InstanceKey> on) {
        String where = on == null || on.isEmpty() ? "on no instance"
                : "on " + String.join(", ", on.stream().map(InstanceKey::reference).toList());
        return account.note().isEmpty() ? Character.toUpperCase(where.charAt(0)) + where.substring(1)
                : account.note() + " · " + where;
    }
}
