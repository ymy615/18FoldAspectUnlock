package com.android.server.wm;

import android.view.InsetsState;

/** Native return/argument recorder, not an Android inset-control implementation. */
public final class InsetsPolicy {
    public boolean appRequestsStatusBarHidden;
    public boolean hasAppRequestedStatusBarHidden() { return appRequestsStatusBarHidden; }
    public DisplayContent mDisplayContent = new DisplayContent();
    public DisplayPolicy mPolicy = new DisplayPolicy();
    public InsetsControlTarget mShowingPermanentControlTarget = new Target();
    public InsetsControlTarget nativeControlTarget = new Target();
    public InsetsControlTarget mFakeStatusControlTarget = new Target();
    public InsetsState nativeResult = new InsetsState();
    public RuntimeException originalFailure;
    public int hiddenTypes = -1;
    public int hideCalls;
    public WindowState lastInsetTarget;
    public InsetsState lastInsetInput;

    public InsetsPolicy() { mDisplayContent.insetsPolicy = this; }

    private InsetsControlTarget getStatusControlTargetInner(WindowState focusedWin, boolean fake) {
        if (originalFailure != null) throw originalFailure;
        return nativeControlTarget;
    }
    void hideSystemBars(int types) {
        if (originalFailure != null) throw originalFailure;
        hiddenTypes = types;
        hideCalls++;
    }
    InsetsState enforceInsetsPolicyForTarget(WindowState window, InsetsState original) {
        if (originalFailure != null) throw originalFailure;
        lastInsetTarget = window;
        lastInsetInput = original;
        return nativeResult;
    }
    public Object createBarWindow(int id) { return new BarWindow(id); }
    private final class BarWindow {
        private final int mId;
        private boolean visible;
        private RuntimeException originalFailure;
        private BarWindow(int id) { mId = id; }
        private void setVisible(boolean value) {
            if (originalFailure != null) throw originalFailure;
            if (InsetsPolicy.this.originalFailure != null) throw InsetsPolicy.this.originalFailure;
            visible = value;
        }
    }
    private static final class Target implements InsetsControlTarget {}
}
