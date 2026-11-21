// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConstantNamesTest {

    @Test
    void wordsAndCamelCaseBecomeUpperSnake() {
        assertEquals("BAG_FULL", ConstantNames.constantFor("Bag full"));
        assertEquals("HANDLE_FULL_INVENTORY", ConstantNames.constantFor("HandleFullInventory"));
        assertEquals("NOTHING_LEFT", ConstantNames.constantFor("NOTHING_LEFT"));
        assertEquals("LEVEL2_BOSS", ConstantNames.constantFor("Level2Boss"));
        assertNull(ConstantNames.constantFor("2 cats"));
    }

    @Test
    void aOneLetterWordOrAnAcronymEndsBeforeTheNextWord() {
        assertEquals("BOOK_A_DEMO", ConstantNames.constantFor("BookADemo"));
        assertEquals("HTTP_SERVER", ConstantNames.constantFor("HTTPServer"));
        assertEquals("GET_URL", ConstantNames.constantFor("GetURL"));
    }
}
