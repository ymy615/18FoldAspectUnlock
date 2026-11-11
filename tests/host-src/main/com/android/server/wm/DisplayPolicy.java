package com.android.server.wm;

/** Native return sentinels distinguish after callbacks from input rewriting. */
public final class DisplayPolicy {
    public WindowState mSystemUiControllingWindow;
    public int nativeOpacityBits = 1;
    public int nativeNavigationBits = 16;
    public RuntimeException originalFailure;
    public boolean keyguardShowing;
    public boolean isKeyguardShowing() { return keyguardShowing; }

    int configureStatusBarOpacity(int appearance) {
        if (originalFailure != null) throw originalFailure;
        return appearance | nativeOpacityBits;
    }
    int updateLightNavigationBarLw(int appearance, WindowState window) {
        if (originalFailure != null) throw originalFailure;
        return appearance | nativeNavigationBits;
    }
}
