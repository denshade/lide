package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects a Ladle project and builds process commands for its CLI.
 *
 * Ladle is a lightweight Java build tool (compile, test, package, dependencies)
 * configured by {@code build.ini} and invoked as {@code java -jar lib/ladle.jar}.
 */
public final class LadleCommand {
    public static final String BUILD = "build";
    public static final String TEST = "test";
    public static final String RELEASE = "release";
    public static final String DEPENDENCY = "dependency";
    public static final String CLEAR = "clear";

    static final String INI_NAME = "build.ini";
    static final String JAR_DIR = "lib";
    static final String JAR_NAME = "ladle.jar";

    private LadleCommand() {
    }

    public static boolean isAvailable(Path projectRoot) {
        return jarPath(projectRoot) != null && iniPath(projectRoot) != null;
    }

    public static Path jarPath(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        Path jar = projectRoot.resolve(JAR_DIR).resolve(JAR_NAME);
        return Files.isRegularFile(jar) ? jar.toAbsolutePath().normalize() : null;
    }

    public static Path iniPath(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        Path ini = projectRoot.resolve(INI_NAME);
        return Files.isRegularFile(ini) ? ini.toAbsolutePath().normalize() : null;
    }

    /**
     * Builds {@code java -jar lib/ladle.jar <command> build.ini} for the project.
     * Run with the project root as the working directory.
     */
    public static List<String> commandFor(Path projectRoot, String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("ladle command is required");
        }
        Path jar = jarPath(projectRoot);
        Path ini = iniPath(projectRoot);
        if (jar == null || ini == null) {
            throw new IllegalStateException(
                    "Ladle requires " + JAR_DIR + "/" + JAR_NAME + " and " + INI_NAME);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable());
        cmd.add("-jar");
        cmd.add(jar.toString());
        cmd.add(command);
        cmd.add(INI_NAME);
        return cmd;
    }

    public static ProcessBuilder processBuilder(Path projectRoot, String command) {
        ProcessBuilder builder = new ProcessBuilder(commandFor(projectRoot, command));
        builder.directory(projectRoot.toAbsolutePath().normalize().toFile());
        builder.redirectErrorStream(true);
        applyJavaHome(builder);
        return builder;
    }

    static void applyJavaHome(ProcessBuilder builder) {
        String existing = builder.environment().get("JAVA_HOME");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        String detected = detectedJdkHome();
        if (detected != null) {
            builder.environment().put("JAVA_HOME", detected);
        }
    }

    /**
     * JDK root that contains {@code bin/javac}, from {@code JAVA_HOME} or {@code java.home}.
     */
    static String detectedJdkHome() {
        String env = System.getenv("JAVA_HOME");
        if (isJdkHome(env)) {
            return Path.of(env).toAbsolutePath().normalize().toString();
        }
        String javaHome = System.getProperty("java.home");
        if (isJdkHome(javaHome)) {
            return Path.of(javaHome).toAbsolutePath().normalize().toString();
        }
        if (javaHome != null && !javaHome.isBlank()) {
            Path parent = Path.of(javaHome).toAbsolutePath().normalize().getParent();
            if (parent != null && isJdkHome(parent.toString())) {
                return parent.toString();
            }
        }
        return null;
    }

    static boolean isJdkHome(String home) {
        if (home == null || home.isBlank()) {
            return false;
        }
        String exe = ScriptCommand.isWindows() ? "javac.exe" : "javac";
        return Files.isRegularFile(Path.of(home).resolve("bin").resolve(exe));
    }

    static String javaExecutable() {
        String home = detectedJdkHome();
        if (home != null) {
            String exe = ScriptCommand.isWindows() ? "java.exe" : "java";
            Path java = Path.of(home).resolve("bin").resolve(exe);
            if (Files.isRegularFile(java)) {
                return java.toAbsolutePath().normalize().toString();
            }
        }
        return "java";
    }
}
