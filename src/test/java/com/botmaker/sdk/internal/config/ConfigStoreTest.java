package com.botmaker.sdk.internal.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loader that used to be a text block in every generated {@code Activities} class.
 *
 * <p>What matters is that it never refuses to answer: the promise the whole configuration path rests on is
 * that a bot starts even when its own file is missing or wrong.
 */
class ConfigStoreTest {

    private static Map<String, List<String>> sample() {
        return ConfigStore.load("/fixtures/activities-sample.json");
    }

    @Test
    void anActivitysTickReadsAsAOneEntryList() {
        assertEquals(List.of("true"), sample().get("MINING"));
        assertEquals(List.of("false"), sample().get("SMELTING"));
    }

    @Test
    void anActivityWithNoTickAtAllIsOff() {
        assertEquals(List.of("false"), sample().get("FISHING"),
                "an older file predating the field must not switch an activity on");
    }

    @Test
    void aVariableKeepsEveryItemInOrder() {
        assertEquals(List.of("a", "escape", "not-a-key"), sample().get("HOTKEYS"));
    }

    @Test
    void aSingleValuedVariableIsStillAList() {
        assertEquals(List.of("1h30m"), sample().get("REST"));
    }

    @Test
    void aMissingFileIsNotAnError() {
        assertTrue(ConfigStore.load("/fixtures/there-is-no-such-file.json").isEmpty(),
                "a bot never fails to start because of its own configuration file");
    }

    @Test
    void unreadableJsonIsNotAnError() {
        assertTrue(ConfigStore.load("/fixtures/activities-broken.json").isEmpty());
    }

    @Test
    void aKeyNobodyStoredReadsAsNothing() {
        // The live accessors, over whatever the running test classpath holds — which is no file at all.
        assertEquals("", ConfigStore.one("NOTHING-STORED-THIS"));
        assertEquals(List.of(), ConfigStore.all("NOTHING-STORED-THIS"));
    }
}
