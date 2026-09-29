package com.botmaker.sdk.plugin.pilot.ui;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.sdk.plugin.pilot.transport.FunnelTransport;
import com.botmaker.sdk.plugin.pilot.transport.FunnelTransport.Diag;
import com.botmaker.sdk.plugin.pilot.transport.FunnelTransport.Issue;
import com.botmaker.sdk.plugin.pilot.transport.TransportKind;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi.PilotOutcome;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure pieces of the Remote Pilot bring-up: turning a {@code tailscale funnel} error into the step of the
 * setup checklist to highlight, deriving the pairing URL from its parts, and which transports are tried.
 *
 * <p>The classification and the URL were untested private statics on {@code UIManager}. The URL half in
 * particular was a regex rewrite ({@code replaceFirst("token=[^&]*$", …)}) that only worked while the token
 * happened to be the last query parameter — these assertions pin the parts-based construction that replaced it.
 */
class RemotePilotFunnelTest {

    @Test
    void classifiesTheOperatorGrantAheadOfEverythingElse() {
        assertEquals(Issue.NEEDS_OPERATOR, FunnelTransport.classify("Funnel: must be run as operator, or with sudo"));
        // Tailscale often names two blockers in one line. The operator grant is the one the user has to do
        // first — nothing else can be attempted without it — so it wins the classification.
        assertEquals(Issue.NEEDS_OPERATOR,
                FunnelTransport.classify("HTTPS cert unavailable; run tailscale set --operator first"));
    }

    @Test
    void classifiesTheHttpsCertificateBlocker() {
        assertEquals(Issue.NO_HTTPS_CERT, FunnelTransport.classify("HTTPS is not enabled in the admin panel"));
        assertEquals(Issue.NO_HTTPS_CERT, FunnelTransport.classify("could not get cert for host.tailnet.ts.net"));
    }

    @Test
    void classifiesTheAclGrantAndTheSignedOutCase() {
        assertEquals(Issue.NOT_ENABLED, FunnelTransport.classify("Funnel is not enabled for this tailnet"));
        assertEquals(Issue.LOGGED_OUT, FunnelTransport.classify("you are not logged in"));
    }

    @Test
    void unrecognisedAndAbsentErrorsFallBackToOther() {
        assertEquals(Issue.OTHER, FunnelTransport.classify("something we have never seen"));
        assertEquals(Issue.OTHER, FunnelTransport.classify(null));
    }

    @Test
    void buildsThePairingUrlFromTheBaseAndTheToken() {
        assertEquals("http://100.64.0.7:8123/?token=abc",
                outcome("http://100.64.0.7:8123", "abc", TransportKind.TAILNET).url());
        assertEquals("https://box.tail1234.ts.net/?token=xyz",
                outcome("https://box.tail1234.ts.net", "xyz", TransportKind.FUNNEL).url());
        assertEquals("https://calm-river-fox.trycloudflare.com/?token=t",
                outcome("https://calm-river-fox.trycloudflare.com", "t", TransportKind.QUICK_TUNNEL).url());
    }

    @Test
    void resettingTheTokenRebuildsTheUrlAndKeepsEverythingElse() {
        PilotOutcome original = new PilotOutcome("http://100.64.0.7:8123", "old-token", TransportKind.TAILNET,
                TransportKind.FUNNEL, "boom", null, null, Map.of());
        PilotOutcome refreshed = original.withToken("new-token");

        assertEquals("http://100.64.0.7:8123/?token=new-token", refreshed.url());
        assertEquals(original.baseUrl(), refreshed.baseUrl());
        assertEquals(original.kind(), refreshed.kind());
        assertEquals(original.asked(), refreshed.asked());
        assertEquals(original.error(), refreshed.error());
        assertNotEquals(original.token(), refreshed.token());
        assertTrue(refreshed.fellBack());
    }

    /** A public transport is only ever the user's pick: when one fails, the pilot falls back to a direct one. */
    @Test
    void a_failed_transport_falls_back_to_the_direct_ones_and_never_to_a_public_one() {
        assertEquals(List.of(TransportKind.QUICK_TUNNEL, TransportKind.TAILNET, TransportKind.LAN),
                RemotePilotUi.attempts(TransportKind.QUICK_TUNNEL));
        assertEquals(List.of(TransportKind.FUNNEL, TransportKind.TAILNET, TransportKind.LAN),
                RemotePilotUi.attempts(TransportKind.FUNNEL));
        assertEquals(List.of(TransportKind.TAILNET, TransportKind.LAN), RemotePilotUi.attempts(TransportKind.TAILNET));
        assertEquals(List.of(TransportKind.LAN, TransportKind.TAILNET), RemotePilotUi.attempts(TransportKind.LAN));
        assertEquals(List.of(TransportKind.TAILNET, TransportKind.LAN), RemotePilotUi.attempts(TransportKind.UNKNOWN));
    }

    @Test
    void the_dialog_warns_on_every_transport_but_the_tailnet() {
        assertNull(RemotePilotDialog.warning(TransportKind.TAILNET));
        assertNotNull(RemotePilotDialog.warning(TransportKind.LAN));
        assertTrue(RemotePilotDialog.warning(TransportKind.FUNNEL).contains("public internet"));
        assertTrue(RemotePilotDialog.warning(TransportKind.QUICK_TUNNEL).contains("public internet"));
    }

    /** Ticked and highlighted as the blocker at once was the old answer when Tailscale named certificates. */
    @Test
    void the_https_step_is_not_ticked_when_it_is_the_blocker() {
        assertTrue(FunnelSetupWizard.httpsStepDone(new Diag(true, true, Issue.NOT_ENABLED)));
        assertFalse(FunnelSetupWizard.httpsStepDone(new Diag(true, true, Issue.NO_HTTPS_CERT)));
        assertFalse(FunnelSetupWizard.httpsStepDone(new Diag(true, false, Issue.LOGGED_OUT)));
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
        PilotOutcome refreshed = outcome("http://192.168.1.20:8123", "old-token", TransportKind.LAN)
                .withToken("new-token");
        assertTrue(refreshed.url().endsWith("token=new-token"), refreshed.url());
        assertTrue(!refreshed.url().contains("old-token"), refreshed.url());
    }

    private static PilotOutcome outcome(String base, String token, TransportKind kind) {
        return new PilotOutcome(base, token, kind, kind, null, null, null, Map.of());
    }
}
