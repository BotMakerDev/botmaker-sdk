package com.botmaker.sdk.plugin.pilot.ui;

import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.pilot.transport.FunnelTransport.Diag;
import com.botmaker.sdk.plugin.pilot.transport.FunnelTransport.Issue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The one-time "make Funnel work so the phone needs nothing" checklist, rendered from the off-thread
 * {@link Diag} snapshot — no blocking CLI calls happen here — with the current blocker highlighted and a
 * Re-check button that re-runs the whole bring-up.
 */
final class FunnelSetupWizard {

    /** Tailscale admin console where the one-time Funnel node-attribute is granted (computer/account side).
     *  The attribute lives in the tailnet policy file on the Access Controls page (there is no
     *  {@code /admin/settings/funnel} page — that 404s). */
    private static final String TAILSCALE_FUNNEL_ADMIN_URL = "https://login.tailscale.com/admin/acls";
    /** Tailscale admin DNS page where HTTPS certificates are enabled for the tailnet. */
    private static final String TAILSCALE_DNS_ADMIN_URL = "https://login.tailscale.com/admin/dns";
    /** The ACL policy snippet that grants the Funnel node-attribute (paste into the admin policy editor). */
    private static final String FUNNEL_ACL_SNIPPET =
            "\"nodeAttrs\": [{ \"target\": [\"autogroup:member\"], \"attr\": [\"funnel\"] }]";

    private FunnelSetupWizard() {
    }

    /**
     * @param onRecheck closes the owning dialog and re-runs the Funnel bring-up
     */
    static Node create(Diag diag, String funnelError, Runnable onRecheck) {
        VBox box = new VBox(6);
        Label title = PilotWidgets.wrapped("Set up Tailscale Funnel once on THIS computer's account — then any "
                + "phone connects by just opening the link (no Tailscale, no VPN, nothing to install on the "
                + "phone):");
        Styles.on(title, Styles.STRONG_TEXT);
        box.getChildren().add(title);

        boolean step1ok = diag != null && diag.cliPresent() && diag.loggedIn();
        Issue issue = diag == null ? Issue.OTHER : diag.issue();

        // 1. Installed & signed in
        HBox s1 = PilotWidgets.stepRow(step1ok, "Tailscale installed & signed in on this computer",
                issue == Issue.NOT_INSTALLED || issue == Issue.LOGGED_OUT);
        if (diag != null && !diag.cliPresent()) {
            s1.getChildren().add(
                    PilotWidgets.linkBtn("Install Tailscale ▸", RemotePilotDialog.TAILSCALE_DOWNLOAD_URL));
        } else if (diag != null && !diag.loggedIn()) {
            s1.getChildren().add(PilotWidgets.copyCmdBtn("tailscale up"));
        }
        box.getChildren().add(s1);

        // 2. HTTPS certificates — can't reliably probe, but the CLI error names it (NO_HTTPS_CERT) when it's
        // the blocker, so highlight it then. This is the most common blocker once the ACL grant is in place.
        HBox s2 = PilotWidgets.stepRow(httpsStepDone(diag), "HTTPS certificates enabled for your tailnet",
                issue == Issue.NO_HTTPS_CERT);
        s2.getChildren().add(PilotWidgets.linkBtn("Open DNS settings ▸", TAILSCALE_DNS_ADMIN_URL));
        box.getChildren().add(s2);

        // 3. Funnel node-attribute granted in the ACL
        HBox s3 = PilotWidgets.stepRow(false, "\"funnel\" attribute granted in your tailnet ACL",
                issue == Issue.NOT_ENABLED);
        s3.getChildren().addAll(PilotWidgets.linkBtn("Open Access Controls ▸", TAILSCALE_FUNNEL_ADMIN_URL),
                PilotWidgets.copyCmdBtn(FUNNEL_ACL_SNIPPET, "Copy ACL snippet"));
        box.getChildren().add(s3);

        // 4. Operator (so Studio, running as you, can drive Funnel without sudo)
        HBox s4 = PilotWidgets.stepRow(false, "Let Studio manage Funnel without root (run once)",
                issue == Issue.NEEDS_OPERATOR);
        String operatorCmd = "sudo tailscale set --operator=" + System.getProperty("user.name", "$USER");
        s4.getChildren().add(PilotWidgets.copyCmdBtn(operatorCmd, "Copy command"));
        box.getChildren().add(s4);

        // Always surface the literal CLI reason (not just for OTHER) — it's the fastest way to tell HTTPS-cert
        // vs ACL vs operator apart when the checklist guesses wrong.
        if (funnelError != null && !funnelError.isBlank()) {
            Label raw = PilotWidgets.wrapped("Tailscale said: " + funnelError.trim());
            Styles.on(raw, Styles.WARNING_TEXT);
            box.getChildren().add(raw);
        }

        Button recheck = new Button("Re-check & enable");
        recheck.setDefaultButton(true);
        recheck.setOnAction(e -> onRecheck.run());
        box.getChildren().add(recheck);
        return box;
    }

    /**
     * Whether the HTTPS-certificate step reads as done. It cannot be probed, so it is ticked once the first step
     * is — except when Tailscale's own error names certificates, which used to tick it and highlight it as the
     * blocker at once.
     */
    static boolean httpsStepDone(Diag diag) {
        return diag != null && diag.cliPresent() && diag.loggedIn() && diag.issue() != Issue.NO_HTTPS_CERT;
    }
}
