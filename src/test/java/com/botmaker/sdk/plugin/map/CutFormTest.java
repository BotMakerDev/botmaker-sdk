package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.plugin.map.CutForm.Intent;
import com.botmaker.sdk.plugin.map.CutForm.Kind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The cut form's kinds, split by what a cut is for. */
class CutFormTest {

    @Test
    void eachIntentOffersItsKinds() {
        assertEquals(List.of(Kind.MARKER, Kind.MORE_MARKER, Kind.ABSENT, Kind.TEXT_MARKER), Intent.TELLS.kinds());
        assertEquals(List.of(Kind.BUTTON, Kind.BACK, Kind.STRIP, Kind.TREE, Kind.TEXT_BUTTON, Kind.SPOT),
                Intent.ACTS.kinds());
        assertEquals(List.of(Kind.LOOK, Kind.SCREEN_LOOK, Kind.ZONE, Kind.DIGITS, Kind.RECUT), Intent.READS.kinds());
    }

    @Test
    void everyKindIsInOneIntentInTheFormsOrder() {
        assertEquals(Arrays.asList(Kind.values()), Arrays.stream(Intent.values())
                .flatMap(i -> i.kinds().stream()).toList());
    }
}
