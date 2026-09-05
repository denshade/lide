package lide;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects a Gradle project and builds process commands for its CLI.
 *
 * Prefers the Gradle wrapper ({@code gradlew} / {@code gradlew.bat}) when
 * present; otherwise invokes {@code gradle} on PATH. Run with the project
 * root as the working directory.
 */
public final class GradleCommand {
    public static final String BUILD = "classes";
    public static final String TEST = "test";
    public static final String RELEASE = "assemble";
    public static final String DEPENDENCY = "dependencies";
    public static final String CLEAR = "clean";

    static final String CONSOLE_PLAIN = "--console=plain";
    static final String REFRESH_DEPENDENCIES = "--refresh-dependencies";
    static final String TESTS_FLAG = "--tests";
    static final String WRAPPER_PROPERTIES = "gradle/wrapper/gradle-wrapper.properties";
    private static final Pattern GRADLE_DIST =
            Pattern.compile("gradle-(\\d+\\.\\d+(?:\\.\\d+)?)");
    private static final Pattern JAVA_VERSION =
            Pattern.compile("JAVA_VERSION=\"(\\d+)");

    static final List<String> PROJECT_FILES = List.of(
            "settings.gradle",
            "settings.gradle.kts",
            "build.gradle",
            "build.gradle.kts");

    private GradleCommand() {
    }

    public static boolean isAvailable(Path projectRoot) {
        return projectFile(projectRoot) != null;
    }

    /**
     * True when this project should use Gradle instead of Ladle (Gradle files,
     * and no {@code lib/ladle.jar} + {@code build.ini}).
     */
    public static boolean preferredOverLadle(Path projectRoot) {
        return isAvailable(projectRoot) && !LadleCommand.isAvailable(projectRoot);
    }

    public static Path projectFile(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        for (String name : PROJECT_FILES) {
            Path file = projectRoot.resolve(name);
            if (Files.isRegularFile(file)) {
                return file.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    public static Path wrapperPath(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        if (ScriptCommand.isWindows()) {
            Path bat = projectRoot.resolve("gradlew.bat");
            if (Files.isRegularFile(bat)) {
                return bat.toAbsolutePath().normalize();
            }
            Path cmd = projectRoot.resolve("gradlew.cmd");
            if (Files.isRegularFile(cmd)) {
                return cmd.toAbsolutePath().normalize();
            }
            return null;
        }
        Path unix = projectRoot.resolve("gradlew");
        return Files.isRegularFile(unix) ? unix.toAbsolutePath().normalize() : null;
    }

    /**
     * Maps a Ladle command name (or a Gradle task) to the Gradle task Lide runs.
     */
    public static String taskFor(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("gradle task is required");
        }
        return switch (command) {
            case LadleCommand.BUILD -> BUILD;
            case LadleCommand.TEST -> TEST;
            case LadleCommand.RELEASE -> RELEASE;
            case LadleCommand.DEPENDENCY -> DEPENDENCY;
            case LadleCommand.CLEAR -> CLEAR;
            default -> command;
        };
    }

    /**
     * Simple class name for {@code gradle test --tests}, from a {@code *Test.java} path.
     */
    public static String testClassFilter(Path testFile) {
        if (testFile == null || testFile.getFileName() == null) {
            return null;
        }
        String name = testFile.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".java")) {
            return name.substring(0, name.length() - ".java".length());
        }
        return name;
    }

    /**
     * Builds the Gradle invocation for the project.
     * Run with the project root as the working directory.
     */
    public static List<String> commandFor(Path projectRoot, String command, String... extraArgs) {
        String task = taskFor(command);
        if (!isAvailable(projectRoot)) {
            throw new IllegalStateException(
                    "Gradle requires build.gradle, build.gradle.kts, settings.gradle, or settings.gradle.kts");
        }
        List<String> cmd = new ArrayList<>(launcher(projectRoot));
        cmd.add(CONSOLE_PLAIN);
        cmd.add(task);
        if (DEPENDENCY.equals(task)) {
            cmd.add(REFRESH_DEPENDENCIES);
        }
        if (extraArgs != null) {
            for (String extra : extraArgs) {
                if (extra != null && !extra.isBlank()) {
                    cmd.add(extra);
                }
            }
        }
        return cmd;
    }

