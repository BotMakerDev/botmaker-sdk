// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Sourced;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every account this PC knows, for every emulator and every bot: the {@link SdkHostValues#BOOK} value of the SDK's
 * project of this PC (spec {@code 2026-11-13-host-projects-design.md}). The Accounts window lists it, and an instance's
 * configuration picks from it and names its entries. An entry is kind, name, note, aliases and the verification its
 * sign-in follows, and a launch target's account its launch target and links (signs in with, plays at, 2026-11-27);
 * never a password, which only the OS credential store holds. Each change is one step of this PC's undo.
 *
 * <p>The book is started on its first use: from the {@code accounts.json} an older SDK kept in the window's
 * {@code state/} folder, as one commit "Account book moved in", after which that file is deleted; else with the PC's
 * own Google account. {@link #known} adds what a configuration lists that the book lacks, and writes only then.
 */
public final class HostBook {

    /** The note the PC's own Google account is seeded with. */
    public static final String PC_ACCOUNT_NOTE = "This PC's Google account";

    /** The commit that brings an older SDK's {@code accounts.json} in. */
    static final String MOVED_IN = "Account book moved in";

    static final String STARTED = "Account book started";

    /** The file an older SDK kept the book in, and the format of an export. */
    static final String LEGACY = "accounts.json";

    /** Set in {@link #LEGACY} once the PC's own account was offered to it. */
    private static final String SEEDED = "pcSeeded";

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final StudioServices services;
    private final Path legacyDir;
    private final Supplier<Optional<String>> pcGoogle;

    /**
     * @param legacyDir where an older SDK kept {@code accounts.json}
     * @param pcGoogle  the PC's own Google account, asked only when the book is started
     */
    public HostBook(StudioServices services, Path legacyDir, Supplier<Optional<String>> pcGoogle) {
        this.services = services;
        this.legacyDir = legacyDir;
        this.pcGoogle = pcGoogle;
    }

    /**
     * The book, in the order accounts were added, with what {@code config} lists that it lacks added to it, and an
     * empty note filled from a configuration's (one written before the book held notes). An address an entry has as
     * an alias is that entry's, never one of its own. Writes only when it added or filled something.
     */
    public List<AccountRecord> known(EmulatorConfig config) throws IOException {
        List<AccountRecord> layered = layered(config);
        Consumer<Map<AccountRecord.Key, AccountRecord>> fill = book -> layered.forEach(account -> {
            AccountRecord was = book.get(account.key());
            if (was == null) {
                if (aliasHolder(book, account.key(), account.key().name()).isEmpty()) book.put(account.key(), account);
            } else if (was.note().isEmpty() && !account.note().isEmpty()) book.put(account.key(), was.withNote(account.note()));
        });
        Map<AccountRecord.Key, AccountRecord> book = new LinkedHashMap<>();
        if (services != null) {
            start();
            book.putAll(understood(current().orElse(HostKnown.none()).list()));
        }
        Map<AccountRecord.Key, AccountRecord> filled = new LinkedHashMap<>(book);
        fill.accept(filled);
        if (services == null || filled.equals(book)) return List.copyOf(filled.values());
        return change("Note the accounts the emulators list", fill);
    }

    /**
     * Whether the step {@code label} names started the book: taking it back would leave no book, and the
     * {@code accounts.json} it moved in is deleted, so ↶ stops there.
     */
    public static boolean startedTheBook(String label) {
        return MOVED_IN.equals(label) || STARTED.equals(label);
    }

    /** Adds the account, or sets the note of the one of its key when {@code account}'s is not empty. */
    public List<AccountRecord> add(AccountRecord account) throws IOException {
        return change("Add an account", book -> {
            AccountRecord was = book.get(account.key());
            book.put(account.key(), was == null ? account
                    : account.note().isEmpty() ? was : was.withNote(account.note()));
        });
    }

    /** <i>Paste list…</i>: adds the accounts the book lacks as one change, one step of undo; a listed one is left. */
    public List<AccountRecord> addAll(List<AccountRecord> accounts) throws IOException {
        return change("Add " + accounts.size() + (accounts.size() == 1 ? " account" : " accounts"),
                book -> accounts.forEach(account -> book.putIfAbsent(account.key(), account)));
    }

    /**
     * Sets the account's note as given, empty or not: the user's own edit of it. Its aliases and verification stay
     * the book's; an account the book lacks is added as given.
     */
    public List<AccountRecord> put(AccountRecord account) throws IOException {
        return change("Change an account's note", book -> {
            AccountRecord was = book.get(account.key());
            book.put(account.key(), was == null ? account : was.withNote(account.note()));
        });
    }

    /**
     * Sets a launch target's account's links: the book entry it signs in with, or none, and the key of the place it
     * plays at, or {@code ""} for any free place. Refused for an account the book lacks, one of another kind, or a
     * {@code signsInWith} the book does not hold or that is another launch target's account.
     */
    public List<AccountRecord> link(AccountRecord.Key key, Optional<AccountRecord.Key> signsInWith, String playsAt)
            throws IOException {
        AtomicReference<String> refused = new AtomicReference<>();
        List<AccountRecord> after = change("Change an account's links", book -> {
            AccountRecord account = book.get(key);
            if (account == null || account.kind() != AccountRecord.Kind.LAUNCH_TARGET) {
                refused.set(key.name() + " is not a launch target's account in this PC's account book.");
                return;
            }
            Optional<AccountRecord.Key> with = signsInWith == null ? Optional.empty() : signsInWith;
            if (with.isPresent() && !book.containsKey(with.get())) {
                refused.set(with.get().name() + " is not in this PC's account book.");
                return;
            }
            if (with.isPresent() && with.get().kind() == AccountRecord.Kind.LAUNCH_TARGET) {
                refused.set(key.name() + " cannot sign in with another launch target's account.");
                return;
            }
            book.put(key, account.withSignsInWith(with).withPlaysAt(playsAt));
        });
        if (refused.get() != null) throw new IOException(refused.get());
        return after;
    }

    /** Takes the account out of the book; the caller offers it only for one no configuration lists. */
    public List<AccountRecord> remove(AccountRecord.Key key) throws IOException {
        return change("Remove an account", book -> book.remove(key));
    }

    /** Sets the verification the account's sign-in follows; {@link Verification#DEFAULT} follows the book's. */
    public List<AccountRecord> verify(AccountRecord.Key key, Verification method) throws IOException {
        return change("Change an account's verification", book -> book.computeIfPresent(key,
                (k, account) -> account.withVerify(method)));
    }

    /**
     * Records {@code address} as one of the account's aliases. Refused when another entry has it as an alias already;
     * an address that is another entry's main one is taken, and that entry is then offered {@link #merge}.
     */
    public List<AccountRecord> addAlias(AccountRecord.Key key, String address) throws IOException {
        if (address == null || address.isBlank()) throw new IOException("An alias is an address.");
        String wanted = address.strip().toLowerCase(Locale.ROOT);
        AtomicReference<String> refused = new AtomicReference<>();
        List<AccountRecord> after = change("Record an alias", book -> {
            AccountRecord account = book.get(key);
            if (account == null) {
                refused.set(key.name() + " is not in this PC's account book.");
                return;
            }
            Optional<AccountRecord> holder = aliasHolder(book, key, wanted);
            if (holder.isPresent()) refused.set(address.strip() + " is an alias of " + holder.get().name() + " already.");
            else book.put(key, account.withAlias(wanted));
        });
        if (refused.get() != null) throw new IOException(refused.get());
        return after;
    }

    /**
     * <i>Record as an alias of A</i>, after a sign-in by {@code of} that Android lists as {@code address}: the address
     * becomes one of {@code of}'s aliases, as one step. When the book lists it as its own entry (an instance's
     * configuration named it after the sign-in), that entry is folded into {@code of}, as {@link #merge} does; the
     * instances keep naming the address Android lists, which {@code of} answers to. {@code of} may be one of an
     * entry's aliases: the sign-in named the address an instance lists.
     */
    public List<AccountRecord> recordAlias(AccountRecord.Key of, String address) throws IOException {
        if (address == null || address.isBlank()) throw new IOException("An alias is an address.");
        String wanted = address.strip().toLowerCase(Locale.ROOT);
        AccountRecord.Key own = new AccountRecord.Key(of.kind(), of.target(), wanted);
        AtomicReference<String> refused = new AtomicReference<>();
        List<AccountRecord> after = change("Record " + wanted + " as an alias of " + of.name(), book -> {
            Optional<AccountRecord> found = Optional.ofNullable(book.get(of)).or(() -> book.values().stream()
                    .filter(one -> one.kind() == of.kind() && one.target().equals(of.target())
                            && one.answersTo(of.name())).findFirst());
            if (found.isEmpty()) {
                refused.set(of.name() + " is not in this PC's account book.");
                return;
            }
            AccountRecord account = found.get();
            if (account.answersTo(wanted)) return;
            Optional<AccountRecord> holder = aliasHolder(book, account.key(), wanted);
            if (holder.isPresent()) {
                refused.set(address.strip() + " is an alias of " + holder.get().name() + " already.");
                return;
            }
            AccountRecord entry = book.remove(own);
            book.put(account.key(), entry == null ? account.withAlias(wanted) : merged(account, entry));
        });
        if (refused.get() != null) throw new IOException(refused.get());
        return after;
    }

    /** Another entry of {@code key}'s kind, and launch target, that has {@code address} among its aliases. */
    private static Optional<AccountRecord> aliasHolder(Map<AccountRecord.Key, AccountRecord> book,
                                                       AccountRecord.Key key, String address) {
        return book.values().stream().filter(other -> !other.key().equals(key) && other.kind() == key.kind()
                        && other.target().equals(key.target()))
                .filter(other -> other.aliases().contains(address)).findFirst();
    }

    /** Takes {@code address} off the account's aliases. */
    public List<AccountRecord> removeAlias(AccountRecord.Key key, String address) throws IOException {
        return change("Remove an alias", book -> book.computeIfPresent(key,
                (k, account) -> account.withoutAlias(address)));
    }

    /**
     * Makes {@code absorbed} one account with {@code kept}, as one step: its main address and aliases become
     * {@code kept}'s aliases, its note is added to {@code kept}'s, and its verification is taken when {@code kept}
     * follows the default. The instances that listed it keep its address, the one Android lists them under, which
     * {@code kept} now answers to: renaming them would have Apply sign the account in a second time. Passwords are
     * not the book's: the caller keeps the one the user picked in the OS store.
     */
    public List<AccountRecord> merge(AccountRecord.Key kept, AccountRecord.Key absorbed) throws IOException {
        if (kept.equals(absorbed)) throw new IOException("An account is not merged into itself.");
        Map<AccountRecord.Key, AccountRecord> now = understood(currentOrStarted().list());
        AccountRecord into = now.get(kept);
        AccountRecord from = now.get(absorbed);
        if (into == null || from == null) throw new IOException("Both accounts must be in this PC's account book.");
        if (into.kind() != from.kind()) throw new IOException("Only two accounts of one kind are merged.");
        return change("Merge " + from.name() + " into " + into.name(), book -> {
            AccountRecord keptNow = book.get(kept);
            AccountRecord absorbedNow = book.remove(absorbed);
            if (keptNow != null && absorbedNow != null) book.put(kept, merged(keptNow, absorbedNow));
            else if (absorbedNow != null) book.put(absorbed, absorbedNow);
        });
    }

    /** {@code into} with {@code from}'s addresses, note and verification, as {@link #merge} writes it. */
    static AccountRecord merged(AccountRecord into, AccountRecord from) {
        List<String> aliases = new ArrayList<>(into.aliases());
        aliases.addAll(from.addresses());
        String note = into.note().isEmpty() ? from.note()
                : from.note().isEmpty() || from.note().equals(into.note()) ? into.note()
                : into.note() + " · " + from.note();
        Verification verify = into.verify() == Verification.DEFAULT && from.verify().isMethod() ? from.verify()
                : into.verify();
        AccountRecord out = into.withNote(note).withAliases(aliases).withVerify(verify);
        if (out.signsInWith().isEmpty()) out = out.withSignsInWith(from.signsInWith());
        return out.playsAt().isEmpty() ? out.withPlaysAt(from.playsAt()) : out;
    }

    /** The verification the book's accounts follow unless one picks its own; Phone prompt until the user sets one. */
    public Verification defaultVerification() {
        Verification read = Verification.fromId(HostConfig.read(services, SdkHostValues.DEFAULT_VERIFICATION,
                String.class, Verification.FIRST_DEFAULT.id()));
        return Verification.defaults().contains(read) ? read : Verification.FIRST_DEFAULT;
    }

    /**
     * The method each address's sign-in follows, read once for an Apply: the book's entry it answers to, else the
     * default. Reads the book without starting it.
     */
    public Function<String, Verification> methods() throws IOException {
        Verification byDefault = defaultVerification();
        if (services == null) return methods(List.of(), byDefault);
        return methods(List.copyOf(understood(current().orElse(HostKnown.none()).list()).values()), byDefault);
    }

    /**
     * The method of the Google account in {@code book} that an address answers to, through any of its addresses,
     * followed through {@code byDefault}; an address the book lacks follows the default.
     */
    public static Function<String, Verification> methods(List<AccountRecord> book, Verification byDefault) {
        List<AccountRecord> google = book.stream().filter(a -> a.kind() == AccountRecord.Kind.GOOGLE).toList();
        return email -> google.stream().filter(account -> account.answersTo(email)).findFirst()
                .map(AccountRecord::verify).orElse(Verification.DEFAULT).followed(byDefault);
    }

    /**
     * The address each typed one's authenticator key is kept under, read once for an Apply: the main address of the
     * book's Google account it answers to, else itself. Reads the book without starting it.
     */
    public Function<String, String> keyedAs() throws IOException {
        if (services == null) return keyedAs(List.of());
        return keyedAs(List.copyOf(understood(current().orElse(HostKnown.none()).list()).values()));
    }

    /** The main address of the Google account in {@code book} an address answers to; an address it lacks, itself. */
    public static Function<String, String> keyedAs(List<AccountRecord> book) {
        List<AccountRecord> google = book.stream().filter(a -> a.kind() == AccountRecord.Kind.GOOGLE).toList();
        return email -> google.stream().filter(account -> account.answersTo(email)).findFirst()
                .map(AccountRecord::name).orElse(email);
    }

    /** Sets the book's default verification, one step of undo; only a method of its own may be the default. */
    public void defaultVerification(Verification method) throws IOException {
        if (method == null || !method.isMethod()) throw new IOException("The default is one of the methods.");
        if (services == null) throw new IOException("BotMaker keeps no values of this PC here.");
        Optional<String> refused = HostConfig.step(services, "Change the default verification", () -> {
            String before = HostConfig.read(services, SdkHostValues.DEFAULT_VERIFICATION, String.class, null);
            return HostConfig.write(services, SdkHostValues.DEFAULT_VERIFICATION, String.class, before, method.id());
        });
        if (refused.isPresent()) throw new IOException(refused.get());
    }

    /**
     * Two or more instances holding one of {@code book}'s accounts through two or more of its addresses: Google sees
     * one account signed in twice. Instances wanting it by a single address are not listed, as nothing is hidden.
     */
    public static List<Shared> shared(EmulatorConfig config, Collection<InstanceKey> instances,
                                      List<AccountRecord> book) {
        List<Shared> out = new ArrayList<>();
        for (AccountRecord account : book) {
            if (account.aliases().isEmpty()) continue;
            Map<InstanceKey, String> through = new LinkedHashMap<>();
            for (InstanceKey instance : instances) {
                for (Sourced<AccountRecord> wanted : config.wanted(instance).accounts()) {
                    AccountRecord held = wanted.value();
                    if (held.kind() == account.kind() && account.answersTo(held.name())) {
                        through.putIfAbsent(instance, held.name());
                    }
                }
            }
            long addresses = through.values().stream().map(a -> a.toLowerCase(Locale.ROOT)).distinct().count();
            if (through.size() > 1 && addresses > 1) out.add(new Shared(account, Map.copyOf(through)));
        }
        return out;
    }

    /**
     * An account of the book held by several instances through different addresses.
     *
     * @param through each instance, by the address it holds the account through
     */
    public record Shared(AccountRecord account, Map<InstanceKey, String> through) {}

    /**
     * Adds the accounts of a file {@link #export} wrote that the book lacks. An account it has keeps its note unless
     * that note is empty, gains the file's aliases, and takes the file's verification when it follows the default.
     * Answers the book after.
     */
    public List<AccountRecord> importFrom(Path source) throws IOException {
        Map<AccountRecord.Key, AccountRecord> given = understood(entries(readJson(source)));
        return change("Import accounts", book -> {
            given.forEach((key, account) -> {
                AccountRecord was = book.get(key);
                List<String> free = account.aliases().stream()
                        .filter(alias -> aliasHolder(book, key, alias).isEmpty()).toList();
                if (was == null) {
                    book.put(key, account.withAliases(free));
                    return;
                }
                List<String> aliases = new ArrayList<>(was.aliases());
                aliases.addAll(free);
                AccountRecord merged = was.withNote(was.note().isEmpty() ? account.note() : was.note())
                        .withAliases(aliases).withVerify(was.verify() == Verification.DEFAULT ? account.verify() : was.verify());
                book.put(key, merged.signsInWith().isEmpty() ? merged.withSignsInWith(account.signsInWith()) : merged);
            });
            // A link the file brought to an entry neither it nor the book holds is dropped, as link() refuses one.
            given.keySet().forEach(key -> book.computeIfPresent(key, (k, account) -> account.signsInWith()
                    .filter(with -> !book.containsKey(with)).isPresent()
                    ? account.withSignsInWith(Optional.empty()) : account));
        });
    }

    /**
     * Writes {@code accounts} to {@code target} for another PC: {@code kind}, {@code name}, {@code note},
     * {@code aliases} and {@code verify} per account, and a launch target's {@code target} and {@code signsInWith},
     * and nothing else, so no password, instance or place ever leaves this PC this way.
     */
    public static void export(List<AccountRecord> accounts, Path target) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("v", 1);
        ArrayNode list = root.putArray("accounts");
        for (AccountRecord account : accounts) {
            ObjectNode entry = list.addObject();
            entry.put("kind", account.kind().id());
            entry.put("name", account.name());
            entry.put("note", account.note());
            ArrayNode aliases = entry.putArray("aliases");
            account.aliases().forEach(aliases::add);
            entry.put("verify", account.verify().id());
            if (account.kind() == AccountRecord.Kind.LAUNCH_TARGET) {
                entry.put("target", account.target());
                account.signsInWith().ifPresent(with -> entry.put("signsInWith", with.text()));
            }
        }
        Files.writeString(target, JSON.writeValueAsString(root), StandardCharsets.UTF_8);
    }

    /** Which of {@code instances} each account is wanted on, by its configuration (the emulator's layer, then its own). */
    public static Map<AccountRecord.Key, List<InstanceKey>> onInstances(EmulatorConfig config,
                                                                       Collection<InstanceKey> instances) {
        Map<AccountRecord.Key, List<InstanceKey>> on = new LinkedHashMap<>();
        for (InstanceKey instance : instances) {
            for (Sourced<AccountRecord> account : config.wanted(instance).accounts()) {
                on.computeIfAbsent(account.value().key(), k -> new ArrayList<>()).add(instance);
            }
        }
        return on;
    }

    /** The accounts on no instance first, then by name, case aside: the free ones are the likely pick. */
    public static List<AccountRecord> sorted(List<AccountRecord> accounts,
                                             Map<AccountRecord.Key, List<InstanceKey>> onInstances) {
        return accounts.stream()
                .sorted(Comparator.<AccountRecord, Boolean>comparing(a -> onInstances.containsKey(a.key()))
                        .thenComparing(AccountRecord::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** The accounts some configuration lists, an emulator's or an instance's: the ones the book may not drop. */
    public static Set<AccountRecord.Key> held(EmulatorConfig config) {
        Set<AccountRecord.Key> held = new HashSet<>();
        for (AccountRecord account : layered(config)) held.add(account.key());
        return held;
    }

    /**
     * The note a configuration lists {@code key} with, empty when none gives one: {@link #known} fills an empty book
     * note from it, so a note cleared in the book comes back while a configuration keeps it.
     */
    public static String listedNote(EmulatorConfig config, AccountRecord.Key key) {
        return layered(config).stream().filter(account -> account.key().equals(key)).map(AccountRecord::note)
                .filter(note -> !note.isEmpty()).findFirst().orElse("");
    }

    /** Each account's note by its key, as the book has it; what the instances' accounts show. */
    static Map<AccountRecord.Key, String> notes(HostKnown book) {
        Map<AccountRecord.Key, String> notes = new LinkedHashMap<>();
        understood(book.list()).forEach((key, account) -> notes.put(key, account.note()));
        return notes;
    }

    // ---- the value ----

    /**
     * Starts the book when it is not there yet, then reads it, lets {@code edit} change it and writes it back as one
     * step {@code label}; nothing is written when the edit changed nothing. An entry of a kind this build does not
     * know is kept after the others.
     */
    private List<AccountRecord> change(String label, Consumer<Map<AccountRecord.Key, AccountRecord>> edit)
            throws IOException {
        if (services == null) throw new IOException("BotMaker keeps no values of this PC here.");
        start();
        AtomicReference<List<AccountRecord>> after = new AtomicReference<>();
        AtomicReference<IOException> unread = new AtomicReference<>();
        Optional<String> refused = HostConfig.step(services, label, () -> {
            HostKnown before;
            try {
                before = current().orElse(HostKnown.none());
            } catch (IOException e) {
                unread.set(e);
                return Optional.empty();
            }
            Map<AccountRecord.Key, AccountRecord> book = understood(before.list());
            edit.accept(book);
            after.set(List.copyOf(book.values()));
            return HostConfig.write(services, SdkHostValues.BOOK, HostKnown.class, before, written(book, before));
        });
        if (unread.get() != null) throw unread.get();
        if (refused.isPresent()) throw new IOException(refused.get());
        return after.get();
    }

    private HostKnown currentOrStarted() throws IOException {
        if (services == null) throw new IOException("BotMaker keeps no values of this PC here.");
        start();
        return current().orElse(HostKnown.none());
    }

    /**
     * Writes the book when it is not there yet: from {@code accounts.json} when an older SDK left one, deleted once
     * the book holds it, else with the PC's own Google account. The check and the write are one step under
     * {@link HostConfig}'s lock, so two first uses start it once. An {@code accounts.json} that does not read is left
     * where it is, and the book starts without it.
     */
    private void start() throws IOException {
        if (current().isPresent()) return;
        Path legacy = legacyDir == null ? null : legacyDir.resolve(LEGACY);
        ObjectNode root = null;
        if (legacy != null && Files.isRegularFile(legacy)) {
            try {
                root = readJson(legacy);
            } catch (IOException unreadable) {
                // left for the user, who may fix it and import it
            }
        }
        boolean moving = root != null;
        ObjectNode from = moving ? root : JSON.createObjectNode();
        AtomicReference<IOException> unread = new AtomicReference<>();
        AtomicBoolean wrote = new AtomicBoolean();
        Optional<String> refused = HostConfig.step(services, moving ? MOVED_IN : STARTED, () -> {
            try {
                if (current().isPresent()) return Optional.empty();
            } catch (IOException e) {
                unread.set(e);
                return Optional.empty();
            }
            List<HostKnown.Entry> entries = new ArrayList<>(entries(from));
            if (!from.path(SEEDED).asBoolean(false)) {
                pcGoogle.get().filter(email -> !email.isBlank())
                        .map(email -> new AccountRecord(AccountRecord.Kind.GOOGLE, email, PC_ACCOUNT_NOTE))
                        .filter(pc -> !understood(entries).containsKey(pc.key()))
                        .ifPresent(pc -> entries.add(entry(pc, "")));
            }
            Optional<String> written = HostConfig.write(services, SdkHostValues.BOOK, HostKnown.class, null,
                    new HostKnown(entries));
            wrote.set(written.isEmpty());
            return written;
        });
        if (unread.get() != null) throw unread.get();
        if (refused.isPresent()) throw new IOException(refused.get());
        if (moving && wrote.get()) {
            try {
                Files.deleteIfExists(legacy);
            } catch (IOException kept) {
                // the book holds it now and is never started again, so the file is only left over
            }
        }
    }

    /** The book as written; empty when there is none yet. A book that is there but does not read is an error. */
    private Optional<HostKnown> current() throws IOException {
        Optional<ValueContext> ctx;
        try {
            ctx = services.hostValues().open(SdkHostValues.BOOK);
        } catch (RuntimeException failed) {
            throw new IOException("This PC's account book could not be opened: " + failed.getMessage(), failed);
        }
        if (ctx.isEmpty()) return Optional.empty();
        Optional<HostKnown> book = ctx.get().value(HostKnown.class);
        if (book.isEmpty()) {
            throw new IOException("This PC's account book could not be read, so nothing was written over it. "
                    + "Look at Accounts.java in BotMaker's folder of this PC.");
        }
        return book;
    }

    /**
     * {@code book} as rows, then every row of {@code before} this build does not understand, kept. A verification a
     * newer BotMaker wrote is kept as it was written.
     */
    private static HostKnown written(Map<AccountRecord.Key, AccountRecord> book, HostKnown before) {
        Map<AccountRecord.Key, String> verifies = new LinkedHashMap<>();
        for (HostKnown.Entry row : before.list()) {
            AccountRecord account = record(row);
            if (account != null) verifies.putIfAbsent(account.key(), row.verify());
        }
        List<HostKnown.Entry> out = new ArrayList<>();
        book.values().forEach(account -> out.add(entry(account, verifies.getOrDefault(account.key(), ""))));
        before.list().stream().filter(row -> record(row) == null).forEach(out::add);
        return new HostKnown(out);
    }

    /** The account as a row; {@code writtenVerify} is kept for a verification this build does not know. */
    private static HostKnown.Entry entry(AccountRecord account, String writtenVerify) {
        String verify = switch (account.verify()) {
            case DEFAULT -> "";
            case UNKNOWN -> writtenVerify;
            default -> account.verify().id();
        };
        return new HostKnown.Entry(account.kind().id(), account.name(), account.note(),
                String.join(" ", account.aliases()), verify, account.target(),
                account.signsInWith().map(AccountRecord.Key::text).orElse(""), account.playsAt());
    }

    /**
     * The row as an account, or null for one of a kind this build does not know, with no name, or a launch target's
     * with no launch target. A link that does not read is no link.
     */
    private static AccountRecord record(HostKnown.Entry row) {
        AccountRecord.Kind kind = AccountRecord.Kind.fromId(row.kind());
        if (kind == AccountRecord.Kind.UNKNOWN || row.name().isBlank()) return null;
        if (kind == AccountRecord.Kind.LAUNCH_TARGET && row.target().isBlank()) return null;
        return new AccountRecord(kind, row.name(), row.note(), row.aliasList(), Verification.fromId(row.verify()),
                row.target(), AccountRecord.Key.parse(row.signsInWith()), row.playsAt());
    }

    private static Map<AccountRecord.Key, AccountRecord> understood(List<HostKnown.Entry> rows) {
        Map<AccountRecord.Key, AccountRecord> out = new LinkedHashMap<>();
        for (HostKnown.Entry row : rows) {
            AccountRecord account = record(row);
            if (account != null) out.putIfAbsent(account.key(), account);
        }
        return out;
    }

    // ---- the JSON of an export and of an older SDK's book ----

    private static ObjectNode readJson(Path file) throws IOException {
        JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        if (root == null || root.isMissingNode()) return JSON.createObjectNode();
        if (!(root instanceof ObjectNode object)) throw new IOException(file.getFileName() + " is not a JSON object");
        return object;
    }

    /** Every entry with a name, a kind this build does not know included. */
    private static List<HostKnown.Entry> entries(ObjectNode root) {
        Function<JsonNode, String> aliases = entry -> {
            List<String> out = new ArrayList<>();
            entry.path("aliases").forEach(alias -> out.add(alias.asText("")));
            return String.join(" ", out);
        };
        List<HostKnown.Entry> out = new ArrayList<>();
        for (JsonNode entry : root.path("accounts")) {
            String verify = Verification.fromId(entry.path("verify").asText("")) == Verification.DEFAULT ? ""
                    : entry.path("verify").asText("");
            HostKnown.Entry row = new HostKnown.Entry(entry.path("kind").asText(""), entry.path("name").asText(""),
                    entry.path("note").asText(""), aliases.apply(entry), verify, entry.path("target").asText(""),
                    entry.path("signsInWith").asText(""), "");
            if (!row.name().isBlank()) out.add(row);
        }
        return out;
    }

    /** Every account a layer lists, emulators' first, in their order. */
    private static List<AccountRecord> layered(EmulatorConfig config) {
        List<AccountRecord> out = new ArrayList<>();
        config.emulators().values().forEach(layer -> out.addAll(layer.accounts()));
        config.instances().values().forEach(layer -> out.addAll(layer.accounts()));
        return out;
    }
}
