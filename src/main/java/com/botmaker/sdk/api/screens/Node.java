package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.List;

/**
 * One node of a {@link Tree} — {@code Node.of(Pictures.HEALTH).after(List.of(Pictures.DAMAGE))
 * .beside(List.of(Beside.of(Pictures.DAMAGE, 1.2, -0.6)))}: the picture of one of the screen's {@link Button buttons},
 * the nodes whose lines lead to it, and where it sits from the nodes it is connected to.
 *
 * <p>{@link #after} is what the game draws, recorded for the map and for choosing what to do; it never moves a walk.
 * {@link #beside} is what a walk follows to bring the node into view.
 *
 * @param picture the picture of its button
 * @param after   the nodes whose line leads to it: they unlock it
 * @param beside  where it sits from connected nodes, each in that node's size
 */
public record Node(ImageTemplate picture, List<ImageTemplate> after, List<Beside> beside) {

    public Node {
        after = after == null ? List.of() : List.copyOf(after);
        beside = beside == null ? List.of() : List.copyOf(beside);
    }

    /** The node whose button is {@code picture}, linked to nothing yet. */
    public static Node of(ImageTemplate picture) {
        return new Node(picture, List.of(), List.of());
    }

    /** This node, unlocked by {@code parents}: the nodes whose line leads to it. */
    public Node after(List<ImageTemplate> parents) {
        return new Node(picture, parents, beside);
    }

    /** This node, placed by {@code offsets} from the nodes it is connected to. */
    public Node beside(List<Beside> offsets) {
        return new Node(picture, after, offsets);
    }
}
