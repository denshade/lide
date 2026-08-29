package lide;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory file navigation stack (browser-style back/forward).
 */
public final class NavigationHistory {
    public static final int DEFAULT_LIMIT = 50;

    private final List<Path> entries = new ArrayList<>();
    private final int limit;
    private int index = -1;

    public NavigationHistory() {
        this(DEFAULT_LIMIT);
    }

    public NavigationHistory(int limit) {
        this.limit = Math.max(1, limit);
    }

    /**
     * Records {@code path} as the current location. Consecutive visits to the
     * same file are ignored. Visiting after going back drops the forward list.
     */
    public void visit(Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (index >= 0 && index < entries.size() && entries.get(index).equals(normalized)) {
            return;
        }
        if (index >= 0 && index < entries.size() - 1) {
            entries.subList(index + 1, entries.size()).clear();
        }
        entries.add(normalized);
        index = entries.size() - 1;
        while (entries.size() > limit) {
            entries.remove(0);
            index--;
        }
    }

    public boolean canGoBack() {
        return index > 0;
    }

    public boolean canGoForward() {
        return index >= 0 && index < entries.size() - 1;
    }

    public Path back() {
        if (!canGoBack()) {
            return null;
        }
        index--;
        return entries.get(index);
    }

    public Path forward() {
        if (!canGoForward()) {
            return null;
        }
        index++;
        return entries.get(index);
    }

    public Path current() {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    public List<Path> entries() {
        return List.copyOf(entries);
    }

    public int index() {
        return index;
    }

    public void remap(Path from, Path to) {
        if (from == null || to == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            Path remapped = FileRename.remapOpenPath(entries.get(i), from, to);
            if (remapped != null) {
                entries.set(i, remapped.toAbsolutePath().normalize());
            }
        }
    }
}
