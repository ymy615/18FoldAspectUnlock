package com.android.server.wm;

/** Fixture for the independent app-requested visibility path. */
public final class InsetsPolicy {
    public boolean appRequestsStatusBarHidden;
    public boolean hasAppRequestedStatusBarHidden() { return appRequestsStatusBarHidden; }
}
