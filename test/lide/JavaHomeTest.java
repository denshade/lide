package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for persisted JAVA_HOME used by Ladle and Gradle.
 */
public final class JavaHomeTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        Path previousStorage = JavaHome.defaultStorageFile();
        try {
            testResolveJdkHome();
            testSetAndClearPersists();
            testSetRejectsNonJdk();
            testSetAcceptsBinFolder();
            testApplySetsEnvironment();
            testDetectedJdkHomePrefersConfigured();
        } finally {
            JavaHome.useStorageFile(previousStorage);
        }
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testResolveJdkHome() throws Exception {
        Path jdk = Files.createTempDirectory("lide-javahome-resolve");
        try {
            writeFakeJdk(jdk, 21);
            assertEqual("jdk root", jdk.toAbsolutePath().normalize(), JavaHome.resolveJdkHome(jdk));
            assertEqual("bin folder",
                    jdk.toAbsolutePath().normalize(),
                    JavaHome.resolveJdkHome(jdk.resolve("bin")));
            assertTrue("null", JavaHome.resolveJdkHome(null) == null);
            Path other = Files.createTempDirectory("lide-javahome-notjdk");
            try {
                assertTrue("plain folder", JavaHome.resolveJdkHome(other) == null);
            } finally {
                deleteRecursive(other);
            }
        } finally {
            deleteRecursive(jdk);
        }
    }

    private static void testSetAndClearPersists() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-persist");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 21);
            JavaHome.useStorageFile(storage);
            assertTrue("unset", JavaHome.configured() == null);
            JavaHome.set(jdk);
            assertEqual("set", jdk.toAbsolutePath().normalize(), JavaHome.configured());
            JavaHome.useStorageFile(storage);
            assertEqual("reloaded", jdk.toAbsolutePath().normalize(), JavaHome.configured());
            JavaHome.clear();
            assertTrue("cleared", JavaHome.configured() == null);
            assertTrue("storage removed", !Files.isRegularFile(storage));
        } finally {
            deleteRecursive(jdk);
            Files.deleteIfExists(storage);
        }
    }

    private static void testSetRejectsNonJdk() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path other = Files.createTempDirectory("lide-javahome-reject");
        try {
            Files.deleteIfExists(storage);
            JavaHome.useStorageFile(storage);
            try {
                JavaHome.set(other);
                failed++;
                System.err.println("FAIL expected exception for non-JDK folder");
            } catch (IllegalArgumentException ex) {
                assertTrue("mentions javac", ex.getMessage().contains("javac"));
                assertTrue("still unset", JavaHome.configured() == null);
            }
        } finally {
            deleteRecursive(other);
            Files.deleteIfExists(storage);
        }
    }

    private static void testSetAcceptsBinFolder() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-bin");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 17);
            JavaHome.useStorageFile(storage);
            JavaHome.set(jdk.resolve("bin"));
            assertEqual("normalized to root",
                    jdk.toAbsolutePath().normalize(),
                    JavaHome.configured());
        } finally {
            JavaHome.clear();
            deleteRecursive(jdk);
            Files.deleteIfExists(storage);
        }
    }

    private static void testApplySetsEnvironment() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-apply");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 21);
            JavaHome.useStorageFile(storage);
            ProcessBuilder before = new ProcessBuilder("java");
            assertTrue("no configured", !JavaHome.apply(before));
            JavaHome.set(jdk);
            ProcessBuilder builder = new ProcessBuilder("java");
            builder.environment().put("JAVA_HOME", "/old/jdk");
            assertTrue("applied", JavaHome.apply(builder));
            assertEqual("JAVA_HOME",
                    jdk.toAbsolutePath().normalize().toString(),
                    Path.of(builder.environment().get("JAVA_HOME")).toAbsolutePath().normalize().toString());
            String path = pathValue(builder);
            assertTrue("bin on PATH", path != null && path.contains(jdk.resolve("bin").toString()));
        } finally {
            JavaHome.clear();
            deleteRecursive(jdk);
            Files.deleteIfExists(storage);
        }
    }

    private static void testDetectedJdkHomePrefersConfigured() throws Exception {
        Path storage = Files.createTempFile("lide-javahome-", ".txt");
        Path jdk = Files.createTempDirectory("lide-javahome-detect");
        try {
            Files.deleteIfExists(storage);
            writeFakeJdk(jdk, 21);
            JavaHome.useStorageFile(storage);
            JavaHome.set(jdk);
            assertEqual("detected",
                    jdk.toAbsolutePath().normalize().toString(),
                    Path.of(LadleCommand.detectedJdkHome()).toAbsolutePath().normalize().toString());
            ProcessBuilder ladle = new ProcessBuilder("java");
            ladle.environment().put("JAVA_HOME", "/ignored");
            LadleCommand.applyJavaHome(ladle);
            assertEqual("ladle JAVA_HOME",
                    jdk.toAbsolutePath().normalize().toString(),
                    Path.of(ladle.environment().get("JAVA_HOME")).toAbsolutePath().normalize().toString());
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

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label + ": expected " + expected + " but was " + actual);
    }

    private static void assertTrue(String label, boolean condition) {
        if (condition) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label);
    }

    private JavaHomeTest() {
    }
}
