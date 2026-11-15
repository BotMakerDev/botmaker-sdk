package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.AdbDevice;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.PlatformId;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ApplyHost;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.AuthenticatorKeys;
import com.botmaker.shared.emulator.google.Codes;
import com.botmaker.shared.emulator.google.InstanceChanges;
import com.botmaker.shared.emulator.google.InstanceSignIn;
import com.botmaker.shared.emulator.google.KeyringStore;
import com.botmaker.shared.emulator.google.Passwords;
import com.botmaker.shared.emulator.google.SignInPace;
import com.botmaker.shared.emulator.manage.InstanceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The whole plan on this PC, for real: two instances of each emulator the window shows, made as <i>Make two of each</i>
 * makes them, each signed into one Google account as Apply signs it in (paced, readied, its second step by the key
 * BotMaker holds), then each checked by who answers on its address ({@link AdbDevice#connect(EmulatorInstance)}
 * refuses another product or an instance whose BotMaker ID file is not its own) and the account it holds.
 *
 * <p>Runs only with {@code BOTMAKER_LIVE_GOOGLE} set to the accounts to use, comma-separated, two per emulator shown:
 * the emulators by name, each one's first two instances by name, as <i>Make two of each</i> counts them. Each account
 * needs its password remembered on this PC and its authenticator key held (the Accounts window), so nothing is asked.
 * An instance already holding another Google account stops the test before any sign-in. The test counts against the
 * day's cap of 8 sign-ins and takes the 5–10 minute gaps. No email is printed or asserted in a message: an account is
 * named "account N".
 */
@EnabledIfEnvironmentVariable(named = EndToEndLiveTest.ACCOUNTS, matches = ".*\\S.*")
class EndToEndLiveTest {

    static final String ACCOUNTS = "BOTMAKER_LIVE_GOOGLE";
    private static final int EACH = 2;
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final AuthenticatorKeys KEYS = new AuthenticatorKeys(new KeyringStore(AuthenticatorKeys.SERVICE));

    /** Says each step on standard output, emails masked. */
    private record Said(String who) implements Operation.Context {
        @Override
        public void step(String words) {
            System.out.println(who + ": " + masked(words));
        }

        @Override
        public void progress(double fraction) {
        }

        @Override
        public void needsYou(String why) {
            step("Needs you: " + why);
        }

        @Override
        public void resumed() {
            step("Resumed.");
        }

        @Override
        public boolean cancelled() {
            return Thread.currentThread().isInterrupted();
        }
    }

    @Test
    void twoOfEachEmulatorEachSignedIntoOneAccountAndAnsweringAsItself() throws Exception {
        List<String> accounts = Arrays.stream(System.getenv(ACCOUNTS).split(",")).map(String::strip)
                .filter(one -> !one.isEmpty()).toList();
        List<PlatformId> products = EmulatorsModel.SHOWN.stream()
                .sorted(Comparator.comparing(PlatformId::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
        assertTrue(accounts.size() >= products.size() * EACH, products.size() * EACH + " accounts are needed, "
                + accounts.size() + " given");
        for (int i = 0; i < accounts.size(); i++) {
            assertTrue(KEYS.has(accounts.get(i)), "account " + (i + 1) + " has no authenticator key held");
        }
        Optional<String> capped = SignInPace.live().capped();
        assertTrue(capped.isEmpty(), () -> capped.orElse(""));

        // make what each emulator lacks, as Make two of each does
        List<EmulatorInstance> instances = new ArrayList<>();
        for (PlatformId product : products) {
            InstanceManager.Console console = assertInstanceOf(InstanceManager.Console.class,
                    InstanceManager.forProduct(product), product.displayName() + " has no console");
            InstanceManager.Prepare light = InstanceManager.Prepare.light(ApplyHost.live(), EmulatorsWindow.applies());
            for (String name : InstanceManager.Console.missingNames(product, of(product), EACH)) {
                assertNull(console.make(name, EACH, light, made -> {}).run(new Said(name)), "Make " + name);
            }
            List<EmulatorInstance> have = of(product);
            assertTrue(have.size() >= EACH, product.displayName() + " has " + have.size() + " instances");
            instances.addAll(have.subList(0, EACH));
        }

        List<EmulatorInstance> started = new ArrayList<>();
        try {
            // before any sign-in: each answers as itself and holds no account but its own
            for (int i = 0; i < instances.size(); i++) {
                Set<String> held = held(instances.get(i), started);
                held.remove(accounts.get(i).toLowerCase(Locale.ROOT));
                assertTrue(held.isEmpty(), instances.get(i).name() + " already holds " + held.size()
                        + " other Google account(s): the test signs nothing in");
            }

            signIn(instances, accounts);

            // who answers: each instance as itself, holding its account and only it
            for (int i = 0; i < instances.size(); i++) {
                Set<String> held = held(instances.get(i), started);
                assertTrue(held.contains(accounts.get(i).toLowerCase(Locale.ROOT)),
                        instances.get(i).name() + " does not hold account " + (i + 1));
                assertEquals(1, held.size(), instances.get(i).name() + " holds other accounts");
            }
        } finally {
            List<String> unstopped = new ArrayList<>();
            for (EmulatorInstance one : started) {
                String failed = ApplyHost.live().stop(one);
                if (failed != null) unstopped.add(one.name() + ": " + failed);
            }
            assertTrue(unstopped.isEmpty(), "not stopped: " + unstopped);
        }
    }

    /**
     * One account each, each instance on its own lane as Apply's operations are: the pace lets one sign in at a time
     * while the next readies. The first failure cancels the rest, so no further sign-in is spent.
     */
    private static void signIn(List<EmulatorInstance> instances, List<String> accounts) throws Exception {
        InstanceSignIn.SecondStep second = new InstanceSignIn.SecondStep(
                email -> AccountRecord.Verification.AUTHENTICATOR, Codes.NONE, KEYS);
        try (Passwords passwords = new Passwords(new KeyringStore(), email -> null)) {
            ExecutorService lanes = Executors.newVirtualThreadPerTaskExecutor();
            try {
                List<Future<String>> ends = new ArrayList<>();
                for (int i = 0; i < instances.size(); i++) {
                    EmulatorInstance instance = instances.get(i);
                    List<ConfigDiff.AccountChange> one = List.of(new ConfigDiff.AccountChange(
                            new AccountRecord(AccountRecord.Kind.GOOGLE, accounts.get(i)),
                            ConfigDiff.AccountChange.Op.ADD));
                    ends.add(lanes.submit(() -> new InstanceChanges(InstanceChanges.Host.live()).run(instance, one,
                            List.of(), passwords, second, InstanceChanges.ListedAs.NONE, new Said(instance.name()))));
                }
                for (int i = 0; i < ends.size(); i++) {
                    String failed = ends.get(i).get();
                    if (failed != null) {
                        fail(instances.get(i).name() + " with account " + (i + 1) + ": " + masked(failed));
                    }
                }
            } finally {
                lanes.shutdownNow();
            }
        }
    }

    /** The Google accounts {@code instance} holds, started first when it is stopped and added to {@code started}. */
    private static Set<String> held(EmulatorInstance instance, List<EmulatorInstance> started) throws Exception {
        if (!ApplyHost.live().running(instance)) {
            assertNull(ApplyHost.live().start(instance, new Said(instance.name())), "start " + instance.name());
            started.add(instance);
        }
        try (AdbDevice device = AdbDevice.connect(instance)) {
            Set<String> held = AndroidState.googleAccounts(device.shell(AndroidState.ACCOUNTS));
            assertNotNull(held, instance.name() + " did not list its accounts");
            return held;
        }
    }

    /** {@code product}'s instances on this PC, by name. */
    private static List<EmulatorInstance> of(PlatformId product) {
        return Platforms.discoverDetailed().instances().stream().filter(one -> one.platformId() == product)
                .sorted(Comparator.comparing(EmulatorInstance::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static String masked(String words) {
        return EMAIL.matcher(words).replaceAll("account@masked");
    }
}
