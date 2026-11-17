// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.emulator.signin;

import com.botmaker.sdk.api.signin.NeedsYou;
import com.botmaker.sdk.api.signin.Refusal;
import com.botmaker.sdk.api.signin.SecondStep;
import com.botmaker.sdk.internal.signin.DoorLines;
import com.botmaker.sdk.internal.signin.SignInClient;
import com.botmaker.shared.emulator.config.AccountRecord.Verification;
import com.botmaker.shared.emulator.config.Operation;
import com.botmaker.shared.emulator.google.AccountDevice;
import com.botmaker.shared.emulator.google.InstanceSignIn;
import com.botmaker.shared.emulator.google.Totp;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The host of one sign-in run (spec {@code 2026-11-15-google-sign-in-bot-design.md} §2): the loopback door the run's
 * {@link SignInClient} knocks on, bound to one instance's device, one account and one operation. The run names a field
 * by its place; this side resolves the value (the address, the password the batch holds, a code made from the
 * account's authenticator key or asked in BotMaker's prompt), taps the field and types it. No answer carries a value,
 * and nothing here logs one: a step says a code's length only.
 *
 * <p>It serves one connection at a time on a thread of its own, until {@link #close()} or the operation is cancelled.
 * The device, the password and the context stay the caller's.
 */
public final class SignInHost implements AutoCloseable {

    /** How often the serving thread looks whether the operation was cancelled. */
    private static final Duration CANCEL_POLL = Duration.ofMillis(500);
    /** The codes an authenticator key gives before the code is asked: this window's, the one before, the next. */
    private static final int KEY_TRIES = 3;
    private static final int KEY_DEL = 67;
    private static final int KEY_MOVE_END = 123;
    /** How many characters a refused code's field is cleared of: a backup code with its spaces, and some. */
    private static final int CLEARED = 16;

    private final AccountDevice device;
    private final String email;
    private final char[] password;
    private final Verification method;
    private final InstanceSignIn.SecondStep second;
    private final Operation.Context context;
    private final LongSupplier clock;
    private final String id = UUID.randomUUID().toString();
    private final String tokenText = DoorLines.token();
    private final Set<String> before;
    private final ServerSocket server;
    private final Thread serving;

    /** Whether the last call asked the user, so the next one says they are done; read by {@link #close()} too. */
    private volatile boolean waitingForYou;
    private boolean codeTyped;
    /** What the run said Google refused, read by the driver once the run ended. */
    private volatile Refusal refusal;
    private volatile boolean passwordTyped;
    /** Whether Google went past the password: it asked a second step after it was typed. */
    private volatile boolean passwordAccepted;
    /** Whether a value had signs {@code input text} cannot type. */
    private volatile boolean cannotType;
    private int keyTries;
    private long keyWindow;
    private int codeTries;

    private SignInHost(AccountDevice device, String email, char[] password, InstanceSignIn.SecondStep second,
                       Operation.Context context, LongSupplier clock) throws IOException {
        this.device = device;
        this.email = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
        this.second = second == null ? InstanceSignIn.SecondStep.ASK : second;
        Verification chosen = this.second.methodOf().apply(this.email);
        this.method = chosen == null ? Verification.ASK : chosen.followed(Verification.ASK);
        this.context = context;
        this.clock = clock;
        Set<String> held = device.accounts();
        this.before = held == null ? Set.of() : Set.copyOf(held);
        this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        server.setSoTimeout((int) CANCEL_POLL.toMillis());
        this.serving = Thread.ofPlatform().daemon().name("sign-in host").unstarted(this::serve);
        serving.start();
    }

    /**
     * Opens the host of {@code email}'s sign-in on {@code device}.
     *
     * @param password the account's password, which the caller clears once the sign-in ended
     * @param second   the account's second step, its codes' prompt and its authenticator keys
     */
    public static SignInHost open(AccountDevice device, String email, char[] password,
                                  InstanceSignIn.SecondStep second, Operation.Context context) throws IOException {
        return open(device, email, password, second, context, System::currentTimeMillis);
    }

    static SignInHost open(AccountDevice device, String email, char[] password, InstanceSignIn.SecondStep second,
                           Operation.Context context, LongSupplier clock) throws IOException {
        return new SignInHost(device, email, password, second, context, clock);
    }

    /** What the run's environment holds to reach this host; never a property, which the command line shows. */
    public Map<String, String> environment() {
        return Map.of(SignInClient.PORT_VARIABLE, Integer.toString(server.getLocalPort()),
                SignInClient.TOKEN_VARIABLE, tokenText);
    }

    /** Whether the host still serves: false once closed or cancelled. */
    public boolean open() {
        return !server.isClosed();
    }

    /** The address Android added since the host opened, the one asked or another of the account's; empty when none. */
    public Optional<String> added() {
        Set<String> now = device.accounts();
        if (now == null) return Optional.empty();
        if (now.contains(email) && !before.contains(email)) return Optional.of(email);
        return now.stream().filter(account -> !before.contains(account)).sorted().findFirst();
    }

    /**
     * Whether Android lists the account: under the address asked (held before the host opened too, which is signed in
     * already), or under another it added since.
     */
    public boolean signedIn() {
        Set<String> now = device.accounts();
        return now != null && (now.contains(email) || now.stream().anyMatch(account -> !before.contains(account)));
    }

    /** Why the run said Google ended the sign-in; empty when it said nothing. */
    public Optional<Refusal> refusal() {
        return Optional.ofNullable(refusal);
    }

    /**
     * Whether Google took the password short of signing in: it asked a second step after it was typed. Whether the
     * account ended signed in is {@link #signedIn()}'s, read once by the caller.
     */
    public boolean passwordAccepted() {
        return passwordAccepted;
    }

    /** Whether the run asked the user and has not called since: the user's time, not the run's. */
    public boolean waitingForYou() {
        return waitingForYou;
    }

    /** Whether a value the run asked for had signs BotMaker cannot type. */
    public boolean cannotType() {
        return cannotType;
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException alreadyGone) {
            // nothing more to close
        }
        if (Thread.currentThread() != serving) {
            serving.interrupt();
            try {
                serving.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (waitingForYou) {
            waitingForYou = false;
            context.resumed();
        }
    }

    private void serve() {
        while (!server.isClosed()) {
            if (context.cancelled()) {
                close();
                return;
            }
            // one call at a time: a second connection waits for the first, never answered beside it
            try (Socket socket = server.accept()) {
                Optional<String> line = DoorLines.line(socket);
                DoorLines.answer(socket, line.isEmpty() ? refused("no message") : answer(line.get()));
            } catch (SocketTimeoutException quiet) {
                // no call this while: look at the cancel again
            } catch (InterruptedException e) {
                // a host that stops serving says so: open() is false, the run's calls refused at once
                close();
                return;
            } catch (IOException gone) {
                // the run went away mid-call, or the host closed
            }
        }
    }

    private ObjectNode answer(String line) throws InterruptedException {
        try {
            return answered(line);
        } catch (RuntimeException | Error e) {
            // a device gone mid-call (dadb throws either): the call is refused, the host serves on; its words may
            // name a command, so they are not passed on
            return refused("the instance did not answer");
        }
    }

    private ObjectNode answered(String line) throws InterruptedException {
        JsonNode message;
        try {
            message = DoorLines.JSON.readTree(line);
        } catch (IOException notJson) {
            return refused("not a message");
        }
        if (!DoorLines.tokenMatches(tokenText, message.path("token").asText(""))) return refused("wrong token");
        if (context.cancelled()) return refused("the sign-in was cancelled");
        SignInClient.Op op = SignInClient.Op.fromId(message.path("op").asText(""));
        if (op != SignInClient.Op.NEEDS_YOU && waitingForYou) {
            waitingForYou = false;
            context.resumed();
        }
        return switch (op) {
            case REQUEST -> ok().put("id", id).put("method", stepOf(method).id());
            case SIGNED_IN -> ok().put("signedIn", signedIn());
            case NEEDS_YOU -> {
                NeedsYou kind = NeedsYou.fromId(message.path("kind").asText(""));
                // Google asks a second step only of a password it took
                if (passwordTyped && (kind == NeedsYou.PHONE || kind == NeedsYou.CODE)) passwordAccepted = true;
                context.needsYou(kind.displayName());
                waitingForYou = true;
                yield ok();
            }
            case TYPE -> type(SignInClient.Value.fromId(message.path("value").asText("")), message.path("field"));
            case REFUSED -> {
                refusal = Refusal.fromId(message.path("why").asText(""));
                yield ok();
            }
            case UNKNOWN -> refused("not a sign-in call");
        };
    }

    private ObjectNode type(SignInClient.Value value, JsonNode field) throws InterruptedException {
        Optional<Rectangle> place = DoorLines.place(field);
        if (place.isEmpty()) return refused("the field has no place");
        Rectangle at = place.get();
        return switch (value) {
            case EMAIL -> {
                context.step("Typing the email…");
                yield typed(at, email.toCharArray(), true, false);
            }
            case PASSWORD -> {
                if (password == null || password.length == 0) yield refused("no password is given for this account");
                context.step("Typing the password…");
                ObjectNode answer = typed(at, password, false, false);
                if (answer.path("ok").asBoolean()) passwordTyped = true;
                yield answer;
            }
            case CODE -> {
                if (passwordTyped) passwordAccepted = true;
                yield code(at);
            }
            case UNKNOWN -> refused("not a value BotMaker types");
        };
    }

    /** A code made from the account's key, else asked of the user; each code typed over the one Google refused. */
    private ObjectNode code(Rectangle at) throws InterruptedException {
        if (method == Verification.AUTHENTICATOR && keyTries < KEY_TRIES) {
            if (keyTries == 0) keyWindow = Totp.window(clock.getAsLong());
            // Google's clock and this PC's may differ either way: this window's, the one before, then the next one
            // once it has come, as Google refuses a window not yet begun
            long window = switch (keyTries) {
                case 0 -> keyWindow;
                case 1 -> keyWindow - 1;
                default -> keyWindow + 1;
            };
            if (keyTries == 2 && !waitFor(window)) return refused("the sign-in was cancelled");
            Optional<char[]> code = second.keys().code(keyOf(), window * Totp.STEP.toMillis());
            if (code.isPresent()) {
                keyTries++;
                context.step("Typing the code from BotMaker's authenticator key (" + code.get().length
                        + " characters)…");
                return typed(at, code.get(), true, true);
            }
            keyTries = KEY_TRIES;
            context.step("BotMaker holds no authenticator key for this account: its code is asked.");
        }
        Verification asked = method == Verification.BACKUP_CODE ? Verification.BACKUP_CODE : Verification.CODE;
        while (codeTries < InstanceSignIn.CODE_TRIES) {
            int attempt = codeTries + 1;
            context.needsYou((asked == Verification.BACKUP_CODE ? "Type one of the account's backup codes"
                    : "Type the code Google texted, or the one your authenticator app shows,")
                    + " in BotMaker's dialog (try " + attempt + " of " + InstanceSignIn.CODE_TRIES + ").");
            char[] code;
            try {
                code = second.codes().ask(email, asked, attempt);
            } catch (InterruptedException | RuntimeException e) {
                context.resumed();
                throw e;
            }
            if (code == null || code.length == 0) {
                // the user answers on the instance's screen: the needs-you stays until the run's next call
                waitingForYou = true;
                return refused("the user answers on the instance's screen");
            }
            try {
                context.resumed();
                // closed or cancelled while the dialog was open: nothing is typed into an instance no longer ours
                if (!open() || context.cancelled()) return refused("the sign-in was cancelled");
                if (!InstanceSignIn.typeable(code)) {
                    // not one Google refused: the try isn't counted
                    context.step("That code has signs BotMaker can't type (ASCII only).");
                    continue;
                }
                codeTries = attempt;
                context.step("Typing the code (" + code.length + " characters)…");
                return typed(at, code, false, true);
            } finally {
                Arrays.fill(code, '\0');
            }
        }
        return refused("Google refused " + InstanceSignIn.CODE_TRIES + " codes");
    }

    /** Waits, the serving thread only, until this PC's clock reaches {@code window}; false when the host ends first. */
    private boolean waitFor(long window) throws InterruptedException {
        while (Totp.window(clock.getAsLong()) < window) {
            if (!open() || context.cancelled()) return false;
            Thread.sleep(CANCEL_POLL);
        }
        return true;
    }

    /**
     * Taps the middle of {@code at} and types {@code text}; a code goes over the one Google refused, cleared first.
     *
     * @param clear whether {@code text} is this host's own array, cleared once typed
     */
    private ObjectNode typed(Rectangle at, char[] text, boolean clear, boolean isCode) {
        try {
            if (!InstanceSignIn.typeable(text)) {
                cannotType = true;
                return refused("BotMaker types only plain letters, digits and signs (ASCII): sign in by hand");
            }
            device.tap(at);
            if (isCode && codeTyped) {
                // Google keeps a refused code in the field: the next one would be typed after it
                device.key(KEY_MOVE_END);
                for (int i = 0; i < CLEARED; i++) device.key(KEY_DEL);
            }
            device.type(text);
            if (isCode) codeTyped = true;
            return ok();
        } catch (RuntimeException | Error e) {
            // dadb surfaces a gone device as either; its words could hold the command, so they are not passed on
            return refused("the instance did not take the typing");
        } finally {
            if (clear) Arrays.fill(text, '\0');
        }
    }

    private String keyOf() {
        String kept = second.keyedAs().apply(email);
        return kept == null || kept.isBlank() ? email : kept;
    }

    private static SecondStep stepOf(Verification method) {
        return switch (method) {
            case PHONE -> SecondStep.PHONE;
            case CODE -> SecondStep.CODE;
            case AUTHENTICATOR -> SecondStep.AUTHENTICATOR;
            case BACKUP_CODE -> SecondStep.BACKUP_CODE;
            case ASK, DEFAULT -> SecondStep.ASK;
            case UNKNOWN -> SecondStep.UNKNOWN;
        };
    }

    private static ObjectNode ok() {
        return DoorLines.ok();
    }

    private static ObjectNode refused(String why) {
        return DoorLines.refused(why);
    }
}
