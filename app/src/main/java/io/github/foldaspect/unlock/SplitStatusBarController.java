package io.github.foldaspect.unlock;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.WindowInsets;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Keeps only the status bar visible in the native inner-screen left/right split. */
final class SplitStatusBarController {
    final Method controlTarget, barVisibility, hideTypes, targetInsets, dispatchTarget;
    final XC_MethodHook controlCallback, visibilityCallback, hideCallback, insetsCallback, dispatchCallback;
    private final Field display, policy, showingTarget, fakeStatusTarget, barOwner, barId,
            focus, atm, attrs, controllerDisplay;
    private final Method displayId, configuration, screenType, displayBounds, keyguard,
            splitActive, windowMode, windowDisplay, windowTask, taskBounds,
            sourceSize, sourceAt, sourceType, sourceFrame, sourceVisible, sourceIsVisible, addSource,
            displayInsets, enumerateActivities, activityVisible, activityRequested, mainWindow;
    private final Constructor<?> copyState, copySource;
    private final Class<?> stateClass, windowClass;
    private boolean loggedFailure;
    private boolean loggedRouting;
    private boolean loggedHomeScene;

    SplitStatusBarController(ClassLoader loader) throws Exception {
        Class<?> insets = Class.forName("com.android.server.wm.InsetsPolicy", false, loader);
        Class<?> bar = Class.forName("com.android.server.wm.InsetsPolicy$BarWindow", false, loader);
        Class<?> target = Class.forName("com.android.server.wm.InsetsControlTarget", false, loader);
        Class<?> dc = Class.forName("com.android.server.wm.DisplayContent", false, loader);
        Class<?> dp = Class.forName("com.android.server.wm.DisplayPolicy", false, loader);
        Class<?> atms = Class.forName("com.android.server.wm.ActivityTaskManagerService", false, loader);
        Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
        windowClass = window;
        Class<?> stateController = Class.forName("com.android.server.wm.InsetsStateController", false, loader);
        Class<?> task = Class.forName("com.android.server.wm.Task", false, loader);
        Class<?> record = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
        stateClass = Class.forName("android.view.InsetsState", false, loader);
        Class<?> source = Class.forName("android.view.InsetsSource", false, loader);

        controlTarget = method(insets, "getStatusControlTargetInner", target, window, boolean.class);
        barVisibility = method(bar, "setVisible", void.class, boolean.class);
        hideTypes = method(insets, "hideSystemBars", void.class, int.class);
        targetInsets = method(insets, "enforceInsetsPolicyForTarget", stateClass,
                window, stateClass);
        dispatchTarget = method(stateController, "onBarControlTargetChanged", void.class,
                target, target, target, target);
        copyState = stateClass.getConstructor(stateClass, boolean.class);
        copySource = source.getConstructor(source);
        sourceSize = method(stateClass, "sourceSize", int.class);
        sourceAt = method(stateClass, "sourceAt", source, int.class);
        sourceType = method(source, "getType", int.class);
        sourceFrame = method(source, "getFrame", Rect.class);
        sourceVisible = method(source, "setVisible", source, boolean.class);
        sourceIsVisible = method(source, "isVisible", boolean.class);
        addSource = method(stateClass, "addSource", void.class, source);
        display = field(insets, "mDisplayContent", dc);
        policy = field(insets, "mPolicy", dp);
        showingTarget = field(insets, "mShowingPermanentControlTarget", target);
        fakeStatusTarget = field(insets, "mFakeStatusControlTarget", target);
        controllerDisplay = field(stateController, "mDisplayContent", dc);
        barOwner = field(bar, "this$0", insets);
        barId = field(bar, "mId", int.class);
        focus = field(dc, "mCurrentFocus", window);
        atm = field(dc, "mAtmService", atms);
        attrs = field(window, "mAttrs", WindowManager.LayoutParams.class);
        displayId = inheritedMethod(dc, "getDisplayId", int.class);
        configuration = inheritedMethod(dc, "getConfiguration", Configuration.class);
        screenType = method(Configuration.class, "getScreenType", int.class);
        displayBounds = inheritedMethod(dc, "getBounds", Rect.class);
        displayInsets = inheritedMethod(dc, "getInsetsPolicy", insets);
        enumerateActivities = inheritedMethod(dc, "forAllActivities", void.class, Consumer.class);
        activityVisible = inheritedMethod(record, "isVisible", boolean.class);
        activityRequested = inheritedMethod(record, "isVisibleRequested", boolean.class);
        mainWindow = method(record, "findMainWindow", window, boolean.class);
        keyguard = method(dp, "isKeyguardShowing", boolean.class);
        splitActive = method(atms, "isInSplitScreenWindowingMode", boolean.class);
        windowMode = inheritedMethod(window, "getWindowingMode", int.class);
        windowDisplay = inheritedMethod(window, "getDisplayId", int.class);
        windowTask = inheritedMethod(window, "getTask", task);
        taskBounds = inheritedMethod(task, "getBounds", Rect.class);

        controlCallback = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                try {
                    // Native DisplayPolicy can pass a null or stale fullscreen candidate
                    // here while mCurrentFocus is a real split app. Use the actual focus.
                    if (qualifiesCurrent(p.thisObject) && !isSystemTarget(p.args[0])) {
                        Object permanent = showingTarget.get(p.thisObject);
                        if (permanent != null) p.setResult(permanent);
                    }
                } catch (Throwable error) { logFailure(error); }
            }
        };
        dispatchCallback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    Object dc = controllerDisplay.get(p.thisObject);
                    Object owner = dc == null ? null : displayInsets.invoke(dc);
                    if (!qualifiesCurrent(owner) || isSystemTarget(p.args[0])) return;
                    Object permanent = showingTarget.get(owner);
                    if (permanent == null) return;
                    // This is the final source-control dispatch, after native selection.
                    // Keep the policy fake-status field consistent with the source maps.
                    // Navigation and fake-navigation targets are deliberately untouched.
                    fakeStatusTarget.set(owner, null);
                    p.args[0] = permanent;
                    p.args[1] = null;
                    if (!loggedRouting) {
                        loggedRouting = true;
                        XposedBridge.log("[FoldAspectUnlock] SPLIT controls=permanent; nav=preserved");
                    }
                } catch (Throwable error) { logFailure(error); }
            }
        };
        visibilityCallback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    // BarWindow id 1 is status; id 2 is navigation. Keep native nav behavior.
                    if (barId.getInt(p.thisObject) != 1) return;
                    Object owner = barOwner.get(p.thisObject);
                    if (qualifiesCurrent(owner)) p.args[0] = true;
                } catch (Throwable error) { logFailure(error); }
            }
        };
        hideCallback = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (qualifiesCurrent(p.thisObject)) {
                        p.args[0] = (Integer) p.args[0] & ~WindowInsets.Type.statusBars();
                    }
                } catch (Throwable error) { logFailure(error); }
            }
        };
        insetsCallback = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !stateClass.isInstance(p.getResult())
                        || !stateClass.isInstance(p.args[1])) return;
                try {
                    if (!qualifiesCurrent(p.thisObject) || !qualifies(p.thisObject, p.args[0])) return;
                    Object original = p.args[1];
                    Object result = p.getResult();
                    // The firmware strips system-bar sources for split app windows. Restore
                    // the native status source only; never modify borrowed/shared InsetsState.
                    Object copy = null;
                    boolean existing = false;
                    for (int i = 0; i < (Integer) sourceSize.invoke(result); i++) {
                        Object source = sourceAt.invoke(result, i);
                        if ((Integer) sourceType.invoke(source) != WindowInsets.Type.statusBars()) continue;
                        existing = true;
                        if (!(Boolean) sourceIsVisible.invoke(source)) {
                            if (copy == null) copy = copyState.newInstance(result, true);
                            sourceVisible.invoke(sourceAt.invoke(copy, i), true);
                        }
                    }
                    if (existing) {
                        if (copy != null) p.setResult(copy);
                        return;
                    }
                    for (int i = 0; i < (Integer) sourceSize.invoke(original); i++) {
                        Object source = sourceAt.invoke(original, i);
                        if ((Integer) sourceType.invoke(source) != WindowInsets.Type.statusBars()) continue;
                        Rect frame = (Rect) sourceFrame.invoke(source);
                        if (frame == null || frame.width() <= 0 || frame.height() <= 0) continue;
                        if (copy == null) copy = copyState.newInstance(result, true);
                        Object restored = copySource.newInstance(source);
                        sourceVisible.invoke(restored, true);
                        addSource.invoke(copy, restored);
                    }
                    if (copy != null) p.setResult(copy);
                } catch (Throwable error) { logFailure(error); }
            }
        };
    }

    private boolean qualifiesCurrent(Object owner) throws Exception {
        Object dc = splitDisplay(owner);
        if (dc == null) return false;
        Object current = focus.get(dc);
        if (qualifiesWindow(dc, current)) return true;
        if (current != null && !isHomeFocus(current)) return false;

        // Closing either split app can focus the fullscreen launcher behind the
        // empty stage. Prove that a visible ordinary half-screen app remains;
        // a launcher focus alone must never enable the split exception.
        boolean[] eligible = {false};
        enumerateActivities.invoke(dc, (Consumer<Object>) record -> {
            if (eligible[0] || record == null) return;
            try {
                if ((Boolean) activityVisible.invoke(record)
                        && (Boolean) activityRequested.invoke(record)) {
                    eligible[0] = qualifiesWindow(dc, mainWindow.invoke(record, false));
                }
            } catch (Exception error) {
                throw new IllegalStateException("split activity inspection failed", error);
            }
        });
        if (eligible[0] && !loggedHomeScene) {
            loggedHomeScene = true;
            XposedBridge.log("[FoldAspectUnlock] SPLIT scene=app-and-home; visible-stage=confirmed");
        }
        return eligible[0];
    }

    private boolean isHomeFocus(Object window) throws Exception {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) attrs.get(window);
        return params != null && params.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                && "com.miui.home".equals(params.packageName)
                && (Integer) windowMode.invoke(window) == 1
                && (Integer) windowDisplay.invoke(window) == 0;
    }

    private boolean isSystemTarget(Object target) throws Exception {
        if (!windowClass.isInstance(target)) return false;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) attrs.get(target);
        return params == null || params.type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
    }

    private Object splitDisplay(Object owner) throws Exception {
        if (owner == null) return null;
        Object dc = display.get(owner);
        Object dp = policy.get(owner);
        if (dc == null || dp == null || (Integer) displayId.invoke(dc) != 0
                || (Boolean) keyguard.invoke(dp)) return null;
        Object config = configuration.invoke(dc);
        Object service = atm.get(dc);
        if (config == null || (Integer) screenType.invoke(config) != 0
                || service == null || !(Boolean) splitActive.invoke(service)) return null;
        return dc;
    }

    private boolean qualifies(Object owner, Object window) throws Exception {
        Object dc = splitDisplay(owner);
        return dc != null && qualifiesWindow(dc, window);
    }

    private boolean qualifiesWindow(Object dc, Object window) throws Exception {
        if (window == null) return false;
        if ((Integer) windowMode.invoke(window) != 6 || (Integer) windowDisplay.invoke(window) != 0) return false;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) attrs.get(window);
        if (params == null || params.type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                || !UnlockPolicy.mayUnlockPackage(params.packageName)) return false;
        Object task = windowTask.invoke(window);
        Rect whole = (Rect) displayBounds.invoke(dc);
        Rect half = task == null ? null : (Rect) taskBounds.invoke(task);
        if (whole == null || half == null || whole.width() <= 0 || whole.height() <= 0
                || half.width() <= 0 || half.width() >= whole.width()
                || half.top != whole.top || half.bottom != whole.bottom
                || half.left < whole.left || half.right > whole.right) return false;
        // A real side-by-side stage touches exactly one vertical display edge.
        // Fullscreen, top/bottom stages, freeform and embedded app panels fail this test.
        return (half.left == whole.left) != (half.right == whole.right);
    }

    private void logFailure(Throwable error) {
        if (!loggedFailure) {
            loggedFailure = true;
            XposedBridge.log("[FoldAspectUnlock] split status bar skipped: " + error);
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
        Field result = owner.getDeclaredField(name);
        if (result.getType() != type) throw new NoSuchFieldException(name + " has changed type");
        result.setAccessible(true);
        return result;
    }

    private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... args)
            throws Exception {
        Method result = owner.getDeclaredMethod(name, args);
        if (result.getReturnType() != returns) throw new NoSuchMethodException(name + " has changed return type");
        result.setAccessible(true);
        return result;
    }

    private static Method inheritedMethod(Class<?> owner, String name, Class<?> returns, Class<?>... args)
            throws Exception {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try { return method(current, name, returns, args); }
            catch (NoSuchMethodException missing) {
                try { current.getDeclaredMethod(name, args); }
                catch (NoSuchMethodException absent) { continue; }
                throw missing;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }
}
