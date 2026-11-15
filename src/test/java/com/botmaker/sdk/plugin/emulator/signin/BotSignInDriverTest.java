package com.botmaker.sdk.plugin.emulator.signin;

import com.botmaker.plugin.api.run.SideRun;
import com.botmaker.plugin.api.run.SideRuns;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.signin.NeedsYou;
import com.botmaker.sdk.api.signin.Refusal;
import com.botmaker.sdk.internal.config.InstalledGame;
import com.botmaker.sdk.internal.signin.SignInClient;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.AccountDevice;
import com.botmaker.shared.emulator.google.InstanceSignIn;
import com.botmaker.shared.emulator.google.InstanceSignIn.End;
import com.botmaker.shared.emulator.google.SignInDriver.Attempt;
import com.botmaker.shared.emulator.tree.UiTree;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotSignInDriverTest {

    private static final String EMAIL = "someone@example.com";
    private static final char[] PASSWORD = "not-a-real-one-7".toCharArray();
    private static final Rect FIELD = new Rect(100, 200, 300, 40);
    private static final EmulatorInstance INSTANCE = new EmulatorInstance(PlatformId.LDPLAYER, "LDPlayer-1",
            "127.0.0.1", 5555);
    private static final Path BOT = Path.of("google-sign-in");
    private static final InstanceSignIn.SecondStep ASK = InstanceSignIn.SecondStep.ASK;

    private final FakeDevice device = new FakeDevice();
    private final FakeContext context = new FakeContext();
    private final AtomicLong clock = new AtomicLong(1_700_000_000_000L);

    @Test
    void aRunThatSignsInEndsSignedInAndTheHostClosesAfter() throws Exception {
        FakeRuns runs = new FakeRuns(client -> device.accounts.add(EMAIL));

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.SIGNED_IN, attempt.result().end());
        assertNull(attempt.result().listedAs());
        assertEquals("shell " + BotSignInDriver.ADD_ACCOUNT, device.done.getFirst(), "the bot starts on Android's page");
        assertEquals(BOT, runs.started);
        assertEquals(Map.of(InstalledGame.CAPTURE_EMULATOR, INSTANCE.reference()), runs.properties);
        assertTrue(runs.environment.containsKey(SignInClient.PORT_VARIABLE), "the host is named in the environment");
        assertTrue(runs.client.request().isEmpty(), "the host no longer answers once the sign-in ended");
    }

    @Test
    void anotherAddressAndroidListsIsTheOneListedAs() throws Exception {
        FakeRuns runs = new FakeRuns(client -> device.accounts.add("other@example.com"));

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.SIGNED_IN, attempt.result().end());
        assertEquals("other@example.com", attempt.result().listedAs());
    }

    @Test
    void aRefusalTheRunToldIsTheEnd() throws Exception {
        FakeRuns runs = new FakeRuns(client -> {
            assertTrue(client.type(SignInClient.Value.PASSWORD, FIELD).isTyped());
            client.refused(Refusal.WRONG_PASSWORD);
        });

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.WRONG_PASSWORD, attempt.result().end());
        assertEquals(Refusal.WRONG_PASSWORD.displayName(), attempt.result().words());
        assertFalse(attempt.passwordAccepted());
    }

    @Test
    void aSecondStepAskedAfterThePasswordMeansGoogleTookIt() throws Exception {
        FakeRuns runs = new FakeRuns(client -> {
            client.type(SignInClient.Value.PASSWORD, FIELD);
            client.needsYou(NeedsYou.PHONE);
            client.refused(Refusal.REFUSED);
        });

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.REFUSED, attempt.result().end());
        assertTrue(attempt.passwordAccepted());
    }

    @Test
    void aRunThatEndsSayingNothingFailed() throws Exception {
        Attempt attempt = driver(new FakeRuns(client -> { })).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.FAILED, attempt.result().end());
    }

    @Test
    void cancelStopsTheRunAndLeavesTheInstanceHome() throws Exception {
        FakeRuns runs = new FakeRuns(client -> context.cancelled.set(true));
        runs.endsAfterBody = false;

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.CANCELLED, attempt.result().end());
        assertTrue(runs.stopped.get());
        assertEquals("key " + BotSignInDriver.KEY_HOME, device.done.getLast());
    }

    @Test
    void anyEndLeavesTheInstanceHome() throws Exception {
        driver(new FakeRuns(client -> { })).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals("key " + BotSignInDriver.KEY_HOME, device.done.getLast());
    }

    @Test
    void aRunCutShortAfterAndroidAddedTheAccountIsSignedIn() throws Exception {
        FakeRuns runs = new FakeRuns(client -> {
            device.accounts.add(EMAIL);
            clock.addAndGet(BotSignInDriver.LIMIT.toMillis() + 1);
        });
        runs.endsAfterBody = false;

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.SIGNED_IN, attempt.result().end());
        assertTrue(attempt.passwordAccepted());
    }

    @Test
    void aRunPastItsTimeIsStopped() throws Exception {
        FakeRuns runs = new FakeRuns(client -> clock.addAndGet(BotSignInDriver.LIMIT.toMillis() + 1));
        runs.endsAfterBody = false;

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.TIMED_OUT, attempt.result().end());
        assertTrue(runs.stopped.get());
    }

    @Test
    void theUsersTimeIsNotTheRuns() throws Exception {
        FakeRuns runs = new FakeRuns(client -> client.needsYou(NeedsYou.CAPTCHA));
        runs.endsAfterBody = false;
        // each look is a minute further: past the run's five, short of the user's fifteen, the run goes on
        runs.eachAwait = () -> {
            clock.addAndGet(Duration.ofMinutes(1).toMillis());
            if (clock.get() > 1_700_000_000_000L + Duration.ofMinutes(8).toMillis()) runs.running.set(false);
        };

        Attempt attempt = driver(runs).signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.FAILED, attempt.result().end(), "ended by the run, not timed out");
    }

    @Test
    void noBotStartsNothing() throws Exception {
        FakeRuns runs = new FakeRuns(client -> { });
        BotSignInDriver driver = new BotSignInDriver(() -> runs, () -> {
            throw new IOException("offline, and none was got before");
        }, clock::get, Duration.ZERO);

        Attempt attempt = driver.signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.FAILED, attempt.result().end());
        assertTrue(attempt.result().words().contains("offline, and none was got before"), attempt.result().words());
        assertNull(runs.started);
    }

    @Test
    void aBotThatDoesNotStartFailedWithWhy() throws Exception {
        SideRuns refusing = (bot, properties, environment) -> {
            throw new IOException("javac: 2 errors");
        };
        BotSignInDriver driver = new BotSignInDriver(() -> refusing, () -> BOT, clock::get, Duration.ZERO);

        Attempt attempt = driver.signIn(INSTANCE, device, EMAIL, PASSWORD, ASK, context);

        assertEquals(End.FAILED, attempt.result().end());
        assertTrue(attempt.result().words().contains("javac: 2 errors"), attempt.result().words());
    }

    private BotSignInDriver driver(FakeRuns runs) {
        return new BotSignInDriver(() -> runs, () -> BOT, clock::get, Duration.ZERO);
    }

    /** One run: its body plays the bot's calls on the first look, then it ends (or runs on until stopped). */
    private static final class FakeRuns implements SideRuns, SideRun {
        private final Consumer<SignInClient> body;
        final AtomicBoolean running = new AtomicBoolean();
        final AtomicBoolean stopped = new AtomicBoolean();
        boolean endsAfterBody = true;
        Runnable eachAwait = () -> { };
        Path started;
        Map<String, String> properties;
        Map<String, String> environment;
        SignInClient client;
        private boolean played;

        FakeRuns(Consumer<SignInClient> body) {
            this.body = body;
        }

        @Override
        public SideRun start(Path bot, Map<String, String> properties, Map<String, String> environment) {
            started = bot;
            this.properties = properties;
            this.environment = environment;
            client = new SignInClient(Integer.parseInt(environment.get(SignInClient.PORT_VARIABLE)),
                    environment.get(SignInClient.TOKEN_VARIABLE));
            running.set(true);
            return this;
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public OptionalInt await(Duration limit) {
            if (!played) {
                played = true;
                body.accept(client);
                if (endsAfterBody) running.set(false);
            } else {
                eachAwait.run();
            }
            return running.get() ? OptionalInt.empty() : OptionalInt.of(0);
        }

        @Override
        public void stop() {
            stopped.set(true);
            running.set(false);
        }
    }

    private static final class FakeDevice implements AccountDevice {
        final List<String> done = new CopyOnWriteArrayList<>();
        final Set<String> accounts = new CopyOnWriteArraySet<>();

        @Override
        public Optional<UiTree> screen() {
            return Optional.empty();
        }

        @Override
        public void tap(int x, int y) {
            done.add("tap");
        }

        @Override
        public void type(char[] text) {
            done.add("type " + text.length + " characters");
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
            return Set.copyOf(accounts);
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeContext implements Operation.Context {
        final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void step(String words) {
        }

        @Override
        public void progress(double fraction) {
        }

        @Override
        public void needsYou(String why) {
        }

        @Override
        public void resumed() {
        }

        @Override
        public boolean cancelled() {
            return cancelled.get();
        }
    }
}
