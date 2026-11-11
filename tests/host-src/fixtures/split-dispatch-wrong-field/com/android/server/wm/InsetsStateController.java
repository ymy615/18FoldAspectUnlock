package com.android.server.wm;

/** Correct dispatch ABI but an incompatible owner field must still fail preflight. */
public final class InsetsStateController {
    private final Object mDisplayContent = null;
    void onBarControlTargetChanged(InsetsControlTarget status, InsetsControlTarget fakeStatus,
            InsetsControlTarget nav, InsetsControlTarget fakeNav) {}
}
