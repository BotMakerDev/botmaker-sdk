package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.sdk.api.text.OcrOptions;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code OcrOptions} as a declared type: its constructor gives every part back, a fresh one has no part the
 * host cannot write, and the chain a person writes by hand is read and never written.
 */
class OcrOptionsTypeTest {

    private static ComponentType<OcrOptions> chain(String name) {
        return SdkTypes.OCR_CHAINS.stream()
                .filter(c -> c.factory().getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theConstructorGivesEveryPartBack() {
        OcrOptions value = OcrOptions.defaults().withLanguages("eng+jpn").withUpscale(3.0)
                .withBinarize(OcrOptions.BinarizeMode.ADAPTIVE).withInvert(true).withCharWhitelist("0123456789")
                .withLevel(TextResult.Level.LINE);
        assertEquals(value, SdkTypes.OCR_OPTIONS.build(SdkTypes.OCR_OPTIONS.components(value)));
    }

    /** The host writes no {@code null} part, so a fresh value written through the constructor must have none. */
    @Test
    void aFreshOneIsWhatTextReadsWithAndHasNoNullPart() {
        OcrOptions fresh = SdkTypes.OCR_OPTIONS.fresh();
        assertEquals(Text.DEFAULT_OPTIONS.withCharWhitelist(""), fresh);
        for (Object part : SdkTypes.OCR_OPTIONS.components(fresh)) assertNotNull(part);
    }

    @Test
    void theChainBuildsWhatEachMethodDoes() {
        OcrOptions base = OcrOptions.defaults();
        assertEquals(OcrOptions.defaults(), chain("defaults").build(List.of()));
        assertEquals(base.withUpscale(3.0), chain("withUpscale").build(List.of(base, 3.0)));
        assertEquals(base.withLevel(TextResult.Level.LINE),
                chain("withLevel").build(List.of(base, TextResult.Level.LINE)));
        assertEquals(base.withLanguages("eng+kor"), chain("withLanguages").build(List.of(base, "eng+kor")));
        assertEquals(base.withCharWhitelist("0123456789"),
                chain("withCharWhitelist").build(List.of(base, "0123456789")));
    }

    /**
     * Every wither is an instance factory on the value itself, so the host reads it and never writes it; the
     * one static call, {@code defaults()}, is not the type's writer either — {@link SdkTypes#OCR_OPTIONS} is.
     */
    @Test
    void eachWitherIsAnInstanceFactoryThatGivesItsPartsBack() {
        OcrOptions value = OcrOptions.defaults().withUpscale(2.5);
        for (ComponentType<OcrOptions> each : SdkTypes.OCR_CHAINS) {
            Method method = (Method) each.factory();
            if (method.getName().equals("defaults")) continue;
            assertFalse(Modifier.isStatic(method.getModifiers()), method::toString);
            assertEquals(OcrOptions.class, each.componentTypes().getFirst());
            assertEquals(value, each.build(each.components(value)), method::toString);
        }
        assertEquals(10, SdkTypes.OCR_CHAINS.size());
    }

    @Test
    void thePluginDeclaresTheTypeAndListsTheChain() {
        SdkPlugin plugin = new SdkPlugin();
        assertTrue(plugin.types().contains(SdkTypes.OCR_OPTIONS));
        assertTrue(plugin.componentTypes().containsAll(SdkTypes.OCR_CHAINS));
    }
}
