package com.android.server.wm;

public final class ActivityRecord {
    public BoundsCompatController mBoundsCompatControllerStub = new BoundsCompatController();
    public WindowState mainWindow;
    public boolean failFindMainWindow;
    public boolean lastIncludeChildWindows = true;
    public WindowState findMainWindow(boolean includeChildWindows) {
        lastIncludeChildWindows = includeChildWindows;
        if (failFindMainWindow) throw new IllegalStateException("injected main-window lookup failure");
        return mainWindow;
    }
}
