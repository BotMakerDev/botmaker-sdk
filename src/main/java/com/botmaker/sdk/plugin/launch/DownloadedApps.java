// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.emulator.ApkLibrary;
import com.botmaker.shared.emulator.CpuAbi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Downloaded apps window's rows (spec {@code 2026-11-25-game-window-design.md}, footer): every file of the APK
 * library, with the devices that have its app, and what each bulk action deletes. Deleting a file never uninstalls
 * an app.
 */
final class DownloadedApps {

    private DownloadedApps() {}

    /** One file, and the captions of the devices whose apps include its package. */
    record Row(ApkLibrary.Entry entry, List<String> on) {

        Row {
            on = List.copyOf(on);
        }

        /** Its version and processor, {@code 1.3.0 · x86 64-bit}. */
        String version() {
            return entry.version() + " · " + entry.abi().displayName();
        }
    }

    /** Every file of {@code library}, package by package and newest first; {@code onDevices} maps a package to devices. */
    static List<Row> rows(ApkLibrary library, Map<String, List<String>> onDevices) {
        List<Row> rows = new ArrayList<>();
        for (String pkg : library.packages()) {
            for (ApkLibrary.Entry entry : library.versions(pkg)) rows.add(new Row(entry, onDevices.getOrDefault(pkg,
                    List.of())));
        }
        return rows;
    }

    /** The files whose app no device has. */
    static List<ApkLibrary.Entry> installedNowhere(List<Row> rows) {
        return rows.stream().filter(row -> row.on().isEmpty()).map(Row::entry).toList();
    }

    /** Every file but the newest of each app and processor; {@code rows} is newest first per app. */
    static List<ApkLibrary.Entry> allButNewest(List<Row> rows) {
        Set<String> seen = new HashSet<>();
        List<ApkLibrary.Entry> older = new ArrayList<>();
        for (Row row : rows) {
            CpuAbi abi = row.entry().abi();
            if (!seen.add(row.entry().packageName() + "|" + abi.name())) older.add(row.entry());
        }
        return older;
    }

    /** {@code bytes} as a person reads it: {@code 0 B}, {@code 12 KB}, {@code 1.4 GB}. */
    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return value >= 10 ? Math.round(value) + " " + units[unit]
                : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
