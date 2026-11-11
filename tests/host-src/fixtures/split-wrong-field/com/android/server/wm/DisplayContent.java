package com.android.server.wm;

import android.content.res.Configuration;
import android.graphics.Rect;

/** An OTA changed the service field type; preflight must reject it. */
public final class DisplayContent extends WindowContainer {
    public WindowState mCurrentFocus;
    public Object mAtmService;
    public Configuration getConfiguration() { return null; }
    public Rect getBounds() { return null; }
    public InsetsPolicy getInsetsPolicy() { return null; }
    public void forAllActivities(java.util.function.Consumer<ActivityRecord> action) {}
}
