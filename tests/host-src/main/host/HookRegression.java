package host;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.BoundsCompatController;
import com.android.server.wm.BoundsCompatUtils;
import com.android.server.wm.InsetsPolicy;
import com.android.server.wm.MiuiSizeCompatService;
import com.android.server.wm.WindowState;
import com.android.server.wm.DisplayPolicy;
import com.android.server.wm.AppCompatLetterboxPolicy;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.foldaspect.unlock.AspectUnlock;
import java.io.File;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Runs the real module with a small host API surface. One scenario per JVM. */
public final class HookRegression {
    private static final String TARGET_BUILD = "OS4.0.21.0.XPNCNXM";
    private static final String PROTECTED[] = {
        "android", "com.android.systemui", "com.miui.home",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller"
    };

    public static void main(String[] args) throws Throwable {
        if (args.length != 2) throw new IllegalArgumentException("scenario and fixture root required");
        String scenario = args[0];
        switch (scenario) {
            case "exact-install": exactInstall(); break;
            case "environment-guards": environmentGuards(); break;
            case "missing-class": rejectedTargets(args[1], "missing-class"); break;
            case "missing-method": rejectedTargets(args[1], "missing-method"); break;
            case "wrong-return": rejectedTargets(args[1], "wrong-return"); break;
            case "rollback-retry": rollbackRetry(); break;
            case "fold-unfold-policy": foldUnfoldPolicy(); break;
            case "package-uid-protection": packageUidProtection(); break;
            case "status-bar-policy": statusBarPolicy(); break;
            default: throw new IllegalArgumentException(scenario);
        }
        System.out.println("PASS host scenario: " + scenario);
    }

    private static void require(boolean condition, String explanation) {
        if (!condition) throw new AssertionError(explanation);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters)
            throws Exception {
        return type.getDeclaredMethod(name, parameters);
    }

    private static void load(String packageName, ClassLoader loader) {
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam();
        load.packageName = packageName;
        load.processName = packageName;
        load.classLoader = loader;
        new AspectUnlock().handleLoadPackage(load);
    }

    private static void install() { load("android", HookRegression.class.getClassLoader()); }

    private static Set<Member> expectedMethods() throws Exception {
        return new LinkedHashSet<Member>(Arrays.<Member>asList(
            method(MiuiSizeCompatService.class, "isWideFoldDisabledPackage", String.class),
            method(MiuiSizeCompatService.class, "shouldUseFullscreenBypassInternal", String.class),
            method(BoundsCompatUtils.class, "isWideFoldSystemApp", ApplicationInfo.class),
            method(MiuiSizeCompatService.class, "isInnerCompatDisplay", ActivityTaskManagerService.class),
            method(BoundsCompatController.class, "hideStatusBarInSizeCompat"),
            method(WindowState.class, "getCompatInsetsState"),
            method(DisplayPolicy.class, "configureStatusBarOpacity", int.class),
            method(DisplayPolicy.class, "updateLightNavigationBarLw", int.class, WindowState.class),
            method(AppCompatLetterboxPolicy.class, "getLetterboxDetails")
        ));
    }

    private static void requireExactMethods() throws Exception {
        require(XposedBridge.hookedMethods().equals(expectedMethods()),
            "Only the inspected owner/name/parameter signatures may be hooked");
    }

    private static void exactInstall() throws Exception {
        install();
        requireExactMethods();
        int expected = expectedMethods().size();
        require(XposedBridge.installAttempts == expected, "Each expected target must be installed once");
        install();
        require(XposedBridge.installAttempts == expected, "Duplicate load must not install callbacks twice");
        require(XposedBridge.logs.stream().anyMatch(s -> s.contains("ACTIVE " + expected + "/" + expected)),
            "Successful installation must expose the active diagnostic");
    }

