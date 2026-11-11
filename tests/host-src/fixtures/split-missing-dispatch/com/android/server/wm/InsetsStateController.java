package com.android.server.wm;

/** The final dispatch ABI changed; do not install any of the core transaction. */
public final class InsetsStateController {
    private final DisplayContent mDisplayContent = null;
    void onBarControlTargetChanged(InsetsControlTarget status, InsetsControlTarget fakeStatus,
            InsetsControlTarget nav) {}
}
