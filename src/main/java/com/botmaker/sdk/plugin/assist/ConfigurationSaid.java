package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.plugin.emulator.window.EmulatorsModel;
import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AndroidState;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.InstanceFacts;
import com.botmaker.shared.emulator.config.Sourced;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * {@code setup}'s {@code configuration}: what one emulator instance should be against what it has, each wanted value
 * with the layer it comes from, as the Emulators window shows it. It only reads: changing a configuration, Apply and
 * signing in are the user's, in that window.
 */
final class ConfigurationSaid {

    private ConfigurationSaid() {}

    /** The instances {@code reference} names, each said; a refusal's words when nothing is called that. */
    static String said(List<EmulatorsModel.InstanceRow> rows, String reference) {
        if (rows.isEmpty()) {
            return "No emulator instance is called \"" + reference + "\"; list_emulators names them.";
        }
        return String.join("\n\n", rows.stream().map(ConfigurationSaid::said).toList());
    }

    static String said(EmulatorsModel.InstanceRow row) {
        List<String> lines = new ArrayList<>();
        lines.add("\"" + row.instance().reference() + "\" — " + row.key().platform().displayName()
                + (row.facts() == null ? "" : row.running() ? ", running" : ", stopped") + ". " + verdict(row));
        ConfigDiff diff = row.diff();
        if (!row.wanted().fields().isEmpty()) {
            lines.add("Settings (wanted, from, now):");
            row.wanted().fields().forEach((field, wanted) -> {
                Optional<ConfigDiff.FieldState> state = diff == null ? Optional.empty() : diff.fields().stream()
                        .filter(one -> one.field() == field).findFirst();
                String now = state.flatMap(ConfigDiff.FieldState::now)
                        .map(read -> read.value().said() + " (" + origin(read) + ")").orElse("not read");
                lines.add("• " + field.displayName() + ": " + wanted.value().said() + ", " + from(wanted) + ", now "
                        + now + state.map(one -> " — " + one.status().displayName()).orElse(""));
            });
        }
        Optional<Set<String>> held = android(row).flatMap(AndroidState::googleAccounts);
        if (!row.wanted().accounts().isEmpty()) {
            lines.add("Accounts wanted:");
            for (Sourced<AccountRecord> wanted : row.wanted().accounts()) {
                AccountRecord account = wanted.value();
                String state = !account.kind().signedInByBotMaker() ? "a record only, nothing signs it in"
                        : held.map(emails -> emails.contains(account.name().toLowerCase(Locale.ROOT)) ? "signed in"
                                : "not signed in").orElse("not read");
                boolean first = account.kind() == AccountRecord.Kind.GOOGLE && row.wanted().defaultGoogle().filter(account.name()::equalsIgnoreCase).isPresent();
                lines.add("• " + account.kind().displayName() + " " + account.name() + (first ? " (offered first)" : "")
                        + ", " + from(wanted) + " — " + state);
            }
        }
        List<ConfigDiff.AccountChange> removals = diff == null ? List.of() : diff.accounts().stream()
                .filter(change -> change.op() == ConfigDiff.AccountChange.Op.REMOVE).toList();
        if (!removals.isEmpty()) {
            lines.add("Accounts its configuration says to sign out:");
            removals.forEach(change -> lines.add("• " + change.account().kind().displayName() + " "
                    + change.account().name() + " — signed in"));
        }
        // with no facts, whether it runs was not read either
        boolean stopped = row.facts() != null && !row.running();
        lines.add(held.map(emails -> emails.isEmpty() ? "Signed in to Google now: no account."
                : "Signed in to Google now: " + String.join(", ", emails) + ".")
                .orElse("Signed in to Google now: not read" + (stopped ? ", it is stopped." : ".")));
        Optional<Set<String>> installed = android(row).flatMap(AndroidState::packages);
        if (!row.wanted().games().isEmpty()) {
            lines.add("Games wanted:");
            for (Sourced<GameRecord> wanted : row.wanted().games()) {
                GameRecord game = wanted.value();
                lines.add("• " + game.label() + " (" + game.packageName() + "), " + from(wanted) + " — "
                        + installed.map(packages -> packages.contains(game.packageName()) ? "installed"
                                : "not installed").orElse("not read"));
            }
        }
        if (!row.tags().isEmpty()) lines.add("Tags: " + String.join(", ", row.tags()) + ".");
        if (!row.usedBy().isEmpty()) lines.add("Used by the bots: " + String.join(", ", row.usedBy()) + ".");
        return String.join("\n", lines);
    }

    private static String verdict(EmulatorsModel.InstanceRow row) {
        if (row.facts() == null) return "What it has now could not be read.";
        boolean nothing = row.wanted().fields().isEmpty() && row.wanted().accounts().isEmpty()
                && row.wanted().games().isEmpty();
        if (nothing) return "Its configuration sets nothing.";
        if (!row.differs()) return "It is as configured, as far as it was read.";
        return row.applicable() ? "It differs: Apply, in the Emulators window, would change " + row.changes() + "."
                : "It differs, in what the user sets by hand on this emulator.";
    }

    private static Optional<AndroidState> android(EmulatorsModel.InstanceRow row) {
        return row.facts() == null ? Optional.empty() : row.facts().android();
    }

    private static String from(Sourced<?> wanted) {
        return wanted.source() == Sourced.Source.INSTANCE ? "its own" : "its emulator's";
    }

    private static String origin(InstanceFacts.Read read) {
        return read.origin() == InstanceFacts.Origin.ANDROID ? "Android says" : "its settings say";
    }
}
