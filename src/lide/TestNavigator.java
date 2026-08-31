package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pairs a class file with its test file by name: {@code Foo.java} ↔ {@code FooTest.java}.
 */
public final class TestNavigator {
    private static final String TEST_SUFFIX = "Test";

    private TestNavigator() {
    }

    public static Optional<Path> findTest(Path projectRoot, Path currentFile) {
        String target = testFileName(fileName(currentFile));
        return findNamed(projectRoot, currentFile, target);
    }

    public static Optional<Path> findImplementation(Path projectRoot, Path currentFile) {
        String target = implementationFileName(fileName(currentFile));
        return findNamed(projectRoot, currentFile, target);
    }

    static String testFileName(String fileName) {
        String stem = stem(fileName);
        String ext = extension(fileName);
        if (stem == null || ext == null || isTestStem(stem)) {
            return null;
        }
        return stem + TEST_SUFFIX + ext;
    }

    static String implementationFileName(String fileName) {
        String stem = stem(fileName);
        String ext = extension(fileName);
        if (stem == null || ext == null || !isTestStem(stem)) {
            return null;
        }
        return stem.substring(0, stem.length() - TEST_SUFFIX.length()) + ext;
    }

    static boolean isTestStem(String stem) {
        return stem != null && stem.length() > TEST_SUFFIX.length() && stem.endsWith(TEST_SUFFIX);
    }

    private static Optional<Path> findNamed(Path projectRoot, Path currentFile, String fileName) {
        if (projectRoot == null || !Files.isDirectory(projectRoot) || currentFile == null
                || fileName == null) {
            return Optional.empty();
        }
        List<Path> matches = new ArrayList<>(ClassNavigator.findFilesNamed(projectRoot, fileName));
        Path current = currentFile.toAbsolutePath().normalize();
        matches.remove(current);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ClassNavigator.pickBest(matches, null, current));
    }

    private static String fileName(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        return path.getFileName().toString();
    }

    private static String stem(String fileName) {
        int dot = lastDot(fileName);
        if (dot <= 0) {
            return null;
        }
        return fileName.substring(0, dot);
    }

    private static String extension(String fileName) {
        int dot = lastDot(fileName);
        if (dot <= 0) {
            return null;
        }
        return fileName.substring(dot);
    }

    private static int lastDot(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return -1;
        }
        return fileName.lastIndexOf('.');
    }
}
