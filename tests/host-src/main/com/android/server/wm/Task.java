package com.android.server.wm;

public final class Task {
    public boolean runtimeFullscreen;
    public int changes;
    public boolean failChange;
    public void onSizeCompatActivityChanged() {
        changes++;
        if (failChange) throw new IllegalStateException("injected task update failure");
    }
}
