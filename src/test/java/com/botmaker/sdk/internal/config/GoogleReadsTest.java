// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Each instance's last-read Google accounts (claimed accounts, run-places phase 7b). */
class GoogleReadsTest {

    private static final Instant AT = Instant.parse("2026-11-27T10:00:00Z");

    @Test
    void aReadIsNotedLowerCaseSortedAndReadBackWithItsTime() {
        HostGoogleReads kept = GoogleReads.noted(null, " MEmu_1 ", Set.of("B@Example.com", "a@example.com", " "), AT);
        assertEquals(HostGoogleReads.of(new HostGoogleReads.Row("MEmu_1", "a@example.com b@example.com", AT.toString())),
                kept);
        assertEquals(Optional.of(new GoogleReads.Read(Set.of("a@example.com", "b@example.com"), AT)),
                GoogleReads.last(kept, "MEmu_1"));
        assertEquals(Optional.empty(), GoogleReads.last(kept, "MEmu_2"), "an instance never read");
    }

    @Test
    void theSameAccountsReadAgainChangeNothingAndOthersReplaceTheRow() {
        HostGoogleReads kept = GoogleReads.noted(HostGoogleReads.none(), "MEmu_1", Set.of("a@example.com"), AT);
        assertSame(kept, GoogleReads.noted(kept, "MEmu_1", Set.of("A@example.com"), AT.plusSeconds(60)));
        HostGoogleReads other = GoogleReads.noted(kept, "MEmu_1", Set.of(), AT.plusSeconds(60));
        assertEquals(1, other.list().size());
        assertEquals(Set.of(), GoogleReads.last(other, "MEmu_1").orElseThrow().accounts(), "signed out of all");
        assertEquals(AT.plusSeconds(60), GoogleReads.last(other, "MEmu_1").orElseThrow().at());
    }

    @Test
    void noInstanceOrNoAnswerNotesNothingAndATimeThatDoesNotReadIsTheEpoch() {
        HostGoogleReads kept = HostGoogleReads.none();
        assertSame(kept, GoogleReads.noted(kept, " ", Set.of("a@example.com"), AT));
        assertSame(kept, GoogleReads.noted(kept, "MEmu_1", null, AT));
        HostGoogleReads odd = HostGoogleReads.of(new HostGoogleReads.Row("MEmu_1", "a@example.com", "yesterday"));
        assertEquals(Instant.EPOCH, GoogleReads.last(odd, "MEmu_1").orElseThrow().at());
    }

    @Test
    void aRowWrittenByHandReadsWithItsDoublesAndCapitalsFolded() {
        HostGoogleReads hand = HostGoogleReads.of(new HostGoogleReads.Row("MEmu_1", "a@x.com A@x.com  b@x.com", AT.toString()));
        assertEquals(Set.of("a@x.com", "b@x.com"), GoogleReads.last(hand, "MEmu_1").orElseThrow().accounts());
    }

    @Test
    void aChangedInstancesRowStaysWhereItStood() {
        HostGoogleReads kept = HostGoogleReads.none();
        for (String instance : new String[] {"A", "B", "C"}) kept = GoogleReads.noted(kept, instance, Set.of(), AT);
        kept = GoogleReads.noted(kept, "A", Set.of("a@x.com"), AT);
        assertEquals(List.of("A", "B", "C"), kept.list().stream().map(HostGoogleReads.Row::instance).toList());
    }
}
