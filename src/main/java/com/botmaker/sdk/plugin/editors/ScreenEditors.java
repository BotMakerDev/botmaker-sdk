package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.screens.GameScreen;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Way;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A screen of the bot's map in a block, {@code Navigate.to(Screens.CAMPAIGN)} (2026-10-14): a button showing the
 * screen picked, its marker beside its name, which lists the map's screens to pick from. The pick is written as the
 * {@code Screens} constant, never spelled out. Popups are offered only to {@code Navigate.isOn}: nothing goes to one.
 *
 * <p>{@code GameScreen} has no fresh value, as {@code Flow} has none: a dropped block holds {@code null} until a
 * screen is picked, which {@code Navigate} reads as no screen.
 */
public final class ScreenEditors {

    private static final ManagedSet<GameScreen> SCREENS = ManagedSet.of(SdkValues.SCREEN_MAP);
    /** The side of a marker's thumbnail, in the button and in the list. */
    private static final double THUMB = 22;
    /** The button's text while no screen is picked. */
    static final String PROMPT = "Pick a screen";
    /** The list's only entry while the bot has no map. */
    static final String NO_MAP = "No screens yet: map them in 🗺 Screens";

    private ScreenEditors() {}

    /** One screen the picker offers: its constant, the name the game shows, its marker picture's name or null. */
    record Choice(String constant, String name, String marker) {}

    /**
     * Whether {@code ctx} is a screen in a block: a {@code GameScreen} argument of a call. The map's own, the screen
     * of a {@code Way.of(HOME, …)} in {@code Screens.java}, is left to the map: a pick there could name a screen
     * declared after it.
     */
    public static boolean isScreen(ValueContext ctx) {
        return ctx.type().is(GameScreen.class) && ctx.slot().flatMap(SlotContext::enclosingExecutable)
                .filter(call -> call.getDeclaringClass() != Way.class).isPresent();
    }

    /** Whether {@code ctx} is the screen of {@code Navigate.isOn}, the one call a popup is a fair answer to. */
    private static boolean takesPopups(ValueContext ctx) {
        return ctx.slot().flatMap(SlotContext::enclosingExecutable)
                .filter(call -> call.getDeclaringClass() == Navigate.class && call.getName().equals("isOn"))
                .isPresent();
    }

    /** The screens of {@code graph} a block can name, in the map's order: the popups only when {@code popups}. */
    static List<Choice> choices(ScreenGraph graph, boolean popups) {
        List<Choice> choices = new ArrayList<>();
        graph.screens().forEach((constant, screen) -> {
            if (popups || !screen.isPopup()) {
                choices.add(new Choice(constant, graph.shown(constant),
                        screen.hasMarker() ? screen.firstMarker().id() : null));
            }
        });
        return choices;
    }

    /** The bot's map as it is now; empty when there is none or it will not read. */
    private static ScreenGraph graph(PluginValues values) {
        // A map that will not read leaves the picker empty, never the block undrawn.
        return ScreenMapRead.orEmpty(values);
    }

    /** The picker. The map is read again each time the list opens: a screen mapped since then is offered. */
    public static Node screen(ValueContext ctx) {
        StudioServices services = ctx.services();
        Path resources = services.resourcesDir();
        Button button = Styles.on(new Button(), Styles.INSET_FIELD_FLAT);
        // What the button shows: the screen picked, by the name the game shows and its marker, or the prompt.
        Consumer<Optional<Choice>> show = picked -> {
            button.setText(picked.map(choice -> choice.name() + "  ▾").orElse(PROMPT));
            button.setGraphic(picked.map(choice -> thumb(resources, choice.marker())).orElse(null));
        };
        boolean popups = takesPopups(ctx);
        // A constant the map does not list (it will not read, or the screen is gone) still shows by its name: the
        // Java names it, and a prompt would invite a pick over it.
        Optional<String> current = SCREENS.picked(ctx).map(Member::name);
        show.accept(current.map(constant -> choices(graph(services.pluginValues()), true).stream()
                .filter(choice -> choice.constant().equals(constant)).findFirst()
                .orElse(new Choice(constant, constant, null))));
        button.setOnAction(e -> {
            ContextMenu menu = new ContextMenu();
            List<Choice> choices = choices(graph(services.pluginValues()), popups);
            if (choices.isEmpty()) {
                MenuItem none = new MenuItem(NO_MAP);
                none.setDisable(true);
                menu.getItems().add(none);
            }
            for (Choice choice : choices) {
                MenuItem item = new MenuItem(choice.name(), thumb(resources, choice.marker()));
                item.setOnAction(a -> {
                    Optional<String> refused = SCREENS.pick(ctx, Member.of(choice.constant()));
                    refused.ifPresent(services::status);
                    if (refused.isEmpty()) show.accept(Optional.of(choice));
                });
                menu.getItems().add(item);
            }
            menu.show(button, Side.BOTTOM, 0, 0);
        });
        return button;
    }

    /** The marker {@code picture} as a small image; null when the screen has none or its file is missing. */
    private static Node thumb(Path resources, String picture) {
        if (resources == null || picture == null) return null;
        Path file = TemplateLibrary.fileForName(resources, picture);
        if (!Files.isRegularFile(file)) return null;
        // Loaded in the background: a long map's list opens at once, each marker drawn as it arrives.
        ImageView view = new ImageView(new Image(file.toUri().toString(), THUMB, THUMB, true, true, true));
        view.setPreserveRatio(true);
        return view;
    }
}
