package android.content.pm;

public final class ActivityInfo {
    public String packageName = "com.example.reader";
    public ApplicationInfo applicationInfo = new ApplicationInfo();
    public ActivityInfo() { applicationInfo.packageName = packageName; applicationInfo.uid = 10123; }
}
