package com.botmaker.sdk.plugin.pictures;

import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manage Pictures' screen groups: read off the map, never stored, never filed into by hand. */
class ScreenGroupsTest {

    private static final Path HOME = Path.of("images/home.png");
    private static final Path CLOSE = Path.of("images/close_x.png");
    private static final Path TITLE = Path.of("images/campaign_title.png");
    private static final Path ORE = Path.of("images/ore.png");

    private static ImageTemplate picture(String id) {
        return new ImageTemplate("images/" + id + ".png");
    }

    @Test
    void eachScreenGroupsThePicturesItsMarkerAndInputsNameAndAPictureMayBeUnderTwo() {
        Map<String, GameScreen> screens = new LinkedHashMap<>();
        screens.put("HOME", GameScreen.named("Home").marker(picture("home"))
                .inputs(List.of(Input.click(picture("campaign_title")), Input.click(picture("close_x")))));
        screens.put("CAMPAIGN", GameScreen.named("Campaign").marker(picture("campaign_title"))
                .from(Way.of(BotConstants.screen("HOME"), Input.click(picture("campaign_title"))))
                .back(Input.click(picture("close_x"))));
        screens.put("ENGINEER", GameScreen.named("Engineer"));

        Map<String, List<Path>> groups = ScreenGroups.groups(ScreenGraph.of(screens), List.of(HOME, CLOSE, TITLE, ORE));

        assertEquals(List.of("🗺 Home", "🗺 Campaign", "🗺 Engineer"), List.copyOf(groups.keySet()));
        assertEquals(List.of(HOME, TITLE, CLOSE), groups.get("🗺 Home"));
        assertEquals(List.of(TITLE, CLOSE), groups.get("🗺 Campaign"), "its way back is its picture too");
        assertEquals(List.of(), groups.get("🗺 Engineer"), "a screen naming nothing is still a group");
    }

    @Test
    void screenRowsSitUnderTheirOwnHeadingReadOnlyAndNoTagMayTakeTheirName() {
        Map<String, List<Path>> byTag = new LinkedHashMap<>();
        byTag.put(TemplateManifest.ALL, List.of(HOME, ORE));
        byTag.put("Campaign", List.of(ORE));
        byTag.put("🗺 Campaign", List.of(HOME));
        TagCatalog catalog = TagCatalog.of(List.of("Collect"), List.of("Campaign"));

        List<TemplateGalleryModel.Row> rows = TemplateGalleryModel.rows(byTag, catalog, List.of("🗺 Campaign"));
        assertEquals(List.of(new TemplateGalleryModel.TagRow(TemplateManifest.ALL, 2, false),
                new TemplateGalleryModel.Heading("Activities"), new TemplateGalleryModel.TagRow("Collect", 0, true),
                new TemplateGalleryModel.Heading("Screens"), new TemplateGalleryModel.TagRow("🗺 Campaign", 1, true),
                new TemplateGalleryModel.Heading("Custom"), new TemplateGalleryModel.TagRow("Campaign", 1, false)),
                rows, "an older bot's screen tag stays a custom tag beside the map's group");

        assertTrue(TemplateGalleryModel.isScreenKey(TemplateGalleryModel.screenKey("Campaign")));
        assertFalse(TemplateGalleryModel.isScreenKey("Campaign"));
        assertNotNull(catalog.nameProblem("🗺 Arena", null));
        assertNull(catalog.nameProblem("Arena", null));
    }
}
