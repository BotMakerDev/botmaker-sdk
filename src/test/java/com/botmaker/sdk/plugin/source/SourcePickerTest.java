package com.botmaker.sdk.plugin.source;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The region row narrows a source only when all four fields make a rectangle inside it. */
class SourcePickerTest {

    @Test
    void theRegionFieldsParseOnlyAWholeRectangle() {
        assertEquals(new Rectangle(1, 2, 3, 4), SourcePicker.parseRegion("1", " 2", "3 ", "4"));
        assertNull(SourcePicker.parseRegion("1", "2", "3", ""));
        assertNull(SourcePicker.parseRegion("1", "2", "0", "4"));
        assertNull(SourcePicker.parseRegion("-1", "2", "3", "4"));
        assertNull(SourcePicker.parseRegion("a", "2", "3", "4"));
    }
}
