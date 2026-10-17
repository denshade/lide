package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for Gradle project detection and CLI command building.
 */
public final class GradleCommandTest {

    @Test
    void testUnavailableWhenNull() throws Exception {
        assertTrue(!GradleCommand.isAvailable(null), "null root");
        assertTrue(GradleCommand.projectFile(null) == null, "null project file");
        assertTrue(GradleCommand.wrapperPath(null) == null, "null wrapper");
    }

    @Test
    void testUnavailableWhenMissingBuildFile() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-empty");
        try {
            Files.writeString(root.resolve("readme.txt"), "no gradle");
            assertTrue(!GradleCommand.isAvailable(root), "no build file");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testAvailableWhenBuildGradlePresent() throws Exception {
        Path root = fakeGradleProject();
        try {
            assertTrue(GradleCommand.isAvailable(root), "available");
            assertTrue(GradleCommand.projectFile(root)
                    .endsWith(Path.of("build.gradle")), "project file");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testAvailableWhenKotlinDslPresent() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-kts");
        try {
            Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
            assertTrue(GradleCommand.isAvailable(root), "kts available");
            assertTrue(GradleCommand.projectFile(root)
                    .getFileName().toString().equals("build.gradle.kts"), "kts file");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testAvailableWhenSettingsGradlePresent() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-settings");
        try {
            Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
            assertTrue(GradleCommand.isAvailable(root), "settings available");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testPreferredOverLadle() throws Exception {
        Path gradleOnly = fakeGradleProject();
        Path ladleOnly = Files.createTempDirectory("lide-ladle-not-gradle");
        Path both = fakeGradleProject();
        try {
            assertTrue(GradleCommand.preferredOverLadle(gradleOnly), "gradle only");
            Files.createDirectories(ladleOnly.resolve("lib"));
            Files.writeString(ladleOnly.resolve("lib").resolve("ladle.jar"), "fake");
            Files.writeString(ladleOnly.resolve("build.ini"), "[javac]\n");
            assertTrue(!GradleCommand.preferredOverLadle(ladleOnly), "ladle only");
            Files.createDirectories(both.resolve("lib"));
            Files.writeString(both.resolve("lib").resolve("ladle.jar"), "fake");
            Files.writeString(both.resolve("build.ini"), "[javac]\n");
            assertTrue(!GradleCommand.preferredOverLadle(both), "both prefers ladle");
            assertTrue(!GradleCommand.preferredOverLadle(null), "null");
        } finally {
            deleteRecursive(gradleOnly);
            deleteRecursive(ladleOnly);
            deleteRecursive(both);
        }
    }

    @Test
    void testWrapperPathPrefersPlatformScript() throws Exception {
        Path root = fakeGradleProject();
        try {
            Path wrapper = GradleCommand.wrapperPath(root);
            if (ScriptCommand.isWindows()) {
                assertTrue(wrapper != null
                        && wrapper.getFileName().toString().equals("gradlew.bat"), "windows wrapper");
            } else {
                assertTrue(wrapper != null
                        && wrapper.getFileName().toString().equals("gradlew"), "unix wrapper");
            }
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandForBuildUsesWrapper() throws Exception {
        Path root = fakeGradleProject();
        try {
            List<String> cmd = GradleCommand.commandFor(root, GradleCommand.BUILD);
            assertEquals(GradleCommand.BUILD, taskOf(cmd), "task");
            assertTrue(cmd.contains(GradleCommand.CONSOLE_PLAIN), "plain console");
            if (ScriptCommand.isWindows()) {
                assertEquals("cmd.exe", cmd.get(0), "cmd");
                assertEquals("/c", cmd.get(1), "/c");
                assertEquals("gradlew.bat", cmd.get(2), "wrapper");
            } else {
                assertEquals("sh", cmd.get(0), "sh");
                assertTrue(cmd.get(1).replace('\\', '/').endsWith("/gradlew"), "gradlew path");
            }
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandForTestWithClassFilter() throws Exception {
        Path root = fakeGradleProject();
        try {
            List<String> cmd = GradleCommand.commandFor(
                    root, GradleCommand.TEST, GradleCommand.TESTS_FLAG, "FooTest");
            assertEquals(GradleCommand.TEST, taskOf(cmd), "task");
            int tests = cmd.indexOf(GradleCommand.TESTS_FLAG);
            assertTrue(tests >= 0, "--tests present");
            assertEquals("FooTest", cmd.get(tests + 1), "filter");
            List<String> skippedBlank = GradleCommand.commandFor(
                    root, GradleCommand.TEST, "  ", null);
            assertEquals(GradleCommand.TEST, taskOf(skippedBlank), "blank extras ignored");
            assertTrue(!skippedBlank.contains("  "), "no blank arg");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandForDependenciesRefreshes() throws Exception {
        Path root = fakeGradleProject();
        try {
            List<String> cmd = GradleCommand.commandFor(root, GradleCommand.DEPENDENCY);
            assertEquals(GradleCommand.DEPENDENCY, taskOf(cmd), "task");
            assertTrue(cmd.contains(GradleCommand.REFRESH_DEPENDENCIES), "refresh");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandWithoutWrapperUsesGradle() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-nowrapper");
        try {
            Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
            List<String> cmd = GradleCommand.commandFor(root, GradleCommand.CLEAR);
            assertEquals(GradleCommand.CLEAR, taskOf(cmd), "task");
            if (ScriptCommand.isWindows()) {
                assertEquals("cmd.exe", cmd.get(0), "cmd");
                assertEquals("gradle", cmd.get(2), "gradle");
            } else {
                assertEquals("gradle", cmd.get(0), "gradle");
            }
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCommandForRequiresProject() throws Exception {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> GradleCommand.commandFor(null, GradleCommand.BUILD));
        assertTrue(ex.getMessage().contains("build.gradle"), "message mentions build.gradle");
    }

    @Test
    void testCommandForRejectsBlank() throws Exception {
        Path root = fakeGradleProject();
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> GradleCommand.commandFor(root, "  "));
        assertTrue(ex.getMessage().contains("task"), "message mentions task");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testTaskForLadleCommands() throws Exception {
        assertEquals(GradleCommand.BUILD, GradleCommand.taskFor(LadleCommand.BUILD), "build");
        assertEquals(GradleCommand.TEST, GradleCommand.taskFor(LadleCommand.TEST), "test");
        assertEquals(GradleCommand.RELEASE, GradleCommand.taskFor(LadleCommand.RELEASE), "release");
        assertEquals(GradleCommand.DEPENDENCY, GradleCommand.taskFor(LadleCommand.DEPENDENCY), "deps");
        assertEquals(GradleCommand.CLEAR, GradleCommand.taskFor(LadleCommand.CLEAR), "clear");
        assertEquals("classes", GradleCommand.taskFor("classes"), "passthrough");
        assertThrows(IllegalArgumentException.class, () -> GradleCommand.taskFor(" "));
    }

    @Test
    void testTestClassFilter() throws Exception {
        assertEquals("FooTest", GradleCommand.testClassFilter(Path.of("src", "test", "java", "FooTest.java")), "java file");
        assertEquals("FooTest", GradleCommand.testClassFilter(Path.of("FooTest")), "plain name");
        assertTrue(GradleCommand.testClassFilter(null) == null, "null");
    }

    @Test
    void testGradleVersionFromWrapper() throws Exception {
        Path root = fakeGradleProject();
        try {
            assertTrue(GradleCommand.gradleVersion(root) == null, "no wrapper props");
            writeWrapper(root, "8.10.2");
            assertEquals("8.10.2", GradleCommand.gradleVersion(root), "version");
            writeWrapper(root, "9.4.0");
            assertEquals("9.4.0", GradleCommand.gradleVersion(root), "9.4");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testJavaRangeForGradleVersions() throws Exception {
        GradleCommand.JavaRange unknown = GradleCommand.javaRangeForGradle(null);
        assertEquals(8, unknown.min(), "unknown min");
        assertEquals(21, unknown.max(), "unknown max");
        assertEquals(21, GradleCommand.javaRangeForGradle("8.5").max(), "8.5 max");
        assertEquals(23, GradleCommand.javaRangeForGradle("8.10.2").max(), "8.10 max");
        assertEquals(24, GradleCommand.javaRangeForGradle("8.14.3").max(), "8.14 max");
        assertEquals(17, GradleCommand.javaRangeForGradle("9.0.0").min(), "9.0 min");
        assertEquals(25, GradleCommand.javaRangeForGradle("9.1.0").max(), "9.1 max");
        assertEquals(26, GradleCommand.javaRangeForGradle("9.4.0").max(), "9.4 max");
        assertTrue(GradleCommand.javaRangeForGradle("8.5").contains(21), "8.5 allows 21");
        assertTrue(!GradleCommand.javaRangeForGradle("8.5").contains(26), "8.5 rejects 26");
    }

    @Test
    void testFeatureVersionFromReleaseFile() throws Exception {
        Path jdk = Files.createTempDirectory("lide-fake-jdk");
        try {
            writeFakeJdk(jdk, 21);
            assertEquals(21, GradleCommand.featureVersion(jdk), "feature");
            assertTrue(GradleCommand.featureVersion(jdk.resolve("missing")) < 0, "missing");
        } finally {
            deleteRecursive(jdk);
        }
    }

    @Test
    void testJdkForGradlePicksNewestSupported() throws Exception {
        Path root = fakeGradleProject();
        Path jdk17 = Files.createTempDirectory("lide-jdk-17");
        Path jdk21 = Files.createTempDirectory("lide-jdk-21");
        Path jdk26 = Files.createTempDirectory("lide-jdk-26");
        try {
            writeFakeJdk(jdk17, 17);
            writeFakeJdk(jdk21, 21);
            writeFakeJdk(jdk26, 26);
            writeWrapper(root, "8.10.2");
            Path picked = GradleCommand.jdkForGradle(root, List.of(jdk17, jdk21, jdk26));
            assertEquals(jdk21.toAbsolutePath().normalize(), picked, "8.10 picks 21");
            writeWrapper(root, "9.4.0");
            Path newest = GradleCommand.jdkForGradle(root, List.of(jdk17, jdk21, jdk26));
            assertEquals(jdk26.toAbsolutePath().normalize(), newest, "9.4 picks 26");
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk17);
            deleteRecursive(jdk21);
            deleteRecursive(jdk26);
        }
    }

    @Test
    void testApplyJavaHomeKeepsCompatible() throws Exception {
        Path root = fakeGradleProject();
        Path jdk21 = Files.createTempDirectory("lide-keep-21");
        Path jdk17 = Files.createTempDirectory("lide-keep-17");
        try {
            writeFakeJdk(jdk21, 21);
            writeFakeJdk(jdk17, 17);
            writeWrapper(root, "8.5");
            ProcessBuilder builder = new ProcessBuilder("gradle");
            builder.environment().put("JAVA_HOME", jdk21.toString());
            GradleCommand.applyGradleJavaHome(builder, root, List.of(jdk17, jdk21));
            assertEquals(Path.of(builder.environment().get("JAVA_HOME")).toAbsolutePath().normalize(), jdk21.toAbsolutePath().normalize(), "kept 21");
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk21);
            deleteRecursive(jdk17);
        }
    }

    @Test
    void testApplyJavaHomeReplacesJava26ForOldGradle() throws Exception {
        Path root = fakeGradleProject();
        Path jdk21 = Files.createTempDirectory("lide-replace-21");
        Path jdk26 = Files.createTempDirectory("lide-replace-26");
        try {
            writeFakeJdk(jdk21, 21);
            writeFakeJdk(jdk26, 26);
            writeWrapper(root, "8.10.2");
            ProcessBuilder builder = new ProcessBuilder("gradle");
            builder.environment().put("JAVA_HOME", jdk26.toString());
            GradleCommand.applyGradleJavaHome(builder, root, List.of(jdk21, jdk26));
            assertEquals(Path.of(builder.environment().get("JAVA_HOME")).toAbsolutePath().normalize(), jdk21.toAbsolutePath().normalize(), "replaced with 21");
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk21);
            deleteRecursive(jdk26);
        }
    }

    @Test
    void testApplyJavaHomeThrowsWhenNoCompatibleJdk() throws Exception {
        Path root = fakeGradleProject();
        Path jdk26 = Files.createTempDirectory("lide-only-26");
        try {
            writeFakeJdk(jdk26, 26);
            writeWrapper(root, "8.5");
            ProcessBuilder builder = new ProcessBuilder("gradle");
            builder.environment().put("JAVA_HOME", jdk26.toString());
            try {
                GradleCommand.applyGradleJavaHome(builder, root, List.of(jdk26));
                if (Runtime.version().feature() <= 21) {
                    Assumptions.assumeTrue(false, "running JVM is compatible with Gradle 8.5");
                }
                fail("expected IllegalStateException for Java 26 vs Gradle 8.5");
            } catch (IllegalStateException ex) {
                assertTrue(ex.getMessage().contains("cannot run on Java"), "mentions Java 26 or running version");
                assertTrue(ex.getMessage().contains("major version"), "mentions major version");
                assertTrue(ex.getMessage().contains("JDK 21"), "suggests JDK 21");
            }
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk26);
        }
    }

    @Test
    void testIncompatibleMessageMentionsMajorVersion() throws Exception {
        String message = GradleCommand.incompatibleJdkMessage(
                "8.10.2", GradleCommand.javaRangeForGradle("8.10.2"), 26);
        assertTrue(message.contains("8.10.2"), "gradle version");
        assertTrue(message.contains("Java 26"), "java 26");
        assertTrue(message.contains("70"), "major 70");
        assertTrue(message.contains("9.4"), "upgrade wrapper");
    }

    @Test
    void testProcessBuilderSetsJavaHome() throws Exception {
        Path root = fakeGradleProject();
        try {
            writeWrapper(root, "9.4.0");
            ProcessBuilder builder = GradleCommand.processBuilder(root, GradleCommand.BUILD);
            assertEquals(root.toAbsolutePath().normalize().toFile(), builder.directory(), "cwd");
            String detected = LadleCommand.detectedJdkHome();
            Assumptions.assumeTrue(detected != null || builder.environment().get("JAVA_HOME") != null, "SKIP JAVA_HOME env (no JDK detected)");
            String home = builder.environment().get("JAVA_HOME");
            assertTrue(home != null && !home.isBlank(), "JAVA_HOME set");
            assertTrue(LadleCommand.isJdkHome(home), "JAVA_HOME is a JDK");
        } finally {
            deleteRecursive(root);
        }
    }

    private static String taskOf(List<String> cmd) {
        int console = cmd.indexOf(GradleCommand.CONSOLE_PLAIN);
        if (console < 0 || console + 1 >= cmd.size()) {
            return null;
        }
        return cmd.get(console + 1);
    }

    private static Path fakeGradleProject() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle");
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(root.resolve("gradlew.bat"), "@echo off\n");
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\n");
        return root;
    }

    private static void writeWrapper(Path root, String gradleVersion) throws Exception {
        Path dir = root.resolve("gradle").resolve("wrapper");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("gradle-wrapper.properties"),
                "distributionUrl=https\\://services.gradle.org/distributions/gradle-"
                        + gradleVersion
                        + "-bin.zip\n");
    }

    private static void writeFakeJdk(Path home, int feature) throws Exception {
        Files.createDirectories(home.resolve("bin"));
        String javac = ScriptCommand.isWindows() ? "javac.exe" : "javac";
        Files.writeString(home.resolve("bin").resolve(javac), "");
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + feature + ".0.0\"\n");
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
