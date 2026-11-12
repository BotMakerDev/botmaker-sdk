package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.PluginType;
import com.botmaker.sdk.plugin.emulator.host.HostAccounts;
import com.botmaker.sdk.plugin.emulator.host.HostGames;
import com.botmaker.sdk.plugin.emulator.host.HostLabels;
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
        return List.of(SETTINGS, SETTING, GAMES, GAME, ACCOUNTS, ACCOUNT, LABELS, LABEL);
    }

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
}
