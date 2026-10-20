package com.android.server.wm;

import android.content.pm.ApplicationInfo;

public final class BoundsCompatUtils {
    public boolean isWideFoldSystemApp(ApplicationInfo info) {
        return info != null && (info.isSystemApp() || info.isUpdatedSystemApp());
    }
}
