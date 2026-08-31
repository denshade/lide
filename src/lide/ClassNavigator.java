package lide;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Ctrl+click on class/type names to source files in the project.
 */
public final class ClassNavigator {
    private static final Set<String> SKIP_NAMES = Set.of(
            "out", "build", "target", "node_modules", ".git", ".idea", ".svn", ".hg");

    private static final Pattern JAVA_PACKAGE =
            Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern JAVA_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;");
    private static final Pattern JAVA_DECL = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([A-Za-z_][\\w]*)");

    private ClassNavigator() {
    }

    public record Target(Path path, int caretOffset) {
    }

    public static String identifierAt(String text, int offset) {
        if (text == null || text.isEmpty() || offset < 0 || offset > text.length()) {
            return null;
        }
        int i = offset;
        if (i == text.length()) {
            i--;
        }
        if (i < 0 || !isIdentChar(text.charAt(i))) {
            if (i > 0 && isIdentChar(text.charAt(i - 1))) {
                i--;
            } else {
                return null;
            }
        }
        int start = i;
        while (start > 0 && isIdentChar(text.charAt(start - 1))) {
            start--;
        }
        int end = i + 1;
        while (end < text.length() && isIdentChar(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

    public static boolean isNavigableClassName(String word, Language language) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        if (language.keywords().contains(word)) {
            return false;
        }
        if (language == Language.JAVA || language == Language.JAVASCRIPT) {
            return language.types().contains(word) || looksLikeType(word);
        }
        if (language == Language.PYTHON) {
            return Character.isUpperCase(word.charAt(0));
        }
        return false;
    }

    public static Optional<Target> resolve(
            Path projectRoot,
            Path currentFile,
            String sourceText,
            Language language,
            int offset) {
        String name = identifierAt(sourceText, offset);
        if (!isNavigableClassName(name, language)) {
            return Optional.empty();
        }
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return Optional.empty();
        }

        Path preferred = preferredPath(projectRoot, currentFile, sourceText, language, name);
        List<Path> matches = findSourceFiles(projectRoot, name, language);
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        Path chosen = pickBest(matches, preferred, currentFile);
        int caret = findDeclarationOffset(readQuietly(chosen), name, language);
        return Optional.of(new Target(chosen, Math.max(caret, 0)));
    }

    static Path preferredPath(
            Path projectRoot,
            Path currentFile,
            String sourceText,
            Language language,
            String className) {
        if (language != Language.JAVA) {
            return null;
        }
        Map<String, String> imports = parseJavaImports(sourceText);
        String fqn = imports.get(className);
        if (fqn == null) {
            Matcher pkg = JAVA_PACKAGE.matcher(sourceText);
            if (pkg.find()) {
                fqn = pkg.group(1) + "." + className;
            }
        }
        if (fqn == null) {
            return null;
        }
        String relative = fqn.replace('.', '/') + ".java";
        Path underRoot = projectRoot.resolve(relative).normalize();
        if (Files.isRegularFile(underRoot)) {
            return underRoot;
        }
        // Common layout: src/.../com/example/Foo.java
        Path srcGuess = projectRoot.resolve("src").resolve(relative).normalize();
        if (Files.isRegularFile(srcGuess)) {
            return srcGuess;
        }
        if (currentFile != null) {
            Path parent = currentFile.getParent();
            if (parent != null) {
                Path sibling = parent.resolve(className + ".java");
                if (Files.isRegularFile(sibling)) {
                    return sibling;
                }
            }
        }
        return projectRoot.resolve(relative);
    }

    static Map<String, String> parseJavaImports(String sourceText) {
        Map<String, String> map = new HashMap<>();
        Matcher matcher = JAVA_IMPORT.matcher(sourceText);
        while (matcher.find()) {
            String fqn = matcher.group(1);
            if (fqn.endsWith(".*")) {
                continue;
            }
            int dot = fqn.lastIndexOf('.');
            String simple = dot < 0 ? fqn : fqn.substring(dot + 1);
            map.put(simple, fqn);
        }
        return map;
    }

    static List<Path> findSourceFiles(Path projectRoot, String className, Language language) {
        return findFilesNamed(projectRoot, className + extensionFor(language));
    }

    static List<Path> findFilesNamed(Path projectRoot, String fileName) {
        if (projectRoot == null || !Files.isDirectory(projectRoot)
                || fileName == null || fileName.isEmpty()) {
            return List.of();
        }
        List<Path> found = new ArrayList<>();
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    Path name = dir.getFileName();
                    if (name != null && SKIP_NAMES.contains(name.toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path name = file.getFileName();
                    if (name != null && name.toString().equals(fileName)) {
                        found.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Incomplete search is fine.
        }
        return found;
    }

    static Path pickBest(List<Path> matches, Path preferred, Path currentFile) {
        if (preferred != null) {
            Path normalized = preferred.toAbsolutePath().normalize();
            for (Path match : matches) {
                if (match.equals(normalized)) {
                    return match;
                }
            }
        }
        if (currentFile != null) {
            Path current = currentFile.toAbsolutePath().normalize();
            return matches.stream()
                    .min(Comparator.comparingInt(p -> pathDistance(current, p)))
                    .orElse(matches.get(0));
        }
        return matches.get(0);
    }

    static int findDeclarationOffset(String text, String className, Language language) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (language == Language.JAVA || language == Language.JAVASCRIPT) {
            Matcher matcher = JAVA_DECL.matcher(text);
            while (matcher.find()) {
                if (className.equals(matcher.group(1))) {
                    return matcher.start(1);
                }
            }
        }
        if (language == Language.PYTHON) {
            Pattern py = Pattern.compile("(?m)^\\s*class\\s+" + Pattern.quote(className) + "\\b");
            Matcher matcher = py.matcher(text);
            if (matcher.find()) {
                return matcher.start() + matcher.group().lastIndexOf(className);
            }
        }
        // Fallback: first identifier occurrence that isn't an import line-only miss
        return Math.max(text.indexOf(className), 0);
    }

    private static String extensionFor(Language language) {
        return switch (language) {
            case JAVA -> ".java";
            case JAVASCRIPT -> ".js";
            case GO -> ".go";
            case PYTHON -> ".py";
            default -> ".java";
        };
    }

    private static String readQuietly(Path path) {
        try {
            // Normalize to LF so declaration offsets match the editor document model.
            return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException ex) {
            return "";
        }
    }

    private static int pathDistance(Path a, Path b) {
        Path absA = a.toAbsolutePath().normalize();
        Path absB = b.toAbsolutePath().normalize();
        Path parent = absA.getParent();
        if (parent != null && absB.startsWith(parent)) {
            return absB.getNameCount() - parent.getNameCount();
        }
        return absA.relativize(absB).getNameCount() + 100;
    }

    private static boolean looksLikeType(String word) {
        return !word.isEmpty()
                && Character.isUpperCase(word.charAt(0))
                && word.chars().noneMatch(ch -> ch == '_');
    }

    private static boolean isIdentChar(char c) {
        return Character.isJavaIdentifierPart(c);
    }
}
