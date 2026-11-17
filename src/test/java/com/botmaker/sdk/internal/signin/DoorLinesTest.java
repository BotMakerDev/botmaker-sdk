// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.internal.signin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorLinesTest {

    private static final Duration WAIT = Duration.ofMillis(600);

    @Test
    void readsOneWholeLine() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket caller = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
             Socket door = server.accept()) {
            caller.getOutputStream().write("{\"op\":\"type\"}\n".getBytes(StandardCharsets.UTF_8));
            assertEquals(Optional.of("{\"op\":\"type\"}"), DoorLines.line(door, WAIT));
        }
    }

    @Test
    void aCallerTricklingBytesIsCutOffAtTheLinesWait() throws IOException {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket caller = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
             Socket door = server.accept()) {
            Thread trickle = Thread.ofVirtual().start(() -> {
                try {
                    OutputStream out = caller.getOutputStream();
                    // each byte well inside the wait, the line never done within it
                    for (int i = 0; i < 40; i++) {
                        out.write('x');
                        out.flush();
                        Thread.sleep(100);
                    }
                } catch (IOException | InterruptedException stopped) {
                    // the door hung up
                }
            });
            long start = System.nanoTime();
            assertThrows(SocketTimeoutException.class, () -> DoorLines.line(door, WAIT));
            assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2_000, "cut off at the line's wait");
            trickle.interrupt();
        }
    }
}
