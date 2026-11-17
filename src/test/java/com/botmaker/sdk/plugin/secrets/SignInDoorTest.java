// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.secrets;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.api.signin.Typed;
import com.botmaker.sdk.internal.signin.SignInClient;
import com.botmaker.sdk.plugin.emulator.host.HostSecrets;
import com.botmaker.shared.emulator.google.AccountDevice;
import com.botmaker.shared.emulator.google.PasswordStore;
import com.botmaker.shared.emulator.google.Totp;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiNode;
import com.botmaker.shared.emulator.tree.UiTree;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** This PC's sign-in door over fakes: what it types where, what it refuses, and that no word of it holds a value. */
class SignInDoorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INSTANCE = "farm-1";
    private static final String PASSWORD = "hunter2-farm";
    private static final String KEY = "JBSWY3DPEHPK3PXP";
    private static final long NOW = 1_763_400_000_000L;
    private static final Rect FIELD = new Rect(100, 200, 300, 40);

    @TempDir
    Path dir;

    private final List<String> log = new CopyOnWriteArrayList<>();
    private final FakeDevice device = new FakeDevice();
    private final Map<String, char[]> kept = new HashMap<>();
    private SignInDoor door;
    private Path doorFile;

    @BeforeEach
    void open() throws IOException {
        kept.put("farm password", PASSWORD.toCharArray());
        kept.put("farm key", KEY.toCharArray());
        doorFile = dir.resolve("signin-door.json");
        door = SignInDoor.open(new Index(HostSecrets.of(new HostSecrets.Kept("Farm Password", "password"),
                        new HostSecrets.Kept("farm key", "authenticator"))), new Store(),
                instance -> INSTANCE.equals(instance) ? Optional.of(device) : Optional.empty(), doorFile, log::add,
                () -> NOW);
    }

    @AfterEach
    void close() {
        door.close();
    }

    private SignInClient client() throws IOException {
        JsonNode file = JSON.readTree(doorFile.toFile());
        return new SignInClient(file.path("port").asInt(), file.path("token").asText());
    }

    private String token() throws IOException {
        return JSON.readTree(doorFile.toFile()).path("token").asText();
    }

    @Test
    void typesTheKeptPasswordOnTheNamedDevicesPasswordField() throws IOException {
        device.focus = Set.of(Trait.FOCUSED, Trait.PASSWORD);
        Typed typed = client().type(Secret.of("farm password"), FIELD, INSTANCE);
        assertTrue(typed.isTyped(), typed.why());
        assertEquals(List.of("tap 250,220", "type " + PASSWORD), device.done);
        assertNoValueIn(typed.why());
    }

    @Test
    void typesAnAuthenticatorsCurrentCode() throws IOException {
        Typed typed = client().type(Secret.of("Farm Key"), FIELD, INSTANCE);
        assertTrue(typed.isTyped(), typed.why());
        String code = new String(Totp.code(Totp.decode(KEY.toCharArray()).orElseThrow(), NOW));
        assertEquals(List.of("tap 250,220", "type " + code), device.done);
        assertTrue(log.stream().noneMatch(line -> line.contains(code)), "no code logged: " + log);
    }

    @Test
    void refusesAPasswordIntoAFieldThatIsNotOne() throws IOException {
        device.focus = Set.of(Trait.FOCUSED);
        Typed typed = client().type(Secret.of("farm password"), FIELD, INSTANCE);
        assertFalse(typed.isTyped());
        assertEquals("the focused field isn't a password field", typed.why());
        assertEquals(List.of("tap 250,220"), device.done);
    }

    @Test
    void refusesASecretNotKept() throws IOException {
        Typed typed = client().type(Secret.of("other password"), FIELD, INSTANCE);
        assertEquals("no secret \"other password\" kept on this PC: add it in the secret picker", typed.why());
        kept.remove("farm password");
        assertEquals("no secret \"farm password\" kept on this PC: add it in the secret picker",
                client().type(Secret.of("farm password"), FIELD, INSTANCE).why());
        assertEquals(List.of(), device.done);
    }

    @Test
    void refusesAnInstanceNotRunningHere() throws IOException {
        Typed typed = client().type(Secret.of("farm password"), FIELD, "farm-2");
        assertEquals("the instance \"farm-2\" isn't running on this PC", typed.why());
        assertEquals("\"farm password\" is typed only into an emulator instance, and the run named none",
                client().type(Secret.of("farm password"), FIELD, "").why());
        assertEquals(List.of(), device.done);
    }

    @Test
    void refusesAWrongTokenAndSaysWhetherSignedIn() throws IOException {
        assertEquals("wrong token", door.answer("{\"op\":\"type\",\"token\":\"nope\"}").path("why").asText());
        device.accounts = Set.of("farm@example.com");
        JsonNode answer = door.answer(JSON.createObjectNode().put("token", token()).put("op", "signed-in")
                .put("email", "Farm@Example.com").put("instance", INSTANCE).toString());
        assertTrue(answer.path("signedIn").asBoolean(), answer.toString());
    }

    @Test
    void noAnswerNorLogLineHoldsAValue() throws IOException {
        device.focus = Set.of(Trait.FOCUSED, Trait.PASSWORD);
        client().type(Secret.of("farm password"), FIELD, INSTANCE);
        client().type(Secret.of("farm key"), FIELD, INSTANCE);
        device.focus = Set.of();
        assertNoValueIn(client().type(Secret.of("farm password"), FIELD, INSTANCE).why());
        assertFalse(log.isEmpty());
        log.forEach(SignInDoorTest::assertNoValueIn);
    }

    @Test
    void closingDeletesItsDoorFile() {
        assertTrue(Files.isRegularFile(doorFile));
        door.close();
        assertFalse(Files.exists(doorFile));
    }

    private static void assertNoValueIn(String words) {
        assertFalse(words.contains(PASSWORD) || words.contains(KEY), "a value in: " + words);
    }

    private record Index(HostSecrets secrets) implements KeptSecrets.Index {
        @Override
        public HostSecrets read() {
            return secrets;
        }

        @Override
        public void change(String label, UnaryOperator<HostSecrets> edit) {
            throw new UnsupportedOperationException();
        }
    }

    /** Hands a copy of each kept value, as the keyring does: the door clears it. */
    private final class Store implements PasswordStore {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Optional<char[]> read(String key) {
            return Optional.ofNullable(kept.get(key)).map(char[]::clone);
        }

        @Override
        public boolean remember(String key, char[] value) {
            return false;
        }

        @Override
        public void forget(String key) {
        }
    }

    /** Records each tap and what was typed, in full: only the test reads it. */
    private static final class FakeDevice implements AccountDevice {
        final List<String> done = new CopyOnWriteArrayList<>();
        volatile Set<Trait> focus = Set.of();
        volatile Set<String> accounts = Set.of();

        @Override
        public Optional<UiTree> screen() {
            UiNode field = new UiNode("", "", "", "android.widget.EditText", "com.google.android.gms",
                    new Rectangle(100, 200, 300, 40), focus, List.of());
            return Optional.of(new UiTree(List.of(field)));
        }

        @Override
        public void tap(int x, int y) {
            done.add("tap " + x + "," + y);
        }

        @Override
        public void type(char[] text) {
            done.add("type " + new String(text));
        }

        @Override
        public void key(int keyCode) {
            done.add("key " + keyCode);
        }

        @Override
        public void shell(String command) {
        }

        @Override
        public Set<String> accounts() {
            return accounts;
        }

        @Override
        public void close() {
        }
    }
}
