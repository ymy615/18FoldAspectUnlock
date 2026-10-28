package android.content;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory backing store, independent of any controller instance. */
public final class ContentResolver {
    public final Map<String, Integer> globalInts = new LinkedHashMap<>();
    public int writes;
    public boolean rejectWrites;
}
