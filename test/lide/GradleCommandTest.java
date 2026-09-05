package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for Gradle project detection and CLI command building.
 */
public final class GradleCommandTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testUnavailableWhenNull();
        testUnavailableWhenMissingBuildFile();
        testAvailableWhenBuildGradlePresent();
        testAvailableWhenKotlinDslPresent();
        testAvailableWhenSettingsGradlePresent();
        testPreferredOverLadle();
        testWrapperPathPrefersPlatformScript();
        testCommandForBuildUsesWrapper();
        testCommandForTestWithClassFilter();
        testCommandForDependenciesRefreshes();
        testCommandWithoutWrapperUsesGradle();
        testCommandForRequiresProject();
        testCommandForRejectsBlank();
        testTaskForLadleCommands();
        testTestClassFilter();
        testGradleVersionFromWrapper();
        testJavaRangeForGradleVersions();
        testFeatureVersionFromReleaseFile();
        testJdkForGradlePicksNewestSupported();
        testApplyJavaHomeKeepsCompatible();
        testApplyJavaHomeReplacesJava26ForOldGradle();
        testApplyJavaHomeThrowsWhenNoCompatibleJdk();
        testIncompatibleMessageMentionsMajorVersion();
        testProcessBuilderSetsJavaHome();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testUnavailableWhenNull() {
        assertTrue("null root", !GradleCommand.isAvailable(null));
        assertTrue("null project file", GradleCommand.projectFile(null) == null);
        assertTrue("null wrapper", GradleCommand.wrapperPath(null) == null);
    }

    private static void testUnavailableWhenMissingBuildFile() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-empty");
        try {
            Files.writeString(root.resolve("readme.txt"), "no gradle");
            assertTrue("no build file", !GradleCommand.isAvailable(root));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testAvailableWhenBuildGradlePresent() throws Exception {
        Path root = fakeGradleProject();
        try {
            assertTrue("available", GradleCommand.isAvailable(root));
            assertTrue("project file", GradleCommand.projectFile(root)
                    .endsWith(Path.of("build.gradle")));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testAvailableWhenKotlinDslPresent() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-kts");
        try {
            Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
            assertTrue("kts available", GradleCommand.isAvailable(root));
            assertTrue("kts file", GradleCommand.projectFile(root)
                    .getFileName().toString().equals("build.gradle.kts"));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testAvailableWhenSettingsGradlePresent() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-settings");
        try {
            Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'demo'\n");
            assertTrue("settings available", GradleCommand.isAvailable(root));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testPreferredOverLadle() throws Exception {
        Path gradleOnly = fakeGradleProject();
        Path ladleOnly = Files.createTempDirectory("lide-ladle-not-gradle");
        Path both = fakeGradleProject();
        try {
            assertTrue("gradle only", GradleCommand.preferredOverLadle(gradleOnly));
            Files.createDirectories(ladleOnly.resolve("lib"));
            Files.writeString(ladleOnly.resolve("lib").resolve("ladle.jar"), "fake");
            Files.writeString(ladleOnly.resolve("build.ini"), "[javac]\n");
            assertTrue("ladle only", !GradleCommand.preferredOverLadle(ladleOnly));
            Files.createDirectories(both.resolve("lib"));
            Files.writeString(both.resolve("lib").resolve("ladle.jar"), "fake");
            Files.writeString(both.resolve("build.ini"), "[javac]\n");
            assertTrue("both prefers ladle", !GradleCommand.preferredOverLadle(both));
            assertTrue("null", !GradleCommand.preferredOverLadle(null));
        } finally {
            deleteRecursive(gradleOnly);
            deleteRecursive(ladleOnly);
            deleteRecursive(both);
        }
    }

    private static void testWrapperPathPrefersPlatformScript() throws Exception {
        Path root = fakeGradleProject();
        try {
            Path wrapper = GradleCommand.wrapperPath(root);
            if (ScriptCommand.isWindows()) {
                assertTrue("windows wrapper", wrapper != null
                        && wrapper.getFileName().toString().equals("gradlew.bat"));
            } else {
                assertTrue("unix wrapper", wrapper != null
                        && wrapper.getFileName().toString().equals("gradlew"));
            }
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandForBuildUsesWrapper() throws Exception {
        Path root = fakeGradleProject();
        try {
            List<String> cmd = GradleCommand.commandFor(root, GradleCommand.BUILD);
            assertEqual("task", GradleCommand.BUILD, taskOf(cmd));
            assertTrue("plain console", cmd.contains(GradleCommand.CONSOLE_PLAIN));
            if (ScriptCommand.isWindows()) {
                assertEqual("cmd", "cmd.exe", cmd.get(0));
                assertEqual("/c", "/c", cmd.get(1));
                assertEqual("wrapper", "gradlew.bat", cmd.get(2));
            } else {
                assertEqual("sh", "sh", cmd.get(0));
                assertTrue("gradlew path", cmd.get(1).replace('\\', '/').endsWith("/gradlew"));
            }
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandForTestWithClassFilter() throws Exception {
        Path root = fakeGradleProject();
        try {
            List<String> cmd = GradleCommand.commandFor(
                    root, GradleCommand.TEST, GradleCommand.TESTS_FLAG, "FooTest");
            assertEqual("task", GradleCommand.TEST, taskOf(cmd));
            int tests = cmd.indexOf(GradleCommand.TESTS_FLAG);
            assertTrue("--tests present", tests >= 0);
            assertEqual("filter", "FooTest", cmd.get(tests + 1));
            List<String> skippedBlank = GradleCommand.commandFor(
                    root, GradleCommand.TEST, "  ", null);
            assertEqual("blank extras ignored", GradleCommand.TEST, taskOf(skippedBlank));
            assertTrue("no blank arg", !skippedBlank.contains("  "));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandForDependenciesRefreshes() throws Exception {
        Path root = fakeGradleProject();
        try {
            List<String> cmd = GradleCommand.commandFor(root, GradleCommand.DEPENDENCY);
            assertEqual("task", GradleCommand.DEPENDENCY, taskOf(cmd));
            assertTrue("refresh", cmd.contains(GradleCommand.REFRESH_DEPENDENCIES));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandWithoutWrapperUsesGradle() throws Exception {
        Path root = Files.createTempDirectory("lide-gradle-nowrapper");
        try {
            Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
            List<String> cmd = GradleCommand.commandFor(root, GradleCommand.CLEAR);
            assertEqual("task", GradleCommand.CLEAR, taskOf(cmd));
            if (ScriptCommand.isWindows()) {
                assertEqual("cmd", "cmd.exe", cmd.get(0));
                assertEqual("gradle", "gradle", cmd.get(2));
            } else {
                assertEqual("gradle", "gradle", cmd.get(0));
            }
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCommandForRequiresProject() {
        try {
            GradleCommand.commandFor(null, GradleCommand.BUILD);
            failed++;
            System.err.println("FAIL expected exception for null project");
        } catch (IllegalStateException ex) {
            assertTrue("message mentions build.gradle", ex.getMessage().contains("build.gradle"));
        }
    }

    private static void testCommandForRejectsBlank() throws Exception {
        Path root = fakeGradleProject();
        try {
            try {
                GradleCommand.commandFor(root, "  ");
                failed++;
                System.err.println("FAIL expected exception for blank task");
            } catch (IllegalArgumentException ex) {
                assertTrue("message mentions task", ex.getMessage().contains("task"));
            }
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testTaskForLadleCommands() {
        assertEqual("build", GradleCommand.BUILD, GradleCommand.taskFor(LadleCommand.BUILD));
        assertEqual("test", GradleCommand.TEST, GradleCommand.taskFor(LadleCommand.TEST));
        assertEqual("release", GradleCommand.RELEASE, GradleCommand.taskFor(LadleCommand.RELEASE));
        assertEqual("deps", GradleCommand.DEPENDENCY, GradleCommand.taskFor(LadleCommand.DEPENDENCY));
        assertEqual("clear", GradleCommand.CLEAR, GradleCommand.taskFor(LadleCommand.CLEAR));
        assertEqual("passthrough", "classes", GradleCommand.taskFor("classes"));
        try {
            GradleCommand.taskFor(" ");
            failed++;
            System.err.println("FAIL expected exception for blank taskFor");
        } catch (IllegalArgumentException ex) {
            passed++;
        }
    }

    private static void testTestClassFilter() {
        assertEqual("java file", "FooTest",
                GradleCommand.testClassFilter(Path.of("src", "test", "java", "FooTest.java")));
        assertEqual("plain name", "FooTest", GradleCommand.testClassFilter(Path.of("FooTest")));
        assertTrue("null", GradleCommand.testClassFilter(null) == null);
    }

    private static void testGradleVersionFromWrapper() throws Exception {
        Path root = fakeGradleProject();
        try {
            assertTrue("no wrapper props", GradleCommand.gradleVersion(root) == null);
            writeWrapper(root, "8.10.2");
            assertEqual("version", "8.10.2", GradleCommand.gradleVersion(root));
            writeWrapper(root, "9.4.0");
            assertEqual("9.4", "9.4.0", GradleCommand.gradleVersion(root));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testJavaRangeForGradleVersions() {
        GradleCommand.JavaRange unknown = GradleCommand.javaRangeForGradle(null);
        assertEqual("unknown min", 8, unknown.min());
        assertEqual("unknown max", 21, unknown.max());
        assertEqual("8.5 max", 21, GradleCommand.javaRangeForGradle("8.5").max());
        assertEqual("8.10 max", 23, GradleCommand.javaRangeForGradle("8.10.2").max());
        assertEqual("8.14 max", 24, GradleCommand.javaRangeForGradle("8.14.3").max());
        assertEqual("9.0 min", 17, GradleCommand.javaRangeForGradle("9.0.0").min());
        assertEqual("9.1 max", 25, GradleCommand.javaRangeForGradle("9.1.0").max());
        assertEqual("9.4 max", 26, GradleCommand.javaRangeForGradle("9.4.0").max());
        assertTrue("8.5 allows 21", GradleCommand.javaRangeForGradle("8.5").contains(21));
        assertTrue("8.5 rejects 26", !GradleCommand.javaRangeForGradle("8.5").contains(26));
    }

    private static void testFeatureVersionFromReleaseFile() throws Exception {
        Path jdk = Files.createTempDirectory("lide-fake-jdk");
        try {
            writeFakeJdk(jdk, 21);
            assertEqual("feature", 21, GradleCommand.featureVersion(jdk));
            assertTrue("missing", GradleCommand.featureVersion(jdk.resolve("missing")) < 0);
        } finally {
            deleteRecursive(jdk);
        }
    }

    private static void testJdkForGradlePicksNewestSupported() throws Exception {
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
            assertEqual("8.10 picks 21", jdk21.toAbsolutePath().normalize(), picked);
            writeWrapper(root, "9.4.0");
            Path newest = GradleCommand.jdkForGradle(root, List.of(jdk17, jdk21, jdk26));
            assertEqual("9.4 picks 26", jdk26.toAbsolutePath().normalize(), newest);
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk17);
            deleteRecursive(jdk21);
            deleteRecursive(jdk26);
        }
    }

    private static void testApplyJavaHomeKeepsCompatible() throws Exception {
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
            assertEqual("kept 21",
                    Path.of(builder.environment().get("JAVA_HOME")).toAbsolutePath().normalize(),
                    jdk21.toAbsolutePath().normalize());
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk21);
            deleteRecursive(jdk17);
        }
    }

    private static void testApplyJavaHomeReplacesJava26ForOldGradle() throws Exception {
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
            assertEqual("replaced with 21",
                    Path.of(builder.environment().get("JAVA_HOME")).toAbsolutePath().normalize(),
                    jdk21.toAbsolutePath().normalize());
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk21);
            deleteRecursive(jdk26);
        }
    }

    private static void testApplyJavaHomeThrowsWhenNoCompatibleJdk() throws Exception {
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
                    System.out.println("SKIP throw: running JVM is compatible with Gradle 8.5");
                    passed++;
                    return;
                }
                failed++;
                System.err.println("FAIL expected IllegalStateException for Java 26 vs Gradle 8.5");
            } catch (IllegalStateException ex) {
                assertTrue("mentions Java 26 or running version",
                        ex.getMessage().contains("cannot run on Java"));
                assertTrue("mentions major version", ex.getMessage().contains("major version"));
                assertTrue("suggests JDK 21", ex.getMessage().contains("JDK 21"));
            }
        } finally {
            deleteRecursive(root);
            deleteRecursive(jdk26);
        }
    }

    private static void testIncompatibleMessageMentionsMajorVersion() {
        String message = GradleCommand.incompatibleJdkMessage(
                "8.10.2", GradleCommand.javaRangeForGradle("8.10.2"), 26);
        assertTrue("gradle version", message.contains("8.10.2"));
        assertTrue("java 26", message.contains("Java 26"));
        assertTrue("major 70", message.contains("70"));
        assertTrue("upgrade wrapper", message.contains("9.4"));
    }

    private static void testProcessBuilderSetsJavaHome() throws Exception {
        Path root = fakeGradleProject();
        try {
            writeWrapper(root, "9.4.0");
            ProcessBuilder builder = GradleCommand.processBuilder(root, GradleCommand.BUILD);
            assertEqual("cwd", root.toAbsolutePath().normalize().toFile(), builder.directory());
            String detected = LadleCommand.detectedJdkHome();
            if (detected == null && builder.environment().get("JAVA_HOME") == null) {
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

    private GradleCommandTest() {
    }
}
