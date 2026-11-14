package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.sdk.plugin.emulator.host.HostBook;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.google.PasswordStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What the Accounts window lists, with no JavaFX (board Accounts, spec {@code 2026-11-13-google-accounts-design.md}): a
 * row per account of the book, by name, with how many instances want it, whether this PC remembers its password, and
 * whether it may leave the book.
 */
final class AccountsListModel {

    private AccountsListModel() {}

    /** What this PC keeps of an account's password. */
    enum Password {
        REMEMBERED("Remembered"),
        ASKED("Asked at Apply"),
        /** A kind BotMaker signs nothing in for: a record, with no password at all. */
        NONE("—");

        private final String displayName;

        Password(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    /**
     * One account.
     *
     * @param instances how many instances its configurations want it on
     * @param removable no configuration lists it, so taking it out of the book drops nothing a layer names
     */
    record Row(AccountRecord account, int instances, Password password, boolean removable) {

        boolean canForget() {
            return password == Password.REMEMBERED;
        }

        /** <i>Remember…</i>: a Google account whose password is not kept, on a PC with a store. */
        boolean canRemember(boolean storeAvailable) {
            return storeAvailable && password == Password.ASKED;
        }

        String instancesText() {
            return instances == 0 ? "None" : instances == 1 ? "1 instance" : instances + " instances";
        }
    }

    /**
     * @param onInstances which instances want each account ({@link HostBook#onInstances})
     * @param held        the accounts some configuration lists ({@link HostBook#held})
     * @param remembered  the keys ({@link PasswordStore#key}) of the passwords this PC keeps
     */
    static List<Row> rows(List<AccountRecord> book, Map<AccountRecord.Key, List<InstanceKey>> onInstances,
                          Set<AccountRecord.Key> held, Set<String> remembered) {
        return book.stream()
                .sorted(Comparator.comparing(AccountRecord::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(account -> account.kind().ordinal()))
                .map(account -> new Row(account, onInstances.getOrDefault(account.key(), List.of()).size(),
                        !account.kind().signedInByBotMaker() ? Password.NONE
                                : remembered.contains(PasswordStore.key(account.name())) ? Password.REMEMBERED
                                : Password.ASKED,
                        !held.contains(account.key())))
                .toList();
    }

    /** The keys the store is asked about: the accounts BotMaker signs in. */
    static Set<String> keys(List<AccountRecord> book) {
        return book.stream().filter(account -> account.kind().signedInByBotMaker())
                .map(account -> PasswordStore.key(account.name())).collect(Collectors.toSet());
    }

    /** What an import did: the accounts it added, and the ones whose empty note it filled. */
    record Imported(int added, int notes) {

        String said(String file) {
            if (added == 0 && notes == 0) return "Nothing new in " + file + ".";
            String words = added == 0 ? "" : added + (added == 1 ? " account" : " accounts") + " imported";
            if (notes > 0) {
                words += (words.isEmpty() ? "" : ", ") + notes + (notes == 1 ? " note" : " notes") + " filled";
            }
            return words + " from " + file + ".";
        }
    }

    /** Compares the book read before an import with the book after it. */
    static Imported imported(List<AccountRecord> before, List<AccountRecord> after) {
        Map<AccountRecord.Key, AccountRecord> was = before.stream()
                .collect(Collectors.toMap(AccountRecord::key, account -> account, (one, other) -> one));
        int added = 0;
        int notes = 0;
        for (AccountRecord account : after) {
            AccountRecord old = was.get(account.key());
            if (old == null) added++;
            else if (old.note().isEmpty() && !account.note().isEmpty()) notes++;
        }
        return new Imported(added, notes);
    }

    /**
     * <i>Paste list…</i>: what a pasted list holds, split on line breaks, tabs, commas and semicolons; a name keeps its
     * spaces.
     *
     * @param added   the new accounts of {@code kind}, in the list's order, each once, none the book has already
     * @param known   the words the book has already, of that kind, case aside
     * @param skipped the words that are not an email address, when {@code kind} is Google
     */
    record Pasted(List<AccountRecord> added, int known, List<String> skipped) {

        /** {@code done} is the verb's tense: "to add" before the dialog's Add, "added" after it. */
        String said(String done) {
            String words = added.isEmpty() ? "No account " + done
                    : added.size() + (added.size() == 1 ? " account " : " accounts ") + done;
            if (known > 0) words += ", " + known + " listed already";
            if (!skipped.isEmpty()) words += ", " + skipped.size() + " not an email address";
            return words + ".";
        }
    }

    static Pasted pasted(String text, AccountRecord.Kind kind, List<AccountRecord> book) {
        Set<String> have = book.stream().filter(account -> account.kind() == kind)
                .map(account -> account.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> booked = new HashSet<>(have);
        Set<String> counted = new HashSet<>();
        List<AccountRecord> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String part : (text == null ? "" : text).split("[\\r\\n\\t,;]+")) {
            String word = part.strip();
            if (word.isEmpty()) continue;
            if (kind == AccountRecord.Kind.GOOGLE && !EMAIL.matcher(word).matches()) {
                skipped.add(word);
            } else if (booked.contains(word.toLowerCase(Locale.ROOT))) {
                counted.add(word.toLowerCase(Locale.ROOT));
            } else if (have.add(word.toLowerCase(Locale.ROOT))) {
                added.add(new AccountRecord(kind, word, ""));
            }
        }
        return new Pasted(List.copyOf(added), counted.size(), List.copyOf(skipped));
    }

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /**
     * The rows whose name or note holds {@code text}, case aside, of {@code kind} (null for any).
     */
    static List<Row> filtered(List<Row> rows, String text, AccountRecord.Kind kind) {
        String words = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(row -> kind == null || row.account().kind() == kind)
                .filter(row -> words.isEmpty() || row.account().name().toLowerCase(Locale.ROOT).contains(words)
                        || row.account().note().toLowerCase(Locale.ROOT).contains(words))
                .toList();
    }
}
