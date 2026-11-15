package com.botmaker.sdk.plugin.emulator.signin;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.signin.NeedsYou;
import com.botmaker.sdk.api.signin.SecondStep;
import com.botmaker.sdk.api.signin.SignIn;
import com.botmaker.sdk.internal.signin.SignInClient;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.AccountDevice;
import com.botmaker.shared.emulator.google.AuthenticatorKeys;
import com.botmaker.shared.emulator.google.InstanceSignIn;
import com.botmaker.shared.emulator.google.PasswordStore;
import com.botmaker.shared.emulator.google.Totp;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignInHostTest {

    private static final String EMAIL = "someone@example.com";
    private static final char[] PASSWORD = "not-a-real-one-7".toCharArray();
    private static final String CODE = "481516";
    /** A made-up setup key, Base32 as Google shows them. */
    private static final String KEY = "JBSWY3DPEHPK3PXP";
    private static final Rect FIELD = new Rect(100, 200, 300, 40);

    private final FakeDevice device = new FakeDevice();
    private final FakeContext context = new FakeContext();
    private final AtomicInteger asked = new AtomicInteger();
    /** Mid-window, so the window before and the next are both a step away. */
    private final AtomicLong clock = new AtomicLong(1_700_000_010_000L);
    private SignInHost host;

    @AfterEach
    void close() {
        if (host != null) host.close();
    }

    private SignInClient open(Verification method) throws IOException {
        return open(method, PasswordStore.NONE);
    }

    private SignInClient open(Verification method, PasswordStore keys) throws IOException {
        InstanceSignIn.SecondStep second = new InstanceSignIn.SecondStep(email -> method, (email, how, attempt) -> {
            asked.incrementAndGet();
            return CODE.toCharArray();
        }, new AuthenticatorKeys(keys));
        return client(SignInHost.open(device, "  SomeOne@Example.com ", PASSWORD, second, context, clock::get));
    }

    private SignInClient client(SignInHost opened) {
        host = opened;
        Map<String, String> env = host.environment();
        return new SignInClient(Integer.parseInt(env.get(SignInClient.PORT_VARIABLE)),
                env.get(SignInClient.TOKEN_VARIABLE));
    }

    @Test
    void answersTheRequestWithAnIdAndTheMethodOnly() throws IOException {
        Optional<SignIn.Request> request = open(Verification.CODE).request();
        assertTrue(request.isPresent());
        assertEquals(SecondStep.CODE, request.get().method());
        assertFalse(request.get().id().contains("example"));
    }

    @Test
    void typesTheEmailAndPasswordInTheFieldsMiddle() throws IOException {
        SignInClient client = open(Verification.PHONE);
        assertTrue(client.type(SignInClient.Value.EMAIL, FIELD).isTyped());
        assertTrue(client.type(SignInClient.Value.PASSWORD, FIELD).isTyped());
        assertEquals(List.of("tap 250,220", "type " + EMAIL, "tap 250,220", "type " + new String(PASSWORD)),
                device.done);
        // the password is the caller's: it is cleared by the batch, not by the host
        assertArrayEquals("not-a-real-one-7".toCharArray(), PASSWORD);
    }

    @Test
    void noSecretInAnyByteSentBack() throws IOException {
        open(Verification.CODE);
        Map<String, String> env = host.environment();
        String token = env.get(SignInClient.TOKEN_VARIABLE);
        int port = Integer.parseInt(env.get(SignInClient.PORT_VARIABLE));
        String field = ",\"field\":{\"x\":1,\"y\":1,\"width\":10,\"height\":10}";
        StringBuilder heard = new StringBuilder();
        for (String message : List.of(
                "{\"token\":\"" + token + "\",\"op\":\"request\"}",
                "{\"token\":\"" + token + "\",\"op\":\"type\",\"value\":\"email\"" + field + "}",
                "{\"token\":\"" + token + "\",\"op\":\"type\",\"value\":\"password\"" + field + "}",
                "{\"token\":\"" + token + "\",\"op\":\"type\",\"value\":\"code\"" + field + "}",
                "{\"token\":\"" + token + "\",\"op\":\"type\",\"value\":\"password\"}",
                "{\"token\":\"" + token + "\",\"op\":\"signed-in\"}",
                "{\"token\":\"" + token + "\",\"op\":\"needs-you\",\"kind\":\"captcha\"}",
                "{\"token\":\"wrong\",\"op\":\"type\",\"value\":\"password\"" + field + "}",
                "{\"token\":\"" + token + "\",\"op\":\"what\"}",
                "not json")) {
            heard.append(raw(port, message)).append('\n');
        }
        String said = heard.toString().toLowerCase();
        for (String secret : List.of(EMAIL, "someone", new String(PASSWORD), CODE, token)) {
            assertFalse(said.contains(secret.toLowerCase()), "an answer held a secret");
        }
        assertTrue(device.done.contains("type " + CODE), "the code was typed, not said");
    }

    @Test
    void asksTheCodeOncePerTypingAndTypesTheNextOverTheRefusedOne() throws IOException {
        SignInClient client = open(Verification.CODE);
        assertTrue(client.type(SignInClient.Value.CODE, FIELD).isTyped());
        assertEquals(1, asked.get());
        assertEquals(List.of("tap 250,220", "type " + CODE), device.done);
        device.done.clear();
        assertTrue(client.type(SignInClient.Value.CODE, FIELD).isTyped());
        assertEquals(2, asked.get());
        assertEquals("key 123", device.done.get(1), "a refused code is cleared before the next");
        assertTrue(context.resumes.get() >= 2, "each prompt's needs-you is ended");
    }

    @Test
    void anAuthenticatorKeyGivesThisWindowsCodeTheOneBeforeThenTheNextOnceItCame() throws IOException {
        SignInClient client = open(Verification.AUTHENTICATOR, new OneKey());
        assertEquals(SecondStep.AUTHENTICATOR, client.request().orElseThrow().method());
        long window = Totp.window(clock.get());
        assertTrue(client.type(SignInClient.Value.CODE, FIELD).isTyped());
        assertTrue(client.type(SignInClient.Value.CODE, FIELD).isTyped());
        // the next window's code waits for its window: the clock moves there
        clock.set((window + 1) * Totp.STEP.toMillis());
        assertTrue(client.type(SignInClient.Value.CODE, FIELD).isTyped());
        assertEquals(List.of("type " + codeAt(window), "type " + codeAt(window - 1), "type " + codeAt(window + 1)),
                device.done.stream().filter(done -> done.startsWith("type")).toList());
        assertEquals(0, asked.get());
        // the key gave all it may: the code is asked
        client.type(SignInClient.Value.CODE, FIELD);
        assertEquals(1, asked.get());
    }

    @Test
    void aCodeThatCannotBeTypedIsAskedAgainUncounted() throws IOException {
        List<String> answers = new ArrayList<>(List.of("48 15 16 ", CODE));
        InstanceSignIn.SecondStep second = new InstanceSignIn.SecondStep(email -> Verification.CODE,
                (email, how, attempt) -> {
                    asked.incrementAndGet();
                    assertEquals(1, attempt, "an untypeable code spends no try");
                    return answers.removeFirst().toCharArray();
                });
        SignInClient client = client(SignInHost.open(device, EMAIL, PASSWORD, second, context));
        assertTrue(client.type(SignInClient.Value.CODE, FIELD).isTyped());
        assertEquals(2, asked.get());
        assertEquals(List.of("tap 250,220", "type " + CODE), device.done);
    }

    @Test
    void aDeclinedPromptLeavesTheUserAskedUntilTheNextCall() throws IOException {
        InstanceSignIn.SecondStep second = new InstanceSignIn.SecondStep(email -> Verification.CODE,
                (email, how, attempt) -> null);
        SignInClient client = client(SignInHost.open(device, EMAIL, PASSWORD, second, context));
        assertFalse(client.type(SignInClient.Value.CODE, FIELD).isTyped());
        assertEquals(0, context.resumes.get());
        client.signedIn();
        assertEquals(1, context.resumes.get());
    }

    @Test
    void aDeviceThatThrowsRefusesTheCallAndTheHostServesOn() throws IOException {
        SignInClient client = open(Verification.PHONE);
        device.gone.set(true);
        assertFalse(client.signedIn());
        device.gone.set(false);
        assertTrue(client.request().isPresent());
        assertTrue(host.open());
    }

    @Test
    void aLineTooLongIsRefusedUnread() throws IOException {
        SignInClient client = open(Verification.PHONE);
        int port = Integer.parseInt(host.environment().get(SignInClient.PORT_VARIABLE));
        try {
            assertTrue(raw(port, "x".repeat(10_000)).contains("\"ok\":false"));
        } catch (IOException reset) {
            // the host closed with the rest unread, which the OS may answer by a reset: refused either way
        }
        assertTrue(client.request().isPresent(), "the host serves on");
    }

    private static String codeAt(long window) {
        return new String(Totp.code(Totp.decode(KEY.toCharArray()).orElseThrow(), window * Totp.STEP.toMillis()));
    }

    @Test
    void aWrongTokenTypesNothing() throws IOException {
        open(Verification.PHONE);
        SignInClient stranger = new SignInClient(Integer.parseInt(host.environment()
                .get(SignInClient.PORT_VARIABLE)), "0".repeat(64));
        assertFalse(stranger.type(SignInClient.Value.PASSWORD, FIELD).isTyped());
        assertFalse(stranger.request().isPresent());
        assertEquals(List.of(), device.done);
    }

    @Test
    void cancelClosesIt() throws Exception {
        SignInClient client = open(Verification.PHONE);
        context.cancelled.set(true);
        for (int i = 0; i < 50 && host.open(); i++) Thread.sleep(100);
        assertFalse(host.open());
        assertFalse(client.type(SignInClient.Value.PASSWORD, FIELD).isTyped());
        assertEquals(List.of(), device.done);
    }

    @Test
    void signedInOnceAndroidListsTheAccountAndNeedsYouIsEndedByTheNextCall() throws IOException {
        SignInClient client = open(Verification.PHONE);
        assertFalse(client.signedIn());
        client.needsYou(NeedsYou.CAPTCHA);
        assertEquals(List.of(NeedsYou.CAPTCHA.displayName()), context.needs);
        device.accounts.add(EMAIL);
        assertTrue(client.signedIn());
        assertEquals(1, context.resumes.get());
        assertEquals(Optional.of(EMAIL), host.added());
    }

    @Test
    void anUntypeableValueIsRefusedUntyped() throws IOException {
        SignInClient client = client(SignInHost.open(device, EMAIL, "pässword".toCharArray(),
                InstanceSignIn.SecondStep.ASK, context));
        assertFalse(client.type(SignInClient.Value.PASSWORD, FIELD).isTyped());
        assertEquals(List.of(), device.done);
    }

    private static String raw(int port, String line) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write((line + "\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            String answer = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
            return answer == null ? "" : answer;
        }
    }

    /** Records what the host did to it, a typed value in full: only the test reads it. */
    private static final class FakeDevice implements AccountDevice {
        final List<String> done = new CopyOnWriteArrayList<>();
        final Set<String> accounts = new CopyOnWriteArraySet<>();
        /** Whether Android stopped answering, as dadb says it: by throwing. */
        final AtomicBoolean gone = new AtomicBoolean();

        @Override
        public Optional<UiTree> screen() {
            return Optional.empty();
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
            done.add("shell " + command);
        }

        @Override
        public Set<String> accounts() {
            if (gone.get()) throw new IllegalStateException("device offline");
            return Set.copyOf(accounts);
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeContext implements Operation.Context {
        final List<String> needs = new ArrayList<>();
        final AtomicInteger resumes = new AtomicInteger();
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void step(String words) {
        }

        @Override
        public void progress(double fraction) {
        }

        @Override
        public synchronized void needsYou(String why) {
            needs.add(why);
        }

        @Override
        public void resumed() {
            resumes.incrementAndGet();
        }

        @Override
        public boolean cancelled() {
            return cancelled.get();
        }
    }

    /** A store holding one made-up authenticator key, under any address. */
    private static final class OneKey implements PasswordStore {
        private final Map<String, char[]> kept = new HashMap<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Optional<char[]> read(String email) {
            return Optional.of(KEY.toCharArray());
        }

        @Override
        public boolean remember(String email, char[] password) {
            kept.put(email, password.clone());
            return true;
        }

        @Override
        public void forget(String email) {
            kept.remove(email);
        }
    }
}
