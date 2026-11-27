// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import com.botmaker.plugin.api.managed.RunHostValues;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.sdk.internal.plan.SdkRunValue;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The Google accounts each emulator instance last said it holds, and since when (spec
 * {@code 2026-11-27-claimed-accounts-design.md}): what the deal of a launch target's accounts reads, never the device.
 * Every read of Android writes it: the Emulators window showing the instance, a Google sign-in, and a run starting on
 * it. Kept in {@code Runs.java} of the SDK's folder of this PC, since a run writes it; never in a bot or published.
 *
 * <p>A read that finds what the row says already writes nothing, so a Refresh of the window adds no step to this PC's
 * history: the row's time is when Android first listed the accounts so.
 */
public final class GoogleReads {

    /** Every instance's last read, in {@code Runs.java} of the SDK's folder of this PC. */
    public static final ManagedMethod<HostGoogleReads> VALUE = ManagedValue.method(SdkRunValue.Id.GOOGLE_READS)
            .in(SdkRunValue.Holder.RUNS)
            .holds(HostGoogleReads.class, HostGoogleReads.none())
            .because("These are the Google accounts each emulator instance last said it holds, and since when."
                    + " BotMaker writes them each time it reads an instance.");

    private GoogleReads() {}

    /**
     * What one read said.
     *
     * @param accounts the Google emails, lower case
     * @param at       since when Android listed them so; {@link Instant#EPOCH} for a time that does not read
     */
    public record Read(Set<String> accounts, Instant at) {
        public Read {
            accounts = accounts == null ? Set.of() : Set.copyOf(accounts);
        }
    }

    /** {@code instance}'s last read in {@code kept}; empty when it was never read. */
    public static Optional<Read> last(HostGoogleReads kept, String instance) {
        if (kept == null || instance == null) return Optional.empty();
        return kept.row(instance.strip()).map(row -> new Read(words(row.accounts()), time(row.at())));
    }

    /** The emails a row lists, lower case; a row written by hand may name one twice or in capitals. */
    private static Set<String> words(String accounts) {
        Set<String> words = new TreeSet<>();
        for (String word : accounts.split(" ")) {
            if (!word.isBlank()) words.add(word.strip().toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /**
     * {@code kept} with {@code instance}'s read of {@code accounts} at {@code at}; {@code kept} itself when the row says
     * those accounts already, or {@code instance} is blank.
     */
    public static HostGoogleReads noted(HostGoogleReads kept, String instance, Set<String> accounts, Instant at) {
        HostGoogleReads before = kept == null ? HostGoogleReads.none() : kept;
        if (instance == null || instance.isBlank() || accounts == null) return before;
        Set<String> sorted = new TreeSet<>();
        accounts.forEach(account -> {
            if (account != null && !account.isBlank()) sorted.add(account.strip().toLowerCase(Locale.ROOT));
        });
        String words = String.join(" ", sorted);
        Optional<HostGoogleReads.Row> was = before.row(instance.strip());
        if (was.isPresent() && was.get().accounts().equals(words)) return before;
        return before.with(new HostGoogleReads.Row(instance, words, at.toString()));
    }

    /**
     * Notes, from a run, what {@code instance} holds now; empty when kept, else why not. A run that cannot write it
     * plays on: the deal reads an older read meanwhile.
     */
    public static Optional<String> noteFromRun(String instance, Set<String> accounts) {
        Instant now = Instant.now();
        return RunHostValues.update(VALUE, kept -> noted(kept, instance, accounts, now));
    }

    private static Instant time(String at) {
        try {
            return Instant.parse(at);
        } catch (DateTimeParseException unread) {
            return Instant.EPOCH;
        }
    }
}
