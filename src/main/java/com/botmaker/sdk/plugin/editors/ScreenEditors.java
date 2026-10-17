package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.screens.Navigate;
import com.botmaker.sdk.api.screens.Order;
import com.botmaker.sdk.api.screens.Pick;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A screen of the bot's map in a block, {@code Navigate.to(Screens.CAMPAIGN)} (2026-10-14): a button showing the
 * screen picked, its marker beside its name, which lists the map's screens to pick from. The pick is written as the
 * {@code Screens} constant, never spelled out. Popups are offered only to {@code Navigate.isOn}: nothing goes to one.
 *
 * <p>{@code Screen} has no fresh value, as {@code Flow} has none: a dropped block holds {@code null} until a screen
 * is picked, which {@code Navigate} reads as no screen.
 */
public final class ScreenEditors {

    private static final ManagedSet<Screen> SCREENS = ManagedSet.of(SdkValues.SCREEN_MAP);
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
     * Whether {@code ctx} is a screen in a block: a {@code Screen} argument of a call. The map names inputs, never a
     * screen, so none is the map's own.
     */
    public static boolean isScreen(ValueContext ctx) {
        return ctx.type().is(Screen.class) && ctx.slot().flatMap(SlotContext::enclosingExecutable).isPresent();
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

    // --- which copy: an order and a pick ----------------------------------------------------------------

    /** The pad's orders, row by row, and the arrow each is drawn as. */
    private static final List<Order> PAD = List.of(Order.NORTH_WEST, Order.NORTH, Order.NORTH_EAST, Order.WEST,
            Order.CENTER, Order.EAST, Order.SOUTH_WEST, Order.SOUTH, Order.SOUTH_EAST);
    private static final String ARROWS = "↖↑↗←⊙→↙↓↘";

    /** Whether {@code ctx} is an {@link Order}: which copy of a repeated button comes first. */
    public static boolean isOrder(ValueContext ctx) {
        return ctx.type().is(Order.class);
    }

    /** An order as a pad of arrows, the old direction pad (2026-10-17): the side or corner the first copy is at. */
    public static Node order(ValueContext ctx) {
        return pad(ctx.value(Order.class).orElse(null), ctx::set);
    }

    /** The nine orders as a pad, {@code current} held down; a press hands {@code onPick} its order. */
    static Node pad(Order current, Consumer<Order> onPick) {
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < PAD.size(); i++) {
            Order order = PAD.get(i);
            ToggleButton arrow = new ToggleButton(String.valueOf(ARROWS.charAt(i)));
            arrow.setToggleGroup(group);
            arrow.setSelected(order == current);
            arrow.setPrefSize(30, 30);
            arrow.setTooltip(new Tooltip(order.name().toLowerCase(Locale.ROOT).replace('_', '-') + " first"));
            arrow.setOnAction(e -> {
                arrow.setSelected(true);    // a toggle group must not be emptied by clicking the pick
                onPick.accept(order);
            });
            grid.add(arrow, i % 3, i / 3);
        }
        return grid;
    }

    /** Whether {@code ctx} is a {@link Pick}: which copies of a repeated button a press takes. */
    public static boolean isPick(ValueContext ctx) {
        return ctx.type().is(Pick.class);
    }

    /**
     * A pick: how many copies (the first, the nth, each, or until none is left), and along which order. Beside a
     * button the map shows once, only a line saying so: there is no copy to pick (the call's button read, when the
     * host can).
     */
    public static Node pick(ValueContext ctx) {
        Optional<com.botmaker.sdk.api.screens.Button> pressed = ctx.slot()
                .flatMap(slot -> slot.argumentValue(0, com.botmaker.sdk.api.screens.Button.class));
        if (pressed.filter(com.botmaker.sdk.api.screens.Button::cycles).isPresent()) {
            return untilLook(ctx, pressed.get().cycle());
        }
        if (pressed.filter(button -> !button.repeats()).isPresent()) {
            return new Label("Shown once on its screen: no copy to pick.");
        }
        Pick[] now = {ctx.value(Pick.class).orElse(Pick.first())};
        Spinner<Integer> nth = new Spinner<>(1, 50, Math.max(1, now[0].nth()));
        nth.setPrefWidth(70);
        Node[] pad = new Node[1];
        Consumer<Pick> write = pick -> {
            now[0] = pick;
            ctx.set(pick);
            nth.setVisible(pick.mode() == Pick.Mode.NTH);
            pad[0].setVisible(pick.mode() != Pick.Mode.UNTIL_NONE);
        };
        pad[0] = pad(now[0].order(), order -> write.accept(new Pick(now[0].mode(), now[0].nth(), order, now[0].look())));
        nth.valueProperty().addListener((o, was, n) -> write.accept(new Pick(Pick.Mode.NTH, n, now[0].order())));
        ToggleGroup modes = new ToggleGroup();
        HBox row = new HBox(2);
        for (Pick.Mode mode : Pick.Mode.values()) {
            // Until a look is a cycling button's, offered above.
            if (mode == Pick.Mode.UNTIL) continue;
            ToggleButton button = new ToggleButton(switch (mode) {
                case FIRST -> "First";
                case NTH -> "Nth";
                case EACH -> "Each";
                case UNTIL_NONE, UNTIL -> "Until none";
            });
            button.setToggleGroup(modes);
            button.setSelected(mode == now[0].mode());
            button.setOnAction(e -> {
                button.setSelected(true);
                write.accept(new Pick(mode, nth.getValue(), now[0].order()));
            });
            row.getChildren().add(button);
        }
        nth.setVisible(now[0].mode() == Pick.Mode.NTH);
        pad[0].setVisible(now[0].mode() != Pick.Mode.UNTIL_NONE);
        HBox top = new HBox(6, row, nth);
        top.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, top, pad[0]);
    }

    /** A cycling button's pick: which of its {@code looks} it is pressed until it shows. */
    private static Node untilLook(ValueContext ctx, List<ImageTemplate> looks) {
        Pick now = ctx.value(Pick.class).orElse(Pick.first());
        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(2);
        for (ImageTemplate look : looks) {
            ToggleButton button = new ToggleButton(look.id());
            button.setToggleGroup(group);
            button.setSelected(now.look() != null && now.look().id().equals(look.id()));
            button.setOnAction(e -> {
                button.setSelected(true);
                ctx.set(Pick.until(look));
            });
            row.getChildren().add(button);
        }
        return new VBox(4, new Label("Pressed until it shows:"), row);
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
