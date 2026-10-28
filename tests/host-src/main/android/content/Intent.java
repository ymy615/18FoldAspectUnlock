package android.content;

import java.util.HashMap;
import java.util.Map;

public final class Intent {
    private final String action;
    private String targetPackage;
    private final Map<String, Object> extras = new HashMap<>();
    public Intent(String action) { this.action = action; }
    public String getAction() { return action; }
    public Intent setPackage(String targetPackage) { this.targetPackage = targetPackage; return this; }
    public String getPackage() { return targetPackage; }
    public Intent putExtra(String key, int value) { extras.put(key, value); return this; }
    public Intent putExtra(String key, boolean value) { extras.put(key, value); return this; }
    public boolean getBooleanExtra(String key, boolean fallback) {
        return extras.get(key) instanceof Boolean ? (Boolean) extras.get(key) : fallback;
    }
    public int getIntExtra(String key, int fallback) {
        return extras.get(key) instanceof Integer ? (Integer) extras.get(key) : fallback;
    }
}
