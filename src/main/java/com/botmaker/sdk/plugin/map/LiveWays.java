package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.api.geometry.Point;
import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.plugin.map.ScreenCards.Row;
import com.botmaker.sdk.plugin.pictures.FrameCut;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the live board learns from the game as a person drives it: where a screen's inputs are on a live frame, which
 * one a click fell in, and the way in a click proved — a click on one of A's inputs, then B recognised. Plain values,
 * so it is tested without a game.
 */
final class LiveWays {

    /** How long after a click a new screen recognised is still that click's doing. */
    static final long RECENT_MS = 8_000;

    private LiveWays() {}

    /** A click on the live board: on {@code screen} (null when none was recognised), in its input {@code field} or none. */
    record Click(String screen, String field, long atMs) {}

    /** A way in a click proved: {@code from}'s input {@code field} opens {@code to}. */
    record Proved(String from, String field, String to) {}

    /**
     * Where each of {@code rows} is on a {@code width}×{@code height} frame, by its field: where its picture was cut
     * ({@code cuts}, by picture), its frame fitted into this one whole and centred, as {@link ScreenCanvas#boxOn} draws
     * it on a shot. A row with no picture or no place is left out.
     */
    static Map<String, Rect> boxes(List<Row> rows, Map<String, FrameCut> cuts, int width, int height) {
        Map<String, Rect> boxes = new LinkedHashMap<>();
        for (Row row : rows) {
            FrameCut cut = row.picture() == null ? null : cuts.get(row.picture());
            if (cut != null && cut.place() != null) boxes.putIfAbsent(row.field(), scaled(cut, width, height));
        }
        return boxes;
    }

    /** Where each of {@code markers} (picture ids) was cut, on a frame {@code width} by {@code height}, by id. */
    static Map<String, Rect> markerBoxes(List<String> markers, Map<String, FrameCut> cuts, int width, int height) {
        Map<String, Rect> boxes = new LinkedHashMap<>();
        for (String marker : markers) {
            FrameCut cut = cuts.get(marker);
            if (cut != null && cut.place() != null) boxes.putIfAbsent(marker, scaled(cut, width, height));
        }
        return boxes;
    }

    /** {@code cut}'s place on a frame {@code width} by {@code height}, letterboxed from the frame it was cut on. */
    private static Rect scaled(FrameCut cut, int width, int height) {
        double from = cut.frame() == null ? width : cut.frame().width();
        double fromHeight = cut.frame() == null ? height : cut.frame().height();
        double scale = Math.min(width / from, height / fromHeight);
        double left = (width - from * scale) / 2;
        double top = (height - fromHeight * scale) / 2;
        Rect place = cut.place();
        return new Rect((int) Math.round(left + place.x() * scale), (int) Math.round(top + place.y() * scale),
                Math.max(1, (int) Math.round(place.width() * scale)),
                Math.max(1, (int) Math.round(place.height() * scale)));
    }

    /** The field whose box holds {@code at}, the smallest when several do: a button drawn on a panel. */
    static Optional<String> fieldAt(Map<String, Rect> boxes, Point at) {
        return boxes.entrySet().stream().filter(e -> e.getValue().contains(at))
                .min(Comparator.comparingLong(e -> e.getValue().area())).map(Map.Entry::getKey);
    }

    /**
     * The way {@code last} proved when the screen recognised went from {@code was} to {@code now} at {@code nowMs}: the
     * click was on {@code was}, in one of its inputs, at most {@link #RECENT_MS} before. Whether the map may take it is
     * {@code ScreenEdits.wayRefused}'s to say.
     */
    static Optional<Proved> proved(Click last, String was, String now, long nowMs) {
        if (last == null || last.field() == null || was == null || now == null || was.equals(now)) {
            return Optional.empty();
        }
        if (!Objects.equals(last.screen(), was) || nowMs - last.atMs() > RECENT_MS || nowMs < last.atMs()) {
            return Optional.empty();
        }
        return Optional.of(new Proved(was, last.field(), now));
    }
}
