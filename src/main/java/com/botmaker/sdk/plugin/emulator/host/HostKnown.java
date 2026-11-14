package com.botmaker.sdk.plugin.emulator.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This PC's account book: every account it knows, for every emulator and every bot, each once ({@link HostBook}).
 * Rows of text, so an account kind or a verification a newer BotMaker wrote is kept by an older one as it is. There is
 * no password here, and never will be.
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
     * @param kind    {@code google}, {@code facebook} or {@code mail}
     * @param name    the email, or the account's name
     * @param note    the user's own words, or empty
     * @param aliases the account's other addresses, separated by spaces, or empty
     * @param verify  the verification's id ({@code phone}, {@code code}…), empty for the book's default
     */
    public record Entry(String kind, String name, String note, String aliases, String verify) {
        public Entry {
            kind = kind == null ? "" : kind.strip();
            name = name == null ? "" : name.strip();
            note = note == null ? "" : note.strip();
            aliases = aliases == null ? "" : String.join(" ", aliases.strip().split("\\s+"));
            verify = verify == null ? "" : verify.strip();
        }

        /** An entry with no alias and the default verification, as the book was written before 2026-11-14. */
        public Entry(String kind, String name, String note) {
            this(kind, name, note, "", "");
        }

        /** The aliases as a list, as written; {@code AccountRecord} lower-cases them and drops repeats. */
        public List<String> aliasList() {
            return aliases.isEmpty() ? List.of() : List.of(aliases.split(" "));
        }
    }
}
