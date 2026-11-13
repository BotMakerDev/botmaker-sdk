package com.botmaker.sdk.plugin.emulator.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This PC's account book: every account it knows, for every emulator and every bot, each once ({@link HostBook}).
 * Rows of text, so an account kind a newer BotMaker wrote is kept by an older one as it is. There is no password here,
 * and never will be.
 */
public record HostKnown(List<Entry> list) {

    public HostKnown {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostKnown of(Entry... entries) {
        return new HostKnown(entries == null ? List.of() : Arrays.asList(entries));
    }

    public static HostKnown none() {
        return new HostKnown(List.of());
    }

    /**
     * One account.
     *
     * @param kind {@code google}, {@code facebook} or {@code mail}
     * @param name the email, or the account's name
     * @param note the user's own words, or empty
     */
    public record Entry(String kind, String name, String note) {
        public Entry {
            kind = kind == null ? "" : kind.strip();
            name = name == null ? "" : name.strip();
            note = note == null ? "" : note.strip();
        }
    }
}
