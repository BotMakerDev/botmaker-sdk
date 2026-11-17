// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.signin;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.api.signin.NeedsYou;
import com.botmaker.sdk.api.signin.Refusal;
import com.botmaker.sdk.api.signin.SecondStep;
import com.botmaker.sdk.api.signin.SignIn;
import com.botmaker.sdk.api.signin.Typed;
import com.botmaker.shared.tools.UserDirs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The run's side of a sign-in: one loopback connection per call to the host that started the run, named by two
 * environment variables ({@link #PORT_VARIABLE}, {@link #TOKEN_VARIABLE}), never a property another process reads
 * off the run's command line. Each call is one line of JSON out and one back; no answer ever carries a value the
 * host typed.
 */
public final class SignInClient {

    public static final String PORT_VARIABLE = "BOTMAKER_SIGNIN_PORT";
    public static final String TOKEN_VARIABLE = "BOTMAKER_SIGNIN_TOKEN";
    /** The door file's name in this PC's cache folder ({@link #doorFile()}). */
    public static final String DOOR_FILE = "signin-door.json";

    /** A user's code is typed in BotMaker's prompt while the call waits; the host gives up long before this. */
    private static final Duration CODE_WAIT = Duration.ofMinutes(20);
    /** Every other call is answered at once. */
    private static final Duration ANSWER_WAIT = Duration.ofSeconds(30);
    private static final Duration CONNECT_WAIT = Duration.ofSeconds(2);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** What the host types: it holds each, the run names which. */
    public enum Value {
        EMAIL("email"), PASSWORD("password"), CODE("code"), UNKNOWN("unknown");

        private final String id;

        Value(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Value fromId(String id) {
            for (Value value : values()) if (value.id.equals(id)) return value;
            return UNKNOWN;
        }
    }

    /** What a call asks; its id is the message's {@code op}. */
    public enum Op {
        REQUEST("request"), TYPE("type"), SIGNED_IN("signed-in"), NEEDS_YOU("needs-you"), REFUSED("refused"), UNKNOWN("unknown");

        private final String id;

        Op(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Op fromId(String id) {
            for (Op op : values()) if (op.id.equals(id)) return op;
            return UNKNOWN;
        }
    }

    private final int port;
    private final String token;

    public SignInClient(int port, String token) {
        this.port = port;
        this.token = token;
    }

    /** The client of this run's host, or none when the run was not started as a sign-in. */
    public static Optional<SignInClient> ofRun() {
        return of(System::getenv);
    }

    /** Where this PC's core says how to reach its sign-in door: {@code port} and {@code token}, readable by its owner only. */
    public static Path doorFile() {
        return UserDirs.cache().resolve(DOOR_FILE);
    }

    /**
     * The door that types this run's secrets: this PC's, by {@link #doorFile()}, even for a run a sign-in host started,
     * which types only its own account's values; none without one, so a call refuses at once rather than wait on
     * nobody.
     */
    public static Optional<SignInClient> door() {
        return door(doorFile());
    }

    /** The client {@code doorFile} names; none when it is missing, unreadable or names no port. */
    static Optional<SignInClient> door(Path doorFile) {
        try {
            if (!Files.isRegularFile(doorFile)) return Optional.empty();
            JsonNode door = JSON.readTree(doorFile.toFile());
            String token = door.path("token").asText("");
            return of(name -> switch (name) {
                case PORT_VARIABLE -> door.path("port").asText("");
                case TOKEN_VARIABLE -> token;
                default -> null;
            });
        } catch (IOException | RuntimeException unread) {
            return Optional.empty();
        }
    }

    /** The client {@code environment} names; none without both variables or with a port that is not one. */
    static Optional<SignInClient> of(UnaryOperator<String> environment) {
        String port = environment.apply(PORT_VARIABLE);
        String token = environment.apply(TOKEN_VARIABLE);
        if (port == null || token == null || port.isBlank() || token.isBlank()) return Optional.empty();
        try {
            int number = Integer.parseInt(port.strip());
            return number > 0 && number <= 65_535 ? Optional.of(new SignInClient(number, token)) : Optional.empty();
        } catch (NumberFormatException notAPort) {
            return Optional.empty();
        }
    }

    /** The request the host answers; none when it answers no id. */
    public Optional<SignIn.Request> request() {
        return call(message(Op.REQUEST), ANSWER_WAIT).filter(SignInClient::ok)
                .filter(answer -> !answer.path("id").asText("").isBlank())
                .map(answer -> new SignIn.Request(answer.path("id").asText(),
                        SecondStep.fromId(answer.path("method").asText(""))));
    }

    /** Asks the host to tap the middle of {@code field} and type {@code value}. */
    public Typed type(Value value, Rect field) {
        ObjectNode message = message(Op.TYPE).put("value", value.id());
        return typed(placed(message, field), value == Value.CODE ? CODE_WAIT : ANSWER_WAIT);
    }

    /**
     * Asks the door to tap the middle of {@code field} on {@code instance} and type what {@code secret} names, as its
     * kind says: a password as kept, an authenticator's current code. Only the secret's name goes out.
     */
    public Typed type(Secret secret, Rect field, String instance) {
        ObjectNode message = message(Op.TYPE).put("secret", secret.name()).put("instance", instance);
        return typed(placed(message, field), ANSWER_WAIT);
    }

    private static ObjectNode placed(ObjectNode message, Rect field) {
        message.putObject("field").put("x", field.x()).put("y", field.y())
                .put("width", field.width()).put("height", field.height());
        return message;
    }

    private Typed typed(ObjectNode message, Duration wait) {
        return call(message, wait)
                .map(answer -> ok(answer) ? Typed.typed() : Typed.refused(answer.path("why").asText("")
                        .isBlank() ? "the sign-in's host refused, saying no why" : answer.path("why").asText()))
                .orElseGet(() -> Typed.refused("the sign-in's host does not answer"));
    }

    public boolean signedIn() {
        return call(message(Op.SIGNED_IN), ANSWER_WAIT).filter(SignInClient::ok)
                .map(answer -> answer.path("signedIn").asBoolean()).orElse(false);
    }

    public void needsYou(NeedsYou kind) {
        call(message(Op.NEEDS_YOU).put("kind", kind.id()), ANSWER_WAIT);
    }

    public void refused(Refusal why) {
        call(message(Op.REFUSED).put("why", why.id()), ANSWER_WAIT);
    }

    private ObjectNode message(Op op) {
        return JSON.createObjectNode().put("token", token).put("op", op.id());
    }

    private static boolean ok(JsonNode answer) {
        return answer.path("ok").asBoolean(false);
    }

    /** One line out, one line back; none when the host is gone, slow past {@code wait} or answers nothing that reads. */
    private Optional<JsonNode> call(ObjectNode message, Duration wait) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), (int) CONNECT_WAIT.toMillis());
            socket.setSoTimeout((int) wait.toMillis());
            OutputStream out = socket.getOutputStream();
            out.write((JSON.writeValueAsString(message) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            String line = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
            return line == null ? Optional.empty() : Optional.of(JSON.readTree(line));
        } catch (IOException gone) {
            return Optional.empty();
        }
    }
}
