package lide;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;

/**
 * Copies a filesystem path to the system clipboard.
 */
public final class PathClipboard {
    private PathClipboard() {
    }

    public static String absolutePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Path is required");
        }
        return path.toAbsolutePath().normalize().toString();
    }

    public static void copy(Path path) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(absolutePath(path)), null);
    }
}
