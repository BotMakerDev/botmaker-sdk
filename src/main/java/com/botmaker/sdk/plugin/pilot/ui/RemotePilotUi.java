package com.botmaker.sdk.plugin.pilot.ui;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.toolbar.ActionContext;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.sdk.plugin.pilot.NestedSessionLauncher;
import com.botmaker.sdk.plugin.pilot.PilotControlService;
import com.botmaker.sdk.plugin.pilot.PilotProject;
import com.botmaker.sdk.plugin.pilot.PilotServer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;

import com.botmaker.sdk.plugin.pilot.PilotPreferences;
import com.botmaker.sdk.plugin.pilot.transport.FunnelTransport;
import com.botmaker.sdk.plugin.pilot.transport.PilotTransport;
import com.botmaker.sdk.plugin.pilot.transport.PilotTransport.Availability;
import com.botmaker.sdk.plugin.pilot.transport.PilotTransport.Opened;
import com.botmaker.sdk.plugin.pilot.transport.TransportKind;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Remote Pilot's state machine: brings the pilot server up, decides how it is exposed, and owns the two
 * heavyweight resources that outlive a dialog — the {@link PilotServer} itself and the
 * {@link NestedSessionLauncher} that produces the private {@code :N} display it streams.
 *
 * <p>The rendering is elsewhere in this package: {@link RemotePilotDialog} (pairing),
 * {@link FunnelSetupWizard} (the one-time Tailscale checklist) and {@link BackgroundModeBox}
 * (private-display controls). This class calls them; none of them calls back into it except through the
 * callbacks it hands over.
 *
 * <p><b>Everything it needs from the host comes through {@link StudioServices}</b>, and that is the whole
 * shape of the pilot's move out of the editor. It used to hold an event bus, a settings service, a project
 * config and the execution service — four editor classes — for four facts: which project is open
 * ({@code resourcesDir}), how to say a line in the status bar ({@code status}), how to look like the rest of
 * the application ({@code theme}, {@code dialogs().ownerWindow()}), and the bot as a process ({@code runs}). Every
 * one of those is something only a host can answer, which is why they are on the contract; everything else
 * the pilot does — binding a port, driving Tailscale, capturing pixels, opening a nested display — it does
 * for itself out of shared and session.
 *
 * <p><b>Closeable, and it must be closed.</b> One is built per project the pilot is opened in, and the
 * plugin closes it from {@code projectClosing()}. Nothing used to release either resource, so switching
 * projects left a bound port serving a project that no longer exists and a nested display with the game
 * still running inside it.
 */
public final class RemotePilotUi implements AutoCloseable {

    /**
     * Result of a pilot bring-up ({@link #bringUpWith}) — enough to render the pairing dialog on the FX thread.
     *
     * <p>{@code baseUrl} is the address without the query string ({@code http://host:port}, the Funnel
     * {@code https://…ts.net} or a tunnel's {@code https://…trycloudflare.com}); the pairing URL is derived from
     * it and the token. Keeping the two apart is what lets "Reset pairing token" rebuild the URL instead of
     * rewriting it with a regex that only worked while the token happened to be the last query parameter.
     *
     * @param kind    the transport that carries the pilot
     * @param asked   the transport the user picked; differs from {@code kind} when it failed and a direct one
     *                took over, and then {@code error} and {@code fix} say why
     * @param diag    Tailscale's state when {@code asked} is Funnel, for its setup checklist; else {@code null}
     * @param options each offered transport's availability, for the chooser
     */
    record PilotOutcome(String baseUrl, String token, TransportKind kind, TransportKind asked, String error,
                        String fix, FunnelTransport.Diag diag, Map<TransportKind, Availability> options) {
        String url() {
            return baseUrl + "/?token=" + token;
        }

        PilotOutcome withToken(String fresh) {
            return new PilotOutcome(baseUrl, fresh, kind, asked, error, fix, diag, options);
        }

        /** Whether the transport the user picked is not the one carrying the pilot. */
        boolean fellBack() {
            return asked != kind;
        }
    }

    /**
     * The transports tried for {@code asked}, in order: the one asked for, then the direct ones. A public
     * transport is never a fallback: putting the pilot on the internet is something only the user picks.
     */
    static List<TransportKind> attempts(TransportKind asked) {
        LinkedHashSet<TransportKind> order = new LinkedHashSet<>();
        order.add(asked == TransportKind.UNKNOWN ? TransportKind.TAILNET : asked);
        order.add(TransportKind.TAILNET);
        order.add(TransportKind.LAN);
        return List.copyOf(order);
    }

    private final StudioServices services;
    private final PilotProject project;

    private PilotServer pilotServer;
    /** Produces + tears down the bot-owned {@code :N} session the pilot streams; created lazily with the server. */
    private NestedSessionLauncher nestedLauncher;

    /** The last successful bring-up, so re-clicking the toolbar just re-shows the same dialog instead of
     *  restarting the server on a fresh port (which would drop an already-paired phone). */
    private PilotOutcome lastOutcome;

    /** FX-thread-only guard: a bring-up is in flight, so a second click must not start a second one. */
    private boolean bringingUp;

