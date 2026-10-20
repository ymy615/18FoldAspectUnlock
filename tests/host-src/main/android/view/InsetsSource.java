package android.view;

import android.graphics.Insets;
import android.graphics.Rect;

public final class InsetsSource {
    private final int type;
    private final Rect frame;
    public Rect visibleFrame;
    public Insets attachedInsets;
    public Rect[] boundingRects;
    public InsetsBoundingRect[] insetsBoundingRects;
    public boolean throwOnSetBoundingRects;

    public InsetsSource(int type, Rect frame) {
        this.type = type;
        this.frame = new Rect(frame);
    }
    public InsetsSource(InsetsSource other) {
        type = other.type;
        frame = new Rect(other.frame);
        visibleFrame = other.visibleFrame == null ? null : new Rect(other.visibleFrame);
        attachedInsets = other.attachedInsets;
        if (other.boundingRects != null) {
            boundingRects = new Rect[other.boundingRects.length];
            for (int i = 0; i < boundingRects.length; i++) boundingRects[i] = new Rect(other.boundingRects[i]);
        }
        insetsBoundingRects = other.insetsBoundingRects == null ? null : other.insetsBoundingRects.clone();
        throwOnSetBoundingRects = other.throwOnSetBoundingRects;
    }
    public int getType() { return type; }
    public Rect getFrame() { return frame; }
    public Rect getVisibleFrame() { return visibleFrame; }
    public Insets getAttachedInsets() { return attachedInsets; }
    public Rect[] getBoundingRects() { return boundingRects; }
    public InsetsBoundingRect[] getInsetsBoundingRects() { return insetsBoundingRects; }
    public InsetsSource setBoundingRects(Rect[] rects) {
        if (throwOnSetBoundingRects) throw new IllegalStateException("injected metadata setter failure");
        boundingRects = rects;
        return this;
    }
}
