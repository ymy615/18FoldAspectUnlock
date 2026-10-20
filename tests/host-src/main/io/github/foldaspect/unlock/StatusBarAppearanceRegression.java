package io.github.foldaspect.unlock;

import android.graphics.Rect;
import com.android.internal.statusbar.LetterboxDetails;
import com.android.server.wm.AppCompatLetterboxPolicy;
import com.android.server.wm.DisplayPolicy;
import com.android.server.wm.WindowState;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Verifies returned appearance flags, not SystemUI scrim rendering or icon color. */
public final class StatusBarAppearanceRegression {
    private static final int COMPACT_MARKER = 0x10000000;
    private static Method opacity, appearance, letterbox;

    public static void main(String[] args) throws Throwable {
        if (args.length != 2) throw new IllegalArgumentException("scenario and fixture root required");
        StatusBarAppearance fix = new StatusBarAppearance(StatusBarAppearanceRegression.class.getClassLoader());
        opacity = fix.opacityTarget;
        appearance = fix.appearanceTarget;
        letterbox = fix.letterboxTarget;
        XposedBridge.hookMethod(opacity, fix);
        XposedBridge.hookMethod(appearance, fix);
        XposedBridge.hookMethod(letterbox, fix.letterboxCallback);
        switch (args[0]) {
            case "appearance-native-flags": nativeFlags(); break;
            case "appearance-context-exit": contextAndExit(); break;
            case "appearance-exception-preservation": exceptionPreservation(); break;
            case "letterbox-context-restore": letterboxContext(); break;
            case "letterbox-exception-preservation": letterboxExceptions(); break;
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS host scenario: " + args[0]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static DisplayPolicy fixture() {
        DisplayPolicy policy = new DisplayPolicy();
        policy.mSystemUiControllingWindow = new WindowState();
        return policy;
    }

    private static int invokeOpacity(DisplayPolicy policy, int flags) throws Throwable {
        return (Integer) XposedBridge.invokeHooked(opacity, policy, flags);
    }

    private static int invokeAppearance(DisplayPolicy policy, int flags, WindowState navigationWindow)
            throws Throwable {
        return (Integer) XposedBridge.invokeHooked(appearance, policy, flags, navigationWindow);
    }

    private static void nativeFlags() throws Throwable {
        DisplayPolicy policy = fixture();
        int statusBits = 1 | 4 | 8;
        int preservedBits = 2 | 16 | 64 | 256;
        int input = statusBits | preservedBits;
        // Different native-added bits verify that callback uses the original return.
        policy.nativeOpacityBits = 512;
        policy.nativeNavigationBits = 1024;
        WindowState unrelatedNavigationWindow = new WindowState();
        unrelatedNavigationWindow.mAttrs.packageName = "com.android.systemui";
        int opacityResult = invokeOpacity(policy, input);
        int appearanceResult = invokeAppearance(policy, input, unrelatedNavigationWindow);
        require(opacityResult == (preservedBits | 512 | 32 | COMPACT_MARKER),
            "Opacity callback must select semi/compact mode and retain every unrelated/native-added bit");
        require(appearanceResult == (preservedBits | 1024 | 32 | COMPACT_MARKER),
            "Appearance callback must carry the compact marker and preserve navigation flags");
        int merged = opacityResult | appearanceResult;
        require((merged & statusBits) == 0 && (merged & (32 | COMPACT_MARKER)) == (32 | COMPACT_MARKER),
            "Both native OR inputs must retain compact/semi mode without opaque/light/low-profile bits");
        require(policy.mSystemUiControllingWindow.requestedVisibleTypes == 1
                && "com.example.reader".equals(policy.mSystemUiControllingWindow.mAttrs.packageName)
                && "com.android.systemui".equals(unrelatedNavigationWindow.mAttrs.packageName),
            "Appearance callbacks must leave app visibility/attrs and navigation source unchanged");
        require(XposedBridge.originalCallCount(opacity) == 1
                && XposedBridge.originalCallCount(appearance) == 1,
            "Both status callbacks must run after their original framework methods");
    }

    private static void contextAndExit() throws Throwable {
        int input = 1 | 4 | 8 | 2 | 64;
        DisplayPolicy policy = fixture();
        int entered = invokeOpacity(policy, input);
        require((entered & COMPACT_MARKER) != 0 && StatusBarStylePolicy.requested(0, entered, 1),
            "Entering an eligible scene must send a usable compact marker to the scoped renderer");
        policy.mSystemUiControllingWindow.mActivityRecord.mBoundsCompatControllerStub.outerCompatScene = false;
        int exitedOpacity = invokeOpacity(policy, input);
        int exitedAppearance = invokeAppearance(policy, input, null);
        require(exitedOpacity == (input | policy.nativeOpacityBits)
                && exitedAppearance == (input | policy.nativeNavigationBits)
                && ((exitedOpacity | exitedAppearance) & COMPACT_MARKER) == 0
                && !StatusBarStylePolicy.requested(0, exitedOpacity | exitedAppearance, 1),
            "Leaving the outer scene must restore each native return immediately without stale flag state");
        policy.mSystemUiControllingWindow.mActivityRecord.mBoundsCompatControllerStub.outerCompatScene = true;
        require(StatusBarStylePolicy.requested(0, invokeAppearance(policy, input, null), 1),
            "Returning to an eligible outer scene must restore the scoped renderer request");
        Map<String, Consumer<DisplayPolicy>> guards = new LinkedHashMap<>();
        guards.put("missing controlling window", p -> p.mSystemUiControllingWindow = null);
        guards.put("secondary display", p -> p.mSystemUiControllingWindow.displayId = 1);
        guards.put("freeform", p -> p.mSystemUiControllingWindow.windowingMode = 5);
        guards.put("app immersive request", p -> p.mSystemUiControllingWindow.requestedVisibleTypes = 0);
        guards.put("protected package", p -> p.mSystemUiControllingWindow.mAttrs.packageName = "com.android.systemui");
        guards.put("non-main window", p -> p.mSystemUiControllingWindow.mAttrs.type = 2);
        guards.put("missing params", p -> p.mSystemUiControllingWindow.mAttrs = null);
        guards.put("missing activity", p -> p.mSystemUiControllingWindow.mActivityRecord = null);
        guards.put("missing controller", p -> p.mSystemUiControllingWindow.mActivityRecord.mBoundsCompatControllerStub = null);
        guards.put("noncompat", p -> p.mSystemUiControllingWindow.mActivityRecord.mBoundsCompatControllerStub.inSizeCompat = false);
        guards.put("split", p -> p.mSystemUiControllingWindow.mActivityRecord.mBoundsCompatControllerStub.multipleSplit = true);
        guards.put("game", p -> p.mSystemUiControllingWindow.mActivityRecord.mBoundsCompatControllerStub.game = true);
        guards.put("missing frame", p -> p.mSystemUiControllingWindow.frame = null);
        guards.put("empty frame", p -> p.mSystemUiControllingWindow.frame.right = 0);
        guards.put("landscape", p -> p.mSystemUiControllingWindow.frame.bottom = 720);
        for (Map.Entry<String, Consumer<DisplayPolicy>> entry : guards.entrySet()) {
            DisplayPolicy candidate = fixture();
            entry.getValue().accept(candidate);
            require(invokeOpacity(candidate, input) == (input | candidate.nativeOpacityBits)
                    && invokeAppearance(candidate, input, new WindowState()) == (input | candidate.nativeNavigationBits),
                "Ineligible scene must retain both native returns: " + entry.getKey());
        }
        require(XposedBridge.logs.isEmpty(), "Skipped appearance contexts must not be callback failures");
    }

    private static void exceptionPreservation() throws Throwable {
        int input = 1 | 8 | 64;
        DisplayPolicy policy = fixture();
        RuntimeException sentinel = new IllegalStateException("native appearance failure");
        policy.originalFailure = sentinel;
        for (boolean useOpacity : new boolean[]{true, false}) {
            try {
                if (useOpacity) invokeOpacity(policy, input);
                else invokeAppearance(policy, input, null);
                throw new AssertionError("Native appearance failure must propagate");
            } catch (RuntimeException error) {
                require(error == sentinel, "Original appearance exception identity must be preserved");
            }
        }
        require(XposedBridge.logs.isEmpty(), "Original exceptions must not be logged as callback failures");
        policy.originalFailure = null;
        policy.mSystemUiControllingWindow.failGetFrame = true;
        require(invokeOpacity(policy, input) == (input | policy.nativeOpacityBits)
                && invokeAppearance(policy, input, null) == (input | policy.nativeNavigationBits),
            "Reflection failure must preserve the original appearance result");
        require(XposedBridge.logs.size() == 1
                && XposedBridge.logs.get(0).contains("status appearance skipped"),
            "One shared callback instance must log repeated runtime failure only once");
        policy.mSystemUiControllingWindow.failGetFrame = false;
        require((invokeOpacity(policy, input) & 32) != 0,
            "A later valid native result must remain correctable after a transient error");
    }

    private static AppCompatLetterboxPolicy letterboxFixture() {
        AppCompatLetterboxPolicy policy = new AppCompatLetterboxPolicy();
        WindowState window = new WindowState();
        policy.mActivityRecord = window.mActivityRecord;
        policy.mActivityRecord.mainWindow = window;
        return policy;
    }

    private static LetterboxDetails invokeLetterbox(AppCompatLetterboxPolicy policy) throws Throwable {
        return (LetterboxDetails) XposedBridge.invokeHooked(letterbox, policy);
    }

    private static void letterboxContext() throws Throwable {
        AppCompatLetterboxPolicy policy = letterboxFixture();
        WindowState window = policy.mActivityRecord.mainWindow;
        LetterboxDetails original = policy.nativeResult;
        Rect originalFrame = new Rect(window.frame);
        Rect originalInner = new Rect(original.innerBounds);
        Rect originalFull = new Rect(original.fullBounds);
        require(invokeLetterbox(policy) == null,
            "A focused eligible app must suppress only the returned SystemUI recoloring metadata");
        require(!policy.mActivityRecord.lastIncludeChildWindows,
            "Main-window lookup must exclude child windows");
        require(window.frame.equals(originalFrame) && original.innerBounds.equals(originalInner)
                && original.fullBounds.equals(originalFull) && policy.nativeResult == original,
            "Suppressing the return must not mutate native details or window geometry");
        window.focused = false;
        require(invokeLetterbox(policy) == original,
            "A nonfocused app must retain its exact native LetterboxDetails result");
        window.focused = true;
        window.mActivityRecord.mBoundsCompatControllerStub.outerCompatScene = false;
        require(invokeLetterbox(policy) == original,
            "Leaving the outer scene must restore native letterbox metadata immediately");
        window.mActivityRecord.mBoundsCompatControllerStub.outerCompatScene = true;
        require(invokeLetterbox(policy) == null,
            "Reentering the focused eligible scene must suppress metadata again without stale state");
        Map<String, Consumer<AppCompatLetterboxPolicy>> guards = new LinkedHashMap<>();
        guards.put("missing owner", p -> p.mActivityRecord = null);
        guards.put("missing main window", p -> p.mActivityRecord.mainWindow = null);
        guards.put("immersive app", p -> p.mActivityRecord.mainWindow.requestedVisibleTypes = 0);
        guards.put("protected app", p -> p.mActivityRecord.mainWindow.mAttrs.packageName = "com.android.systemui");
        guards.put("noncompat", p -> p.mActivityRecord.mBoundsCompatControllerStub.inSizeCompat = false);
        guards.put("split", p -> p.mActivityRecord.mBoundsCompatControllerStub.multipleSplit = true);
        guards.put("game", p -> p.mActivityRecord.mBoundsCompatControllerStub.game = true);
        guards.put("landscape", p -> p.mActivityRecord.mainWindow.frame.bottom = 720);
        for (Map.Entry<String, Consumer<AppCompatLetterboxPolicy>> entry : guards.entrySet()) {
            AppCompatLetterboxPolicy candidate = letterboxFixture();
            entry.getValue().accept(candidate);
            require(invokeLetterbox(candidate) == candidate.nativeResult,
                "Ineligible window must preserve letterbox metadata: " + entry.getKey());
        }
        require(XposedBridge.logs.isEmpty(), "Skipped letterbox contexts must not be callback errors");
    }

    private static void letterboxExceptions() throws Throwable {
        AppCompatLetterboxPolicy policy = letterboxFixture();
        policy.nativeResult = null;
        require(invokeLetterbox(policy) == null, "Null native letterbox metadata must remain null");
        RuntimeException sentinel = new IllegalStateException("native letterbox failure");
        policy.originalFailure = sentinel;
        try {
            invokeLetterbox(policy);
            throw new AssertionError("Original letterbox failure must propagate");
        } catch (RuntimeException error) {
            require(error == sentinel, "Original letterbox exception identity must be preserved");
        }
        require(XposedBridge.logs.isEmpty(), "Original letterbox exception must not be logged as callback failure");
        policy.originalFailure = null;
        policy.nativeResult = new LetterboxDetails();
        policy.mActivityRecord.failFindMainWindow = true;
        require(invokeLetterbox(policy) == policy.nativeResult,
            "Main-window reflection failure must preserve valid native metadata");
        require(invokeLetterbox(policy) == policy.nativeResult && XposedBridge.logs.size() == 1,
            "Repeated letterbox callback failures must not flood system_server logs");
        policy.mActivityRecord.failFindMainWindow = false;
        require(invokeLetterbox(policy) == null, "Later valid metadata must remain correctable");
    }
}
