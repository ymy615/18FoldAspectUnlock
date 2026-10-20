package android.graphics;

import java.util.Objects;

/** Mutable rectangle semantics needed by the inspected inset callback. */
public final class Rect {
    public int left, top, right, bottom;
    public Rect() {}
    public Rect(int left, int top, int right, int bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public Rect(Rect other) { this(other.left, other.top, other.right, other.bottom); }
    public int width() { return right - left; }
    public int height() { return bottom - top; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Rect)) return false;
        Rect r = (Rect) other;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }
    @Override public int hashCode() { return Objects.hash(left, top, right, bottom); }
    @Override public String toString() { return left + "," + top + "," + right + "," + bottom; }
}
