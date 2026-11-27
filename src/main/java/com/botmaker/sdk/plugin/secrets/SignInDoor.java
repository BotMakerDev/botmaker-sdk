// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.sdk.plugin.secrets;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.sdk.api.secrets.Secret;
import com.botmaker.sdk.api.signin.NeedsYou;
import com.botmaker.sdk.api.signin.Refusal;
import com.botmaker.sdk.internal.signin.DoorLines;
import com.botmaker.sdk.internal.signin.SignInClient;
import com.botmaker.sdk.plugin.SdkLog;
import com.botmaker.sdk.plugin.emulator.host.HostSecrets;
import com.botmaker.sdk.plugin.emulator.host.KeptSecrets;
import com.botmaker.sdk.plugin.emulator.host.SecretKind;
import com.botmaker.shared.emulator.EmulatorInstance;
import com.botmaker.shared.emulator.Platforms;
import com.botmaker.shared.emulator.google.AccountDevice;
import com.botmaker.shared.emulator.google.InstanceSignIn;
import com.botmaker.shared.emulator.google.PasswordStore;
import com.botmaker.shared.emulator.google.Totp;
import com.botmaker.shared.emulator.tree.Trait;
import com.botmaker.shared.emulator.tree.UiTree;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.Rectangle;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * This PC's sign-in door (2026-11-17, spec {@code 2026-11-17-google-sign-in-map-design.md} §3): the loopback server
 * the core opens once, which any run's {@link SignInClient#door()} finds by {@link SignInClient#doorFile()}. A run
 * names a {@link Secret}, a field's place and its instance; the door reads the value from this PC's credential store,
 * taps the field on that instance and types it (a password as kept, an authenticator's current code). No answer and
 * no log line carries a value: a step says a length only.
 *
 * <p>The checks keep an honest bot from typing a secret where it would show: the secret is kept on this PC, the
 * instance is one running here, and a password goes only into a focused password field. The wall is elsewhere: a
 * secret never enters a run.
 */
public final class SignInDoor implements AutoCloseable {

    /** How many times the screen is read for the focused field after the tap, and how long between. */
    private static final int FOCUS_READS = 3;
    private static final Duration FOCUS_PAUSE = Duration.ofMillis(300);
    /** How old a scan of this PC's instances may be when a call names one. */
    private static final Duration SCAN_AGE = Duration.ofSeconds(5);

    /** Opens the instance a run names, by its reference or its name; empty when none such runs on this PC. */
    @FunctionalInterface
    public interface DeviceFinder {
        Optional<AccountDevice> open(String instance);
    }

    private static SignInDoor serving;

    /** The open project's index of this PC's secrets, the latest one {@link #serve} was handed. */
    private volatile KeptSecrets.Index index;
    private final PasswordStore store;
    private final DeviceFinder devices;
    private final Path doorFile;
    private final Consumer<String> log;
    private final LongSupplier clock;
    private final String token = DoorLines.token();
    private final ServerSocket server;
    private final Thread thread;

    private SignInDoor(KeptSecrets.Index index, PasswordStore store, DeviceFinder devices, Path doorFile,
                       Consumer<String> log, LongSupplier clock) throws IOException {
        this.index = index;
        this.store = store;
        this.devices = devices;
        this.doorFile = doorFile;
        this.log = log;
        this.clock = clock;
        this.server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        try {
            writeDoorFile();
        } catch (IOException | RuntimeException e) {
            server.close();
            throw e;
        }
        this.thread = Thread.ofPlatform().daemon().name("sign-in door").unstarted(this::serve);
        thread.start();
    }

    /**
     * Opens a door serving {@code index}'s secrets, whose values {@code store} keeps, on the instances {@code devices}
     * opens, and writes {@code doorFile} for runs to find it. Each step goes to {@code log}, never a value.
     */
    public static SignInDoor open(KeptSecrets.Index index, PasswordStore store, DeviceFinder devices, Path doorFile,
                                  Consumer<String> log, LongSupplier clock) throws IOException {
        return new SignInDoor(index, store, devices, doorFile, log, clock);
    }

    /** The instances running on this PC, matched as a run's capture source matches them: by reference, then by name. */
    public static DeviceFinder running() {
        return instance -> {
            List<EmulatorInstance> all = Platforms.recent(SCAN_AGE);
            return all.stream().filter(each -> instance.equals(each.reference())).findFirst()
                    .or(() -> all.stream().filter(each -> instance.equals(each.name())).findFirst())
                    .filter(EmulatorInstance::reachable)
                    .map(AccountDevice::of);
        };
    }

    /**
     * Opens this PC's door, once per process and until it exits, which every run of this PC reaches whatever project
     * is open: a side run outlives the project that started it. A later project only hands it its secrets' index. A
     * door that does not open is said, and runs' secrets are refused with words.
     */
    public static synchronized void serve(StudioServices services) {
        if (serving != null) {
            serving.index = KeptSecrets.index(services);
            return;
        }
        try {
            SignInDoor door = open(KeptSecrets.index(services), KeptSecrets.store(), running(),
                    SignInClient.doorFile(), SdkLog::info, System::currentTimeMillis);
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(door::close));
            serving = door;
        } catch (IOException | RuntimeException e) {
            SdkLog.warn("BotMaker can't type runs' secrets: its sign-in door did not open (" + e.getMessage() + ").");
        }
    }

    /** The port the door listens on. */
    int port() {
        return server.getLocalPort();
    }

    /** Stops serving and deletes the door file, unless another door wrote its own since. */
    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException alreadyGone) {
            // nothing more to close
        }
        if (Thread.currentThread() != thread) {
            try {
                thread.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (Files.isRegularFile(doorFile) && Files.readString(doorFile).contains(token)) Files.delete(doorFile);
        } catch (IOException | RuntimeException leftBehind) {
            // a stale door file names a port nobody serves: a run's call is refused at once
        }
    }

    private void writeDoorFile() throws IOException {
        Files.createDirectories(doorFile.toAbsolutePath().getParent());
        Path written = doorFile.resolveSibling(doorFile.getFileName() + ".new");
        Files.deleteIfExists(written);
        try {
            Files.createFile(written, PosixFilePermissions.asFileAttribute(
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
        } catch (UnsupportedOperationException notPosix) {
            // Windows: the user's own cache folder is theirs alone
            Files.createFile(written);
        }
        ObjectNode door = DoorLines.JSON.createObjectNode().put("port", port()).put("token", token);
        Files.writeString(written, DoorLines.JSON.writeValueAsString(door), StandardCharsets.UTF_8);
        try {
            Files.move(written, doorFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(written, doorFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void serve() {
        while (!server.isClosed()) {
            // one call at a time: two runs never type at once
            try (Socket socket = server.accept()) {
                Optional<String> line = DoorLines.line(socket);
                DoorLines.answer(socket, line.isEmpty() ? DoorLines.refused("no message") : answer(line.get()));
            } catch (IOException gone) {
                // the run went away mid-call, or the door closed
            }
        }
    }

    ObjectNode answer(String line) {
        try {
            return answered(line);
        } catch (RuntimeException | Error e) {
            // the door serves on whatever one call met; its words may name a command, so they are not passed on
            return DoorLines.refused("the sign-in door failed on this call");
        }
    }

    private ObjectNode answered(String line) {
        JsonNode message;
        try {
            message = DoorLines.JSON.readTree(line);
        } catch (IOException notJson) {
            return DoorLines.refused("not a message");
        }
        if (!DoorLines.tokenMatches(token, message.path("token").asText(""))) return DoorLines.refused("wrong token");
        String instance = message.path("instance").asText("").strip();
        return switch (SignInClient.Op.fromId(message.path("op").asText(""))) {
            case TYPE -> type(Secret.of(message.path("secret").asText("")), message.path("field"), instance);
            case SIGNED_IN -> signedIn(message.path("email").asText(""), instance);
            case NEEDS_YOU -> {
                log.accept(on(instance) + "needs you: " + NeedsYou.fromId(message.path("kind").asText("")).displayName());
                yield DoorLines.ok();
            }
            case REFUSED -> {
                log.accept(on(instance) + "the sign-in ended: "
                        + Refusal.fromId(message.path("why").asText("")).displayName());
                yield DoorLines.ok();
            }
            case REQUEST -> DoorLines.refused("this run was not started as a sign-in");
            case UNKNOWN -> DoorLines.refused("not a sign-in call");
        };
    }

    private ObjectNode type(Secret secret, JsonNode field, String instance) {
        if (secret.isBlank()) return DoorLines.refused("the run named no secret");
        Optional<Rectangle> at = DoorLines.place(field);
        if (at.isEmpty()) return DoorLines.refused("the field has no place");
        if (instance.isEmpty()) {
            return DoorLines.refused("\"" + secret.name() + "\" is typed only into an emulator instance, and the run "
                    + "named none");
        }
        SecretKind kind;
        try {
            kind = kindOf(secret);
        } catch (IOException | RuntimeException unread) {
            return DoorLines.refused("this PC's secrets could not be read");
        }
        if (kind == null) return DoorLines.refused(notKept(secret));
        if (kind == SecretKind.UNKNOWN) {
            return DoorLines.refused("\"" + secret.name() + "\" is of a kind BotMaker doesn't type: add it again in "
                    + "the secret picker");
        }
        Optional<char[]> kept;
        try {
            kept = store.read(secret.key());
        } catch (RuntimeException locked) {
            kept = Optional.empty();
        }
        if (kept.isEmpty()) return DoorLines.refused(notKept(secret));
        char[] text = null;
        try {
            text = kind == SecretKind.AUTHENTICATOR ? code(kept.get()) : kept.get();
            if (text == null) {
                return DoorLines.refused("\"" + secret.name() + "\" holds no authenticator key: add it again in the "
                        + "secret picker");
            }
            if (!InstanceSignIn.typeable(text)) {
                return DoorLines.refused("BotMaker types only plain letters, digits and signs (ASCII): \""
                        + secret.name() + "\" has others");
            }
            return typeOn(instance, at.get(), secret, kind, text);
        } finally {
            Arrays.fill(kept.get(), '\0');
            if (text != null) Arrays.fill(text, '\0');
        }
    }

    private ObjectNode typeOn(String instance, Rectangle at, Secret secret, SecretKind kind, char[] text) {
        Optional<AccountDevice> found;
        try {
            found = devices.open(instance);
        } catch (RuntimeException | Error unreachable) {
            found = Optional.empty();
        }
        if (found.isEmpty()) return DoorLines.refused("the instance \"" + instance + "\" isn't running on this PC");
        try (AccountDevice device = found.get()) {
            device.tap(at);
            if (kind == SecretKind.PASSWORD && !passwordFocused(device)) {
                return DoorLines.refused("the focused field isn't a password field");
            }
            log.accept(on(instance) + "typing \"" + secret.name() + "\" (" + text.length + " characters)");
            device.type(text);
            return DoorLines.ok();
        } catch (RuntimeException | Error e) {
            // dadb surfaces a gone device as either; its words could hold the command, so they are not passed on
            return DoorLines.refused("the instance did not take the typing");
        }
    }

    /** Whether, once tapped, the focused field is a password field, so what is typed shows as dots. */
    private static boolean passwordFocused(AccountDevice device) {
        for (int read = 0; read < FOCUS_READS; read++) {
            Optional<UiTree> screen = device.screen();
            if (screen.isPresent() && screen.get().all().stream()
                    .anyMatch(node -> node.has(Trait.FOCUSED) && node.has(Trait.PASSWORD))) {
                return true;
            }
            if (read + 1 < FOCUS_READS) {
                try {
                    Thread.sleep(FOCUS_PAUSE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /** The current code {@code key} gives; null when it does not read as a setup key. */
    private char[] code(char[] key) {
        Optional<byte[]> decoded = Totp.decode(key);
        if (decoded.isEmpty()) return null;
        try {
            return Totp.code(decoded.get(), clock.getAsLong());
        } finally {
            Arrays.fill(decoded.get(), (byte) 0);
        }
    }

    /** {@code secret}'s kind as this PC's index lists it; null when it lists none. */
    private SecretKind kindOf(Secret secret) throws IOException {
        HostSecrets kept = index.read();
        return kept.list().stream().filter(row -> Secret.of(row.name()).equals(secret)).findFirst()
                .map(row -> SecretKind.fromId(row.kind())).orElse(null);
    }

    private ObjectNode signedIn(String email, String instance) {
        String address = email.strip().toLowerCase(Locale.ROOT);
        if (address.isEmpty()) return DoorLines.refused("the run named no account");
        Optional<AccountDevice> found;
        try {
            found = devices.open(instance);
        } catch (RuntimeException | Error unreachable) {
            found = Optional.empty();
        }
        if (found.isEmpty()) return DoorLines.refused("the instance \"" + instance + "\" isn't running on this PC");
        try (AccountDevice device = found.get()) {
            Set<String> accounts = device.accounts();
            if (accounts == null) return DoorLines.refused("Android did not say which accounts it holds");
            return DoorLines.ok().put("signedIn", accounts.contains(address));
        } catch (RuntimeException | Error e) {
            return DoorLines.refused("the instance did not answer");
        }
    }

    private static String notKept(Secret secret) {
        return "no secret \"" + secret.name() + "\" kept on this PC: add it in the secret picker";
    }

    private static String on(String instance) {
        return instance.isEmpty() ? "" : instance + ": ";
    }
}
