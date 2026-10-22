package com.botmaker.sdk.internal.screens;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.screens.Place;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Which copies of a button meet a {@link Place}, once its anchors are found: pure, in the frame's pixels. */
final class Placed {

    private Placed() {}

    /**
     * {@code items} whose {@code place} meets {@code side} of {@code anchor} ({@code other} the second anchor of
     * {@link Place.Side#BETWEEN}), the nearest the anchor first; between two, the nearest the middle of both.
     */
    static <T> List<T> meeting(List<T> items, Function<T, Rect> place, Place.Side side, Rect anchor, Rect other) {
        Point from = side == Place.Side.BETWEEN && other != null ? span(anchor, other).center() : anchor.center();
        List<T> kept = new ArrayList<>();
        for (T item : items) if (meets(place.apply(item), side, anchor, other)) kept.add(item);
        kept.sort(Comparator.comparingLong(item -> squared(place.apply(item).center(), from)));
        return kept;
    }

    /**
     * Whether {@code copy} sits {@code side} of {@code anchor}: in its column (centres no further across than the
     * anchor's width) past its top or bottom edge, in its row (by its height) past its left or right edge, anywhere
     * for the nearest, inside the box spanning both anchors between two.
     */
    static boolean meets(Rect copy, Place.Side side, Rect anchor, Rect other) {
        Point c = copy.center();
        Point a = anchor.center();
        boolean column = Math.abs(c.x() - a.x()) <= anchor.width();
        boolean row = Math.abs(c.y() - a.y()) <= anchor.height();
        return switch (side) {
            case ABOVE -> column && c.y() < anchor.y();
            case BELOW -> column && c.y() > anchor.y() + anchor.height();
            case LEFT_OF -> row && c.x() < anchor.x();
            case RIGHT_OF -> row && c.x() > anchor.x() + anchor.width();
            case NEAREST -> true;
            case BETWEEN -> other != null && span(anchor, other).contains(c);
            case UNKNOWN -> false;
        };
    }

    private static Rect span(Rect a, Rect b) {
        int left = Math.min(a.x(), b.x());
        int top = Math.min(a.y(), b.y());
        return new Rect(left, top, Math.max(a.x() + a.width(), b.x() + b.width()) - left,
                Math.max(a.y() + a.height(), b.y() + b.height()) - top);
    }

    private static long squared(Point p, Point q) {
        long dx = p.x() - q.x();
        long dy = p.y() - q.y();
        return dx * dx + dy * dy;
    }
}
