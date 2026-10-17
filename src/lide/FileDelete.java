package lide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Deletes one file. Folders are refused so a tree action cannot remove a directory.
 */
public final class FileDelete {
    private FileDelete() {
    }

    public static void delete(Path source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Path is required");
        }
        Path path = source.toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("Cannot delete a folder");
        }
        if (!Files.exists(path)) {
            throw new NoSuchFileException(path.toString());
        }
        Files.delete(path);
    }
}
