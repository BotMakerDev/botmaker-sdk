package com.botmaker.sdk.plugin.launch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionHandoffTest {

    @Test
    void eachPropertyIsReadOffTheHandoffsOwnArguments() {
        List<String> args = List.of("-Dbotmaker.session.display=:1", "-Dbotmaker.session.owner.backend=gamescope");
        assertEquals(":1", SessionHandoff.value(args, "botmaker.session.display"));
        assertEquals("gamescope", SessionHandoff.value(args, "botmaker.session.owner.backend"));
        assertNull(SessionHandoff.value(args, "botmaker.session.window"), "no window known: the offer clears it");
        assertNull(SessionHandoff.value(List.of(), "botmaker.session.display"), "no session: every one cleared");
    }
}
