// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.pilot.transport;

import com.botmaker.sdk.plugin.pilot.PilotPreferences;
import com.botmaker.sdk.plugin.pilot.TailscaleFunnelService;

import java.util.Locale;
import java.util.function.LongPredicate;

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
    private Thread shutdownHook;

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

    /**
     * {@inheritDoc}
     *
     * <p>{@code funnel --bg} outlives the CLI and this process, so it is also turned off by a shutdown hook, and
     * recorded as this process's ({@link PilotPreferences#funnelOwner}) for {@link #clearLeftover} should the
     * process die without running it.
     */
    @Override
    public Opened open(int port) {
        // Recorded before the Funnel goes up: a death in between must not leave an unrecorded one.
        long before = PilotPreferences.funnelOwner();
        PilotPreferences.funnelOwner(ProcessHandle.current().pid());
        TailscaleFunnelService.Result result = funnel.enable(port);
        if (result.ok()) {
            opened = true;
            shutdownHook = new Thread(this::turnOff, "funnel-off");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            diag = new Diag(true, true, Issue.NONE);
            return Opened.at(result.publicBase());
        }
        // A Funnel a dead process left, and that could not be turned off yet, stays recorded.
        PilotPreferences.funnelOwner(before);
        diag = new Diag(true, true, classify(result.error()));
        return Opened.failed(result.error());
    }

    @Override
    public void close() {
        if (!opened) return;
        opened = false;
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // Already shutting down: the hook is running or has run.
            }
            shutdownHook = null;
        }
        turnOff();
    }

    private void turnOff() {
        if (funnel.disable()) PilotPreferences.funnelOwner(0);
    }

    /**
     * Turns off a Funnel a process that is gone left up: killed or crashed, it kept serving a port publicly.
     * Blocking (a CLI call), and nothing when there is none. One at a time: a second caller waits, so it cannot
     * turn off a Funnel opened after the first one cleared.
     */
    public static synchronized void clearLeftover() {
        if (!leftover(PilotPreferences.funnelOwner(),
                pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))) return;
        if (new TailscaleFunnelService().disable()) PilotPreferences.funnelOwner(0);
    }

    /** Whether the Funnel recorded as {@code owner}'s ({@code 0}: none) has nobody left to turn it off. */
    static boolean leftover(long owner, LongPredicate alive) {
        return owner > 0 && !alive.test(owner);
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