    public RemotePilotUi(StudioServices services) {
        this.services = services;
        this.project = new PilotProject(services);
    }

    /**
     * Starts (once) the remote BotPilot server over the transport the user last picked, and shows a pairing
     * dialog. Until they pick one it is a direct bind on the Tailscale address: the phone runs Tailscale signed
     * into the same account, and nothing is public. The dialog's chooser switches to Funnel, a Cloudflare quick
     * tunnel or the local network ({@link #use}).
     *
     * <p>Idempotent while the server is up: it re-shows the existing pairing dialog, keeping the paired phone
     * connected on the same URL/port/token.
     */
    public void show() {
        bringUp(false, PilotPreferences.transport());
    }

    /**
     * The 🎮 Pilot press: shows the pilot of the project {@code context} describes, bringing it up on the first
     * press.
     *
     * <p>The pilot is kept rather than rebuilt, because it owns the port and the display: a second press must
     * re-show the pairing dialog rather than rebind and drop an already-paired phone. A pilot left over from
     * another project is released first. Touched only on the JavaFX thread — a press and
     * {@link #release()} both arrive there — so the field needs no synchronization.
     *
     * <p>"Another project" is decided by the project's directory, never by the services object: the host
     * builds a fresh {@link StudioServices} for every press, so comparing the objects released the pilot on
     * every press — the paired phone dropped and the game in the private display killed.
     */
    public static void open(ActionContext context) {
        StudioServices services = context.services();
        if (current != null && !sameProject(current.services, services)) release();
        if (current == null) current = new RemotePilotUi(services);
        current.show();
    }

    /** Whether two services answer for the same open project — the same resources directory. */
    static boolean sameProject(StudioServices a, StudioServices b) {
        return a != null && b != null && java.util.Objects.equals(a.resourcesDir(), b.resourcesDir());
    }

    /**
     * Releases the pilot's port and its nested display, if one was brought up; the plugin calls it when the
     * project it was serving is left. A pilot still answering on the old port would be streaming a project
     * nobody has open.
     */
    public static void release() {
        RemotePilotUi open = current;
        current = null;
        if (open != null) open.close();
    }

    /** The pilot of the project currently bound, or {@code null} until its button is first pressed. */
    private static RemotePilotUi current;

    /**
     * The live private session's host window id for the overlay to draw over, or {@code 0} when there is none —
     * revealing it first, since bring-up minimizes it and an overlay over a minimized window shows nothing.
     *
     * <p>The launcher is created lazily by the background-mode box, so a {@code null} one means no session has
     * ever been started in this project and there is nothing to look at.
     */
    public long liveSessionWindow() {
        return nestedLauncher == null ? 0 : nestedLauncher.revealHostWindow();
    }

    /**
     * Releases both heavyweight resources: unbinds the pilot port (tearing down any Funnel front with it) and
     * stops the private {@code :N} session, killing the game running inside it.
     *
     * <p>A paired phone loses its connection here. That is deliberate and is the honest signal — the project it
     * was driving is gone, and a pilot still answering on the old port would be controlling nothing.
     *
     * <p>Idempotent: both fields are dropped, so a second call is a no-op.
     */
    @Override
    public void close() {
        RemotePilotDialog.closeShowing();
        if (nestedLauncher != null) {
            try {
                nestedLauncher.close();
            } catch (Exception e) {
                System.err.println("Couldn't stop the private display session: " + e.getMessage());
            }
            nestedLauncher = null;
        }
        if (pilotServer != null) {
            try {
                pilotServer.close();
            } catch (Exception e) {
                System.err.println("Couldn't stop the Remote Pilot server: " + e.getMessage());
            }
            pilotServer = null;
        }
        lastOutcome = null;
    }

    /**
     * The chooser's action: remembers {@code kind} and brings the pilot up again over it. Always rebinds, since a
     * tunnel fronts a loopback bind and a direct transport binds its own address. A paired phone reconnects on
     * the new address, which the dialog shows.
     */
    void use(TransportKind kind) {
        PilotPreferences.transport(kind);
        bringUp(true, kind);
    }

    /**
     * The launcher for the private {@code :N} display, created on first use bound to the (by now started)
     * {@link PilotServer} and reused across dialog reopens so Stop and the status line still reflect a session
     * started earlier.
     */
    NestedSessionLauncher launcher() {
        if (nestedLauncher == null) {
            nestedLauncher = new NestedSessionLauncher(project.resourcesDir(), project::launchTarget);
        }
        return nestedLauncher;
    }

