package com.botmaker.sdk.plugin.map;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.History;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.source.PluginValues;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 🗺 Screens' bar at the window's least width, headless: no label cut, and the rest in ⋯. */
class ScreenMapBarTest extends ApplicationTest {

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

    @Override
    public void start(Stage stage) {
        map = new ScreenMap(services(project), true);
        // The window as ScreenMap.open frames it: the rail beside the map, at its least width.
        stage.setScene(new Scene((BorderPane) map.windowNode(), ScreenMap.LEAST_WIDTH, 460));
        stage.show();
    }

    @AfterEach
    void stopTheMap() {
        // Its poll and the live board would outlive the test on the shared toolkit.
        interact(map::stop);
    }

    @Test
    void noBarLabelIsCut() {
        WaitForAsyncUtils.waitForFxEvents();
        Parent bar = (Parent) map.bar();
        double width = bar.getLayoutBounds().getWidth();
        List<Labeled> labeled = new ArrayList<>();
        for (Node each : bar.getChildrenUnmodifiable()) if (each instanceof Labeled one) labeled.add(one);
        assertFalse(labeled.isEmpty());
        for (Labeled one : labeled) {
            // The count beside the buttons is the one label that may give way.
            if (one.getStyleClass().contains("screen-panel-path")) continue;
            Text drawn = (Text) one.lookup(".text");
            assertEquals(one.getText(), drawn.getText(), "cut: " + one.getText());
            Bounds at = one.getBoundsInParent();
            assertTrue(at.getMaxX() <= width + 0.5, "past the window's edge: " + one.getText());
        }
    }

    @Test
    void overflowHoldsTheRest() {
        List<String> texts = new ArrayList<>();
        interact(() -> map.moreItems().forEach(item -> texts.add(item.getText())));
        List<String> starts = List.of("⇄ All links", "Other ways", "Tidy", "⛶ Full screen", "↶ Undo", "↷ Redo");
        assertEquals(starts.size(), texts.size(), texts.toString());
        for (int i = 0; i < starts.size(); i++) {
            assertTrue(texts.get(i).startsWith(starts.get(i)), texts.get(i));
        }
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
