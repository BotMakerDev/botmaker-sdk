package com.botmaker.sdk.internal.emulator;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An app runs while Android's task stack holds one of its activities, a dialog of another app over it or not. */
class EmulatorRunsTest {

    @Test
    void anAppRunsWhileTheStackHoldsItsActivity() {
        String covered = """
                  topResumedActivity=ActivityRecord{1 u0 com.google.android.permissioncontroller/.GrantActivity t9}
                    * Hist #1: ActivityRecord{2 u0 com.zjcs.android.us/com.unity3d.player.UnityPlayerActivity t8}
                """;
        String stopped = """
                  topResumedActivity=ActivityRecord{82d4948 u0 com.android.launcher3/.uioverrides.QuickstepLauncher t129}
                """;
        assertTrue(Emulator.hasActivity(covered, "com.zjcs.android.us"), "a permission dialog over it");
        assertFalse(Emulator.hasActivity(stopped, "com.zjcs.android.us"), "force-stopped: only the launcher");
        assertFalse(Emulator.hasActivity(covered, "com.zjcs.android"), "a package's prefix is not the package");
    }

    /** What a MEmu capture takes once more: a frame whose sampled pixels are all black. */
    @Test
    void aFrameIsBlackWhenEverySampledPixelIs() {
        BufferedImage frame = new BufferedImage(900, 1600, BufferedImage.TYPE_INT_RGB);
        assertTrue(Emulator.black(frame));
        frame.setRGB(0, 0, 0x202020);
        assertFalse(Emulator.black(frame));
        assertFalse(Emulator.black(null));
    }
}
