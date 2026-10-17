package lide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a Java source file for a new class in an existing package folder.
 */
public final class NewJavaClass {
    private static final Pattern PACKAGE =
            Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    private NewJavaClass() {
    }

    /**
     * Folder and package of a Java file already open in the editor.
     * {@code packageName} is empty when that file has no package declaration.
     */
    public record Namespace(Path directory, String packageName) {
    }

    public static Namespace namespaceOf(Path javaFile, String source) {
        if (javaFile == null) {
            throw new IllegalArgumentException("Open a Java file first");
        }
        Path file = javaFile.toAbsolutePath().normalize();
        Path directory = file.getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Folder does not exist");
        }
        return new Namespace(directory, packageName(source));
    }

    /**
     * Simple class name from a dialog entry. A trailing {@code .java} is removed.
     */
    public static String className(String entered) {
        if (entered == null || entered.isBlank()) {
            throw new IllegalArgumentException("Enter a class name");
        }
        String trimmed = entered.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".java")) {
            trimmed = trimmed.substring(0, trimmed.length() - ".java".length()).trim();
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Enter a class name");
        }
        if (!isJavaIdentifier(trimmed) || Language.JAVA.keywords().contains(trimmed)) {
            throw new IllegalArgumentException("Enter a valid Java class name");
        }
        return trimmed;
    }

    public static String sourceText(String packageName, String className) {
        String simple = className(className);
        String pkg = packageName == null ? "" : packageName.trim();
        if (!pkg.isEmpty() && !isPackageName(pkg)) {
            throw new IllegalArgumentException("Enter a valid package name");
        }
        if (pkg.isEmpty()) {
            return "public class " + simple + " {\n}\n";
        }
        return "package " + pkg + ";\n\npublic class " + simple + " {\n}\n";
    }

    public static Path create(Path directory, String packageName, String enteredName) throws IOException {
        String simple = className(enteredName);
        Path path = NewFile.resolve(directory, simple + ".java");
        if (Files.exists(path)) {
            throw new FileAlreadyExistsException(path.toString());
        }
        Files.writeString(
                path,
                sourceText(packageName, simple),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        return path;
    }

    static String packageName(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        Matcher matcher = PACKAGE.matcher(normalized);
        if (!matcher.find()) {
            return "";
        }
        String name = matcher.group(1);
        if (!isPackageName(name)) {
            throw new IllegalArgumentException("Open file has an invalid package name");
        }
        return name;
    }

    private static boolean isPackageName(String name) {
        String[] parts = name.split("\\.", -1);
        if (parts.length == 0) {
            return false;
        }
        for (String part : parts) {
            if (!isJavaIdentifier(part) || Language.JAVA.keywords().contains(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJavaIdentifier(String name) {
        if (name == null || name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
