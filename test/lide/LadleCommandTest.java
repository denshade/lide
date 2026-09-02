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
        testCommandForTestWithClassFilter();
        testSupportsTestFilters();
        testJarForTestFilterFallsBackWhenProjectJarIsOld();
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

    private static void testCommandForTestWithClassFilter() throws Exception {
        Path root = fakeLadleProject();
        try {
            Path projectJar = writeLadleClassJar(
                    root.resolve("lib").resolve("ladle.jar"),
                    "ladle test [<ini-file>] [<class>...]");
            Path testFile = root.resolve("test").resolve("FooTest.java");
            List<String> cmd = LadleCommand.commandFor(
                    root, LadleCommand.TEST, testFile.toString());
            assertEqual("uses project jar", projectJar.toString(), cmd.get(2));
            assertEqual("command", "test", cmd.get(3));
            assertEqual("ini", "build.ini", cmd.get(4));
            assertEqual("filter", testFile.toString(), cmd.get(5));
            assertEqual("size", 6, cmd.size());
            List<String> skippedBlank = LadleCommand.commandFor(root, LadleCommand.TEST, "  ", null);
            assertEqual("blank extras ignored", 5, skippedBlank.size());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testSupportsTestFilters() throws Exception {
        Path dir = Files.createTempDirectory("lide-filter-detect");
        try {
            Path neu = writeLadleClassJar(
                    dir.resolve("new.jar"),
                    "ladle test [<ini-file>] [<class>...]");
            Path old = writeLadleClassJar(
                    dir.resolve("old.jar"),
                    "ladle test [<ini-file>]        Run unit tests");
            assertTrue("new jar", LadleCommand.supportsTestFilters(neu));
            assertTrue("old jar", !LadleCommand.supportsTestFilters(old));
            assertTrue("missing", !LadleCommand.supportsTestFilters(dir.resolve("missing.jar")));
            Path project = fakeLadleProject();
            try {
                assertTrue("text fake jar", !LadleCommand.supportsTestFilters(LadleCommand.jarPath(project)));
            } finally {
                deleteRecursive(project);
            }
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testJarForTestFilterFallsBackWhenProjectJarIsOld() throws Exception {
        Path root = fakeLadleProject();
        Path fallbackDir = Files.createTempDirectory("lide-filter-fallback");
        try {
            writeLadleClassJar(
                    root.resolve("lib").resolve("ladle.jar"),
                    "ladle test [<ini-file>]        Run unit tests");
            assertTrue("old project has no filter",
                    LadleCommand.jarForTestFilter(root, List.of()) == null);
            Path fallback = writeLadleClassJar(
                    fallbackDir.resolve("ladle.jar"),
                    "ladle test [<ini-file>] [<class>...]");
            Path used = LadleCommand.jarForTestFilter(root, List.of(fallback));
            assertEqual("fallback jar", fallback.toAbsolutePath().normalize(), used);
            List<String> cmd = LadleCommand.commandFor(
                    root, LadleCommand.TEST, "FooTest");
            assertTrue("run-test uses a capable jar",
                    LadleCommand.supportsTestFilters(Path.of(cmd.get(2))));
            assertEqual("keeps class filter", "FooTest", cmd.get(5));
        } finally {
            deleteRecursive(root);
            deleteRecursive(fallbackDir);
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

    private static Path writeLadleClassJar(Path jar, String classUtf) throws Exception {
        Files.createDirectories(jar.getParent() == null ? Path.of(".") : jar.getParent());
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.zip.ZipEntry("thelaboflieven/info/Ladle.class"));
            out.write(classUtf.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar.toAbsolutePath().normalize();
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
