// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Role;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.api.text.Words;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElementsViewTest {

    /**
     * A shot Delete may remove lies inside the resources: never an absolute path, one that climbs out, or one through
     * a linked folder or to a link.
     */
    @Test
    void aShotOutsideTheResourcesIsNeverTouched(@TempDir Path dir) throws IOException {
        Path resources = Files.createDirectories(dir.resolve("resources"));
        Files.createDirectories(resources.resolve("elements"));
        Path outside = Files.createDirectories(dir.resolve("outside"));
        Files.writeString(outside.resolve("secret.png"), "x");
        Files.createSymbolicLink(resources.resolve("linked"), outside);
        Files.createSymbolicLink(resources.resolve("elements/link.png"), outside.resolve("secret.png"));

        assertEquals(resources.toRealPath().resolve("elements/home.png"),
                ElementsView.shotFile(resources, "elements/home.png"));
        assertNull(ElementsView.shotFile(resources, "linked/secret.png"));
        assertNull(ElementsView.shotFile(resources, "elements/link.png"));
        assertNull(ElementsView.shotFile(resources, "missing/home.png"));
        assertNull(ElementsView.shotFile(resources, "../../pom.xml"));
        assertNull(ElementsView.shotFile(resources, "elements/../../x.png"));
        assertNull(ElementsView.shotFile(resources, "/etc/passwd"));
        assertNull(ElementsView.shotFile(resources, "."));
        assertNull(ElementsView.shotFile(resources, null));
        assertNull(ElementsView.shotFile(null, "elements/home.png"));
    }

    /** A tree element's row says what finds it, its shot, and the screens whose inputs press it, its shot aside. */
    @Test
    void aTreeElementsRowSaysWhatFindsItAndWhereItIsPressed() {
        Element home = Element.byRole(Role.LINK, Words.of("Home")).shot("elements/home.png");
        Element close = Element.byRole(Role.BUTTON, Words.of("Close"));
        Map<String, Input> pricing = Map.of("home", Button.of(home.shot(null)).free());
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("PRICING", new MapScreen(Setup.named("Pricing").route("/pricing"), pricing));
        screens.put("ABOUT", new MapScreen(Setup.named("About").route("/about"), Map.of("home", Button.of(home).free())));
        Map<String, Element> elements = new LinkedHashMap<>();
        elements.put("HOME", home);
        elements.put("CLOSE", close);

        List<ElementsView.Row> rows = ElementsView.rows(elements, ScreenGraph.of(screens));

        assertEquals(new ElementsView.Row("HOME", "link \"Home\"", "elements/home.png", List.of("Pricing", "About")),
                rows.getFirst());
        assertEquals(new ElementsView.Row("CLOSE", "button \"Close\"", null, List.of()), rows.get(1));
        assertTrue(rows.getFirst().matches("about"));
        assertFalse(rows.get(1).matches("home"));
    }
}
