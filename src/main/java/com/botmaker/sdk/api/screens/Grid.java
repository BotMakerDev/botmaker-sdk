package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * The cells of a {@link Button#grid grid button}, an inventory's slots, measured from its anchor picture as a
 * {@link Beside} offset is — {@code Grid.of(3, 5, 1.2, 1.2)}: the first cell where the button's {@link Button#beside}
 * puts it, each next one {@code across} of the anchor's widths right and each next row {@code down} of its heights
 * below. No pixel enters, so the cells follow the anchor when the panel moves or the frame is scaled (the user's call,
 * 2026-10-22). A cell is pressed only when the anchor is found; {@link Pick#cell} names one.
 *
 * @param rows    how many rows, at least 1
 * @param columns how many cells in a row, at least 1
 * @param across  from one cell's centre to the next one's right, in the anchor's widths
 * @param down    from one row's centre to the next one's below, in the anchor's heights
 */
public record Grid(int rows, int columns, double across, double down) {

    public Grid {
        rows = Math.max(1, rows);
        columns = Math.max(1, columns);
    }

    /** {@code rows} by {@code columns} cells, {@code across} widths and {@code down} heights of the anchor apart. */
    public static Grid of(int rows, int columns, double across, double down) {
        return new Grid(rows, columns, across, down);
    }

    /** How many cells. */
    public int size() {
        return rows * columns;
    }

    /**
     * Every cell, row by row, when the anchor is found at {@code anchor} and the first cell sits {@code first} from
     * it (null: on the anchor). A cell is as wide as the step across, as high as the step down: the anchor's size
     * along a step of none.
     */
    public List<Rect> cells(Rect anchor, Beside first) {
        double w = anchor.width();
        double h = anchor.height();
        double fromAcross = first == null ? 0 : first.across();
        double fromDown = first == null ? 0 : first.down();
        int cw = (int) Math.round(across == 0 ? w : Math.abs(across) * w);
        int ch = (int) Math.round(down == 0 ? h : Math.abs(down) * h);
        double cx = anchor.x() + w / 2;
        double cy = anchor.y() + h / 2;
        List<Rect> cells = new ArrayList<>(size());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                double x = cx + (fromAcross + c * across) * w;
                double y = cy + (fromDown + r * down) * h;
                cells.add(new Rect((int) Math.round(x - cw / 2.0), (int) Math.round(y - ch / 2.0), cw, ch));
            }
        }
        return cells;
    }
}
