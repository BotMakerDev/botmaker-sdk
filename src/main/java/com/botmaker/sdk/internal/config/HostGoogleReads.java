// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Google accounts each emulator instance last said it holds ({@link GoogleReads}): one row per instance. Rows of
 * text, as the SDK's other values of this PC are, so one a newer BotMaker wrote is kept by an older one as it is.
 */
public record HostGoogleReads(List<Row> list) {

    public HostGoogleReads {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostGoogleReads of(Row... rows) {
        return new HostGoogleReads(rows == null ? List.of() : Arrays.asList(rows));
    }

    public static HostGoogleReads none() {
        return new HostGoogleReads(List.of());
    }

    /** {@code instance}'s row; empty when it was never read. */
    public Optional<Row> row(String instance) {
        return list.stream().filter(row -> row.instance().equals(instance)).findFirst();
    }

    /** These rows with {@code row} in place of its instance's, where that stood; a new instance's last. */
    public HostGoogleReads with(Row row) {
        List<Row> rows = new ArrayList<>(list);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).instance().equals(row.instance())) {
                rows.set(i, row);
                return new HostGoogleReads(rows);
            }
        }
        rows.add(row);
        return new HostGoogleReads(rows);
    }

    /**
     * One instance's last read.
     *
     * @param instance the instance by its name, as {@code Place.android} names it
     * @param accounts the Google emails Android listed, lower case, separated by spaces; empty for none
     * @param at       when Android first listed them so, as {@code Instant.toString()} writes it
     */
    public record Row(String instance, String accounts, String at) {
        public Row {
            instance = instance == null ? "" : instance.strip();
            accounts = accounts == null ? "" : String.join(" ", accounts.strip().split("\\s+"));
            at = at == null ? "" : at.strip();
        }
    }
}
