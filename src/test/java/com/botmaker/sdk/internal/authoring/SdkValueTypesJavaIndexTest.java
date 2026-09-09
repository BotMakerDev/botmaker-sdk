package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.sdk.api.geometry.Direction;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.interaction.Key;
import com.botmaker.sdk.api.interaction.MouseButton;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Precision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SDK's own eight, asked for by Java class rather than by id.
 *
 * <p>This is the consumer half of {@code ValueCatalog.forJava}: a bot author writes
 * {@code Settings.load("target", Rect.class)} and an editor author writes {@code Rect.class} too, and
 * neither of them ever spells {@code RECT}. The id stays what the project file holds and stops being
 * something anybody outside this class has to know.
 *
 * <p><b>The nine JDK types are plugin-basics' since 2026-09-09</b>, and what a host actually holds is the
 * merge of the two catalogs — that is {@link ValueVocabularyTest}'s subject, together with the bot-side
 * grammar, which reads types from both halves and so cannot be checked against this catalog alone.
 */
class SdkValueTypesJavaIndexTest {

    private static final ValueCatalog CATALOG = SdkValueTypes.CATALOG;

    @Test
    void everySdkTypeIsFoundByItsJavaClass() {
        assertEquals(SdkValueTypes.IMAGE_TEMPLATE, CATALOG.forJava(ImageTemplate.class).orElseThrow());
        assertEquals(SdkValueTypes.PRECISION, CATALOG.forJava(Precision.class).orElseThrow());
        assertEquals(SdkValueTypes.POINT, CATALOG.forJava(Point.class).orElseThrow());
        assertEquals(SdkValueTypes.RECT, CATALOG.forJava(Rect.class).orElseThrow());
        assertEquals(SdkValueTypes.SIZE, CATALOG.forJava(Size.class).orElseThrow());
        assertEquals(SdkValueTypes.DIRECTION, CATALOG.forJava(Direction.class).orElseThrow());
        assertEquals(SdkValueTypes.KEY, CATALOG.forJava(Key.class).orElseThrow());
        assertEquals(SdkValueTypes.MOUSE_BUTTON, CATALOG.forJava(MouseButton.class).orElseThrow());
    }

    /** The nine went, ids and Java types together — asking here for one is now an ordinary empty answer. */
    @Test
    void theJdkTypesAreNotTheSdksAnyMore() {
        assertTrue(CATALOG.forJava(Duration.class).isEmpty());
        assertTrue(CATALOG.forJava(String.class).isEmpty());
        assertTrue(CATALOG.forJava(int.class).isEmpty());
        assertEquals(8, CATALOG.types().size(), CATALOG.types().toString());
    }

    /**
     * Eight types, eight Java types. It was already true and nothing made it true; the builder refuses a
     * second claimant now, so this test is what says the SDK never asks it to.
     */
    @Test
    void noTwoSdkTypesClaimOneJavaType() {
        List<String> names = CATALOG.types().stream().map(ValueType::javaName).toList();
        Set<String> distinct = new LinkedHashSet<>(names);
        assertEquals(names.size(), distinct.size(), names.toString());
    }
}
