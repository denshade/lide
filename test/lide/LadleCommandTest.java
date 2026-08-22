package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for Ladle project detection and CLI command building.
 */
public final class LadleCommandTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testUnavailableWhenNull();
        testUnavailableWhenMissingJar();
        testUnavailableWhenMissingIni();
        testAvailableWhenJarAndIniPresent();
        testCommandForBuild();
        testCommandForTest();
        testCommandForRequiresProject();
        testCommandForRejectsBlank();
        testDetectedJdkHomeHasJavacWhenPresent();
        testProcessBuilderSetsJavaHome();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testUnavailableWhenNull() {
        assertTrue("null root", !LadleCommand.isAvailable(null));
        assertTrue("null jar", LadleCommand.jarPath(null) == null);
        assertTrue("null ini", LadleCommand.iniPath(null) == null);
    }

    private static void testUnavailableWhenMissingJar() throws Exception {
        Path root = Files.createTempDirectory("lide-ladle-no-jar");
        try {
            Files.writeString(root.resolve("build.ini"), "[javac]\n");
            assertTrue("no jar", !LadleCommand.isAvailable(root));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testUnavailableWhenMissingIni() throws Exception {
        Path root = Files.createTempDirectory("lide-ladle-no-ini");
        try {
            Files.createDirectories(root.resolve("lib"));
            Files.writeString(root.resolve("lib").resolve("ladle.jar"), "fake");
            assertTrue("no ini", !LadleCommand.isAvailable(root));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testAvailableWhenJarAndIniPresent() throws Exception {
        Path root = fakeLadleProject();
        try {
            assertTrue("available", LadleCommand.isAvailable(root));
            assertTrue("jar path", LadleCommand.jarPath(root).endsWith(Path.of("lib", "ladle.jar")));
            assertTrue("ini path", LadleCommand.iniPath(root).endsWith(Path.of("build.ini")));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandForBuild() throws Exception {
        Path root = fakeLadleProject();
        try {
            List<String> cmd = LadleCommand.commandFor(root, LadleCommand.BUILD);
            assertTrue("java", cmd.get(0).toLowerCase().contains("java"));
            assertEqual("-jar", "-jar", cmd.get(1));
            assertTrue("ladle.jar", cmd.get(2).replace('\\', '/').endsWith("lib/ladle.jar"));
            assertEqual("command", "build", cmd.get(3));
            assertEqual("ini", "build.ini", cmd.get(4));
            assertEqual("size", 5, cmd.size());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandForTest() throws Exception {
        Path root = fakeLadleProject();
        try {
            List<String> cmd = LadleCommand.commandFor(root, LadleCommand.TEST);
            assertEqual("command", "test", cmd.get(3));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandForRequiresProject() {
        try {
            LadleCommand.commandFor(null, LadleCommand.BUILD);
            failed++;
            System.err.println("FAIL expected IllegalStateException for null project");
        } catch (IllegalStateException ex) {
            passed++;
            assertTrue("message mentions ladle.jar", ex.getMessage().contains("ladle.jar"));
        }
    }

    private static void testCommandForRejectsBlank() throws Exception {
        Path root = fakeLadleProject();
        try {
            try {
                LadleCommand.commandFor(root, "  ");
                failed++;
                System.err.println("FAIL expected IllegalArgumentException for blank command");
            } catch (IllegalArgumentException ex) {
                passed++;
            }
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testDetectedJdkHomeHasJavacWhenPresent() {
        String home = LadleCommand.detectedJdkHome();
        if (home == null) {
            System.out.println("SKIP detected JDK (no javac next to java.home)");
            return;
        }
        String exe = ScriptCommand.isWindows() ? "javac.exe" : "javac";
        assertTrue("detected javac", Files.isRegularFile(Path.of(home).resolve("bin").resolve(exe)));
        assertTrue("not using $JAVA_HOME placeholder", !home.contains("$"));
    }

    private static void testProcessBuilderSetsJavaHome() throws Exception {
        Path root = fakeLadleProject();
        try {
            ProcessBuilder builder = LadleCommand.processBuilder(root, LadleCommand.BUILD);
            assertEqual("cwd", root.toAbsolutePath().normalize().toFile(), builder.directory());
            String detected = LadleCommand.detectedJdkHome();
            if (detected == null) {
                System.out.println("SKIP JAVA_HOME env (no JDK detected)");
                return;
            }
            String home = builder.environment().get("JAVA_HOME");
            assertTrue("JAVA_HOME set", home != null && !home.isBlank());
            assertTrue("JAVA_HOME is a JDK", LadleCommand.isJdkHome(home));
        } finally {
            deleteRecursive(root);
        }
    }

    private static Path fakeLadleProject() throws Exception {
        Path root = Files.createTempDirectory("lide-ladle");
        Files.createDirectories(root.resolve("lib"));
        Files.writeString(root.resolve("lib").resolve("ladle.jar"), "fake-jar");
        Files.writeString(root.resolve("build.ini"), "[javac]\npath = $JAVA_HOME\n");
        return root;
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

    private LadleCommandTest() {
    }
}
