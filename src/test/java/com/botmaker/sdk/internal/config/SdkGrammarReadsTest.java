package com.botmaker.sdk.internal.config;

import com.botmaker.plugin.basics.store.ProjectValues;
import com.botmaker.plugin.basics.store.Settings;
import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.api.vision.Images;
import com.botmaker.sdk.authoring.WireText;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This SDK's own eight value types, read back the way a running bot reads them — through plugin #2's
 * {@link Settings}, over {@code src/test/resources/activities.json}, which sits exactly where a generated
 * bot's own file sits.
 *
 * <p><b>Nothing here registers a grammar.</b> That is the test: {@link SdkGrammar} is found through
 * {@code META-INF/services}, which is the whole arrangement a bot depends on and the one part of it that no
 * amount of unit testing of {@code WireText} would exercise. A missing or misspelled service file makes every
 * case below throw.
 *
 * <p><b>It asks {@code com.botmaker.plugin.basics.store.Settings} directly since 2026-09-11</b>, when the
 * SDK facade over it — {@code api.config.Settings}, and {@code Wire} before that — was deleted. The test is
 * unchanged otherwise, which is the useful part: the facade held no logic, so every answer below is the one
 * it always gave, and this file is now testing the thing that actually answers rather than a delegation.
 *
 * <p>What is being defended is the trade inherited from {@code Wire}: the values keep the names the editor
 * gave them, and a misspelling stops being a compile error. That is only acceptable while every misspelling
 * has a defined, harmless answer — which is most of what follows.
 */
class SdkGrammarReadsTest {

    // ---- the grammar the classpath supplies -------------------------------------------------------------

    @Test
    void everySdkTypeIsReadableThroughTheServiceLoadedGrammar() {
        assertEquals(20, Settings.load("minHealth", int.class));
        assertEquals("hello", Settings.load("greeting", String.class));
        assertEquals(Duration.ofSeconds(90), Settings.load("restBetween", Duration.class));
        assertEquals(new Point(10, 20), Settings.load("anchor", Point.class));
    }

    @Test
    void aPrimitiveAndItsBoxedClassAreTheSameQuestion() {
        assertEquals(Settings.load("minHealth", Integer.class), Settings.load("minHealth", int.class),
                "a bot writes int.class because the field it assigns to is an int");
    }

    @Test
    void everyReaderAgreesWithTheEditorsOwnParser() {
        // One grammar, two readers: SdkGrammar wraps exactly the WireText calls SdkValueTypes' codecs do,
        // so the editor and the running bot cannot disagree about what a stored string means.
        assertEquals(WireText.duration("1m30s"), Settings.load("restBetween", Duration.class));
        assertEquals(WireText.point("10,20"), Settings.load("anchor", Point.class));
    }

    @Test
    void aListIsReadWholeOrByItsFirstItem() {
        assertEquals(List.of("ore", "gem"), Settings.loadAll("targets", String.class));
        assertEquals("ore", Settings.load("targets", String.class));
    }

    // ---- activities are a different list ----------------------------------------------------------------

    @Test
    void anActivitysSwitchIsNotAVariable() {
        assertTrue(Settings.enabled("Mining"));
        assertFalse(Settings.enabled("Fishing"));
        assertFalse(Settings.declares("Mining"));
    }

    // ---- the cost of losing the compiler ----------------------------------------------------------------

    @Test
    void aMisspelledNameAnswersItsTypesFallback() {
        assertEquals(0, Settings.load("minHelath", int.class));
        assertEquals("", Settings.load("greetnig", String.class));
        assertEquals(Duration.ZERO, Settings.load("restBteween", Duration.class));
        assertEquals(List.of(), Settings.loadAll("targts", String.class));
    }

    @Test
    void aNameDeclaredAsAnotherTypeAnswersTheFallbackToo() {
        assertEquals(0, Settings.load("greeting", int.class));
        assertFalse(Settings.load("minHealth", boolean.class));
        assertEquals(Color.WHITE, Settings.load("greeting", Color.class));
    }

    @Test
    void tellsAnUnsetValueApartFromAMisspelledOne() {
        assertTrue(Settings.declares("unset"));
        assertFalse(Settings.declares("unsett"));
        assertEquals("", Settings.load("unset", String.class));
    }

    @Test
    void nothingTheGrammarCoversThrowsForANameThatIsNotThere() {
        String absent = "nothingIsCalledThis";

        assertEquals("", Settings.load(absent, String.class));
        assertFalse(Settings.load(absent, boolean.class));
        assertEquals(0.0, Settings.load(absent, double.class));
        assertEquals('a', Settings.load(absent, char.class));
        assertEquals(2000, Settings.load(absent, java.time.LocalDate.class).getYear());
        assertEquals(0, Settings.load(absent, java.time.LocalTime.class).getHour());
        assertEquals(new Rect(0, 0, 0, 0), Settings.load(absent, Rect.class));
        // The enums fall back to their own first constant and the records to a clamped default; that none of
        // them throws is the whole point.
        assertNotNull(Settings.load(absent, com.botmaker.sdk.api.geometry.Direction.class));
        assertNotNull(Settings.load(absent, com.botmaker.sdk.api.interaction.Key.class));
        assertNotNull(Settings.load(absent, com.botmaker.sdk.api.interaction.MouseButton.class));
        assertNotNull(Settings.load(absent, com.botmaker.sdk.api.vision.Precision.class));
        assertNotNull(Settings.load(absent, ImageTemplate.class));
    }

    // ---- the one thing that throws ----------------------------------------------------------------------

    @Test
    void aTypeNoPluginOnTheClasspathClaimsIsAPackagingError() {
        // Not a bad file — a bot compiled against a plugin it does not run with. There is no value to fall
        // back to, and inventing one would show up as "the default, always".
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Settings.load("greeting", java.util.Locale.class));
        assertTrue(e.getMessage().contains("java.util.Locale"), e.getMessage());
    }

    // ---- the text underneath ----------------------------------------------------------------------------

    /**
     * The honest bottom of the stack: a value written by a plugin whose grammar this bot does not carry is
     * still text, and reading it is better than a throw.
     *
     * <p>It asks {@code ProjectValues.current()} rather than {@code Settings}, which is where these three
     * always did their work — the deleted SDK facade forwarded {@code one}/{@code many}/{@code names}
     * straight to it, and plugin #2's {@code Settings} does not repeat them.
     */
    @Test
    void theStoredTextIsStillReachable() {
        assertEquals("1m30s", ProjectValues.current().one("restBetween"));
        assertEquals(List.of("ore", "gem"), ProjectValues.current().many("targets"));
        assertTrue(ProjectValues.current().variables().contains("minHealth"));
        assertFalse(ProjectValues.current().variables().contains("Mining"));
    }

    // ---- the member that moved --------------------------------------------------------------------------

    @Test
    void aPictureIsNamedByItsFileAndNotByAVariable() {
        // Images.named takes a file's base name; Settings.load takes a variable whose value is a picture.
        assertEquals("ore", Images.named("ore").id());
        assertTrue(Images.named("ore").filePath().endsWith("images/ore.png"));
        assertNotNull(Images.named(null), "total, like every other name lookup");
    }
}
