package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueForm;
import com.botmaker.plugin.api.value.ValueType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every SDK type reads back the literal it wrote.
 *
 * <p><b>None of these eight could, until 2026-09-20.</b> The contract's reader was a {@code default}
 * answering empty and this file overrode it nowhere, so a picture, a precision, three geometry shapes and
 * three enums were written into a user's Java by an editor that then showed them read-only — the whole
 * asymmetry that made the method abstract. This test is the property that replaces the default: the pair is
 * one fact, so it is asserted as one.
 *
 * <p>The mirror of {@code botmaker-plugin-basics}' {@code LiteralInverseTest}, which holds the other nine.
 */
class SdkLiteralInverseTest {

    private static final ValueCatalog CATALOG = SdkValueTypes.CATALOG;

    private static ValueForm one(ValueType type) {
        return ValueForm.of(type);
    }

    private static ValueForm list(ValueType type) {
        return ValueForm.listOf(ValueForm.of(type));
    }

    /** Every type, every sample: the initialiser is written, then read, and the stored value comes back. */
    @Test
    void everyTypeReadsBackWhatItWrote() {
        record Sample(ValueType type, String wire) {}
        List<Sample> samples = List.of(
                new Sample(SdkValueTypes.IMAGE_TEMPLATE, "ore"),
                new Sample(SdkValueTypes.IMAGE_TEMPLATE, "ui/health bar"),
                new Sample(SdkValueTypes.PRECISION, "12.0,1,0"),
                new Sample(SdkValueTypes.PRECISION, "2.5,400,16"),
                new Sample(SdkValueTypes.POINT, "0,0"),
                new Sample(SdkValueTypes.POINT, "-12,340"),
                new Sample(SdkValueTypes.RECT, "10,20,640,480"),
                new Sample(SdkValueTypes.SIZE, "1920,1080"),
                new Sample(SdkValueTypes.DIRECTION, "NORTH"),
                new Sample(SdkValueTypes.KEY, "ENTER"),
                new Sample(SdkValueTypes.MOUSE_BUTTON, "LEFT"));

        for (Sample sample : samples) {
            ValueForm form = one(sample.type());
            String canonical = CATALOG.normalize(sample.type().id(), sample.wire());
            String java = CATALOG.initializerOfWires(form, List.of(sample.wire())).orElseThrow();
            assertEquals(Optional.of(List.of(canonical)), CATALOG.wiresOfInitializer(form, java),
                    sample.type().id() + " wrote " + java + " and could not read it back");
        }
    }

    @Test
    void aListOfPicturesRoundTripsItemByItem() {
        ValueForm pictures = list(SdkValueTypes.IMAGE_TEMPLATE);
        String java = CATALOG.initializerOfWires(pictures, List.of("ore", "gem")).orElseThrow();
        assertEquals(Optional.of(List.of("ore", "gem")), CATALOG.wiresOfInitializer(pictures, java));
    }

    /**
     * An enum constant this build does not have is declined, not silently retyped.
     *
     * <p>The parsers here are total and fall back, so reading {@code Direction.UP} through {@code parse}
     * alone would answer {@code NORTH} — a value the user wrote replaced by one they did not, on the
     * strength of not recognising it. ({@code UP} is the real near miss: this project's own directions are
     * compass points.)
     */
    @Test
    void anEnumConstantThatDoesNotExistIsDeclined() {
        assertEquals(Optional.empty(),
                CATALOG.wiresOfInitializer(one(SdkValueTypes.DIRECTION), "Direction.UP"));
        assertEquals(Optional.empty(),
                CATALOG.wiresOfInitializer(one(SdkValueTypes.KEY), "Key.MISSING"));
    }

    /** A file that did not import the type writes the package too, and that is still this codec's literal. */
    @Test
    void aFullyQualifiedSpellingReadsToo() {
        assertEquals(Optional.of(List.of("3,4")), CATALOG.wiresOfInitializer(
                one(SdkValueTypes.POINT), "new com.botmaker.sdk.api.geometry.Point(3, 4)"));
        assertEquals(Optional.of(List.of("NORTH")), CATALOG.wiresOfInitializer(
                one(SdkValueTypes.DIRECTION),
                "com.botmaker.sdk.api.geometry.Direction.NORTH"));
    }

    /**
     * A spelling this plugin never writes is declined rather than read generously.
     *
     * <p>Each of these means what the canonical literal means, and recognising it would be recognising
     * somebody else's Java: the author wrote it on purpose, and the window shows it as written instead of
     * rewriting it the moment the file is opened.
     */
    @Test
    void anInitializerThisPluginDoesNotWriteIsDeclined() {
        assertEquals(Optional.empty(),
                CATALOG.wiresOfInitializer(one(SdkValueTypes.POINT), "Point.of(3, 4)"));
        assertEquals(Optional.empty(),
                CATALOG.wiresOfInitializer(one(SdkValueTypes.POINT), "ORIGIN"));
        assertEquals(Optional.empty(),
                CATALOG.wiresOfInitializer(one(SdkValueTypes.PRECISION), "Precision.TIGHT"));
        assertEquals(Optional.empty(), CATALOG.wiresOfInitializer(
                one(SdkValueTypes.PRECISION), "Precision.TIGHT.minArea(400)"));
        assertEquals(Optional.empty(), CATALOG.wiresOfInitializer(
                one(SdkValueTypes.SIZE), "new Size(width, height)"));
    }

    /**
     * A picture outside the project's own folder has no stored form, so it is not read as a name.
     *
     * <p>What is stored is a base name, and the folder is added back on the way out. Reading a path from
     * somewhere else would store a name that, written again, points at a different file.
     */
    @Test
    void aPictureOutsideTheProjectsFolderIsDeclined() {
        assertEquals(Optional.empty(), CATALOG.wiresOfInitializer(
                one(SdkValueTypes.IMAGE_TEMPLATE), "new ImageTemplate(\"/tmp/ore.png\")"));
        assertEquals(Optional.empty(), CATALOG.wiresOfInitializer(
                one(SdkValueTypes.IMAGE_TEMPLATE),
                "new ImageTemplate(\"src/main/resources/images/ore.jpg\")"));
    }

    @Test
    void whitespaceAsAFormatterWouldLeaveItIsTolerated() {
        assertEquals(Optional.of(List.of("10,20,640,480")), CATALOG.wiresOfInitializer(
                one(SdkValueTypes.RECT), "  new Rect( 10 , 20 , 640 , 480 )  "));
    }
}
