package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.screens.Button;
import com.botmaker.sdk.api.screens.Hotkey;
import com.botmaker.sdk.api.screens.Input;
import com.botmaker.sdk.api.screens.Strip;
import com.botmaker.sdk.api.screens.TextButton;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.internal.screens.ScreenGraph;
import com.botmaker.sdk.plugin.map.Outline.Entry;
import com.botmaker.sdk.plugin.map.Outline.Filter;
import com.botmaker.sdk.plugin.map.Outline.Kind;
import com.botmaker.sdk.plugin.map.ScreenCards.Card;
import com.botmaker.sdk.plugin.map.ScreenCards.Link;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * The left of 🗺 Screens: the map as a text tree ({@link Outline}) with a filter, beside the graph (the user's call,
 * 2026-10-18). Picking a line picks its screen on the graph and in the side panel, and the graph's pick picks its line;
 * a right-click gives the screen's or the input's ⋯ menu.
 */
final class ScreenOutline {

    private static final PseudoClass HERE = PseudoClass.getPseudoClass("here");
    private static final String ALL_INPUTS = "All inputs";
    private static final String OPENING_INPUTS = "Inputs that open a screen";

    private final TreeView<Entry> tree = new TreeView<>();
    private final TextField filterText = new TextField();
    private final ToggleButton owes = new ToggleButton("Owes");
    private final ToggleButton untried = new ToggleButton("Untried");
    /** Shows only the inputs that open a screen, or may; off, every input. */
    private final ToggleButton opening = new ToggleButton(ALL_INPUTS);
    private final ToLongFunction<String> shotTime;
    private final VBox root;
    /** Lines shut by hand, by {@link Entry#key}: kept shut across reads. */
    private final Set<String> shut = new HashSet<>();
    private ScreenGraph graph = ScreenGraph.of(Map.of());
    private List<Entry> entries = List.of();
    /** Set while the outline picks a line itself, so the pick is not handed back to the graph. */
    private boolean picking;
    /** The screen the live board sees the game on, lit; null for none. */
    private String here;

