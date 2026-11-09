package com.botmaker.sdk.api.screens;

import com.botmaker.sdk.api.geometry.Rect;
import com.botmaker.sdk.api.vision.ImageTemplate;

import java.util.Objects;

/**
 * A box of the screen measured from a picture it shows (2026-11-09): where a {@link Readout} reads, a {@link Caption}'s
 * words are read, a {@link Drag} lets go. {@code Area.of(Pictures.GEMS_ICON, 1.2, 0, 3, 1)}: the box starts 1.2 of
 * the icon's widths right of the icon's top-left corner, level with it, and is 3 widths across and 1 height down.
 *
 * <p>The numbers are in the anchor's own widths and heights, never pixels, so the box follows the icon wherever the game
 * draws it and at whatever size: found at 1.5× its picture, the gap and the box scale with it. A person never types
 * them: a tool computes them from two cuts, the anchor's and the box's ({@link #between}).
 *
 * <p>The offset runs from the anchor's top-left corner to the box's, its sign giving the side: negative is left of or
 * above the anchor. The size is always positive. A run finds the anchor, then reads or acts in {@link #place}; an
 * anchor not shown is an area not there, and a readout of it reads nothing.
 *
 * @param anchor  the picture it is measured from, one of the bot's {@code Pictures}
 * @param offsetX how many of the anchor's widths the box's left edge is right of the anchor's
 * @param offsetY how many of the anchor's heights the box's top edge is below the anchor's
 * @param sizeX   the box's width, in the anchor's widths; above zero
 * @param sizeY   the box's height, in the anchor's heights; above zero
 */
public record Area(ImageTemplate anchor, double offsetX, double offsetY, double sizeX, double sizeY) {

    /** The decimals {@link #between} keeps: a thousandth of an anchor's size is under a pixel for any icon. */
    private static final double KEPT = 1000;

    public Area {
        Objects.requireNonNull(anchor, "An area is measured from a picture: its anchor");
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)) {
            throw new IllegalArgumentException("An area's offset is a number, not " + offsetX + ", " + offsetY);
        }
        if (!(sizeX > 0) || !(sizeY > 0) || !Double.isFinite(sizeX) || !Double.isFinite(sizeY)) {
            throw new IllegalArgumentException("An area's size is above zero, not " + sizeX + " x " + sizeY);
        }
    }

    /** The box {@code offsetX, offsetY} of {@code anchor}'s widths and heights from its top-left, {@code sizeX × sizeY}. */
    public static Area of(ImageTemplate anchor, double offsetX, double offsetY, double sizeX, double sizeY) {
        return new Area(anchor, offsetX, offsetY, sizeX, sizeY);
    }

    /**
     * The area of the box cut at {@code cut} while {@code anchor} shows at {@code found}, both on one frame: what a tool
     * writes from a person's two cuts. Each number is kept to a thousandth.
     */
    public static Area between(ImageTemplate anchor, Rect found, Rect cut) {
        if (found.width() <= 0 || found.height() <= 0) {
            throw new IllegalArgumentException("The anchor is found with a size, not " + found);
        }
        double w = found.width();
        double h = found.height();
        return new Area(anchor, kept((cut.x() - found.x()) / w), kept((cut.y() - found.y()) / h),
                kept(cut.width() / w), kept(cut.height() / h));
    }

    /** Where the box is while the anchor shows at {@code found}, in the frame's pixels, rounded to whole ones. */
    public Rect place(Rect found) {
        int x = found.x() + (int) Math.round(offsetX * found.width());
        int y = found.y() + (int) Math.round(offsetY * found.height());
        int w = Math.max(1, (int) Math.round(sizeX * found.width()));
        int h = Math.max(1, (int) Math.round(sizeY * found.height()));
        return new Rect(x, y, w, h);
    }

    /** {@code value} to a thousandth, and never a positive size of zero. */
    private static double kept(double value) {
        double rounded = Math.round(value * KEPT) / KEPT;
        return rounded == 0 && value > 0 ? 1 / KEPT : rounded;
    }

    /** As a window or a tool names it: {@code "1.2, 0 from gems_icon, 3 x 1"}. */
    public String said() {
        return number(offsetX) + ", " + number(offsetY) + " from " + anchor.id() + ", "
                + number(sizeX) + " x " + number(sizeY);
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
