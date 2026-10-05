package com.botmaker.sdk.plugin.pilot.ui;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.toolkit.Async;
import com.botmaker.plugin.toolkit.Modals;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.plugin.pilot.transport.PilotTransport.Availability;
import com.botmaker.sdk.plugin.pilot.transport.TailnetPhones;
import com.botmaker.sdk.plugin.pilot.transport.TransportKind;
import com.botmaker.sdk.plugin.pilot.ui.RemotePilotUi.PilotOutcome;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The pairing dialog: what a finished {@link PilotOutcome} looks like on screen — how to reach the pilot from
 * the phone, the two QR codes, the token controls, and (when Funnel was asked for and didn't come up) the
 * {@link FunnelSetupWizard}.
 *
 * <p>Pure rendering: it never starts, stops or probes anything itself. Everything that would change state goes
 * back through {@link Actions}.
 */
final class RemotePilotDialog {

    /** Stable "latest release" permalink the install-app QR points at; the botmaker-pilot CI attaches this. */
    private static final String APK_URL =
            "https://github.com/BotMakerDev/botmaker-pilot/releases/latest/download/botpilot.apk";

    /** Where a user without Tailscale installs it. */
    static final String TAILSCALE_DOWNLOAD_URL = "https://tailscale.com/download";

    /** On-screen QR edge in px. The bitmap is encoded at exactly this size and shown 1:1 (no resample) so the
     *  modules stay crisp — a fractional downscale blurs the edges enough to defeat phone-camera decoding. */
    private static final int QR_PX = 240;

    /**
     * What the dialog can ask {@link RemotePilotUi} to do.
     *
     * @param resetToken revokes the pairing token and returns the outcome to re-render, or {@code null} if there
     *                   is no server to revoke it on
     * @param use remembers a transport and brings the pilot up again over it
     * @param backgroundMode builds the private-display controls (built lazily — it starts a launcher)
     */
    record Actions(UnaryOperator<PilotOutcome> resetToken, Consumer<TransportKind> use,
                   Supplier<Node> backgroundMode) {}

    /**
     * The pairing dialog on screen, if any. It is not modal, so a second 🎮 press used to stack a second copy,
     * each with its own background-mode box; the press now brings the open one forward. Every path that
     * re-renders (a reset token, the Funnel bring-up) closes the old one first. FX thread only.
     */
    private static Alert showing;

    private RemotePilotDialog() {
    }

    /** Closes the pairing dialog, if one is up — its URL and token stop working when the pilot is released. */
    static void closeShowing() {
        Alert open = showing;
        showing = null;
        if (open == null) return;
        if (javafx.application.Platform.isFxApplicationThread()) {
            if (open.isShowing()) open.close();
        } else {
            javafx.application.Platform.runLater(() -> { if (open.isShowing()) open.close(); });
        }
    }

    static void show(StudioServices services, PilotOutcome outcome, Actions actions) {
        if (showing != null && showing.isShowing()) {
            if (showing.getDialogPane().getScene().getWindow() instanceof javafx.stage.Stage stage) {
                stage.toFront();
                stage.requestFocus();
            }
            return;
        }
        TransportKind kind = outcome.kind();
        String url = outcome.url();

        Alert alert = services.theme().alert(Alert.AlertType.INFORMATION);
        alert.initOwner(Modals.owner(services));
        alert.setTitle("Remote Pilot");
        alert.setHeaderText(switch (kind) {
            case FUNNEL -> "Remote Pilot is live over HTTPS — your phone needs nothing installed.";
            case QUICK_TUNNEL -> "Remote Pilot is live over a Cloudflare quick tunnel — your phone needs nothing installed.";
            case LAN -> "Remote Pilot is on your local network.";
            case TAILNET, UNKNOWN -> "Remote Pilot is running on your tailnet.";
        });

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 4;");

        // The way the user picked didn't come up and a direct one took over. Funnel leads with its guided,
        // re-checkable setup checklist; the others say why and what to do.
        if (outcome.fellBack()) {
            if (outcome.asked() == TransportKind.FUNNEL) {
                content.getChildren().add(FunnelSetupWizard.create(outcome.diag(), outcome.error(),
                        () -> { alert.close(); actions.use().accept(TransportKind.FUNNEL); }));
            } else {
                Label failed = PilotWidgets.wrapped("⚠ " + outcome.asked().displayName() + " didn't start: "
                        + outcome.error() + (outcome.fix() == null ? "" : "\n" + outcome.fix()));
                Styles.on(failed, Styles.WARNING_TEXT);
                content.getChildren().add(failed);
            }
            content.getChildren().addAll(new Separator(), PilotWidgets.wrapped(
                    "Meanwhile the pilot is reachable over " + kind.displayName() + " with the link below."));
        }
        content.getChildren().add(PilotWidgets.wrapped(switch (kind) {
            case FUNNEL -> "Scan the LEFT QR (or tap the link) to open Remote Pilot on your phone — no app, account "
                    + "or VPN needed there. The RIGHT QR installs the optional BotPilot Android app.";
            case QUICK_TUNNEL -> "Scan the LEFT QR (or tap the link) on your phone — nothing to install there. The "
                    + "address is new each time the pilot starts, so scan again after a restart.";
            case LAN -> "Your phone must be on the same Wi-Fi as this computer. Scan the LEFT QR (or open the "
                    + "link). The RIGHT QR installs the optional BotPilot app.";
            case TAILNET, UNKNOWN -> "On your phone: ① install Tailscale, ② sign in to THIS same account, ③ scan "
                    + "the LEFT QR (or open the link). The RIGHT QR installs the optional BotPilot app.";
        }));
        if (kind == TransportKind.TAILNET) {
            content.getChildren().addAll(
                    PilotWidgets.linkBtn("Get Tailscale for your phone ▸", TAILSCALE_DOWNLOAD_URL),
                    phoneStatus());
        }

        // Lead with the input-mode choice: background (isolated :N) vs. mirroring the real desktop. This is the
        // recommended path (cursor stays free) and used to be buried at the very bottom of the dialog where the
        // user never found it — hence every click went through the cursor-moving :0 controller.
        content.getChildren().addAll(actions.backgroundMode().get(), new Separator());

        // The URL as a real clickable link (opens the system browser).
        Hyperlink link = new Hyperlink(url);
        link.setOnAction(e -> PilotWidgets.openInBrowser(url));
        link.setWrapText(true);
        // Editable field kept as a selectable copy fallback, in case the clipboard write is swallowed by the
        // window system (e.g. some Wayland setups).
        TextField urlField = new TextField(url);
        urlField.setPrefColumnCount(44);
        urlField.setEditable(false);
        Button copy = new Button("Copy URL");
        copy.setOnAction(e -> {
            urlField.requestFocus();
            urlField.selectAll();
            PilotWidgets.copyToClipboard(url);
            PilotWidgets.flashCopied(copy);
        });
        Button reset = new Button("Reset pairing token");
        reset.setTooltip(new Tooltip("Revoke the current token so previously-paired phones must scan again."));
        reset.setOnAction(e -> {
            PilotOutcome refreshed = actions.resetToken().apply(outcome);
            if (refreshed == null) return;
            alert.close();
            show(services, refreshed, actions);
        });

        content.getChildren().addAll(link, new Label("Token: " + outcome.token()),
                new HBox(8, copy, reset), qrRow(url));

        String warning = warning(kind);
        if (warning != null) {
            Label warn = PilotWidgets.wrapped(warning);
            Styles.on(warn, Styles.WARNING_TEXT);
            content.getChildren().add(warn);
        }

        content.getChildren().addAll(new Separator(), chooser(outcome, choice -> {
            alert.close();
            actions.use().accept(choice);
        }));

        alert.getDialogPane().setContent(content);
        alert.setResizable(true); // let the user grow it if the QR codes crowd the buttons on small screens
        showing = alert;
        alert.show();
    }

    /**
     * Whether Tailscale sees the phone online, filled in off the FX thread (the CLI can take seconds). A phone
     * offline here cannot reach a tailnet address whatever the dialog says, and the fix is on the phone.
     */
    private static Node phoneStatus() {
        Label label = PilotWidgets.wrapped("Checking whether your phone is on the tailnet…");
        PilotWidgets.tone(label, Styles.MUTED_TEXT);
        Async.load("pilot-tailnet-phones", TailnetPhones::probe, phones -> {
            label.setText(phoneStatusText(phones, Instant.now()));
            PilotWidgets.tone(label, phones.stream().anyMatch(TailnetPhones.Phone::online)
                    ? Styles.OK_TEXT : Styles.WARNING_TEXT);
        });
        return label;
    }

    /** The phone lines, plus what to do on the phone when none of them is online. */
    static String phoneStatusText(List<TailnetPhones.Phone> phones, Instant now) {
        if (phones.isEmpty()) {
            return "No phone on this tailnet yet: sign the phone's Tailscale app in to the same account.";
        }
        StringBuilder text = new StringBuilder();
        for (TailnetPhones.Phone phone : phones) text.append(TailnetPhones.describe(phone, now)).append('\n');
        if (phones.stream().noneMatch(TailnetPhones.Phone::online)) {
            text.append("On the phone: open Tailscale and connect; turn on Settings ▸ Network ▸ VPN ▸ Tailscale ▸ "
                    + "Always-on VPN; set Tailscale's battery use to Unrestricted.");
        }
        return text.toString().strip();
    }

    /** What the user should know about who else can reach the pilot over {@code kind}, or {@code null}. */
    static String warning(TransportKind kind) {
        if (kind == TransportKind.LAN) {
            return "⚠ Anyone on this network who has the link can view and control the bot. Prefer Tailscale "
                    + "on a network you don't own.";
        }
        if (kind.publicInternet()) {
            return "⚠ This address is on the public internet: the token in the link is the only lock. Don't share "
                    + "the link, and use Reset pairing token if it leaks.";
        }
        return null;
    }

    /**
     * The transport chooser: one radio per offered way to reach the pilot, the current one selected. One that
     * cannot be used right now says why, and can still be picked — picking it is how the user gets its setup
     * steps (Funnel's checklist, cloudflared's install line).
     */
    private static Node chooser(PilotOutcome outcome, java.util.function.Consumer<TransportKind> pick) {
        VBox box = new VBox(4);
        Label title = new Label("Reach the pilot through:");
        Styles.on(title, Styles.STRONG_TEXT);
        box.getChildren().add(title);
        ToggleGroup group = new ToggleGroup();
        for (TransportKind kind : TransportKind.offered()) {
            Availability availability = outcome.options().get(kind);
            String text = kind.displayName() + (kind.publicInternet() ? " (public)" : "");
            if (availability != null && !availability.ok()) text += " — " + availability.reason();
            RadioButton radio = new RadioButton(text);
            radio.setToggleGroup(group);
            radio.setSelected(kind == outcome.kind());
            if (availability != null && availability.fix() != null) radio.setTooltip(new Tooltip(availability.fix()));
            radio.setOnAction(e -> {
                if (kind != outcome.kind()) pick.accept(kind);
            });
            box.getChildren().add(radio);
        }
        return box;
    }

    /** Side-by-side QR codes with clear separation: left pairs the pilot URL, right downloads the APK. */
    private static Node qrRow(String pairingUrl) {
        HBox row = new HBox(40);
        row.setAlignment(Pos.TOP_CENTER);
        row.setStyle("-fx-padding: 8 0 0 0;");
        row.getChildren().addAll(
                qrCell(pairingUrl, "① Open on phone", "Scan to control the bot"),
                qrCell(APK_URL, "② Get the app (optional)", "Installs BotPilot for Android"));
        return row;
    }

    /**
     * A titled, captioned QR image in its own bordered card.
     *
     * <p>When the code can't be encoded the card still appears, saying so — it used to return {@code null} and
     * the caller dropped it silently, leaving a dialog with a missing QR and no explanation of why.
     */
    private static Node qrCell(String text, String title, String caption) {
        Label heading = new Label(title);
        Styles.on(heading, Styles.STRONG_TEXT);
        Image code = QrCodes.qr(text, QR_PX);

        Node body;
        if (code != null) {
            ImageView iv = new ImageView(code);
            iv.setFitWidth(QR_PX);
            iv.setFitHeight(QR_PX);
            iv.setSmooth(false); // keep module edges sharp if the platform ever scales it
            // White backing so the encoded quiet zone survives against the dark card background/border.
            StackPane qrFrame = new StackPane(iv);
            qrFrame.setStyle("-fx-background-color: white; -fx-padding: 8; -fx-background-radius: 4;");
            body = qrFrame;
        } else {
            Label failed = new Label("Couldn't draw this QR code — use the link above instead.");
            failed.setWrapText(true);
            failed.setAlignment(Pos.CENTER);
            failed.setMaxWidth(QR_PX);
            Styles.on(failed, Styles.WARNING_TEXT);
            body = failed;
        }

        Label cap = new Label(caption);
        cap.setWrapText(true);
        Styles.on(cap, Styles.MUTED_TEXT);
        VBox cell = new VBox(6, heading, body, cap);
        cell.setAlignment(Pos.CENTER);
        cell.setMaxWidth(QR_PX + 40);
        cell.setStyle("-fx-padding: 10; -fx-border-color: -bm-divider; -fx-border-radius: 8; -fx-border-width: 1;");
        return cell;
    }
}
