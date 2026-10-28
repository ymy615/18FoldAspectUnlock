package com.android.server.wm;

import java.util.HashMap;
import java.util.Map;

public final class ActivityTaskManagerServiceStub {
    private static final ActivityTaskManagerServiceStub INSTANCE = new ActivityTaskManagerServiceStub();
    public final Map<String, Integer> scaleModes = new HashMap<>();
    public static ActivityTaskManagerServiceStub get() { return INSTANCE; }
    public int getScaleMode(String packageName) { return scaleModes.getOrDefault(packageName, 0); }
}