    public static ProcessBuilder processBuilder(Path projectRoot, String command, String... extraArgs) {
        ProcessBuilder builder = new ProcessBuilder(commandFor(projectRoot, command, extraArgs));
        builder.directory(projectRoot.toAbsolutePath().normalize().toFile());
        builder.redirectErrorStream(true);
        applyGradleJavaHome(builder, projectRoot, defaultJdkCandidates());
        return builder;
    }

    /**
     * Points Gradle at a JDK it can actually run on. Lide itself may be on a
     * newer JDK (Java 26 class files are major version 70) than the wrapper.
     */
    static void applyGradleJavaHome(ProcessBuilder builder, Path projectRoot, List<Path> candidates) {
        JavaRange range = javaRangeForGradle(gradleVersion(projectRoot));
        String existing = builder.environment().get("JAVA_HOME");
        if (LadleCommand.isJdkHome(existing) && range.contains(featureVersion(Path.of(existing)))) {
            return;
        }
        Path jdk = jdkForGradle(projectRoot, candidates);
        if (jdk != null) {
            builder.environment().put("JAVA_HOME", jdk.toAbsolutePath().normalize().toString());
            return;
        }
        int running = Runtime.version().feature();
        if (range.contains(running)) {
            LadleCommand.applyJavaHome(builder);
            return;
        }
        throw new IllegalStateException(incompatibleJdkMessage(gradleVersion(projectRoot), range, running));
    }

    static Path jdkForGradle(Path projectRoot, List<Path> candidates) {
        JavaRange range = javaRangeForGradle(gradleVersion(projectRoot));
        Path best = null;
        int bestFeature = Integer.MIN_VALUE;
        if (candidates == null) {
            return null;
        }
        for (Path candidate : candidates) {
            if (candidate == null || !LadleCommand.isJdkHome(candidate.toString())) {
                continue;
            }
            int feature = featureVersion(candidate);
            if (range.contains(feature) && feature >= bestFeature) {
                best = candidate.toAbsolutePath().normalize();
                bestFeature = feature;
            }
        }
        return best;
    }

    static String gradleVersion(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        Path properties = projectRoot.resolve(WRAPPER_PROPERTIES);
        if (!Files.isRegularFile(properties)) {
            return null;
        }
        try {
            Matcher matcher = GRADLE_DIST.matcher(Files.readString(properties));
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException ex) {
            AppLog.exception("Could not read " + properties, ex);
            return null;
        }
    }

    static JavaRange javaRangeForGradle(String gradleVersion) {
        int[] parts = versionParts(gradleVersion);
        if (parts == null) {
            return new JavaRange(8, 21);
        }
        int major = parts[0];
        int minor = parts[1];
        int minJava = major >= 9 ? 17 : 8;
        int maxJava;
        if (atLeast(major, minor, 9, 4)) {
            maxJava = 26;
        } else if (atLeast(major, minor, 9, 1)) {
            maxJava = 25;
        } else if (atLeast(major, minor, 9, 0) || atLeast(major, minor, 8, 14)) {
            maxJava = 24;
        } else if (atLeast(major, minor, 8, 10)) {
            maxJava = 23;
        } else if (atLeast(major, minor, 8, 8)) {
            maxJava = 22;
        } else if (atLeast(major, minor, 8, 5)) {
            maxJava = 21;
        } else if (atLeast(major, minor, 8, 3)) {
            maxJava = 20;
        } else if (atLeast(major, minor, 7, 6)) {
            maxJava = 19;
        } else if (atLeast(major, minor, 7, 5)) {
            maxJava = 18;
        } else if (atLeast(major, minor, 7, 3)) {
            maxJava = 17;
        } else {
            maxJava = 16;
        }
        return new JavaRange(minJava, maxJava);
    }

