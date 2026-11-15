package com.botmaker.sdk.api.signin;

import com.botmaker.plugin.api.palette.Palette;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Element;
import com.botmaker.sdk.internal.screens.ElementMatch;
import com.botmaker.sdk.internal.screens.RunReach;
import com.botmaker.sdk.internal.signin.SignInClient;
import com.botmaker.shared.emulator.tree.UiNode;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

/**
 * A sign-in run's door to its host (spec {@code docs/superpowers/specs/2026-11-15-google-sign-in-bot-design.md}
 * §2): the run never holds an account, a password or a code. It names the field, the host taps it and types the
 * value over its own connection to the device, and the run hears back only {@link Typed}.
 *
 * <pre>{@code
 * Typed typed = SignIn.typePassword(Elements.PASSWORD);
 * if (!typed.isTyped()) Bot.stop(typed.why());
 * }</pre>
 *
 * <p>A run the host did not start as a sign-in has no {@link #request()}, and every call refuses.
 */
@Palette(category = "signin", categoryLabel = "Sign-in", icon = "🔑")
public final class SignIn {

    private SignIn() {}

    /**
     * The sign-in this run was started for: an opaque id and the second-step method the account's owner chose.
     *
     * @param id     the host's id of this sign-in, which says nothing of the account
     * @param method how the account answers Google's second step
     */
    public record Request(String id, SecondStep method) {}

    /** This run's sign-in, or none when the host did not start the run as one. */
    public static Optional<Request> request() {
        return SignInClient.ofRun().flatMap(SignInClient::request);
    }

    /** Asks the host to tap {@code field} and type the account's address. */
    public static Typed typeEmail(Element field) {
        return type(SignInClient.Value.EMAIL, field);
    }

    /** Asks the host to tap {@code field} and type the account's password. */
    public static Typed typePassword(Element field) {
        return type(SignInClient.Value.PASSWORD, field);
    }

    /**
     * Asks the host to tap {@code field} and type the second step's code: made from the key it holds, or asked of the
     * user in BotMaker's prompt. It waits while the user types it.
     */
    public static Typed typeCode(Element field) {
        return type(SignInClient.Value.CODE, field);
    }

    /** Whether the host sees the request's account on the device; {@code false} when it cannot say. */
    public static boolean signedIn() {
        return SignInClient.ofRun().map(SignInClient::signedIn).orElse(false);
    }

    /** Asks the user, through the host, for what only they can do; it returns at once, the run reading on. */
    public static void needsYou(NeedsYou kind) {
        SignInClient.ofRun().ifPresent(client -> client.needsYou(kind));
    }

    /**
     * Tells the host why Google ended the sign-in, so the account is dealt with as that end asks (a refused password
     * is not typed again); then stop the run with {@code why}'s words.
     */
    public static void refused(Refusal why) {
        SignInClient.ofRun().ifPresent(client -> client.refused(why));
    }

    private static Typed type(SignInClient.Value value, Element field) {
        Optional<SignInClient> client = SignInClient.ofRun();
        if (client.isEmpty()) return Typed.refused("this run is not a sign-in");
        RunReach reach = new RunReach();
        Optional<BufferedImage> frame = reach.look();
        Optional<ElementMatch.Matcher> tree = frame.flatMap(reach::treeOn);
        String name = ElementMatch.named(field);
        if (tree.isEmpty()) return Typed.refused(name + ": this target shows no tree; a field is found only in an app's");
        // a secret typed into whichever of two fields came first may land where it shows: one field, or nothing typed
        List<UiNode> nodes = tree.get().matches(field);
        if (nodes.isEmpty()) return Typed.refused(name + " is not on the screen");
        if (nodes.size() > 1) return Typed.refused(name + ": " + nodes.size() + " fields match; pick it again in 🗺 Screens");
        Rect at = ElementMatch.place(nodes.getFirst());
        return client.get().type(value, at);
    }
}
