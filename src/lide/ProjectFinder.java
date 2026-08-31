package lide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Searches text files under a project directory for a query string.
 */
public final class ProjectFinder {
    public static final int DEFAULT_MAX_MATCHES = 500;
    public static final int DEFAULT_MAX_FILES = 5000;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private ProjectFinder() {
    }

    public record Match(Path file, String relativePath, int lineNumber, int offset, int length, String snippet) {
        @Override
        public String toString() {
            return relativePath + ":" + lineNumber + ": " + snippet;
        }
    }

    public record Result(List<Match> matches, int filesSearched, boolean truncated) {
        public int size() {
            return matches.size();
        }
    }

    public static Result search(Path projectRoot, String query, boolean matchCase) {
        return search(projectRoot, query, matchCase, DEFAULT_MAX_MATCHES, DEFAULT_MAX_FILES,
                MAX_FILE_BYTES, () -> false);
    }

    static Result search(
            Path projectRoot,
            String query,
            boolean matchCase,
            int maxMatches,
            int maxFiles,
            long maxFileBytes,
            BooleanSupplier cancelled) {
        List<Match> matches = new ArrayList<>();
        if (projectRoot == null || query == null || query.isEmpty() || maxMatches <= 0) {
            return new Result(matches, 0, false);
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new Result(matches, 0, false);
        }
        BooleanSupplier stop = cancelled == null ? () -> false : cancelled;
        int[] filesSearched = {0};
        boolean[] truncated = {false};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (stop.getAsBoolean() || truncated[0]) {
                        return FileVisitResult.TERMINATE;
                    }
                    Path name = dir.getFileName();
                    if (name != null && !dir.equals(root) && ScriptFinder.SKIP_DIRS.contains(name.toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (stop.getAsBoolean() || truncated[0]) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!attrs.isRegularFile() || shouldSkipFile(file, attrs.size(), maxFileBytes)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (filesSearched[0] >= maxFiles) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    filesSearched[0]++;
                    searchFile(root, file, query, matchCase, maxMatches, matches, truncated);
                    return truncated[0] ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Return whatever was found.
        }
        if (stop.getAsBoolean()) {
            truncated[0] = true;
        }
        return new Result(List.copyOf(matches), filesSearched[0], truncated[0]);
    }

    private static void searchFile(
            Path root,
            Path file,
            String query,
            boolean matchCase,
            int maxMatches,
            List<Match> matches,
            boolean[] truncated) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException ex) {
            return;
        }
        if (BinaryDetector.isBinary(bytes)) {
            return;
        }
        String text = TextFinder.normalizeNewlines(new String(bytes, StandardCharsets.UTF_8));
        String relative = ScriptFinder.displayName(root, file);
        for (int offset : TextFinder.findAll(text, query, matchCase)) {
            if (matches.size() >= maxMatches) {
                truncated[0] = true;
                return;
            }
            String line = TextFinder.lineAt(text, offset).strip();
            if (line.length() > 120) {
                line = line.substring(0, 119) + "…";
            }
            matches.add(new Match(
                    file.toAbsolutePath().normalize(),
                    relative,
                    TextFinder.lineNumber(text, offset),
                    offset,
                    query.length(),
                    line));
        }
    }

    static boolean shouldSkipFile(Path file, long size, long maxFileBytes) {
        if (size > maxFileBytes) {
            return true;
        }
        Path name = file.getFileName();
        if (name == null) {
            return true;
        }
        return TextFiles.isKnownBinary(file);
    }
}
