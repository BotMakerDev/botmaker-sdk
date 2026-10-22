package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.ManagedSet;
import com.botmaker.plugin.toolkit.Styles;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.geometry.Size;
import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Screen;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.bot.SdkValues;
import com.botmaker.sdk.internal.screens.MapScreen;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.assist.ScreenShots;
import com.botmaker.sdk.plugin.flow.ActivityScreens;
import com.botmaker.sdk.plugin.pictures.FrameCut;
import com.botmaker.sdk.plugin.pictures.ScreenMapRead;
import com.botmaker.sdk.plugin.pictures.TemplateLibrary;
import com.botmaker.sdk.plugin.pictures.TemplateUses;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * An input of the bot's map in a block, {@code Navigate.press(Screens.MARKET.beerBuy)} (2026-10-17): the screen
 * picked first, from the map's screens, then one of its inputs, on its shot with each boxed where its picture was cut,
 * or from the list beside it. Only the inputs the slot takes are offered: a {@code Button} slot shows the screen's
 * buttons, a strip's members under it, and no hotkey. The pick is written as the field, {@code Screens.MARKET.beerBuy},
 * never spelled out; nothing is read of the statements before the block, so an empty slot opens on the screen list.
 */
public final class InputPicker {

    private static final ManagedSet<Screen> SCREENS = ManagedSet.of(SdkValues.SCREEN_MAP);
    /** How wide a screen's shot is drawn in the picker. */
    private static final double SHOT = 480;
    /** The side of a picture's thumbnail in the list. */
    private static final double THUMB = 22;
    /** The button's text while no input is picked. */
    static final String PROMPT = "Pick an input";

    private InputPicker() {}

    /** Whether {@code ctx} is an input in a block: a {@code Button}, {@code Hotkey}, {@code Strip} or any input. */
    public static boolean isInput(ValueContext ctx) {
        return kind(ctx) != null && ctx.slot().flatMap(SlotContext::enclosingExecutable).isPresent();
    }

    /** The kind of input {@code ctx} takes; null for none. */
    private static Class<? extends Input> kind(ValueContext ctx) {
        for (Class<? extends Input> kind : List.of(Button.class, Hotkey.class, Strip.class, Input.class)) {
            if (ctx.type().is(kind)) return kind;
        }
        return null;
    }

    /** One input the picker offers: its field, what it is, and the strip it is a member of (null for none). */
    record Choice(String field, Input input, String strip) {}

    /**
     * The fields of {@code screen} a slot taking {@code kind} can name, in the screen's order, each strip's members
     * right after the strip: a strip in a {@code Button} slot is a heading, its members the choices.
     */
    static List<Choice> choices(MapScreen screen, Class<? extends Input> kind) {
        List<Choice> choices = new ArrayList<>();
        List<String> placed = new ArrayList<>();
        screen.inputs().forEach((field, input) -> {
            if (placed.contains(field)) return;
            if (input instanceof Strip strip) {
                if (kind.isInstance(strip) || kind == Button.class) {
                    choices.add(new Choice(field, strip, null));
                    placed.add(field);
                }
                screen.inputs().forEach((member, in) -> {
                    if (in instanceof Button b && strip.has(b.picture()) && kind.isInstance(b) && !placed.contains(member)) {
                        choices.add(new Choice(member, b, field));
                        placed.add(member);
                    }
                });
            } else if (kind.isInstance(input) && screen.stripOf(input).isEmpty()) {
                choices.add(new Choice(field, input, null));
                placed.add(field);
            }
        });
        return choices;
    }

    /** Whether {@code choice} can be written into a slot taking {@code kind}: a strip heading in a button slot is not. */
    static boolean pickable(Choice choice, Class<? extends Input> kind) {
        return kind.isInstance(choice.input());
    }

    /** The picker. The map is read again each time it opens: a screen or an input mapped since then is offered. */
    public static Node input(ValueContext ctx) {
        StudioServices services = ctx.services();
        Class<? extends Input> kind = kind(ctx);
        javafx.scene.control.Button button = Styles.on(new javafx.scene.control.Button(), Styles.INSET_FIELD_FLAT);
        Optional<Member> current = SCREENS.picked(ctx).filter(Member::grouped);
        button.setText(current.map(m -> label(graph(services), m) + "  ▾").orElse(PROMPT));
        button.setOnAction(e -> {
            ScreenGraph graph = graph(services);
            // Read again: a pick made since the picker was drawn opens on its screen.
            Optional<String> on = SCREENS.picked(ctx).filter(Member::grouped).map(Member::group)
                    .filter(graph.screens()::containsKey);
            if (on.isPresent()) showScreen(ctx, kind, graph, on.get(), button);
            else screenMenu(ctx, kind, graph, button);
        });
        return button;
    }