    static int featureVersion(Path jdkHome) {
        if (jdkHome == null) {
            return -1;
        }
        Path release = jdkHome.resolve("release");
        if (!Files.isRegularFile(release)) {
            return -1;
        }
        try {
            Matcher matcher = JAVA_VERSION.matcher(Files.readString(release));
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (IOException ex) {
            AppLog.exception("Could not read " + release, ex);
            return -1;
        }
    }

    static String incompatibleJdkMessage(String gradleVersion, JavaRange range, int running) {
        String tool = gradleVersion == null || gradleVersion.isBlank()
                ? "Gradle"
                : "Gradle " + gradleVersion;
        int suggest = running > range.max() ? range.max() : range.min();
        StringBuilder message = new StringBuilder();
        message.append(tool).append(" cannot run on Java ").append(running)
                .append(" (class file major version ").append(44 + running).append(").\n");
        message.append("Install JDK ").append(suggest).append(" and try again.");
        if (running >= 26 && range.max() < 26) {
            message.append("\nOr upgrade the wrapper to Gradle 9.4 or later.");
        }
        return message.toString();
    }

    static List<Path> defaultJdkCandidates() {
        Set<Path> homes = new LinkedHashSet<>();
        addJdkHome(homes, envPath("JAVA_HOME"));
        addJdkHome(homes, propertyPath("java.home"));
        Path javaHome = propertyPath("java.home");
        if (javaHome != null) {
            addJdkChildren(homes, javaHome.getParent());
            Path jdkRoot = LadleCommand.isJdkHome(javaHome.toString()) ? javaHome : javaHome.getParent();
            if (jdkRoot != null && jdkRoot.getParent() != null) {
                addJdkChildren(homes, jdkRoot.getParent());
            }
        }
        if (ScriptCommand.isWindows()) {
            addJdkChildren(homes, Path.of("C:\\Program Files\\Java"));
            addJdkChildren(homes, Path.of("C:\\Program Files\\Eclipse Adoptium"));
            addJdkChildren(homes, Path.of("C:\\Program Files\\Microsoft"));
            addJdkChildren(homes, Path.of("C:\\Program Files\\Amazon Corretto"));
        } else {
            addJdkChildren(homes, Path.of("/usr/lib/jvm"));
            addJdkChildren(homes, Path.of("/usr/lib64/jvm"));
            addJdkChildren(homes, Path.of("/Library/Java/JavaVirtualMachines"));
        }
        return List.copyOf(homes);
    }

    static List<String> launcher(Path projectRoot) {
        Path wrapper = wrapperPath(projectRoot);
        if (wrapper != null) {
            if (ScriptCommand.isWindows()) {
                return List.of("cmd.exe", "/c", wrapper.getFileName().toString());
            }
            return List.of("sh", wrapper.toString());
        }
        if (ScriptCommand.isWindows()) {
            return List.of("cmd.exe", "/c", "gradle");
        }
        return List.of("gradle");
    }

    private static void addJdkHome(Set<Path> homes, Path raw) {
        if (raw == null) {
            return;
        }
        Path path = raw.toAbsolutePath().normalize();
        if (LadleCommand.isJdkHome(path.toString())) {
            homes.add(path);
            return;
        }
        Path parent = path.getParent();
        if (parent != null && LadleCommand.isJdkHome(parent.toString())) {
            homes.add(parent.toAbsolutePath().normalize());
        }
    }

    private static void addJdkChildren(Set<Path> homes, Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                addJdkHome(homes, child);
                addJdkHome(homes, child.resolve("Contents").resolve("Home"));
            }
        } catch (IOException ex) {
            AppLog.exception("Could not list JDKs in " + directory, ex);
        }
    }

    private static Path envPath(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (java.nio.file.InvalidPathException ex) {
            AppLog.exception("Invalid " + name + " path: " + value, ex);
            return null;
        }
    }

    private static Path propertyPath(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (java.nio.file.InvalidPathException ex) {
            AppLog.exception("Invalid " + name + " path: " + value, ex);
            return null;
        }
    }

    private static int[] versionParts(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String[] bits = version.split("\\.");
        try {
            int major = Integer.parseInt(bits[0]);
            int minor = bits.length > 1 ? Integer.parseInt(bits[1]) : 0;
            return new int[] { major, minor };
        } catch (NumberFormatException ex) {
            AppLog.exception("Could not parse Gradle version " + version, ex);
            return null;
        }
    }

    private static boolean atLeast(int major, int minor, int requiredMajor, int requiredMinor) {
        return major > requiredMajor || (major == requiredMajor && minor >= requiredMinor);
    }

    record JavaRange(int min, int max) {
        boolean contains(int feature) {
            return feature >= min && feature <= max;
        }
    }
}
