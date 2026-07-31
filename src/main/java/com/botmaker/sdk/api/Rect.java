package com.botmaker.sdk.api;

import java.awt.Toolkit;
import java.awt.Dimension;

public class Rect {

    public int x, y, width, height;

    public Rect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rect() {
        this(0, 0, 0, 0);
    }

    public Rect(Point p1, Point p2) {
        this.x = (int) Math.min(p1.x, p2.x);
        this.y = (int) Math.min(p1.y, p2.y);
        this.width = (int) Math.abs(p2.x - p1.x);
        this.height = (int) Math.abs(p2.y - p1.y);
    }

    public Rect(Point p, Size s) {
        this((int) p.x, (int) p.y, (int) s.width, (int) s.height);
    }

    public Rect(double[] vals) {
        set(vals);
    }

    /**
     * Creates a Rect representing the full screen.
     */
    public static Rect fullScreen() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return new Rect(0, 0, screenSize.width, screenSize.height);
    }

    /**
     * Creates a Rect centered around a point.
     */
    public static Rect around(Point center, int width, int height) {
        return new Rect(
                (int) center.x - width / 2,
                (int) center.y - height / 2,
                width,
                height
        );
    }

    public void set(double[] vals) {
        if (vals != null) {
            x = vals.length > 0 ? (int) vals[0] : 0;
            y = vals.length > 1 ? (int) vals[1] : 0;
            width = vals.length > 2 ? (int) vals[2] : 0;
            height = vals.length > 3 ? (int) vals[3] : 0;
        } else {
            x = 0;
            y = 0;
            width = 0;
            height = 0;
        }
    }

    public Rect clone() {
        return new Rect(x, y, width, height);
    }

    public Point getTopLeft() {
        return new Point(x, y);
    }

    public Point getTopRight() {
        return new Point(x + width, y);
    }

    public Point getBottomLeft() {
        return new Point(x, y + height);
    }

    public Point getBottomRight() {
        return new Point(x + width, y + height);
    }

    public Point getCenter() {
        return new Point(x + width / 2.0, y + height / 2.0);
    }

    public Size size() {
        return new Size(width, height);
    }

    public double area() {
        return width * height;
    }

    public boolean empty() {
        return width <= 0 || height <= 0;
    }

    public boolean contains(Point p) {
        return x <= p.x && p.x < x + width && y <= p.y && p.y < y + height;
    }

    public boolean overlaps(Rect other) {
        return x < other.x + other.width &&
                x + width > other.x &&
                y < other.y + other.height &&
                y + height > other.y;
    }

    public Rect intersection(Rect other) {
        if (!overlaps(other)) {
            return null;
        }

        int newX = Math.max(x, other.x);
        int newY = Math.max(y, other.y);
        int newX2 = Math.min(x + width, other.x + other.width);
        int newY2 = Math.min(y + height, other.y + other.height);

        return new Rect(newX, newY, newX2 - newX, newY2 - newY);
    }

    public Rect expand(int amount) {
        return new Rect(
                x - amount,
                y - amount,
                width + 2 * amount,
                height + 2 * amount
        );
    }

    public Rect shrink(int amount) {
        return expand(-amount);
    }

    @Override
    public String toString() {
        return "{" + x + ", " + y + ", " + width + "x" + height + "}";
    }
}