    /**
     * @param picked     a screen picked here, by its constant, and the field of its input picked (null: none)
     * @param screenMenu a screen's right-click menu
     * @param inputMenu  an input's right-click menu
     * @param shotTime   when a screen's shot was written, 0 for none: the newest are listed in Recent
     */
    ScreenOutline(BiConsumer<String, String> picked, Function<String, ContextMenu> screenMenu,
                  BiFunction<String, Row, ContextMenu> inputMenu, ToLongFunction<String> shotTime) {
        this.shotTime = shotTime;
        filterText.setPromptText("Filter screens and inputs…");
        HBox.setHgrow(filterText, Priority.ALWAYS);
        owes.setTooltip(new Tooltip(MapGuide.FILTER_OWES));
        untried.setTooltip(new Tooltip(MapGuide.FILTER_UNTRIED));
        opening.setTooltip(new Tooltip(MapGuide.OPENING_ONLY));
        filterText.textProperty().addListener((o, was, is) -> draw());
        owes.selectedProperty().addListener((o, was, is) -> draw());
        untried.selectedProperty().addListener((o, was, is) -> draw());
        opening.selectedProperty().addListener((o, was, is) -> {
            opening.setText(is ? OPENING_INPUTS : ALL_INPUTS);
            draw();
        });
        // The screens' Button is imported: JavaFX's is named in full.
        javafx.scene.control.Button collapse = new javafx.scene.control.Button("⊟ Collapse all");
        collapse.setTooltip(new Tooltip(MapGuide.COLLAPSE_ALL));
        collapse.setOnAction(e -> openAll(false));
        javafx.scene.control.Button expand = new javafx.scene.control.Button("⊞ Expand all");
        expand.setTooltip(new Tooltip(MapGuide.EXPAND_ALL));
        expand.setOnAction(e -> openAll(true));
        HBox bar = new HBox(6, filterText, owes, untried);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 6, 3, 6));
        HBox folds = new HBox(6, collapse, expand, opening);
        folds.setAlignment(Pos.CENTER_LEFT);
        folds.setPadding(new Insets(0, 6, 6, 6));
        tree.setShowRoot(false);
        tree.getStyleClass().add("screen-outline");
        tree.setCellFactory(view -> new Line(screenMenu, inputMenu));
        tree.getSelectionModel().selectedItemProperty().addListener((o, was, is) -> {
            if (picking || is == null || is.getValue().constant() == null) return;
            Entry entry = is.getValue();
            picked.accept(entry.constant(), entry.kind() == Kind.INPUT ? entry.row().field() : null);
        });
        VBox.setVgrow(tree, Priority.ALWAYS);
        root = new VBox(bar, folds, tree);
        root.setMinWidth(180);
        root.setPrefWidth(300);
    }

    Node node() {
        return root;
    }

    /** The map as a read drew it. On the FX thread. */
    void show(ScreenGraph graph, List<Card> cards, List<Link> links) {
        this.graph = graph;
        List<Entry> now = Outline.entries(graph, cards, links, shotTime);
        // Drawn again only when a line changed: a redraw resets the outline's scroll.
        if (now.equals(entries)) return;
        entries = now;
        draw();
    }

    /**
     * Picks {@code constant}'s line, as the graph picked it, and scrolls to it; null picks none. A line of it already
     * picked, an input's, stays picked.
     */
    void select(String constant) {
        Entry picked = Optional.ofNullable(tree.getSelectionModel().getSelectedItem()).map(TreeItem::getValue)
                .orElse(null);
        if (picked != null && constant != null && constant.equals(picked.constant())) return;
        TreeItem<Entry> at = constant == null ? null : find(tree.getRoot(), e -> e.kind() == Kind.SCREEN
                && e.inTree() && constant.equals(e.constant())).orElse(null);
        pick(at);
        if (at != null) tree.scrollTo(tree.getRow(at));
    }

    /** Lights {@code constant}'s line as the screen the game shows; null lights none. */
    void setHere(String constant) {
        if (Objects.equals(constant, here)) return;
        here = constant;
        tree.refresh();
    }

    /** Picks {@code constant}'s input {@code field}'s line, as the board picked it; null {@code field}: the screen's. */
    void selectInput(String constant, String field) {
        TreeItem<Entry> at = find(tree.getRoot(), e -> e.inTree() && constant.equals(e.constant()) && (field == null
                ? e.kind() == Kind.SCREEN : e.kind() == Kind.INPUT && field.equals(e.row().field()))).orElse(null);
        if (at == null) return;
        pick(at);
        tree.scrollTo(tree.getRow(at));
    }

    private void pick(TreeItem<Entry> at) {
        picking = true;
        try {
            if (at == null) {
                tree.getSelectionModel().clearSelection();
            } else {
                for (TreeItem<Entry> up = at.getParent(); up != null; up = up.getParent()) up.setExpanded(true);
                tree.getSelectionModel().select(at);
            }
        } finally {
            picking = false;
        }
    }

    private void draw() {
        Filter filter = new Filter(filterText.getText(), owes.isSelected(), untried.isSelected());
        Entry was = Optional.ofNullable(tree.getSelectionModel().getSelectedItem()).map(TreeItem::getValue)
                .orElse(null);
        TreeItem<Entry> top = new TreeItem<>();
        List<Entry> shown = opening.isSelected() ? Outline.opening(entries) : entries;
        for (Entry entry : Outline.filter(graph, shown, filter)) top.getChildren().add(item(entry, filter.isNone()));
        picking = true;
        try {
            tree.setRoot(top);
        } finally {
            picking = false;
        }
        // The line picked before, by its key, so an input's stays an input's.
        // Gone from Recent, or an input folded away: its screen's line, which the panel still shows.
        if (was != null) {
            pick(find(top, e -> was.key().equals(e.key())).or(() -> was.constant() == null ? Optional.empty()
                    : find(top, e -> e.kind() == Kind.SCREEN && e.inTree() && was.constant().equals(e.constant())))
                    .orElse(null));
        }
    }

    /** {@code entry}'s line and those under it; a filtered outline opens every line, to show what matched. */
    private TreeItem<Entry> item(Entry entry, boolean whole) {
        TreeItem<Entry> item = new TreeItem<>(entry);
        for (Entry child : entry.children()) item.getChildren().add(item(child, whole));
        item.setExpanded(!whole || !shut.contains(entry.key()));
        if (whole) {
            item.expandedProperty().addListener((o, was, open) -> {
                if (open) shut.remove(entry.key());
                else shut.add(entry.key());
            });
        }
        return item;
    }

    /**
     * Opens or shuts every line with lines under it; on the whole outline each line's listener keeps it so across
     * reads, a filtered one opens again with its next change.
     */
    private void openAll(boolean open) {
        openAll(tree.getRoot(), open);
    }

    private static void openAll(TreeItem<Entry> at, boolean open) {
        if (at == null) return;
        for (TreeItem<Entry> child : at.getChildren()) {
            if (!child.isLeaf()) child.setExpanded(open);
            openAll(child, open);
        }
    }

    private static Optional<TreeItem<Entry>> find(TreeItem<Entry> at, Predicate<Entry> wanted) {
        if (at == null) return Optional.empty();
        if (at.getValue() != null && wanted.test(at.getValue())) return Optional.of(at);
        for (TreeItem<Entry> child : at.getChildren()) {
            Optional<TreeItem<Entry>> found = find(child, wanted);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /** What an input is, by a sign before its field. */
    static String sign(Input input) {
        return switch (input) {
            case Button button -> button.isDrag() ? "⇢" : button.isField() ? "✎" : "◻";
            case Hotkey ignored -> "⌨";
            case TextButton ignored -> "🔤";
            case Strip ignored -> "☰";
            case Tree ignored -> "🌳";
        };
    }

    /** One line: a group's name, a screen's name and how far it got, or an input and what it did. */
    private final class Line extends TreeCell<Entry> {

        Line(Function<String, ContextMenu> screenMenu, BiFunction<String, Row, ContextMenu> inputMenu) {
            // Made when asked, so it marks the input as it is now.
            setOnContextMenuRequested(e -> {
                Entry entry = getItem();
                if (entry == null || entry.kind() == Kind.GROUP) return;
                ContextMenu menu = entry.kind() == Kind.SCREEN ? screenMenu.apply(entry.constant())
                        : inputMenu.apply(entry.constant(), entry.row());
                menu.show(this, e.getScreenX(), e.getScreenY());
                e.consume();
            });
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            setText(null);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            setGraphic(switch (entry.kind()) {
                case GROUP -> styled(new Label(entry.label()), "screen-outline-group");
                case SCREEN -> screen(entry.card(), entry.constant().equals(here));
                case INPUT -> input(entry.row());
            });
        }

        private static Node screen(Card card, boolean lit) {
            Label name = styled(new Label(card.name()), "screen-outline-screen");
            name.pseudoClassStateChanged(HERE, lit);
            HBox line = new HBox(6, name);
            for (ScreenCards.Pill pill : ScreenCards.pills(card)) {
                line.getChildren().add(styled(new Label(pill.text()), "screen-outline-pill", pill.style()));
            }
            line.setAlignment(Pos.CENTER_LEFT);
            return line;
        }

        private static Node input(Row row) {
            Label field = styled(new Label(sign(row.input()) + " " + row.label()), "screen-row-label");
            Label result = styled(new Label(row.result()), "screen-outline-result",
                    "screen-outline-" + row.state().name().toLowerCase(Locale.ROOT));
            HBox line = new HBox(6, field, result);
            if (!row.kind().isEmpty()) line.getChildren().add(styled(new Label(row.kind()), "screen-row-kind"));
            line.setAlignment(Pos.CENTER_LEFT);
            return line;
        }

        private static Label styled(Label label, String... styles) {
            label.getStyleClass().addAll(styles);
            return label;
        }
    }
}
