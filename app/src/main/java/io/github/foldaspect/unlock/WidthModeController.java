package io.github.foldaspect.unlock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Firmware-specific width hooks and a signature-protected settings endpoint. */
final class WidthModeController {
    final Method ratioTarget, scaleTarget, readyTarget;
    final XC_MethodHook ratioCallback, scaleCallback, readyCallback;
    private final Field contextField, handlerField, atmsField, globalLock, rootField,
            controllerField, infoField, processField, cachedScale;
    private final Method outerDisplay, globalConfiguration, displayId, windowingMode,
            outerScene, compatMode, splitScene, gameScene, enumerateActivities,
            visible, attached, processUpdate, applyConfiguration, getTask, taskChanged,
            atmsStub, scaleMode;
    private volatile int mode = WidthModePolicy.NARROW;
    private volatile Object service;
    private boolean registered;

    WidthModeController(ClassLoader loader) throws Exception {
        Class<?> serviceClass = cls(loader, "com.android.server.wm.MiuiSizeCompatService");
        Class<?> utils = cls(loader, "com.android.server.wm.BoundsCompatUtils");
        Class<?> atms = cls(loader, "com.android.server.wm.ActivityTaskManagerService");
        Class<?> record = cls(loader, "com.android.server.wm.ActivityRecord");
        Class<?> bounds = cls(loader, "com.android.server.wm.BoundsCompatController");
        Class<?> root = cls(loader, "com.android.server.wm.RootWindowContainer");
        Class<?> process = cls(loader, "com.android.server.wm.WindowProcessController");
        Class<?> task = cls(loader, "com.android.server.wm.Task");
        Class<?> stub = cls(loader, "com.android.server.wm.ActivityTaskManagerServiceStub");
        ratioTarget = method(serviceClass, "getWideFoldDefaultRatio", float.class, record);
        scaleTarget = method(utils, "getGlobalScaleByName", float.class,
                String.class, int.class, Rect.class, boolean.class, float.class);
        readyTarget = method(serviceClass, "onSystemReady", void.class, atms);
        contextField = field(serviceClass, "mContext", Context.class);
        handlerField = field(serviceClass, "mFgHandler", Handler.class);
        atmsField = field(serviceClass, "mAtms", atms);
        globalLock = field(atms, "mGlobalLock", cls(loader, "com.android.server.wm.WindowManagerGlobalLock"));
        rootField = field(atms, "mRootWindowContainer", root);
        controllerField = record.getDeclaredField("mBoundsCompatControllerStub");
        controllerField.setAccessible(true);
        infoField = field(record, "info", ActivityInfo.class);
        processField = field(record, "app", process);
        cachedScale = field(bounds, "mGlobalScale", float.class);
        outerDisplay = method(serviceClass, "isOuterCompatDisplay", boolean.class, atms);
        globalConfiguration = inheritedMethod(atms, "getGlobalConfiguration", Configuration.class);
        displayId = inheritedMethod(record, "getDisplayId", int.class);
        windowingMode = inheritedMethod(record, "getWindowingMode", int.class);
        outerScene = method(bounds, "isOuterCompatScene", boolean.class);
        compatMode = method(bounds, "inMiuiSizeCompatMode", boolean.class);
        splitScene = method(bounds, "isInMultipleSplitWindow", boolean.class);
        gameScene = method(bounds, "isSizeCompatGameApp", boolean.class);
        enumerateActivities = inheritedMethod(root, "forAllActivities", void.class, Consumer.class);
        visible = inheritedMethod(record, "isVisible", boolean.class);
        attached = method(record, "attachedToProcess", boolean.class);
        processUpdate = method(process, "updateBoundsCompatStateIfNeeded", void.class);
        applyConfiguration = method(serviceClass, "applyWideFoldConfigLocked", void.class,
                record, boolean.class, int.class);
        getTask = method(record, "getTask", task);
        taskChanged = inheritedMethod(task, "onSizeCompatActivityChanged", void.class);
        atmsStub = method(stub, "get", stub);
        scaleMode = method(stub, "getScaleMode", int.class, String.class);

        ratioCallback = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || mode != WidthModePolicy.WIDE || !(p.getResult() instanceof Float)) return;
                try {
                    Object ar = p.args[0];
                    // Null ActivityRecord is the real process/package query path. Its
                    // native caller has already decided whether that package is enabled.
                    if (isOuterPortrait() && (ar == null || regularActivity(ar, false))) {
                        p.setResult(WidthModePolicy.LEGACY_RATIO);
                    }
                } catch (Throwable error) { log("ratio skipped", error); }
            }
        };
        scaleCallback = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || mode != WidthModePolicy.WIDE || !(p.getResult() instanceof Float)) return;
                float nativeScale = (Float) p.getResult();
                Rect display = (Rect) p.args[2];
                float ratio = (Float) p.args[4];
                // This is the native ordinary-outer fallback. Dedicated game/cloud
                // scales bypass it. Distinguish the explicit scale-mode-3 opt-out
                // from a -1 caused by the new 1080px cap with the legacy ratio.
                if (!Float.isFinite(nativeScale) || (nativeScale <= 0f && nativeScale != -1f)
                        || !(Boolean) p.args[3] || display == null
                        || !UnlockPolicy.mayUnlockPackage((String) p.args[0])
                        || Math.abs(ratio - WidthModePolicy.LEGACY_RATIO) > 0.0001f) return;
                try {
                    if (!isOuterPortrait()) return;
                    if ((Integer) scaleMode.invoke(atmsStub.invoke(null), (String) p.args[0]) == 3) return;
                    float legacy = WidthModePolicy.legacyScale(display.width(), display.height(), ratio);
                    if (legacy > 0f) p.setResult(legacy);
                } catch (Throwable error) { log("scale skipped", error); }
            }
        };
        readyCallback = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                try { register(p.thisObject); }
                catch (Throwable error) { log("width control unavailable", error); }
            }
        };
    }

    private void register(Object owner) throws Exception {
        if (registered) return;
        Context context = (Context) contextField.get(owner);
        Handler handler = (Handler) handlerField.get(owner);
        if (context == null || handler == null || atmsField.get(owner) == null) return;
        int saved = Settings.Global.getInt(context.getContentResolver(), WidthModePolicy.SETTING, WidthModePolicy.NARROW);
        mode = WidthModePolicy.valid(saved) ? saved : WidthModePolicy.NARROW;
        service = owner;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!WidthModePolicy.ACTION.equals(intent.getAction())) return;
                try {
                    int refreshed = 0, failures = 0;
                    if (intent.getBooleanExtra(WidthModePolicy.EXTRA_WRITE, false)) {
                        int requested = intent.getIntExtra(WidthModePolicy.EXTRA_MODE, -1);
                        if (!WidthModePolicy.valid(requested)) return;
                        Object atms = atmsField.get(owner);
                        Object lock = atms == null ? null : globalLock.get(atms);
                        if (lock == null) return;
                        if (requested != mode && !Settings.Global.putInt(context.getContentResolver(), WidthModePolicy.SETTING, requested)) return;
                        synchronized (lock) {
                            mode = requested;
                            try {
                                // Same-mode writes may retry a previously deferred refresh.
                                int[] result = refreshActivities(owner, atms);
                                refreshed = result[0];
                                failures = result[1];
                            } catch (Exception error) {
                                // The preference is already saved. A refresh failure
                                // must be acknowledged as saved/deferred, not lost.
                                failures++;
                                log("refresh deferred", error);
                            }
                        }
                        XposedBridge.log("[FoldAspectUnlock] width=" + (mode == WidthModePolicy.WIDE ? "wide" : "narrow")
                                + "; refreshed=" + refreshed + "; deferred=" + failures);
                    }
                    Bundle response = new Bundle();
                    response.putInt(WidthModePolicy.EXTRA_MODE, mode);
                    response.putInt("refreshed", refreshed);
                    response.putInt("deferred", failures);
                    setResultExtras(response);
                    setResultCode(Activity.RESULT_OK);
                } catch (Throwable error) { log("width request failed", error); }
            }
        }, new IntentFilter(WidthModePolicy.ACTION), WidthModePolicy.PERMISSION, handler, Context.RECEIVER_EXPORTED);
        registered = true;
        XposedBridge.log("[FoldAspectUnlock] WIDTH READY; mode=" + (mode == WidthModePolicy.WIDE ? "wide" : "narrow"));
    }

    private boolean isOuterPortrait() throws Exception {
        Object owner = service;
        if (owner == null) return false;
        Object atms = atmsField.get(owner);
        if (atms == null || !(Boolean) outerDisplay.invoke(null, atms)) return false;
        return ((Configuration) globalConfiguration.invoke(atms)).orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private boolean regularActivity(Object ar, boolean requireCompat) throws Exception {
        if ((Integer) displayId.invoke(ar) != 0 || (Integer) windowingMode.invoke(ar) != 1) return false;
        ActivityInfo info = (ActivityInfo) infoField.get(ar);
        if (info == null || info.applicationInfo == null
                || !UnlockPolicy.mayUnlockApplication(info.packageName, info.applicationInfo.uid)) return false;
        Object policy = controllerField.get(ar);
        return policy != null && (Boolean) outerScene.invoke(policy)
                && !(Boolean) splitScene.invoke(policy) && !(Boolean) gameScene.invoke(policy)
                && (!requireCompat || (Boolean) compatMode.invoke(policy));
    }

    private int[] refreshActivities(Object owner, Object atms) throws Exception {
        if (!isOuterPortrait()) return new int[]{0, 0};
        List<Object> targets = new ArrayList<>();
        enumerateActivities.invoke(rootField.get(atms), (Consumer<Object>) ar -> {
            try { if (regularActivity(ar, true)) targets.add(ar); }
            catch (Exception error) { log("activity skipped", error); }
        });
        Set<Object> processes = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        Set<Object> tasks = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        List<Object> prepared = new ArrayList<>();
        int failures = 0, refreshed = 0;
        for (Object ar : targets) try {
            cachedScale.setFloat(controllerField.get(ar), -1f);
            Object process = processField.get(ar);
            if (process != null) processes.add(process);
            prepared.add(ar);
        } catch (Exception error) { failures++; log("cache refresh deferred", error); }
        for (Object process : processes) try { processUpdate.invoke(process); }
        catch (Exception error) { failures++; log("process refresh deferred", error); }
        for (Object ar : prepared) try {
            if (!(Boolean) visible.invoke(ar) || !(Boolean) attached.invoke(ar)) continue;
            // Native recompute/ensure (or relaunch according to the app manifest),
            // without touching the native persisted ratio or runtime-fullscreen flag.
            applyConfiguration.invoke(owner, ar, true, 3328);
            Object task = getTask.invoke(ar);
            if (task != null) tasks.add(task);
            refreshed++;
        } catch (Exception error) { failures++; log("activity refresh deferred", error); }
        for (Object task : tasks) try { taskChanged.invoke(task); }
        catch (Exception error) { failures++; log("task refresh deferred", error); }
        return new int[]{refreshed, failures};
    }

    private static void log(String message, Throwable error) {
        XposedBridge.log("[FoldAspectUnlock] " + message + ": " + error);
    }

    private static Class<?> cls(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
    private static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        Field result = owner.getDeclaredField(name);
        if (result.getType() != type) throw new NoSuchFieldException(name + " changed type");
        result.setAccessible(true);
        return result;
    }
    private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... args) throws Exception {
        Method result = owner.getDeclaredMethod(name, args);
        if (result.getReturnType() != returns) throw new NoSuchMethodException(name + " changed return type");
        result.setAccessible(true);
        return result;
    }
    private static Method inheritedMethod(Class<?> owner, String name, Class<?> returns, Class<?>... args) throws Exception {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try { return method(current, name, returns, args); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
