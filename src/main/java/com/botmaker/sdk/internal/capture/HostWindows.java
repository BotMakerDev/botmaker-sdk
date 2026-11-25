// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.capture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The windows the runs of this PC picked for their games ({@link AutoPicks}): one row per launch target. Rows of text,
 * as the SDK's other values of this PC are, so one a newer BotMaker wrote is kept by an older one as it is.
 */
public record HostWindows(List<Row> list) {

    public HostWindows {
        list = list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    public static HostWindows of(Row... rows) {
        return new HostWindows(rows == null ? List.of() : Arrays.asList(rows));
    }

    public static HostWindows none() {
        return new HostWindows(List.of());
    }

    /** The window kept for {@code target}, as {@code WindowIdentity.spec()} writes it; empty when none is. */
    public Optional<String> window(String target) {
        return list.stream().filter(row -> row.target().equals(target) && !row.window().isEmpty())
                .map(Row::window).findFirst();
    }

    /** These rows with {@code target}'s replaced by {@code window}, or removed when {@code window} is null. */
    public HostWindows with(String target, String window) {
        List<Row> rows = new ArrayList<>(list.stream().filter(row -> !row.target().equals(target)).toList());
        if (window != null) rows.add(new Row(target, window));
        return new HostWindows(rows);
    }

    /**
     * One game's window.
     *
     * @param target the game's launch target, {@code LaunchSpec.spec()}
     * @param window its window, {@code WindowIdentity.spec()}: its class, and its title when the class is generic
     */
    public record Row(String target, String window) {
        public Row {
            target = target == null ? "" : target.strip();
            window = window == null ? "" : window.strip();
        }
    }
}
