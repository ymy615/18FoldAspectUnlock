package com.android.server.wm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MiuiSizeCompatService {
    public final Map<String, Float> mWideFoldConfigs = new LinkedHashMap<>();
    public int settingsWrites;
    public android.content.Context mContext = new android.content.Context();
    public android.os.Handler mFgHandler = new android.os.Handler();
    public ActivityTaskManagerService mAtms;
    public Runnable beforeApply;
    public RuntimeException readyFailure;
    public float legacyPackageRatio = 16f / 9f;
    public float gameRatio = -1f;
    public RuntimeException queryFailure;
    private float getAspectRatioByPackage(String name, int user) {
        if (queryFailure != null) throw queryFailure;
        return legacyPackageRatio;
    }
    private float getGameAspectRatioByPackage(String name) { return gameRatio; }
    public float packageRatio = 20f / 9f;
    public int nativePersistCalls;
    private float getAspectRatio(String name, int user, ActivityRecord record) {
        if (queryFailure != null) throw queryFailure;
        return packageRatio;
    }
    private void persistWideFoldRatioIfNeededLocked(ActivityRecord record, boolean enabled) {
        nativePersistCalls++;
        mWideFoldConfigs.put((record.info.applicationInfo.uid / 100000) + "#" + record.info.packageName,
                enabled ? 20f/9f : 0f);
        settingsWrites++;
    }

    static float getWideFoldDefaultRatio(ActivityRecord record) { return 20f / 9f; }
    void onSystemReady(ActivityTaskManagerService atms) {
        if (readyFailure != null) throw readyFailure;
        mAtms = atms;
    }
    void applyWideFoldConfigLocked(ActivityRecord record, boolean enabled, int changes) {
        if (beforeApply != null) beforeApply.run();
        record.applyCalls++;
        if (record.failApply) throw new IllegalStateException("injected activity update failure");
        record.lastEnabled = enabled;
        record.lastChanges = changes;
    }

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
