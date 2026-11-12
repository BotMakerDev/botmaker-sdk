package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.managed.HostHolder;
import com.botmaker.plugin.api.managed.ManagedId;
import com.botmaker.plugin.api.managed.ManagedMarker;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks one of the SDK's values of <b>this computer</b>: what its emulators and their instances should be, which the
 * Emulators window keeps. They are Java in BotMaker's own folder of this PC, never in a bot: no run reads them, and
 * nothing here is committed, published or moved to another computer. No password is ever one of them.
 */
@Documented
@ManagedMarker
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SdkHostValue {

    /** Which of the SDK's values of this PC this is. */
    Id value();

    /** The values, one constant each. */
    enum Id implements ManagedId<Holder> {
        /** What each emulator's instances start from: its settings, one row per field. */
        EMULATOR_SETTINGS,
        /** The games every instance of an emulator should have. */
        EMULATOR_GAMES,
        /** An instance's own settings, over its emulator's. */
        INSTANCE_SETTINGS,
        /** An instance's own games, and the emulator's games it should not have. */
        INSTANCE_GAMES,
        /** The user's labels for an instance. */
        INSTANCE_TAGS,
        /** The bots seen launching an instance, remembered each time one is opened. */
        INSTANCE_USED_BY,
        /** The instances BotMaker renamed, old name to new, so a bot opened later is pointed at the new name. */
        INSTANCE_RENAMES,
        /** The accounts an emulator's instances, or one instance, should hold: Google, Facebook, mail. */
        ACCOUNTS
    }

    /** This PC's classes the values live in: {@code Emulators.java}, {@code Instances.java}, {@code Accounts.java}. */
    enum Holder implements HostHolder {
        EMULATORS,
        INSTANCES,
        ACCOUNTS
    }
}
