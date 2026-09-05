package lide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
    static final String FILTER_TOO_OLD =
            "This project's Ladle is too old to run a single test.\n"
                    + "Use Ladle → Install Ladle to update it.";
    private static final String LADLE_CLASS = "thelaboflieven/info/Ladle.class";
    private static final String TEST_FILTER_HELP = "[<class";

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
     * Builds {@code java -jar lib/ladle.jar <command> build.ini [extra...]} for the project.
     * Run with the project root as the working directory.
     */
    public static List<String> commandFor(Path projectRoot, String command, String... extraArgs) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("ladle command is required");
        }
        Path jar = jarFor(projectRoot, extraArgs);
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
        applyJavaHome(builder);
        return builder;
    }

    /**
     * Project {@code lib/ladle.jar}, or a newer Ladle from Lide / a sibling
     * distribution when extra arguments need single-test filter support.
     */
    static Path jarFor(Path projectRoot, String... extraArgs) {
        Path projectJar = jarPath(projectRoot);
        if (!hasExtras(extraArgs)) {
            return projectJar;
        }
        Path capable = jarForTestFilter(projectRoot);
        if (capable == null) {
            throw new IllegalStateException(FILTER_TOO_OLD);
        }
        return capable;
    }

    static Path jarForTestFilter(Path projectRoot) {
        return jarForTestFilter(projectRoot, defaultFilterFallbacks(projectRoot));
    }

    static Path jarForTestFilter(Path projectRoot, List<Path> fallbacks) {
        Path projectJar = jarPath(projectRoot);
        if (supportsTestFilters(projectJar)) {
            return projectJar;
        }
        if (fallbacks == null) {
            return null;
        }
        for (Path fallback : fallbacks) {
            if (supportsTestFilters(fallback)) {
                return fallback.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    static List<Path> defaultFilterFallbacks(Path projectRoot) {
        List<Path> jars = new ArrayList<>();
        addExistingJar(jars, bundledJarNearLide());
        Path distribution = LadleInstaller.findDistribution(projectRoot);
        if (distribution != null) {
            addExistingJar(jars, distribution.resolve(JAR_DIR).resolve(JAR_NAME));
        }
        return jars;
    }

    static Path bundledJarNearLide() {
        List<Path> roots = new ArrayList<>();
        addRoot(roots, Path.of(System.getProperty("user.dir", ".")));
        Path code = LadleInstaller.codeSourceDirectory();
        addRoot(roots, code);
        if (code != null) {
            addRoot(roots, code.getParent());
        }
        for (Path root : roots) {
            Path jar = root.resolve(JAR_DIR).resolve(JAR_NAME);
            if (Files.isRegularFile(jar)) {
                return jar.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    static boolean supportsTestFilters(Path jar) {
        if (jar == null || !Files.isRegularFile(jar) || !looksLikeZip(jar)) {
            return false;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(LADLE_CLASS);
            if (entry == null) {
                return false;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                return text.contains(TEST_FILTER_HELP);
            }
        } catch (IOException ex) {
            AppLog.exception("Could not inspect " + jar + " for single-test support", ex);
            return false;
        }
    }

    private static boolean hasExtras(String... extraArgs) {
        if (extraArgs == null) {
            return false;
        }
        for (String extra : extraArgs) {
            if (extra != null && !extra.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeZip(Path jar) {
        try (InputStream in = Files.newInputStream(jar)) {
            byte[] magic = in.readNBytes(2);
            return magic.length == 2 && magic[0] == 'P' && magic[1] == 'K';
        } catch (IOException ex) {
            AppLog.exception("Could not read " + jar, ex);
            return false;
        }
    }

    private static void addExistingJar(List<Path> jars, Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return;
        }
        Path normalized = jar.toAbsolutePath().normalize();
        if (!jars.contains(normalized)) {
            jars.add(normalized);
        }
    }

    private static void addRoot(List<Path> roots, Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!roots.contains(normalized)) {
            roots.add(normalized);
        }
    }

    static void applyJavaHome(ProcessBuilder builder) {
        if (JavaHome.apply(builder)) {
            return;
        }
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
     * JDK root that contains {@code bin/javac}, from {@code Java → Set JAVA_HOME},
     * {@code JAVA_HOME}, or {@code java.home}.
     */
    static String detectedJdkHome() {
        Path configured = JavaHome.configured();
        if (configured != null && isJdkHome(configured.toString())) {
            return configured.toAbsolutePath().normalize().toString();
        }
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
