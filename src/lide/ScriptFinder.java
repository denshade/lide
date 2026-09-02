package lide;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds runnable scripts under a project directory.
 */
public final class ScriptFinder {
    static final Set<String> SKIP_DIRS = Set.of(
            "out", "build", "target", "node_modules", ".git", ".idea", ".svn", ".hg");

    static final Set<String> SCRIPT_EXTENSIONS = Set.of(
            "bat", "cmd", "ps1", "sh", "bash", "py");

    public static final int DEFAULT_LIMIT = 200;

    private ScriptFinder() {
    }

    public static boolean isScript(Path path) {
        if (path == null) {
            return false;
        }
        Path name = path.getFileName();
        if (name == null) {
            return false;
        }
        String fileName = name.toString().toLowerCase(Locale.ROOT);
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return SCRIPT_EXTENSIONS.contains(fileName.substring(dot + 1));
    }

    public static List<Path> findScripts(Path projectRoot) {
        return findScripts(projectRoot, DEFAULT_LIMIT);
    }

    public static List<Path> findScripts(Path projectRoot, int limit) {
        List<Path> found = new ArrayList<>();
        if (projectRoot == null || !Files.isDirectory(projectRoot) || limit <= 0) {
            return found;
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path name = dir.getFileName();
                    if (name != null && !dir.equals(root) && SKIP_DIRS.contains(name.toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && isScript(file)) {
                        found.add(file.toAbsolutePath().normalize());
                        if (found.size() >= limit) {
                            return FileVisitResult.TERMINATE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            AppLog.exception("Script scan failed under " + root, ex);
        }
        found.sort(Comparator.comparing(p -> root.relativize(p).toString().toLowerCase(Locale.ROOT)));
        return found;
    }

    public static String displayName(Path projectRoot, Path script) {
        if (projectRoot == null || script == null) {
            return script == null ? "" : script.toString();
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        Path absolute = script.toAbsolutePath().normalize();
        if (absolute.startsWith(root)) {
            return root.relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString();
    }
}
