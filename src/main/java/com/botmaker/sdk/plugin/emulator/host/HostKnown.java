// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

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
     * @param verify      the verification's id ({@code phone}, {@code code}…), empty for the book's default
     * @param target      a {@code launch-target} account's launch target ({@code emu-app:com.game}), else empty
     * @param signsInWith the entry a launch target's account signs in with, {@code google:main@gmail.com}, or empty
     * @param playsAt     the key of the place a launch target's account plays at, {@code this-pc/android/MEmu_1}, or
     *                    empty for any free place
     */
    public record Entry(String kind, String name, String note, String aliases, String verify, String target,
                        String signsInWith, String playsAt) {
        public Entry {
            kind = kind == null ? "" : kind.strip();
            name = name == null ? "" : name.strip();
            note = note == null ? "" : note.strip();
            aliases = aliases == null ? "" : String.join(" ", aliases.strip().split("\\s+"));
            verify = verify == null ? "" : verify.strip();
            target = target == null ? "" : target.strip();
            signsInWith = signsInWith == null ? "" : signsInWith.strip();
            playsAt = playsAt == null ? "" : playsAt.strip();
        }

        /** A Google, Facebook or mail entry, as the book was written before 2026-11-27. */
        public Entry(String kind, String name, String note, String aliases, String verify) {
            this(kind, name, note, aliases, verify, "", "", "");
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
