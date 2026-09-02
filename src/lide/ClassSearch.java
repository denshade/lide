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

/**
 * Finds source files by class / type name under a project directory.
 */
public final class ClassSearch {
    static final int DEFAULT_MAX_FILES = 5000;
    static final int DEFAULT_MAX_RESULTS = 80;

    private ClassSearch() {
    }

    public record Hit(String className, Path path, String relativePath) {
        @Override
        public String toString() {
            return className + "  " + relativePath;
        }
    }

    public static List<Hit> listClasses(Path projectRoot) {
        return listClasses(projectRoot, DEFAULT_MAX_FILES);
    }

    static List<Hit> listClasses(Path projectRoot, int maxFiles) {
        List<Hit> found = new ArrayList<>();
        if (projectRoot == null || maxFiles <= 0) {
            return found;
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return found;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (found.size() >= maxFiles) {
                        return FileVisitResult.TERMINATE;
                    }
                    Path name = dir.getFileName();
                    if (name != null && !dir.equals(root)) {
                        String text = name.toString();
                        if (text.startsWith(".") || ScriptFinder.SKIP_DIRS.contains(text)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (found.size() >= maxFiles) {
                        return FileVisitResult.TERMINATE;
                    }
                    Path namePath = file.getFileName();
                    if (namePath == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fileName = namePath.toString();
                    if (fileName.startsWith(".") || !isClassFile(fileName)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String className = stem(fileName);
                    if (className == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path absolute = file.toAbsolutePath().normalize();
                    String relative = root.relativize(absolute).toString().replace('\\', '/');
                    found.add(new Hit(className, absolute, relative));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            AppLog.exception("Class search failed under " + root, ex);
        }
        found.sort(Comparator.comparing((Hit h) -> h.className().toLowerCase(Locale.ROOT))
                .thenComparing(Hit::relativePath));
        return found;
    }

    public static List<Hit> filter(List<Hit> classes, String query) {
        return filter(classes, query, DEFAULT_MAX_RESULTS);
    }

    static List<Hit> filter(List<Hit> classes, String query, int maxResults) {
        if (classes == null || classes.isEmpty() || maxResults <= 0) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String trimmed = query.trim();
        List<Scored> scored = new ArrayList<>();
        for (Hit hit : classes) {
            int rank = rank(hit.className(), trimmed);
            if (rank < 0) {
                continue;
            }
            scored.add(new Scored(hit, rank));
        }
        scored.sort(Comparator
                .comparingInt((Scored s) -> s.rank)
                .thenComparingInt(s -> s.hit.className().length())
                .thenComparing(s -> s.hit.className().toLowerCase(Locale.ROOT))
                .thenComparing(s -> s.hit.relativePath()));
        int limit = Math.min(maxResults, scored.size());
        List<Hit> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            result.add(scored.get(i).hit);
        }
        return result;
    }

    /**
     * Lower is better. {@code -1} means no match.
     */
    static int rank(String className, String query) {
        if (className == null || query == null || query.isEmpty()) {
            return -1;
        }
        if (className.equalsIgnoreCase(query)) {
            return 0;
        }
        if (startsWithIgnoreCase(className, query)) {
            return 1;
        }
        if (containsIgnoreCase(className, query)) {
            return 2;
        }
        if (camelMatch(className, query)) {
            return 3;
        }
        return -1;
    }

    static boolean isClassFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        Language language = Language.fromPath(Path.of(fileName));
        return language == Language.JAVA
                || language == Language.JAVASCRIPT
                || language == Language.GO
                || language == Language.PYTHON;
    }

    static String stem(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        return fileName.substring(0, dot);
    }

    static boolean camelMatch(String className, String query) {
        if (className == null || query == null || query.isEmpty()) {
            return false;
        }
        int ni = 0;
        for (int qi = 0; qi < query.length(); qi++) {
            char qc = query.charAt(qi);
            boolean found = false;
            while (ni < className.length()) {
                char nc = className.charAt(ni++);
                if (Character.toLowerCase(nc) != Character.toLowerCase(qc)) {
                    continue;
                }
                if (Character.isUpperCase(qc) && !Character.isUpperCase(nc) && ni > 1) {
                    continue;
                }
                found = true;
                break;
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean containsIgnoreCase(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private record Scored(Hit hit, int rank) {
    }
}
