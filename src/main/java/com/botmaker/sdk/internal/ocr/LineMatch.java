package com.botmaker.sdk.internal.ocr;

import java.util.Locale;

/**
 * Whether a line OCR read is some words the map names ({@code Caption}): the whole line, whatever its case and the
 * spaces or marks at its ends, within a few edits that grow with the words' length — none up to four letters, one up
 * to eight, two past. A short label forgiven two edits matches almost any line, and a word one letter longer is often
 * another state of the same button ({@code Claim}, {@code Claimed}): the user's call, 2026-10-22.
 */
public final class LineMatch {

    private LineMatch() {}

    /** Whether {@code line} is {@code words}, as the map reads them. */
    public static boolean same(String line, String words) {
        if (line == null || words == null) return false;
        String read = plain(line);
        String said = plain(words);
        // Words of marks only (+, >>) are the line exactly, its case and spaces aside.
        if (said.isEmpty()) {
            String marks = words.strip().replaceAll("\\s+", "");
            return !marks.isEmpty() && line.strip().replaceAll("\\s+", "").equalsIgnoreCase(marks);
        }
        return distance(read, said) <= edits(said.length());
    }

    /** How many edits words of {@code length} letters are forgiven: a misread {@code l↔1}, a dropped letter. */
    public static int edits(int length) {
        return length <= 4 ? 0 : length <= 8 ? 1 : 2;
    }

    /** {@code text} lower-cased, its inner spaces one, and the spaces and marks at its ends gone. */
    static String plain(String text) {
        String spaced = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        int from = 0;
        int to = spaced.length();
        while (from < to && !Character.isLetterOrDigit(spaced.charAt(from))) from++;
        while (to > from && !Character.isLetterOrDigit(spaced.charAt(to - 1))) to--;
        return spaced.substring(from, to);
    }

    /** The Levenshtein edit distance between {@code a} and {@code b}: insertions, deletions and substitutions. */
    public static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
