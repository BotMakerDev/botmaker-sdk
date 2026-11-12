package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.ValueHolder;
import com.botmaker.plugin.api.source.ManagedMethod;
import com.botmaker.plugin.api.source.ManagedValue;

import java.util.List;

/**
 * The SDK's values of this PC: what each emulator and each instance should be (spec
 * {@code 2026-11-12-emulators-window-design.md}, amended: the configuration is the SDK's host data). The host keeps
 * them as Java in its folder of this PC, {@code Emulators.java}, {@code Instances.java} and {@code Accounts.java}; no
 * bot holds them and no run reads them. {@link HostConfig} turns them into shared's model and back.
 */
public final class SdkHostValues implements ValueHolder {

    private static final String WINDOW = " Change it in the Emulators window.";

    public static final ManagedMethod<HostSettings> EMULATOR_SETTINGS =
            ManagedValue.method(SdkHostValue.Id.EMULATOR_SETTINGS)
                    .in(SdkHostValue.Holder.EMULATORS)
                    .holds(HostSettings.class, HostSettings.none())
                    .because("These are the settings each emulator's instances start from." + WINDOW);

    public static final ManagedMethod<HostGames> EMULATOR_GAMES = ManagedValue.method(SdkHostValue.Id.EMULATOR_GAMES)
            .in(SdkHostValue.Holder.EMULATORS)
            .holds(HostGames.class, HostGames.none())
            .because("These are the games every instance of an emulator should have." + WINDOW);

    public static final ManagedMethod<HostSettings> INSTANCE_SETTINGS =
            ManagedValue.method(SdkHostValue.Id.INSTANCE_SETTINGS)
                    .in(SdkHostValue.Holder.INSTANCES)
                    .holds(HostSettings.class, HostSettings.none())
                    .because("These are the settings an instance has of its own, over its emulator's." + WINDOW);

    public static final ManagedMethod<HostGames> INSTANCE_GAMES = ManagedValue.method(SdkHostValue.Id.INSTANCE_GAMES)
            .in(SdkHostValue.Holder.INSTANCES)
            .holds(HostGames.class, HostGames.none())
            .because("These are the games an instance should have of its own." + WINDOW);

    public static final ManagedMethod<HostLabels> INSTANCE_TAGS = ManagedValue.method(SdkHostValue.Id.INSTANCE_TAGS)
            .in(SdkHostValue.Holder.INSTANCES)
            .holds(HostLabels.class, HostLabels.none())
            .because("These are your tags for the instances." + WINDOW);

    public static final ManagedMethod<HostLabels> INSTANCE_USED_BY =
            ManagedValue.method(SdkHostValue.Id.INSTANCE_USED_BY)
                    .in(SdkHostValue.Holder.INSTANCES)
                    .holds(HostLabels.class, HostLabels.none())
                    .because("These are the bots seen launching each instance. BotMaker writes them when a bot is"
                            + " opened.");

    public static final ManagedMethod<HostLabels> INSTANCE_RENAMES =
            ManagedValue.method(SdkHostValue.Id.INSTANCE_RENAMES)
                    .in(SdkHostValue.Holder.INSTANCES)
                    .holds(HostLabels.class, HostLabels.none())
                    .because("These are the instances BotMaker renamed, each old name beside its new one. A bot that"
                            + " still names the old one is pointed at the new one when it is opened.");

    public static final ManagedMethod<HostAccounts> ACCOUNTS = ManagedValue.method(SdkHostValue.Id.ACCOUNTS)
            .in(SdkHostValue.Holder.ACCOUNTS)
            .holds(HostAccounts.class, HostAccounts.none())
            .because("These are the accounts the instances should hold, never a password." + WINDOW);

    private SdkHostValues() {}

    @Override
    public List<ManagedValue<?>> values() {
        return List.of(EMULATOR_SETTINGS, EMULATOR_GAMES, INSTANCE_SETTINGS, INSTANCE_GAMES, INSTANCE_TAGS,
                INSTANCE_USED_BY, INSTANCE_RENAMES, ACCOUNTS);
    }
}
