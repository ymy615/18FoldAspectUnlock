package io.github.foldaspect.unlock;

import android.content.pm.ActivityInfo;
import android.os.Build;
import com.android.server.wm.*;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.util.ArrayList;
import java.util.List;

/** Scoped callback tests, not a model of Binder transport or device reconfiguration. */
public final class InnerAspectRegression {
    private static final String PKG = "com.tencent.mm";
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static ActivityRecord app(int user) {
        ActivityRecord ar = new ActivityRecord();
        ar.info.packageName = PKG;
        ar.info.applicationInfo.packageName = PKG;
        ar.info.applicationInfo.uid = user * 100000 + 10123;
        ar.mBoundsCompatControllerStub.mOwner = ar;
        ar.task.activities.add(ar);
        return ar;
    }
    private static final class Harness implements AutoCloseable {
        final MiuiSizeCompatService service = new MiuiSizeCompatService();
        final ActivityTaskManagerService atms = new ActivityTaskManagerService();
        final InnerAspectController inner;
        final WidthModeController width;
        final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        Harness() throws Exception {
            ClassLoader loader = InnerAspectRegression.class.getClassLoader();
            inner = new InnerAspectController(loader);
            width = new WidthModeController(loader, new SplitStatusBarController(loader), inner);
            hooks.add(XposedBridge.hookMethod(width.ratioTarget, width.ratioCallback));
            hooks.add(XposedBridge.hookMethod(width.readyTarget, width.readyCallback));
            if (!inner.legacy) hooks.add(XposedBridge.hookMethod(inner.queryTarget, inner.queryCallback));
            hooks.add(XposedBridge.hookMethod(inner.resolveTarget, inner.resolveCallback));
            hooks.add(XposedBridge.hookMethod(inner.persistTarget, inner.persistCallback));
        }
        void ready() throws Throwable { XposedBridge.invokeHooked(width.readyTarget, service, atms); }
        float query(int user, ActivityRecord record) throws Throwable {
            return (Float) (inner.legacy
                    ? XposedBridge.invokeHooked(inner.queryTarget, service, PKG, user)
                    : XposedBridge.invokeHooked(inner.queryTarget, service, PKG, user, record));
        }
        float resolve(ActivityRecord ar) throws Throwable {
            return (Float) XposedBridge.invokeHooked(inner.resolveTarget, ar.mBoundsCompatControllerStub, ar.info, ar.task);
        }
        void choose(ActivityRecord ar, boolean value) throws Throwable {
            XposedBridge.invokeHooked(inner.persistTarget, service, ar, value);
        }
        void fold(boolean value) {
            atms.folded = value;
            atms.mWindowManager.listener.onDisplayFoldChanged(0, value);
        }
        public void close() { for (XC_MethodHook.Unhook hook : hooks) hook.unhook(); }
    }
    public static void main(String[] args) throws Throwable {
        switch (args[0]) {
            case "inner-session-lifecycle": lifecycle(); break;
            case "inner-session-users": users(); break;
            case "inner-session-guards": guards(); break;
            case "inner-session-restoration": restoration(); break;
            case "inner-session-failures": failures(); break;
            case "inner-session-legacy-21": Build.VERSION.INCREMENTAL = UnlockPolicy.BUILD_21; lifecycle(); break;
            case "inner-session-legacy-22": Build.VERSION.INCREMENTAL = UnlockPolicy.BUILD_22; lifecycle(); break;
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS host scenario: " + args[0]);
    }
    private static void lifecycle() throws Throwable {
        try (Harness h = new Harness()) {
            ActivityRecord ar = app(0);
            float original = h.query(0, ar);
            require(original > 0, "Before registration the native result must survive");
            h.ready();
            require(h.atms.mWindowManager.listener.asBinder() != null, "The retained fold listener must have a Binder identity");
            for (int cycle = 0; cycle < 3; cycle++) {
                h.fold(false);
                require(h.query(0, ar) == 0f && h.query(0, null) == 0f && h.resolve(ar) == 0f,
                        "Each unfold starts fullscreen for both activity and process queries");
                h.choose(ar, true);
                require(h.query(0, ar) > 0f && h.query(0, null) > 0f && h.resolve(ar) > 0f,
                        "An approved manual entry must enable both query paths and activity geometry");
                h.choose(ar, false);
                require(h.query(0, ar) == 0f && h.resolve(ar) == 0f, "Manual exit returns to fullscreen in this unfold");
                h.choose(ar, true);
                h.fold(true);
                require(h.query(0, ar) == (h.inner.legacy ? 20f / 9f : original) && h.resolve(ar) > 0f,
                        "Refold restores the outer native/width-policy ratio without retaining the inner choice");
            }
            require(h.service.nativePersistCalls == 0 && h.service.settingsWrites == 0
                    && h.service.mWideFoldConfigs.isEmpty(), "Inner choices must never rewrite the native app setting file");
            h.choose(ar, true);
            require(h.service.nativePersistCalls == 1, "An outer-screen choice must still reach native persistence");
        }
    }
    private static void users() throws Throwable {
        try (Harness h = new Harness()) {
            h.ready();
            ActivityRecord first = app(0), second = app(10);
            h.choose(first, true);
            require(h.query(0, first) > 0 && h.query(10, second) == 0 && h.resolve(second) == 0,
                    "One Android user's choice must not enable another user's application");
            ActivityRecord other = app(0);
            other.info.packageName = "com.example.other";
            require(h.resolve(other) == 0, "Another package starts fullscreen");
        }
    }
    private static void guards() throws Throwable {
        try (Harness h = new Harness()) {
            h.ready();
            ActivityRecord ar = app(0);
            h.atms.folded = true;
            require(h.query(0, ar) > 0 && h.resolve(ar) > 0, "Outer results remain native");
            h.atms.folded = false; h.atms.portrait = false;
            require(h.query(0, ar) > 0 && h.resolve(ar) > 0, "Landscape qualification stays native");
            h.atms.portrait = true;
            h.service.gameRatio = 16f / 9f;
            require(h.query(0, ar) > 0 && h.resolve(ar) > 0, "Independent game ratio stays native");
            h.service.gameRatio = -1f;
            ar.displayId = 1;
            require(h.query(0, ar) > 0 && h.resolve(ar) > 0, "Secondary display remains native");
            ar.displayId = 0; ar.windowingMode = 6;
            require(h.query(0, ar) > 0 && h.resolve(ar) > 0, "Split activity geometry remains native");
            ar.windowingMode = 1; ar.info.applicationInfo.uid = 1000;
            require(h.query(0, ar) > 0 && h.resolve(ar) > 0, "Privileged activity must retain its native behavior");
            ar.info.applicationInfo.uid = 10123; ar.info.packageName = "com.miui.home";
            require(h.resolve(ar) > 0, "Home activity must retain native geometry");
        }
    }
    private static void restoration() throws Throwable {
        try (Harness h = new Harness()) {
            h.ready();
            ActivityRecord fullscreen = app(0), compat = app(0);
            fullscreen.task.mTaskStub.fullscreen = true;
            compat.task.mTaskStub.fullscreen = false;
            h.choose(fullscreen, true);
            fullscreen.task.mTaskStub.setMiuiSizeCompatRuntimeFullscreen(false); // Native runtime action after persistence.
            h.choose(fullscreen, false);
            fullscreen.task.mTaskStub.setMiuiSizeCompatRuntimeFullscreen(true);
            h.choose(fullscreen, true);
            fullscreen.task.mTaskStub.setMiuiSizeCompatRuntimeFullscreen(false);
            h.choose(compat, false);
            compat.task.mTaskStub.setMiuiSizeCompatRuntimeFullscreen(true);
            h.service.beforeApply = () -> {
                require(Thread.holdsLock(h.atms.mGlobalLock), "Fold refresh must hold the WM lock");
                require(fullscreen.task.mTaskStub.fullscreen && !compat.task.mTaskStub.fullscreen,
                        "Restore every task flag before the first native activity refresh");
                require(h.service.settingsWrites == 0, "Fold refresh must not rewrite per-app settings");
            };
            h.fold(true);
            require(fullscreen.task.mTaskStub.fullscreen && !compat.task.mTaskStub.fullscreen,
                    "Refold restores each task's original flag, including both values and repeated manual changes");
            require(fullscreen.applyCalls == 1 && compat.applyCalls == 1,
                    "The completed fold restoration must refresh affected visible activity configurations");
            h.fold(false);
            require(h.query(0, fullscreen) == 0 && h.resolve(compat) == 0, "Another unfold resets temporary choices");
        }
    }
    private static void failures() throws Throwable {
        try (Harness h = new Harness()) {
            ActivityRecord ar = app(0);
            h.atms.mWindowManager.failRegistration = true;
            h.ready();
            require(h.query(0, ar) > 0 && h.resolve(ar) > 0, "Failed listener registration must not activate partial suppression");
            h.atms.mWindowManager.failRegistration = false;
            h.inner.bind(h.service);
            RuntimeException nativeFailure = new IllegalStateException("native ratio query failed");
            h.service.queryFailure = nativeFailure;
            try { h.query(0, ar); throw new AssertionError("Original native failure must propagate"); }
            catch (RuntimeException error) { require(error == nativeFailure, "Preserve original exception identity"); }
            h.service.queryFailure = null;
            h.service.packageRatio = Float.NaN;
            require(Float.isNaN(h.query(0, ar)), "A non-finite native result must not be fabricated as valid geometry");
            h.service.packageRatio = 0f;
            require(h.query(0, ar) == 0, "A disabled outer ratio starts fullscreen");
            ar.task.mTaskStub.fullscreen = true;
            h.choose(ar, true);
            ar.task.mTaskStub.fullscreen = false;
            require(h.query(0, ar) > 0, "Manual inner entry must work even when the outer saved ratio is zero");
            ar.task.mTaskStub.failSet = true;
            h.fold(true); // Callback errors must never escape into system-server dispatch.
            require(!ar.task.mTaskStub.fullscreen, "A failed native flag write must not be fabricated as a successful restore");
            ar.task.mTaskStub.failSet = false;
            h.query(0, ar);
            require(ar.task.mTaskStub.fullscreen, "An outer query retries the saved task restoration");
        }
    }
}
