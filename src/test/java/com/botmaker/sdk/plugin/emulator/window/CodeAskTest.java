// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.window;

import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeAskTest {

    @Test
    void theDialogSaysTheMethodAndTheTry() {
        CodeAsk code = new CodeAsk("op", "user@example.com", Verification.CODE, 2);
        assertEquals("Verification code for user@example.com", code.header());
        assertTrue(code.words().contains("authenticator"), code.words());
        assertTrue(code.hint().contains("never stored") && code.hint().contains("Try 2 of 3"), code.hint());

        CodeAsk backup = new CodeAsk("op", "user@example.com", Verification.BACKUP_CODE, 1);
        assertEquals("Backup code for user@example.com", backup.header());
        assertTrue(backup.words().contains("backup codes"), backup.words());
    }

    @Test
    void aCodeAnsweredLateIsZeroedAndTheFirstAnswerStands() throws Exception {
        CodeAsk code = new CodeAsk("op", "user@example.com", Verification.CODE, 1);
        char[] typed = {'1', '2', '3'};
        code.answer(typed);
        assertArrayEquals(new char[] {'1', '2', '3'}, code.answer().get(), "handed over as typed");

        CodeAsk cancelled = new CodeAsk("op", "user@example.com", Verification.CODE, 1);
        cancelled.answer(null);
        char[] late = {'4', '5', '6'};
        cancelled.answer(late);
        assertNull(cancelled.answer().get());
        assertArrayEquals(new char[3], late, "a code nobody waits for is zeroed");
    }
}
