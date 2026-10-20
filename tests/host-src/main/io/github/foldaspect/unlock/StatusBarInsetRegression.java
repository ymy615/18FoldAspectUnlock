package io.github.foldaspect.unlock;

import android.graphics.Insets;
import android.graphics.Rect;
import android.view.InsetsBoundingRect;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.WindowInsets;
import com.android.server.wm.WindowState;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Real inset callback, reflected fields and callback ordering; simplified
 * framework containers. This does not emulate WindowInsets API consumers or ART.
 */
public final class StatusBarInsetRegression {
    private static Method target;

    public static void main(String[] args) throws Throwable {
        if (args.length != 2) throw new IllegalArgumentException("scenario and fixture root required");
        if ("inset-preflight".equals(args[0])) {
            preflight(args[1]);
        } else {
            StatusBarInsetFix fix = new StatusBarInsetFix(StatusBarInsetRegression.class.getClassLoader());
            target = fix.target;
            XposedBridge.hookMethod(target, fix);
            switch (args[0]) {
                case "inset-dimensions": dimensions(); break;
                case "inset-context-guards": contextGuards(); break;
                case "inset-complex-metadata": complexMetadata(); break;
                case "inset-copy-repeat": copyAndRepeat(); break;
                case "inset-exception-preservation": exceptionPreservation(); break;
                default: throw new IllegalArgumentException(args[0]);
            }
        }
        System.out.println("PASS host scenario: " + args[0]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static WindowState fixture() {
        WindowState window = new WindowState();
        // getCompatInsetsState's native result is already in scaled coordinates.
        // Status/cutout were 126 physical pixels before native inverse scaling.
        window.nativeResult = new InsetsState()
            .addSource(new InsetsSource(WindowInsets.Type.statusBars(), new Rect(0, 0, 1280, 153)))
            .addSource(new InsetsSource(WindowInsets.Type.navigationBars(), new Rect(0, 2182, 1280, 2280)))
            .addSource(new InsetsSource(WindowInsets.Type.displayCutout(), new Rect(600, 0, 680, 153)));
        return window;
    }

    private static InsetsState invoke(WindowState window) throws Throwable {
        return (InsetsState) XposedBridge.invokeHooked(target, window);
    }

    private static void dimensions() throws Throwable {
        float[] scales = {0.82466f, 0.75f, 0.5f};
        int[] nativeHeights = {153, 168, 252};
        int[] correctedHeights = {127, 126, 126};
        int[] deliveredHeights = {154, 168, 252};
        for (int i = 0; i < scales.length; i++) {
            WindowState window = fixture();
            window.mGlobalScale = scales[i];
            window.mActivityRecord.mBoundsCompatControllerStub.currentScale = scales[i];
            window.nativeResult.sourceAt(0).getFrame().bottom = nativeHeights[i];
            Rect originalNavigation = new Rect(window.nativeResult.sourceAt(1).getFrame());
            Rect originalCutout = new Rect(window.nativeResult.sourceAt(2).getFrame());
            InsetsState result = invoke(window);
            require(result != window.nativeResult, "Eligible status inset must produce a copy");
            InsetsSource status = result.sourceAt(0);
            require(status.getFrame().equals(new Rect(0, 0, 1280, correctedHeights[i])),
                "Unexpected compensated source geometry for scale " + scales[i]);
            int delivered = (int) (status.getFrame().height() / scales[i]);
            require(delivered == deliveredHeights[i] && delivered >= nativeHeights[i],
                "Client truncation must never reduce the native safe inset");
            require(status.getBoundingRects().length == 1
                    && status.getBoundingRects()[0].equals(new Rect(0, 0, 1280, delivered)),
                "Explicit client bounding rectangle must retain the delivered logical height");
            require(result.sourceAt(1).getFrame().equals(originalNavigation)
                    && result.sourceAt(2).getFrame().equals(originalCutout),
                "Navigation and cutout source geometry must not be compensated");
            require(window.nativeResult.sourceAt(0).getFrame().height() == nativeHeights[i],
                "Compensation must not mutate native source height");
        }
        require(XposedBridge.originalCallCount(target) == scales.length,
            "Insets compensation must run after every native getCompatInsetsState result");
    }

    private static void contextGuards() throws Throwable {
        Map<String, Consumer<WindowState>> cases = new LinkedHashMap<>();
        cases.put("unscaled window", w -> w.mGlobalScale = 1f);
        cases.put("zero scale", w -> w.mGlobalScale = 0f);
        cases.put("negative scale", w -> w.mGlobalScale = -0.5f);
        cases.put("unsupported tiny scale", w -> {
            w.mGlobalScale = 0.05f;
            w.mActivityRecord.mBoundsCompatControllerStub.currentScale = 0.05f;
        });
        cases.put("nonfinite scale", w -> w.mGlobalScale = Float.NaN);
        cases.put("upscaled window", w -> w.mGlobalScale = 1.2f);
        cases.put("child window", w -> w.mIsChildWindow = true);
        cases.put("secondary display", w -> w.displayId = 1);
        cases.put("freeform window", w -> w.windowingMode = 5);
        cases.put("non-base application window", w -> w.mAttrs.type = 2);
        cases.put("protected SystemUI", w -> w.mAttrs.packageName = "com.android.systemui");
        cases.put("protected framework", w -> w.mAttrs.packageName = "android");
        cases.put("missing layout params", w -> w.mAttrs = null);
        cases.put("missing activity", w -> w.mActivityRecord = null);
        cases.put("missing compat controller", w -> w.mActivityRecord.mBoundsCompatControllerStub = null);
        cases.put("inner display", w -> w.mActivityRecord.mBoundsCompatControllerStub.outerCompatScene = false);
        cases.put("ordinary noncompat window", w -> w.mActivityRecord.mBoundsCompatControllerStub.inSizeCompat = false);
        cases.put("split scene", w -> w.mActivityRecord.mBoundsCompatControllerStub.multipleSplit = true);
        cases.put("game scene", w -> w.mActivityRecord.mBoundsCompatControllerStub.game = true);
        cases.put("scale disagreement", w -> w.mActivityRecord.mBoundsCompatControllerStub.currentScale = 0.7f);
        cases.put("missing client frame", w -> w.frame = null);
        cases.put("client below top", w -> w.frame.top = 30);
        cases.put("zero client width", w -> w.frame.right = 0);
        cases.put("landscape client", w -> w.frame.bottom = 720);
        cases.put("narrow source right edge", w -> w.nativeResult.sourceAt(0).getFrame().right = 1279);
        cases.put("narrow source left edge", w -> w.nativeResult.sourceAt(0).getFrame().left = 1);
        cases.put("source below top", w -> w.nativeResult.sourceAt(0).getFrame().top = 1);
        cases.put("empty source", w -> w.nativeResult.sourceAt(0).getFrame().bottom = 0);
        cases.put("source reaches client bottom", w -> w.nativeResult.sourceAt(0).getFrame().bottom = 2280);
        cases.put("no useful size reduction", w -> w.nativeResult.sourceAt(0).getFrame().bottom = 1);
        for (Map.Entry<String, Consumer<WindowState>> entry : cases.entrySet()) {
            WindowState window = fixture();
            entry.getValue().accept(window);
            InsetsState original = window.nativeResult;
            require(invoke(window) == original, "Guard must preserve native result: " + entry.getKey());
        }
        require(XposedBridge.logs.isEmpty(), "Rejected contexts must not generate callback failures");
    }

    private static void complexMetadata() throws Throwable {
        Map<String, Consumer<InsetsSource>> cases = new LinkedHashMap<>();
        cases.put("attached insets", s -> s.attachedInsets = Insets.NONE);
        cases.put("explicit bounding metadata", s -> s.boundingRects = new Rect[]{new Rect(0, 0, 1280, 153)});
        cases.put("empty explicit bounding metadata", s -> s.boundingRects = new Rect[0]);
        cases.put("structured bounding metadata", s -> s.insetsBoundingRects = new InsetsBoundingRect[]{new InsetsBoundingRect()});
        cases.put("empty structured bounding metadata", s -> s.insetsBoundingRects = new InsetsBoundingRect[0]);
        cases.put("different visible frame", s -> s.visibleFrame = new Rect(0, 0, 1280, 100));
        for (Map.Entry<String, Consumer<InsetsSource>> entry : cases.entrySet()) {
            WindowState window = fixture();
            entry.getValue().accept(window.nativeResult.sourceAt(0));
            require(invoke(window) == window.nativeResult,
                "Unsupported source metadata must be left untouched: " + entry.getKey());
        }
        WindowState mixed = fixture();
        mixed.nativeResult.sourceAt(0).boundingRects = new Rect[0];
        mixed.nativeResult.addSource(new InsetsSource(WindowInsets.Type.statusBars(), new Rect(0, 0, 1280, 153)));
        InsetsState result = invoke(mixed);
        require(result.sourceAt(0).getFrame().height() == 153
                && result.sourceAt(0).getBoundingRects().length == 0
                && result.sourceAt(3).getFrame().height() == 127,
            "Complex source metadata must not prevent a separate simple source from being corrected");
    }

    private static void copyAndRepeat() throws Throwable {
        WindowState window = fixture();
        InsetsSource originalStatus = window.nativeResult.sourceAt(0);
        originalStatus.visibleFrame = new Rect(originalStatus.getFrame());
        InsetsState first = invoke(window);
        require(first != window.nativeResult, "The native InsetsState must not be reused for mutation");
        for (int i = 0; i < first.sourceSize(); i++) {
            require(first.sourceAt(i) != window.nativeResult.sourceAt(i)
                    && first.sourceAt(i).getFrame() != window.nativeResult.sourceAt(i).getFrame(),
                "The reflected copy constructor must be called with copySources=true");
        }
        require(first.sourceAt(0).getVisibleFrame().height() == 127
                && first.sourceAt(0).getVisibleFrame() != originalStatus.getVisibleFrame(),
            "Matching visible-frame geometry must be compensated on the independent copy");
        require(originalStatus.getFrame().height() == 153
                && originalStatus.getVisibleFrame().height() == 153
                && originalStatus.getBoundingRects() == null,
            "Original frame, visible frame and metadata must remain unchanged");
        first.sourceAt(0).getFrame().bottom = 5;
        first.sourceAt(1).getFrame().top = 5;
        InsetsState second = invoke(window);
        require(second.sourceAt(0).getFrame().height() == 127
                && second.sourceAt(1).getFrame().top == 2182,
            "Repeated native delivery must create a fresh, unpolluted correction");
        window.nativeResult = second;
        require(invoke(window) == second && second.sourceAt(0).getFrame().height() == 127,
            "An already corrected result must not accumulate another scale reduction");
    }

    private static void exceptionPreservation() throws Throwable {
        WindowState nullResult = fixture();
        nullResult.nativeResult = null;
        require(invoke(nullResult) == null, "Null native result must remain null");
        WindowState originalFailure = fixture();
        RuntimeException sentinel = new IllegalStateException("native failure sentinel");
        originalFailure.originalFailure = sentinel;
        try {
            invoke(originalFailure);
            throw new AssertionError("Original method exception must propagate");
        } catch (RuntimeException error) {
            require(error == sentinel, "After callback must retain the original exception identity");
        }
        require(XposedBridge.logs.isEmpty(), "Original exceptions must not be treated as callback errors");
        WindowState setterFailure = fixture();
        setterFailure.nativeResult.sourceAt(0).visibleFrame = new Rect(0, 0, 1280, 153);
        setterFailure.nativeResult.sourceAt(0).throwOnSetBoundingRects = true;
        require(invoke(setterFailure) == setterFailure.nativeResult
                && setterFailure.nativeResult.sourceAt(0).getFrame().height() == 153
                && setterFailure.nativeResult.sourceAt(0).getVisibleFrame().height() == 153,
            "Failure after copying/mutating must discard the partial copy and preserve native geometry");
        WindowState traversalFailure = fixture();
        traversalFailure.nativeResult.throwAtIndex = 1;
        require(invoke(traversalFailure) == traversalFailure.nativeResult
                && traversalFailure.nativeResult.sourceAt(0).getFrame().height() == 153,
            "A later-source failure must not publish an earlier source's partial adjustment");
        WindowState reflectedFailure = fixture();
        reflectedFailure.failGetFrame = true;
        require(invoke(reflectedFailure) == reflectedFailure.nativeResult,
            "Runtime reflection errors must fall back to the original result");
        require(XposedBridge.logs.stream().filter(s -> s.contains("status inset correction skipped")).count() == 1,
            "Repeated runtime failures must log once rather than flood system_server: " + XposedBridge.logs);
        require(invoke(fixture()).sourceAt(0).getFrame().height() == 127,
            "A runtime failure must not permanently disable later valid corrections");
    }

    private static void preflight(String fixtureRoot) throws Exception {
        URL common = StatusBarInsetRegression.class.getProtectionDomain().getCodeSource().getLocation();
        URL variant = new File(fixtureRoot, "insets-wrong-field").toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{variant, common},
                StatusBarInsetRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("com.android.server.wm.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> result = findLoadedClass(name);
                    if (result == null) result = findClass(name);
                    if (resolve) resolveClass(result);
                    return result;
                }
            }
        }) {
            try {
                new StatusBarInsetFix(loader);
                throw new AssertionError("Changed field type must be rejected during constructor preflight");
            } catch (NoSuchFieldException expected) {
                require(expected.getMessage().contains("mGlobalScale"), "Expected exact field-type guard");
            }
        }
        ClassLoader missingMetadataType = new ClassLoader(StatusBarInsetRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("android.view.InsetsBoundingRect")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        try {
            new StatusBarInsetFix(missingMetadataType);
            throw new AssertionError("Missing metadata API must be rejected before installing a callback");
        } catch (ClassNotFoundException expected) {
            require(expected.getMessage().contains("InsetsBoundingRect"), "Expected exact metadata API guard");
        }
        require(XposedBridge.hookedMethods().isEmpty() && XposedBridge.installAttempts == 0,
            "Failed reflective preflight must not leave a callback installed");
    }
}
