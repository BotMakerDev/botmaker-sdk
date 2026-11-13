package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.InstanceKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GooglePickTest {

    private static final AccountRecord MAIN = new AccountRecord(AccountRecord.Kind.GOOGLE, "main@example.com",
            "This PC's Google account");
    private static final AccountRecord FARM = new AccountRecord(AccountRecord.Kind.GOOGLE, "farm@example.com");
    private static final AccountRecord ALT = new AccountRecord(AccountRecord.Kind.GOOGLE, "alt@example.com", "alt");
    private static final AccountRecord HERE = new AccountRecord(AccountRecord.Kind.GOOGLE, "here@example.com");
    private static final AccountRecord FB = new AccountRecord(AccountRecord.Kind.FACEBOOK, "a player");
    private static final InstanceKey MUMU = new InstanceKey(PlatformId.MUMU, "MuMu-0");
    private static final InstanceKey MEMU = new InstanceKey(PlatformId.MEMU, "MEmu");

    @Test
    void thePcsAccountComesFirstThenTheFreeOnesThenByNameAndTheLayersOwnAreLeftOut() {
        List<GooglePick.Choice> choices = GooglePick.choices(List.of(FARM, ALT, MAIN, HERE, FB),
                Map.of(MAIN.key(), List.of(MUMU, MEMU), FARM.key(), List.of(MUMU)), Set.of(),
                Optional.of("Main@Example.com"), Set.of(HERE.key()));
        assertEquals(List.of(MAIN, ALT, FARM), choices.stream().map(GooglePick.Choice::account).toList());
    }

    @Test
    void theDetailSaysTheNoteAndTheInstancesAndThePillWhetherThePasswordIsRemembered() {
        List<GooglePick.Choice> choices = GooglePick.choices(List.of(MAIN, ALT, FARM),
                Map.of(MAIN.key(), List.of(MUMU, MEMU), FARM.key(), List.of(MUMU)), Set.of("main@example.com"),
                Optional.empty(), Set.of());
        Map<String, GooglePick.Choice> byName = Map.of(choices.get(0).account().name(), choices.get(0),
                choices.get(1).account().name(), choices.get(1), choices.get(2).account().name(), choices.get(2));
        assertEquals("This PC's Google account · on MuMu-0, MEmu", byName.get("main@example.com").detail());
        assertEquals("alt · on no instance", byName.get("alt@example.com").detail());
        assertEquals("On MuMu-0", byName.get("farm@example.com").detail());
        assertEquals(GooglePick.Pill.REMEMBERED, byName.get("main@example.com").pill());
        assertEquals(GooglePick.Pill.ASKED, byName.get("farm@example.com").pill());
    }

    @Test
    void thePcsAccountIsOfferedEvenWhenTheBookLostIt() {
        List<GooglePick.Choice> choices = GooglePick.choices(List.of(FARM), Map.of(), Set.of(),
                Optional.of("main@example.com"), Set.of());
        assertEquals(List.of(MAIN, FARM), choices.stream().map(GooglePick.Choice::account).toList());
    }

    @Test
    void theKeysAskedOfTheStoreAreTheAccountsOffered() {
        assertEquals(Set.of("main@example.com", "farm@example.com"),
                GooglePick.keys(List.of(FARM, HERE, FB), Optional.of("Main@example.com"), Set.of(HERE.key())));
    }
}
