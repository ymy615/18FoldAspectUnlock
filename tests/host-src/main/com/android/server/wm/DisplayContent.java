package com.android.server.wm;

import android.content.res.Configuration;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DisplayContent extends WindowContainer {
    public WindowState mCurrentFocus;
    public ActivityTaskManagerService mAtmService = new ActivityTaskManagerService();
    public Configuration configuration = new Configuration();
    public Rect bounds = new Rect(0, 0, 2364, 1672);
    public boolean failConfiguration;
    public boolean failGetBounds;
    public InsetsPolicy insetsPolicy;
    public boolean failInsetsPolicy;
    public final List<ActivityRecord> activities = new ArrayList<>();
    public int enumerationCalls;
    public int visitedActivities;
    public boolean failEnumeration;
    public int failEnumerationAfterVisits = -1;
    public void forAllActivities(Consumer<ActivityRecord> action) {
        enumerationCalls++;
        if (failEnumeration) throw new IllegalStateException("injected display enumeration failure");
        int visits = 0;
        for (ActivityRecord activity : activities) {
            action.accept(activity);
            visitedActivities++;
            visits++;
            if (visits == failEnumerationAfterVisits) {
                throw new IllegalStateException("injected enumeration failure after a visit");
            }
        }
    }
    public InsetsPolicy getInsetsPolicy() {
        if (failInsetsPolicy) throw new IllegalStateException("injected inset-policy lookup failure");
        return insetsPolicy;
    }
    public Configuration getConfiguration() {
        if (failConfiguration) throw new IllegalStateException("injected configuration lookup failure");
        return configuration;
    }
    public Rect getBounds() {
        if (failGetBounds) throw new IllegalStateException("injected display-bounds lookup failure");
        return bounds;
    }
}
