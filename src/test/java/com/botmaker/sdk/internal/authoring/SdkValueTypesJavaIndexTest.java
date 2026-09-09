package com.botmaker.sdk.internal.authoring;

import com.botmaker.plugin.api.value.ValueCatalog;
import com.botmaker.plugin.api.value.ValueType;
import com.botmaker.plugin.toolkit.config.ValueGrammar;
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
 * The SDK's vocabulary, asked for by Java class rather than by id.
 *
 * <p>This is the consumer half of {@code ValueCatalog.forJava}: a bot author writes
 * {@code Settings.load("wait", Duration.class)} and an editor author writes {@code Duration.class} too, and
 * neither of them ever spells {@code DURATION}. The id stays what the project file holds and stops being
 * something anybody outside this class has to know.
 *
 * <p>The last test is the one worth having. The catalog is what the <b>editor</b> knows about a type and the
 * grammar is what a <b>running bot</b> knows about it, and they are written in two modules that cannot see
 * each other — so nothing but a test can say that a type the editor lets you declare is a type the bot can
 * actually read back.
 */
class SdkValueTypesJavaIndexTest {

    private static final ValueCatalog CATALOG = SdkValueTypes.CATALOG;

    @Test
    void everySdkTypeIsFoundByItsJavaClass() {
        assertEquals(SdkValueTypes.TEXT, CATALOG.forJava(String.class).orElseThrow());
        assertEquals(SdkValueTypes.YES_NO, CATALOG.forJava(boolean.class).orElseThrow());
        assertEquals(SdkValueTypes.WHOLE_NUMBER, CATALOG.forJava(int.class).orElseThrow());
        assertEquals(SdkValueTypes.DECIMAL_NUMBER, CATALOG.forJava(double.class).orElseThrow());
        assertEquals(SdkValueTypes.CHARACTER, CATALOG.forJava(char.class).orElseThrow());
        assertEquals(SdkValueTypes.COLOR, CATALOG.forJava(Color.class).orElseThrow());
        assertEquals(SdkValueTypes.DATE, CATALOG.forJava(LocalDate.class).orElseThrow());
        assertEquals(SdkValueTypes.TIME_OF_DAY, CATALOG.forJava(LocalTime.class).orElseThrow());
        assertEquals(SdkValueTypes.DURATION, CATALOG.forJava(Duration.class).orElseThrow());
    }

    /** A boxed ask is the same type: it is how a list of them is written. */
    @Test
    void aWrapperFindsThePrimitiveType() {
        assertEquals(SdkValueTypes.YES_NO, CATALOG.forJava(Boolean.class).orElseThrow());
        assertEquals(SdkValueTypes.WHOLE_NUMBER, CATALOG.forJava(Integer.class).orElseThrow());
        assertEquals(SdkValueTypes.DECIMAL_NUMBER, CATALOG.forJava(Double.class).orElseThrow());
        assertEquals(SdkValueTypes.CHARACTER, CATALOG.forJava(Character.class).orElseThrow());
    }

    /**
     * Seventeen types, seventeen Java types. It was already true and nothing made it true; the builder
     * refuses a second claimant now, so this test is what says the SDK never asks it to.
     */
    @Test
    void noTwoSdkTypesClaimOneJavaType() {
        List<String> names = CATALOG.types().stream().map(ValueType::javaName).toList();
        Set<String> distinct = new LinkedHashSet<>(names);
        assertEquals(names.size(), distinct.size(), names.toString());
    }

    /** A type the editor offers is a type the bot can read: the two halves are written in two modules. */
    @Test
    void theBotsGrammarAndTheEditorsCatalogDescribeTheSameTypes() {
        List<String> unreadable = new ArrayList<>();
        for (ValueGrammar.Reader<?> reader : new SdkGrammar().readers()) {
            if (CATALOG.forJava(reader.type()).isEmpty()) unreadable.add(reader.type().getName());
        }
        assertEquals(List.of(), unreadable, "the grammar reads a type the catalog cannot declare");
        assertTrue(CATALOG.types().size() >= new SdkGrammar().readers().size());
    }
}
