package com.botmaker.sdk.plugin.assist;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.assist.AgentContext;
import com.botmaker.plugin.api.assist.AgentReply;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.flow.BotConstants;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.types.ScreenTypes;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * The map edited by hand from 🗺 Screens' side panel: what the assistant's tools record by trying, set by the user
 * instead (the user's call, 2026-10-15): an input's result, kind, never, lock or coming and going, an input removed or
 * made the way back, a screen renamed, made a popup, taken back, deleted or given a new shot, pictures the bot has put
 * on a screen, and a screen added under another without trying its way in. Each answers in a sentence what it wrote,
 * or why it did not. On the FX thread, except {@link #retakeShot}, which reads the game.
 */
public final class ScreenEdits {

    private ScreenEdits() {}

    /**
     * {@code input} as it was cut, untried: its result, never and lock cleared, its kind, variants and coming and
     * going kept.
     */
    public static Input untried(Input input) {
        return new Input(input.act(), input.picture(), input.key(), input.notches(), null, input.kind(), null,
                input.comesAndGoes(), null, input.variants(), input.when());
    }

    /** {@code input} shown only at times, or always ({@code sometimes} false: when it is there goes too). */
    public static Input sometimes(Input input, boolean sometimes) {
        return new Input(input.act(), input.picture(), input.key(), input.notches(), input.did(), input.kind(),
                input.never(), sometimes, input.lock(), input.variants(), sometimes ? input.when() : null);
    }

    /** {@code input}, with no kind named. */
    public static Input unkinded(Input input) {
        return new Input(input.act(), input.picture(), input.key(), input.notches(), input.did(), null,
                input.never(), input.comesAndGoes(), input.lock(), input.variants(), input.when());
    }

    /** Puts {@code now} where {@code old} is on {@code constant}; {@code now} null removes it. */
    public static String setInput(StudioServices services, String constant, Input old, Input now) {
        return said(ScreenBook.update(services, constant, s -> MapTools.replaced(s, old, now)),
                now == null ? old.label() + " is off " + constant + "." : "Noted: " + now.label() + ".");
    }

    /** Makes {@code input} {@code constant}'s way back, off its inputs. */
    public static String useAsBack(StudioServices services, String constant, Input input) {
        Input back = untried(input);
        return said(ScreenBook.update(services, constant, s -> MapTools.replaced(s, input, null).back(back)),
                input.label() + " is the way back.");
    }

    /** Gives {@code constant} the name the game shows, {@code name}; its constant stays. */
    public static String rename(StudioServices services, String constant, String name) {
        if (name == null || !MapTools.isName(name.strip())) return "Name it in plain words, on one line.";
        String typed = name.strip();
        return said(ScreenBook.update(services, constant, s -> s.name(typed)), "Renamed " + typed + ".");
    }

    /** Makes {@code constant} a popup, or a screen of the tree again ({@code popup} false). */
    public static String setPopup(StudioServices services, String constant, boolean popup) {
        return said(ScreenBook.update(services, constant, s -> popup ? s.popup() : s.notPopup()),
                popup ? "A popup now." : "No longer a popup.");
    }

    /** Puts {@code notes} on {@code constant}, over what it had; blank removes them. */
    public static String setNotes(StudioServices services, String constant, String notes) {
        return said(ScreenBook.update(services, constant, s -> s.notes(notes == null ? null : notes.strip())),
                notes == null || notes.isBlank() ? "Notes removed." : "Notes saved.");
    }

    /** not_a_screen: {@code constant} off the map, its way in noted on its parent as changing it in place. */
    public static String notAScreen(AgentContext context, String constant) {
        return text(MapTools.notAScreen(new MapTools.Mistake(constant), context));
    }

    /** Deletes {@code constant} and its shot; refused while another screen is reached from it. */
    public static String delete(StudioServices services, String constant) {
        ScreenGraph graph = ScreenBook.read(services);
        List<String> after = graph.screens().keySet().stream()
                .filter(c -> !c.equals(constant) && graph.dependsOn(c).contains(constant)).map(graph::shown).toList();
        if (!after.isEmpty()) return String.join(", ", after) + " is reached from it: delete those first.";
        Optional<String> failed = ScreenBook.remove(services, constant);
        if (failed.isEmpty()) ScreenShots.remove(services.resourcesDir(), constant);
        return said(failed, "Deleted " + graph.shown(constant) + ".");
    }

    /** {@code constant}'s whole-screen shot taken again from the game now. Off the FX thread: it reads the game. */
    public static String retakeShot(AgentContext context, String constant) {
        Optional<BufferedImage> frame = Frames.look(context);
        if (frame.isEmpty()) return Frames.noScreen();
        ScreenShots.save(context.services().resourcesDir(), constant, frame.get());
        return "Shot taken.";
    }

    /** place_pictures: pictures the bot has, put on {@code screen} as inputs, its marker or its way back. */
    public static String place(AgentContext context, String screen, List<String> pictures, String marker,
                               String back) {
        return text(MapTools.placePictures(new MapTools.Places(screen, pictures, marker, back, null, null), context));
    }

    /**
     * Adds a screen the game calls {@code name} under {@code parent}, opened by {@code via}, without trying it: the
     * input is put on {@code parent} too when it is not there.
     */
    public static String newScreen(StudioServices services, String parent, String name, Input via) {
        if (name == null || !MapTools.isName(name.strip())) return "Name it in plain words, on one line.";
        ScreenGraph graph = ScreenBook.read(services);
        if (ScreenBook.find(graph, name.strip()).isPresent()) return "The map has " + name.strip() + " already.";
        String constant = ScreenBook.constantFor(graph, name.strip());
        Input entrance = untried(via);
        GameScreen made = GameScreen.named(name.strip()).from(Way.of(BotConstants.screen(parent), entrance));
        GameScreen on = graph.screens().get(parent);
        Optional<Input> had = on == null ? Optional.empty()
                : on.inputs().stream().filter(i -> i.sameAs(via)).findFirst();
        Optional<String> failed = ScreenBook.update(services, parent, s -> MapTools.replaced(s, had.orElse(null),
                        entrance))
                .or(() -> ScreenBook.add(services, constant, made));
        return said(failed, "Added " + name.strip() + " as Screens." + constant + ": it owes its marker.");
    }

    /**
     * Makes {@code to}'s way in from {@code from} by {@code via} open only while {@code method} answers true — a
     * method reference, {@code Firestone::eventOn} — or always, {@code method} blank.
     */
    public static String setWayWhen(StudioServices services, String to, String from, Input via, String method) {
        BooleanSupplier when = ScreenTypes.condition(method);
        ScreenGraph graph = ScreenBook.read(services);
        return said(ScreenBook.update(services, to, s -> withWayWhen(graph, s, from, via, when)),
                when == null ? "That way is always open now." : "That way is open only while " + method.strip() + ".");
    }

    /** {@code screen} with its way in from {@code from} by {@code via} taken only while {@code when}; null: always. */
    static GameScreen withWayWhen(ScreenGraph graph, GameScreen screen, String from, Input via, BooleanSupplier when) {
        GameScreen now = screen.from() != null && wayFrom(graph, screen.from(), from, via)
                ? screen.from(screen.from().when(when)) : screen;
        return now.alsoFrom(now.alsoFrom().stream()
                .map(way -> wayFrom(graph, way, from, via) ? way.when(when) : way).toList());
    }

    private static boolean wayFrom(ScreenGraph graph, Way way, String from, Input via) {
        return way.via() != null && way.via().sameAs(via) && graph.constantOf(way.from()).filter(from::equals)
                .isPresent();
    }

    /** {@code input} of {@code constant} there only while {@code method} answers true; blank: no method says. */
    public static String setInputWhen(StudioServices services, String constant, Input input, String method) {
        BooleanSupplier when = ScreenTypes.condition(method);
        Input now = input.when(when);
        return said(ScreenBook.update(services, constant, s -> MapTools.replaced(s, input, now)),
                when == null ? "No method says when " + input.label() + " is there."
                        : input.label() + " is there only while " + method.strip() + ".");
    }

    /** The bot's picture {@code name}, as its constant holds it. */
    public static ImageTemplate picture(StudioServices services, String name) {
        return ScreenBook.picture(services, name);
    }

    /** Every picture the bot has, by name. */
    public static List<String> pictures(StudioServices services) {
        Path resources = services.resourcesDir();
        if (resources == null) return List.of();
        List<String> names = new ArrayList<>();
        for (Path file : TemplateLibrary.list(resources)) {
            if (!TemplateLibrary.isUnmodifiedDefaultTemplate(file)) names.add(TemplateLibrary.baseName(file));
        }
        names.sort(String::compareTo);
        return names;
    }

    private static String said(Optional<String> failed, String done) {
        return failed.map(why -> "Not written: " + why).orElse(done);
    }

    private static String text(AgentReply reply) {
        return reply.parts().stream().filter(p -> p.kind() == AgentReply.Kind.TEXT).map(AgentReply.Part::text)
                .collect(Collectors.joining("\n")).strip();
    }
}
