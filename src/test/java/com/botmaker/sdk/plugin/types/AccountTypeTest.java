// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.api.signin.Account;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A game account as the grammar writes it (claimed accounts, run-places phase 7c). */
class AccountTypeTest {

    private static final Account MAIN = Account.of("emu-app:com.game", "Main").note("the farm")
            .signsInWith(Account.google("main@gmail.com")).playsAt(Place.android("MEmu_1"));

    private static List<ComponentType<Account>> declared() {
        List<ComponentType<Account>> all = new ArrayList<>(List.of(SdkTypes.ACCOUNT));
        all.addAll(SdkTypes.ACCOUNT_OTHERS);
        return all;
    }

    private static <T> T roundTrip(ComponentType<T> shape, T value) {
        return shape.build(shape.components(value));
    }

    @Test
    void aLaunchTargetsAccountIsWrittenWithItsLinksAndReadBackEqual() {
        assertEquals(SdkTypes.ACCOUNT, ComponentType.writer(declared(), MAIN));
        assertEquals(MAIN, roundTrip(SdkTypes.ACCOUNT, MAIN));
        Account bare = Account.of("steam:1145350", "alt");
        assertEquals(bare, roundTrip(SdkTypes.ACCOUNT, bare));
        assertEquals(Account.none(), roundTrip(SdkTypes.ACCOUNT, Account.none()));
    }

    @Test
    void theAccountItSignsInWithIsWrittenByItsKindsFactory() {
        assertEquals(SdkTypes.ACCOUNT_OTHERS.get(0), ComponentType.writer(declared(), Account.google("a@gmail.com")));
        assertEquals(SdkTypes.ACCOUNT_OTHERS.get(1), ComponentType.writer(declared(), Account.facebook("a player")));
        assertEquals(SdkTypes.ACCOUNT_OTHERS.get(2), ComponentType.writer(declared(), Account.mail("a@mail.com")));
        Account noted = Account.google("a@gmail.com").note("work");
        assertEquals(noted, roundTrip(SdkTypes.ACCOUNT_OTHERS.get(0), noted));
    }

    @Test
    void anAccountIsKeyedByKindTargetAndLoginAndItsPasswordIsTheSecretOfThatKey() {
        assertEquals("launch-target:emu-app:com.game:main", MAIN.toString());
        assertEquals("google:main@gmail.com", Account.google(" Main@gmail.com ").toString());
        assertEquals("launch-target:emu-app:com.game:main", MAIN.password().name());
        assertTrue(Account.none().password().isBlank());
        assertTrue(Account.none().isNone());
        assertNotEquals(MAIN, MAIN.playsAt(null), "the links are parts of the value");
        assertEquals("", Account.google("a@gmail.com").target(), "only a launch target's account has one");
    }

    @Test
    void onlyALaunchTargetsAccountTakesLinksAndItSignsInWithNoOtherLaunchTargetsAccount() {
        assertThrows(IllegalArgumentException.class,
                () -> Account.of("emu-app:com.game", "main").signsInWith(Account.of("steam:1", "other")));
        assertThrows(IllegalArgumentException.class,
                () -> Account.google("a@gmail.com").playsAt(Place.desktop()));
        assertThrows(IllegalArgumentException.class,
                () -> Account.google("a@gmail.com").signsInWith(Account.mail("b@mail.com")));
    }
}
