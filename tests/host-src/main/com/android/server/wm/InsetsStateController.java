package com.android.server.wm;

/** Records the final provider-dispatch arguments, not SurfaceControl/Binder behavior. */
public final class InsetsStateController {
    private final DisplayContent mDisplayContent;
    public InsetsControlTarget statusTarget;
    public InsetsControlTarget fakeStatusTarget;
    public InsetsControlTarget navTarget;
    public InsetsControlTarget fakeNavTarget;
    public int dispatchCalls;
    public RuntimeException originalFailure;
    public InsetsStateController(DisplayContent display) { mDisplayContent = display; }
    void onBarControlTargetChanged(InsetsControlTarget status, InsetsControlTarget fakeStatus,
            InsetsControlTarget nav, InsetsControlTarget fakeNav) {
        if (originalFailure != null) throw originalFailure;
        statusTarget = status;
        fakeStatusTarget = fakeStatus;
        navTarget = nav;
        fakeNavTarget = fakeNav;
        dispatchCalls++;
    }
}
