// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.plugin.emulator.host.KeptSecrets.Form;
import com.botmaker.sdk.plugin.emulator.host.KeptSecrets.Result;
import com.botmaker.shared.emulator.google.PasswordStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** <i>Add secret…</i>: the value into the store, name and kind into the index, every array cleared. */
class KeptSecretsTest {

    /** A credential store in memory, keyed as the real one. */
    private static final class Store implements PasswordStore {
        final Map<String, char[]> kept = new HashMap<>();
        boolean available = true;
        boolean refuses;

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Optional<char[]> read(String name) {
            char[] value = kept.get(name.strip().toLowerCase(Locale.ROOT));
            return value == null ? Optional.empty() : Optional.of(value.clone());
        }

        @Override
        public boolean remember(String name, char[] value) {
            if (refuses) return false;
            kept.put(name.strip().toLowerCase(Locale.ROOT), value.clone());
            return true;
        }

        @Override
        public void forget(String name) {
            kept.remove(name.strip().toLowerCase(Locale.ROOT));
        }
    }

    /** The index in memory. */
    private static final class Index implements KeptSecrets.Index {
        HostSecrets rows = HostSecrets.none();
        boolean fails;

        @Override
        public HostSecrets read() {
            return rows;
        }

        @Override
        public void change(String label, UnaryOperator<HostSecrets> edit) throws IOException {
            if (fails) throw new IOException("disk full");
            rows = edit.apply(rows);
        }
    }

    private final Store store = new Store();
    private final Index index = new Index();

    @Test
    void addsThenUpdatesOneSecretWhateverItsCase() throws Exception {
        assertEquals(Result.ADDED, KeptSecrets.add(index, store,
                new Form("farm password", SecretKind.PASSWORD, "first".toCharArray())));
        assertEquals(Result.UPDATED, KeptSecrets.add(index, store,
                new Form(" Farm Password ", SecretKind.PASSWORD, "second".toCharArray())));
        assertEquals(1, index.rows.list().size());
        assertArrayEquals("second".toCharArray(), store.read("farm password").orElseThrow());
        assertEquals(List.of(new KeptSecrets.Listed(Secret.of("farm password"), SecretKind.PASSWORD, true)),
                KeptSecrets.list(index, store));
    }

    @Test
    void aMoveTakesTheValueToTheNewNameAndTheOldLeaves() throws Exception {
        KeptSecrets.add(index, store, new Form("launch-target:emu-app:com.game:main", SecretKind.PASSWORD,
                "hunter2".toCharArray()));
        assertEquals(Result.ADDED, KeptSecrets.move(index, store, Secret.of("launch-target:emu-app:com.game:main"),
                Secret.of("launch-target:emu-app:com.game:farm")));
        assertEquals(List.of(new KeptSecrets.Listed(Secret.of("launch-target:emu-app:com.game:farm"),
                SecretKind.PASSWORD, true)), KeptSecrets.list(index, store));
        assertEquals(Optional.empty(), store.read("launch-target:emu-app:com.game:main"));

        assertEquals(Result.REFUSED, KeptSecrets.move(index, store, Secret.of("none kept"), Secret.of("other")),
                "nothing kept: nothing moves");
        assertEquals(1, index.rows.list().size());

        KeptSecrets.add(index, store, new Form("taken", SecretKind.PASSWORD, "mine".toCharArray()));
        assertEquals(Result.REFUSED, KeptSecrets.move(index, store, Secret.of("launch-target:emu-app:com.game:farm"),
                Secret.of("taken")), "a move never overwrites a kept value");
        assertArrayEquals("mine".toCharArray(), store.read("taken").orElseThrow());
        assertArrayEquals("hunter2".toCharArray(), store.read("launch-target:emu-app:com.game:farm").orElseThrow());
    }

