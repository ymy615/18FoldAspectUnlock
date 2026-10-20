package com.android.server.wm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MiuiSizeCompatService {
    public final Map<String, Float> mWideFoldConfigs = new LinkedHashMap<>();
    public int settingsWrites;

    // True sentinels let tests distinguish a bypass from the original restriction.
    static boolean isWideFoldDisabledPackage(String packageName) { return true; }
    static boolean shouldUseFullscreenBypassInternal(String component) { return true; }

    public static boolean isInnerCompatDisplay(ActivityTaskManagerService atms) {
        return atms != null && !atms.folded && atms.portrait;
    }

    public static boolean isOuterCompatDisplay(ActivityTaskManagerService atms) {
        return atms != null && atms.folded;
    }

    public void writeWideFoldSetting() { settingsWrites++; }
}
