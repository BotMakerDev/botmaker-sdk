package com.botmaker.sdk.internal.config;

import com.botmaker.sdk.api.bot.LaunchChoice;
import com.botmaker.sdk.api.bot.Launcher;
import com.botmaker.sdk.api.bot.SdkValue;
import com.botmaker.sdk.api.capture.CaptureSource;
import com.botmaker.sdk.api.capture.Source;
import com.botmaker.shared.platform.Os;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The game half of a bot's {@code Sdk} parameters, read by their marks, and the launcher a system picks. */
class InstalledGameTest {

    /** A bot's {@code Sdk}: the methods are found by mark, whatever they are called. */
    public static final class Sdk {
        @SdkValue(SdkValue.Id.LAUNCH_TARGETS)
        public static List<Launcher> launchers() {
            return List.of(Launcher.of("faugus:abc"), Launcher.of(" steam:570 "), Launcher.of(""));
        }

        @SdkValue(SdkValue.Id.VM)
        public static String gameVm() {
            return " win11 ";
        }

        @SdkValue(SdkValue.Id.CAPTURE)
        public static CaptureSource game() {
            return CaptureSource.window("firestone.exe", "");
        }

        @SdkValue(SdkValue.Id.DO_SETUP)
        public static boolean setUpFirst() {
            return true;
        }
    }

    @Test
    void theRunsOwnParametersAreReadByTheirMarksFromTheFirstClassMarkingThem() {
        assertEquals(Boolean.TRUE, InstalledGame.marked(SdkValue.Id.DO_SETUP, null, Object.class, Sdk.class));
        assertNull(InstalledGame.marked(SdkValue.Id.PLAN, Sdk.class), "a bot with no plan method has none");
        assertNull(InstalledGame.marked(SdkValue.Id.PLAN, (Class<?>[]) null));
    }

    @Test
    void aHoldersParametersAreReadFromTheGroupBesideIt() {
        Class<?> holder = com.botmaker.sdk.internal.config.fixture.plugins.g.a.Sdk.class;
        assertEquals("com.botmaker.sdk.internal.config.fixture.params.g.a", ParamGroups.paramsPackage(holder.getName()));
        assertEquals(Boolean.FALSE, InstalledGame.marked(SdkValue.Id.DO_SETUP, holder),
                "main hands the holder; the value is the group's method");
        InstalledGame.install(holder);
        assertEquals(List.of("faugus:abc", "steam:570"), InstalledGame.launchTargets(), "the author's, in the holder");
        assertEquals("steam:570", InstalledGame.launchChoice(), "the runner's, in the group");
        assertEquals(2, ParamGroups.groups(holder).size(), "every @Param group of the plugin's package, Game and Running");
        assertEquals(2, ParamGroups.groups(holder, holder).size(), "each group made once");
        assertTrue(ParamGroups.groups(Sdk.class).isEmpty(), "a class outside plugins/ has no group");
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
        assertEquals("Window[firestone.exe]", Source.current().toString(), "the bot reads the window picked");

        InstalledGame.install(String.class);
        assertTrue(InstalledGame.launchTargets().isEmpty(), "a bot with no such field launches nothing");
        assertEquals("", InstalledGame.vm());
        assertEquals(CaptureSource.desktop().toString(), Source.current().toString(),
                "a bot with no capture source reads the whole desktop again");
    }

    @Test
    void eachSystemTakesTheFirstLauncherItCanStart() {
        List<String> launchers = List.of("lutris:3", "steam:570");
        assertEquals("lutris:3", InstalledGame.firstRunnable(launchers, Os.LINUX));
        assertEquals("steam:570", InstalledGame.firstRunnable(launchers, Os.WINDOWS), "Lutris is Linux's");
        assertEquals("steam:570", InstalledGame.firstRunnable(List.of("faugus:abc", "steam:570"), Os.LINUX),
                "Faugus retired for Lutris (2026-10-29): passed over");
        assertNull(InstalledGame.firstRunnable(List.of("lutris:3"), Os.MAC));
        assertEquals("steam:570", InstalledGame.firstRunnable(List.of("nonsense", "steam:570"), Os.LINUX),
                "a spec that does not parse is passed over");
    }

    @Test
    void theRunnersPickWinsWhenTheBotListsItAndTheSystemStartsIt() {
        List<String> launchers = List.of("faugus:abc", "steam:570", "lutris:3");
        assertEquals("lutris:3", InstalledGame.pick(launchers, " lutris: 3", Os.LINUX));
        assertEquals("steam:570", InstalledGame.pick(launchers, "", Os.LINUX), "no pick: the first available");
        assertEquals("steam:570", InstalledGame.pick(launchers, "epic:x", Os.LINUX),
                "a pick the author no longer lists is none");
        assertEquals("steam:570", InstalledGame.pick(launchers, "faugus:abc", Os.LINUX),
                "a pick this system no longer starts is none");
        assertEquals("steam:570", InstalledGame.pick(launchers, "lutris:3", Os.WINDOWS),
                "a pick this system cannot start is none");
    }

    @Test
    void theRunnersPickIsReadByItsMark() {
        InstalledGame.install(Picked.class);
        assertEquals("steam:570", InstalledGame.launchChoice());
        InstalledGame.install(Sdk.class);
        assertEquals("", InstalledGame.launchChoice(), "a bot with no pick has none");
    }

    /** A bot whose runner picked a launcher. */
    public static final class Picked {
        @SdkValue(SdkValue.Id.LAUNCH_TARGETS)
        public static List<Launcher> launchers() {
            return List.of(Launcher.of("faugus:abc"), Launcher.of("steam:570"));
        }

        @SdkValue(SdkValue.Id.LAUNCH_TARGET)
        public static LaunchChoice launcher() {
            return LaunchChoice.of(" steam:570 ");
        }
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