    private static void environmentGuards() throws Exception {
        Build.VERSION.INCREMENTAL = "OS4.0.22.0.XPNCNXM";
        install();
        Build.VERSION.INCREMENTAL = TARGET_BUILD;
        Build.DEVICE = "different_device";
        install();
        Build.DEVICE = "lhasa";
        Build.VERSION.SDK_INT = 36;
        install();
        Build.VERSION.SDK_INT = 37;
        // A null loader makes accidental entry into target discovery observable.
        load("com.android.systemui", null);
        load("com.example.app", null);
        require(XposedBridge.installAttempts == 0 && XposedBridge.hookedMethods().isEmpty(),
            "Unknown firmware and non-android package contexts must not attempt hooks");
        require(XposedBridge.logs.stream().noneMatch(s -> s.contains("setup failed")),
            "Non-android contexts must return before trying to resolve framework classes");
        install();
        requireExactMethods();
    }

    private static void rejectedTargets(String fixtureRoot, String fixture) throws Exception {
        File fixtureDirectory = new File(fixtureRoot, fixture);
        URL common = HookRegression.class.getProtectionDomain().getCodeSource().getLocation();
        URL[] urls = {fixtureDirectory.toURI().toURL(), common};
        try (FrameworkFixtureLoader loader = new FrameworkFixtureLoader(urls,
                fixture.equals("missing-class"))) {
            load("android", loader);
        }
        require(XposedBridge.installAttempts == 0 && XposedBridge.hookedMethods().isEmpty(),
            "Target preflight must reject " + fixture + " before the first installation");
        require(XposedBridge.logs.stream().anyMatch(s -> s.contains("INACTIVE")),
            "Target preflight failure must expose an inactive diagnostic");
        install();
        requireExactMethods();
    }

    private static final class FrameworkFixtureLoader extends URLClassLoader {
        private final boolean omitController;
        FrameworkFixtureLoader(URL[] paths, boolean omitController) {
            super(paths, HookRegression.class.getClassLoader());
            this.omitController = omitController;
        }
        @Override protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.startsWith("com.android.server.wm.")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                if (omitController && name.equals("com.android.server.wm.BoundsCompatController")) {
                    throw new ClassNotFoundException("injected missing class: " + name);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }

    private static void rollbackRetry() throws Exception {
        int expected = expectedMethods().size();
        XposedBridge.failOnInstallAttempt = expected;
        install();
        require(XposedBridge.installAttempts == expected, "Failure must occur at the final installation");
        require(XposedBridge.removedHooks == expected - 1 && XposedBridge.hookedMethods().isEmpty(),
            "Installation failure must remove every previously applied callback");
        require(XposedBridge.logs.stream().noneMatch(s -> s.contains("ACTIVE " + expected + "/" + expected)),
            "A rolled-back setup must not claim activation");
        XposedBridge.failOnInstallAttempt = -1;
        install();
        requireExactMethods();
        require(XposedBridge.installAttempts == expected * 2, "A failed setup must permit a full retry");
        install();
        require(XposedBridge.installAttempts == expected * 2, "The successful retry must retain idempotency");
    }

