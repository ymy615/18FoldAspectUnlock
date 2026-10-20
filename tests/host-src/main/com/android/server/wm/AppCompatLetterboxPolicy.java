package com.android.server.wm;

import com.android.internal.statusbar.LetterboxDetails;

public final class AppCompatLetterboxPolicy {
    public ActivityRecord mActivityRecord;
    public LetterboxDetails nativeResult = new LetterboxDetails();
    public RuntimeException originalFailure;

    LetterboxDetails getLetterboxDetails() {
        if (originalFailure != null) throw originalFailure;
        return nativeResult;
    }
}
