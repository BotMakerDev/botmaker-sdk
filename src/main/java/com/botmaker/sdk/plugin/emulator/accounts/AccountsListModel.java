package com.botmaker.sdk.plugin.emulator.accounts;

import com.botmaker.sdk.plugin.emulator.host.HostBook;
import com.botmaker.sdk.plugin.emulator.window.Look;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.google.PasswordStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
        REMEMBERED("Remembered", Look.Tone.OK, "Remembered on this PC: the next Apply that signs it in types it"),
        ASKED("Asked", Look.Tone.MUTED, "Not remembered: the first Apply that signs it in asks it"),
        /** A kind BotMaker signs nothing in for: a record, with no password at all. */
        NONE("None", Look.Tone.OUTLINED, "No password: a record only, BotMaker signs nothing in for it");

        private final String displayName;
        private final Look.Tone tone;
        private final String words;

        Password(String displayName, Look.Tone tone, String words) {
            this.displayName = displayName;
            this.tone = tone;
            this.words = words;
        }

        String displayName() {
            return displayName;
        }

        /** The list's pill: the short word on the state's colour; {@link #words} goes in its tooltip. */
        Look.Pill pill() {
            return new Look.Pill(tone, displayName);
        }

        /** What the state means, said whole. */
        String words(boolean storeAvailable) {
            return this == ASKED && !storeAvailable
                    ? "No credential store on this PC: its password is asked at each Apply" : words;
        }
    }

    /**
     * One account.
     *
     * @param on        the instances its configurations want it on
     * @param removable no configuration lists it, so taking it out of the book drops nothing a layer names
     */
    record Row(AccountRecord account, List<InstanceKey> on, Password password, boolean removable) {

        Row {
            on = List.copyOf(on);
        }

        /** How many instances want it. */
        int instances() {
            return on.size();
        }

        boolean canForget() {
            return password == Password.REMEMBERED;
        }

        /** <i>Remember…</i>: a Google account whose password is not kept, on a PC with a store. */
        boolean canRemember(boolean storeAvailable) {
            return storeAvailable && password == Password.ASKED;
        }

        String instancesText() {
            int instances = instances();
            return instances == 0 ? "None" : instances == 1 ? "1 instance" : instances + " instances";
        }
    }

    /**
     * A row per account, each through any of its addresses: the instances that want it by an alias count, a
     * password remembered under an alias is remembered, and an alias a configuration lists keeps it in the book.
     *
     * @param onInstances which instances want each account ({@link HostBook#onInstances})
     * @param held        the accounts some configuration lists ({@link HostBook#held})
     * @param remembered  the keys ({@link PasswordStore#key}) of the passwords this PC keeps
     */
    static List<Row> rows(List<AccountRecord> book, Map<AccountRecord.Key, List<InstanceKey>> onInstances,
                          Set<AccountRecord.Key> held, Set<String> remembered) {
        return book.stream()
                .sorted(Comparator.comparing(AccountRecord::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(account -> account.kind().ordinal()))
                .map(account -> {
                    List<AccountRecord.Key> keys = keys(account);
                    List<InstanceKey> on = keys.stream().flatMap(key -> onInstances.getOrDefault(key, List.of())
                            .stream()).distinct().toList();
                    return new Row(account, on, !account.kind().signedInByBotMaker() ? Password.NONE
                            : remembered(account, remembered) ? Password.REMEMBERED : Password.ASKED,
                            keys.stream().noneMatch(held::contains));
                })
                .toList();
    }

    /** The account's key under each of its addresses, its own first. */
    private static List<AccountRecord.Key> keys(AccountRecord account) {
        return account.addresses().stream().map(address -> new AccountRecord.Key(account.kind(), address)).toList();
    }

    /** Whether the store keeps a password under any of the account's addresses. */
    static boolean remembered(AccountRecord account, Set<String> remembered) {
        return PasswordStore.rememberedAny(account.addresses(), remembered);
    }

    /** The list's small tag beside a name: "+2" for two aliases, empty for none. */
    static String aliasTag(AccountRecord account) {
        return account.aliases().isEmpty() ? "" : "+" + account.aliases().size();
    }

    /**
     * <i>Merge into A…</i>: the other entry of the book, of the account's kind, that has its address as an alias;
     * empty when none has.
     */
    static Optional<AccountRecord> mergeInto(AccountRecord account, List<AccountRecord> book) {
        return book.stream().filter(other -> other.kind() == account.kind() && !other.key().equals(account.key()))
                .filter(other -> other.aliases().contains(account.key().name())).findFirst();
    }

    /**
     * Whether a merge asks which password to keep: both accounts have one remembered and they differ. Compared here,
     * in BotMaker's process; the dialog says only that they differ.
     */
    static boolean asksWhichPassword(char[] kept, char[] absorbed) {
        return kept != null && absorbed != null && !Arrays.equals(kept, absorbed);
    }

    /** Why {@code typed} cannot be one more alias of {@code account}, or empty when it can. */
    static Optional<String> aliasRefused(AccountRecord account, String typed) {
        String address = typed == null ? "" : typed.strip();
        if (!EMAIL.matcher(address).matches()) return Optional.of("An alias is an email address.");
        if (account.answersTo(address)) return Optional.of(account.name() + " answers to it already.");
        return Optional.empty();
    }

    /** The "+2" tag's tooltip: every alias, one a line. */
    static String aliasWords(AccountRecord account) {
        return "Also signs in as:\n" + String.join("\n", account.aliases());
    }

    /**
     * One segment of the kind filter: a kind, or null for every kind, and how many accounts of the book it holds.
     */
    record KindCount(AccountRecord.Kind kind, int count) {

        String said() {
            return (kind == null ? "All" : kind.displayName()) + " " + count;
        }
    }

    /** The kind filter's segments: All first, then each kind an account can be, a kind with none at 0. */
    static List<KindCount> counts(List<Row> rows) {
        List<KindCount> out = new ArrayList<>();
        out.add(new KindCount(null, rows.size()));
        for (AccountRecord.Kind kind : kinds()) {
            out.add(new KindCount(kind, (int) rows.stream().filter(row -> row.account().kind() == kind).count()));
        }
        return out;
    }

    /** The kinds an account can be: every one but UNKNOWN. */
    static List<AccountRecord.Kind> kinds() {
        return Arrays.stream(AccountRecord.Kind.values()).filter(one -> one != AccountRecord.Kind.UNKNOWN).toList();
    }

    /** The keys the store is asked about: every address of the accounts BotMaker signs in. */
    static Set<String> keys(List<AccountRecord> book) {
        return book.stream().filter(account -> account.kind().signedInByBotMaker())
                .flatMap(account -> account.addresses().stream()).map(PasswordStore::key).collect(Collectors.toSet());
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
     * The detail panel's <i>Verification</i> choices: <i>Default</i> first, then the methods, and the account's own
     * when a newer BotMaker wrote it, so it shows as it is until the user picks another.
     */
    static List<Verification> verificationChoices(Verification current) {
        List<Verification> choices = new ArrayList<>();
        choices.add(Verification.DEFAULT);
        choices.addAll(Verification.methods());
        if (current != null && !choices.contains(current)) choices.add(current);
        return choices;
    }

    /** How a choice reads: <i>Default</i> names the book's default, "Default (Phone prompt)". */
    static String said(Verification method, Verification byDefault) {
        return method == Verification.DEFAULT ? method.displayName() + " (" + byDefault.displayName() + ")"
                : method.displayName();
    }

    /**
     * The rows whose name, an alias or the note holds {@code text}, case aside, of {@code kind} (null for any).
     */
    static List<Row> filtered(List<Row> rows, String text, AccountRecord.Kind kind) {
        String words = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(row -> kind == null || row.account().kind() == kind)
                .filter(row -> words.isEmpty()
                        || row.account().addresses().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(words))
                        || row.account().note().toLowerCase(Locale.ROOT).contains(words))
                .toList();
    }
}
