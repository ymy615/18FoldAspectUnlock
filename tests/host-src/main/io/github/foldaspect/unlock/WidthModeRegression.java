package io.github.foldaspect.unlock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import com.android.server.wm.ActivityRecord;
import com.android.server.wm.ActivityTaskManagerService;
import com.android.server.wm.ActivityTaskManagerServiceStub;
import com.android.server.wm.BoundsCompatUtils;
import com.android.server.wm.MiuiSizeCompatService;
import com.android.server.wm.Task;
import com.android.server.wm.WindowProcessController;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Real width callbacks and receiver; no UI, Binder or Android permission emulation. */
public final class WidthModeRegression {
    private static final Rect OUTER = new Rect(0, 0, 1168, 1712);
    private static final String PACKAGE = "com.example.reader";

    public static void main(String[] args) throws Throwable {
        if (args.length != 2) throw new IllegalArgumentException("scenario and manifest path required");
        switch (args[0]) {
            case "width-policy-geometry": geometry(); break;
            case "width-native-wide": nativeWide(); break;
            case "width-context-optouts": contextOptouts(); break;
            case "width-endpoint-persistence": endpointPersistence(args[1]); break;
            case "width-registration-failures": registrationFailures(); break;
            case "width-refresh-targets": refreshTargets(); break;
            case "width-refresh-failures": refreshFailures(); break;
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS host scenario: " + args[0]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.000001f, message + ": " + actual + " != " + expected);
    }

    private static final class Harness implements AutoCloseable {
        final WidthModeController controller;
        final MiuiSizeCompatService service = new MiuiSizeCompatService();
        final ActivityTaskManagerService atms = new ActivityTaskManagerService();
        final BoundsCompatUtils utils = new BoundsCompatUtils();
        final Context context;
        final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        Harness() throws Exception { this(new ContentResolver()); }
        Harness(ContentResolver store) throws Exception {
            context = new Context(store);
            service.mContext = context;
            atms.folded = true;
            controller = new WidthModeController(WidthModeRegression.class.getClassLoader());
            hooks.add(XposedBridge.hookMethod(controller.ratioTarget, controller.ratioCallback));
            hooks.add(XposedBridge.hookMethod(controller.scaleTarget, controller.scaleCallback));
            hooks.add(XposedBridge.hookMethod(controller.readyTarget, controller.readyCallback));
        }
        void ready() throws Throwable { XposedBridge.invokeHooked(controller.readyTarget, service, atms); }
        float ratio(ActivityRecord record) throws Throwable {
            return (Float) XposedBridge.invokeHooked(controller.ratioTarget, null, record);
        }
        float scale(String name, Rect rect, boolean outer, float ratio) throws Throwable {
            return (Float) XposedBridge.invokeHooked(controller.scaleTarget, utils, name, 0, rect, outer, ratio);
        }
        BroadcastReceiver request(boolean write, int mode) {
            return context.dispatchOrdered(new Intent(WidthModePolicy.ACTION).setPackage("android")
                .putExtra(WidthModePolicy.EXTRA_WRITE, write).putExtra(WidthModePolicy.EXTRA_MODE, mode));
        }
        Bundle ok(boolean write, int mode) {
            BroadcastReceiver response = request(write, mode);
            require(response.getResultCode() == Activity.RESULT_OK && response.getResultExtras(false) != null,
                "Valid width request must produce an ordered result");
            return response.getResultExtras(false);
        }
        @Override public void close() { for (XC_MethodHook.Unhook hook : hooks) hook.unhook(); }
    }

    private static void geometry() {
        require(WidthModePolicy.valid(0) && WidthModePolicy.valid(1)
                && !WidthModePolicy.valid(-1) && !WidthModePolicy.valid(2), "Only narrow/wide modes are valid");
        near(WidthModePolicy.legacyScale(1168, 1712, 16f / 9f), 1712f / 2076f,
            "Legacy outer scale must derive from full 1168px width rather than the new 1080px cap");
        near(WidthModePolicy.legacyScale(1168, 1712, 9f / 16f), 1712f / 2076f,
            "Reciprocal ratios must normalize to the same scale");
        for (int[] size : new int[][]{{0,1712},{-1,1712},{1168,0},{1712,1168},{1168,1168},{1080,2400}}) {
            require(WidthModePolicy.legacyScale(size[0], size[1], 16f / 9f) == -1f,
                "Invalid/landscape/upscale geometry must opt out: " + Arrays.toString(size));
        }
        for (float ratio : new float[]{0f,-1f,Float.NaN,Float.POSITIVE_INFINITY}) {
            require(WidthModePolicy.legacyScale(1168,1712,ratio) == -1f, "Invalid ratio must opt out");
        }
    }

    private static void nativeWide() throws Throwable {
        try (Harness h = new Harness()) {
            h.ready();
            ActivityRecord app = new ActivityRecord();
            near(h.ratio(app), 20f/9f, "Default narrow mode must retain the new native 20:9 ratio");
            near(h.scale(PACKAGE, OUTER, true, 20f/9f), 1712f/2400f,
                "Narrow mode must retain the native 1080px-capped scale");
            require(h.ok(true,1).getInt("mode") == 1, "Wide selection must be acknowledged");
            near(h.ratio(app), 16f/9f, "Wide mode must restore the inspected 16:9 default");
            near(h.ratio(null), 16f/9f, "Native package/process ratio query with null record must work");
            near(h.scale(PACKAGE, OUTER, true, h.ratio(app)), 1712f/2076f,
                "Wide mode must restore approximately 0.82466 using the uncapped width");
            near(h.scale(PACKAGE, new Rect(0,0,1440,2240), true, 16f/9f), 0.875f,
                "A -1 generated by the 1080px cap must be recoverable when old geometry is valid");
            ActivityTaskManagerServiceStub.get().scaleModes.put(PACKAGE,3);
            require(h.scale(PACKAGE,new Rect(0,0,1440,2240),true,16f/9f) == -1f,
                "Explicit native scale-mode-3 opt-out must remain -1 despite recoverable geometry");
            ActivityTaskManagerServiceStub.get().scaleModes.clear();
            h.ok(true,0);
            near(h.ratio(app),20f/9f,"Returning to narrow must restore the native ratio immediately");
            near(h.scale(PACKAGE,OUTER,true,20f/9f),1712f/2400f,
                "Returning to narrow must restore the native scale without persisting app ratios");
        }
    }

    private static void contextOptouts() throws Throwable {
        try (Harness h = new Harness()) {
            h.ready(); h.ok(true,1);
            Map<String,Consumer<ActivityRecord>> guards = new LinkedHashMap<>();
            guards.put("protected package",a -> a.info.packageName="com.android.systemui");
            guards.put("privileged UID",a -> a.info.applicationInfo.uid=1000);
            guards.put("secondary display",a -> a.displayId=1);
            guards.put("multiwindow",a -> a.windowingMode=5);
            guards.put("missing info",a -> a.info=null);
            guards.put("missing app info",a -> a.info.applicationInfo=null);
            guards.put("missing controller",a -> a.mBoundsCompatControllerStub=null);
            guards.put("inner scene",a -> a.mBoundsCompatControllerStub.outerCompatScene=false);
            guards.put("game scene",a -> a.mBoundsCompatControllerStub.game=true);
            guards.put("split scene",a -> a.mBoundsCompatControllerStub.multipleSplit=true);
            for (Map.Entry<String,Consumer<ActivityRecord>> guard : guards.entrySet()) {
                ActivityRecord app = new ActivityRecord(); guard.getValue().accept(app);
                near(h.ratio(app),20f/9f,"Excluded activity must retain native ratio: "+guard.getKey());
            }
            h.atms.folded=false;
            near(h.ratio(new ActivityRecord()),20f/9f,"Unfolded global display must retain native ratio");
            near(h.scale(PACKAGE,OUTER,true,16f/9f),1712f/1920f,"Unfolded global display must retain native scale");
            h.atms.folded=true; h.atms.configuration.orientation=Configuration.ORIENTATION_LANDSCAPE;
            near(h.ratio(null),20f/9f,"Landscape must preserve native package/process ratio");
            h.atms.configuration.orientation=Configuration.ORIENTATION_PORTRAIT;
            h.utils.forcedScale=0.4f;
            for (String name : new String[]{null,"","android","com.android.systemui","com.miui.home"}) {
                near(h.scale(name,OUTER,true,16f/9f),0.4f,"Protected/invalid package scale must stay native");
            }
            near(h.scale(PACKAGE,OUTER,false,16f/9f),0.4f,"Non-outer scale call must stay native");
            near(h.scale(PACKAGE,OUTER,true,4f/3f),0.4f,"Other ratios must stay native");
            for (Rect rect : new Rect[]{null,new Rect(0,0,0,1712),new Rect(0,0,1712,1168)}) {
                near(h.scale(PACKAGE,rect,true,16f/9f),0.4f,"Invalid target geometry must not replace native result");
            }
            for (float value : new float[]{0f,-2f,Float.NaN,Float.POSITIVE_INFINITY}) {
                h.utils.forcedScale=value;
                require(Float.floatToIntBits(h.scale(PACKAGE,OUTER,true,16f/9f))==Float.floatToIntBits(value),
                    "Other native opt-outs/nonfinite scales must retain their exact result");
            }
            h.utils.forcedScale=null;
            require(h.scale(PACKAGE,new Rect(0,0,1080,2400),true,16f/9f)==-1f,
                "A native negative result must remain negative if old geometry would upscale");
            RuntimeException sentinel=new IllegalStateException("native scale error"); h.utils.scaleFailure=sentinel;
            try { h.scale(PACKAGE,OUTER,true,16f/9f); throw new AssertionError("Native error must propagate"); }
            catch(RuntimeException error){require(error==sentinel,"Native scale exception identity must be retained");}
        }
    }

    private static void endpointPersistence(String manifestPath) throws Throwable {
        ContentResolver store = new ContentResolver();
        try (Harness h = new Harness(store)) {
            h.ready(); h.ready();
            require(h.context.registrations==1 && h.context.receiverFlags==Context.RECEIVER_EXPORTED
                && h.context.receiverHandler==h.service.mFgHandler
                && WidthModePolicy.ACTION.equals(h.context.filter.action)
                && WidthModePolicy.PERMISSION.equals(h.context.receiverPermission),
                "Endpoint registration must be idempotent, exported and protected by the exact signature permission");
            assertSignaturePermission(manifestPath);
            require(h.ok(false,-999).getInt("mode")==0 && store.writes==0,
                "Missing setting must query as narrow without writing a default");
            require(h.request(true,99).getResultCode()!=Activity.RESULT_OK && store.writes==0,
                "Invalid requested mode must not write or claim success");
            h.context.dispatchOrdered(new Intent("unrelated.ACTION").setPackage("android").putExtra("write",true).putExtra("mode",1));
            require(store.writes==0,"Wrong action must not modify settings");
            require(h.ok(true,1).getInt("mode")==1 && store.globalInts.get(WidthModePolicy.SETTING)==1 && store.writes==1,
                "Valid write must persist exactly the module setting and acknowledge the new mode");
            h.ok(false,0); h.ok(true,1);
            require(store.writes==1 && store.globalInts.size()==1,"Query and repeated mode must not rewrite settings");
        }
        try(Harness restarted=new Harness(store)) {
            restarted.ready(); require(restarted.ok(false,0).getInt("mode")==1,
                "A fresh controller must reload the persisted width selection");
        }
        store.globalInts.put(WidthModePolicy.SETTING,77);
        try(Harness invalidSaved=new Harness(store)) {
            invalidSaved.ready(); require(invalidSaved.ok(false,0).getInt("mode")==0 && store.globalInts.get(WidthModePolicy.SETTING)==77,
                "Invalid saved mode must fall back to narrow without silently rewriting storage");
        }
    }

    private static void assertSignaturePermission(String path) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        NodeList permissions=factory.newDocumentBuilder().parse(new File(path)).getElementsByTagName("permission");
        boolean found=false;
        for(int i=0;i<permissions.getLength();i++) {
            Element p=(Element)permissions.item(i); String ns="http://schemas.android.com/apk/res/android";
            if(WidthModePolicy.PERMISSION.equals(p.getAttributeNS(ns,"name"))) {
                found="signature".equals(p.getAttributeNS(ns,"protectionLevel"));
            }
        }
        require(found,"The declared receiver permission must have signature protection in the actual manifest");
    }

