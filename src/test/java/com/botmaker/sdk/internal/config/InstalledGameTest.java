package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.shared.platform.Os;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The game half of a bot's {@code Sdk} parameters, read by their marks, and the launcher a system picks. */
class InstalledGameTest {

    /** A bot's {@code Sdk}: the fields are found by mark, whatever they are called. */
    public static final class Sdk {
        @SdkValue(SdkValue.Id.LAUNCH_TARGETS)
        public static List<String> launchers = List.of("faugus:abc", " steam:570 ", "");

        @SdkValue(SdkValue.Id.VM)
        public static String gameVm = " win11 ";
    }

    @AfterEach
    void forget() {
        InstalledGame.install();
    }

    @Test
    void theParametersAreReadByTheirMarks() {
        InstalledGame.install(Sdk.class);
        assertEquals(List.of("faugus:abc", "steam:570"), InstalledGame.launchTargets());
        assertEquals("win11", InstalledGame.vm());

        InstalledGame.install(String.class);
        assertTrue(InstalledGame.launchTargets().isEmpty(), "a bot with no such field launches nothing");
        assertEquals("", InstalledGame.vm());
    }

    @Test
    void eachSystemTakesTheFirstLauncherItCanStart() {
        List<String> launchers = List.of("faugus:abc", "steam:570");
        assertEquals("faugus:abc", InstalledGame.firstRunnable(launchers, Os.LINUX));
        assertEquals("steam:570", InstalledGame.firstRunnable(launchers, Os.WINDOWS), "Faugus is Linux's");
        assertNull(InstalledGame.firstRunnable(List.of("lutris:3"), Os.MAC));
        assertEquals("steam:570", InstalledGame.firstRunnable(List.of("nonsense", "steam:570"), Os.LINUX),
                "a spec that does not parse is passed over");
    }

    @Test
    void aLaunchTargetGivenByHandWinsOverTheBots() {
        InstalledGame.install(Sdk.class);
        String was = System.getProperty(InstalledGame.LAUNCH_TARGET);
        try {
            System.clearProperty(InstalledGame.LAUNCH_TARGET);
            assertEquals(InstalledGame.firstRunnable(InstalledGame.launchTargets(), Os.current()),
                    InstalledGame.launchTarget());
            System.setProperty(InstalledGame.LAUNCH_TARGET, " steam:440 ");
            assertEquals("steam:440", InstalledGame.launchTarget());
        } finally {
            if (was == null) System.clearProperty(InstalledGame.LAUNCH_TARGET);
            else System.setProperty(InstalledGame.LAUNCH_TARGET, was);
        }
    }
}
