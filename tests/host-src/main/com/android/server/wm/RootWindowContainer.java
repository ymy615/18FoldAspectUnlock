package com.android.server.wm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RootWindowContainer {
    public final List<ActivityRecord> activities = new ArrayList<>();
    public boolean failEnumeration;
    public void forAllActivities(Consumer<ActivityRecord> action) {
        if (failEnumeration) throw new IllegalStateException("injected activity enumeration failure");
        for (ActivityRecord activity : activities) action.accept(activity);
    }
}
