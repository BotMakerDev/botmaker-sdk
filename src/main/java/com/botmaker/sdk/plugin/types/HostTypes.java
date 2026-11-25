// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.internal.capture.HostWindows;
import com.botmaker.sdk.internal.plan.HostDone;
import com.botmaker.sdk.plugin.emulator.host.HostAccounts;
import com.botmaker.sdk.plugin.emulator.host.HostGames;
import com.botmaker.sdk.plugin.emulator.host.HostKnown;
import com.botmaker.sdk.plugin.emulator.host.HostLabels;
import com.botmaker.sdk.plugin.emulator.host.HostSecrets;
import com.botmaker.sdk.plugin.emulator.host.HostSettings;

import java.util.List;

/**
 * How the SDK's values of this PC are written ({@code @SdkHostValue}): each a list of rows,
 * {@code HostSettings.of(new HostSettings.Setting("ldplayer", "screen", "1080x1920@320"))}. Rows of text rather than
 * nested values, so a field, a product or an account kind a newer BotMaker wrote is kept by an older one as it is.
 * Nothing here has an editor: the Emulators window is where they are changed.
 */
public final class HostTypes implements TypeHolder {

    private HostTypes() {}

    @Override
    public List<PluginType<?>> types() {
        return List.of();
    }

    @Override
    public List<ComponentType<?>> components() {
        return List.of(SETTINGS, SETTING, GAMES, GAME, ACCOUNTS, ACCOUNT, LABELS, LABEL, KNOWN, KNOWN_PLAIN,
                KNOWN_ENTRY, SECRETS, SECRET, DONE, DONE_ROW, WINDOWS, WINDOW);
    }

    /** The windows the runs picked for their games, which a run writes ({@code AutoPicks}). */
    public static final DeclaredCall<HostWindows> WINDOWS = ComponentType.part(HostWindows.class)
            .writtenAsEach(HostWindows::of, HostWindows::list);

    public static final DeclaredCall<HostWindows.Row> WINDOW = ComponentType.part(HostWindows.Row.class)
            .writtenAsRecord();

    /** The secrets this PC keeps, by name and kind ({@code KeptSecrets}). */
    public static final DeclaredCall<HostSecrets> SECRETS = ComponentType.part(HostSecrets.class)
            .writtenAsEach(HostSecrets::of, HostSecrets::list);

    public static final DeclaredCall<HostSecrets.Kept> SECRET = ComponentType.part(HostSecrets.Kept.class)
            .writtenAsRecord();

    /** What the runs did, which a run writes ({@code DoneRecords}). */
    public static final DeclaredCall<HostDone> DONE = ComponentType.part(HostDone.class)
            .writtenAsEach(HostDone::of, HostDone::list);

    public static final DeclaredCall<HostDone.Row> DONE_ROW = ComponentType.part(HostDone.Row.class)
            .writtenAsRecord();

    public static final DeclaredCall<HostSettings> SETTINGS = ComponentType.part(HostSettings.class)
            .writtenAsEach(HostSettings::of, HostSettings::list);

    public static final DeclaredCall<HostSettings.Setting> SETTING = ComponentType.part(HostSettings.Setting.class)
            .writtenAsRecord();

    public static final DeclaredCall<HostGames> GAMES = ComponentType.part(HostGames.class)
            .writtenAsEach(HostGames::of, HostGames::list);

    public static final DeclaredCall<HostGames.Game> GAME = ComponentType.part(HostGames.Game.class)
            .writtenAsRecord();

    public static final DeclaredCall<HostAccounts> ACCOUNTS = ComponentType.part(HostAccounts.class)
            .writtenAsEach(HostAccounts::of, HostAccounts::list);

    public static final DeclaredCall<HostAccounts.Account> ACCOUNT = ComponentType.part(HostAccounts.Account.class)
            .writtenAsRecord();

    public static final DeclaredCall<HostLabels> LABELS = ComponentType.part(HostLabels.class)
            .writtenAsEach(HostLabels::of, HostLabels::list);

    public static final DeclaredCall<HostLabels.Label> LABEL = ComponentType.part(HostLabels.Label.class)
            .writtenAsRecord();

    public static final DeclaredCall<HostKnown> KNOWN = ComponentType.part(HostKnown.class)
            .writtenAsEach(HostKnown::of, HostKnown::list);

    /**
     * {@code new HostKnown.Entry(kind, name, note)}: an entry with no alias and the default verification, the form a
     * book written before 2026-11-14 holds, so it still reads. The host writes it whenever it rebuilds the entry.
     */
    public static final DeclaredCall<HostKnown.Entry> KNOWN_PLAIN = ComponentType.part(HostKnown.Entry.class)
            .writtenAs(HostKnown.Entry::new, HostKnown.Entry::kind, HostKnown.Entry::name, HostKnown.Entry::note);

    /** An entry with its aliases or its own verification. */
    public static final DeclaredCall<HostKnown.Entry> KNOWN_ENTRY = ComponentType.part(HostKnown.Entry.class)
            .writtenAsRecord();
}
