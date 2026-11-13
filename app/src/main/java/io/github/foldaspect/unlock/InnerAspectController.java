package io.github.foldaspect.unlock;

import android.content.pm.ActivityInfo;
import android.os.Binder;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;

/** Inner choices last only for one unfold; native entry guards remain authoritative. */
final class InnerAspectController {
    final Method queryTarget, resolveTarget, persistTarget;
    final XC_MethodHook queryCallback, resolveCallback, persistCallback;
    final boolean legacy;
    private final Field atmsField, globalLock, wmField, recordInfo, recordTaskController,
            boundsOwner, taskStub, recordProcess;
    private final Method innerDisplay, outerDisplay, gameRatio, defaultRatio, recordDisplay,
            recordMode, recordTask, boundsGame, taskFullscreen, setTaskFullscreen, registerFold,
            taskActivities, recordAttached, recordVisible, processUpdate, applyConfiguration;
    private final Class<?> foldInterface;
    private final Set<String> selected = new HashSet<>();
    private final Map<Object, Boolean> outerTaskFlags = new WeakHashMap<>();
    private final Map<Object, WeakReference<Object>> changedTasks = new WeakHashMap<>();
    private volatile Object service;
    private volatile boolean active;
    private Object listener;
    private boolean loggedFailure;

    InnerAspectController(ClassLoader loader) throws Exception {
        Class<?> svc = cls(loader, "com.android.server.wm.MiuiSizeCompatService");
        Class<?> atms = cls(loader, "com.android.server.wm.ActivityTaskManagerService");
        Class<?> ar = cls(loader, "com.android.server.wm.ActivityRecord");
        Class<?> bounds = cls(loader, "com.android.server.wm.BoundsCompatController");
        Class<?> task = cls(loader, "com.android.server.wm.Task");
        Class<?> stub = cls(loader, "com.android.server.wm.TaskStub");
        Class<?> wm = cls(loader, "com.android.server.wm.WindowManagerService");
        Class<?> process = cls(loader, "com.android.server.wm.WindowProcessController");
        foldInterface = cls(loader, "android.view.IDisplayFoldListener");
        legacy = UnlockPolicy.usesLegacyWidthApi(android.os.Build.VERSION.INCREMENTAL);
        queryTarget = legacy ? method(svc, "getAspectRatioByPackage", float.class, String.class, int.class)
                : method(svc, "getAspectRatio", float.class, String.class, int.class, ar);
        resolveTarget = method(bounds, "resolveFixedAspectRatioIfNeeded", float.class, ActivityInfo.class, task);
        persistTarget = method(svc, "persistWideFoldRatioIfNeededLocked", void.class, ar, boolean.class);
        innerDisplay = method(svc, "isInnerCompatDisplay", boolean.class, atms);
        outerDisplay = method(svc, "isOuterCompatDisplay", boolean.class, atms);
        gameRatio = method(svc, "getGameAspectRatioByPackage", float.class, String.class);
        defaultRatio = legacy ? null : method(svc, "getWideFoldDefaultRatio", float.class, ar);
        atmsField = field(svc, "mAtms", atms);
        globalLock = field(atms, "mGlobalLock", cls(loader, "com.android.server.wm.WindowManagerGlobalLock"));
        wmField = field(atms, "mWindowManager", wm);
        recordInfo = field(ar, "info", ActivityInfo.class);
        recordTaskController = ar.getDeclaredField("mBoundsCompatControllerStub");
        recordTaskController.setAccessible(true);
        boundsOwner = field(bounds, "mOwner", ar);
        taskStub = field(task, "mTaskStub", stub);
        recordProcess = field(ar, "app", process);
        recordDisplay = inheritedMethod(ar, "getDisplayId", int.class);
        recordMode = inheritedMethod(ar, "getWindowingMode", int.class);
        recordTask = inheritedMethod(ar, "getTask", task);
        boundsGame = method(bounds, "isSizeCompatGameApp", boolean.class);
        taskFullscreen = method(stub, "isMiuiSizeCompatRuntimeFullscreen", boolean.class);
        setTaskFullscreen = method(stub, "setMiuiSizeCompatRuntimeFullscreen", void.class, boolean.class);
        registerFold = method(wm, "registerDisplayFoldListener", void.class, foldInterface);
        method(foldInterface, "onDisplayFoldChanged", void.class, int.class, boolean.class);
        if (!android.os.IInterface.class.isAssignableFrom(foldInterface)) throw new ClassCastException("fold listener interface changed");
        method(android.os.IInterface.class, "asBinder", android.os.IBinder.class);
        taskActivities = inheritedMethod(task, "forAllActivities", void.class, Consumer.class);
        recordAttached = method(ar, "attachedToProcess", boolean.class);
        recordVisible = inheritedMethod(ar, "isVisible", boolean.class);
        processUpdate = method(process, "updateBoundsCompatStateIfNeeded", void.class);
        applyConfiguration = method(svc, "applyWideFoldConfigLocked", void.class, ar, boolean.class, int.class);

        queryCallback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { beforeQuery(p); }
            @Override protected void afterHookedMethod(MethodHookParam p) { afterQuery(p); }
        };
        resolveCallback = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!active || p.hasThrowable() || !(p.getResult() instanceof Float)) return;
                try {
                    Object owner = service;
                    Object record = boundsOwner.get(p.thisObject);
                    if (isInner(owner) && eligible(record) && !isGame(owner, packageName(record))) {
                        if (!chosen(key(record))) p.setResult(0f);
                    }
                } catch (Throwable e) { failure(e); }
            }
        };
        persistCallback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!active) return;
                try {
                    Object owner = p.thisObject;
                    restoreIfOuter(owner);
                    Object record = p.args[0];
                    if (!isInner(owner) || !eligible(record) || isGame(owner, packageName(record))) return;
                    Object task = recordTask.invoke(record);
                    Object stub = task == null ? null : taskStub.get(task);
                    if (stub == null) return;
                    boolean previous = (Boolean) taskFullscreen.invoke(stub);
                    String key = key(record);
                    synchronized (InnerAspectController.this) {
                        if (!outerTaskFlags.containsKey(stub)) outerTaskFlags.put(stub, previous);
                        changedTasks.put(stub, new WeakReference<>(task));
                        if ((Boolean) p.args[1]) selected.add(key); else selected.remove(key);
                    }
                    // Native UI has already approved/confirmed this action. Its runtime
                    // resizing and task handling still execute; only the shared disk write
                    // is replaced by this unfold's per-user/per-package choice.
                    p.setResult(null);
                    XposedBridge.log("[FoldAspectUnlock] INNER manual=" + p.args[1]
                            + "; package=" + packageName(record) + "; outerSetting=preserved");
                } catch (Throwable e) { failure(e); }
            }
        };
    }

    void bind(Object owner) throws Exception {
        if (active) return;
        Object atms = atmsField.get(owner);
        Object lock = atms == null ? null : globalLock.get(atms);
        Object wm = atms == null ? null : wmField.get(atms);
        if (lock == null || wm == null) throw new IllegalStateException("inner display service not ready");
        final Binder binder = new Binder();
        Object callback = Proxy.newProxyInstance(foldInterface.getClassLoader(), new Class<?>[]{foldInterface},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "asBinder": return binder;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "FoldAspectUnlockInnerFoldListener";
                        case "onDisplayFoldChanged":
                            try { if ((Integer) args[0] == 0) reset(owner); }
                            catch (Throwable e) { failure(e); }
                            return null;
                        default: return null;
                    }
                });
        service = owner;
        registerFold.invoke(wm, callback);
        listener = callback; // Strong identity for RemoteCallbackList / Binder lifetime.
        active = true;
        XposedBridge.log("[FoldAspectUnlock] INNER READY; unfold=fullscreen; manual=allowed; outer=preserved");
    }

    // Legacy ratio query is already hooked by WidthModeController. Share that
    // callback instead of installing the same method twice.
    void beforeQuery(XC_MethodHook.MethodHookParam p) {
        if (!active) return;
        try { restoreIfOuter(p.thisObject); } catch (Throwable e) { failure(e); }
    }

    void afterQuery(XC_MethodHook.MethodHookParam p) {
        if (!active || p.hasThrowable() || !(p.getResult() instanceof Float)
                || !Float.isFinite((Float) p.getResult())) return;
        try {
            String name = (String) p.args[0];
            Object record = legacy ? null : p.args[2];
            if (!isInner(p.thisObject) || !UnlockPolicy.mayUnlockPackage(name)
                    || isGame(p.thisObject, name) || (record != null && !eligible(record))) return;
            if (!chosen(record == null ? p.args[1] + "#" + name : key(record))) {
                p.setResult(0f);
            } else if ((Float) p.getResult() <= 0f) {
                p.setResult(legacy ? WidthModePolicy.LEGACY_RATIO : (Float) defaultRatio.invoke(null, record));
            }
        } catch (Throwable e) { failure(e); }
    }

    private boolean isInner(Object owner) throws Exception {
        Object atms = owner == null ? null : atmsField.get(owner);
        return atms != null && (Boolean) innerDisplay.invoke(null, atms);
    }

    private boolean eligible(Object record) throws Exception {
        if (record == null || (Integer) recordDisplay.invoke(record) != 0
                || (Integer) recordMode.invoke(record) != 1) return false;
        ActivityInfo info = (ActivityInfo) recordInfo.get(record);
        if (info == null || info.applicationInfo == null
                || !UnlockPolicy.mayUnlockApplication(info.packageName, info.applicationInfo.uid)) return false;
        Object bounds = recordTaskController.get(record);
        return bounds != null && !(Boolean) boundsGame.invoke(bounds);
    }

    private String packageName(Object record) throws Exception {
        return ((ActivityInfo) recordInfo.get(record)).packageName;
    }

    private String key(Object record) throws Exception {
        ActivityInfo info = (ActivityInfo) recordInfo.get(record);
        return (info.applicationInfo.uid / 100000) + "#" + info.packageName;
    }

    private boolean isGame(Object owner, String name) throws Exception {
        return (Float) gameRatio.invoke(owner, name) > 0f;
    }

    private synchronized boolean chosen(String key) { return selected.contains(key); }

    private void restoreIfOuter(Object owner) throws Exception {
        Object atms = owner == null ? null : atmsField.get(owner);
        if (atms != null && (Boolean) outerDisplay.invoke(null, atms)) reset(owner);
    }

    private void reset(Object owner) throws Exception {
        Object atms = atmsField.get(owner);
        Object lock = atms == null ? null : globalLock.get(atms);
        if (lock == null) throw new IllegalStateException("fold reset has no WM lock");
        synchronized (lock) {
            Map<Object, Boolean> saved;
            Map<Object, WeakReference<Object>> tasks;
            synchronized (this) {
                selected.clear();
                if (outerTaskFlags.isEmpty()) return;
                saved = new LinkedHashMap<>(outerTaskFlags);
                tasks = new LinkedHashMap<>(changedTasks);
                outerTaskFlags.clear();
                changedTasks.clear();
            }
            List<Object> refreshTasks = new ArrayList<>();
            for (Map.Entry<Object, Boolean> entry : saved.entrySet()) {
                try {
                    setTaskFullscreen.invoke(entry.getKey(), entry.getValue());
                    WeakReference<Object> reference = tasks.get(entry.getKey());
                    Object task = reference == null ? null : reference.get();
                    if (task != null) refreshTasks.add(task);
                }
                catch (Throwable e) {
                    synchronized (this) {
                        outerTaskFlags.put(entry.getKey(), entry.getValue());
                        changedTasks.put(entry.getKey(), tasks.get(entry.getKey()));
                    }
                    failure(e);
                }
            }
            // The listener may run after the first fold configuration pass. Recompute
            // affected visible activities after restoring flags so outer choices do
            // not wait for an unrelated later layout. Never write native ratio files.
            List<Object> records = new ArrayList<>();
            Set<Object> processes = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            for (Object task : refreshTasks) try {
                taskActivities.invoke(task, (Consumer<Object>) ar -> {
                    try {
                        if (!eligible(ar)) return;
                        Object process = recordProcess.get(ar);
                        if (process != null) processes.add(process);
                        if ((Boolean) recordAttached.invoke(ar) && (Boolean) recordVisible.invoke(ar)) records.add(ar);
                    } catch (Throwable e) { failure(e); }
                });
            } catch (Throwable e) { failure(e); }
            for (Object process : processes) try { processUpdate.invoke(process); }
            catch (Throwable e) { failure(e); }
            for (Object ar : records) try { applyConfiguration.invoke(owner, ar, true, 3328); }
            catch (Throwable e) { failure(e); }
        }
    }

    private void failure(Throwable e) {
        if (!loggedFailure) {
            loggedFailure = true;
            XposedBridge.log("[FoldAspectUnlock] inner session skipped: " + e);
        }
    }

    private static Class<?> cls(ClassLoader loader, String name) throws Exception {
        return Class.forName(name, false, loader);
    }
    private static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        Field value = owner.getDeclaredField(name);
        if (value.getType() != type) throw new NoSuchFieldException(name + " type changed");
        value.setAccessible(true);
        return value;
    }
    private static Method method(Class<?> owner, String name, Class<?> type, Class<?>... args) throws Exception {
        Method value = owner.getDeclaredMethod(name, args);
        if (value.getReturnType() != type) throw new NoSuchMethodException(name + " return type changed");
        value.setAccessible(true);
        return value;
    }
    private static Method inheritedMethod(Class<?> owner, String name, Class<?> type, Class<?>... args) throws Exception {
        // getMethod also traverses the hidden IInterface parent of the fold listener.
        try {
            Method value = owner.getMethod(name, args);
            if (value.getReturnType() != type) throw new NoSuchMethodException(name + " return type changed");
            value.setAccessible(true);
            return value;
        } catch (NoSuchMethodException absent) {
            for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
                try { return method(current, name, type, args); }
                catch (NoSuchMethodException ignored) { }
            }
            throw absent;
        }
    }
}
