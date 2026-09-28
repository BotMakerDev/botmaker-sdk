package com.botmaker.sdk.plugin.pilot.ui;

import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi.FunnelIssue;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi.PilotMode;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi.PilotOutcome;
import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two pure pieces of the Remote Pilot bring-up: turning a {@code tailscale funnel} error into the step of
 * the setup wizard to highlight, and deriving the pairing URL from its parts.
 *
 * <p>Both were untested private statics on {@code UIManager}. The URL half in particular was a regex rewrite
 * ({@code replaceFirst("token=[^&]*$", …)}) that only worked while the token happened to be the last query
 * parameter — these assertions pin the parts-based construction that replaced it.
 */
class RemotePilotFunnelTest {

    @Test
    void classifiesTheOperatorGrantAheadOfEverythingElse() {
        assertEquals(FunnelIssue.NEEDS_OPERATOR,
                RemotePilotUi.classifyFunnel("Funnel: must be run as operator, or with sudo"));
        // Tailscale often names two blockers in one line. The operator grant is the one the user has to do
        // first — nothing else can be attempted without it — so it wins the classification.
        assertEquals(FunnelIssue.NEEDS_OPERATOR,
                RemotePilotUi.classifyFunnel("HTTPS cert unavailable; run tailscale set --operator first"));
    }

    @Test
    void classifiesTheHttpsCertificateBlocker() {
        assertEquals(FunnelIssue.NO_HTTPS_CERT,
                RemotePilotUi.classifyFunnel("HTTPS is not enabled in the admin panel"));
        assertEquals(FunnelIssue.NO_HTTPS_CERT,
                RemotePilotUi.classifyFunnel("could not get cert for host.tailnet.ts.net"));
    }

    @Test
    void classifiesTheAclGrantAndTheSignedOutCase() {
        assertEquals(FunnelIssue.NOT_ENABLED,
                RemotePilotUi.classifyFunnel("Funnel is not enabled for this tailnet"));
        assertEquals(FunnelIssue.LOGGED_OUT, RemotePilotUi.classifyFunnel("you are not logged in"));
    }

    @Test
    void unrecognisedAndAbsentErrorsFallBackToOther() {
        assertEquals(FunnelIssue.OTHER, RemotePilotUi.classifyFunnel("something we have never seen"));
        assertEquals(FunnelIssue.OTHER, RemotePilotUi.classifyFunnel(null));
    }

    @Test
    void buildsThePairingUrlFromTheBaseAndTheToken() {
        PilotOutcome direct = new PilotOutcome("http://100.64.0.7:8123", "abc", PilotMode.TAILNET_DIRECT, null, null);
        assertEquals("http://100.64.0.7:8123/?token=abc", direct.url());

        PilotOutcome funnel = new PilotOutcome("https://box.tail1234.ts.net", "xyz", PilotMode.FUNNEL_HTTPS, null,
                new RemotePilotUi.FunnelDiag(true, true, FunnelIssue.NONE));
        assertEquals("https://box.tail1234.ts.net/?token=xyz", funnel.url());
    }

    @Test
    void resettingTheTokenRebuildsTheUrlAndKeepsEverythingElse() {
        PilotOutcome original =
                new PilotOutcome("http://100.64.0.7:8123", "old-token", PilotMode.TAILNET_DIRECT, "boom", null);
        PilotOutcome refreshed = original.withToken("new-token");

        assertEquals("http://100.64.0.7:8123/?token=new-token", refreshed.url());
        assertEquals(original.baseUrl(), refreshed.baseUrl());
        assertEquals(original.mode(), refreshed.mode());
        assertEquals(original.funnelError(), refreshed.funnelError());
        assertNotEquals(original.token(), refreshed.token());
    }

    /** Ticked and highlighted as the blocker at once was the old answer when Tailscale named certificates. */
    @Test
    void the_https_step_is_not_ticked_when_it_is_the_blocker() {
        assertTrue(FunnelSetupWizard.httpsStepDone(new RemotePilotUi.FunnelDiag(true, true, FunnelIssue.NOT_ENABLED)));
        assertFalse(FunnelSetupWizard.httpsStepDone(
                new RemotePilotUi.FunnelDiag(true, true, FunnelIssue.NO_HTTPS_CERT)));
        assertFalse(FunnelSetupWizard.httpsStepDone(new RemotePilotUi.FunnelDiag(true, false, FunnelIssue.LOGGED_OUT)));
        assertFalse(FunnelSetupWizard.httpsStepDone(null));
    }

    /**
     * The host hands every press a fresh services object, so the pilot is kept by project directory — comparing
     * the objects released it (and dropped the paired phone) on every press.
     */
    @Test
    void a_second_press_in_the_same_project_keeps_the_pilot() {
        assertTrue(RemotePilotUi.sameProject(services("/p/a"), services("/p/a")));
        assertFalse(RemotePilotUi.sameProject(services("/p/a"), services("/p/b")));
        assertFalse(RemotePilotUi.sameProject(null, services("/p/a")));
    }

    private static StudioServices services(String resources) {
        return new StudioServices() {
            @Override public Path projectDir() { return Path.of(resources).getParent(); }
            @Override public Path resourcesDir() { return Path.of(resources); }
            @Override public Theme theme() { return null; }
            @Override public Dialogs dialogs() { return null; }
        };
    }

    @Test
    void aResetNeverLeavesTheOldTokenAnywhereInTheUrl() {
        // What the old regex could not promise: anchored at the end of the string, it rewrote only a token
        // that was the last query parameter and silently left any other occurrence behind.
        PilotOutcome refreshed =
                new PilotOutcome("http://100.64.0.7:8123", "old-token", PilotMode.ALL_INTERFACES, null, null)
                        .withToken("new-token");
        assertTrue(refreshed.url().endsWith("token=new-token"), refreshed.url());
        assertTrue(!refreshed.url().contains("old-token"), refreshed.url());
    }
}