    private static void registrationFailures() throws Throwable {
        try(Harness h=new Harness()) {
            RuntimeException sentinel=new IllegalStateException("native ready failure"); h.service.readyFailure=sentinel;
            try{h.ready();throw new AssertionError("Native ready failure must propagate");}
            catch(RuntimeException error){require(error==sentinel,"Native ready failure must be retained");}
            require(h.context.registrations==0,"Failed native ready must not register a receiver");
            h.service.readyFailure=null; h.service.mFgHandler=null; h.ready();
            require(h.context.registrations==0,"Missing handler must defer registration");
            h.service.mFgHandler=new android.os.Handler(); h.context.failRegistration=true; h.ready();
            require(h.context.registrations==0,"Failed receiver setup must remain retryable");
            h.context.failRegistration=false; h.ready();
            require(h.context.registrations==1,"Receiver setup must succeed after dependencies recover");
            h.context.getContentResolver().rejectWrites=true;
            require(h.request(true,1).getResultCode()!=Activity.RESULT_OK && h.ok(false,0).getInt("mode")==0,
                "Failed persistence must not change active mode");
            h.context.getContentResolver().rejectWrites=false; h.atms.mGlobalLock=null;
            require(h.request(true,1).getResultCode()!=Activity.RESULT_OK && h.context.getContentResolver().writes==0,
                "Missing native lock must be rejected before persistence");
        }
    }

