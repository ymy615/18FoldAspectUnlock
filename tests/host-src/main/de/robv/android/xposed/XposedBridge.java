package de.robv.android.xposed;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Host recorder, not an implementation of ART method interception. */
public final class XposedBridge {
    private static final Map<Member, XC_MethodHook> hooks = new LinkedHashMap<>();
    private static final Map<Member, Integer> originalCalls = new LinkedHashMap<>();
    public static final List<String> logs = new ArrayList<>();
    public static int installAttempts;
    public static int removedHooks;
    public static int failOnInstallAttempt = -1;

    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook callback) {
        installAttempts++;
        if (installAttempts == failOnInstallAttempt) {
            throw new IllegalStateException("injected installation failure");
        }
        if (hooks.put(method, callback) != null) {
            throw new AssertionError("Duplicate hook: " + method);
        }
        return callback.new Unhook(method);
    }

    static void remove(Member method, XC_MethodHook callback) {
        if (hooks.get(method) == callback) {
            hooks.remove(method);
            removedHooks++;
        }
    }

    public static Set<Member> hookedMethods() {
        return Collections.unmodifiableSet(hooks.keySet());
    }

    public static int originalCallCount(Member method) {
        Integer count = originalCalls.get(method);
        return count == null ? 0 : count;
    }

    public static Object invokeHooked(Method method, Object receiver, Object... arguments)
            throws Throwable {
        XC_MethodHook callback = hooks.get(method);
        XC_MethodHook.MethodHookParam parameter = new XC_MethodHook.MethodHookParam();
        parameter.method = method;
        parameter.thisObject = receiver;
        parameter.args = arguments;
        if (callback != null) {
            callback.beforeHookedMethod(parameter);
        }
        if (!parameter.returnEarly) {
            originalCalls.put(method, originalCallCount(method) + 1);
            method.setAccessible(true);
            try {
                parameter.setResult(method.invoke(receiver, arguments));
            } catch (InvocationTargetException failure) {
                parameter.setThrowable(failure.getCause());
            }
        }
        if (callback != null) callback.afterHookedMethod(parameter);
        if (parameter.hasThrowable()) throw parameter.getThrowable();
        return parameter.getResult();
    }

    public static void log(String message) { logs.add(message); }
    public static void log(Throwable failure) {
        logs.add(failure.getClass().getName() + ": " + failure.getMessage());
    }
}
