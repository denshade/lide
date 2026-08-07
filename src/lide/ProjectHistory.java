package lide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted most-recently-opened project directories.
 */
public final class ProjectHistory {
    public static final int DEFAULT_LIMIT = 10;

    private final Path storageFile;
    private final int limit;
    private final List<Path> entries = new ArrayList<>();

    public ProjectHistory() {
        this(defaultStorageFile(), DEFAULT_LIMIT);
    }

    public ProjectHistory(Path storageFile, int limit) {
        this.storageFile = storageFile;
        this.limit = Math.max(1, limit);
        load();
    }

    public static Path defaultStorageFile() {
        return Path.of(System.getProperty("user.home"), ".lide", "recent-projects.txt");
    }

    public List<Path> entries() {
        return List.copyOf(entries);
    }

    public void remember(Path directory) {
        if (directory == null) {
            return;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        entries.removeIf(existing -> existing.equals(normalized));
        entries.add(0, normalized);
        while (entries.size() > limit) {
            entries.remove(entries.size() - 1);
        }
        save();
    }

    public void remove(Path directory) {
        if (directory == null) {
            return;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        if (entries.removeIf(existing -> existing.equals(normalized))) {
            save();
        }
    }

    public void clear() {
        if (entries.isEmpty()) {
            return;
        }
        entries.clear();
        save();
    }

    private void load() {
        entries.clear();
        if (!Files.isRegularFile(storageFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                Path path = Path.of(trimmed).toAbsolutePath().normalize();
                if (!entries.contains(path)) {
                    entries.add(path);
                }
                if (entries.size() >= limit) {
                    break;
                }
            }
        } catch (IOException ignored) {
            entries.clear();
        }
    }

    private void save() {
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>(entries.size());
            for (Path entry : entries) {
                lines.add(entry.toString());
            }
            Files.write(storageFile, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // History is best-effort.
        }
    }
}