    private static void refreshTargets() throws Throwable {
        try(Harness h=new Harness()) {
            h.ready();
            ActivityRecord a=new ActivityRecord(), b=new ActivityRecord(), background=new ActivityRecord(), detached=new ActivityRecord();
            b.app=a.app; b.task=a.task; a.task.runtimeFullscreen=true;
            background.visible=false; detached.attached=false;
            ActivityRecord optout=new ActivityRecord(), game=new ActivityRecord(), split=new ActivityRecord(), protectedApp=new ActivityRecord();
            optout.mBoundsCompatControllerStub.inSizeCompat=false;
            game.mBoundsCompatControllerStub.game=true; split.mBoundsCompatControllerStub.multipleSplit=true;
            protectedApp.info.packageName="com.android.systemui";
            List<ActivityRecord> targets=Arrays.asList(a,b,background,detached);
            h.atms.mRootWindowContainer.activities.addAll(targets);
            h.atms.mRootWindowContainer.activities.addAll(Arrays.asList(optout,game,split,protectedApp));
            h.service.mWideFoldConfigs.put("0#com.example.reader",20f/9f);
            h.service.mWideFoldConfigs.put("0#com.example.disabled",0f);
            Map<String,Float> original=new LinkedHashMap<>(h.service.mWideFoldConfigs);
            for(WindowProcessController process:Arrays.asList(a.app,background.app,detached.app)) process.beforeUpdate=()->{
                require(Thread.holdsLock(h.atms.mGlobalLock),"Refresh must run under the native global lock");
                for(ActivityRecord ar:targets) require(ar.mBoundsCompatControllerStub.mGlobalScale==-1f,
                    "All eligible controller caches must be invalidated before the first process update");
            };
            h.service.beforeApply=()->require(a.app.updates==1 && background.app.updates==1 && detached.app.updates==1,
                "All distinct processes must update before visible activity application");
            Bundle response=h.ok(true,1);
            require(response.getInt("refreshed")==2 && response.getInt("deferred")==0,
                "Only two visible attached targets should be reported refreshed");
            require(a.applyCalls==1 && b.applyCalls==1 && a.lastEnabled && b.lastEnabled
                    && a.lastChanges==3328 && b.lastChanges==3328,
                "Visible attached targets must use native enabled configuration application");
            require(background.applyCalls==0 && detached.applyCalls==0 && background.mBoundsCompatControllerStub.mGlobalScale==-1f,
                "Background/detached activities must defer application while keeping cache invalidation");
            require(a.task.changes==1 && background.task.changes==0 && detached.task.changes==0,
                "Changed tasks must be notified once, only after successful visible application");
            for(ActivityRecord ar:Arrays.asList(optout,game,split,protectedApp)) require(ar.mBoundsCompatControllerStub.mGlobalScale==0.8f
                    && ar.app.updates==0 && ar.applyCalls==0,"Excluded app/task state must not be refreshed");
            require(h.service.mWideFoldConfigs.equals(original) && h.service.settingsWrites==0
                    && a.task.runtimeFullscreen && !background.task.runtimeFullscreen,
                "Width selection must not rewrite per-app ratio entries or task runtime-fullscreen flags");
        }
    }

