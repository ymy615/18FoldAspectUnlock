package android.os;

import java.util.HashMap;
import java.util.Map;

public final class Bundle {
    private final Map<String, Integer> ints = new HashMap<>();
    public void putInt(String key, int value) { ints.put(key, value); }
    public int getInt(String key) { return getInt(key, 0); }
    public int getInt(String key, int fallback) { return ints.containsKey(key) ? ints.get(key) : fallback; }
}
