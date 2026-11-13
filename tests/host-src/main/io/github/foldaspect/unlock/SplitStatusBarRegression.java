package io.github.foldaspect.unlock;

import android.graphics.Insets;
import android.graphics.Rect;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.WindowInsets;
import com.android.server.wm.DisplayPolicy;
import com.android.server.wm.ActivityRecord;
import com.android.server.wm.DisplayContent;
import com.android.server.wm.InsetsControlTarget;
import com.android.server.wm.InsetsPolicy;
import com.android.server.wm.InsetsStateController;
import com.android.server.wm.WindowState;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Real callbacks and native return recorders, not a split-screen UI or Binder simulation. */
public final class SplitStatusBarRegression {
    private static Method control, visibility, hide, insets, dispatch;

    public static void main(String[] args) throws Throwable {
        if (args.length != 2) throw new IllegalArgumentException("scenario and fixture root required");
        if ("split-preflight".equals(args[0])) {
            preflight(args[1]);
        } else {
            SplitStatusBarController toggle = null;
            if ("split-enabled-policy".equals(args[0])) {
                toggle = new SplitStatusBarController(SplitStatusBarRegression.class.getClassLoader());
                XposedBridge.hookMethod(toggle.controlTarget, toggle.controlCallback);
                XposedBridge.hookMethod(toggle.barVisibility, toggle.visibilityCallback);
                XposedBridge.hookMethod(toggle.hideTypes, toggle.hideCallback);
                XposedBridge.hookMethod(toggle.targetInsets, toggle.insetsCallback);
                XposedBridge.hookMethod(toggle.dispatchTarget, toggle.dispatchCallback);
                StatusBarAppearance appearance = new StatusBarAppearance(SplitStatusBarRegression.class.getClassLoader());
                XposedBridge.hookMethod(appearance.opacityTarget, appearance);
            } else {
                install(SplitStatusBarRegression.class.getClassLoader());
            }
            control = InsetsPolicy.class.getDeclaredMethod("getStatusControlTargetInner", WindowState.class, boolean.class);
            visibility = Class.forName("com.android.server.wm.InsetsPolicy$BarWindow").getDeclaredMethod("setVisible", boolean.class);
            hide = InsetsPolicy.class.getDeclaredMethod("hideSystemBars", int.class);
            insets = InsetsPolicy.class.getDeclaredMethod("enforceInsetsPolicyForTarget", WindowState.class, InsetsState.class);
            dispatch = InsetsStateController.class.getDeclaredMethod("onBarControlTargetChanged",
                InsetsControlTarget.class, InsetsControlTarget.class, InsetsControlTarget.class, InsetsControlTarget.class);
            switch (args[0]) {
                case "split-status-targets": statusTargets(); break;
                case "split-context-guards": contextGuards(); break;
                case "split-hide-masks": hideMasks(); break;
                case "split-inset-copy": insetCopy(); break;
                case "split-inset-target-guards": targetGuards(); break;
                case "split-lifecycle": lifecycle(); break;
                case "split-exception-preservation": exceptions(); break;
                case "split-final-dispatch": finalDispatch(); break;
                case "split-dispatch-guards": dispatchGuards(); break;
                case "split-dispatch-failures": dispatchFailures(); break;
                case "split-home-visible-scenes": homeVisibleScenes(); break;
                case "split-home-guards": homeGuards(); break;
                case "split-home-failures": homeFailures(); break;
                case "split-enabled-policy": enabledPolicy(toggle); break;
                default: throw new IllegalArgumentException(args[0]);
            }
        }
        System.out.println("PASS host scenario: " + args[0]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void install(ClassLoader loader) {
        XC_LoadPackage.LoadPackageParam load = new XC_LoadPackage.LoadPackageParam();
        load.packageName = "android";
        load.processName = "android";
        load.classLoader = loader;
        new AspectUnlock().handleLoadPackage(load);
    }
    private static WindowState stage(boolean right) {
        WindowState window = new WindowState();
        window.windowingMode = 6;
        window.task.bounds = right ? new Rect(1194, 0, 2364, 1672) : new Rect(0, 0, 1170, 1672);
        return window;
    }
    private static InsetsPolicy fixture() {
        InsetsPolicy owner = new InsetsPolicy();
        owner.mDisplayContent.mAtmService.splitScreen = true;
        owner.mDisplayContent.mCurrentFocus = stage(false);
        return owner;
    }
    private static Object target(InsetsPolicy owner, WindowState window, boolean fake) throws Throwable {
        return XposedBridge.invokeHooked(control, owner, window, fake);
    }
    private static InsetsState enforce(InsetsPolicy owner, WindowState window, InsetsState borrowed) throws Throwable {
        return (InsetsState) XposedBridge.invokeHooked(insets, owner, window, borrowed);
    }
    private static boolean barVisible(Object bar) throws Exception {
        Field field = bar.getClass().getDeclaredField("visible");
        field.setAccessible(true);
        return field.getBoolean(bar);
    }
    private static InsetsState statusState(boolean visible) {
        InsetsState state = new InsetsState();
        state.addSource(new InsetsSource(WindowInsets.Type.statusBars(), new Rect(0, 0, 2364, 140)).setVisible(visible));
        return state;
    }
    private static InsetsSource source(InsetsState state, int type) {
        for (int i = 0; i < state.sourceSize(); i++) {
            InsetsSource source = state.sourceAt(i);
            if (source.getType() == type) return source;
        }
        return null;
    }
    private static void nativeUnchanged(InsetsPolicy owner, String explanation) throws Throwable {
        WindowState window = owner.mDisplayContent == null ? null : owner.mDisplayContent.mCurrentFocus;
        require(target(owner, window, false) == owner.nativeControlTarget, "Native control target retained: " + explanation);
        Object status = owner.createBarWindow(1);
        XposedBridge.invokeHooked(visibility, status, false);
        require(!barVisible(status), "Native status visibility retained: " + explanation);
        XposedBridge.invokeHooked(hide, owner, 1 | 2 | 8);
        require(owner.hiddenTypes == (1 | 2 | 8), "Native hide mask retained: " + explanation);
        InsetsState original = owner.nativeResult;
        require(enforce(owner, window, statusState(false)) == original,
            "Native inset return retained: " + explanation);
    }

    private static void statusTargets() throws Throwable {
        InsetsPolicy owner = fixture();
        for (boolean right : new boolean[]{false, true}) {
            WindowState window = stage(right);
            owner.mDisplayContent.mCurrentFocus = window;
            for (boolean fake : new boolean[]{false, true}) {
                require(target(owner, window, fake) == owner.mShowingPermanentControlTarget,
                    "Both real/fake status-control paths must select the permanent target for either stage");
            }
            Object status = owner.createBarWindow(1);
            XposedBridge.invokeHooked(visibility, status, false);
            require(barVisible(status), "Status BarWindow must remain visible in the eligible scene");
            for (int other : new int[]{2, 3, -1}) {
                Object bar = owner.createBarWindow(other);
                XposedBridge.invokeHooked(visibility, bar, false);
                require(!barVisible(bar), "Only BarWindow id 1 may be forced visible: " + other);
            }
            require(window.requestedVisibleTypes == 1 && window.windowingMode == 6
                    && "com.example.reader".equals(window.mAttrs.packageName),
                "Split visibility must not rewrite application visibility, mode or package attrs");
        }
        owner.mShowingPermanentControlTarget = null;
        require(target(owner, owner.mDisplayContent.mCurrentFocus, false) == owner.nativeControlTarget,
            "An unavailable permanent target must retain the native target");
        require(XposedBridge.originalCallCount(control) == 5,
            "Status-control replacement must run after the real native selection");
        for (boolean right : new boolean[]{false, true}) {
            InsetsPolicy current = fixture();
            current.mDisplayContent.mCurrentFocus = stage(right);
            WindowState staleFullscreen = new WindowState();
            staleFullscreen.task.bounds = new Rect(0, 0, 2364, 1672);
            WindowState oldHome = new WindowState();
            oldHome.mAttrs.packageName = "com.miui.home";
            for (WindowState candidate : new WindowState[]{null, staleFullscreen, oldHome}) {
                for (boolean fake : new boolean[]{false, true}) {
                    require(target(current, candidate, fake) == current.mShowingPermanentControlTarget,
                        "Actual split focus must win over a null, old fullscreen or desktop candidate");
                }
            }
            WindowState shade = new WindowState();
            shade.mAttrs.packageName = "com.android.systemui";
            shade.mAttrs.type = 2040;
            require(target(current, shade, false) == current.nativeControlTarget,
                "An explicitly non-base system control candidate must preserve the native target");
            shade.mAttrs = null;
            require(target(current, shade, false) == current.nativeControlTarget,
                "Unavailable candidate attributes must not override an unknown system-control target");
        }
    }

    private static void contextGuards() throws Throwable {
        Map<String, Consumer<InsetsPolicy>> guards = new LinkedHashMap<>();
        guards.put("outer screen", p -> p.mDisplayContent.configuration.screenType = 1);
        guards.put("unknown screen", p -> p.mDisplayContent.configuration.screenType = -1);
        guards.put("secondary display", p -> p.mDisplayContent.displayId = 1);
        guards.put("locked display", p -> p.mPolicy.keyguardShowing = true);
        guards.put("not actually split", p -> p.mDisplayContent.mAtmService.splitScreen = false);
        guards.put("missing display", p -> p.mDisplayContent = null);
        guards.put("missing policy", p -> p.mPolicy = null);
        guards.put("missing configuration", p -> p.mDisplayContent.configuration = null);
        guards.put("missing activity service", p -> p.mDisplayContent.mAtmService = null);
        guards.put("missing focus", p -> p.mDisplayContent.mCurrentFocus = null);
        guards.put("freeform focus", p -> p.mDisplayContent.mCurrentFocus.windowingMode = 5);
        guards.put("fullscreen focus", p -> p.mDisplayContent.mCurrentFocus.windowingMode = 1);
        guards.put("focus on another display", p -> p.mDisplayContent.mCurrentFocus.displayId = 1);
        guards.put("non-base window", p -> p.mDisplayContent.mCurrentFocus.mAttrs.type = 2);
        guards.put("missing attrs", p -> p.mDisplayContent.mCurrentFocus.mAttrs = null);
        guards.put("protected SystemUI", p -> p.mDisplayContent.mCurrentFocus.mAttrs.packageName = "com.android.systemui");
        guards.put("protected home", p -> p.mDisplayContent.mCurrentFocus.mAttrs.packageName = "com.miui.home");
        guards.put("malformed package", p -> p.mDisplayContent.mCurrentFocus.mAttrs.packageName = "pkg/.Activity");
        guards.put("missing task", p -> p.mDisplayContent.mCurrentFocus.task = null);
        guards.put("missing whole bounds", p -> p.mDisplayContent.bounds = null);
        guards.put("missing task bounds", p -> p.mDisplayContent.mCurrentFocus.task.bounds = null);
        guards.put("top-bottom stage", p -> p.mDisplayContent.mCurrentFocus.task.bounds = new Rect(0, 0, 2364, 820));
        guards.put("full display task", p -> p.mDisplayContent.mCurrentFocus.task.bounds = new Rect(0, 0, 2364, 1672));
        guards.put("centered panel", p -> p.mDisplayContent.mCurrentFocus.task.bounds = new Rect(200, 0, 1360, 1672));
        guards.put("partial-height panel", p -> p.mDisplayContent.mCurrentFocus.task.bounds.top = 140);
        guards.put("task outside display", p -> p.mDisplayContent.mCurrentFocus.task.bounds.left = -1);
        guards.put("zero-width stage", p -> p.mDisplayContent.mCurrentFocus.task.bounds.right = 0);
        guards.put("inverted stage", p -> p.mDisplayContent.mCurrentFocus.task.bounds.right = -1);
        guards.put("empty whole bounds", p -> p.mDisplayContent.bounds.right = 0);
        for (Map.Entry<String, Consumer<InsetsPolicy>> entry : guards.entrySet()) {
            InsetsPolicy owner = fixture();
            entry.getValue().accept(owner);
            nativeUnchanged(owner, entry.getKey());
        }
        require(XposedBridge.logs.stream().noneMatch(s -> s.contains("split status bar skipped")),
            "Ordinary rejected scenes must not be logged as callback errors");
    }

    private static void hideMasks() throws Throwable {
        InsetsPolicy owner = fixture();
        for (int mask : new int[]{0, 1, 2, 3, 8, 9, 11, 128 | 8 | 2 | 1, -1}) {
            XposedBridge.invokeHooked(hide, owner, mask);
            require(owner.hiddenTypes == (mask & ~WindowInsets.Type.statusBars()),
                "Only the status bit may be removed; preserve nav/IME/unknown bits: " + mask);
        }
        require(owner.hideCalls == 9 && XposedBridge.originalCallCount(hide) == 9,
            "Even zero/status-only masks must preserve the native method call path");
        owner.mDisplayContent.mAtmService.splitScreen = false;
        XposedBridge.invokeHooked(hide, owner, 11);
        require(owner.hiddenTypes == 11, "Exiting split mode must restore the original combined hide mask");
    }

    private static void insetCopy() throws Throwable {
        InsetsPolicy owner = fixture();
        InsetsState borrowed = statusState(false);
        InsetsSource status = borrowed.sourceAt(0);
        status.visibleFrame = new Rect(0, 0, 2364, 140);
        status.attachedInsets = Insets.of(0, 140, 0, 0);
        status.boundingRects = new Rect[]{new Rect(0, 0, 2364, 140)};
        borrowed.addSource(new InsetsSource(2, new Rect(0, 1580, 2364, 1672)));
        borrowed.addSource(new InsetsSource(128, new Rect(2250, 0, 2364, 140)));
        InsetsSource nativeNavigation = new InsetsSource(2, new Rect(0, 1600, 2364, 1672));
        owner.nativeResult.addSource(nativeNavigation);
        InsetsState nativeResult = owner.nativeResult;
        InsetsState corrected = enforce(owner, owner.mDisplayContent.mCurrentFocus, borrowed);
        require(corrected != nativeResult && corrected != borrowed && corrected.sourceSize() == 2,
            "Restore a status copy into a copy of the native result, not into borrowed/shared inputs");
        InsetsSource restored = source(corrected, 1);
        require(restored != status && restored.isVisible() && restored.getFrame().equals(status.getFrame())
                && restored.getVisibleFrame().equals(status.getVisibleFrame())
                && restored.getAttachedInsets().equals(status.getAttachedInsets())
                && restored.getBoundingRects()[0].equals(status.getBoundingRects()[0]),
            "Restored status geometry and metadata must come from the original source, with visibility enabled");
        require(!status.isVisible() && nativeResult.sourceSize() == 1 && source(nativeResult, 1) == null
                && owner.lastInsetInput == borrowed && owner.lastInsetTarget == owner.mDisplayContent.mCurrentFocus,
            "Native enforcement must execute with original arguments and leave both borrowed states unchanged");
        require(source(corrected, 2).getFrame().equals(nativeNavigation.getFrame()) && source(corrected, 128) == null,
            "Keep native-result non-status sources; never reintroduce cutout or navigation sources from input");
        restored.getFrame().bottom = 120;
        restored.getVisibleFrame().bottom = 121;
        restored.getBoundingRects()[0].bottom = 122;
        require(status.getFrame().bottom == 140 && status.getVisibleFrame().bottom == 140
                && status.getBoundingRects()[0].bottom == 140,
            "Corrected status geometry and metadata must not alias borrowed input");
        owner.nativeResult = corrected;
        require(enforce(owner, owner.mDisplayContent.mCurrentFocus, borrowed) == corrected
                && corrected.sourceSize() == 2 && source(corrected, 1) == restored,
            "Repeated enforcement must keep an existing native status source without duplication or replacement");
        restored.setVisible(false);
        InsetsState existingCorrected = enforce(owner, owner.mDisplayContent.mCurrentFocus, borrowed);
        require(existingCorrected != corrected && existingCorrected.sourceSize() == 2
                && source(existingCorrected, 1) != restored && source(existingCorrected, 1).isVisible()
                && !restored.isVisible() && !status.isVisible(),
            "An existing hidden status source must become visible only in a deep copy, without duplication");
        require(source(existingCorrected, 1).getFrame().bottom == 120
                && source(existingCorrected, 1).getVisibleFrame().bottom == 121
                && source(existingCorrected, 1).getBoundingRects()[0].bottom == 122
                && source(existingCorrected, 2).getFrame().equals(nativeNavigation.getFrame()),
            "Existing hidden sources must keep native-result geometry and non-status metadata, not input replacements");
        source(existingCorrected, 1).getFrame().bottom = 123;
        require(restored.getFrame().bottom == 120,
            "Correcting an existing hidden status source must not alias native-result frame geometry");
        owner.nativeResult = existingCorrected;
        require(enforce(owner, owner.mDisplayContent.mCurrentFocus, borrowed) == existingCorrected,
            "An already-visible corrected native source must remain idempotent");
        owner.nativeResult = new InsetsState();
        require(enforce(owner, owner.mDisplayContent.mCurrentFocus, new InsetsState()) == owner.nativeResult,
            "Missing status input must not fabricate status geometry");
        InsetsState emptyFrame = statusState(false);
        emptyFrame.sourceAt(0).getFrame().bottom = 0;
        require(enforce(owner, owner.mDisplayContent.mCurrentFocus, emptyFrame) == owner.nativeResult,
            "Empty original status geometry must not be reintroduced");
    }

    private static void targetGuards() throws Throwable {
        InsetsPolicy owner = fixture();
        WindowState right = stage(true);
        require(source(enforce(owner, right, statusState(false)), 1) != null,
            "Insets may be restored to the other valid side, not only the focused stage");
        Map<String, Consumer<WindowState>> guards = new LinkedHashMap<>();
        guards.put("fullscreen target", w -> w.windowingMode = 1);
        guards.put("freeform target", w -> w.windowingMode = 5);
        guards.put("target on another display", w -> w.displayId = 1);
        guards.put("non-base target", w -> w.mAttrs.type = 2);
        guards.put("system target", w -> w.mAttrs.packageName = "android");
        guards.put("missing target task", w -> w.task = null);
        guards.put("top-bottom target", w -> w.task.bounds = new Rect(0, 0, 2364, 820));
        guards.put("centered target panel", w -> w.task.bounds = new Rect(200, 0, 1360, 1672));
        for (Map.Entry<String, Consumer<WindowState>> entry : guards.entrySet()) {
            WindowState target = stage(true);
            entry.getValue().accept(target);
            require(enforce(owner, target, statusState(false)) == owner.nativeResult,
                "Inset target must independently satisfy the split guards: " + entry.getKey());
        }
        require(enforce(owner, null, statusState(false)) == owner.nativeResult,
            "Null inset target must retain the native return");
        require(enforce(owner, right, null) == owner.nativeResult,
            "Null borrowed state must retain the native return");
        owner.nativeResult = null;
        require(enforce(owner, right, statusState(false)) == null, "Null native return must remain null");
    }

    private static void lifecycle() throws Throwable {
        InsetsPolicy owner = fixture();
        WindowState left = owner.mDisplayContent.mCurrentFocus;
        Object status = owner.createBarWindow(1);
        for (int round = 0; round < 3; round++) {
            owner.mDisplayContent.mAtmService.splitScreen = true;
            owner.mDisplayContent.mCurrentFocus = round % 2 == 0 ? left : stage(true);
            require(target(owner, owner.mDisplayContent.mCurrentFocus, false) == owner.mShowingPermanentControlTarget,
                "Entering split with either focus side must restore permanent status visibility");
            XposedBridge.invokeHooked(visibility, status, false);
            require(barVisible(status), "Split status visible during repeated round trip");
            owner.mDisplayContent.mAtmService.splitScreen = false;
            XposedBridge.invokeHooked(visibility, status, false);
            require(!barVisible(status)
                    && target(owner, owner.mDisplayContent.mCurrentFocus, false) == owner.nativeControlTarget,
                "Exit must immediately restore native visibility/control, without latched scene state");
        }
        owner.mDisplayContent.mAtmService.splitScreen = true;
        WindowState shade = stage(false);
        shade.mAttrs.packageName = "com.android.systemui";
        shade.mAttrs.type = 2040;
        require(target(owner, shade, false) == owner.nativeControlTarget,
            "A notification-shade control selection must preserve the original target");
        owner.mDisplayContent.mCurrentFocus = shade;
        XposedBridge.invokeHooked(visibility, status, false);
        require(!barVisible(status), "Protected focused system windows must retain native bar behavior");
        owner.mDisplayContent.mCurrentFocus = left;
        DisplayPolicy appearance = new DisplayPolicy();
        appearance.mSystemUiControllingWindow = left;
        Method opacity = DisplayPolicy.class.getDeclaredMethod("configureStatusBarOpacity", int.class);
        int splitFlags = (Integer) XposedBridge.invokeHooked(opacity, appearance, 1);
        require((splitFlags & StatusBarStylePolicy.COMPACT_APPEARANCE) == 0,
            "New inner-screen split visibility must not request the old outer-screen compact style");
        left.windowingMode = 1;
        int outerFlags = (Integer) XposedBridge.invokeHooked(opacity, appearance, 1);
        require(StatusBarStylePolicy.requested(0, outerFlags, 1)
                && StatusBarStylePolicy.height(126, 2.5f) == 90,
            "Existing full-screen outer compat styling and its 90px policy remain available");
    }

    private static void exceptions() throws Throwable {
        InsetsPolicy owner = fixture();
        RuntimeException sentinel = new IllegalStateException("native split failure");
        owner.originalFailure = sentinel;
        try {
            target(owner, owner.mDisplayContent.mCurrentFocus, false);
            throw new AssertionError("Original control failure must propagate");
        } catch (RuntimeException error) { require(error == sentinel, "Preserve native control exception identity"); }
        try {
            enforce(owner, owner.mDisplayContent.mCurrentFocus, statusState(false));
            throw new AssertionError("Original inset failure must propagate");
        } catch (RuntimeException error) { require(error == sentinel, "Preserve native inset exception identity"); }
        require(XposedBridge.logs.stream().noneMatch(s -> s.contains("split status bar skipped")),
            "Original native exceptions must not be mislabeled as callback failures");
        owner.originalFailure = null;
        owner.mDisplayContent.failConfiguration = true;
        nativeUnchanged(owner, "configuration reflection failure");
        owner.mDisplayContent.failConfiguration = false;
        owner.mDisplayContent.mCurrentFocus.task.failGetBounds = true;
        nativeUnchanged(owner, "task-bounds reflection failure");
        owner.mDisplayContent.mCurrentFocus.task.failGetBounds = false;
        InsetsState traversalFailure = statusState(false);
        traversalFailure.addSource(new InsetsSource(2, new Rect(0, 1600, 2364, 1672)));
        traversalFailure.throwAtIndex = 1;
        require(enforce(owner, owner.mDisplayContent.mCurrentFocus, traversalFailure) == owner.nativeResult
                && owner.nativeResult.sourceSize() == 0 && !traversalFailure.sourceAt(0).isVisible(),
            "A late input traversal failure must not publish a partial copy or mutate borrowed sources");
        InsetsState setterFailure = statusState(false);
        setterFailure.sourceAt(0).throwOnSetVisible = true;
        require(enforce(owner, owner.mDisplayContent.mCurrentFocus, setterFailure) == owner.nativeResult
                && !setterFailure.sourceAt(0).isVisible(),
            "A copied-source setter failure must retain the complete native result");
        require(XposedBridge.logs.stream().filter(s -> s.contains("split status bar skipped")).count() == 1,
            "Repeated reflection/source-copy failures must produce only one diagnostic");
        require(source(enforce(owner, owner.mDisplayContent.mCurrentFocus, statusState(false)), 1) != null,
            "A recovered valid scene must remain usable after earlier callback errors");
    }

    private static void dispatch(InsetsStateController controller, InsetsControlTarget status,
            InsetsControlTarget fakeStatus, InsetsControlTarget nav, InsetsControlTarget fakeNav) throws Throwable {
        XposedBridge.invokeHooked(dispatch, controller, status, fakeStatus, nav, fakeNav);
    }

    private static void finalDispatch() throws Throwable {
        InsetsPolicy owner = fixture();
        InsetsStateController controller = new InsetsStateController(owner.mDisplayContent);
        InsetsControlTarget fakeStatus = new Target();
        InsetsControlTarget nav = new Target();
        InsetsControlTarget fakeNav = new Target();
        WindowState oldHome = new WindowState();
        oldHome.mAttrs.packageName = "com.miui.home";
        int logsBefore = XposedBridge.logs.size();
        for (InsetsControlTarget nativeStatus : new InsetsControlTarget[]{new Target(), null, oldHome}) {
            owner.mFakeStatusControlTarget = fakeStatus;
            dispatch(controller, nativeStatus, fakeStatus, nav, fakeNav);
            require(controller.statusTarget == owner.mShowingPermanentControlTarget
                    && controller.fakeStatusTarget == null && owner.mFakeStatusControlTarget == null,
                "The final provider dispatch must select permanent status control and clear both fake-status references");
            require(controller.navTarget == nav && controller.fakeNavTarget == fakeNav,
                "Final status routing must retain navigation and fake-navigation identity exactly");
        }
        require(XposedBridge.originalCallCount(control) == 0
                && XposedBridge.originalCallCount(dispatch) == 3 && controller.dispatchCalls == 3,
            "Final dispatch must work without relying on the earlier private selection callback executing");
        require(XposedBridge.logs.size() == logsBefore + 1
                && XposedBridge.logs.get(logsBefore).contains("SPLIT controls=permanent; nav=preserved"),
            "Repeated successful routing must produce exactly one positive diagnostic");
        owner.mDisplayContent.mAtmService.splitScreen = false;
        owner.mFakeStatusControlTarget = fakeStatus;
        InsetsControlTarget nativeStatus = new Target();
        dispatch(controller, nativeStatus, fakeStatus, nav, fakeNav);
        require(controller.statusTarget == nativeStatus && controller.fakeStatusTarget == fakeStatus
                && controller.navTarget == nav && controller.fakeNavTarget == fakeNav
                && owner.mFakeStatusControlTarget == fakeStatus,
            "Exiting the actual split scene must restore every native target and preserve policy fake-status state");
        owner.mDisplayContent.mAtmService.splitScreen = true;
        dispatch(controller, nativeStatus, fakeStatus, null, null);
        require(controller.statusTarget == owner.mShowingPermanentControlTarget
                && controller.fakeStatusTarget == null && controller.navTarget == null && controller.fakeNavTarget == null,
            "Absent navigation targets must remain absent while eligible status routing resumes");
    }

    private static void unchangedDispatch(InsetsPolicy owner, InsetsStateController controller,
            InsetsControlTarget status, String explanation) throws Throwable {
        InsetsControlTarget fakeStatus = new Target();
        InsetsControlTarget nav = new Target();
        InsetsControlTarget fakeNav = new Target();
        owner.mFakeStatusControlTarget = fakeStatus;
        dispatch(controller, status, fakeStatus, nav, fakeNav);
        require(controller.statusTarget == status && controller.fakeStatusTarget == fakeStatus
                && controller.navTarget == nav && controller.fakeNavTarget == fakeNav
                && owner.mFakeStatusControlTarget == fakeStatus,
            "Rejected/unknown dispatch must preserve all four arguments and the policy field: " + explanation);
    }

    private static void dispatchGuards() throws Throwable {
        Map<String, Consumer<InsetsPolicy>> guards = new LinkedHashMap<>();
        guards.put("outer display", p -> p.mDisplayContent.configuration.screenType = 1);
        guards.put("secondary display", p -> p.mDisplayContent.displayId = 1);
        guards.put("lockscreen", p -> p.mPolicy.keyguardShowing = true);
        guards.put("no actual split", p -> p.mDisplayContent.mAtmService.splitScreen = false);
        guards.put("missing actual focus", p -> p.mDisplayContent.mCurrentFocus = null);
        guards.put("protected actual focus", p -> p.mDisplayContent.mCurrentFocus.mAttrs.packageName = "com.android.systemui");
        guards.put("actual focus is fullscreen", p -> p.mDisplayContent.mCurrentFocus.windowingMode = 1);
        guards.put("actual focus is freeform", p -> p.mDisplayContent.mCurrentFocus.windowingMode = 5);
        guards.put("actual focus is top/bottom", p -> p.mDisplayContent.mCurrentFocus.task.bounds = new Rect(0, 0, 2364, 820));
        guards.put("missing display policy", p -> p.mPolicy = null);
        guards.put("missing permanent target", p -> p.mShowingPermanentControlTarget = null);
        guards.put("missing owning inset policy", p -> p.mDisplayContent.insetsPolicy = null);
        guards.put("unavailable policy context", p -> p.mDisplayContent = null);
        for (Map.Entry<String, Consumer<InsetsPolicy>> entry : guards.entrySet()) {
            InsetsPolicy owner = fixture();
            DisplayContent display = owner.mDisplayContent;
            entry.getValue().accept(owner);
            unchangedDispatch(owner, new InsetsStateController(display), new Target(), entry.getKey());
        }
        InsetsPolicy owner = fixture();
        unchangedDispatch(owner, new InsetsStateController(null), new Target(), "controller without display");
        for (int type : new int[]{2, 2019, 2040}) {
            WindowState systemTarget = new WindowState();
            systemTarget.mAttrs.type = type;
            unchangedDispatch(owner, new InsetsStateController(owner.mDisplayContent), systemTarget,
                "explicit non-base control candidate " + type);
        }
        WindowState unknownTarget = new WindowState();
        unknownTarget.mAttrs = null;
        unchangedDispatch(owner, new InsetsStateController(owner.mDisplayContent), unknownTarget,
            "candidate with missing attributes");
        require(XposedBridge.logs.stream().noneMatch(s -> s.contains("split status bar skipped") || s.contains("SPLIT controls=")),
            "Ordinary ineligible dispatches must produce neither error nor positive-route diagnostics");
    }

    private static void dispatchFailures() throws Throwable {
        InsetsPolicy owner = fixture();
        InsetsStateController controller = new InsetsStateController(owner.mDisplayContent);
        owner.mDisplayContent.failInsetsPolicy = true;
        unchangedDispatch(owner, controller, new Target(), "display-to-policy reflection failure");
        owner.mDisplayContent.failInsetsPolicy = false;
        owner.mDisplayContent.failConfiguration = true;
        unchangedDispatch(owner, controller, new Target(), "configuration reflection failure");
        owner.mDisplayContent.failConfiguration = false;
        owner.mDisplayContent.mCurrentFocus.task.failGetBounds = true;
        unchangedDispatch(owner, controller, new Target(), "stage-bounds reflection failure");
        owner.mDisplayContent.mCurrentFocus.task.failGetBounds = false;
        require(XposedBridge.logs.stream().filter(s -> s.contains("split status bar skipped")).count() == 1,
            "Repeated dispatcher guard failures must be logged once while all native arguments remain intact");
        RuntimeException sentinel = new IllegalStateException("native final-dispatch failure");
        controller.originalFailure = sentinel;
        try {
            dispatch(controller, new Target(), new Target(), new Target(), new Target());
            throw new AssertionError("Native final-dispatch exception must propagate");
        } catch (RuntimeException error) {
            require(error == sentinel, "Preserve the original final-dispatch exception identity");
        }
        controller.originalFailure = null;
        InsetsControlTarget nav = new Target();
        InsetsControlTarget fakeNav = new Target();
        dispatch(controller, new Target(), new Target(), nav, fakeNav);
        require(controller.statusTarget == owner.mShowingPermanentControlTarget
                && controller.fakeStatusTarget == null && controller.navTarget == nav && controller.fakeNavTarget == fakeNav,
            "A valid final dispatch must remain recoverable after reflection and original-method failures");
    }

    private static final class Target implements InsetsControlTarget {}

    private static WindowState home() {
        WindowState window = new WindowState();
        window.mAttrs.packageName = "com.miui.home";
        window.task.bounds = new Rect(0, 0, 2364, 1672);
        return window;
    }

    private static ActivityRecord visibleStage(InsetsPolicy owner, boolean right) {
        ActivityRecord record = new ActivityRecord();
        record.windowingMode = 6;
        record.mainWindow = stage(right);
        record.mainWindow.mActivityRecord = record;
        record.task = record.mainWindow.task;
        owner.mDisplayContent.activities.add(record);
        return record;
    }

    private static void homeActive(InsetsPolicy owner, ActivityRecord remaining) throws Throwable {
        WindowState current = owner.mDisplayContent.mCurrentFocus;
        require(target(owner, current, false) == owner.mShowingPermanentControlTarget,
            "A proven remaining split app must keep permanent status control with Home/null focus");
        Object status = owner.createBarWindow(1);
        XposedBridge.invokeHooked(visibility, status, false);
        require(barVisible(status), "A remaining visible split stage must retain status visibility");
        XposedBridge.invokeHooked(hide, owner, 1 | 2 | 8);
        require(owner.hiddenTypes == (2 | 8), "Home focus may remove only status hiding, preserving nav and IME");
        InsetsControlTarget nav = new Target();
        InsetsControlTarget fakeNav = new Target();
        InsetsStateController controller = new InsetsStateController(owner.mDisplayContent);
        dispatch(controller, current, new Target(), nav, fakeNav);
        require(controller.statusTarget == owner.mShowingPermanentControlTarget
                && controller.fakeStatusTarget == null && owner.mFakeStatusControlTarget == null
                && controller.navTarget == nav && controller.fakeNavTarget == fakeNav,
            "App-and-Home routing must clear fake-status references while retaining navigation identities");
        InsetsState borrowed = statusState(false);
        InsetsState original = owner.nativeResult;
        InsetsState restored = enforce(owner, remaining.mainWindow, borrowed);
        require(restored != original && source(restored, 1) != null && source(restored, 1).isVisible()
                && source(original, 1) == null && !borrowed.sourceAt(0).isVisible(),
            "Only the remaining app may receive a copied visible status source; inputs stay unchanged");
        require(enforce(owner, home(), borrowed) == original,
            "A qualifying Home-focused scene must not make Home itself a split-inset target");
        require(!remaining.lastIncludeChildWindows, "Visible-stage proof must request the actual non-child main window");
        require(!UnlockPolicy.mayUnlockPackage("com.miui.home"),
            "Supporting Home focus must not weaken the existing global protected-package policy");
    }

    private static void homeVisibleScenes() throws Throwable {
        for (boolean right : new boolean[]{false, true}) {
            InsetsPolicy owner = fixture();
            ActivityRecord remaining = visibleStage(owner, right);
            owner.mDisplayContent.mCurrentFocus = home();
            homeActive(owner, remaining);
            require(owner.mDisplayContent.enumerationCalls > 0,
                "A Home focus alone is insufficient; the display's activities must be inspected");
            owner.mDisplayContent.mCurrentFocus = null;
            homeActive(owner, remaining);
            owner.mDisplayContent.mCurrentFocus = remaining.mainWindow;
            int previousEnumerations = owner.mDisplayContent.enumerationCalls;
            owner.mDisplayContent.failEnumeration = true;
            homeActive(owner, remaining);
            require(owner.mDisplayContent.enumerationCalls == previousEnumerations,
                "A genuinely focused split app must retain the direct path without enumerating old tasks");
            owner.mDisplayContent.failEnumeration = false;
            owner.mDisplayContent.mCurrentFocus = home();
            homeActive(owner, remaining);
            owner.mDisplayContent.activities.clear();
            nativeUnchanged(owner, "both split apps closed while Home remains visible");
            unchangedDispatch(owner, new InsetsStateController(owner.mDisplayContent), new Target(),
                "no remaining split app after closing both panes");
            owner.mDisplayContent.activities.add(remaining);
            owner.mDisplayContent.mAtmService.splitScreen = false;
            nativeUnchanged(owner, "complete exit from split with an old visible activity entry");
            unchangedDispatch(owner, new InsetsStateController(owner.mDisplayContent), new Target(),
                "native split mode has ended");
        }
        require(XposedBridge.logs.stream().filter(s -> s.contains("SPLIT scene=app-and-home; visible-stage=confirmed")).count() == 1,
            "Repeated Home/null/app focus round trips must log the confirmed Home scene only once");
    }

    private static void rejectedHome(InsetsPolicy owner, String explanation) throws Throwable {
        nativeUnchanged(owner, explanation);
        unchangedDispatch(owner, new InsetsStateController(owner.mDisplayContent), new Target(), explanation);
    }

    private static void homeGuards() throws Throwable {
        InsetsPolicy empty = fixture();
        empty.mDisplayContent.mCurrentFocus = home();
        rejectedHome(empty, "Home without any app activity");
        ActivityRecord onlyHome = new ActivityRecord();
        onlyHome.mainWindow = home();
        empty.mDisplayContent.activities.add(onlyHome);
        empty.mDisplayContent.activities.add(null);
        rejectedHome(empty, "Home/null activity entries are not proof of a remaining split app");

        Map<String, Consumer<ActivityRecord>> candidates = new LinkedHashMap<>();
        candidates.put("hidden historical app", r -> r.visible = false);
        candidates.put("visibility no longer requested", r -> r.visibleRequested = false);
        candidates.put("no main window", r -> r.mainWindow = null);
        candidates.put("candidate on another display", r -> r.mainWindow.displayId = 1);
        candidates.put("candidate in freeform", r -> r.mainWindow.windowingMode = 5);
        candidates.put("candidate in fullscreen", r -> r.mainWindow.windowingMode = 1);
        candidates.put("candidate top/bottom stage", r -> r.mainWindow.task.bounds = new Rect(0, 0, 2364, 820));
        candidates.put("candidate centered embedded panel", r -> r.mainWindow.task.bounds = new Rect(200, 0, 1360, 1672));
        candidates.put("candidate non-base window", r -> r.mainWindow.mAttrs.type = 2);
        candidates.put("candidate protected system window", r -> r.mainWindow.mAttrs.packageName = "com.android.systemui");
        candidates.put("candidate protected home window", r -> r.mainWindow.mAttrs.packageName = "com.miui.home");
        for (Map.Entry<String, Consumer<ActivityRecord>> entry : candidates.entrySet()) {
            InsetsPolicy owner = fixture();
            owner.mDisplayContent.mCurrentFocus = home();
            ActivityRecord remaining = visibleStage(owner, true);
            entry.getValue().accept(remaining);
            rejectedHome(owner, entry.getKey());
            if (!remaining.visible || !remaining.visibleRequested) {
                require(remaining.lastIncludeChildWindows,
                    "Hidden/unrequested old activities must be rejected before resolving their main windows");
            }
            owner.mDisplayContent.mCurrentFocus = null;
            rejectedHome(owner, "null focus and " + entry.getKey());
        }

        Map<String, Consumer<InsetsPolicy>> scenes = new LinkedHashMap<>();
        scenes.put("lockscreen", p -> p.mPolicy.keyguardShowing = true);
        scenes.put("no native split", p -> p.mDisplayContent.mAtmService.splitScreen = false);
        scenes.put("outer screen", p -> p.mDisplayContent.configuration.screenType = 1);
        scenes.put("secondary display", p -> p.mDisplayContent.displayId = 1);
        scenes.put("different launcher focus", p -> p.mDisplayContent.mCurrentFocus.mAttrs.packageName = "com.other.launcher");
        scenes.put("notification focus", p -> p.mDisplayContent.mCurrentFocus.mAttrs.type = 2040);
        scenes.put("Home in nonfullscreen mode", p -> p.mDisplayContent.mCurrentFocus.windowingMode = 6);
        scenes.put("Home on another display", p -> p.mDisplayContent.mCurrentFocus.displayId = 1);
        scenes.put("Home without params", p -> p.mDisplayContent.mCurrentFocus.mAttrs = null);
        for (Map.Entry<String, Consumer<InsetsPolicy>> entry : scenes.entrySet()) {
            InsetsPolicy owner = fixture();
            owner.mDisplayContent.mCurrentFocus = home();
            visibleStage(owner, false);
            entry.getValue().accept(owner);
            rejectedHome(owner, entry.getKey());
            require(owner.mDisplayContent.enumerationCalls == 0,
                "Unsupported display/focus conditions must reject Home fallback before inspecting apps");
        }
        require(XposedBridge.logs.stream().noneMatch(s -> s.contains("split status bar skipped") || s.contains("visible-stage=confirmed")),
            "Ordinary rejected Home scenes must neither log inspection failures nor claim visible-stage proof");
    }

    private static void homeFailures() throws Throwable {
        InsetsPolicy owner = fixture();
        owner.mDisplayContent.mCurrentFocus = home();
        ActivityRecord remaining = visibleStage(owner, false);
        owner.mDisplayContent.failEnumeration = true;
        rejectedHome(owner, "display activity enumeration failed");
        owner.mDisplayContent.failEnumeration = false;
        remaining.failVisible = true;
        rejectedHome(owner, "activity visibility lookup failed");
        remaining.failVisible = false;
        remaining.failVisibleRequested = true;
        rejectedHome(owner, "requested activity visibility lookup failed");
        remaining.failVisibleRequested = false;
        remaining.failFindMainWindow = true;
        rejectedHome(owner, "main-window lookup failed");
        remaining.failFindMainWindow = false;
        remaining.task.failGetBounds = true;
        rejectedHome(owner, "visible-stage geometry lookup failed");
        remaining.task.failGetBounds = false;
        owner.mDisplayContent.failEnumerationAfterVisits = 1;
        rejectedHome(owner, "enumeration failed after a valid stage had already been visited");
        owner.mDisplayContent.failEnumerationAfterVisits = -1;
        require(XposedBridge.logs.stream().filter(s -> s.contains("split status bar skipped")).count() == 1,
            "Repeated fallback inspection errors must retain native state and produce only one failure diagnostic");
        require(XposedBridge.logs.stream().noneMatch(s -> s.contains("visible-stage=confirmed")),
            "A partial scan that later fails must not publish a confirmed Home scene");
        homeActive(owner, remaining);
        require(XposedBridge.logs.stream().filter(s -> s.contains("SPLIT scene=app-and-home; visible-stage=confirmed")).count() == 1,
            "A later successful scan must still recover and report the actual Home scene");
    }

    private static void enabledPolicy(SplitStatusBarController toggle) throws Throwable {
        require(toggle.getMode() == SplitStatusBarPolicy.DEFAULT && toggle.getMode() == 1,
            "A fresh split helper must start enabled without an endpoint or settings write");
        InsetsPolicy owner = fixture();
        ActivityRecord remaining = visibleStage(owner, false);
        owner.mDisplayContent.mCurrentFocus = home();
        homeActive(owner, remaining);
        owner.mDisplayContent.mCurrentFocus = remaining.mainWindow;

        DisplayPolicy outerAppearance = new DisplayPolicy();
        outerAppearance.mSystemUiControllingWindow = new WindowState();
        Method opacity = DisplayPolicy.class.getDeclaredMethod("configureStatusBarOpacity", int.class);
        int initialOuter = (Integer) XposedBridge.invokeHooked(opacity, outerAppearance, 1);
        require(StatusBarStylePolicy.requested(0, initialOuter, 1),
            "The independent outer-screen appearance path remains available before changing split mode");
        toggle.setMode(SplitStatusBarPolicy.DISABLED);
        require(toggle.getMode() == 0, "Disabling must update the helper's observable runtime mode");
        nativeUnchanged(owner, "split override disabled with an ordinary focused stage");
        unchangedDispatch(owner, new InsetsStateController(owner.mDisplayContent), new Target(),
            "split override disabled with an ordinary focused stage");
        require((Integer) XposedBridge.invokeHooked(opacity, outerAppearance, 1) == initialOuter
                && StatusBarStylePolicy.height(126, 2.5f) == 90,
            "Disabling split status must not disable outer-screen semi-transparent/90px appearance");

        owner.mDisplayContent.mCurrentFocus = home();
        int enumerations = owner.mDisplayContent.enumerationCalls;
        nativeUnchanged(owner, "split override disabled with a proven app-and-Home scene");
        unchangedDispatch(owner, new InsetsStateController(owner.mDisplayContent), new Target(),
            "split override disabled with a proven app-and-Home scene");
        require(owner.mDisplayContent.enumerationCalls == enumerations,
            "Disabled split policy must return to native behavior before scanning historical activities");
        for (int invalid : new int[]{-1, 2, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            try {
                toggle.setMode(invalid);
                throw new AssertionError("Invalid runtime split mode must be rejected");
            } catch (IllegalArgumentException expected) {
                require(toggle.getMode() == 0, "Invalid mode must retain the previous disabled state");
            }
        }
        toggle.setMode(SplitStatusBarPolicy.ENABLED);
        homeActive(owner, remaining);
        owner.mDisplayContent.mCurrentFocus = remaining.mainWindow;
        homeActive(owner, remaining);
        require(toggle.getMode() == 1, "Re-enabling must restore both ordinary and Home-backed split paths");
    }

    private static void preflight(String fixtureRoot) throws Exception {
        URL common = SplitStatusBarRegression.class.getProtectionDomain().getCodeSource().getLocation();
        for (String fixture : new String[]{"split-missing-target", "split-wrong-field", "split-missing-dispatch", "split-dispatch-wrong-field"}) {
            URL[] paths = {new File(fixtureRoot, fixture).toURI().toURL(), common};
            try (FrameworkLoader loader = new FrameworkLoader(paths)) { install(loader); }
            require(XposedBridge.installAttempts == 0 && XposedBridge.hookedMethods().isEmpty(),
                "Split preflight must reject " + fixture + " before installing any core callback");
        }
        install(SplitStatusBarRegression.class.getClassLoader());
        require(XposedBridge.installAttempts == 19 && XposedBridge.hookedMethods().size() == 19,
            "A split preflight failure must allow a later complete nineteen-hook installation");
    }

    private static final class FrameworkLoader extends URLClassLoader {
        FrameworkLoader(URL[] paths) { super(paths, SplitStatusBarRegression.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.android.server.wm.")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
