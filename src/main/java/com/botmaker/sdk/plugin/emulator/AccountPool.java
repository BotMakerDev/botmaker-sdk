// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.run.PluginPool;
import com.botmaker.sdk.api.capture.Place;
import com.botmaker.sdk.api.signin.Account;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.config.GoogleReads;
import com.botmaker.sdk.internal.config.HostGoogleReads;
import com.botmaker.sdk.internal.config.PlaceKeys;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.params.LaunchTargets;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.launch.LaunchKind;
import com.botmaker.shared.launch.LaunchSpec;
import com.botmaker.shared.vm.VmInventory;
import com.botmaker.shared.vm.VmRecord;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The SDK's second pool, {@code SdkPools.ACCOUNTS} (spec {@code 2026-11-27-claimed-accounts-design.md} § Dealing): each
 * of several runs is dealt one of this PC's book's accounts for the bot's launch targets into its {@code account()},
 * after its place, and the account follows the place. It reads the book and each instance's last-read Google accounts,
 * never a device.
 */
public final class AccountPool {

    private AccountPool() {}

    /**
     * What a deal knows of this PC besides the accounts, for {@link #left} and {@link #note}.
     *
     * @param instances the instances this PC knows, by name: a {@code playsAt} naming another counts as none
     * @param vms       the game VMs that are set up, by name: a {@code playsAt} naming another counts as none
     * @param reads     by instance name, the Google accounts Android last said it holds, lower case; an instance never
     *                  read is absent
     */
    public record Pc(Set<String> instances, Set<String> vms, Map<String, Set<String>> reads) {
        public Pc {
            instances = Set.copyOf(instances);
            vms = Set.copyOf(vms);
            Map<String, Set<String>> copy = new HashMap<>();
            reads.forEach((name, read) -> copy.put(name, read.stream().map(AccountPool::folded)
                    .collect(Collectors.toUnmodifiableSet())));
            reads = Map.copyOf(copy);
        }

        /** A PC with no game VM. */
        public Pc(Set<String> instances, Map<String, Set<String>> reads) {
            this(instances, Set.of(), reads);
        }
    }

    // ---- through the host ----

    /**
     * The candidates: the book's launch targets' accounts for the bot's games, else no account, which any run takes.
     */
    static List<Account> candidates(StudioServices services) {
        List<AccountRecord> book;
        try {
            book = HostConfig.accounts(services).kept();
        } catch (IOException unread) {
            book = List.of();
        }
        return candidates(book, LaunchTargets.all(services));
    }

    /** The accounts {@code picks}' place can play, linked first ({@link #left(List, Optional, Pc)}). */
    static List<Account> left(List<Account> free, PluginPool.Picks picks, StudioServices services) {
        return left(free, picks.of(SdkValues.PLACE), pc(services));
    }

    /** Why the row dealt {@code account} after {@code picks} wants a look ({@link #note(Account, Optional, Pc)}). */
    static String note(Account account, PluginPool.Picks picks, StudioServices services) {
        return note(account, picks.of(SdkValues.PLACE), pc(services)).orElse(null);
    }

    /** As many runs as ask share no account; each other one is one run's. */
    static int runsOf(Account account) {
        return account.isNone() ? PluginPool.Sharing.ANY : 1;
    }

    /** This PC as a deal and the Accounts window read it. Reads its values and lists its VMs, so never on FX. */
    public static Pc pc(StudioServices services) {
        Set<String> instances = SdkPools.instances(HostConfig.load(services));
        Set<String> vms = VmInventory.list().stream().filter(vm -> vm.stage() == VmRecord.Stage.READY)
                .map(VmRecord::name).collect(Collectors.toSet());
        HostGoogleReads kept = HostConfig.googleReads(services);
        Map<String, Set<String>> reads = new HashMap<>();
        for (HostGoogleReads.Row row : kept.list()) {
            GoogleReads.last(kept, row.instance()).ifPresent(read -> reads.put(row.instance(), read.accounts()));
        }
        return new Pc(instances, vms, reads);
    }

    // ---- pure ----

    /**
     * The launch targets' accounts of {@code book} whose target is one of {@code targets}, in the book's order, each as
     * the bot is given it; {@link Account#none()} alone when there is none, so a bot with no account kept is dealt as
     * before accounts.
     */
    static List<Account> candidates(List<AccountRecord> book, List<String> targets) {
        Set<String> games = targets.stream().map(AccountPool::game).collect(Collectors.toSet());
        List<Account> out = book.stream()
                .filter(record -> record.kind() == AccountRecord.Kind.LAUNCH_TARGET
                        && games.contains(game(record.target())))
                .map(AccountPool::account).toList();
        return out.isEmpty() ? List.of(Account.none()) : out;
    }

    /** The game a launch target starts, as accounts are kept for it: an emulator app's without its instance. */
    private static String game(String target) {
        String spec = target == null ? "" : target.strip();
        LaunchSpec parsed = LaunchSpec.parse(spec);
        return parsed != null && parsed.kind() == LaunchKind.EMULATOR_APP ? parsed.withoutInstance().spec() : spec;
    }

