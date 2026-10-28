package com.android.server.wm;

/** Keeps getWindowingMode inherited, matching constructor preflight behavior. */
public class WindowContainer {
    public int windowingMode = 1;
    public int requestedVisibleTypes = 1;
    public int displayId;
    public int getDisplayId() { return displayId; }
    public int getWindowingMode() { return windowingMode; }
    public boolean isRequestedVisible(int type) { return (requestedVisibleTypes & type) != 0; }
}
