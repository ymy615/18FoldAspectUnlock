package com.android.server.wm;

public final class ActivityRecord extends WindowContainer {
    public android.content.pm.ActivityInfo info = new android.content.pm.ActivityInfo();
    public WindowProcessController app = new WindowProcessController();
    public Task task = new Task();
    public boolean visible = true;
    public boolean attached = true;
    public boolean failApply;
    public int applyCalls;
    public boolean lastEnabled;
    public int lastChanges;
    public boolean isVisible() { return visible; }
    public boolean attachedToProcess() { return attached; }
    public Task getTask() { return task; }
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