    private static void foldUnfoldPolicy() throws Throwable {
        install();
        Method inner = method(MiuiSizeCompatService.class, "isInnerCompatDisplay",
            ActivityTaskManagerService.class);
        Method outer = method(MiuiSizeCompatService.class, "isOuterCompatDisplay",
            ActivityTaskManagerService.class);
        Method save = method(MiuiSizeCompatService.class, "writeWideFoldSetting");
        require(!XposedBridge.hookedMethods().contains(outer)
                && !XposedBridge.hookedMethods().contains(save),
            "The outer-display decision and ratio persistence must remain unhooked");
        ActivityTaskManagerService atms = new ActivityTaskManagerService();
        MiuiSizeCompatService service = new MiuiSizeCompatService();
        service.mWideFoldConfigs.put("0#com.example.reader", 16.0f / 9.0f);
        service.mWideFoldConfigs.put("10#com.example.reader", 4.0f / 3.0f);
        service.mWideFoldConfigs.put("0#com.example.fullscreen", 0.0f);
        Map<String, Float> saved = new LinkedHashMap<>(service.mWideFoldConfigs);
        SimulatedTask letterboxed = new SimulatedTask(false);
        SimulatedTask runtimeFullscreen = new SimulatedTask(true);
        for (int cycle = 0; cycle < 4; cycle++) {
            atms.folded = true;
            simulatedConfigurationPass(atms, service, letterboxed, inner, outer);
            simulatedConfigurationPass(atms, service, runtimeFullscreen, inner, outer);
            require(letterboxed.activityRatio == saved.get("0#com.example.reader")
                    && letterboxed.processRatio == letterboxed.activityRatio,
                "Outer-screen configuration must recover the saved ratio after cycle " + cycle);
            require(runtimeFullscreen.activityRatio == 0.0f && runtimeFullscreen.processRatio == 0.0f,
                "An existing runtime-fullscreen task must remain fullscreen on the outer screen");
            atms.folded = false;
            // Verify the real callback rejects the inner display, rather than relying on the model.
            require(Boolean.FALSE.equals(XposedBridge.invokeHooked(inner, null, atms)),
                "The real inner-display before callback must return false");
            simulatedConfigurationPass(atms, service, letterboxed, inner, outer);
            simulatedConfigurationPass(atms, service, runtimeFullscreen, inner, outer);
            require(letterboxed.activityRatio == 0.0f && letterboxed.processRatio == 0.0f,
                "The simulated native reconfiguration must clear activity/process compat on unfold");
        }
        require(service.mWideFoldConfigs.equals(saved) && service.settingsWrites == 0,
            "Fold/unfold callbacks must not rewrite persisted per-user aspect ratios");
        require(!letterboxed.runtimeFullscreen && runtimeFullscreen.runtimeFullscreen,
            "Fold/unfold must preserve both values of the task runtime-fullscreen flag");
        require(XposedBridge.originalCallCount(inner) == 0
                && XposedBridge.originalCallCount(outer) > 0,
            "Only the inner gate may be short-circuited; the outer original must still execute");
    }

    private static final class SimulatedTask {
        boolean runtimeFullscreen;
        float activityRatio;
        float processRatio;
        SimulatedTask(boolean runtimeFullscreen) { this.runtimeFullscreen = runtimeFullscreen; }
    }

    /**
     * Deliberately limited model of the native fold configuration pass. It checks
     * inputs/outputs around real callbacks, not Android dispatch, bounds or rendering.
     */
    private static void simulatedConfigurationPass(ActivityTaskManagerService atms,
            MiuiSizeCompatService service, SimulatedTask task, Method inner, Method outer)
            throws Throwable {
        boolean eligible = (Boolean) XposedBridge.invokeHooked(outer, null, atms)
            || (Boolean) XposedBridge.invokeHooked(inner, null, atms);
        float ratio = eligible && !task.runtimeFullscreen
            ? service.mWideFoldConfigs.get("0#com.example.reader") : 0.0f;
        task.activityRatio = ratio;
        task.processRatio = ratio;
    }

