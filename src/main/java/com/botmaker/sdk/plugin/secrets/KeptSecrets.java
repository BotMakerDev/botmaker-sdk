// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.secrets;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.plugin.emulator.host.HostConfig;
import com.botmaker.sdk.plugin.emulator.host.HostSecrets;
import com.botmaker.shared.emulator.google.KeyringStore;
import com.botmaker.shared.emulator.google.PasswordStore;
import com.botmaker.shared.emulator.google.Totp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The secrets this PC keeps (2026-11-17, spec {@code 2026-11-17-google-sign-in-map-design.md} §1): each value in the
 * OS credential store under {@link #SERVICE} and the secret's {@link Secret#key()}, each name and kind in an
 * {@link Index} that holds no value. <i>Add secret…</i> writes both; nothing here touches a bot. Blocks on the store,
 * so never on the FX thread. Every array it is handed is cleared, whatever it answers.
 */
public final class KeptSecrets {

    /** The service the credential store keeps the values under. */
    public static final String SERVICE = "BotMaker secrets";

    /** The longest name kept. */
    public static final int MAX_NAME = 100;

    private static final PasswordStore STORE = new KeyringStore(SERVICE);

    /** The names and kinds this PC keeps: {@link #index}, or a test's. */
    public interface Index {
        HostSecrets read() throws IOException;

        /** Changes the index by {@code edit}, read and written at once, as one step of this PC's undo named {@code label}. */
        void change(String label, UnaryOperator<HostSecrets> edit) throws IOException;
    }

    /** The index among the SDK's values of this PC, as {@code services} holds them. */
    public static Index index(StudioServices services) {
        return new Index() {
            @Override
            public HostSecrets read() {
                return HostConfig.secrets(services);
            }

            @Override
            public void change(String label, UnaryOperator<HostSecrets> edit) throws IOException {
                HostConfig.changeSecrets(services, label, edit);
            }
        };
    }

    /** A kept secret as a picker lists it: {@code stored} false when this PC's credential store lacks its value. */
    public record Listed(Secret secret, SecretKind kind, boolean stored) {}

    /** What an add did. */
    public enum Result {
        /** No secret had the name; one has it now. */
        ADDED("added"),
        /** One had the name: its kind and value are the form's now. */
        UPDATED("updated"),
        /** A blank name, one too long, or one with a line break. Nothing was kept. */
        INVALID_NAME("invalid-name"),
        /** This PC has no credential store that answers. Nothing was kept. */
        NO_KEYRING("no-keyring"),
        /** An authenticator's value does not read as a setup key. Nothing was kept. */
        NOT_A_KEY("not-a-key"),
        /** No value, or the store refused it. Nothing was kept. */
        REFUSED("refused"),
        UNKNOWN("unknown");

        private final String id;

        Result(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** Whether the secret is kept with its value. */
        public boolean kept() {
            return this == ADDED || this == UPDATED;
        }

        public static Result fromId(String id) {
            return Arrays.stream(values()).filter(result -> result.id.equals(id)).findFirst().orElse(UNKNOWN);
        }
    }

    /** What the user typed; {@code value} is a password, or an authenticator's setup key. */
    public record Form(String name, SecretKind kind, char[] value) {
        public Form {
            name = name == null ? "" : name.strip();
            kind = kind == null ? SecretKind.PASSWORD : kind;
        }

        /** Clears the value. */
        void clear() {
            if (value != null) Arrays.fill(value, '\0');
        }
    }

    private KeptSecrets() {}

    /** This PC's credential store for secrets. */
    public static PasswordStore store() {
        return STORE;
    }

    /** The kept secrets in the order they were added, each with whether the store holds its value. Reads the store. */
    public static List<Listed> list(Index index, PasswordStore store) throws IOException {
        Map<Secret, HostSecrets.Kept> first = new LinkedHashMap<>();
        index.read().list().stream()
                .filter(kept -> !kept.name().isEmpty())
                .forEach(kept -> first.putIfAbsent(Secret.of(kept.name()), kept));
        return first.entrySet().stream()
                .map(each -> new Listed(each.getKey(), SecretKind.fromId(each.getValue().kind()),
                        store.remembered(each.getKey().key())))
                .toList();
    }

    /**
     * Checks the form, keeps the value (an authenticator's key in its canonical form), then writes the index. An index
     * that fails puts the value back as it was; a secret the index lacked is forgotten again.
     */
    public static Result add(Index index, PasswordStore store, Form form) throws IOException {
        Optional<char[]> before = Optional.empty();
        char[] kept = null;
        try {
            if (!validName(form.name())) return Result.INVALID_NAME;
            if (!store.available()) return Result.NO_KEYRING;
            if (form.value() == null || form.value().length == 0) return Result.REFUSED;
            if (form.kind() == SecretKind.AUTHENTICATOR) {
                Optional<char[]> canonical = Totp.canonical(form.value());
                if (canonical.isEmpty()) return Result.NOT_A_KEY;
                kept = canonical.get();
            } else {
                kept = form.value().clone();
            }
            Secret secret = Secret.of(form.name());
            HostSecrets.Kept row = new HostSecrets.Kept(form.name(), form.kind().id());
            boolean listed = listed(index.read(), secret);

            before = store.read(secret.key());
            if (!store.remember(secret.key(), kept)) return Result.REFUSED;
            try {
                index.change((listed ? "Change the secret " : "Add the secret ") + secret.name(),
                        was -> withRow(was, secret, row));
            } catch (IOException | RuntimeException e) {
                if (before.isPresent()) store.remember(secret.key(), before.get());
                else if (!listed) store.forget(secret.key());
                // a listed secret whose old value did not read keeps the new one rather than losing both
                throw e;
            }
            return listed ? Result.UPDATED : Result.ADDED;
        } finally {
            form.clear();
            if (kept != null) Arrays.fill(kept, '\0');
            before.ifPresent(value -> Arrays.fill(value, '\0'));
        }
    }

    private static boolean listed(HostSecrets index, Secret secret) {
        return index.list().stream().anyMatch(row -> Secret.of(row.name()).equals(secret));
    }

    /** {@code index} with {@code row} in place of {@code secret}'s rows, or after the others when it had none. */
    static HostSecrets withRow(HostSecrets index, Secret secret, HostSecrets.Kept row) {
        List<HostSecrets.Kept> rows = new ArrayList<>();
        boolean placed = false;
        for (HostSecrets.Kept each : index.list()) {
            if (!Secret.of(each.name()).equals(secret)) {
                rows.add(each);
            } else if (!placed) {
                rows.add(row);
                placed = true;
            }
        }
        if (!placed) rows.add(row);
        return new HostSecrets(rows);
    }

    /** A name kept: not blank, at most {@link #MAX_NAME} characters, on one line. */
    static boolean validName(String name) {
        return !name.isBlank() && name.length() <= MAX_NAME && name.chars().noneMatch(Character::isISOControl);
    }
}
