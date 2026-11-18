// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.screens;

/**
 * A website screen's route against the path its page is at (2026-11-18): {@code "/case-studies/{slug}"} matches
 * {@code "/case-studies/alpha"}, each {@code {name}} one segment of any text, every other segment the same text. A
 * trailing {@code /} counts for nothing, nor does the query, which a path never carries.
 */
public final class Routes {

    private Routes() {}

    /** Whether {@code path} is a page of {@code route}. */
    public static boolean matches(String route, String path) {
        if (route == null || path == null) return false;
        String[] want = segments(route);
        String[] have = segments(path);
        if (want.length != have.length) return false;
        for (int i = 0; i < want.length; i++) {
            boolean any = want[i].startsWith("{") && want[i].endsWith("}") && !have[i].isEmpty();
            if (!any && !want[i].equals(have[i])) return false;
        }
        return true;
    }

    /** {@code path}'s segments, no query, no leading nor trailing {@code /}; none for the site's root. */
    private static String[] segments(String path) {
        int query = path.indexOf('?');
        String bare = (query < 0 ? path : path.substring(0, query)).strip();
        while (bare.startsWith("/")) bare = bare.substring(1);
        while (bare.endsWith("/")) bare = bare.substring(0, bare.length() - 1);
        return bare.isEmpty() ? new String[0] : bare.split("/", -1);
    }
}
