package com.botmaker.sdk.internal.signin;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.signin.NeedsYou;
import com.botmaker.sdk.api.signin.Refusal;
import com.botmaker.sdk.api.signin.SecondStep;
import com.botmaker.sdk.api.signin.SignIn;
import com.botmaker.sdk.api.signin.Typed;
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
        message.putObject("field").put("x", field.x()).put("y", field.y())
                .put("width", field.width()).put("height", field.height());
        return call(message, value == Value.CODE ? CODE_WAIT : ANSWER_WAIT)
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
