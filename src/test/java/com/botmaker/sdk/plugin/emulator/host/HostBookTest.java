// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.internal.config.GoogleReads;
import com.botmaker.sdk.internal.config.HostGoogleReads;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
    void aPastedListIsOneStepAndKeepsTheNotesOfTheAccountsListedAlready() throws IOException {
        HostBook book = book(Optional.empty());
        book.add(new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "kept"));
        int steps = host.steps.size();

        List<AccountRecord> after = book.addAll(List.of(new AccountRecord(AccountRecord.Kind.GOOGLE, "FARM@example.com"),
                new AccountRecord(AccountRecord.Kind.GOOGLE, "new@example.com"),
                new AccountRecord(AccountRecord.Kind.GOOGLE, "other@example.com")));

        assertEquals(List.of("farm@example.com", "new@example.com", "other@example.com"), names(after));
        assertEquals("kept", after.getFirst().note());
        assertEquals(steps + 1, host.steps.size(), "one step of undo for the whole list");
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
    void theListedNoteIsTheNoteALayerGivesAnAccountEmptyForNone() {
        EmulatorConfig config = EmulatorConfig.EMPTY.withInstance(LD1, Layer.EMPTY.withAccount(FARM).withAccount(ALT));
        assertEquals("farm", HostBook.listedNote(config, FARM.key()));
        assertEquals("", HostBook.listedNote(config, ALT.key()), "listed with no note");
        assertEquals("", HostBook.listedNote(config, FB.key()), "not listed");
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
    void theExportHoldsKindNameNoteAliasesAndVerificationOnly(@TempDir Path dir) throws IOException {
        Path exported = dir.resolve("export.json");
        HostBook.export(List.of(FARM.withAlias("f@example.com").withVerify(Verification.CODE), FB), exported);
        JsonNode root = new ObjectMapper().readTree(Files.readString(exported, StandardCharsets.UTF_8));
        Set<String> top = new HashSet<>();
        root.fieldNames().forEachRemaining(top::add);
        assertEquals(Set.of("v", "accounts"), top);
        assertEquals(2, root.path("accounts").size());
        for (JsonNode entry : root.path("accounts")) {
            Set<String> keys = new HashSet<>();
            entry.fieldNames().forEachRemaining(keys::add);
            assertEquals(Set.of("kind", "name", "note", "aliases", "verify"), keys);
        }
        assertEquals("f@example.com", root.path("accounts").get(0).path("aliases").get(0).asText());
        assertEquals("code", root.path("accounts").get(0).path("verify").asText());
        assertFalse(Files.readString(exported).toLowerCase().contains("password"));
    }

    /** The authenticator method travels; its key, kept in the credential store only, never does. */
    @Test
    void anAuthenticatorAccountExportsItsMethodAndNoKey(@TempDir Path dir) throws IOException {
        Path exported = dir.resolve("export.json");
        HostBook.export(List.of(FARM.withVerify(Verification.AUTHENTICATOR)), exported);
        JsonNode entry = new ObjectMapper().readTree(Files.readString(exported, StandardCharsets.UTF_8))
                .path("accounts").get(0);
        Set<String> keys = new HashSet<>();
        entry.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("kind", "name", "note", "aliases", "verify"), keys);
        assertEquals("authenticator", entry.path("verify").asText());
        assertFalse(Files.readString(exported).toLowerCase().contains("secret"));
    }

    @Test
    void aliasesAndTheVerificationAreWrittenAsTextAndReadBack() throws IOException {
        HostBook book = book(Optional.empty());
        book.add(FARM);
        book.addAlias(FARM.key(), "F.Arm@example.com");
        book.verify(FARM.key(), Verification.BACKUP_CODE);
        HostKnown written = (HostKnown) host.values.get(SdkHostValues.BOOK.id());
        assertEquals(new HostKnown.Entry("google", "farm@example.com", "farm", "f.arm@example.com", "backup-code"),
                written.list().getFirst());

        AccountRecord read = book.known(EmulatorConfig.EMPTY).getFirst();
        assertEquals(List.of("f.arm@example.com"), read.aliases());
        assertEquals(Verification.BACKUP_CODE, read.verify());

        book.put(new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "renoted"));
        book.add(new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "again"));
        book.known(EmulatorConfig.EMPTY.withInstance(LD1, Layer.EMPTY.withAccount(FARM)));
        AccountRecord after = book.known(EmulatorConfig.EMPTY).getFirst();
        assertEquals("again", after.note());
        assertEquals(List.of("f.arm@example.com"), after.aliases(), "a note's edit keeps the aliases");
        assertEquals(Verification.BACKUP_CODE, after.verify(), "and the verification");
    }

    @Test
    void anEntryWrittenBeforeAliasesReadsWithNoneAndAVerificationOfANewerBotMakerIsKept() throws IOException {
        host.values.put(SdkHostValues.BOOK.id(), HostKnown.of(new HostKnown.Entry("google", "farm@example.com", "farm"),
                new HostKnown.Entry("google", "alt@example.com", "", "", "security-key")));
        HostBook book = book(Optional.empty());
        List<AccountRecord> known = book.known(EmulatorConfig.EMPTY);
        assertEquals(FARM, known.getFirst());
        assertEquals(Verification.UNKNOWN, known.getLast().verify());

        book.put(new AccountRecord(AccountRecord.Kind.GOOGLE, "alt@example.com", "noted"));
        assertEquals("security-key", ((HostKnown) host.values.get(SdkHostValues.BOOK.id())).list().getLast().verify());
    }

    @Test
    void eachAddressFollowsItsEntrysMethodThroughAnyAliasElseTheDefault() throws IOException {
        HostBook book = book(Optional.empty());
        assertEquals(Verification.PHONE, book.methods().apply("farm@example.com"), "no book yet: the first default");
        assertTrue(host.values.isEmpty(), "reading the methods starts no book");

        book.addAll(List.of(FARM, ALT));
        book.addAlias(FARM.key(), "f.arm@example.com");
        book.verify(FARM.key(), Verification.CODE);
        book.defaultVerification(Verification.BACKUP_CODE);
        Function<String, Verification> methods = book.methods();
        assertEquals(Verification.CODE, methods.apply("farm@example.com"));
        assertEquals(Verification.CODE, methods.apply("f.arm@example.com"), "by its alias");
        assertEquals(Verification.BACKUP_CODE, methods.apply("alt@example.com"), "Default follows the book's");
        assertEquals(Verification.BACKUP_CODE, methods.apply("new@example.com"), "one the book lacks too");
        assertEquals(Verification.ASK, HostBook.methods(List.of(ALT.withVerify(Verification.UNKNOWN)),
                Verification.PHONE).apply("alt@example.com"), "a newer BotMaker's is asked");
        assertEquals(Verification.PHONE, HostBook.methods(List.of(new AccountRecord(AccountRecord.Kind.FACEBOOK,
                "farm@example.com").withVerify(Verification.CODE)), Verification.PHONE).apply("farm@example.com"),
                "only a Google entry answers");
        Function<String, String> keyedAs = book.keyedAs();
        assertEquals("farm@example.com", keyedAs.apply("F.ARM@example.com"), "an alias's key is its account's");
        assertEquals("alt@example.com", keyedAs.apply("alt@example.com"));
        assertEquals("new@example.com", keyedAs.apply("new@example.com"), "one the book lacks keeps its own");
    }

    @Test
    void anAddressAnotherEntryHasAsAnAliasIsRefused() throws IOException {
        HostBook book = book(Optional.empty());
        book.addAll(List.of(FARM, ALT));
        book.addAlias(FARM.key(), "shared@example.com");
        IOException refused = assertThrows(IOException.class, () -> book.addAlias(ALT.key(), "SHARED@example.com"));
        assertTrue(refused.getMessage().contains("farm@example.com"), refused.getMessage());
        assertEquals(List.of("alt@example.com"), book.addAlias(FARM.key(), "alt@example.com").getFirst().aliases()
                .subList(1, 2), "another entry's main address is taken; merging it is offered");
        assertEquals(List.of("alt@example.com"), book.removeAlias(FARM.key(), "shared@example.com").getFirst()
                .aliases());
    }

    @Test
    void theDefaultVerificationIsThePhonePromptUntilSetAndOneStep() throws IOException {
        HostBook book = book(Optional.empty());
        assertEquals(Verification.PHONE, book.defaultVerification());
        book.defaultVerification(Verification.CODE);
        assertEquals(Verification.CODE, book.defaultVerification());
        assertEquals("code", host.values.get(SdkHostValues.DEFAULT_VERIFICATION.id()));
        assertEquals("Change the default verification", host.steps.getLast());
        assertThrows(IOException.class, () -> book.defaultVerification(Verification.DEFAULT));

        host.values.put(SdkHostValues.DEFAULT_VERIFICATION.id(), "security-key");
        assertEquals(Verification.PHONE, book.defaultVerification(), "an unknown default reads as the first");
    }

    @Test
    void aMergeMovesTheAddressesTheNoteAndTheVerificationInOneStepAndLeavesTheInstances() throws IOException {
        AccountRecord one = new AccountRecord(AccountRecord.Kind.GOOGLE, "p.one@example.com", "the alt", List.of(),
                Verification.CODE);
        HostBook book = book(Optional.empty());
        book.addAll(List.of(FARM.withAlias("f@example.com"), one));
        HostAccounts rows = HostAccounts.of(
                new HostAccounts.Account(MUMU.text(), "google", "P.One@example.com", "", false, true),
                new HostAccounts.Account(LD1.text(), "google", "farm@example.com", "", false, false));
        host.values.put(SdkHostValues.ACCOUNTS.id(), rows);
        int steps = host.steps.size();

        List<AccountRecord> after = book.merge(FARM.key(), one.key());

        AccountRecord merged = new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com", "farm · the alt",
                List.of("f@example.com", "p.one@example.com"), Verification.CODE);
        assertEquals(List.of(merged), after);
        assertEquals(rows, host.values.get(SdkHostValues.ACCOUNTS.id()),
                "MuMu-0 keeps the address Android lists it under, which farm answers to now");
        assertEquals(List.of(merged), book.known(EmulatorConfig.EMPTY.withInstance(MUMU, Layer.EMPTY.withAccount(
                new AccountRecord(AccountRecord.Kind.GOOGLE, "P.One@example.com")))),
                "an instance naming an alias adds no entry of its own");
        assertEquals(steps + 1, host.steps.size(), "one step of undo");
        assertThrows(IOException.class, () -> book.merge(FARM.key(), FARM.key()));
        assertThrows(IOException.class, () -> book.merge(FARM.key(), FB.key()));
    }

    @Test
    void recordingTheAddressAndroidListsFoldsItsOwnEntryIntoTheAccountTyped() throws IOException {
        HostBook book = book(Optional.empty());
        book.add(FARM);
        // the sign-in renamed MuMu-0's account to the address Android lists, and the next read noted it
        AccountRecord listed = new AccountRecord(AccountRecord.Kind.GOOGLE, "Listed@example.com", "farm");
        EmulatorConfig config = EmulatorConfig.EMPTY.withInstance(MUMU, Layer.EMPTY.withAccount(listed));
        assertEquals(List.of("farm@example.com", "Listed@example.com"), names(book.known(config)));
        int steps = host.steps.size();

        List<AccountRecord> after = book.recordAlias(FARM.key(), "Listed@example.com");

        assertEquals(List.of(FARM.withAlias("listed@example.com")), after, "one entry, its note kept once");
        assertEquals(steps + 1, host.steps.size());
        assertEquals(after, book.known(config));
        assertEquals(after, book.recordAlias(FARM.key(), "listed@example.com"), "recorded twice is recorded once");
        book.add(ALT);
        assertThrows(IOException.class, () -> book.recordAlias(ALT.key(), "listed@example.com"),
                "farm has it as an alias already");
        AccountRecord.Key byAlias = new AccountRecord.Key(AccountRecord.Kind.GOOGLE, "Listed@example.com");
        assertEquals(FARM.withAlias("listed@example.com").withAlias("third@example.com"),
                book.recordAlias(byAlias, "Third@example.com").stream()
                        .filter(one -> one.key().equals(FARM.key())).findFirst().orElseThrow(),
                "a sign-in by an alias records on the entry that answers to it");
    }

    @Test
    void sharedListsTheInstancesHoldingOneAccountThroughTwoOfItsAddresses() {
        AccountRecord farm = FARM.withAlias("f@example.com");
        EmulatorConfig config = EmulatorConfig.EMPTY
                .withInstance(LD0, Layer.EMPTY.withAccount(FARM))
                .withInstance(LD1, Layer.EMPTY.withAccount(new AccountRecord(AccountRecord.Kind.GOOGLE, "F@example.com")))
                .withInstance(MUMU, Layer.EMPTY.withAccount(FARM).withAccount(ALT));
        List<HostBook.Shared> shared = HostBook.shared(config, List.of(LD0, LD1, MUMU), List.of(farm, ALT));
        assertEquals(1, shared.size());
        assertEquals(farm, shared.getFirst().account());
        assertEquals(Map.of(LD0, "farm@example.com", LD1, "F@example.com", MUMU, "farm@example.com"),
                shared.getFirst().through());

        assertEquals(List.of(), HostBook.shared(config, List.of(LD0, MUMU), List.of(farm, ALT)),
                "two instances on one address are not hidden from each other");
    }

    @Test
    void anImportMergesTheAliasesOfAnEntryItHolds(@TempDir Path dir) throws IOException {
        Path exported = dir.resolve("accounts-export.json");
        HostBook.export(List.of(FARM.withAlias("b@example.com").withVerify(Verification.ASK)), exported);
        HostBook book = book(Optional.empty());
        book.add(FARM.withAlias("a@example.com"));
        AccountRecord after = book.importFrom(exported).getFirst();
        assertEquals(List.of("a@example.com", "b@example.com"), after.aliases());
        assertEquals(Verification.ASK, after.verify(), "the file's verification over the default");
        assertEquals("farm", after.note());
    }

    private static final AccountRecord GAME_MAIN = AccountRecord.ofLaunchTarget("emu-app:com.game", "main", "farm");

    @Test
    void aLaunchTargetsAccountIsWrittenWithItsLinksAndReadBack() throws IOException {
        HostBook book = book(Optional.empty());
        book.add(FARM);
        book.add(GAME_MAIN);
        book.add(AccountRecord.ofLaunchTarget("steam:12345", "main", ""));
        assertEquals(3, book.known(EmulatorConfig.EMPTY).size(), "farm, and one main per launch target");

        book.link(GAME_MAIN.key(), Optional.of(FARM.key()), "this-pc/android/MEmu_1");
        HostKnown written = (HostKnown) host.values.get(SdkHostValues.BOOK.id());
        assertEquals(new HostKnown.Entry("launch-target", "main", "farm", "", "", "emu-app:com.game",
                "google:farm@example.com", "this-pc/android/MEmu_1"), written.list().get(1));
        assertEquals(new HostKnown.Entry("google", "farm@example.com", "farm"), written.list().getFirst(),
                "a Google entry is written as before");

        AccountRecord read = book.known(EmulatorConfig.EMPTY).get(1);
        assertEquals(GAME_MAIN.key(), read.key());
        assertEquals(Optional.of(FARM.key()), read.signsInWith());
        assertEquals("this-pc/android/MEmu_1", read.playsAt());
        assertEquals("Change an account's links", host.steps.getLast());

        book.put(read.withNote("renoted"));
        AccountRecord renoted = book.known(EmulatorConfig.EMPTY).get(1);
        assertEquals("renoted", renoted.note());
        assertEquals("this-pc/android/MEmu_1", renoted.playsAt(), "a note's edit keeps the links");

        book.link(GAME_MAIN.key(), Optional.empty(), "");
        AccountRecord unlinked = book.known(EmulatorConfig.EMPTY).get(1);
        assertTrue(unlinked.signsInWith().isEmpty());
        assertEquals("", unlinked.playsAt());
    }

    @Test
    void aLinkIsRefusedToAnEntryTheBookLacksAndOnAnAccountOfAnotherKind() throws IOException {
        HostBook book = book(Optional.empty());
        book.add(FARM);
        book.add(GAME_MAIN);
        IOException gone = assertThrows(IOException.class, () -> book.link(GAME_MAIN.key(),
                Optional.of(new AccountRecord.Key(AccountRecord.Kind.GOOGLE, "nobody@example.com")), ""));
        assertEquals("nobody@example.com is not in this PC's account book.", gone.getMessage());
        assertThrows(IOException.class, () -> book.link(FARM.key(), Optional.empty(), "this-pc/desktop"));
    }

    @Test
    void aLaunchTargetRowWithNoTargetOrALinkThatDoesNotReadIsReadAsBestItCan() throws IOException {
        host.values.put(SdkHostValues.BOOK.id(), HostKnown.of(
                new HostKnown.Entry("launch-target", "lost", "", "", "", "", "", ""),
                new HostKnown.Entry("launch-target", "main", "", "", "", "emu-app:com.game", "newer:x", "")));
        List<AccountRecord> known = book(Optional.empty()).known(EmulatorConfig.EMPTY);
        assertEquals(List.of("main"), names(known), "no launch target: kept as written, not read");
        assertTrue(known.getFirst().signsInWith().isEmpty(), "a link that does not read is none");
        book(Optional.empty()).add(FARM);
        assertEquals("lost", ((HostKnown) host.values.get(SdkHostValues.BOOK.id())).list().getLast().name(),
                "the row this build does not read is kept");
    }

    @Test
    void theExportCarriesALaunchTargetsTargetAndSignInButNeverItsPlace(@TempDir Path dir) throws IOException {
        Path exported = dir.resolve("export.json");
        HostBook.export(List.of(FARM, GAME_MAIN.withSignsInWith(Optional.of(FARM.key()))
                .withPlaysAt("this-pc/android/MEmu_1")), exported);
        JsonNode game = new ObjectMapper().readTree(Files.readString(exported, StandardCharsets.UTF_8))
                .path("accounts").get(1);
        assertEquals("emu-app:com.game", game.path("target").asText());
        assertEquals("google:farm@example.com", game.path("signsInWith").asText());
        assertTrue(game.path("playsAt").isMissingNode(), "a place is this PC's");

        HostBook book = book(Optional.empty());
        book.add(FARM);
        AccountRecord imported = book.importFrom(exported).getLast();
        assertEquals(GAME_MAIN.key(), imported.key());
        assertEquals(Optional.of(FARM.key()), imported.signsInWith());
        assertEquals("", imported.playsAt());
    }

    @Test
    void anImportedLinkToAnEntryNeitherTheFileNorTheBookHoldsIsDropped(@TempDir Path dir) throws IOException {
        Path exported = dir.resolve("export.json");
        HostBook.export(List.of(GAME_MAIN.withSignsInWith(Optional.of(FARM.key()))), exported);
        AccountRecord imported = book(Optional.empty()).importFrom(exported).getLast();
        assertEquals(GAME_MAIN.key(), imported.key());
        assertEquals(Optional.empty(), imported.signsInWith());
    }

    @Test
    void aLaunchTargetsAccountSignsInWithNoOtherLaunchTargetsAccount() throws IOException {
        HostBook book = book(Optional.empty());
        AccountRecord other = AccountRecord.ofLaunchTarget("steam:12345", "main", "");
        book.add(GAME_MAIN);
        book.add(other);
        IOException refused = assertThrows(IOException.class,
                () -> book.link(GAME_MAIN.key(), Optional.of(other.key()), ""));
        assertEquals("main cannot sign in with another launch target's account.", refused.getMessage());
    }

    @Test
    void eachInstancesGoogleReadIsNotedOnlyWhenItChangedAsOneStepUndoStopsAt() {
        StudioServices services = host.services();
        Instant first = Instant.parse("2026-11-27T10:00:00Z");
        assertEquals(Optional.empty(), HostConfig.notedGoogle(services,
                Map.of("MEmu_1", Set.of("Farm@Example.com"), "MEmu_2", Set.of()), first));
        HostGoogleReads kept = (HostGoogleReads) host.values.get(GoogleReads.VALUE.id());
        assertEquals(new GoogleReads.Read(Set.of("farm@example.com"), first),
                GoogleReads.last(kept, "MEmu_1").orElseThrow());
        assertEquals(Set.of(), GoogleReads.last(kept, "MEmu_2").orElseThrow().accounts());
        String label = host.steps.getLast();
        assertTrue(HostConfig.followsTheEmulator(label), label);

        int steps = host.steps.size();
        int writes = host.writes;
        HostConfig.notedGoogle(services, Map.of("MEmu_1", Set.of("farm@example.com")), first.plusSeconds(60));
        assertEquals(writes, host.writes, "the same accounts read again write nothing");
        assertEquals(first, GoogleReads.last((HostGoogleReads) host.values.get(GoogleReads.VALUE.id()), "MEmu_1")
                .orElseThrow().at(), "the time is when Android first listed them so");

        HostConfig.notedGoogle(services, Map.of("MEmu_1", Set.of("other@example.com")), first.plusSeconds(120));
        assertEquals(steps + 2, host.steps.size());
        assertEquals(Set.of("other@example.com"), GoogleReads.last(
                (HostGoogleReads) host.values.get(GoogleReads.VALUE.id()), "MEmu_1").orElseThrow().accounts());
    }
}
