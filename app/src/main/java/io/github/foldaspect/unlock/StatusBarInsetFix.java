package io.github.foldaspect.unlock;

import android.graphics.Insets;
import android.graphics.Rect;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Compensates this firmware's second status-inset scaling in the app process. */
final class StatusBarInsetFix extends XC_MethodHook {
    final Method target;
    private final Field activity, controller, globalScale, attrs, childWindow;
    private final Method displayId, windowingMode, windowFrame, outerScene, compatMode, splitScene,
            gameScene, compatScale, sourceSize, sourceAt, sourceType, sourceFrame, visibleFrame,
            attachedInsets, boundingRects, insetsBoundingRects, setBoundingRects;
    private final Constructor<?> copyState;
    private boolean loggedFailure;
    private boolean loggedCorrection;

    StatusBarInsetFix(ClassLoader loader) throws Exception {
        Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
        Class<?> record = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
        Class<?> bounds = Class.forName("com.android.server.wm.BoundsCompatController", false, loader);
        Class<?> state = Class.forName("android.view.InsetsState", false, loader);
        Class<?> source = Class.forName("android.view.InsetsSource", false, loader);
        Class<?> insetRect = Class.forName("android.view.InsetsBoundingRect", false, loader);
        target = method(window, "getCompatInsetsState", state);
        activity = field(window, "mActivityRecord", record);
        controller = record.getDeclaredField("mBoundsCompatControllerStub");
        controller.setAccessible(true);
        globalScale = field(window, "mGlobalScale", float.class);
        attrs = field(window, "mAttrs", WindowManager.LayoutParams.class);
        childWindow = field(window, "mIsChildWindow", boolean.class);
        displayId = method(window, "getDisplayId", int.class);
        windowingMode = inheritedMethod(window, "getWindowingMode", int.class);
        windowFrame = method(window, "getFrame", Rect.class);
        outerScene = method(bounds, "isOuterCompatScene", boolean.class);
        compatMode = method(bounds, "inMiuiSizeCompatMode", boolean.class);
        splitScene = method(bounds, "isInMultipleSplitWindow", boolean.class);
        gameScene = method(bounds, "isSizeCompatGameApp", boolean.class);
        compatScale = method(bounds, "getCurrentMiuiSizeCompatScale", float.class, float.class);
        sourceSize = method(state, "sourceSize", int.class);
        sourceAt = method(state, "sourceAt", source, int.class);
        sourceType = method(source, "getType", int.class);
        sourceFrame = method(source, "getFrame", Rect.class);
        visibleFrame = method(source, "getVisibleFrame", Rect.class);
        attachedInsets = method(source, "getAttachedInsets", Insets.class);
        boundingRects = method(source, "getBoundingRects", Rect[].class);
        insetsBoundingRects = method(source, "getInsetsBoundingRects",
                java.lang.reflect.Array.newInstance(insetRect, 0).getClass());
        setBoundingRects = method(source, "setBoundingRects", source, Rect[].class);
        copyState = state.getConstructor(state, boolean.class);
    }

    @Override protected void afterHookedMethod(MethodHookParam parameter) {
        if (parameter.hasThrowable() || parameter.getResult() == null) return;
        try {
            Object window = parameter.thisObject;
            float scale = globalScale.getFloat(window);
            if (!(scale >= 0.1f && scale < 1f) || childWindow.getBoolean(window)
                    || (Integer) displayId.invoke(window) != 0
                    || (Integer) windowingMode.invoke(window) != 1) return;
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) attrs.get(window);
            if (params == null || params.type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                    || !UnlockPolicy.mayUnlockPackage(params.packageName)) return;
            Object owner = activity.get(window);
            if (owner == null) return;
            Object policy = controller.get(owner);
            if (policy == null || !(Boolean) outerScene.invoke(policy)
                    || !(Boolean) compatMode.invoke(policy) || (Boolean) splitScene.invoke(policy)
                    || (Boolean) gameScene.invoke(policy)
                    || Math.abs((Float) compatScale.invoke(policy, 1f) - scale) > 0.0001f) return;
            Rect client = (Rect) windowFrame.invoke(window);
            if (client == null || client.top != 0 || client.width() <= 0
                    || client.height() <= client.width()) return;

            Object original = parameter.getResult();
            Object copy = null;
            int previousTop = 0, correctedTop = 0;
            int count = (Integer) sourceSize.invoke(original);
            for (int i = 0; i < count; i++) {
                Object source = sourceAt.invoke(original, i);
                if ((Integer) sourceType.invoke(source) != WindowInsets.Type.statusBars()
                        || attachedInsets.invoke(source) != null
                        || insetsBoundingRects.invoke(source) != null
                        || boundingRects.invoke(source) != null) continue;
                Rect frame = (Rect) sourceFrame.invoke(source);
                Rect visible = (Rect) visibleFrame.invoke(source);
                // These are the exact geometry conditions under which InsetsSource calls
                // BoundsCompat.getAppSizeCompatInsets and divides by the scale a second time.
                if (frame.top != client.top || frame.height() <= 0 || frame.bottom >= client.bottom
                        || frame.left > client.left || frame.right < client.right
                        || (visible != null && !visible.equals(frame))) continue;
                int logicalHeight = frame.height();
                // Client helper truncates rather than rounds. Round upward so the resulting
                // safe inset never becomes smaller than the correctly scaled native inset.
                int correctedHeight = (int) Math.ceil(logicalHeight * (double) scale);
                while ((int) (correctedHeight / scale) < logicalHeight) correctedHeight++;
                if (correctedHeight >= logicalHeight) continue;
                int deliveredHeight = (int) (correctedHeight / scale);
                if (copy == null) copy = copyState.newInstance(original, true);
                Object adjusted = sourceAt.invoke(copy, i);
                Rect adjustedFrame = (Rect) sourceFrame.invoke(adjusted);
                adjustedFrame.bottom = adjustedFrame.top + correctedHeight;
                Rect adjustedVisible = (Rect) visibleFrame.invoke(adjusted);
                if (adjustedVisible != null) adjustedVisible.bottom = adjustedVisible.top + correctedHeight;
                // Bounding rectangles don't pass through the vendor's second division.
                // This firmware clips explicit rectangles to the client, not the source.
                // Preserve the public bounding-rectangle API alongside getInsets().
                setBoundingRects.invoke(adjusted, (Object) new Rect[]{
                        new Rect(0, 0, frame.width(), deliveredHeight)});
                previousTop = (int) (logicalHeight / scale);
                correctedTop = deliveredHeight;
            }
            if (copy != null) {
                parameter.setResult(copy);
                if (!loggedCorrection) {
                    loggedCorrection = true;
                    XposedBridge.log("[FoldAspectUnlock] statusInsets clientTop="
                            + previousTop + "->" + correctedTop
                            + " logical px; scale=" + scale);
                }
            }
        } catch (Throwable error) {
            // Never replace a valid native result with a partially adjusted copy.
            if (!loggedFailure) {
                loggedFailure = true;
                XposedBridge.log("[FoldAspectUnlock] status inset correction skipped: " + error);
            }
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

    private static Method inheritedMethod(Class<?> owner, String name, Class<?> returns) throws Exception {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try { return method(current, name, returns); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }
}
