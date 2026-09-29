package com.botmaker.sdk.plugin.pilot.transport;

import com.botmaker.sdk.plugin.pilot.TailscaleFunnelService;

import java.util.Locale;

/**
 * Tailscale Funnel: the server binds loopback and Tailscale serves it publicly as
 * {@code https://<machine>.<tailnet>.ts.net}, with a real certificate. The phone needs nothing installed.
 *
 * <p>It needs a one-time setup on the computer's Tailscale account, so a failure carries a {@link Diag} the
 * pairing dialog turns into its setup checklist.
 */
public final class FunnelTransport implements PilotTransport {

    /** The specific reason Funnel isn't live, so the checklist can point at the exact one-time fix. */
    public enum Issue { NONE, NOT_INSTALLED, LOGGED_OUT, NOT_ENABLED, NO_HTTPS_CERT, NEEDS_OPERATOR, OTHER }

    /** What the last probe or {@link #open} found: whether the CLI is there, signed in, and what blocked it. */
    public record Diag(boolean cliPresent, boolean loggedIn, Issue issue) {}

    private final TailscaleFunnelService funnel = new TailscaleFunnelService();
    private volatile Diag diag = new Diag(false, false, Issue.OTHER);
    private volatile boolean opened;

    @Override
    public TransportKind kind() {
        return TransportKind.FUNNEL;
    }

    /** The last probe's findings, for the setup checklist. */
    public Diag diag() {
        return diag;
    }

    @Override
    public Availability available() {
        boolean cli = funnel.isAvailable();
        boolean loggedIn = cli && funnel.isLoggedIn();
        if (!cli) {
            diag = new Diag(false, false, Issue.NOT_INSTALLED);
            return Availability.no("Tailscale isn't installed on this computer.", "Install Tailscale and sign in.");
        }
        if (!loggedIn) {
            diag = new Diag(true, false, Issue.LOGGED_OUT);
            return Availability.no("Tailscale isn't signed in on this computer.", "Run tailscale up.");
        }
        if (funnel.dnsName().isEmpty()) {
            diag = new Diag(true, true, Issue.OTHER);
            return Availability.no("This computer has no Tailscale name yet.", "Check MagicDNS in the admin console.");
        }
        diag = new Diag(true, true, Issue.NONE);
        return Availability.yes();
    }

    @Override
    public String bindHost() {
        return "127.0.0.1";
    }

    @Override
    public Opened open(int port) {
        TailscaleFunnelService.Result result = funnel.enable(port);
        if (result.ok()) {
            opened = true;
            diag = new Diag(true, true, Issue.NONE);
            return Opened.at(result.publicBase());
        }
        diag = new Diag(true, true, classify(result.error()));
        return Opened.failed(result.error());
    }

    @Override
    public void close() {
        if (!opened) return;
        opened = false;
        funnel.disable();
    }

    /** Maps a {@code tailscale funnel} error to the checklist step to highlight. */
    public static Issue classify(String err) {
        if (err == null) return Issue.OTHER;
        String e = err.toLowerCase(Locale.ROOT);
        if (e.contains("operator")) return Issue.NEEDS_OPERATOR;
        if (e.contains("https") || e.contains("cert")) return Issue.NO_HTTPS_CERT;
        if (e.contains("not enabled")) return Issue.NOT_ENABLED;
        if (e.contains("not logged in") || e.contains("logged out")) return Issue.LOGGED_OUT;
        return Issue.OTHER;
    }
}
