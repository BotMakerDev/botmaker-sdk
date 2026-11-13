package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.Sourced;
import com.botmaker.shared.emulator.google.PasswordStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The live screen's Fill panel, with no JavaFX: the Google accounts it offers, in order, each with the password button
 * it has. The account the instance's Apply is signing in comes first, then the accounts the instance holds, then the
 * ones its configuration names; an account is listed once, by {@link PasswordStore#key}.
 */
final class FillAccounts {

    private FillAccounts() {}

    /** Why an account is listed: its first reason, in the panel's order. */
    enum Source {
        SIGNING_IN("signing in now"),
        ON_INSTANCE("on this instance"),
        CONFIGURED("in its configuration");

        private final String displayName;

        Source(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    /** The password button a row has. */
    enum PasswordFill {
        /** *Fill password*: this PC remembers it, and it is typed straight from the store. */
        REMEMBERED("Fill password"),
        /** *Fill password…*: asked in the prompt, then typed. */
        ASKED("Fill password…");

        private final String displayName;

        PasswordFill(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }
    }

    /** One account of the panel; every row has *Fill email*. */
    record Row(String email, Source source, PasswordFill password) {

        /** Highlighted: the account the sign-in on screen is for. */
        boolean highlighted() {
            return source == Source.SIGNING_IN;
        }
    }

    /**
     * The panel's rows.
     *
     * @param signingIn  the accounts the instance's running Apply signs in, in its order
     * @param onInstance the Google accounts the instance holds, as last read
     * @param configured the accounts its configuration names, of any kind: only Google's are offered
     * @param remembered the accounts this PC remembers a password for, by {@link PasswordStore#key}
     */
    static List<Row> rows(List<String> signingIn, Collection<String> onInstance,
                          List<Sourced<AccountRecord>> configured, Set<String> remembered) {
        List<Row> rows = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        add(rows, listed, signingIn, Source.SIGNING_IN, remembered);
        add(rows, listed, onInstance.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), Source.ON_INSTANCE,
                remembered);
        add(rows, listed, google(configured), Source.CONFIGURED, remembered);
        return rows;
    }

    /** The Google accounts among {@code configured}, by name. */
    static List<String> google(List<Sourced<AccountRecord>> configured) {
        return configured.stream().map(Sourced::value)
                .filter(account -> account.kind() == AccountRecord.Kind.GOOGLE)
                .map(AccountRecord::name).toList();
    }

    /** Every account the panel may list, for asking the store which it remembers. */
    static Set<String> keys(List<String> signingIn, Collection<String> onInstance,
                            List<Sourced<AccountRecord>> configured) {
        Set<String> keys = new HashSet<>();
        signingIn.forEach(email -> keys.add(PasswordStore.key(email)));
        onInstance.forEach(email -> keys.add(PasswordStore.key(email)));
        google(configured).forEach(email -> keys.add(PasswordStore.key(email)));
        keys.remove("");
        return keys;
    }

    private static void add(List<Row> rows, Set<String> listed, Collection<String> emails, Source source,
                            Set<String> remembered) {
        for (String email : emails) {
            String key = PasswordStore.key(email);
            if (key.isEmpty() || !listed.add(key)) continue;
            rows.add(new Row(email.strip(), source,
                    remembered.contains(key) ? PasswordFill.REMEMBERED : PasswordFill.ASKED));
        }
    }
}
