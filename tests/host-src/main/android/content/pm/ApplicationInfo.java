package android.content.pm;

public final class ApplicationInfo {
    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_UPDATED_SYSTEM_APP = 128;
    public String packageName;
    public int uid;
    public int flags;

    public boolean isSystemApp() { return (flags & FLAG_SYSTEM) != 0; }
    public boolean isUpdatedSystemApp() { return (flags & FLAG_UPDATED_SYSTEM_APP) != 0; }
}
