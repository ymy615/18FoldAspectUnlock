package io.github.foldaspect.unlock;

/** Decisions shared by the hook and the offline regression checks. */
public final class UnlockPolicy {
    public static final String TARGET_DEVICE = "lhasa";
    public static final String TARGET_BUILD = "OS4.0.26.0.XPNCNXM";
    public static final String BUILD_21 = "OS4.0.21.0.XPNCNXM";
    public static final String BUILD_22 = "OS4.0.22.0.XPNCNXM";
    private UnlockPolicy() {}

    public static boolean supports(String device, int sdk, String incremental) {
        return TARGET_DEVICE.equals(device) && sdk == 37
                && (BUILD_21.equals(incremental) || BUILD_22.equals(incremental) || TARGET_BUILD.equals(incremental));
    }

    public static String supportedBuildsText() {
        return BUILD_21 + " / " + BUILD_22 + " / " + TARGET_BUILD;
    }

    static boolean usesLegacyWidthApi(String build) {
        return BUILD_21.equals(build) || BUILD_22.equals(build);
    }

    public static boolean mayUnlockPackage(String name) {
        return name != null && !name.isEmpty() && name.indexOf('/') < 0
                && !name.equals("android")
                && !name.equals("com.android.systemui")
                && !name.equals("com.miui.home")
                && !name.equals("com.android.permissioncontroller")
                && !name.equals("com.google.android.permissioncontroller");
    }

    public static boolean mayUnlockApplication(String name, int uid) {
        return uid >= 0 && uid % 100000 >= 10000 && uid % 100000 < 99000
                && mayUnlockPackage(name);
    }

    public static boolean mayUnlockActivity(String component) {
        if (component == null) return false;
        int slash = component.indexOf('/');
        return slash > 0 && slash < component.length() - 1
                && mayUnlockPackage(component.substring(0, slash));
    }
}
