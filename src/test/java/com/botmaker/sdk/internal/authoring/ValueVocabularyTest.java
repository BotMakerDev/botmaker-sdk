package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.basics.values.BasicsValueTypes;
import com.botmaker.plugin.toolkit.config.ValueGrammar;
import com.botmaker.sdk.authoring.Authoring;
import com.botmaker.sdk.authoring.SdkVersion;
import com.botmaker.sdk.internal.config.SdkGrammar;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a project's vocabulary is now that it comes from two plugins: plugin-basics' nine JDK types merged
 * with the SDK's own eight.
 *
 * <p>The merge is what a <em>host</em> does — it loads every plugin and folds their catalogs together — and
 * this test does the same thing with the two that are in this reactor. It is the phase-2 check the plan
 * names: seventeen types, no clash of either kind, and {@code forJava(Duration.class)} answering
 * plugin-basics' registration rather than a leftover of the SDK's.
 *
 * <p>The last test is the one worth having independently. The catalog is what the <b>editor</b> knows about
 * a type and the grammar is what a <b>running bot</b> knows about it, and they are written in modules that
 * do not share a class — so nothing but a test can say that a type the editor lets you declare is a type the
 * bot can actually read back.
 */
class ValueVocabularyTest {

    /** plugin-basics first, which is what {@code Authoring}'s own mapper does and what a menu shows. */
    private static final ValueCatalog MERGED = BasicsValueTypes.CATALOG.merge(SdkValueTypes.CATALOG);

    @Test
    void theTwoPluginsTogetherOfferSeventeenTypes() {
        assertEquals(9, BasicsValueTypes.CATALOG.types().size());
        assertEquals(8, SdkValueTypes.CATALOG.types().size());
        assertEquals(17, MERGED.types().size(), MERGED.types().toString());
    }

    /** Neither kind of clash: no id claimed twice, and no Java type claimed by two ids. */
    @Test
    void neitherPluginClaimsAnythingTheOtherDoes() {
        assertEquals(List.of(), BasicsValueTypes.CATALOG.clashesWith(SdkValueTypes.CATALOG));
        assertEquals(List.of(), BasicsValueTypes.CATALOG.javaClashesWith(SdkValueTypes.CATALOG));

        List<String> names = MERGED.types().stream().map(ValueType::javaName).toList();
        assertEquals(names.size(), new LinkedHashSet<>(names).size(), names.toString());
        Set<String> ids = new LinkedHashSet<>(MERGED.types().stream().map(ValueType::id).toList());
        assertEquals(17, ids.size(), ids.toString());
    }

    /** The nine answer as plugin #2's, which is the whole of the move being real rather than cosmetic. */
    @Test
    void theJdkTypesAreThePluginBasicsRegistrations() {
        assertEquals(BasicsValueTypes.DURATION, MERGED.forJava(Duration.class).orElseThrow());
        assertEquals(BasicsValueTypes.TEXT, MERGED.forJava(String.class).orElseThrow());
        assertEquals(BasicsValueTypes.COLOR, MERGED.forJava(Color.class).orElseThrow());
        assertEquals(BasicsValueTypes.DATE, MERGED.forJava(LocalDate.class).orElseThrow());
        assertEquals(BasicsValueTypes.TIME_OF_DAY, MERGED.forJava(LocalTime.class).orElseThrow());
    }

    /** The ids a stored project holds are unchanged by the move, which is what makes it invisible. */
    @Test
    void everyStoredIdStillResolves() {
        for (String id : List.of("TEXT", "YES_NO", "WHOLE_NUMBER", "DECIMAL_NUMBER", "CHARACTER", "COLOR",
                "DATE", "TIME_OF_DAY", "DURATION", "IMAGE_TEMPLATE", "PRECISION", "POINT", "RECT", "SIZE",
                "DIRECTION", "KEY", "MOUSE_BUTTON")) {
            assertTrue(MERGED.knows(id), id + " is an id projects already hold");
        }
    }

    /**
     * The SDK offers only its own eight, exactly as any plugin does — the split is not a private
     * arrangement the SDK gets to opt out of.
     */
    @Test
    void theSdkContributesItsEightAndNoMore() {
        assertEquals(SdkValueTypes.CATALOG.types(), Authoring.valueTypes(SdkVersion.latest()).types());
    }

    /** A type the editor offers is a type the bot can read: the two halves are written in two modules. */
    @Test
    void theBotsGrammarAndTheEditorsCatalogDescribeTheSameTypes() {
        List<String> unreadable = new ArrayList<>();
        for (ValueGrammar.Reader<?> reader : new SdkGrammar().readers()) {
            if (MERGED.forJava(reader.type()).isEmpty()) unreadable.add(reader.type().getName());
        }
        assertEquals(List.of(), unreadable, "the grammar reads a type the catalog cannot declare");
        assertTrue(MERGED.types().size() >= new SdkGrammar().readers().size());
    }
}
