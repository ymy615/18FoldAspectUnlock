package com.android.server.wm;

public final class BoundsCompatController {
    public boolean wideFoldCompatScene = true;
    public boolean inSizeCompat = true;
    public boolean outerCompatScene = true;
    public boolean multipleSplit;
    public boolean game;
    public float currentScale = 0.82466f;
    public float mGlobalScale = 0.8f;

    boolean hideStatusBarInSizeCompat() {
        return wideFoldCompatScene && inSizeCompat;
    }
    boolean isOuterCompatScene() { return outerCompatScene; }
    boolean inMiuiSizeCompatMode() { return inSizeCompat; }
    boolean isInMultipleSplitWindow() { return multipleSplit; }
    boolean isSizeCompatGameApp() { return game; }
    float getCurrentMiuiSizeCompatScale(float fallback) { return currentScale; }
}
