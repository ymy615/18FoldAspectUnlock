package com.android.server.wm;

public final class WindowProcessController {
    public int updates;
    public boolean failUpdate;
    public Runnable beforeUpdate;
    public void updateBoundsCompatStateIfNeeded() {
        if (beforeUpdate != null) beforeUpdate.run();
        updates++;
        if (failUpdate) throw new IllegalStateException("injected process update failure");
    }
}
