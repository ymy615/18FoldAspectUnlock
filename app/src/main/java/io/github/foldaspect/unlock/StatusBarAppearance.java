package io.github.foldaspect.unlock;

import android.graphics.Rect;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Uses this firmware's native 40%-opaque black scrim and white status icons. */
final class StatusBarAppearance extends XC_MethodHook {
    final Method opacityTarget, appearanceTarget, letterboxTarget;
    private final Field controllingWindow, activity, controller, attrs;
    private final Field letterboxActivity;
    private final Method displayId, windowingMode, windowFrame, requestedVisible,
            outerScene, compatMode, splitScene, gameScene;
    private final Method mainWindow, focused;
    final XC_MethodHook letterboxCallback;
    private boolean loggedFailure;
    // Exact InsetsFlags values verified in the target framework and SystemUI.
    private static final int OPAQUE_STATUS = 1, LOW_PROFILE = 4, LIGHT_STATUS = 8, SEMI_STATUS = 32;

    StatusBarAppearance(ClassLoader loader) throws Exception {
        Class<?> policy = Class.forName("com.android.server.wm.DisplayPolicy", false, loader);
        Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
        Class<?> record = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
        Class<?> bounds = Class.forName("com.android.server.wm.BoundsCompatController", false, loader);
        Class<?> letterbox = Class.forName("com.android.server.wm.AppCompatLetterboxPolicy", false, loader);
        Class<?> details = Class.forName("com.android.internal.statusbar.LetterboxDetails", false, loader);
        opacityTarget = method(policy, "configureStatusBarOpacity", int.class, int.class);
        appearanceTarget = method(policy, "updateLightNavigationBarLw", int.class, int.class, window);
        controllingWindow = field(policy, "mSystemUiControllingWindow", window);
        activity = field(window, "mActivityRecord", record);
        controller = record.getDeclaredField("mBoundsCompatControllerStub");
        controller.setAccessible(true);
        attrs = field(window, "mAttrs", WindowManager.LayoutParams.class);
        displayId = method(window, "getDisplayId", int.class);
        windowingMode = inheritedMethod(window, "getWindowingMode", int.class);
        windowFrame = method(window, "getFrame", Rect.class);
        requestedVisible = inheritedMethod(window, "isRequestedVisible", boolean.class, int.class);
        outerScene = method(bounds, "isOuterCompatScene", boolean.class);
        compatMode = method(bounds, "inMiuiSizeCompatMode", boolean.class);
        splitScene = method(bounds, "isInMultipleSplitWindow", boolean.class);
        gameScene = method(bounds, "isSizeCompatGameApp", boolean.class);
        letterboxTarget = method(letterbox, "getLetterboxDetails", details);
        letterboxActivity = field(letterbox, "mActivityRecord", record);
        mainWindow = method(record, "findMainWindow", window, boolean.class);
        focused = method(window, "isFocused", boolean.class);
        letterboxCallback = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam parameter) {
                if (parameter.hasThrowable() || parameter.getResult() == null) return;
                try {
                    Object owner = letterboxActivity.get(parameter.thisObject);
                    Object window = owner == null ? null : mainWindow.invoke(owner, false);
                    if (window != null && (Boolean) focused.invoke(window) && qualifies(window)) {
                        // This method supplies only SystemUI recoloring metadata. Otherwise
                        // SystemUI's AOSP-letterbox mapper can clear the requested semi mode.
                        // Actual letterbox geometry, backgrounds and input stay unchanged.
                        parameter.setResult(null);
                    }
                } catch (Throwable error) { logFailure(error); }
            }
        };
    }

    @Override protected void afterHookedMethod(MethodHookParam parameter) {
        if (parameter.hasThrowable() || !(parameter.getResult() instanceof Integer)) return;
        try {
            Object window = controllingWindow.get(parameter.thisObject);
            if (!qualifies(window)) return;
            int original = (Integer) parameter.getResult();
            // DisplayPolicy ORs the native opacity result with the app appearance result.
            // Both paths must select the same mode, while retaining navigation-bar bits.
            // In this SystemUI SEMI_TRANSPARENT itself selects white icons, independently
            // of AppearanceRegions. App attrs and cached region lists remain untouched.
            parameter.setResult((original & ~(OPAQUE_STATUS | LOW_PROFILE | LIGHT_STATUS))
                    | SEMI_STATUS | StatusBarStylePolicy.COMPACT_APPEARANCE);
        } catch (Throwable error) { logFailure(error); }
    }

    private boolean qualifies(Object window) throws Exception {
        if (window == null || (Integer) displayId.invoke(window) != 0
                || (Integer) windowingMode.invoke(window) != 1
                || !(Boolean) requestedVisible.invoke(window, WindowInsets.Type.statusBars())) return false;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) attrs.get(window);
        if (params == null || params.type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                || !UnlockPolicy.mayUnlockPackage(params.packageName)) return false;
        Object owner = activity.get(window);
        if (owner == null) return false;
        Object bounds = controller.get(owner);
        if (bounds == null || !(Boolean) outerScene.invoke(bounds)
                || !(Boolean) compatMode.invoke(bounds) || (Boolean) splitScene.invoke(bounds)
                || (Boolean) gameScene.invoke(bounds)) return false;
        Rect frame = (Rect) windowFrame.invoke(window);
        return frame != null && frame.width() > 0 && frame.height() > frame.width();
    }

    private void logFailure(Throwable error) {
        if (!loggedFailure) {
            loggedFailure = true;
            XposedBridge.log("[FoldAspectUnlock] status appearance skipped: " + error);
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
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
