package lide;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persisted {@code JAVA_HOME} used for Ladle and Gradle child processes.
 */
public final class JavaHome {
    static final String NOT_A_JDK =
            "That folder is not a JDK.\nIt must contain bin/javac.";

    private static final Object LOCK = new Object();
    private static Path storageFile = defaultStorageFile();
    private static Path configured;

    static {
        reload();
    }

    private JavaHome() {
    }

    public static Path defaultStorageFile() {
        return Path.of(System.getProperty("user.home"), ".lide", "java-home.txt");
    }

    public static Path configured() {
        synchronized (LOCK) {
            return configured;
        }
    }

    /**
     * JDK root for {@code path}, or its parent when {@code path} is {@code bin}.
     */
    public static Path resolveJdkHome(Path path) {
        if (path == null) {
            return null;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (LadleCommand.isJdkHome(normalized.toString())) {
            return normalized;
        }
        Path parent = normalized.getParent();
        if (parent != null && LadleCommand.isJdkHome(parent.toString())) {
            return parent.toAbsolutePath().normalize();
        }
        return null;
    }

    public static void set(Path home) {
        Path jdk = resolveJdkHome(home);
        if (jdk == null) {
            throw new IllegalArgumentException(NOT_A_JDK);
        }
        synchronized (LOCK) {
            configured = jdk;
            saveLocked();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            if (configured == null) {
                return;
            }
            configured = null;
            saveLocked();
        }
    }

    /**
     * Sets {@code JAVA_HOME} (and prepends {@code bin} to {@code PATH}) when the
     * user has chosen a JDK through the Java menu.
     *
     * @return true when a configured JDK was applied
     */
    static boolean apply(ProcessBuilder builder) {
        Path home = configured();
        if (builder == null || home == null || !LadleCommand.isJdkHome(home.toString())) {
            return false;
        }
        String value = home.toAbsolutePath().normalize().toString();
        builder.environment().put("JAVA_HOME", value);
        prependPath(builder, home.resolve("bin"));
        return true;
    }

    static void useStorageFile(Path file) {
        synchronized (LOCK) {
            storageFile = file != null ? file : defaultStorageFile();
            reloadLocked();
        }
    }

    private static void reload() {
        synchronized (LOCK) {
            reloadLocked();
        }
    }

    private static void reloadLocked() {
        configured = null;
        if (!Files.isRegularFile(storageFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                try {
                    configured = Path.of(trimmed).toAbsolutePath().normalize();
                } catch (java.nio.file.InvalidPathException ex) {
                    AppLog.exception("Invalid JAVA_HOME path in " + storageFile + ": " + trimmed, ex);
                    configured = null;
                }
                return;
            }
        } catch (IOException ex) {
            AppLog.exception("Could not load JAVA_HOME from " + storageFile, ex);
            configured = null;
        }
    }

    private static void saveLocked() {
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (configured == null) {
                Files.deleteIfExists(storageFile);
                return;
            }
            Files.writeString(
                    storageFile,
                    configured.toString() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            AppLog.exception("Could not save JAVA_HOME to " + storageFile, ex);
        }
    }

    private static void prependPath(ProcessBuilder builder, Path bin) {
        if (bin == null) {
            return;
        }
        String binDir = bin.toAbsolutePath().normalize().toString();
        String key = pathKey(builder);
        String current = builder.environment().get(key);
        if (current == null || current.isBlank()) {
            builder.environment().put(key, binDir);
            return;
        }
        if (current.equals(binDir) || current.startsWith(binDir + File.pathSeparator)) {
            return;
        }
        builder.environment().put(key, binDir + File.pathSeparator + current);
    }

    private static String pathKey(ProcessBuilder builder) {
        for (String key : builder.environment().keySet()) {
            if ("PATH".equalsIgnoreCase(key)) {
                return key;
            }
        }
        return ScriptCommand.isWindows() ? "Path" : "PATH";
    }
}
