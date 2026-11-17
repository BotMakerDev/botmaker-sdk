// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.signin;

import com.botmaker.sdk.api.screens.Answer;
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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code Accounts.signedIn(Login::email)}'s answer: the door's yes or no, unknown whenever it cannot ask. */
class SignedInCheckTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Optional<String> EMAIL = Optional.of("farm@example.com");
    private static final Optional<String> INSTANCE = Optional.of("waydroid");

    private final List<JsonNode> heard = new CopyOnWriteArrayList<>();
    private ServerSocket server;

    /** A door that answers every call with {@code answer}. */
    private Optional<SignInClient> door(String answer) throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        Thread serving = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    heard.add(JSON.readTree(new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine()));
                    socket.getOutputStream().write((answer + "\n").getBytes(StandardCharsets.UTF_8));
                } catch (IOException closed) {
                    return;
                }
            }
        });
        serving.setDaemon(true);
        serving.start();
        return Optional.of(new SignInClient(server.getLocalPort(), "t"));
    }

    @AfterEach
    void stop() throws IOException {
        if (server != null) server.close();
    }

    @Test
    void yesOrNoAsTheDoorSays() throws IOException {
        Optional<SignInClient> door = door("{\"ok\":true,\"signedIn\":true}");
        assertEquals(Answer.YES, SignedInCheck.answer(EMAIL, door, INSTANCE));
        assertEquals("signed-in", heard.getFirst().path("op").asText());
        assertEquals("farm@example.com", heard.getFirst().path("email").asText());
        assertEquals("waydroid", heard.getFirst().path("instance").asText());
    }

    @Test
    void noWhenTheInstanceLacksTheAccount() throws IOException {
        assertEquals(Answer.NO, SignedInCheck.answer(EMAIL, door("{\"ok\":true,\"signedIn\":false}"), INSTANCE));
    }

    @Test
    void unknownWhenItCannotAsk() throws IOException {
        assertEquals(Answer.UNKNOWN, SignedInCheck.answer(EMAIL, Optional.empty(), INSTANCE), "no door");
        assertEquals(Answer.UNKNOWN, SignedInCheck.answer(Optional.empty(), door("{\"ok\":true,\"signedIn\":true}"),
                INSTANCE), "no address dealt");
        assertEquals(Answer.UNKNOWN, SignedInCheck.answer(EMAIL, door("{\"ok\":true}"), Optional.empty()),
                "no instance");
        assertEquals(List.of(), heard, "nothing asked without all three");
    }

    @Test
    void unknownWhenTheDoorCannotSay() throws IOException {
        assertEquals(Answer.UNKNOWN, SignedInCheck.answer(EMAIL,
                door("{\"ok\":false,\"why\":\"the instance did not answer\"}"), INSTANCE));
    }

    @Test
    void unknownWhenTheDoorIsGone() throws IOException {
        Optional<SignInClient> door = door("{}");
        server.close();
        assertEquals(Answer.UNKNOWN, SignedInCheck.answer(EMAIL, door, INSTANCE));
    }
}