    /**
     * {@code record}, a launch target's account, as {@code Account}: a {@code playsAt} that does not read, or an
     * instance naming none, is none.
     */
    public static Account account(AccountRecord record) {
        Account account = Account.of(record.target(), record.name()).note(record.note());
        Optional<Account> with = record.signsInWith().map(key -> switch (key.kind()) {
            case GOOGLE -> Account.google(key.name());
            case FACEBOOK -> Account.facebook(key.name());
            case MAIL -> Account.mail(key.name());
            case LAUNCH_TARGET, UNKNOWN -> null;
        });
        Optional<Place> at = PlaceKeys.parse(record.playsAt())
                .filter(place -> place.kind() != Place.Kind.ANDROID || place.namesAndroid());
        return account.signsInWith(with.orElse(null)).playsAt(at.orElse(null));
    }

    /**
     * Those of {@code free} that can join a row dealt {@code at}, in the order to try them (spec § Dealing):
     *
     * <ol>
     *   <li>the accounts that play at {@code at};</li>
     *   <li>for an instance, the accounts that play anywhere and sign in with a Google account it last read;</li>
     *   <li>the other accounts that play anywhere, those signing in with a Google account another instance last read
     *       after the rest, so each is left for its instance;</li>
     *   <li>no account, when it is a candidate.</li>
     * </ol>
     *
     * An account that plays at another place is left out. A {@code playsAt} naming an instance this PC does not know
     * counts as none. With no place dealt, every account is left, those of no place first.
     */
    static List<Account> left(List<Account> free, Optional<Place> at, Pc pc) {
        List<Account> here = new ArrayList<>();
        List<Account> signedIn = new ArrayList<>();
        List<Account> anywhere = new ArrayList<>();
        List<Account> elsewhere = new ArrayList<>();
        List<Account> placed = new ArrayList<>();
        List<Account> none = new ArrayList<>();
        Set<String> readHere = at.filter(Place::namesAndroid).map(place -> pc.reads().get(place.name()))
                .orElse(Set.of());
        for (Account account : free) {
            if (account.isNone()) {
                none.add(account);
                continue;
            }
            Optional<Place> plays = playsAt(account, pc);
            if (plays.isPresent()) {
                if (at.isEmpty()) placed.add(account);
                else if (plays.equals(at)) here.add(account);
                continue;
            }
            Optional<String> google = google(account);
            if (google.isPresent() && readHere.contains(google.get())) signedIn.add(account);
            else if (google.isPresent() && readElsewhere(google.get(), at, pc)) elsewhere.add(account);
            else anywhere.add(account);
        }
        List<Account> out = new ArrayList<>(here);
        out.addAll(signedIn);
        out.addAll(anywhere);
        out.addAll(elsewhere);
        out.addAll(placed);
        out.addAll(none);
        return out;
    }

    /**
     * What the Runs window says with ⚠ on a row dealt {@code account} at {@code at}, or none (spec § The ⚠ mark): an
     * account that plays at an instance this PC no longer knows, or one that plays at {@code at} and signs in with a
     * Google account the instance did not hold when last read.
     */
    public static Optional<String> note(Account account, Optional<Place> at, Pc pc) {
        if (account.isNone()) return Optional.empty();
        Optional<Place> link = account.playsAt();
        if (link.isPresent() && playsAt(account, pc).isEmpty()) {
            return Optional.of(account.login() + " plays at " + PlaceKeys.said(link.get()) + ", which is gone");
        }
        Optional<String> google = google(account);
        if (link.isEmpty() || !link.equals(at) || google.isEmpty() || !at.get().namesAndroid()) return Optional.empty();
        Set<String> read = pc.reads().get(at.get().name());
        if (read == null || read.contains(google.get())) return Optional.empty();
        String held = read.isEmpty() ? "no Google account" : String.join(", ", new TreeSet<>(read));
        return Optional.of(at.get().name() + " last read " + held + "; " + account.login() + " signs in with "
                + google.get());
    }

    /**
     * Where {@code account} plays as the deal reads it: an instance or a game VM this PC does not know is no place. A
     * private display is never gone: how many there are follows this PC's memory.
     */
    private static Optional<Place> playsAt(Account account, Pc pc) {
        return account.playsAt().filter(place -> switch (place.kind()) {
            case ANDROID -> pc.instances().contains(place.name());
            case VM -> pc.vms().contains(place.name());
            case DESKTOP, PRIVATE_DISPLAY, UNKNOWN -> true;
        });
    }

    /** The Google account {@code account} signs in with, lower case. */
    private static Optional<String> google(Account account) {
        return account.signsInWith().filter(with -> with.kind() == Account.Kind.GOOGLE).map(Account::login)
                .map(AccountPool::folded);
    }

    private static boolean readElsewhere(String google, Optional<Place> at, Pc pc) {
        Set<String> others = new LinkedHashSet<>(pc.reads().keySet());
        at.filter(Place::namesAndroid).ifPresent(place -> others.remove(place.name()));
        return others.stream().anyMatch(name -> pc.reads().get(name).contains(google));
    }

    private static String folded(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
