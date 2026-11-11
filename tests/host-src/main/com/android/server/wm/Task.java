package com.android.server.wm;

public final class Task {
    public android.graphics.Rect bounds = new android.graphics.Rect(0, 0, 1170, 1672);
    public boolean failGetBounds;
    public android.graphics.Rect getBounds() {
        if (failGetBounds) throw new IllegalStateException("injected task-bounds lookup failure");
        return bounds;
    }
    public boolean runtimeFullscreen;
    public int changes;
    public boolean failChange;
    public void onSizeCompatActivityChanged() {
        changes++;
        if (failChange) throw new IllegalStateException("injected task update failure");
    }
}