    @Test
    void aForgetTheStoreIgnoresThrowsAndKeepsTheRow() throws Exception {
        KeptSecrets.add(index, store, new Form("pin", SecretKind.PASSWORD, "1".toCharArray()));
        PasswordStore stuck = new PasswordStore() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public Optional<char[]> read(String name) {
                return store.read(name);
            }

            @Override
            public boolean remember(String name, char[] value) {
                return false;
            }

            @Override
            public void forget(String name) {}
        };
        assertThrows(IOException.class, () -> KeptSecrets.forget(index, stuck, Secret.of("pin")));
        assertEquals(1, index.rows.list().size(), "still listed, so still forgettable");
        KeptSecrets.forget(index, store, Secret.of("pin"));
        assertTrue(index.rows.list().isEmpty());
    }

    @Test
    void theIndexHoldsNoValue() throws Exception {
        KeptSecrets.add(index, store, new Form("pin", SecretKind.PASSWORD, "4321".toCharArray()));
        assertFalse(index.rows.toString().contains("4321"));
        assertEquals(new HostSecrets.Kept("pin", "password"), index.rows.list().getFirst());
    }

    @Test
    void clearsTheValueItIsHanded() throws Exception {
        char[] value = "secret".toCharArray();
        char[] key = "JBSW Y3DP EHPK 3PXP".toCharArray();
        KeptSecrets.add(index, store, new Form("a", SecretKind.PASSWORD, value));
        KeptSecrets.add(index, store, new Form("b", SecretKind.AUTHENTICATOR, key));
        assertArrayEquals(new char[value.length], value);
        assertArrayEquals(new char[key.length], key);
    }

    @Test
    void anAuthenticatorKeepsItsKeyCanonical() throws Exception {
        assertEquals(Result.ADDED, KeptSecrets.add(index, store,
                new Form("farm code", SecretKind.AUTHENTICATOR, "jbsw y3dp ehpk 3pxp".toCharArray())));
        assertArrayEquals("JBSWY3DPEHPK3PXP".toCharArray(), store.read("farm code").orElseThrow());
        assertEquals(SecretKind.AUTHENTICATOR, KeptSecrets.list(index, store).getFirst().kind());
    }

    @Test
    void aBadNameKeepsNothing() throws Exception {
        char[] value = "secret".toCharArray();
        assertEquals(Result.INVALID_NAME, KeptSecrets.add(index, store, new Form("  ", SecretKind.PASSWORD, value)));
        assertEquals(Result.INVALID_NAME, KeptSecrets.add(index, store,
                new Form("two\nlines", SecretKind.PASSWORD, "x".toCharArray())));
        assertEquals(Result.INVALID_NAME, KeptSecrets.add(index, store,
                new Form("n".repeat(KeptSecrets.MAX_NAME + 1), SecretKind.PASSWORD, "x".toCharArray())));
        assertTrue(index.rows.list().isEmpty());
        assertTrue(store.kept.isEmpty());
        assertArrayEquals(new char[value.length], value, "cleared even when refused");
    }

    @Test
    void noStoreKeepsNothing() throws Exception {
        store.available = false;
        assertEquals(Result.NO_KEYRING, KeptSecrets.add(index, store,
                new Form("pin", SecretKind.PASSWORD, "1".toCharArray())));
        assertTrue(index.rows.list().isEmpty());
    }

    @Test
    void aBadKeyKeepsNothing() throws Exception {
        assertEquals(Result.NOT_A_KEY, KeptSecrets.add(index, store,
                new Form("code", SecretKind.AUTHENTICATOR, "nope!".toCharArray())));
        assertTrue(index.rows.list().isEmpty());
        assertTrue(store.kept.isEmpty());
    }

    @Test
    void noValueOrARefusingStoreKeepsNothing() throws Exception {
        assertEquals(Result.REFUSED, KeptSecrets.add(index, store, new Form("pin", SecretKind.PASSWORD, new char[0])));
        store.refuses = true;
        assertEquals(Result.REFUSED, KeptSecrets.add(index, store,
                new Form("pin", SecretKind.PASSWORD, "1".toCharArray())));
        assertTrue(index.rows.list().isEmpty());
    }

    @Test
    void anIndexThatFailsPutsTheValueBack() throws Exception {
        KeptSecrets.add(index, store, new Form("pin", SecretKind.PASSWORD, "old".toCharArray()));
        index.fails = true;
        assertThrows(IOException.class, () -> KeptSecrets.add(index, store,
                new Form("pin", SecretKind.PASSWORD, "new".toCharArray())));
        assertArrayEquals("old".toCharArray(), store.read("pin").orElseThrow());
        assertThrows(IOException.class, () -> KeptSecrets.add(index, store,
                new Form("other", SecretKind.PASSWORD, "new".toCharArray())));
        assertTrue(store.read("other").isEmpty(), "a value the store lacked is forgotten again");
    }

    @Test
    void aSecretTheStoreLacksIsListedAsNotOnThisPc() throws Exception {
        index.rows = HostSecrets.of(new HostSecrets.Kept("elsewhere", "password"), new HostSecrets.Kept("odd", "x"));
        List<KeptSecrets.Listed> listed = KeptSecrets.list(index, store);
        assertFalse(listed.getFirst().stored());
        assertEquals(SecretKind.UNKNOWN, listed.get(1).kind(), "a kind a newer BotMaker wrote reads as unknown");
    }

    @Test
    void namesThatDifferByCaseAreOneSecret() throws Exception {
        index.rows = HostSecrets.of(new HostSecrets.Kept("Pin", "password"), new HostSecrets.Kept("pin", "authenticator"));
        assertEquals(List.of(new KeptSecrets.Listed(Secret.of("Pin"), SecretKind.PASSWORD, false)),
                KeptSecrets.list(index, store));
        KeptSecrets.add(index, store, new Form("PIN", SecretKind.PASSWORD, "1".toCharArray()));
        assertEquals(List.of(new HostSecrets.Kept("PIN", "password")), index.rows.list(), "the repeats go with it");
    }

    @Test
    void aListedSecretWhoseOldValueDidNotReadKeepsTheNewOne() throws Exception {
        index.rows = HostSecrets.of(new HostSecrets.Kept("pin", "password"));
        index.fails = true;
        assertThrows(IOException.class, () -> KeptSecrets.add(index, store,
                new Form("pin", SecretKind.PASSWORD, "new".toCharArray())));
        assertArrayEquals("new".toCharArray(), store.read("pin").orElseThrow());
    }

    @Test
    void closedSetsReadTheirIds() {
        assertEquals(Result.ADDED, Result.fromId("added"));
        assertEquals(Result.UNKNOWN, Result.fromId("nope"));
        assertEquals(2, Arrays.stream(Result.values()).filter(Result::kept).count());
        assertEquals(SecretKind.AUTHENTICATOR, SecretKind.fromId("authenticator"));
        assertEquals(SecretKind.UNKNOWN, SecretKind.fromId("nope"));
    }
}
