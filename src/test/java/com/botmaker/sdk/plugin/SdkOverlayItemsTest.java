package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.toolbar.ToolbarGroup;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The plugin's overlay row, asserted on the data rather than on a window.
 *
 * <p>Worth a test because {@code toolbarItems()} is called by the host while a
 * project is opening, on a classpath that may have no JavaFX at all ({@code botmaker plugin validate}, the
 * registry's CI). A list that links a JavaFX type while being built is the 2026-09-05 bug in a new place.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SdkOverlayItemsTest {

    @Test
    void building_the_items_links_no_javafx_and_every_one_has_a_tooltip() {
        for (ToolbarItem item : new SdkPlugin().toolbarItems()) {
            assertFalse(item.tooltip() == null || item.tooltip().isBlank(),
                    item.id() + " has no tooltip; a glyph and two words are not an explanation");
        }
    }

    @Test
    void the_overlay_row_reads_point_then_picture() {
        // Declaration order is not the bar's reading order — the host sorts on the order field.
        List<String> ids = new SdkPlugin().toolbarItems().stream()
                .filter(item -> item.group() == ToolbarGroup.OVERLAY)
                .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                .map(ToolbarItem::id)
                .toList();

        assertEquals(List.of("point-here", "picture-here"), ids);
    }
}
