package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for Ladle project detection and CLI command building.
 */
public final class LadleCommandTest {

    @Test
    void testUnavailableWhenNull() throws Exception {
        assertTrue(!LadleCommand.isAvailable(null), "null root");
        assertTrue(LadleCommand.jarPath(null) == null, "null jar");
        assertTrue(LadleCommand.iniPath(null) == null, "null ini");
    }

    @Test
    void testUnavailableWhenMissingJar() throws Exception {
        Path root = Files.createTempDirectory("lide-ladle-no-jar");
        try {
            Files.writeString(root.resolve("build.ini"), "[javac]\n");
            assertTrue(!LadleCommand.isAvailable(root), "no jar");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testUnavailableWhenMissingIni() throws Exception {
        Path root = Files.createTempDirectory("lide-ladle-no-ini");
        try {
            Files.createDirectories(root.resolve("lib"));
            Files.writeString(root.resolve("lib").resolve("ladle.jar"), "fake");
            assertTrue(!LadleCommand.isAvailable(root), "no ini");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testAvailableWhenJarAndIniPresent() throws Exception {
        Path root = fakeLadleProject();
        try {
            assertTrue(LadleCommand.isAvailable(root), "available");
            assertTrue(LadleCommand.jarPath(root).endsWith(Path.of("lib", "ladle.jar")), "jar path");
            assertTrue(LadleCommand.iniPath(root).endsWith(Path.of("build.ini")), "ini path");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandForBuild() throws Exception {
        Path root = fakeLadleProject();
        try {
            List<String> cmd = LadleCommand.commandFor(root, LadleCommand.BUILD);
            assertTrue(cmd.get(0).toLowerCase().contains("java"), "java");
            assertEquals("-jar", cmd.get(1), "-jar");
            assertTrue(cmd.get(2).replace('\\', '/').endsWith("lib/ladle.jar"), "ladle.jar");
            assertEquals("build", cmd.get(3), "command");
            assertEquals("build.ini", cmd.get(4), "ini");
            assertEquals(5, cmd.size(), "size");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandForTest() throws Exception {
        Path root = fakeLadleProject();
        try {
            List<String> cmd = LadleCommand.commandFor(root, LadleCommand.TEST);
            assertEquals("test", cmd.get(3), "command");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandForTestWithClassFilter() throws Exception {
        Path root = fakeLadleProject();
        try {
            Path projectJar = writeLadleClassJar(
                    root.resolve("lib").resolve("ladle.jar"),
                    "ladle test [<ini-file>] [<class>...]");
            Path testFile = root.resolve("test").resolve("FooTest.java");
            List<String> cmd = LadleCommand.commandFor(
                    root, LadleCommand.TEST, testFile.toString());
            assertEquals(projectJar.toString(), cmd.get(2), "uses project jar");
            assertEquals("test", cmd.get(3), "command");
            assertEquals("build.ini", cmd.get(4), "ini");
            assertEquals(testFile.toString(), cmd.get(5), "filter");
            assertEquals(6, cmd.size(), "size");
            List<String> skippedBlank = LadleCommand.commandFor(root, LadleCommand.TEST, "  ", null);
            assertEquals(5, skippedBlank.size(), "blank extras ignored");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testSupportsTestFilters() throws Exception {
        Path dir = Files.createTempDirectory("lide-filter-detect");
        try {
            Path neu = writeLadleClassJar(
                    dir.resolve("new.jar"),
                    "ladle test [<ini-file>] [<class>...]");
            Path old = writeLadleClassJar(
                    dir.resolve("old.jar"),
                    "ladle test [<ini-file>]        Run unit tests");
            assertTrue(LadleCommand.supportsTestFilters(neu), "new jar");
            assertTrue(!LadleCommand.supportsTestFilters(old), "old jar");
            assertTrue(!LadleCommand.supportsTestFilters(dir.resolve("missing.jar")), "missing");
            Path project = fakeLadleProject();
            try {
                assertTrue(!LadleCommand.supportsTestFilters(LadleCommand.jarPath(project)), "text fake jar");
            } finally {
                deleteRecursive(project);
            }
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testJarForTestFilterFallsBackWhenProjectJarIsOld() throws Exception {
        Path root = fakeLadleProject();
        Path fallbackDir = Files.createTempDirectory("lide-filter-fallback");
        try {
            writeLadleClassJar(
                    root.resolve("lib").resolve("ladle.jar"),
                    "ladle test [<ini-file>]        Run unit tests");
            assertTrue(LadleCommand.jarForTestFilter(root, List.of()) == null, "old project has no filter");
            Path fallback = writeLadleClassJar(
                    fallbackDir.resolve("ladle.jar"),
                    "ladle test [<ini-file>] [<class>...]");
            Path used = LadleCommand.jarForTestFilter(root, List.of(fallback));
            assertEquals(fallback.toAbsolutePath().normalize(), used, "fallback jar");
            List<String> cmd = LadleCommand.commandFor(
                    root, LadleCommand.TEST, "FooTest");
            assertTrue(LadleCommand.supportsTestFilters(Path.of(cmd.get(2))), "run-test uses a capable jar");
            assertEquals("FooTest", cmd.get(5), "keeps class filter");
        } finally {
            deleteRecursive(root);
            deleteRecursive(fallbackDir);
        }
    }

    @Test
    void testCommandForRequiresProject() throws Exception {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> LadleCommand.commandFor(null, LadleCommand.BUILD));
        assertTrue(ex.getMessage().contains("ladle.jar"), "message mentions ladle.jar");
    }

    @Test
    void testCommandForRejectsBlank() throws Exception {
        Path root = fakeLadleProject();
        try {
            assertThrows(IllegalArgumentException.class, () -> LadleCommand.commandFor(root, "  "));
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testDetectedJdkHomeHasJavacWhenPresent() throws Exception {
        String home = LadleCommand.detectedJdkHome();
        Assumptions.assumeTrue(home != null, "SKIP detected JDK (no javac next to java.home)");
        String exe = ScriptCommand.isWindows() ? "javac.exe" : "javac";
        assertTrue(Files.isRegularFile(Path.of(home).resolve("bin").resolve(exe)), "detected javac");
        assertTrue(!home.contains("$"), "not using $JAVA_HOME placeholder");
    }

    @Test
    void testProcessBuilderSetsJavaHome() throws Exception {
        Path root = fakeLadleProject();
        try {
            ProcessBuilder builder = LadleCommand.processBuilder(root, LadleCommand.BUILD);
            assertEquals(root.toAbsolutePath().normalize().toFile(), builder.directory(), "cwd");
            String detected = LadleCommand.detectedJdkHome();
            Assumptions.assumeTrue(detected != null, "SKIP JAVA_HOME env (no JDK detected)");
            String home = builder.environment().get("JAVA_HOME");
            assertTrue(home != null && !home.isBlank(), "JAVA_HOME set");
            assertTrue(LadleCommand.isJdkHome(home), "JAVA_HOME is a JDK");
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

}
