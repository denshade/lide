package lide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies a Ladle distribution into a project (jar, launchers, optional starter INI).
 */
public final class LadleInstaller {
    static final List<String> LAUNCHER_NAMES = List.of("ladle", "ladle.cmd", "ladle.ps1", "ladle.bat");

    private LadleInstaller() {
    }

    public static boolean isDistribution(Path directory) {
        if (directory == null) {
            return false;
        }
        return Files.isRegularFile(directory.resolve(LadleCommand.JAR_DIR).resolve(LadleCommand.JAR_NAME));
    }

    public static Path findDistribution(Path projectRoot) {
        return findDistribution(projectRoot, defaultSearchRoots(projectRoot));
    }

    static Path findDistribution(Path projectRoot, List<Path> searchRoots) {
        Path target = projectRoot == null ? null : projectRoot.toAbsolutePath().normalize();
        for (Path root : searchRoots) {
            if (root == null) {
                continue;
            }
            Path normalized = root.toAbsolutePath().normalize();
            Path found = distributionIfUsable(normalized, target);
            if (found != null) {
                return found;
            }
            Path parent = normalized.getParent();
            if (parent != null) {
                found = distributionIfUsable(parent.resolve("ladle"), target);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static List<Path> defaultSearchRoots(Path projectRoot) {
        List<Path> roots = new ArrayList<>();
        addSearchRoot(roots, Path.of(System.getProperty("user.dir", ".")));
        addSearchRoot(roots, projectRoot);
        addSearchRoot(roots, codeSourceDirectory());
        Path code = codeSourceDirectory();
        if (code != null) {
            addSearchRoot(roots, code.getParent());
        }
        return roots;
    }

    private static void addSearchRoot(List<Path> roots, Path path) {
        if (path == null) {
            return;
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!roots.contains(normalized)) {
            roots.add(normalized);
        }
    }

    private static Path distributionIfUsable(Path candidate, Path targetProject) {
        if (!isDistribution(candidate)) {
            return null;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (targetProject != null && normalized.equals(targetProject)) {
            return null;
        }
        return normalized;
    }

    static Path codeSourceDirectory() {
        try {
            var source = LideApp.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path.getParent() : path;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Result install(Path source, Path projectRoot) throws IOException {
        if (source == null || projectRoot == null) {
            throw new IllegalArgumentException("source and project are required");
        }
        if (!isDistribution(source)) {
            throw new IllegalArgumentException(
                    "Not a Ladle distribution (missing lib/" + LadleCommand.JAR_NAME + "): " + source);
        }
        Path target = projectRoot.toAbsolutePath().normalize();
        Path srcJar = source.resolve(LadleCommand.JAR_DIR).resolve(LadleCommand.JAR_NAME)
                .toAbsolutePath().normalize();
        Path destJar = target.resolve(LadleCommand.JAR_DIR).resolve(LadleCommand.JAR_NAME);
        Files.createDirectories(destJar.getParent());
        if (!srcJar.equals(destJar)) {
            Files.copy(srcJar, destJar, StandardCopyOption.REPLACE_EXISTING);
        }

        List<Path> scripts = new ArrayList<>();
        Path srcBin = source.resolve("bin");
        if (Files.isDirectory(srcBin)) {
            Files.createDirectories(target.resolve("bin"));
            for (String name : LAUNCHER_NAMES) {
                Path from = srcBin.resolve(name);
                if (!Files.isRegularFile(from)) {
                    continue;
                }
                Path to = target.resolve("bin").resolve(name);
                if (!from.toAbsolutePath().normalize().equals(to.toAbsolutePath().normalize())) {
                    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
                makeExecutableIfNeeded(to);
                scripts.add(to.toAbsolutePath().normalize());
            }
        }

        boolean wroteIni = false;
        Path ini = target.resolve(LadleCommand.INI_NAME);
        if (!Files.isRegularFile(ini)) {
            Files.writeString(ini, defaultBuildIni(target));
            wroteIni = true;
        }
        return new Result(
                source.toAbsolutePath().normalize(),
                destJar.toAbsolutePath().normalize(),
                List.copyOf(scripts),
                wroteIni);
    }

    static String defaultBuildIni(Path projectRoot) {
        String jarName = "app";
        if (projectRoot != null && projectRoot.getFileName() != null) {
            jarName = projectRoot.getFileName().toString();
        }
        return javacSection() + """

                [sources]
                paths = src

                [build]
                directory = build

                [testdependencies]
                org.junit.jupiter.api = https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter-api/6.1.0/junit-jupiter-api-6.1.0.jar
                junit-platform-console-standalone-6.1.0.jar = https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/6.1.0/junit-platform-console-standalone-6.1.0.jar

                [test]
                sources = test
                classpath = build/classes
                output = build/test-classes

                [jar]
                name = %s
                directory = build
                """.formatted(jarName);
    }

    static String javacSection() {
        String jdk = LadleCommand.detectedJdkHome();
        if (jdk != null) {
            String path = Path.of(jdk).toAbsolutePath().normalize().toString().replace('\\', '/');
            return """
                    [javac]
                    path = %s
                    release = 21
                    parameters = -encoding UTF-8 -d build/classes
                    """.formatted(path).stripTrailing();
        }
        return """
                [javac]
                path = .jdk
                download.windows = https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse
                download.linux = https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse
                download.macos = https://api.adoptium.net/v3/binary/latest/21/ga/mac/x64/jdk/hotspot/normal/eclipse
                release = 21
                parameters = -encoding UTF-8 -d build/classes
                """.stripTrailing();
    }

    static void makeExecutableIfNeeded(Path file) {
        Path name = file.getFileName();
        if (name == null || name.toString().contains(".")) {
            return;
        }
        try {
            Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(file));
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            file.toFile().setExecutable(true, false);
        }
    }

    public record Result(Path source, Path jar, List<Path> scripts, boolean wroteIni) {
    }
}
