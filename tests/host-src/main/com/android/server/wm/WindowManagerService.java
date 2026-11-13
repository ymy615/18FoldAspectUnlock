package com.android.server.wm;
public final class WindowManagerService {
    public android.view.IDisplayFoldListener listener;
    public boolean failRegistration;
    public void registerDisplayFoldListener(android.view.IDisplayFoldListener value) {
        if (failRegistration) throw new IllegalStateException("injected fold registration failure");
        listener = value;
    }
}
