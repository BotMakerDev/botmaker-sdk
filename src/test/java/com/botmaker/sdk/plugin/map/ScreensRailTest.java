package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.sdk.api.screens.Setup;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.map.ScreensRail.View;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 🗺 Screens' rail, headless: it switches the window's view, and Goals' double click comes back to the map. */
class ScreensRailTest extends ApplicationTest {

    static {
        // Read when the toolkit starts, so before ApplicationTest starts it: no display needed.
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    static Path project;

    private ScreenMap map;
    private BorderPane window;

    @Override
    public void start(Stage stage) {
        map = new ScreenMap(services(project), true);
        window = (BorderPane) map.windowNode();
        stage.setScene(new Scene(window, 1180, 720));
        stage.show();
    }

    @AfterEach
    void stopTheMap() {
        interact(map::stop);
    }

    @Test
    void railSwitchesView() {
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(View.MAP, map.viewShown());
        assertSame(map.node(), window.getCenter());
        List<ToggleButton> buttons = railButtons();
        assertEquals(List.of("Map", "Goals", "Explore", "Pictures"),
                buttons.stream().map(b -> b.getText().split("\n")[1]).toList());
        assertTrue(buttons.stream().allMatch(b -> b.isVisible() && b.isManaged()), "every view offered");

        clickOn(button("Goals"));
        assertEquals(View.GOALS, map.viewShown());
        Node goals = window.getCenter();
        assertNotNull(goals);
        assertFalse(goals == map.node());

        clickOn(button("Goals"));
        assertEquals(View.GOALS, map.viewShown(), "a press on the view shown keeps it");
        assertSame(goals, window.getCenter());

        clickOn(button("Map"));
        assertSame(map.node(), window.getCenter());
        clickOn(button("Goals"));
        assertSame(goals, window.getCenter(), "a view is built once and kept");
    }

    @Test
    void goalsDoubleClickOpensMap() {
        Map<String, MapScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", MapScreen.of(Setup.named("Home")));
        screens.put("SHOP", MapScreen.of(Setup.named("Shop")));
        interact(() -> {
            map.draw(ScreenGraph.of(screens));
            map.showView(View.GOALS);
        });
        assertEquals(View.GOALS, map.viewShown());

        // What a Goals row's double click hands its screen to.
        interact(() -> map.openScreen("SHOP"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(View.MAP, map.viewShown());
        assertSame(map.node(), window.getCenter());
        TabPane tabs = (TabPane) map.node().lookup(".tab-pane");
        assertEquals("SHOP", tabs.getSelectionModel().getSelectedItem().getUserData(), "the screen's tab in front");
    }

    private List<ToggleButton> railButtons() {
        return ((Parent) window.getLeft()).getChildrenUnmodifiable().stream()
                .map(ToggleButton.class::cast).toList();
    }

    private ToggleButton button(String name) {
        return railButtons().stream().filter(b -> b.getText().endsWith("\n" + name)).findFirst().orElseThrow();
    }

    private static StudioServices services(Path project) {
        return new StudioServices() {
            @Override
            public Path projectDir() {
                return project;
            }

            @Override
            public Path resourcesDir() {
                return project;
            }

            @Override
            public Theme theme() {
                return null;
            }

            @Override
            public Dialogs dialogs() {
                return null;
            }

            @Override
            public PluginValues pluginValues() {
                return null;
            }

            @Override
            public History history() {
                return History.NONE;
            }
        };
    }
}
