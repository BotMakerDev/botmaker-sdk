// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.host;

import com.botmaker.shared.emulator.config.AccountRecord;
import com.botmaker.shared.emulator.config.AccountRecord.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChooserWatchTest {

    @Test
    void theInstancesDefaultIsTheBooksEntryWithItsAliasesElseTheAddressAlone() {
        AccountRecord entry = new AccountRecord(Kind.GOOGLE, "main@example.com", "")
                .withAliases(List.of("alias@example.com"));
        List<AccountRecord> book = List.of(new AccountRecord(Kind.MAIL, "alias@example.com"), entry);

        assertEquals(entry, ChooserWatch.account(book, "Alias@example.com"), "found through its alias");
        assertEquals(new AccountRecord(Kind.GOOGLE, "new@example.com"), ChooserWatch.account(book, "new@example.com"));
    }
}
