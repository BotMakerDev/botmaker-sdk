package com.botmaker.sdk.plugin.types;

import com.botmaker.sdk.api.flow.Activity;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.PictureTag;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A picture's tags and activities in its Java (2026-10-14): {@code .tags(List.of(Tags.UI))} and
 * {@code .activities(List.of(Activities.Collect.class))} come apart and go back together with the rest of it, and a
 * tag inside a picture is only ever written as its constant.
 */
class PictureTagsTypeTest {

    private static final ImageTemplate ORE = new ImageTemplate("src/main/resources/images/ore.png")
            .at(new Rect(1, 2, 3, 4))
            .tags(List.of(BotConstants.tag("UI"), BotConstants.tag("SHARED")))
            .inActivities(List.of(new Activity("Collect")));

    @Test
    void aTaggedPictureComesApartAndGoesBackTogetherWhole() {
        assertEquals(ORE, SdkTypes.IMAGE_TEMPLATE.build(SdkTypes.IMAGE_TEMPLATE.components(ORE)));
        ImageTemplate plain = new ImageTemplate("src/main/resources/images/ore.png");
        assertEquals(plain, SdkTypes.IMAGE_TEMPLATE.build(SdkTypes.IMAGE_TEMPLATE.components(plain)));
    }

    @Test
    void tagsAndActivitiesAreHowAPictureDiffersAndTheirOrderIsNot() {
        ImageTemplate untagged = new ImageTemplate("src/main/resources/images/ore.png").at(new Rect(1, 2, 3, 4));
        assertNotEquals(untagged, ORE, "a picture filed elsewhere is written again");
        assertEquals(ORE, untagged.inActivities(List.of(new Activity("Collect")))
                .tags(List.of(BotConstants.tag("SHARED"), BotConstants.tag("UI"))));
        assertEquals(ORE.tags(), ORE.at(new Rect(9, 9, 9, 9)).in(null).tags(), "a recut keeps them");
    }

    @Test
    void aTagIsSpelledOutOnlyWhereItIsDeclared() {
        assertTrue(SdkTypes.PICTURE_TAG.writes(PictureTag.named("UI")), "Tags.UI = PictureTag.named(\"UI\")");
        assertFalse(SdkTypes.PICTURE_TAG.writes(BotConstants.tag("UI")), "inside a picture: Tags.UI");
        assertEquals("UI", BotConstants.tagName(BotConstants.tag("UI")));
        assertEquals(null, BotConstants.tagName(PictureTag.named("UI")));
        assertEquals(PictureTag.named("Arena of Kings"), PictureTag.named("  arena   of kings "));
        assertThrows(IllegalArgumentException.class, () -> PictureTag.named(" "));
        assertTrue(new SdkPlugin().componentTypes().contains(SdkTypes.PICTURE_TAG));
    }
}
