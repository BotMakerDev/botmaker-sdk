package com.botmaker.sdk.plugin.assist;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.input.Key;
import com.botmaker.sdk.api.screens.Beside;
import com.botmaker.sdk.api.screens.Node;
import com.botmaker.sdk.api.screens.Tree;
import com.botmaker.sdk.api.vision.ImageTemplate;
import com.botmaker.sdk.internal.screens.Reach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeMeasureTest {

    private static final ImageTemplate A = new ImageTemplate("images/a.png");
    private static final ImageTemplate B = new ImageTemplate("images/b.png");
    private static final ImageTemplate C = new ImageTemplate("images/c.png");

    /** a, then b and c, each unlocked by a. */
    private static final Tree TREE = Tree.of(List.of(Node.of(A), Node.of(B).after(List.of(A)),
            Node.of(C).after(List.of(A)))).dragged();

    @Test
    void aLinkedPairInViewIsPlacedInItsParentsSize() {
        Map<String, Rect> seen = Map.of("a", new Rect(100, 200, 100, 80), "b", new Rect(220, 152, 100, 80));

        assertEquals(List.of(new TreeMeasure.Offset(B, Beside.of(A, 1.2, -0.6))), TreeMeasure.between(seen, TREE));
    }

    @Test
    void aPairNotLinkedIsNotMeasured() {
        Map<String, Rect> seen = Map.of("b", new Rect(0, 0, 100, 80), "c", new Rect(300, 0, 100, 80));

        assertTrue(TreeMeasure.between(seen, TREE).isEmpty());
    }

    @Test
    void anOffsetIsRoundedToAStep() {
        // 123 px right of a 100 px wide parent: 1.23, read as 1.25.
        Map<String, Rect> seen = Map.of("a", new Rect(0, 0, 100, 100), "b", new Rect(123, 0, 100, 100));

        assertEquals(1.25, TreeMeasure.between(seen, TREE).getFirst().beside().across());
    }

    @Test
    void aPairSplitByTheMoveIsMeasuredAsIfSeenTogether() {
        // a showed at x 100 before the view moved 600 px left; c shows at x 500 after: 1000 px apart.
        Map<String, Rect> before = Map.of("a", new Rect(100, 100, 100, 100));
        Map<String, Rect> after = Map.of("c", new Rect(500, 100, 100, 100));

        assertEquals(List.of(new TreeMeasure.Offset(C, Beside.of(A, 10.0, 0.0))),
                TreeMeasure.across(before, new Point(-600, 0), after, TREE));
    }

    @Test
    void aPairSeenOnOneSideIsNotMeasuredAcrossTheMove() {
        Map<String, Rect> before = Map.of("a", new Rect(700, 100, 100, 100), "b", new Rect(820, 100, 100, 100));
        Map<String, Rect> after = Map.of("a", new Rect(100, 100, 100, 100), "b", new Rect(220, 100, 100, 100));

        assertTrue(TreeMeasure.across(before, new Point(-600, 0), after, TREE).isEmpty());
    }

    @Test
    void theMoveIsReadOffTheNodesSeenBothBeforeAndAfter() {
        Map<String, Rect> before = Map.of("a", new Rect(700, 100, 100, 100), "b", new Rect(820, 90, 100, 100));
        Map<String, Rect> after = Map.of("a", new Rect(100, 100, 100, 100), "b", new Rect(222, 90, 100, 100));

        assertEquals(Optional.of(new Point(-598, 0)), TreeMeasure.common(before, after));
        assertEquals(Optional.empty(), TreeMeasure.common(before, Map.of("c", new Rect(0, 0, 1, 1))));
    }

    @Test
    void anOffsetReplacesTheOneOfItsPairEitherWay() {
        Tree written = Tree.of(List.of(Node.of(A).beside(List.of(Beside.of(B, -1, 0))),
                Node.of(B).after(List.of(A)).beside(List.of(Beside.of(A, 2, 0))), Node.of(C).after(List.of(A))));

        Tree now = TreeMeasure.with(written, List.of(new TreeMeasure.Offset(B, Beside.of(A, 1.2, 0))));

        assertEquals(List.of(), now.nodes().get(0).beside(), "the pair written the other way round goes");
        assertEquals(List.of(Beside.of(A, 1.2, 0)), now.nodes().get(1).beside());
        assertEquals(List.of("c"), now.unplaced(), "c has no offset to a yet");
    }

    /**
     * A view that starts gliding only after the first look, each look taking 11 s (Firestone's Tree I): the first look,
     * past the settle time already, is not taken for the view at its end.
     */
    @Test
    void aViewThatGlidesLateIsLookedAtAgainPastTheTime() {
        Reach late = new Reach() {
            final Map<BufferedImage, Integer> shifts = new HashMap<>();
            long now;

            @Override
            public Optional<BufferedImage> look() {
                now += 11_000;
                BufferedImage frame = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
                shifts.put(frame, shifts.isEmpty() ? 0 : -300);
                return Optional.of(frame);
            }

            @Override
            public Optional<Rect> find(ImageTemplate picture, BufferedImage frame) {
                return Optional.of(new Rect(700 + shifts.get(frame), 100, 100, 100));
            }

            @Override
            public void click(Point at) {
            }

            @Override
            public void key(Key key) {
            }

            @Override
            public void scroll(Point at, int notches) {
            }

            @Override
            public void drag(Point from, Point to, Duration time) {
            }

            @Override
            public long nowMs() {
                return now;
            }

            @Override
            public void pause(long ms) {
                now += ms;
            }
        };
        Tree one = Tree.of(List.of(Node.of(A)));

        BufferedImage settled = TreeMeasure.settled(late, one, Map.of(), Map.of("a", new Rect(700, 100, 100, 100)))
                .orElseThrow();

        assertEquals(new Rect(400, 100, 100, 100), late.find(A, settled).orElseThrow(), "the view after the glide");
    }
}
