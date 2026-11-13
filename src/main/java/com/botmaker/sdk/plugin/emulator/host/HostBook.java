package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.shared.emulator.config.AccountRecord;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Every account this PC knows, for every emulator and every bot: the {@link SdkHostValues#BOOK} value of the SDK's
 * project of this PC (spec {@code 2026-11-13-host-projects-design.md}). The Accounts window lists it, and an instance's
 * configuration picks from it and names its entries. An entry is kind, name and note, never a password, which only the
 * OS credential store holds. Each change is one step of this PC's undo.
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
     * empty note filled from a configuration's (one written before the book held notes). Writes only when it added or
     * filled something.
     */
    public List<AccountRecord> known(EmulatorConfig config) throws IOException {
        List<AccountRecord> layered = layered(config);
        Consumer<Map<AccountRecord.Key, AccountRecord>> fill = book -> layered.forEach(account -> {
            AccountRecord was = book.get(account.key());
            if (was == null || was.note().isEmpty() && !account.note().isEmpty()) book.put(account.key(), account);
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
            book.put(account.key(), was == null || !account.note().isEmpty() ? account : was);
        });
    }

    /** Sets the account as given, its note too, empty or not: the user's own edit of it. */
    public List<AccountRecord> put(AccountRecord account) throws IOException {
        return change("Change an account's note", book -> book.put(account.key(), account));
    }

    /** Takes the account out of the book; the caller offers it only for one no configuration lists. */
    public List<AccountRecord> remove(AccountRecord.Key key) throws IOException {
        return change("Remove an account", book -> book.remove(key));
    }

    /**
     * Adds the accounts of a file {@link #export} wrote that the book lacks; an account it has keeps its note, unless
     * that note is empty. Answers the book after.
     */
    public List<AccountRecord> importFrom(Path source) throws IOException {
        Map<AccountRecord.Key, AccountRecord> given = understood(entries(readJson(source)));
        return change("Import accounts", book -> given.forEach((key, account) -> {
            AccountRecord was = book.get(key);
            if (was == null || was.note().isEmpty() && !account.note().isEmpty()) book.put(key, account);
        }));
    }

    /**
     * Writes {@code accounts} to {@code target} for another PC: {@code kind}, {@code name} and {@code note} per account,
     * and nothing else, so no password or instance ever leaves this PC this way.
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

    /** Each account's note by its key, as the book has it; what the instances' accounts show. */
    static Map<AccountRecord.Key, String> notes(HostKnown book) {
        Map<AccountRecord.Key, String> notes = new LinkedHashMap<>();
        understood(book.list()).forEach((key, account) -> notes.put(key, account.note()));
        return notes;
    }

    // ---- the value ----

    /**
     * Starts the book when it is not there yet, then reads it, lets {@code edit} change it and writes it back as one
     * step {@code label}; nothing is written when the edit changed nothing. An entry of a kind this build does not know
     * is kept after the others.
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
                        .ifPresent(pc -> entries.add(entry(pc)));
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

    /** {@code book} as rows, then every row of {@code before} this build does not understand, kept. */
    private static HostKnown written(Map<AccountRecord.Key, AccountRecord> book, HostKnown before) {
        List<HostKnown.Entry> out = new ArrayList<>();
        book.values().forEach(account -> out.add(entry(account)));
        before.list().stream().filter(row -> record(row) == null).forEach(out::add);
        return new HostKnown(out);
    }

    private static HostKnown.Entry entry(AccountRecord account) {
        return new HostKnown.Entry(account.kind().id(), account.name(), account.note());
    }

    /** The row as an account, or null for one of a kind this build does not know or with no name. */
    private static AccountRecord record(HostKnown.Entry row) {
        AccountRecord.Kind kind = AccountRecord.Kind.fromId(row.kind());
        return kind == AccountRecord.Kind.UNKNOWN || row.name().isBlank() ? null
                : new AccountRecord(kind, row.name(), row.note());
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
        List<HostKnown.Entry> out = new ArrayList<>();
        for (JsonNode entry : root.path("accounts")) {
            HostKnown.Entry row = new HostKnown.Entry(entry.path("kind").asText(""), entry.path("name").asText(""),
                    entry.path("note").asText(""));
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
