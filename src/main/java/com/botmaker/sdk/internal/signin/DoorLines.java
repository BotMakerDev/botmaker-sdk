// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.signin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.Rectangle;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * What both loopback doors a {@link SignInClient} knocks on share, the SDK plugin's {@code SignInHost} (one sign-in
 * run) and {@code SignInDoor} (every run of this PC's core): one line of JSON in, one out, a random token, and a
 * field named by its place. No answer carries a value.
 */
public final class DoorLines {

    /** How long a connection may take to send its line. */
    public static final Duration READ_WAIT = Duration.ofSeconds(10);
    /** A call's line is a few hundred bytes: a longer one is refused unread, not buffered. */
    private static final int MAX_LINE = 4096;
    public static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private DoorLines() {}

    /** A new token: 32 random bytes in hex. */
    public static String token() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return HexFormat.of().formatHex(random);
    }

    /** Whether {@code given} is {@code token}, compared in constant time. */
    public static boolean tokenMatches(String token, String given) {
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** The connection's one line, {@link #MAX_LINE} bytes at most: none for a longer one, or one never ended. */
    public static Optional<String> line(Socket socket) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        InputStream in = new BufferedInputStream(socket.getInputStream());
        for (int b = in.read(); b != -1; b = in.read()) {
            if (b == '\n') return Optional.of(line.toString(StandardCharsets.UTF_8));
            if (line.size() >= MAX_LINE) return Optional.empty();
            line.write(b);
        }
        return Optional.empty();
    }

    /** Writes {@code answer} as the connection's one line back. */
    public static void answer(Socket socket, ObjectNode answer) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((JSON.writeValueAsString(answer) + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** The place a call's {@code field} names; none without a width and a height above zero. */
    public static Optional<Rectangle> place(JsonNode field) {
        if (!field.path("width").isInt() || !field.path("height").isInt() || field.path("width").asInt() <= 0
                || field.path("height").asInt() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Rectangle(field.path("x").asInt(), field.path("y").asInt(), field.path("width").asInt(),
                field.path("height").asInt()));
    }

    public static ObjectNode ok() {
        return JSON.createObjectNode().put("ok", true);
    }

    public static ObjectNode refused(String why) {
        return JSON.createObjectNode().put("ok", false).put("why", why);
    }
}
