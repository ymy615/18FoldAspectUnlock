package com.android.server.wm;

/** A later firmware no longer exposes the native inset enforcement method. */
public final class InsetsPolicy {
    public DisplayContent mDisplayContent;
    public DisplayPolicy mPolicy;
    public InsetsControlTarget mShowingPermanentControlTarget;
    public InsetsControlTarget mFakeStatusControlTarget;
    private InsetsControlTarget getStatusControlTargetInner(WindowState window, boolean fake) { return null; }
    void hideSystemBars(int types) {}
    private final class BarWindow {
        private final int mId = 1;
        private void setVisible(boolean visible) { if (InsetsPolicy.this.mPolicy == null) return; }
    }
}
