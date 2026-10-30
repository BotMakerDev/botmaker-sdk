package com.botmaker.sdk.plugin.launch;

import com.botmaker.shared.launch.LaunchSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionHandoffTest {

    @Test
    void eachPropertyIsReadOffTheHandoffsOwnArguments() {
        List<String> args = List.of("-Dbotmaker.session.display=:1", "-Dbotmaker.session.owner.backend=gamescope");
        assertEquals(":1", SessionHandoff.value(args, "botmaker.session.display"));
        assertEquals("gamescope", SessionHandoff.value(args, "botmaker.session.owner.backend"));
        assertNull(SessionHandoff.value(args, "botmaker.session.window"), "no window known: the offer clears it");
        assertNull(SessionHandoff.value(List.of(), "botmaker.session.display"), "no session: every one cleared");
    }

    @Test
    void aDisplayBroughtUpForAnotherTargetIsNotTheOneAskedFor() {
        LaunchSpec firestone = LaunchSpec.parse("steam:1013320");
        assertFalse(SessionHandoff.differs("steam:1013320", firestone), "the same target: joined");
        assertTrue(SessionHandoff.differs("emu-app:com.zjcs.android.us@Waydroid", firestone),
                "the target changed since: replaced");
        assertTrue(SessionHandoff.differs(null, firestone), "no target recorded: nothing says it is this game");
    }
}
