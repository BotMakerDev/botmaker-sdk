// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.shared.emulator.EmulatorIdentity;
import com.botmaker.shared.emulator.InstanceIds;
import com.botmaker.shared.emulator.config.InstanceKey;

import java.util.Objects;
import java.util.Optional;

/**
 * Each instance's BotMaker ID, kept as an {@code instance.id} row of its settings in {@code Instances.java} (spec
 * {@code 2026-11-14-end-to-end-instances-design.md} § Who answers). Not a setting the window shows: a row this build's
 * {@code ConfigField} does not know, which {@link HostConfig} keeps, moves on a rename and drops on a forget.
 */
public final class HostIds implements InstanceIds {

    /** The field of the row. */
    static final String FIELD = "instance.id";

    private final StudioServices services;

    private HostIds(StudioServices services) {
        this.services = services;
    }

    /** Makes this PC's values the keeper of IDs for the checks this process runs ({@link EmulatorIdentity}). */
    public static void bind(StudioServices services) {
        if (services != null) EmulatorIdentity.use(new HostIds(services));
    }

    @Override
    public Optional<String> expected(InstanceKey key) {
        return HostConfig.instanceRows(services).list().stream()
                .filter(row -> row.of().equals(key.text()) && row.field().equals(FIELD) && !row.value().isEmpty())
                .map(HostSettings.Setting::value).findFirst();
    }

    @Override
    public Optional<InstanceKey> owner(String id) {
        return HostConfig.instanceRows(services).list().stream()
                .filter(row -> row.field().equals(FIELD) && row.value().equals(id))
                .map(row -> InstanceKey.parse(row.of())).filter(Objects::nonNull).findFirst();
    }

    @Override
    public Optional<String> remember(InstanceKey key, String id) {
        return HostConfig.instanceRow(services, new HostSettings.Setting(key.text(), FIELD, id));
    }
}
