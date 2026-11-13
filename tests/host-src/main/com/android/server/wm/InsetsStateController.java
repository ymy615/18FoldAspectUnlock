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
    public int insetNotifications;
    public Runnable beforeNotifyInsets;
    public RuntimeException notifyInsetsFailure;
    void notifyInsetsChanged() {
        insetNotifications++;
        if (beforeNotifyInsets != null) beforeNotifyInsets.run();
        if (notifyInsetsFailure != null) throw notifyInsetsFailure;
    }
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
