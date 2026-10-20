package com.android.internal.statusbar;

import android.graphics.Rect;

/** Opaque metadata payload used to check preservation of the native result. */
public final class LetterboxDetails {
    public final Rect innerBounds = new Rect(0, 0, 1280, 2280);
    public final Rect fullBounds = new Rect(0, 0, 1280, 2560);
}
