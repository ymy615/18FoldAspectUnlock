package com.android.server.wm;

import android.content.pm.ApplicationInfo;

public final class BoundsCompatUtils {
    public Float forcedScale;
    public RuntimeException scaleFailure;
    /** Small oracle of the inspected 26-version 1080px outer-width branch. */
    public float getGlobalScaleByName(String name, int flip, android.graphics.Rect bounds,
            boolean outer, float ratio) {
        if (scaleFailure != null) throw scaleFailure;
        if (forcedScale != null) return forcedScale;
        if (ActivityTaskManagerServiceStub.get().getScaleMode(name) == 3 || bounds == null
                || bounds.width() <= 0 || bounds.height() <= 0
                || !Float.isFinite(ratio) || ratio <= 0f) return -1f;
        float normalized = Math.max(ratio, 1f / ratio);
        int logicalLong = Math.round(Math.min(1080, Math.min(bounds.width(), bounds.height())) * normalized);
        float value = (float) Math.max(bounds.width(), bounds.height()) / logicalLong;
        return value > 1f ? -1f : value;
    }
    public boolean isWideFoldSystemApp(ApplicationInfo info) {
        return info != null && (info.isSystemApp() || info.isUpdatedSystemApp());
    }
}
