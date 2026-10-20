package com.android.server.wm;

import android.graphics.Rect;
import android.view.InsetsState;
import android.view.WindowManager;

public final class WindowState extends WindowContainer {
    public ActivityRecord mActivityRecord = new ActivityRecord();
    public float mGlobalScale = 0.82466f;
    public WindowManager.LayoutParams mAttrs = new WindowManager.LayoutParams();
    public boolean mIsChildWindow;
    public int displayId;
    public Rect frame = new Rect(0, 0, 1280, 2280);
    public InsetsState nativeResult;
    public RuntimeException originalFailure;
    public boolean failGetFrame;
    public boolean focused = true;

    public int getDisplayId() { return displayId; }
    public boolean isFocused() { return focused; }
    public Rect getFrame() {
        if (failGetFrame) throw new IllegalStateException("injected frame lookup failure");
        return frame;
    }
    InsetsState getCompatInsetsState() {
        if (originalFailure != null) throw originalFailure;
        return nativeResult;
    }
}
