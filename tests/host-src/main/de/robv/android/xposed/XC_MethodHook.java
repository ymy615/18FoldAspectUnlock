package de.robv.android.xposed;

import java.lang.reflect.Member;

/** Minimal before/after-callback and unhook contract used by the real module. */
public class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam parameter) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam parameter) throws Throwable {}

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        boolean returnEarly;

        public void setResult(Object value) { result = value; throwable = null; returnEarly = true; }
        public Object getResult() { return result; }
        public void setThrowable(Throwable value) { throwable = value; result = null; returnEarly = true; }
        public Throwable getThrowable() { return throwable; }
        public boolean hasThrowable() { return throwable != null; }
    }

    public final class Unhook {
        private final Member method;
        private boolean removed;

        Unhook(Member method) { this.method = method; }
        public void unhook() {
            if (!removed) {
                XposedBridge.remove(method, XC_MethodHook.this);
                removed = true;
            }
        }
    }
}
