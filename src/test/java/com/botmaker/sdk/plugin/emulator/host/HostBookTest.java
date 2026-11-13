package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.config.Layer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** This PC's account book as a value of the SDK's project of this PC (host-projects phase 6). */
class HostBookTest {

    private static final AccountRecord FARM = new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "farm");
    private static final AccountRecord ALT = new AccountRecord(AccountRecord.Kind.GOOGLE, "alt@example.com");
    private static final AccountRecord FB = new AccountRecord(AccountRecord.Kind.FACEBOOK, "a player");
    private static final InstanceKey LD0 = new InstanceKey(PlatformId.LDPLAYER, "LDPlayer");
    private static final InstanceKey LD1 = new InstanceKey(PlatformId.LDPLAYER, "LDPlayer-1");
    private static final InstanceKey MUMU = new InstanceKey(PlatformId.MUMU, "MuMu-0");
    private static final Optional<String> PC = Optional.of("main@example.com");

    @TempDir
    Path state;

    /** The host's values of this PC in memory: each write counted, each batch's label noted. */
    private static final class Host implements PluginValues {
        final Map<String, Object> values = new LinkedHashMap<>();
        final List<String> steps = new ArrayList<>();
        int writes;

        @Override
        public List<String> ids() {
            return List.copyOf(values.keySet());
        }

        @Override
        public Optional<ValueContext> open(ManagedValue<?> value) {
            if (!values.containsKey(value.id())) return Optional.empty();
            return Optional.of(new ValueContext() {
                public TypeRef type() { return null; }
                public <T> Optional<T> value(Class<T> type) {
                    Object held = values.get(value.id());
                    return type.isInstance(held) ? Optional.of(type.cast(held)) : Optional.empty();
                }
                public void set(Object next) {
                    writes++;
                    values.put(value.id(), next);
                }
                public String source() { return ""; }
                public StudioServices services() { return null; }
            });
        }

        @Override
        public Optional<String> create(ManagedValue<?> value) {
            values.put(value.id(), new Object());
            return Optional.empty();
        }

        StudioServices services() {
            History recording = new History() {
                public boolean canUndo() { return false; }
                public boolean canRedo() { return false; }
                public String undoLabel() { return ""; }
                public String redoLabel() { return ""; }
                public void undo() {}
                public void redo() {}
                public AutoCloseable onChanged(Runnable listener) { return () -> {}; }
                public void batch(String label, Runnable writes) {
                    steps.add(label);
                    writes.run();
                }
            };
            PluginValues host = this;
            return new StudioServices() {
                public Path projectDir() { return null; }
                public Path resourcesDir() { return null; }
                public Theme theme() { return null; }
                public Dialogs dialogs() { return null; }
                @Override public PluginValues hostValues() { return host; }
                @Override public History hostHistory() { return recording; }
            };
        }
    }

    private final Host host = new Host();

    private HostBook book(Optional<String> pc) {
        return new HostBook(host.services(), state, () -> pc);
    }

    /** LDPlayer's layer wants farm on every LDPlayer instance; MuMu-0 wants alt and the Facebook record. */
    private static EmulatorConfig config() {
        return EmulatorConfig.EMPTY
                .withEmulator(PlatformId.LDPLAYER, Layer.EMPTY.withAccount(FARM))
                .withInstance(MUMU, Layer.EMPTY.withAccount(ALT).withAccount(FB));
    }

    private static List<String> names(List<AccountRecord> accounts) {
        return accounts.stream().map(AccountRecord::name).toList();
    }

    @Test
    void theFirstUseStartsTheBookWithThePcsAccountAndEachReadAddsWhatAConfigurationLists() throws IOException {
        HostBook book = book(PC);
        List<AccountRecord> first = book.known(config());
        assertEquals(List.of("main@example.com", "farm@example.com", "alt@example.com", "a player"), names(first));
        assertEquals(HostBook.PC_ACCOUNT_NOTE, first.getFirst().note());
        assertEquals(HostBook.STARTED, host.steps.getFirst());

        book.remove(first.getFirst().key());
        AccountRecord added = new AccountRecord(AccountRecord.Kind.MAIL, "mail@example.com");
        List<AccountRecord> later = book.known(config().withInstance(LD1, Layer.EMPTY.withAccount(added)));
        assertEquals(List.of("farm@example.com", "alt@example.com", "a player", "mail@example.com"), names(later),
                "the PC's account is offered when the book starts only");
    }

    @Test
    void addKeepsANoteUnlessGivenOneAndOneAccountPerKey() throws IOException {
        HostBook book = book(Optional.empty());
        book.add(FARM);
        book.add(new AccountRecord(AccountRecord.Kind.GOOGLE, "FARM@example.com"));
        assertEquals(List.of(FARM), book.known(EmulatorConfig.EMPTY));

        List<AccountRecord> renoted = book.add(new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "main"));
        assertEquals("main", renoted.getFirst().note());
        List<AccountRecord> cleared = book.put(new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com"));
        assertEquals("", cleared.getFirst().note(), "the user's own edit clears a note");
    }

    @Test
    void thePcsAccountIsInEvenWhenAnAddStartedTheBookAndAReadThatAddsNothingWritesNothing() throws IOException {
        HostBook book = book(PC);
        book.add(FB);
        assertEquals(List.of("main@example.com", "a player"), names(book.known(EmulatorConfig.EMPTY)));

        int written = host.writes;
        int steps = host.steps.size();
        book.known(EmulatorConfig.EMPTY.withInstance(MUMU, Layer.EMPTY.withAccount(FB)));
        assertEquals(written, host.writes, "nothing to add, no write");
        assertEquals(steps, host.steps.size(), "and no step of the undo");

        AccountRecord noted = new AccountRecord(AccountRecord.Kind.FACEBOOK, "a player", "from a configuration");
        assertEquals(noted, book.known(EmulatorConfig.EMPTY.withInstance(MUMU, Layer.EMPTY.withAccount(noted)))
                .getLast(), "an empty note in the book is filled from a configuration written before the book");
    }

    @Test
    void undoStopsAtTheStepThatStartedTheBook() throws IOException {
        Files.writeString(state.resolve(HostBook.LEGACY), "{\"accounts\":[]}");
        book(PC).add(FARM);
        assertTrue(HostBook.startedTheBook(host.steps.getFirst()));
        assertFalse(HostBook.startedTheBook(host.steps.getLast()));
    }

    @Test
    void anAccountsJsonThatDoesNotReadIsLeftAndTheBookStartsWithoutIt() throws IOException {
        Path legacy = state.resolve(HostBook.LEGACY);
        Files.writeString(legacy, "{\"accounts\":[");
        assertEquals(List.of("main@example.com"), names(book(PC).known(EmulatorConfig.EMPTY)));
        assertEquals(HostBook.STARTED, host.steps.getFirst());
        assertTrue(Files.exists(legacy), "left for the user");
        book(PC).add(FARM);
        assertTrue(Files.exists(legacy), "and never touched again");
    }

    @Test
    void eachChangeIsOneStepOfThisPcsUndo() throws IOException {
        HostBook book = book(Optional.empty());
        book.add(FARM);
        book.put(ALT);
        book.remove(ALT.key());
        assertEquals(List.of(HostBook.STARTED, "Add an account", "Change an account's note", "Remove an account"),
                host.steps);
    }

    @Test
    void anOlderSdksAccountsJsonIsMovedInAsOneCommitWithItsNotesAndThenDeleted() throws IOException {
        Path legacy = state.resolve(HostBook.LEGACY);
        Files.writeString(legacy, "{\"v\":1,\"pcSeeded\":true,\"accounts\":["
                + "{\"kind\":\"google\",\"name\":\"farm@example.com\",\"note\":\"farm\"},"
                + "{\"kind\":\"steam\",\"name\":\"player\",\"note\":\"\"},"
                + "{\"kind\":\"facebook\",\"name\":\"a player\",\"note\":\"the alt\"}]}");

        List<AccountRecord> known = book(PC).known(EmulatorConfig.EMPTY);
        assertEquals(List.of(FARM, new AccountRecord(AccountRecord.Kind.FACEBOOK, "a player", "the alt")), known,
                "the notes come in; the PC's account was offered to the file already");
        assertEquals(List.of(HostBook.MOVED_IN), host.steps.subList(0, 1));
        assertFalse(Files.exists(legacy), "accounts.json is gone");
        HostKnown written = (HostKnown) host.values.get(SdkHostValues.BOOK.id());
        assertTrue(written.list().contains(new HostKnown.Entry("steam", "player", "")),
                "a kind this build does not know comes in too");

        book(PC).add(ALT);
        assertTrue(((HostKnown) host.values.get(SdkHostValues.BOOK.id())).list()
                .contains(new HostKnown.Entry("steam", "player", "")), "and stays on the next write");
        assertEquals(1, host.steps.stream().filter(HostBook.MOVED_IN::equals).count());
    }

    @Test
    void aBookThatDoesNotReadIsNeitherReadNorWrittenOver() {
        host.values.put(SdkHostValues.BOOK.id(), "broken");
        IOException refused = assertThrows(IOException.class, () -> book(PC).add(FARM));
        assertTrue(refused.getMessage().contains("could not be read"), refused.getMessage());
        assertEquals("broken", host.values.get(SdkHostValues.BOOK.id()));
    }

    @Test
    void theInstancesAccountsNameABookEntryAndShowItsNote() {
        EmulatorConfig config = EmulatorConfig.EMPTY.withInstance(LD1, Layer.EMPTY.withAccount(FARM));
        HostConfig.Rows rows = HostConfig.rows(config, HostConfig.Rows.NONE, Map.of(FARM.key(), "farm"));
        assertEquals("", rows.accounts().list().getFirst().note(), "the row names the account, without its note");
        assertEquals("farm", HostConfig.rows(config, HostConfig.Rows.NONE, Map.of()).accounts().list().getFirst()
                .note(), "a note the book does not hold yet stays on the row, never lost");

        EmulatorConfig read = HostConfig.config(rows, Map.of(FARM.key(), "the book's"));
        assertEquals("the book's", read.instance(LD1).accounts().getFirst().note());
        HostConfig.Rows older = new HostConfig.Rows(HostSettings.none(), HostGames.none(), HostSettings.none(),
                HostGames.none(), HostLabels.none(), HostLabels.none(), HostAccounts.of(
                new HostAccounts.Account(LD1.text(), "google", "farm@example.com", "written before", false, false)));
        assertEquals("written before", HostConfig.config(older, Map.of()).instance(LD1).accounts().getFirst().note(),
                "a row written before the book keeps its note until the book has the account");
    }

    @Test
    void onInstancesFollowsTheLayersAndTheSortPutsTheFreeAccountsFirst() {
        EmulatorConfig config = config().withInstance(LD1, Layer.EMPTY.removingAccount(FARM.key()));
        Map<AccountRecord.Key, List<InstanceKey>> on = HostBook.onInstances(config, List.of(LD0, LD1, MUMU));
        assertEquals(List.of(LD0), on.get(FARM.key()), "LD-1 removed the emulator's account");
        assertEquals(List.of(MUMU), on.get(ALT.key()));

        AccountRecord free = new AccountRecord(AccountRecord.Kind.GOOGLE, "zed@example.com");
        assertEquals(List.of(free, ALT, FARM), HostBook.sorted(List.of(FARM, ALT, free), on));
    }

    @Test
    void heldIsWhatAnyLayerListsAnEmulatorsOrAnInstancesAndNotARemoval() {
        EmulatorConfig config = config().withInstance(LD1, Layer.EMPTY.removingAccount(FARM.key()));
        assertEquals(Set.of(FARM.key(), ALT.key(), FB.key()), HostBook.held(config));
        assertEquals(Set.of(), HostBook.held(EmulatorConfig.EMPTY.withInstance(LD1,
                Layer.EMPTY.removingAccount(FARM.key()))));
    }

    @Test
    void anImportAddsWhatIsMissingAndKeepsANoteTheBookHas(@TempDir Path dir) throws IOException {
        Path exported = dir.resolve("accounts-export.json");
        HostBook.export(List.of(new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "from the other PC"),
                new AccountRecord(AccountRecord.Kind.GOOGLE, "alt@example.com", "alt there"), FB), exported);

        HostBook book = book(Optional.empty());
        book.add(FARM);
        book.add(ALT);
        assertEquals(List.of(FARM, new AccountRecord(AccountRecord.Kind.GOOGLE, "alt@example.com", "alt there"), FB),
                book.importFrom(exported));
    }

    @Test
    void theExportHoldsKindNameAndNoteOnly(@TempDir Path dir) throws IOException {
        Path exported = dir.resolve("export.json");
        HostBook.export(List.of(FARM, FB), exported);
        JsonNode root = new ObjectMapper().readTree(Files.readString(exported, StandardCharsets.UTF_8));
        Set<String> top = new HashSet<>();
        root.fieldNames().forEachRemaining(top::add);
        assertEquals(Set.of("v", "accounts"), top);
        assertEquals(2, root.path("accounts").size());
        for (JsonNode entry : root.path("accounts")) {
            Set<String> keys = new HashSet<>();
            entry.fieldNames().forEachRemaining(keys::add);
            assertEquals(Set.of("kind", "name", "note"), keys);
        }
        assertFalse(Files.readString(exported).toLowerCase().contains("password"));
    }
}