    private static void refreshFailures() throws Throwable {
        try(Harness h=new Harness()) {
            h.ready(); ActivityRecord a=new ActivityRecord(), b=new ActivityRecord();
            b.app=a.app; a.app.failUpdate=true; a.failApply=true; b.task.failChange=true;
            h.atms.mRootWindowContainer.activities.addAll(Arrays.asList(a,b));
            h.service.mWideFoldConfigs.put("0#com.example.reader",20f/9f);
            h.service.mWideFoldConfigs.put("0#com.example.disabled",0f);
            Map<String,Float> originalRatios=new LinkedHashMap<>(h.service.mWideFoldConfigs);
            Bundle partial=h.ok(true,1);
            require(partial.getInt("mode")==1 && partial.getInt("refreshed")==1 && partial.getInt("deferred")==3,
                "Process, activity and task failures must be counted while unrelated refresh work continues");
            require(a.app.updates==1 && b.applyCalls==1,"Failure of a shared process must not duplicate updates or abort other activities");
            h.atms.mRootWindowContainer.failEnumeration=true;
            Bundle failed=h.ok(true,0);
            require(failed.getInt("mode")==0 && failed.getInt("refreshed")==0 && failed.getInt("deferred")==1
                    && h.context.getContentResolver().globalInts.get(WidthModePolicy.SETTING)==0,
                "Fatal refresh failure after saving must acknowledge the persisted mode with a deferred result");
            int writesBeforeRetry=h.context.getContentResolver().writes;
            h.atms.mRootWindowContainer.failEnumeration=false;
            a.app.failUpdate=false; a.failApply=false; b.task.failChange=false;
            Bundle retry=h.ok(true,0);
            require(retry.getInt("mode")==0 && retry.getInt("refreshed")==2 && retry.getInt("deferred")==0
                    && a.applyCalls==2 && b.applyCalls==2,
                "Retrying the saved mode after a transient enumeration failure must refresh both visible targets");
            require(h.context.getContentResolver().writes==writesBeforeRetry
                    && h.service.mWideFoldConfigs.equals(originalRatios) && h.service.settingsWrites==0,
                "Same-mode refresh retry must not repeat persistence or alter native per-app choices");
            h.atms.folded=false;
            float cache=a.mBoundsCompatControllerStub.mGlobalScale;
            Bundle inner=h.ok(true,1);
            require(inner.getInt("refreshed")==0 && inner.getInt("deferred")==0
                    && a.mBoundsCompatControllerStub.mGlobalScale==cache,
                "An inner-screen selection must persist for later outer use without refreshing inner activities");
        }
    }
}
