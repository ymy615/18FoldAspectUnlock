package io.github.foldaspect.unlock;

/** Independent persistent choice; does not change width or native per-app settings. */
final class SplitStatusBarPolicy {
    static final int DISABLED = 0;
    static final int ENABLED = 1;
    static final int DEFAULT = ENABLED;
    static final String SETTING = "fold_aspect_unlock_split_status_bar";
    static final String EXTRA_MODE = "split_mode";
    static final String EXTRA_WRITE = "split_write";

    private SplitStatusBarPolicy() {}
    static boolean valid(int mode) { return mode == DISABLED || mode == ENABLED; }
}
