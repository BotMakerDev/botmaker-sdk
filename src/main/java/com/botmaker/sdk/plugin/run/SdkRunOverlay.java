package com.botmaker.sdk.plugin.run;

import com.botmaker.plugin.api.run.RunOverlayContext;
import com.botmaker.plugin.api.run.RunOverlayPart;
import com.botmaker.shared.ipc.TelemetryEvent;
import com.botmaker.shared.ipc.TelemetryFrame;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The SDK's part of the host's run overlay: what the bot is doing, in the bar, and what it found and clicked,
 * over the desktop. Both read the run's telemetry, the frames this SDK's own runtime wrote, so the host decodes
 * none of it.
 */
public final class SdkRunOverlay {

    /** Declared through {@code PluginDeclaration.runOverlay}, which only a host with an overlay calls. */
    public static final List<RunOverlayPart> ALL = List.of(
            RunOverlayPart.id("sdk.run").bar(SdkRunOverlay::bar).layer(SdkRunOverlay::layer));

    private static final Color FOUND = Color.web("#3fb950");
    private static final Color CLICK = Color.web("#f0883e");
    private static final double CLICK_RADIUS = 9;

    private SdkRunOverlay() {}

    /** One line: the activity and the last thing the bot did there. */
    static Node bar(RunOverlayContext context) {
        Label label = new Label();
        label.setVisible(false);
        label.setManaged(false);
        RunStatus[] status = {RunStatus.START};
        listen(context, event -> {
            status[0] = status[0].after(event);
            String text = status[0].text();
            label.setText(text);
            label.setVisible(!text.isEmpty());
            label.setManaged(!text.isEmpty());
        });
        return label;
    }

    /**
     * Boxes where the bot found a picture and dots where it clicked, fading out, in desktop pixels; nothing when
     * the run's pixels are not the desktop's ({@link DesktopRoute}).
     */
    static Node layer(RunOverlayContext context) {
        Pane pane = new Pane();
        pane.setMouseTransparent(true);
        BooleanSupplier onDesktop = DesktopRoute.of(context.services());
        RunMarks marks = new RunMarks();
        // One node per mark for as long as it shows; a frame only fades them.
        Map<Long, Node> drawn = new HashMap<>();
        AnimationTimer fade = new AnimationTimer() {
            @Override
            public void handle(long frame) {
                List<RunMarks.Mark> live = marks.at(System.currentTimeMillis());
                Set<Long> showing = new HashSet<>();
                for (RunMarks.Mark mark : live) {
                    showing.add(mark.id());
                    Node node = drawn.computeIfAbsent(mark.id(), id -> {
                        Node made = node(mark);
                        pane.getChildren().add(made);
                        return made;
                    });
                    node.setOpacity(mark.opacity());
                }
                drawn.entrySet().removeIf(entry -> {
                    if (showing.contains(entry.getKey())) return false;
                    pane.getChildren().remove(entry.getValue());
                    return true;
                });
                if (live.isEmpty()) stop();
            }
        };
        listen(context, event -> {
            if (!onDesktop.getAsBoolean()) return;
            marks.add(event, System.currentTimeMillis());
            if (!marks.isEmpty()) fade.start();
        });
        context.onClosed(fade::stop);
        return pane;
    }

    private static Node node(RunMarks.Mark mark) {
        return switch (mark.kind()) {
            case FOUND -> {
                Rectangle box = new Rectangle(mark.width(), mark.height());
                box.setFill(Color.TRANSPARENT);
                box.setStroke(FOUND);
                box.setStrokeWidth(3);
                // Inside the box's top-left corner rather than above it: above could be off the top of a screen.
                Label confidence = new Label(mark.label());
                confidence.setStyle("-fx-text-fill: white; -fx-background-color: rgba(31,111,46,0.85);"
                        + " -fx-padding: 1 4;");
                Group group = new Group(box, confidence);
                group.relocate(mark.x(), mark.y());
                yield group;
            }
            case CLICK -> {
                Circle dot = new Circle(mark.x(), mark.y(), CLICK_RADIUS, CLICK.deriveColor(0, 1, 1, 0.6));
                dot.setStroke(CLICK);
                dot.setStrokeWidth(2);
                yield dot;
            }
        };
    }

    /**
     * Hands each event of the run to {@code onEvent} on the JavaFX thread, until the overlay closes. A frame this
     * build cannot read is skipped, as a newer runtime's would be.
     */
    private static void listen(RunOverlayContext context, Consumer<TelemetryEvent> onEvent) {
        AutoCloseable subscription = context.services().runs().onTelemetry(frame -> {
            TelemetryEvent event;
            try {
                event = TelemetryFrame.decode(frame);
            } catch (TelemetryFrame.FrameFormatException unreadable) {
                return;
            }
            Platform.runLater(() -> onEvent.accept(event));
        });
        context.onClosed(() -> {
            try {
                subscription.close();
            } catch (Exception ignored) {
                // a host's handle that fails to close leaves a listener feeding a node nobody shows
            }
        });
    }
}
