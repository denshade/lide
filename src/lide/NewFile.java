package lide;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates a new empty file under a directory.
 */
public final class NewFile {
    private NewFile() {
    }

    /**
     * Resolves {@code name} under {@code directory} and creates an empty file.
     * Nested relative paths such as {@code src/Hello.java} are allowed; parent
     * folders are created as needed.
     */
    public static Path create(Path directory, String name) throws IOException {
        Path path = resolve(directory, name);
        return createEmpty(path);
    }

    public static Path createEmpty(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("File path is required");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.exists(normalized)) {
            throw new FileAlreadyExistsException(normalized.toString());
        }
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createFile(normalized);
        return normalized;
    }

    static Path resolve(Path directory, String name) {
        if (directory == null) {
            throw new IllegalArgumentException("Folder is required");
        }
        Path dir = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Folder does not exist");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Enter a file name");
        }
        String trimmed = name.trim();
        if (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
            throw new IllegalArgumentException("Enter a file name, not a folder");
        }
        Path relative = Path.of(trimmed);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Enter a name relative to the folder");
        }
        for (Path part : relative) {
            String partName = part.toString();
            if (partName.isEmpty() || ".".equals(partName) || "..".equals(partName)) {
                throw new IllegalArgumentException("Invalid file name");
            }
            if (containsIllegalNameChar(partName)) {
                throw new IllegalArgumentException("File name contains invalid characters");
            }
        }
        Path resolved = dir.resolve(relative).normalize();
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("Name must stay inside the folder");
        }
        if (Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("A folder with that name already exists");
        }
        return resolved;
    }

    static boolean containsIllegalNameChar(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == '<' || c == '>' || c == ':' || c == '"'
                    || c == '|' || c == '?' || c == '*') {
                return true;
            }
        }
        return name.endsWith(".") || name.endsWith(" ");
    }
}
