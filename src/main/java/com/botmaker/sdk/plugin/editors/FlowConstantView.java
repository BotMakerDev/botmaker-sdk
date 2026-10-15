package com.botmaker.sdk.plugin.editors;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.sdk.api.flow.Flow;
import com.botmaker.sdk.plugin.flow.ActivityFlowDialog;
import com.botmaker.sdk.plugin.flow.FlowCanvas;
import com.botmaker.sdk.plugin.flow.FlowValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@code Flows} constant as the bot's {@code Flows.java} shows it: the flow drawn as 🔀 Activity Flow draws it, and
 * a button opening that window on it (the user's call, 2026-10-15). The file is the plugin's, read-only in the
 * editor; the window edits the flow.
 */
public final class FlowConstantView {

    /** How much of the editor the drawing takes. */
    private static final double WIDTH = 760;
    private static final double HEIGHT = 320;

    private FlowConstantView() {}

    /** Whether {@code ctx} is a {@code Flows} constant's own value, not a reference to one. */
    public static boolean isFlowConstant(ValueContext ctx) {
        return ctx.type().is(Flow.class) && ctx.member(FlowValue.FLOWS.value()).isEmpty()
                && constantOf(ctx).isPresent();
    }

    /** The flow drawn, under its name and the button that opens it. */
    public static Node node(ValueContext ctx) {
        Optional<String> constant = constantOf(ctx);
        if (constant.isEmpty()) return new Label(ctx.source());
        Label name = new Label("Flows." + constant.get());
        name.setStyle("-fx-font-weight: bold;");
        Button open = new Button("Open in 🔀 Activity Flow");
        open.setOnAction(e -> ActivityFlowDialog.open(ctx.services(), constant.get()));
        HBox head = new HBox(10, name, open);
        head.setAlignment(Pos.CENTER_LEFT);
        FlowCanvas drawn = ActivityFlowDialog.preview(ctx.services(), constant.get());
        drawn.setPrefSize(WIDTH, HEIGHT);
        drawn.setMinSize(WIDTH, HEIGHT);
        drawn.setMaxSize(WIDTH, HEIGHT);
        VBox box = new VBox(6, head, drawn);
        box.setPadding(new Insets(4));
        return box;
    }

    /** The constant whose value {@code ctx} holds; empty for none. */
    private static Optional<String> constantOf(ValueContext ctx) {
        PluginValues values = ctx.services().pluginValues();
        Optional<Flow> held = ctx.value(Flow.class);
        if (values == null || held.isEmpty()) return Optional.empty();
        for (Member member : FlowValue.FLOWS.members(values)) {
            if (FlowValue.FLOWS.read(values, member).filter(f -> Objects.equals(f, held.get())).isPresent()) {
                return Optional.of(member.name());
            }
        }
        return Optional.empty();
    }
}
