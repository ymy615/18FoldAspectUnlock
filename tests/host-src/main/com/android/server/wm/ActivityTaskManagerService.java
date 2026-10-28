package com.android.server.wm;

/** Display-state inputs for the explicitly simulated configuration pass. */
public final class ActivityTaskManagerService {
    public boolean folded;
    public boolean portrait = true;
    public WindowManagerGlobalLock mGlobalLock = new WindowManagerGlobalLock();
    public RootWindowContainer mRootWindowContainer = new RootWindowContainer();
    public android.content.res.Configuration configuration = new android.content.res.Configuration();
    public android.content.res.Configuration getGlobalConfiguration() { return configuration; }
}
