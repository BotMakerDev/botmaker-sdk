package com.botmaker.sdk.plugin.emulator.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The accounts an emulator's instances, or one instance, should hold. A Google account is signed in by BotMaker;
 * Facebook and mail are records only. There is no password here, and never will be.
 */
public record HostAccounts(List<Account> list) {

    public HostAccounts {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostAccounts of(Account... accounts) {
        return new HostAccounts(accounts == null ? List.of() : Arrays.asList(accounts));
    }

    public static HostAccounts none() {
        return new HostAccounts(List.of());
    }

    /**
     * One account.
     *
     * @param of      the emulator ({@code ldplayer}) or the instance ({@code ldplayer/LDPlayer-1})
     * @param kind    {@code google}, {@code facebook} or {@code mail}
     * @param name    the email, or the account's name
     * @param note    the user's own words, or empty
     * @param removed an instance's row saying it should not hold an account its emulator lists
     * @param first   the Google account a game's chooser offers first
     */
    public record Account(String of, String kind, String name, String note, boolean removed, boolean first) {
        public Account {
            of = of == null ? "" : of.strip();
            kind = kind == null ? "" : kind.strip();
            name = name == null ? "" : name.strip();
            note = note == null ? "" : note.strip();
        }
    }
}
