package io.github.foldaspect.unlock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Optional SystemUI renderer. No ordinary application process is hooked. */
public final class SystemUiEntry implements IXposedHookLoadPackage {
    private static boolean installed;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!"com.android.systemui".equals(load.packageName)
                || !"com.android.systemui".equals(load.processName)
                || !UnlockPolicy.supports(Build.DEVICE, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL)) return;
        synchronized (SystemUiEntry.class) {
            if (installed) return;
            List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
            try {
                Renderer renderer = new Renderer(load.classLoader);
                renderer.install(hooks);
                installed = true;
                XposedBridge.log("[FoldAspectUnlock] UI ACTIVE 9/9 hooks; height=90px; scrim=20%");
            } catch (Throwable error) {
                for (XC_MethodHook.Unhook hook : hooks) {
                    try { hook.unhook(); } catch (Throwable ignored) { }
                }
                XposedBridge.log("[FoldAspectUnlock] UI INACTIVE: " + error);
            }
        }
    }

    private static final class Renderer {
        final Class<?> phoneView, phoneTransition;
        final Method height, rotatedHeight, attributes, windowLayout, providerStart,
                phoneAttached, phoneConfiguration, cutoutUpdate, applyBackground, refreshWindow, refreshProvider,
                updatePhoneHeight, updateSafeInsets;
        final Field callbackDisplay, windowContext, windowAttached, providerContext,
                cutoutSpace, transitionView, transitionBackground, semiColor;
        final Set<Object> controllers = weakSet(), providers = weakSet(), transitions = weakSet();
        final Set<View> views = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
        final Map<Object, Integer> originalColors = new WeakHashMap<>();
        volatile boolean compact;
        boolean refreshQueued, loggedFailure, loggedColor;

        Renderer(ClassLoader loader) throws Exception {
            Class<?> bars = cls(loader, "com.android.internal.policy.SystemBarUtils");
            Class<?> callback = cls(loader, "com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepositoryImpl$commandQueueCallback$1");
            Class<?> window = cls(loader, "com.android.systemui.statusbar.window.StatusBarWindowControllerImpl");
            Class<?> provider = cls(loader, "com.android.systemui.statusbar.layout.StatusBarContentInsetsProviderImpl");
            phoneView = cls(loader, "com.android.systemui.statusbar.phone.PhoneStatusBarView");
            Class<?> miuiView = cls(loader, "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView");
            Class<?> barTransition = cls(loader, "com.android.systemui.shared.statusbar.phone.BarTransitions");
            phoneTransition = cls(loader, "com.android.systemui.statusbar.phone.PhoneStatusBarTransitions");
            Class<?> background = cls(loader, "com.android.systemui.shared.statusbar.phone.BarTransitions$BarBackgroundDrawable");
            height = method(bars, "getStatusBarHeight", int.class, Context.class);
            rotatedHeight = method(bars, "getStatusBarHeightForRotation", int.class, Context.class, int.class);
            attributes = method(callback, "onSystemBarAttributesChanged", void.class, int.class, int.class,
                    cls(loader, "[Lcom.android.internal.view.AppearanceRegion;"), boolean.class,
                    int.class, int.class, String.class, cls(loader, "[Lcom.android.internal.statusbar.LetterboxDetails;"));
            callbackDisplay = field(callback, "$thisDisplayId", int.class);
            windowLayout = method(window, "getBarLayoutParamsForRotation", WindowManager.LayoutParams.class, int.class);
            refreshWindow = method(window, "refreshStatusBarHeight", void.class);
            windowContext = field(window, "mContext", Context.class);
            windowAttached = field(window, "mIsAttached", boolean.class);
            providerStart = method(provider, "start", void.class);
            refreshProvider = method(provider, "onDensityOrFontScaleChanged", void.class);
            providerContext = field(provider, "context", Context.class);
            phoneAttached = method(phoneView, "onAttachedToWindow", void.class);
            phoneConfiguration = method(phoneView, "onConfigurationChanged", void.class, Configuration.class);
            updatePhoneHeight = method(phoneView, "updateStatusBarHeight", void.class);
            cutoutUpdate = method(miuiView, "updateCutoutLocation", void.class);
            updateSafeInsets = method(miuiView, "updateSafeInsets", void.class);
            cutoutSpace = field(phoneView, "mCutoutSpace", View.class);
            applyBackground = method(barTransition, "applyModeBackground", void.class, int.class, boolean.class);
            transitionView = field(barTransition, "mView", View.class);
            transitionBackground = field(barTransition, "mBarBackground", background);
            semiColor = field(background, "mSemiTransparent", int.class);
        }

        void install(List<XC_MethodHook.Unhook> hooks) {
            XC_MethodHook adjustHeight = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !(p.getResult() instanceof Integer)) return;
                    try {
                        Context context = (Context) p.args[0];
                        if (!inCompactContext(context)) return;
                        int rotation = p.args.length == 2 ? (Integer) p.args[1] : context.getDisplay().getRotation();
                        if (rotation != 0) return;
                        p.setResult(StatusBarStylePolicy.height((Integer) p.getResult(),
                                context.getResources().getDisplayMetrics().density,
                                context.getResources().getConfiguration().fontScale));
                    } catch (Throwable error) { logFailure(error); }
                }
            };
            // The two outer overloads compute independently in this firmware. Never hook
            // the Resources/DisplayCutout overload, which would shrink twice.
            hooks.add(XposedBridge.hookMethod(height, adjustHeight));
            hooks.add(XposedBridge.hookMethod(rotatedHeight, adjustHeight));
            hooks.add(XposedBridge.hookMethod(attributes, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (callbackDisplay.getInt(p.thisObject) != 0 || (Integer) p.args[0] != 0) return;
                        int appearance = (Integer) p.args[1];
                        boolean next = StatusBarStylePolicy.requested(0, appearance, (Integer) p.args[5]);
                        p.args[1] = appearance & ~StatusBarStylePolicy.COMPACT_APPEARANCE;
                        if (compact != next) {
                            compact = next;
                            queueRefresh();
                            XposedBridge.log("[FoldAspectUnlock] UI compact=" + next);
                        }
                    } catch (Throwable error) { logFailure(error); }
                }
            }));
            hooks.add(XposedBridge.hookMethod(windowLayout, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    synchronized (Renderer.this) {
                        if (!p.hasThrowable() && controllers.add(p.thisObject)) queueRefresh();
                    }
                }
            }));
            hooks.add(XposedBridge.hookMethod(providerStart, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    synchronized (Renderer.this) {
                        if (!p.hasThrowable() && providers.add(p.thisObject)) queueRefresh();
                    }
                }
            }));
            hooks.add(XposedBridge.hookMethod(phoneAttached, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    synchronized (Renderer.this) {
                        if (!p.hasThrowable() && views.add((View) p.thisObject)) queueRefresh();
                    }
                }
            }));
            hooks.add(XposedBridge.hookMethod(phoneConfiguration, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable()) queueRefresh();
                }
            }));
            hooks.add(XposedBridge.hookMethod(cutoutUpdate, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    try {
                        View view = (View) p.thisObject;
                        if (!inCompactContext(view.getContext())) return;
                        View space = (View) cutoutSpace.get(view);
                        if (space == null || view.getLayoutParams() == null) return;
                        ViewGroup.LayoutParams layout = space.getLayoutParams();
                        int available = view.getLayoutParams().height - view.getPaddingTop() - view.getPaddingBottom();
                        if (layout != null && available > 0 && layout.height > available) {
                            // Only the transparent placeholder inside the icon row changes.
                            // The real display cutout and horizontal camera exclusion stay.
                            layout.height = available;
                            space.setLayoutParams(layout);
                        }
                    } catch (Throwable error) { logFailure(error); }
                }
            }));
            hooks.add(XposedBridge.hookMethod(applyBackground, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        View view = (View) transitionView.get(p.thisObject);
                        if (!phoneTransition.isInstance(p.thisObject)) return;
                        synchronized (Renderer.this) { transitions.add(p.thisObject); }
                        updateColor(p.thisObject, view);
                    } catch (Throwable error) { logFailure(error); }
                }
            }));
        }

        boolean inCompactContext(Context context) {
            if (!compact || context == null || context.getDisplay() == null
                    || context.getDisplay().getDisplayId() != 0
                    || context.getDisplay().getRotation() != 0) return false;
            Configuration configuration = context.getResources().getConfiguration();
            return configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                    && configuration.smallestScreenWidthDp > 0 && configuration.smallestScreenWidthDp < 600;
        }

        synchronized void queueRefresh() {
            if (refreshQueued || Looper.getMainLooper() == null) return;
            refreshQueued = true;
            new Handler(Looper.getMainLooper()).post(() -> {
                synchronized (Renderer.this) { refreshQueued = false; }
                refresh();
            });
        }

        void refresh() {
            List<Object> providerList, windowList, transitionList;
            List<View> viewList;
            synchronized (this) {
                providerList = new ArrayList<>(providers);
                windowList = new ArrayList<>(controllers);
                transitionList = new ArrayList<>(transitions);
                viewList = new ArrayList<>(views);
            }
            for (Object provider : providerList) try {
                Context context = (Context) providerContext.get(provider);
                if (isPrimaryDisplay(context)) refreshProvider.invoke(provider);
            } catch (Throwable error) { logFailure(error); }
            for (Object window : windowList) try {
                if (windowAttached.getBoolean(window) && isPrimaryDisplay((Context) windowContext.get(window)))
                    refreshWindow.invoke(window);
            } catch (Throwable error) { logFailure(error); }
            for (View view : viewList) try {
                if (!view.isAttachedToWindow() || !isPrimaryDisplay(view.getContext())) continue;
                updatePhoneHeight.invoke(view);
                cutoutUpdate.invoke(view);
                updateSafeInsets.invoke(view);
                view.requestLayout();
                view.invalidate();
            } catch (Throwable error) { logFailure(error); }
            for (Object transition : transitionList) try {
                updateColor(transition, (View) transitionView.get(transition));
            } catch (Throwable error) { logFailure(error); }
        }

        void updateColor(Object transition, View view) throws Exception {
            // The native factory gives PhoneStatusBarTransitions a separate background
            // child View from StatusBarWindowView, not the PhoneStatusBarView icon row.
            // Qualify by the transition owner so navigation-bar backgrounds stay untouched.
            if (!phoneTransition.isInstance(transition) || view == null) return;
            Object background = transitionBackground.get(transition);
            if (background == null) return;
            synchronized (this) {
                Integer original = originalColors.get(background);
                if (inCompactContext(view.getContext())) {
                    if (original == null) {
                        original = semiColor.getInt(background);
                        originalColors.put(background, original);
                    }
                    semiColor.setInt(background, StatusBarStylePolicy.SCRIM_COLOR);
                    if (!loggedColor) {
                        loggedColor = true;
                        XposedBridge.log("[FoldAspectUnlock] UI scrim #" + Integer.toHexString(original)
                                + "->#" + Integer.toHexString(StatusBarStylePolicy.SCRIM_COLOR));
                    }
                } else if (original != null) {
                    semiColor.setInt(background, original);
                    originalColors.remove(background);
                }
            }
            ((Drawable) background).invalidateSelf();
        }

        boolean isPrimaryDisplay(Context context) {
            return context != null && context.getDisplay() != null && context.getDisplay().getDisplayId() == 0;
        }

        void logFailure(Throwable error) {
            if (!loggedFailure) {
                loggedFailure = true;
                XposedBridge.log("[FoldAspectUnlock] UI adjustment skipped: " + error);
            }
        }

        static Set<Object> weakSet() { return Collections.newSetFromMap(new WeakHashMap<Object, Boolean>()); }
        static Class<?> cls(ClassLoader loader, String name) throws ClassNotFoundException {
            return Class.forName(name, false, loader);
        }
        static Field field(Class<?> owner, String name, Class<?> type) throws Exception {
            Field result = owner.getDeclaredField(name);
            if (result.getType() != type) throw new NoSuchFieldException(name + " changed type");
            result.setAccessible(true);
            return result;
        }
        static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... args) throws Exception {
            Method result = owner.getDeclaredMethod(name, args);
            if (result.getReturnType() != returns) throw new NoSuchMethodException(name + " changed return type");
            result.setAccessible(true);
            return result;
        }
    }
}
