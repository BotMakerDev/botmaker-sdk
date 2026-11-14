package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.EmulatorInstances;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Kind;
import com.botmaker.shared.emulator.config.EmulatorConfig;
import com.botmaker.shared.emulator.config.InstanceKey;
import com.botmaker.shared.emulator.google.AccountChooser;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * After the host starts a game on an instance (▶ Launch, a Fix's start, the assistant's {@code launch_game}): Google's
 * account picker, should the game open it, answered with the instance's default Google account ({@link AccountChooser}),
 * matched by any of the book's addresses for it. A bot's run reads no account, so its own start leaves the picker to the
 * user (2026-11-14, the user's call). What it came to is said in words that name no address.
 */
public final class ChooserWatch {

    /** The instances watched now, by the name the spec gives: a second start while one is watched adds none. */
    private static final Set<String> WATCHED = ConcurrentHashMap.newKeySet();

    private ChooserWatch() {}

    /**
     * Watches for the picker on {@code spec}'s instance, on a thread of its own, when the spec is an emulator app and
     * the instance has a default Google account; {@code said} hears the outcome once, unless no picker came up.
     */
    public static void after(LaunchSpec spec, StudioServices services, Consumer<String> said) {
        if (spec == null || spec.kind() != LaunchKind.EMULATOR_APP) return;
        String name = spec.emulatorInstance();
        String game = spec.emulatorPackage();
        if (name == null || game == null || !WATCHED.add(name)) return;
        Thread.ofVirtual().name("account-chooser").start(() -> {
            try {
                watch(name, game, services).ifPresent(said);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                // its message may carry an address: the words say none
                said.accept("The game's account chooser was not watched: the instance's Google account could not be "
                        + "read.");
            } finally {
                WATCHED.remove(name);
            }
        });
    }

    private static Optional<String> watch(String name, String game, StudioServices services)
            throws IOException, InterruptedException {
        Optional<EmulatorInstance> instance = EmulatorInstances.find(name);
        if (instance.isEmpty()) return Optional.empty();
        EmulatorConfig config = HostConfig.load(services).config();
        Optional<String> first = config.wanted(InstanceKey.of(instance.get())).defaultGoogle();
        if (first.isEmpty()) return Optional.empty();
        AccountRecord account = account(HostConfig.accounts(services).known(config), first.get());
        AccountChooser.Outcome outcome = AccountChooser.watch(instance.get(), account, game);
        return outcome == AccountChooser.Outcome.NONE ? Optional.empty() : Optional.of(outcome.displayName() + ".");
    }

    /** The book's Google entry answering to {@code address}, with its aliases; else the address alone. */
    static AccountRecord account(List<AccountRecord> book, String address) {
        return book.stream().filter(one -> one.kind() == Kind.GOOGLE && one.answersTo(address)).findFirst()
                .orElseGet(() -> new AccountRecord(Kind.GOOGLE, address));
    }
}