    /** {@code member} as the button shows it: the screen's name and the field. */
    private static String label(ScreenGraph graph, Member member) {
        return (graph.screens().containsKey(member.group()) ? graph.shown(member.group()) : member.group()) + " › "
                + member.name();
    }

    /** Step 1: the map's screens holding an input the slot takes. */
    private static void screenMenu(ValueContext ctx, Class<? extends Input> kind, ScreenGraph graph,
                                   javafx.scene.control.Button button) {
        ContextMenu menu = new ContextMenu();
        Path resources = ctx.services().resourcesDir();
        // In an activity's body, the screen its flow card says it works on comes first (.on(Screens.X)).
        Optional<String> first = activityScreen(ctx, graph);
        List<MenuItem> rest = new ArrayList<>();
        graph.screens().forEach((constant, screen) -> {
            if (choices(screen, kind).stream().noneMatch(c -> pickable(c, kind))) return;
            // Nothing walks to a popup: its inputs are offered only where any input is taken (a way out).
            if (screen.isPopup() && kind != Input.class) return;
            MenuItem item = new MenuItem(graph.shown(constant),
                    thumb(resources, screen.firstMarkerId()));
            item.setOnAction(a -> showScreen(ctx, kind, graph, constant, button));
            if (first.filter(constant::equals).isPresent()) menu.getItems().add(item);
            else rest.add(item);
        });
        if (!menu.getItems().isEmpty() && !rest.isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().addAll(rest);
        if (menu.getItems().isEmpty()) {
            MenuItem none = new MenuItem(ScreenEditors.NO_MAP);
            none.setDisable(true);
            menu.getItems().add(none);
        }
        menu.show(button, Side.BOTTOM, 0, 0);
    }

    /** Step 2: {@code constant}'s shot with its inputs boxed, and the list of them; a pick writes the field. */
    private static void showScreen(ValueContext ctx, Class<? extends Input> kind, ScreenGraph graph, String constant,
                                   javafx.scene.control.Button button) {
        StudioServices services = ctx.services();
        Path resources = services.resourcesDir();
        MapScreen screen = graph.screens().get(constant);
        List<Choice> choices = choices(screen, kind);
        Popup popup = new Popup();
        popup.setAutoHide(true);

        javafx.scene.control.Button screens = new javafx.scene.control.Button("‹ Screens");
        screens.setOnAction(e -> {
            popup.hide();
            screenMenu(ctx, kind, graph, button);
        });
        Label title = new Label(graph.shown(constant) + ": pick its " + kind.getSimpleName().toLowerCase());
        title.setStyle("-fx-font-weight: bold;");
        HBox head = new HBox(8, screens, title);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox list = new VBox(2);
        for (Choice choice : choices) {
            boolean pickable = pickable(choice, kind);
            javafx.scene.control.Button row = new javafx.scene.control.Button(rowText(choice, pickable),
                    choice.input() instanceof Button b ? thumb(resources, b.label()) : null);
            row.setMaxWidth(Double.MAX_VALUE);
            row.setAlignment(Pos.CENTER_LEFT);
            if (choice.strip() != null) VBox.setMargin(row, new Insets(0, 0, 0, 16));
            row.setDisable(!pickable);
            row.setOnAction(e -> pick(ctx, constant, choice.field(), popup, button, graph));
            list.getChildren().add(row);
        }

        VBox body = new VBox(6, head);
        shot(resources, constant, services.pluginValues(), choices, kind,
                choice -> pick(ctx, constant, choice.field(), popup, button, graph)).ifPresent(body.getChildren()::add);
        ScrollPane rows = new ScrollPane(list);
        rows.setFitToWidth(true);
        rows.setMaxHeight(240);
        body.getChildren().add(rows);
        body.setPadding(new Insets(8));
        body.setStyle("-fx-background-color: -fx-background; -fx-border-color: -fx-box-border;");
        body.setPrefWidth(SHOT + 16);
        popup.getContent().add(body);
        Bounds at = button.localToScreen(button.getBoundsInLocal());
        if (at != null) popup.show(button, at.getMinX(), at.getMaxY());
    }

    /** A row's text: the field, what it is, and what it is for. */
    private static String rowText(Choice choice, boolean pickable) {
        String what = switch (choice.input()) {
            case Button b -> (b.isDrag() ? "drag" : b.isField() ? "text field" : "button")
                    + (b.repeats() ? ", repeated" : "");
            case Hotkey h -> h.label();
            case TextButton t -> t.label();
            case Strip s -> pickable ? "strip of " + s.members().size() : "strip:";
            case Tree t -> pickable ? "tree of " + t.nodes().size() : "tree:";
        };
        return choice.field() + "  —  " + what + (choice.input().isInfo() ? ", info" : "");
    }

    private static void pick(ValueContext ctx, String constant, String field, Popup popup,
                             javafx.scene.control.Button button, ScreenGraph graph) {
        Member member = Member.of(constant, field);
        Optional<String> refused = SCREENS.pick(ctx, member);
        refused.ifPresent(ctx.services()::status);
        if (refused.isEmpty()) button.setText(label(graph, member) + "  ▾");
        popup.hide();
    }

    /** The screen's shot, each choice with a place boxed on it, a click on a box picking it; empty with no shot. */
    private static Optional<Node> shot(Path resources, String constant, PluginValues values, List<Choice> choices,
                                       Class<? extends Input> kind, Consumer<Choice> onPick) {
        if (resources == null) return Optional.empty();
        Path file = ScreenShots.file(resources, constant);
        if (!Files.isRegularFile(file)) return Optional.empty();
        Size size = ScreenShots.size(file);
        ImageView view = new ImageView(new Image(file.toUri().toString(), SHOT * 2, 0, true, true));
        view.setFitWidth(SHOT);
        view.setPreserveRatio(true);
        Pane pane = new Pane(view);
        Map<String, FrameCut> cuts = values == null ? Map.of() : TemplateUses.cuts(values);
        Map<Choice, Cut> boxes = new LinkedHashMap<>();
        for (Choice choice : choices) {
            if (pickable(choice, kind)) place(choice.input(), cuts, size).ifPresent(c -> boxes.put(choice, c));
        }
        boxes.forEach((choice, cut) -> {
            double scale = SHOT / cut.frameWidth();
            Rect r = cut.place();
            Rectangle box = new Rectangle(r.x() * scale, r.y() * scale, r.width() * scale, r.height() * scale);
            box.setStyle("-fx-fill: transparent; -fx-stroke: -fx-accent; -fx-stroke-width: 2; -fx-cursor: hand;");
            Tooltip.install(box, new Tooltip(choice.field()));
            box.setOnMouseClicked(e -> onPick.accept(choice));
            pane.getChildren().add(box);
        });
        return Optional.of(pane);
    }

    /** Where an input was cut, on a frame {@code frameWidth} wide: what scales its box onto the shot. */
    record Cut(Rect place, int frameWidth) {}

    /**
     * Where {@code input} was cut: a button's picture's place, a strip's the band its members' places span; empty for
     * a hotkey, for pictures cut nowhere known, and for members cut on frames of other widths, whose places do not
     * add up. A cut that names no frame is taken on the shot's.
     */
    static Optional<Cut> place(Input input, Map<String, FrameCut> cuts, Size shot) {
        List<ImageTemplate> pictures = switch (input) {
            case Button b -> b.picture() == null ? List.of() : List.of(b.picture());
            case Strip s -> s.members();
            case Tree t -> t.members();
            case Hotkey h -> List.of();
            case TextButton t -> List.of();
        };
        Rect band = null;
        int frameWidth = 0;
        for (ImageTemplate picture : pictures) {
            FrameCut cut = cuts.get(picture.id());
            if (cut == null || cut.place() == null) continue;
            int width = cut.frame() != null ? cut.frame().width() : shot == null ? 0 : shot.width();
            if (band != null && width != frameWidth) return Optional.empty();
            frameWidth = width;
            band = band == null ? cut.place() : span(band, cut.place());
        }
        return band == null || frameWidth <= 0 ? Optional.empty() : Optional.of(new Cut(band, frameWidth));
    }

    private static Rect span(Rect a, Rect b) {
        int x = Math.min(a.x(), b.x());
        int y = Math.min(a.y(), b.y());
        return new Rect(x, y, Math.max(a.x() + a.width(), b.x() + b.width()) - x,
                Math.max(a.y() + a.height(), b.y() + b.height()) - y);
    }

    /** The screen the activity whose body holds this slot works on, as its flow card says; empty elsewhere. */
    private static Optional<String> activityScreen(ValueContext ctx, ScreenGraph graph) {
        Optional<String> body = ctx.slot().flatMap(SlotContext::enclosingMethodSource);
        if (body.isEmpty()) return Optional.empty();
        try {
            return ActivityScreens.screenOf(ActivityScreens.targets(ctx.services().pluginValues(), graph), body.get());
        } catch (RuntimeException unreadable) {
            // Flows that do not read leave the screens in the map's order.
            return Optional.empty();
        }
    }

    private static ScreenGraph graph(StudioServices services) {
        return ScreenMapRead.orEmpty(services);
    }

    /** The picture {@code id} as a small image; null when its file is missing. */
    private static Node thumb(Path resources, String id) {
        if (resources == null || id == null) return null;
        Path file = TemplateLibrary.fileForName(resources, id);
        if (!Files.isRegularFile(file)) return null;
        ImageView view = new ImageView(new Image(file.toUri().toString(), THUMB, THUMB, true, true, true));
        view.setPreserveRatio(true);
        return view;
    }
}
