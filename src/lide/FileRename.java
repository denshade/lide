package lide;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Renames a file or folder in place (same parent directory).
 */
public final class FileRename {
    private FileRename() {
    }

    public static Path rename(Path source, String newName) throws IOException {
        Path from = requireExisting(source);
        Path to = resolveSibling(from, newName);
        String currentName = from.getFileName() != null ? from.getFileName().toString() : "";
        if (currentName.equals(newName == null ? "" : newName.trim())) {
            return from;
        }
        if (from.equals(to)) {
            return renameChangingCase(from, to);
        }
        if (Files.exists(to)) {
            throw new FileAlreadyExistsException(to.toString());
        }
        Files.move(from, to);
        return to.toAbsolutePath().normalize();
    }

    /**
     * If {@code openPath} is {@code renamedFrom} or a path under it, returns the
     * corresponding path under {@code renamedTo}; otherwise {@code null}.
     */
    public static Path remapOpenPath(Path openPath, Path renamedFrom, Path renamedTo) {
        if (openPath == null || renamedFrom == null || renamedTo == null) {
            return null;
        }
        Path open = openPath.toAbsolutePath().normalize();
        Path from = renamedFrom.toAbsolutePath().normalize();
        Path to = renamedTo.toAbsolutePath().normalize();
        if (open.equals(from)) {
            return to;
        }
        if (open.startsWith(from)) {
            return to.resolve(from.relativize(open)).normalize();
        }
        return null;
    }

    static Path resolveSibling(Path source, String newName) {
        if (source == null) {
            throw new IllegalArgumentException("Path is required");
        }
        Path from = source.toAbsolutePath().normalize();
        Path parent = from.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Cannot rename this path");
        }
        String trimmed = validateSimpleName(newName);
        Path to = parent.resolve(trimmed).normalize();
        if (to.getParent() == null || !to.getParent().equals(parent)) {
            throw new IllegalArgumentException("Name must stay in the same folder");
        }
        return to;
    }

    static String validateSimpleName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Enter a name");
        }
        String trimmed = newName.trim();
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new IllegalArgumentException("Name cannot contain path separators");
        }
        if (".".equals(trimmed) || "..".equals(trimmed)) {
            throw new IllegalArgumentException("Invalid name");
        }
        if (NewFile.containsIllegalNameChar(trimmed)) {
            throw new IllegalArgumentException("Name contains invalid characters");
        }
        return trimmed;
    }

    private static Path requireExisting(Path source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("Path is required");
        }
        Path from = source.toAbsolutePath().normalize();
        if (!Files.exists(from)) {
            throw new NoSuchFileException(from.toString());
        }
        return from;
    }

    private static Path renameChangingCase(Path from, Path to) throws IOException {
        Path parent = from.getParent();
        Path temp = parent.resolve(from.getFileName().toString() + ".rename-tmp");
        int n = 0;
        while (Files.exists(temp)) {
            temp = parent.resolve(from.getFileName().toString() + ".rename-tmp" + n);
            n++;
        }
        Files.move(from, temp);
        try {
            Files.move(temp, to);
        } catch (IOException ex) {
            try {
                Files.move(temp, from);
            } catch (IOException ignored) {
                // Keep the original exception.
            }
            throw ex;
        }
        return to.toAbsolutePath().normalize();
    }
}