    private static void packageUidProtection() throws Throwable {
        install();
        Method blocked = method(MiuiSizeCompatService.class, "isWideFoldDisabledPackage", String.class);
        Method fullscreen = method(MiuiSizeCompatService.class,
            "shouldUseFullscreenBypassInternal", String.class);
        Method system = method(BoundsCompatUtils.class, "isWideFoldSystemApp", ApplicationInfo.class);
        BoundsCompatUtils utils = new BoundsCompatUtils();
        require(Boolean.FALSE.equals(XposedBridge.invokeHooked(blocked, null, "com.example.reader"))
                && Boolean.FALSE.equals(XposedBridge.invokeHooked(fullscreen, null,
                    "com.example.reader/.Scanner")),
            "Ordinary package/activity restrictions must still be bypassed");
        for (String name : PROTECTED) {
            require(Boolean.TRUE.equals(XposedBridge.invokeHooked(blocked, null, name))
                    && Boolean.TRUE.equals(XposedBridge.invokeHooked(fullscreen, null, name + "/.Main"))
                    && Boolean.TRUE.equals(XposedBridge.invokeHooked(system, utils, app(name, 10123))),
                "All three callbacks must preserve the native protection for " + name);
        }
        for (int uid : new int[]{-1, 0, 1000, 9999, 99000, 101000, 199000}) {
            require(Boolean.TRUE.equals(XposedBridge.invokeHooked(system, utils,
                    app("com.miui.notes", uid))),
                "Privileged/isolated UID must retain the original system-app decision: " + uid);
        }
        for (int uid : new int[]{10000, 10345, 110345, 99910345}) {
            require(Boolean.FALSE.equals(XposedBridge.invokeHooked(system, utils,
                    app("com.miui.notes", uid))),
                "An ordinary app ID must be unlockable in each user: " + uid);
        }
        for (String name : new String[]{null, "", "pkg/.Main"}) {
            require(Boolean.TRUE.equals(XposedBridge.invokeHooked(blocked, null, name)),
                "Malformed package input must reach the native policy");
        }
        for (String component : new String[]{null, "", "pkg", "pkg/"}) {
            require(Boolean.TRUE.equals(XposedBridge.invokeHooked(fullscreen, null, component)),
                "Malformed activity input must reach the native policy");
        }
        int beforeNull = XposedBridge.originalCallCount(system);
        require(Boolean.FALSE.equals(XposedBridge.invokeHooked(system, utils, (Object) null))
                && XposedBridge.originalCallCount(system) == beforeNull + 1,
            "Null ApplicationInfo must remain a native decision without a callback crash");
    }

    private static ApplicationInfo app(String name, int uid) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = name;
        info.uid = uid;
        info.flags = ApplicationInfo.FLAG_SYSTEM;
        return info;
    }

    private static void statusBarPolicy() throws Throwable {
        install();
        Method forced = method(BoundsCompatController.class, "hideStatusBarInSizeCompat");
        Method appRequested = method(InsetsPolicy.class, "hasAppRequestedStatusBarHidden");
        BoundsCompatController controller = new BoundsCompatController();
        InsetsPolicy insets = new InsetsPolicy();
        require(!XposedBridge.hookedMethods().contains(appRequested),
            "App-requested visibility must not be hooked");
        require(!simulatedStatusBarHidden(controller, insets, forced, appRequested),
            "Size-compat mode alone must no longer force the status bar hidden");
        insets.appRequestsStatusBarHidden = true;
        require(simulatedStatusBarHidden(controller, insets, forced, appRequested)
                && insets.appRequestsStatusBarHidden,
            "An app's immersive request must still hide the status bar and remain unchanged");
        controller.inSizeCompat = false;
        require(simulatedStatusBarHidden(controller, insets, forced, appRequested),
            "Immersive requests must also retain effect outside size-compat mode");
        insets.appRequestsStatusBarHidden = false;
        require(!simulatedStatusBarHidden(controller, insets, forced, appRequested),
            "Exiting app immersive mode must retain ordinary system visibility");
        require(XposedBridge.originalCallCount(forced) == 0
                && XposedBridge.originalCallCount(appRequested) == 4,
            "The callback must replace only the compat override, not the app visibility method");
    }

    private static boolean simulatedStatusBarHidden(BoundsCompatController controller,
            InsetsPolicy insets, Method forced, Method appRequested) throws Throwable {
        // Evaluate both independent policy inputs, as opposed to modifying the app request.
        boolean compatOverride = (Boolean) XposedBridge.invokeHooked(forced, controller);
        boolean immersiveRequest = (Boolean) XposedBridge.invokeHooked(appRequested, insets);
        return compatOverride || immersiveRequest;
    }
}
