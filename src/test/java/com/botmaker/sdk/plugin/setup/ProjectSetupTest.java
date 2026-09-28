package com.botmaker.sdk.plugin.setup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What Project Setup's launch row says for this computer's {@code botmaker.launch.target}. */
class ProjectSetupTest {

    /** No target is a fine place to be: the game has its own launcher, so the row explains rather than warns. */
    @Test
    void no_launch_target_is_neither_set_nor_a_problem() {
        ProjectSetup.LaunchRow row = ProjectSetup.launchRow(null);

        assertFalse(row.set());
        assertFalse(row.unreadable());
        assertEquals(ProjectSetup.NO_LAUNCH_TARGET, row.detail());
        assertEquals(row, ProjectSetup.launchRow("  "));
    }

    @Test
    void a_readable_target_is_set_and_described() {
        ProjectSetup.LaunchRow row = ProjectSetup.launchRow("emu-app:com.game@Pie64");

        assertTrue(row.set());
        assertFalse(row.unreadable());
    }

    /** Both ways a spec fails to read — no kind, or a kind nobody knows — are refused, never ticked. */
    @Test
    void an_unreadable_target_is_not_ticked() {
        for (String spec : new String[] {"Portal 2", "banana:42"}) {
            ProjectSetup.LaunchRow row = ProjectSetup.launchRow(spec);
            assertFalse(row.set(), spec);
            assertTrue(row.unreadable(), spec);
            assertTrue(row.detail().contains(spec), row.detail());
        }
    }
}
