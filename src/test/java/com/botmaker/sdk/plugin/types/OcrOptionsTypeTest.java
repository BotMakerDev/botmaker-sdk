package com.botmaker.sdk.plugin.types;

import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.sdk.api.text.OcrOptions;
import com.botmaker.sdk.api.text.Text;
import com.botmaker.sdk.api.text.TextResult;
import com.botmaker.sdk.plugin.SdkPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code OcrOptions} as a declared type: its constructor gives every part back, a fresh one has no part the
 * host cannot write, and the chain a person writes by hand is one call with a link per wither.
 */
class OcrOptionsTypeTest {

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

    /**
     * {@code defaults()} and one link per wither: a value taken apart and built back is itself, however many links
     * it needs, and {@code defaults()} alone needs none.
     */
    @Test
    void theChainGivesEveryPartBack() {
        DeclaredCall<OcrOptions> chain = SdkTypes.OCR_CHAIN;
        assertEquals("defaults", chain.factory().getName());
        assertEquals(9, chain.withers().size());
        assertEquals(OcrOptions.defaults(), chain.build(chain.components(OcrOptions.defaults())));
        OcrOptions value = OcrOptions.defaults().withLanguages("eng+kor").withUpscale(3.0)
                .withLevel(TextResult.Level.LINE).withCharWhitelist("0123456789").withInvert(true);
        assertEquals(value, chain.build(chain.components(value)));
    }

    @Test
    void thePluginDeclaresTheTypeAndListsTheChain() {
        SdkPlugin plugin = new SdkPlugin();
        assertTrue(plugin.types().contains(SdkTypes.OCR_OPTIONS));
        assertTrue(plugin.componentTypes().contains(SdkTypes.OCR_CHAIN));
    }
}
