package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.config.Applies;
import com.botmaker.shared.emulator.config.Apply;
import com.botmaker.shared.emulator.config.ApplyHost;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.GameRecord;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.InstanceChanges;
import com.botmaker.shared.emulator.google.Passwords;

import java.util.ArrayList;
import java.util.List;

/**
 * What one Apply would do, instance by instance, with no JavaFX: the settings it writes, the Google accounts it signs
 * in and out, the games it installs. The window shows {@link #lines} before anything runs, and queues {@link #body}.
 */
final class ApplyPlan {

    private ApplyPlan() {}

    /** One instance's part of an Apply. */
    record Item(EmulatorsModel.InstanceRow row, List<Apply.Change> changes, List<ConfigDiff.AccountChange> accounts,
                List<GameRecord> games) {

        Item {
            changes = List.copyOf(changes);
            accounts = List.copyOf(accounts);
            games = List.copyOf(games);
        }

        boolean removes() {
            return accounts.stream().anyMatch(change -> change.op() == ConfigDiff.AccountChange.Op.REMOVE);
        }

        boolean empty() {
            return changes.isEmpty() && accounts.isEmpty() && games.isEmpty();
        }

        /** This item with no account removed: the user did not agree to the removals. */
        Item keepingAccounts() {
            return new Item(row, changes, accounts.stream()
                    .filter(change -> change.op() != ConfigDiff.AccountChange.Op.REMOVE).toList(), games);
        }
    }

    /** The rows Apply has something to do on, each with what it does. */
    static List<Item> of(List<EmulatorsModel.InstanceRow> rows) {
        List<Item> items = new ArrayList<>();
        for (EmulatorsModel.InstanceRow row : rows) {
            if (!row.applicable()) continue;
            items.add(new Item(row, Apply.changes(row.diff()), row.diff().accounts(), row.diff().missingGames()));
        }
        return items;
    }

    /** The items the user agreed to: with the removals, or without them, and none left empty. */
    static List<Item> agreed(List<Item> items, boolean removals) {
        return items.stream().map(item -> removals ? item : item.keepingAccounts())
                .filter(item -> !item.empty()).toList();
    }

    /**
     * What the item does, one line each, for the user's eyes only: an account is named here, on their screen, and
     * nowhere else.
     */
    static List<String> lines(Item item) {
        List<String> lines = new ArrayList<>();
        for (Apply.Change change : item.changes()) {
            lines.add(change.field().displayName() + ": "
                    + (change.before() == null ? "not read" : change.before().said()) + " → " + change.after().said()
                    + (change.field().needsRestart() && item.row().running() ? " (restarts it)" : ""));
        }
        for (ConfigDiff.AccountChange change : item.accounts()) {
            boolean remove = change.op() == ConfigDiff.AccountChange.Op.REMOVE;
            String kind = change.account().kind().displayName();
            if (!change.account().kind().signedInByBotMaker()) {
                lines.add(kind + " account " + change.account().name() + ": a record only, nothing is signed "
                        + (remove ? "out" : "in"));
            } else {
                lines.add((remove ? "Remove " : "Sign in ") + kind + " account " + change.account().name());
            }
        }
        for (GameRecord game : item.games()) lines.add("Install " + game.label());
        return lines;
    }

    /**
     * The operation for one item: its settings through {@link Apply}, then its accounts and games on the running
     * instance, in the one operation. Null when all of it worked, else what did not.
     *
     * @param log       where the settings written are logged for Undo
     * @param passwords the batch's, asked once per account and cleared by the window when the batch ends
     * @param listedAs  told of an account Android lists under another of its addresses, for the configuration
     */
    static Operation.Body body(Item item, Applies log, Passwords passwords, InstanceChanges.ListedAs listedAs) {
        EmulatorInstance instance = item.row().instance();
        return context -> {
            List<String> problems = new ArrayList<>();
            if (!item.changes().isEmpty()) {
                Apply.Outcome outcome = new Apply(ApplyHost.live(), instance, item.row().facts(), log)
                        .run(item.changes(), context);
                if (outcome.failed() != null) return outcome.failed();
                outcome.refused().forEach((field, why) -> problems.add(field.displayName() + ": " + why));
            }
            if (context.cancelled()) return "Cancelled.";
            if (!item.accounts().isEmpty() || !item.games().isEmpty()) {
                String rest = new InstanceChanges(InstanceChanges.Host.live())
                        .run(instance, item.accounts(), item.games(), passwords, listedAs, context);
                if (rest != null) problems.add(rest);
            }
            return problems.isEmpty() ? null : String.join(" ", problems);
        };
    }
}
