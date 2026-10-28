package io.github.foldaspect.unlock;

/** Persistent global width choice; the native per-app enabled state stays separate. */
final class WidthModePolicy {
    static final int NARROW = 0;
    static final int WIDE = 1;
    static final float LEGACY_RATIO = 16f / 9f;
    static final String SETTING = "fold_aspect_unlock_width_mode";
    static final String ACTION = "io.github.foldaspect.unlock.WIDTH_CONTROL";
    static final String PERMISSION = "io.github.foldaspect.unlock.permission.WIDTH_CONTROL";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_WRITE = "write";

    static boolean valid(int mode) { return mode == NARROW || mode == WIDE; }

    static float legacyScale(int width, int height, float ratio) {
        if (width <= 0 || height <= width || !Float.isFinite(ratio) || ratio <= 0f) return -1f;
        float normalized = Math.max(ratio, 1f / ratio);
        int logicalLong = Math.round(width * normalized);
        if (logicalLong <= 0) return -1f;
        float scale = (float) height / logicalLong;
        return scale <= 1f ? scale : -1f;
    }
}
