package io.github.foldaspect.unlock;

/** Values shared by the system-server decision and the scoped SystemUI renderer. */
final class StatusBarStylePolicy {
    // Unused in the inspected InsetsFlags; carried only in the server's computed
    // appearance result. It is never written into an application's own attributes.
    static final int COMPACT_APPEARANCE = 0x10000000;
    static final int SCRIM_COLOR = 0x33000000;
    static final int TARGET_HEIGHT_PX = 90;

    static boolean requested(int displayId, int appearance, int visibleTypes) {
        return displayId == 0 && (appearance & COMPACT_APPEARANCE) != 0
                && (appearance & 32) != 0 && (visibleTypes & 1) != 0;
    }

    static int height(int nativeHeight, float density) {
        return height(nativeHeight, density, 1f);
    }

    static int height(int nativeHeight, float density, float fontScale) {
        if (nativeHeight <= 0 || !(density > 0f) || Float.isInfinite(density)) return nativeHeight;
        // Keep normal-sized clock / battery icons; only reduce vertical whitespace.
        float textScale = fontScale > 1f && !Float.isInfinite(fontScale) ? fontScale : 1f;
        int minimum = Math.round(28f * density * textScale);
        return Math.min(nativeHeight, Math.max(minimum, TARGET_HEIGHT_PX));
    }
}
