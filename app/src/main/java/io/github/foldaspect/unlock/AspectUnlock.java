package io.github.foldaspect.unlock;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Hooks only the inspected HyperOS system-server policy, never every app process. */
public final class AspectUnlock implements IXposedHookLoadPackage {
    private static final String TAG = "[FoldAspectUnlock] ";
    private static boolean installed;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!"android".equals(load.packageName)) return;
        if (!UnlockPolicy.supports(Build.DEVICE, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL)) {
            XposedBridge.log(TAG + "SKIPPED: unsupported firmware " + Build.DEVICE + " / "
                    + Build.VERSION.SDK_INT + " / " + Build.VERSION.INCREMENTAL);
            return;
        }
        synchronized (AspectUnlock.class) {
            if (installed) return;
            List<XC_MethodHook.Unhook> applied = new ArrayList<>();
            try {
                // All targets must exist with their exact inspected signatures before installing any hook.
                Class<?> service = Class.forName("com.android.server.wm.MiuiSizeCompatService", false, load.classLoader);
                Class<?> utils = Class.forName("com.android.server.wm.BoundsCompatUtils", false, load.classLoader);
                Method disabled = booleanMethod(service, "isWideFoldDisabledPackage", String.class);
                Method fullscreen = booleanMethod(service, "shouldUseFullscreenBypassInternal", String.class);
                Method systemApp = booleanMethod(utils, "isWideFoldSystemApp", ApplicationInfo.class);

                applied.add(XposedBridge.hookMethod(disabled, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (UnlockPolicy.mayUnlockPackage((String) p.args[0])) p.setResult(false);
                    }
                }));
                applied.add(XposedBridge.hookMethod(fullscreen, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (UnlockPolicy.mayUnlockActivity((String) p.args[0])) p.setResult(false);
                    }
                }));
                applied.add(XposedBridge.hookMethod(systemApp, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        ApplicationInfo info = (ApplicationInfo) p.args[0];
                        if (info != null && UnlockPolicy.mayUnlockApplication(info.packageName, info.uid)) {
                            p.setResult(false);
                        }
                    }
                }));
                installed = true;
                XposedBridge.log(TAG + "ACTIVE 3/3 policy hooks; build=" + Build.VERSION.INCREMENTAL);
            } catch (Throwable failure) {
                for (XC_MethodHook.Unhook hook : applied) {
                    try { hook.unhook(); } catch (Throwable ignored) {}
                }
                XposedBridge.log(TAG + "INACTIVE: hook setup failed; reverted partial setup");
                XposedBridge.log(failure);
            }
        }
    }

    private static Method booleanMethod(Class<?> owner, String name, Class<?>... args) throws Exception {
        Method method = owner.getDeclaredMethod(name, args);
        if (method.getReturnType() != boolean.class) throw new NoSuchMethodException(name + " must return boolean");
        method.setAccessible(true);
        return method;
    }
}
