package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for persisted JAVA_HOME used by Ladle and Gradle.
 */
public final class JavaHomeTest {

    @AfterEach
    void restoreJavaHomeStorage() {
        JavaHome.useStorageFile(JavaHome.defaultStorageFile());
    }

    @Test
    void testResolveJdkHome() throws Exception {
        Path jdk = Files.createTempDirectory("lide-javahome-resolve");
        try {
            writeFakeJdk(jdk, 21);
            assertEquals(jdk.toAbsolutePath().normalize(), JavaHome.resolveJdkHome(jdk), "jdk root");
            assertEquals(jdk.toAbsolutePath().normalize(), JavaHome.resolveJdkHome(jdk.resolve("bin")), "bin folder");
            assertTrue(JavaHome.resolveJdkHome(null) == null, "null");
            Path other = Files.createTempDirectory("lide-javahome-notjdk");
            try {
                assertTrue(JavaHome.resolveJdkHome(other) == null, "plain folder");
            } finally {
                deleteRecursive(other);
            }
        } finally {
            deleteRecursive(jdk);
        }
    }

    @Test
    void testSetAndClearPersists() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-persist");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 21);
            JavaHome.useStorageFile(storage);
            assertTrue(JavaHome.configured() == null, "unset");
            JavaHome.set(jdk);
            assertEquals(jdk.toAbsolutePath().normalize(), JavaHome.configured(), "set");
            JavaHome.useStorageFile(storage);
            assertEquals(jdk.toAbsolutePath().normalize(), JavaHome.configured(), "reloaded");
            JavaHome.clear();
            assertTrue(JavaHome.configured() == null, "cleared");
            assertTrue(!Files.isRegularFile(storage), "storage removed");
        } finally {
            deleteRecursive(jdk);
            Files.deleteIfExists(storage);
        }
    }

    @Test
    void testSetRejectsNonJdk() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path other = Files.createTempDirectory("lide-javahome-reject");
        try {
            Files.deleteIfExists(storage);
            JavaHome.useStorageFile(storage);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> JavaHome.set(other));
        assertTrue(ex.getMessage().contains("javac"), "mentions javac");
                assertTrue(JavaHome.configured() == null, "still unset");
        } finally {
            deleteRecursive(other);
            Files.deleteIfExists(storage);
        }
    }

    @Test
    void testSetAcceptsBinFolder() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-bin");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 17);
            JavaHome.useStorageFile(storage);
            JavaHome.set(jdk.resolve("bin"));
            assertEquals(jdk.toAbsolutePath().normalize(), JavaHome.configured(), "normalized to root");
        } finally {
            JavaHome.clear();
            deleteRecursive(jdk);
            Files.deleteIfExists(storage);
        }
    }

    @Test
    void testApplySetsEnvironment() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-apply");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 21);
            JavaHome.useStorageFile(storage);
            ProcessBuilder before = new ProcessBuilder("java");
            assertTrue(!JavaHome.apply(before), "no configured");
            JavaHome.set(jdk);
            ProcessBuilder builder = new ProcessBuilder("java");
            builder.environment().put("JAVA_HOME", "/old/jdk");
            assertTrue(JavaHome.apply(builder), "applied");
            assertEquals(jdk.toAbsolutePath().normalize().toString(), Path.of(builder.environment().get("JAVA_HOME")).toAbsolutePath().normalize().toString(), "JAVA_HOME");
            String path = pathValue(builder);
            assertTrue(path != null && path.contains(jdk.resolve("bin").toString()), "bin on PATH");
        } finally {
            JavaHome.clear();
            deleteRecursive(jdk);
            Files.deleteIfExists(storage);
        }
    }

    @Test
    void testDetectedJdkHomePrefersConfigured() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-detect");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 21);
            JavaHome.useStorageFile(storage);
            JavaHome.set(jdk);
            assertEquals(jdk.toAbsolutePath().normalize().toString(), Path.of(LadleCommand.detectedJdkHome()).toAbsolutePath().normalize().toString(), "detected");
            ProcessBuilder ladle = new ProcessBuilder("java");
            ladle.environment().put("JAVA_HOME", "/ignored");
            LadleCommand.applyJavaHome(ladle);
            assertEquals(jdk.toAbsolutePath().normalize().toString(), Path.of(ladle.environment().get("JAVA_HOME")).toAbsolutePath().normalize().toString(), "ladle JAVA_HOME");
        } finally {
            JavaHome.clear();
            deleteRecursive(jdk);
            Files.deleteIfExists(storage);
        }
    }

    private static void writeFakeJdk(Path home, int feature) throws Exception {
        Files.createDirectories(home.resolve("bin"));
        String javac = ScriptCommand.isWindows() ? "javac.exe" : "javac";
        Files.writeString(home.resolve("bin").resolve(javac), "");
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + feature + ".0.0\"\n");
    }

    private static String pathValue(ProcessBuilder builder) {
        for (String key : builder.environment().keySet()) {
            if ("PATH".equalsIgnoreCase(key)) {
                return builder.environment().get(key);
            }
        }
        return null;
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

}
