package com.botmaker.sdk.api.launch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link LaunchTarget}'s pure spec parsing and round-trip — the {@code launch.target} string
 * Studio bakes into a project, exercised without launching anything.
 */
class LaunchTargetTest {

    @Test
    void parsesSteam() {
        LaunchTarget t = LaunchTarget.parse("steam:570");
        assertInstanceOf(LaunchTarget.Steam.class, t);
        assertEquals("570", ((LaunchTarget.Steam) t).appId());
        assertEquals("steam:570", t.spec());
    }

    @Test
    void parsesEpic() {
        LaunchTarget t = LaunchTarget.parse("epic:Fortnite");
        assertInstanceOf(LaunchTarget.Epic.class, t);
        assertEquals("Fortnite", ((LaunchTarget.Epic) t).appName());
        assertEquals("epic:Fortnite", t.spec());
    }

    @Test
    void parsesHeroic() {
        LaunchTarget t = LaunchTarget.parse("heroic:Firestone");
        assertInstanceOf(LaunchTarget.Heroic.class, t);
        assertEquals("Firestone", ((LaunchTarget.Heroic) t).appName());
        assertEquals("heroic:Firestone", t.spec());
    }

    @Test
    void parsesCliKeepingArgsAndColons() {
        // Only the first colon separates the kind, so the command's own colons/args survive verbatim.
        LaunchTarget t = LaunchTarget.parse("cli:heroic --no-gui launch Firestone");
        assertInstanceOf(LaunchTarget.Cli.class, t);
        assertEquals("heroic --no-gui launch Firestone", ((LaunchTarget.Cli) t).commandLine());
        assertEquals("cli:heroic --no-gui launch Firestone", t.spec());
    }

    @Test
    void parsesExeKeepingWindowsDriveColon() {
        // Only the first colon separates the kind, so a Windows path's drive colon survives.
        LaunchTarget t = LaunchTarget.parse("exe:C:\\Games\\game.exe");
        assertInstanceOf(LaunchTarget.Exe.class, t);
        assertEquals("C:\\Games\\game.exe", ((LaunchTarget.Exe) t).path());
        assertEquals("exe:C:\\Games\\game.exe", t.spec());
    }

    @Test
    void parsesEmulatorAppSplittingOnLastAt() {
        // The package (with its dots) is kept whole; the split is on the last '@' so the instance is the tail.
        LaunchTarget t = LaunchTarget.parse("emu-app:com.some.game@Rvc64");
        assertInstanceOf(LaunchTarget.EmulatorApp.class, t);
        LaunchTarget.EmulatorApp app = (LaunchTarget.EmulatorApp) t;
        assertEquals("com.some.game", app.packageName());
        assertEquals("Rvc64", app.instance());
        assertEquals("emu-app:com.some.game@Rvc64", t.spec());
    }

    @Test
    void kindIsCaseInsensitive() {
        assertInstanceOf(LaunchTarget.Steam.class, LaunchTarget.parse("STEAM:570"));
    }

    @Test
    void nullAndBlankAndUnknownYieldNull() {
        assertNull(LaunchTarget.parse(null));
        assertNull(LaunchTarget.parse(""));
        assertNull(LaunchTarget.parse("   "));
        assertNull(LaunchTarget.parse("nonsense"));
        assertNull(LaunchTarget.parse("bogus:whatever"));
    }

    @Test
    void malformedTargetsYieldNull() {
        assertNull(LaunchTarget.parse("steam:"), "a kind with no value is not a target");
        assertNull(LaunchTarget.parse("emu-app:com.some.game"), "emu-app needs @instance");
        assertNull(LaunchTarget.parse("emu-app:@Rvc64"), "emu-app needs a package");
        assertNull(LaunchTarget.parse("emu-app:com.some.game@"), "emu-app needs an instance name");
    }
}
