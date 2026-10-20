package android.view;

import java.util.ArrayList;
import java.util.List;

public final class InsetsState {
    private final List<InsetsSource> sources = new ArrayList<>();
    public int throwAtIndex = -1;

    public InsetsState() {}
    public InsetsState(InsetsState original, boolean copySources) {
        for (InsetsSource source : original.sources) {
            sources.add(copySources ? new InsetsSource(source) : source);
        }
        throwAtIndex = original.throwAtIndex;
    }
    public InsetsState addSource(InsetsSource source) { sources.add(source); return this; }
    public int sourceSize() { return sources.size(); }
    public InsetsSource sourceAt(int index) {
        if (index == throwAtIndex) throw new IllegalStateException("injected source traversal failure");
        return sources.get(index);
    }
}
