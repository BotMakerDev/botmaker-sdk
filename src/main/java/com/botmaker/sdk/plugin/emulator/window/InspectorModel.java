// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.config.Apply;
import com.botmaker.shared.emulator.config.ConfigDiff;
import com.botmaker.shared.emulator.config.GameRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Instances tab's inspector says (spec {@code 2026-11-14-emulators-look-design.md}): for one instance, what
 * Apply changes and its device; for several, or an emulator's group, their states counted. No JavaFX.
 */
final class InspectorModel {

    private InspectorModel() {}

    /** One line of the device section: what it is and what the instance has. */
    record Fact(String label, String value) {}

    /**
     * What Apply changes on {@code row}, one line each: {@code ~} a setting from what it has to what it gets,
     * {@code +} a sign-in or an install, {@code −} a sign-out. Nothing when it was not read or is as configured.
     */
    static List<String> applyLines(EmulatorsModel.InstanceRow row) {
        ConfigDiff diff = row.diff();
        if (diff == null) return List.of();
        List<String> lines = new ArrayList<>();
        for (Apply.Change change : Apply.changes(diff)) {
            lines.add("~ " + change.field().displayName() + " "
                    + (change.before() == null ? "not read" : change.before().said()) + " → " + change.after().said());
        }
        for (ConfigDiff.AccountChange change : diff.accounts()) {
            if (!change.account().kind().signedInByBotMaker()) continue;
            boolean remove = change.op() == ConfigDiff.AccountChange.Op.REMOVE;
            lines.add((remove ? "− sign out " : "+ sign in ") + change.account().name()
                    + (remove ? diff.signOutCondition() : ""));
        }
        for (GameRecord game : diff.missingGames()) lines.add("+ install " + game.label());
        return lines;
    }

    /** {@code row}'s device: Android, screen, memory, root and its ADB address; "Not read" for what was not. */
    static List<Fact> device(EmulatorsModel.InstanceRow row) {
        List<Fact> facts = new ArrayList<>();
        for (EmulatorsModel.Column column : List.of(EmulatorsModel.Column.ANDROID, EmulatorsModel.Column.SCREEN,
                EmulatorsModel.Column.MEMORY, EmulatorsModel.Column.ROOT, EmulatorsModel.Column.PORT)) {
            String value = column.text(row);
            facts.add(new Fact(column.displayName(), value == null || value.isBlank() ? "Not read" : value));
        }
        return facts;
    }

    /**
     * Several instances counted: how many, by state as their pills say it, and how many Apply would change.
     *
     * @param notRead the ones whose facts could not be read, running or not
     */
    record Several(int count, int running, int stopped, int notRead, int applicable) {

        /** "3 instances". */
        String title() {
            return EmulatorsModel.count(count, "instance");
        }

        /** "2 running, 1 stopped", with the ones not read when there are some. */
        String states() {
            String said = running + " running, " + stopped + " stopped";
            return notRead == 0 ? said : said + ", " + notRead + " not read";
        }
    }

    static Several several(List<EmulatorsModel.InstanceRow> rows) {
        int running = 0;
        int stopped = 0;
        int notRead = 0;
        int applicable = 0;
        for (EmulatorsModel.InstanceRow row : rows) {
            if (row.facts() == null) notRead++;
            else if (row.running()) running++;
            else stopped++;
            if (row.applicable()) applicable++;
        }
        return new Several(rows.size(), running, stopped, notRead, applicable);
    }
}
