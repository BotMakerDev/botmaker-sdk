package com.botmaker.sdk.authoring;

import com.botmaker.plugin.api.value.ValueForm;
import com.botmaker.plugin.basics.values.BasicsValueTypes;
import com.botmaker.sdk.internal.authoring.SdkValueTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the SDK guarantees about the version pin an editor hands it, and about how a value spells its own
 * type in Java.
 *
 * <p><b>It was about {@code activities.json} until 2026-09-21</b>: that the model round-tripped, that the
 * schema stamp survived a save, that both spellings the file had over its life still loaded, and that every
 * parse was total. There is no such file and no such model — a flow is a {@code Flow} value in the bot's own
 * Java and a parameter is a {@code @Param} field — so those rules are not weakened, they no longer have a
 * subject. What is left here is what {@link Authoring} still answers.
 */
class AuthoringModelTest {

    /** "One of yes and no" is a boolean, said twice and worse — the type says so about itself. */
    @Test
    void aClosedSetCannotCarryAnAuthorWrittenSubset() {
        assertFalse(BasicsValueTypes.YES_NO.shapeable());
        assertTrue(BasicsValueTypes.TEXT.shapeable());
    }

    @Test
    void aVersionThisBuildDoesNotKnowIsRefusedInTheUsersWords() {
        AuthoringUnsupported refusal =
                assertThrows(AuthoringUnsupported.class, () -> Authoring.require("9.9.9"));
        assertTrue(refusal.getMessage().contains("9.9.9"), "the refusal must name the pin");
        assertTrue(refusal.getMessage().contains(SdkVersion.latest().id()),
                "and the newest version this build does know, so the user can act on it");
    }

    /**
     * A snapshot pin is this very jar. Sending it through the unknown-version path would refuse creation in
     * every development build — a refusal about a version that is, by construction, the one refusing.
     */
    @Test
    void aSnapshotPinResolvesToThisBuild() throws AuthoringUnsupported {
        assertEquals(SdkVersion.latest(), Authoring.require("0.0.0-SNAPSHOT"));
        assertEquals(SdkVersion.latest(), Authoring.require(""));
        assertTrue(SdkVersion.of("0.0.0-SNAPSHOT").isEmpty(), "but of() must still say it does not know it");
    }

    @Test
    void aTagIsToleratedOnTheWayInButTheWireFormCarriesNoV() {
        assertEquals(SdkVersion.V1_1_0, SdkVersion.of("v1.1.0").orElseThrow());
        assertEquals("1.1.0", SdkVersion.V1_1_0.id());
        assertFalse(SdkVersion.latest().id().startsWith("v"));
        assertTrue(SdkVersion.latest().atLeast(SdkVersion.V1_1_0));
    }

    /** The emitter's spellings — qualified where a fixed import block could otherwise forget them. */
    @Test
    void theSourceSpellingsAreTheOnesTheGeneratorWrites() {
        assertEquals("java.time.Duration", ValueForm.of(BasicsValueTypes.DURATION).sourceName());
        assertEquals("java.util.List<Key>",
                ValueForm.listOf(ValueForm.of(SdkValueTypes.KEY)).sourceName());
        assertEquals("int", ValueForm.of(BasicsValueTypes.WHOLE_NUMBER).sourceName());
        assertEquals("java.util.List<Integer>",
                ValueForm.listOf(ValueForm.of(BasicsValueTypes.WHOLE_NUMBER)).sourceName());
    }
}
