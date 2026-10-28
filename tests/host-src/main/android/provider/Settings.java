package android.provider;

import android.content.ContentResolver;

public final class Settings {
    public static final class Global {
        public static int getInt(ContentResolver resolver, String key, int fallback) {
            Integer value = resolver.globalInts.get(key);
            return value == null ? fallback : value;
        }
        public static boolean putInt(ContentResolver resolver, String key, int value) {
            if (resolver.rejectWrites) return false;
            resolver.globalInts.put(key, value);
            resolver.writes++;
            return true;
        }
    }
}
