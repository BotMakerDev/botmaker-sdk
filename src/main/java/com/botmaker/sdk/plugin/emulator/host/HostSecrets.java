// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The secrets this PC keeps, by name and kind ({@link SdkHostValues#SECRETS}, 2026-11-17): never a value, which only
 * the OS credential store holds. Rows of text, so a kind a newer BotMaker wrote is kept by an older one as it is.
 */
public record HostSecrets(List<Kept> list) {

    public HostSecrets {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostSecrets of(Kept... kept) {
        return new HostSecrets(kept == null ? List.of() : Arrays.asList(kept));
    }

    public static HostSecrets none() {
        return new HostSecrets(List.of());
    }

    /**
     * One secret.
     *
     * @param name its name, as the user wrote it
     * @param kind its kind's id ({@code password}, {@code authenticator})
     */
    public record Kept(String name, String kind) {
        public Kept {
            name = name == null ? "" : name.strip();
            kind = kind == null ? "" : kind.strip();
        }
    }
}
