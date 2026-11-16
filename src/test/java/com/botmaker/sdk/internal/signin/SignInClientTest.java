// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.signin;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.signin.NeedsYou;
import com.botmaker.sdk.api.signin.SecondStep;
import com.botmaker.sdk.api.signin.SignIn;
import com.botmaker.sdk.api.signin.Typed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignInClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "t-123";

    private final List<JsonNode> heard = new CopyOnWriteArrayList<>();
    private ServerSocket server;
    private Thread serving;

    /** A host that refuses a wrong token and answers each op by {@code answers}. */
    private void host(Function<JsonNode, String> answers) throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        serving = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    JsonNode message = JSON.readTree(new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine());
                    heard.add(message);
                    String answer = TOKEN.equals(message.path("token").asText())
                            ? answers.apply(message) : "{\"ok\":false,\"why\":\"wrong token\"}";
                    socket.getOutputStream().write((answer + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (IOException closed) {
                    return;
                }
            }
        });
        serving.setDaemon(true);
        serving.start();
    }

    @AfterEach
    void stop() throws IOException {
        if (server != null) server.close();
    }

    @Test
    void readsTheRequest() throws IOException {
        host(message -> "{\"ok\":true,\"id\":\"r1\",\"method\":\"phone\"}");
        Optional<SignIn.Request> request = new SignInClient(server.getLocalPort(), TOKEN).request();
        assertEquals(Optional.of(new SignIn.Request("r1", SecondStep.PHONE)), request);
        assertEquals("request", heard.getFirst().path("op").asText());
    }

    @Test
    void asksToTypeByTheFieldsPlace() throws IOException {
        host(message -> "{\"ok\":true}");
        Typed typed = new SignInClient(server.getLocalPort(), TOKEN)
                .type(SignInClient.Value.PASSWORD, new Rect(10, 20, 300, 40));
        assertTrue(typed.isTyped());
        JsonNode message = heard.getFirst();
        assertEquals("type", message.path("op").asText());
        assertEquals("password", message.path("value").asText());
        assertEquals(10, message.path("field").path("x").asInt());
        assertEquals(40, message.path("field").path("height").asInt());
    }

    @Test
    void aRefusalSaysWhy() throws IOException {
        host(message -> "{\"ok\":false,\"why\":\"no password saved\"}");
        Typed typed = new SignInClient(server.getLocalPort(), TOKEN).type(SignInClient.Value.CODE, new Rect(0, 0, 1, 1));
        assertEquals(Typed.refused("no password saved"), typed);
    }

    @Test
    void aWrongTokenIsRefused() throws IOException {
        host(message -> "{\"ok\":true,\"signedIn\":true}");
        SignInClient client = new SignInClient(server.getLocalPort(), "wrong");
        assertFalse(client.signedIn());
        assertEquals(Optional.empty(), client.request());
        assertFalse(client.type(SignInClient.Value.EMAIL, new Rect(0, 0, 1, 1)).isTyped());
    }

    @Test
    void asksSignedInAndNeedsYou() throws IOException {
        host(message -> "{\"ok\":true,\"signedIn\":true}");
        SignInClient client = new SignInClient(server.getLocalPort(), TOKEN);
        assertTrue(client.signedIn());
        client.needsYou(NeedsYou.CAPTCHA);
        assertEquals("needs-you", heard.get(1).path("op").asText());
        assertEquals("captcha", heard.get(1).path("kind").asText());
    }

    @Test
    void aGoneHostRefuses() throws IOException {
        host(message -> "{\"ok\":true}");
        int port = server.getLocalPort();
        server.close();
        SignInClient client = new SignInClient(port, TOKEN);
        assertFalse(client.type(SignInClient.Value.EMAIL, new Rect(0, 0, 1, 1)).isTyped());
        assertFalse(client.signedIn());
    }

    @Test
    void aRefusalWithoutWhyStillSaysSomething() throws IOException {
        host(message -> "{\"ok\":false}");
        Typed typed = new SignInClient(server.getLocalPort(), TOKEN).type(SignInClient.Value.EMAIL, new Rect(0, 0, 1, 1));
        assertFalse(typed.isTyped());
        assertFalse(typed.why().isBlank());
    }

    @Test
    void aRequestWithoutIdIsNone() throws IOException {
        host(message -> "{\"ok\":true,\"method\":\"phone\"}");
        assertEquals(Optional.empty(), new SignInClient(server.getLocalPort(), TOKEN).request());
    }

    @Test
    void aRunWithoutTheVariablesIsNoSignIn() {
        assertEquals(Optional.empty(), SignInClient.of(name -> null));
        assertEquals(Optional.empty(), SignInClient.of(Map.of(SignInClient.TOKEN_VARIABLE, TOKEN)::get));
        for (String port : List.of("not a port", "0", "-1", "70000")) {
            assertEquals(Optional.empty(), SignInClient.of(Map.of(SignInClient.PORT_VARIABLE, port,
                    SignInClient.TOKEN_VARIABLE, TOKEN)::get), port);
        }
    }

    @Test
    void theRunsVariablesNameItsHost() throws IOException {
        host(message -> "{\"ok\":true,\"id\":\"r2\",\"method\":\"authenticator\"}");
        Optional<SignInClient> client = SignInClient.of(Map.of(SignInClient.PORT_VARIABLE,
                Integer.toString(server.getLocalPort()), SignInClient.TOKEN_VARIABLE, TOKEN)::get);
        assertEquals(Optional.of(new SignIn.Request("r2", SecondStep.AUTHENTICATOR)),
                client.flatMap(SignInClient::request));
    }
}
