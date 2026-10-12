package com.botmaker.sdk.plugin.flow;

import com.botmaker.sdk.api.flow.Flow;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The bot's flows as a tree: each flow, its cards under it, and under a sub-flow card the cards of the flow it
 * runs (2026-10-12). 🔀 Activity Flow's side pane opens what is clicked; the flow parameter's editor shows the
 * same tree read-only and picks a flow from it.
 */
public final class FlowTreeView {

    /** The mark a picked flow carries in the tree, the one the bot's {@code flow} parameter names. */
    static final String STAR = "★ ";

    private FlowTreeView() {}

    /**
     * One row: a flow ({@link #card} null), or a card of {@link #flow} — an activity's label, or a sub-flow card's
     * {@code ▸ DAILY}.
     */
    public record Item(String flow, String card) {

        /** Whether this row is a flow, not one of its cards. */
        public boolean isFlow() {
            return card == null;
        }

        /** The flow this row's card runs when it is a sub-flow card; else null. */
        public String subflow() {
            return card == null ? null : FlowNames.subflowOf(card);
        }
    }

    /**
     * A tree of {@code refs}' flows, each collapsed, and {@code starred} marked {@value #STAR}; the root is hidden.
     * A sub-flow card running a flow already open above it shows no cards: that flow runs itself.
     */
    public static TreeView<Item> of(FlowRefs refs, String starred) {
        TreeItem<Item> root = new TreeItem<>(new Item("", null));
        for (String name : refs.names()) {
            TreeItem<Item> flow = new TreeItem<>(new Item(name, null));
            Set<String> open = new HashSet<>();
            open.add(name);
            addCards(flow, refs, name, open);
            root.getChildren().add(flow);
        }
        TreeView<Item> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.getStyleClass().add("flow-tree");
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("flow-tree-flow", "flow-tree-subflow");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                if (item.isFlow()) {
                    getStyleClass().add("flow-tree-flow");
                    setText((item.flow().equals(starred) ? STAR : "") + item.flow());
                } else {
                    if (item.subflow() != null) getStyleClass().add("flow-tree-subflow");
                    setText(item.card());
                }
            }
        });
        return tree;
    }

    private static void addCards(TreeItem<Item> parent, FlowRefs refs, String flow, Set<String> open) {
        for (Flow.Step step : refs.flow(flow).steps()) {
            TreeItem<Item> card = new TreeItem<>(new Item(flow, step.label()));
            String inner = FlowRefs.flowOf(step.node());
            if (inner != null && open.add(inner)) {
                addCards(card, refs, inner, open);
                open.remove(inner);
            }
            parent.getChildren().add(card);
        }
    }

    /**
     * The flows to show for {@code item}, outermost first: the flow at the top of its branch, then the flow of every
     * sub-flow card on the way down to it, its own included — 🔀 Activity Flow's breadcrumb.
     */
    public static List<String> trail(TreeItem<Item> item) {
        List<String> trail = new ArrayList<>();
        for (TreeItem<Item> at = item; at != null && at.getValue() != null; at = at.getParent()) {
            Item value = at.getValue();
            if (value.isFlow() && !value.flow().isEmpty()) trail.addFirst(value.flow());
            else if (value.subflow() != null) trail.addFirst(value.subflow());
        }
        return trail;
    }

    /** The row of the flow {@code name} at the top of {@code tree}, or null. */
    public static TreeItem<Item> flowRow(TreeView<Item> tree, String name) {
        for (TreeItem<Item> row : tree.getRoot().getChildren()) {
            if (row.getValue().flow().equals(name)) return row;
        }
        return null;
    }
}
