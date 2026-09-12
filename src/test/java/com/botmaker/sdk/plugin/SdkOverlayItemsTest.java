package com.botmaker.sdk.plugin;

import com.botmaker.plugin.api.ToolbarGroup;
import com.botmaker.plugin.api.ToolbarItem;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin's overlay row, asserted on the data rather than on a window.
 *
 * <p>Worth a test because {@code toolbarItems()} is called by the host while a project is opening, on a
 * classpath that may have no JavaFX at all ({@code botmaker plugin validate}, the registry's CI). An item
 * list that links a JavaFX type while being built is the 2026-09-05 bug in a new place — and nothing in the
 * three handlers may run until a host with a screen presses one.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SdkOverlayItemsTest {

    @Test
    void three_items_land_on_the_overlay_row() {
        List<ToolbarItem> overlay = new SdkPlugin().toolbarItems().stream()
                .filter(item -> item.group() == ToolbarGroup.OVERLAY)
                .toList();

        assertEquals(3, overlay.size());
        assertTrue(overlay.stream().anyMatch(item -> item.id().equals("point-here")));
        assertTrue(overlay.stream().anyMatch(item -> item.id().equals("picture-here")));
        assertTrue(overlay.stream().anyMatch(item -> item.id().equals("record-here")));
    }

    @Test
    void building_the_items_links_no_javafx_and_every_one_has_a_tooltip() {
        for (ToolbarItem item : new SdkPlugin().toolbarItems()) {
            assertFalse(item.tooltip() == null || item.tooltip().isBlank(),
                    item.id() + " has no tooltip; a glyph and two words are not an explanation");
        }
    }

    @Test
    void the_overlay_row_reads_point_then_picture_then_record() {
        // Declaration order is not the bar's reading order — the host sorts on the order field — so the
        // three orders are the only thing that says a user is offered "point the bot here" first.
        List<String> ids = new SdkPlugin().toolbarItems().stream()
                .filter(item -> item.group() == ToolbarGroup.OVERLAY)
                .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                .map(ToolbarItem::id)
                .toList();

        assertEquals(List.of("point-here", "picture-here", "record-here"), ids);
    }
}
