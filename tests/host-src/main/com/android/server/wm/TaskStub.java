package com.android.server.wm;
public final class TaskStub {
    public boolean fullscreen;
    public boolean failSet;
    public boolean isMiuiSizeCompatRuntimeFullscreen() { return fullscreen; }
    public void setMiuiSizeCompatRuntimeFullscreen(boolean value) {
        if (failSet) throw new IllegalStateException("injected task restore failure");
        fullscreen = value;
    }
}