    /**
     * Shared bring-up scaffold: (optionally) tear down a running server, ensure one exists, then run the chosen
     * bring-up off the FX thread and marshal the pairing dialog back.
     *
     * <p>The Tailscale CLI can block for seconds, so this must not run inline — doing so freezes (and, if the
     * CLI hangs, appears to crash) the UI.
     */
    private void bringUp(boolean forceRestart, TransportKind asked) {
        // A bring-up is already running. Its progress dialog is not modal, so without this a second toolbar
        // click would start a second thread and race two start() calls onto the same server.
        if (bringingUp) return;

        // Already up and we're not deliberately restarting → re-show the same dialog, don't rebind the port.
        if (!forceRestart && pilotServer != null && pilotServer.isRunning() && lastOutcome != null) {
            showDialog(lastOutcome);
            return;
        }
        if (forceRestart && pilotServer != null) {
            pilotServer.close();
            lastOutcome = null;
        }
        if (pilotServer == null) {
            PilotControlService control = new PilotControlService(services.runs());
            pilotServer = new PilotServer(services.runs(), project, control);
        }
        services.status("Starting Remote Pilot…");

        AtomicBoolean cancelled = new AtomicBoolean();
        Alert progress = progressDialog(cancelled);
        bringingUp = true;
        progress.show();

        Thread t = new Thread(() -> {
            PilotOutcome o = null;
            String error = null;
            try {
                o = bringUpWith(asked);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final PilotOutcome outcome = o;
            final String err = error;
            Platform.runLater(() -> {
                bringingUp = false;
                progress.setResult(ButtonType.CANCEL); // let close() dismiss a button-less alert
                progress.close();
                if (outcome == null) {
                    services.status("Could not start Remote Pilot: " + err);
                    return;
                }
                lastOutcome = outcome;
                services.status("Remote Pilot (" + outcome.kind().displayName() + ") at " + outcome.url());
                // Cancel can't unbind a server that has already come up, but it can honour what the user
                // actually asked for: no dialog. The status line above says where it is, and the toolbar
                // button re-shows the pairing dialog on demand.
                if (!cancelled.get()) showDialog(outcome);
            });
        }, "remote-pilot-start");
        t.setDaemon(true);
        t.start();
    }

    /** Indeterminate spinner shown while the (possibly multi-second) Tailscale bring-up runs off-thread. */
    private Alert progressDialog(AtomicBoolean cancelled) {
        Alert a = services.theme().alert(Alert.AlertType.NONE);
        a.initOwner(Modals.owner(services));
        a.setTitle("Remote Pilot");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(30, 30);
        Label msg = new Label("Starting Remote Pilot…\nChecking the ways to reach it (this can take a few seconds).");
        msg.setWrapText(true);
        HBox box = new HBox(12, spinner, msg);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-padding: 10;");
        a.getDialogPane().setContent(box);
        a.getButtonTypes().setAll(ButtonType.CANCEL);
        // Only a real click means "cancel" — closing it programmatically below sets the same result.
        Button cancel = (Button) a.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancel != null) cancel.addEventFilter(ActionEvent.ACTION, e -> cancelled.set(true));
        return a;
    }

    private void showDialog(PilotOutcome outcome) {
        RemotePilotDialog.show(services, outcome, new RemotePilotDialog.Actions(
                this::resetToken,
                this::use,
                () -> BackgroundModeBox.create(launcher(), project)));
    }

    /**
     * Revokes the pairing token and returns the outcome to re-render with, or {@code null} when there is no
     * server to revoke it on.
     */
    private PilotOutcome resetToken(PilotOutcome current) {
        if (pilotServer == null) return null;
        PilotOutcome refreshed = current.withToken(pilotServer.resetToken());
        lastOutcome = refreshed;
        return refreshed;
    }

    /**
     * Brings the server up over {@code asked}, or over the first direct transport that works when it cannot,
     * carrying why into the outcome so the dialog says so. Every offered transport is probed first, so the
     * chooser can show which ones work before the user picks.
     *
     * <p>Blocking (CLI probes, a tunnel's start, the server bind); runs off the FX thread.
     *
     * @throws IllegalStateException when no transport works at all
     */
    private PilotOutcome bringUpWith(TransportKind asked) {
        Map<TransportKind, PilotTransport> transports = new EnumMap<>(TransportKind.class);
        Map<TransportKind, Availability> options = new EnumMap<>(TransportKind.class);
        for (TransportKind kind : TransportKind.offered()) {
            PilotTransport transport = PilotTransport.of(kind);
            transports.put(kind, transport);
            options.put(kind, transport.available());
        }
        String error = null;
        String fix = null;
        for (TransportKind kind : attempts(asked)) {
            Availability availability = options.get(kind);
            PilotTransport transport = transports.get(kind);
            if (!availability.ok()) {
                if (kind == asked) {
                    error = availability.reason();
                    fix = availability.fix();
                }
                continue;
            }
            PilotServer.Endpoint endpoint = pilotServer.start(transport.bindHost());
            Opened opened = transport.open(endpoint.port());
            if (opened.ok()) {
                pilotServer.attach(transport);
                FunnelTransport.Diag diag =
                        transports.get(TransportKind.FUNNEL) instanceof FunnelTransport f && asked == TransportKind.FUNNEL
                                ? f.diag() : null;
                return new PilotOutcome(opened.baseUrl(), endpoint.token(), kind, asked, error, fix, diag,
                        Map.copyOf(options));
            }
            if (kind == asked) {
                error = opened.error();
                fix = null;
            }
            transport.close();
            pilotServer.close();
        }
        throw new IllegalStateException(error != null ? error : "this computer has no network the phone can reach");
    }
}
