package com.android.server.wm;

import android.view.InsetsState;

/** An OTA changed a field type; preflight must reject it before hooking. */
public final class WindowState {
    public ActivityRecord mActivityRecord;
    public double mGlobalScale;
    InsetsState getCompatInsetsState() { return null; }
}
