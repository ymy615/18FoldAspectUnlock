package android.view;

public interface WindowManager {
    public static final class LayoutParams {
        public static final int TYPE_BASE_APPLICATION = 1;
        public int type = TYPE_BASE_APPLICATION;
        public String packageName = "com.example.reader";
    }
}
