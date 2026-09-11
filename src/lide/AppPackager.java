package lide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a native application image with a bundled Java runtime via {@code jpackage}.
 *
 * Windows produces {@code dist/Lide/Lide.exe}; macOS produces {@code dist/Lide.app}.
 * Each image can only be built on its own operating system.
 */
public final class AppPackager {
    static final String APP_NAME = "Lide";
    static final String MAIN_CLASS = "lide.LideApp";
    static final String MAIN_JAR = "lide.jar";
    static final String APP_VERSION = "1.0";
    static final String DESCRIPTION = "Lightweight Java IDE";
    static final String VENDOR = "Lide";
    static final String MAC_IDENTIFIER = "lide.Lide";
    static final String MAC_CATEGORY = "public.app-category.developer-tools";
    static final String ADD_MODULES =
            "java.desktop,java.logging,java.prefs,jdk.unsupported,jdk.unsupported.desktop,jdk.localedata";

    enum Platform {
        WINDOWS,
        MACOS,
        LINUX;

        static Platform current() {
            if (ScriptCommand.isWindows()) {
                return WINDOWS;
            }
            if (ScriptCommand.isMac()) {
                return MACOS;
            }
            return LINUX;
        }

        String iconFileName() {
            return switch (this) {
                case WINDOWS -> "lide.ico";
                case MACOS -> "lide.icns";
                case LINUX -> "lide.png";
            };
        }

        Path outputPath(Path destDir) {
            if (this == MACOS) {
                return destDir.resolve(APP_NAME + ".app");
            }
            return destDir.resolve(APP_NAME);
        }
    }

    private AppPackager() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path output = packageApp(root);
        System.out.println("Created " + output);
    }

    public static Path packageApp(Path projectRoot) throws IOException, InterruptedException {
        return packageApp(projectRoot, Platform.current());
    }

    static Path packageApp(Path projectRoot, Platform platform)
            throws IOException, InterruptedException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path classes = classesDirectory(root);
        if (classes == null) {
            throw new IllegalStateException(
                    "No compiled classes found. Run build.bat or build.sh first.");
        }
        writeIcons(root);
        copyClasspathIcons(root, classes);

        Path destDir = destDirectory(root);
        Path output = platform.outputPath(destDir);
        deleteRecursive(output);

        Path inputDir = inputDirectory(root);
        deleteRecursive(inputDir);
        Files.createDirectories(inputDir);
        createJar(classes, inputDir.resolve(MAIN_JAR));

        Path icon = root.resolve("assets").resolve(platform.iconFileName());
        Path jpackage = jpackageExecutable();
        if (jpackage == null) {
            throw new IllegalStateException(
                    "jpackage not found. Install a JDK (16+) and ensure JAVA_HOME/bin/jpackage exists.");
        }
        Files.createDirectories(destDir);
        List<String> command = commandFor(jpackage, inputDir, destDir, icon, platform);
        System.out.println(String.join(" ", command));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        System.out.print(log);
        int code;
        try {
            code = process.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw ex;
        }
        if (code != 0) {
            throw new IOException("jpackage failed with exit " + code + ":\n" + log);
        }
        if (!Files.exists(output)) {
            throw new IOException("jpackage finished but " + output + " was not created:\n" + log);
        }
        return output;
    }

    static Path destDirectory(Path projectRoot) {
        return projectRoot.resolve("dist");
    }

    static Path inputDirectory(Path projectRoot) {
        return projectRoot.resolve("build").resolve("jpackage-input");
    }

    static Path classesDirectory(Path projectRoot) {
        Path out = projectRoot.resolve("out").resolve("lide").resolve("LideApp.class");
        if (Files.isRegularFile(out)) {
            return projectRoot.resolve("out");
        }
        Path ladle = projectRoot.resolve("build").resolve("classes")
                .resolve("lide").resolve("LideApp.class");
        if (Files.isRegularFile(ladle)) {
            return projectRoot.resolve("build").resolve("classes");
        }
        return null;
    }

    static void writeIcons(Path projectRoot) throws IOException {
        Path assets = projectRoot.resolve("assets");
        IconGenerator.writePng(assets.resolve("lide.png"), 256);
        IconGenerator.writeIco(assets.resolve("lide.ico"));
        IconGenerator.writeIcns(assets.resolve("lide.icns"));
        Path icons = projectRoot.resolve("src").resolve("lide").resolve("icons");
        IconGenerator.writePng(icons.resolve("lide-16.png"), 16);
        IconGenerator.writePng(icons.resolve("lide-32.png"), 32);
        IconGenerator.writePng(icons.resolve("lide-64.png"), 64);
        IconGenerator.writePng(icons.resolve("lide-128.png"), 128);
        IconGenerator.writePng(icons.resolve("lide-256.png"), 256);
    }

    static void copyClasspathIcons(Path projectRoot, Path classesDir) throws IOException {
        Path source = projectRoot.resolve("src").resolve("lide").resolve("icons");
        Path target = classesDir.resolve("lide").resolve("icons");
        if (!Files.isDirectory(source)) {
            return;
        }
        Files.createDirectories(target);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source, "*.png")) {
            for (Path png : stream) {
                Path name = png.getFileName();
                if (name != null) {
                    Files.copy(png, target.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void createJar(Path classesDir, Path jarFile) throws IOException {
        Path parent = jarFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(("Manifest-Version: 1.0\r\n"
                    + "Main-Class: " + MAIN_CLASS + "\r\n"
                    + "Created-By: Lide AppPackager\r\n"
                    + "\r\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            try (var walk = Files.walk(classesDir)) {
                List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
                for (Path file : files) {
                    String entry = classesDir.relativize(file).toString().replace('\\', '/');
                    if (entry.startsWith("META-INF/")) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry(entry));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    static List<String> commandFor(
            Path jpackage, Path inputDir, Path destDir, Path icon, Platform platform) {
        List<String> command = new ArrayList<>();
        command.add(jpackage.toAbsolutePath().normalize().toString());
        command.add("--type");
        command.add("app-image");
        command.add("--name");
        command.add(APP_NAME);
        command.add("--app-version");
        command.add(APP_VERSION);
        command.add("--description");
        command.add(DESCRIPTION);
        command.add("--vendor");
        command.add(VENDOR);
        command.add("--dest");
        command.add(destDir.toAbsolutePath().normalize().toString());
        command.add("--input");
        command.add(inputDir.toAbsolutePath().normalize().toString());
        command.add("--main-jar");
        command.add(MAIN_JAR);
        command.add("--main-class");
        command.add(MAIN_CLASS);
        command.add("--add-modules");
        command.add(ADD_MODULES);
        command.add("--java-options");
        command.add("-Dfile.encoding=UTF-8");
        if (icon != null && Files.isRegularFile(icon)) {
            command.add("--icon");
            command.add(icon.toAbsolutePath().normalize().toString());
        }
        if (platform == Platform.MACOS) {
            command.add("--mac-package-identifier");
            command.add(MAC_IDENTIFIER);
            command.add("--mac-package-name");
            command.add(APP_NAME);
            command.add("--mac-app-category");
            command.add(MAC_CATEGORY);
        }
        return command;
    }

    static Path jpackageExecutable() {
        List<Path> homes = new ArrayList<>();
        addHome(homes, LadleCommand.detectedJdkHome());
        addHome(homes, System.getProperty("java.home"));
        String exe = ScriptCommand.isWindows() ? "jpackage.exe" : "jpackage";
        for (Path home : homes) {
            Path bin = home.resolve("bin").resolve(exe);
            if (Files.isRegularFile(bin)) {
                return bin.toAbsolutePath().normalize();
            }
            Path parent = home.getParent();
            if (parent != null) {
                Path sibling = parent.resolve("bin").resolve(exe);
                if (Files.isRegularFile(sibling)) {
                    return sibling.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private static void addHome(List<Path> homes, String home) {
        if (home == null || home.isBlank()) {
            return;
        }
        addHome(homes, Path.of(home));
    }

    private static void addHome(List<Path> homes, Path home) {
        if (home == null) {
            return;
        }
        Path normalized = home.toAbsolutePath().normalize();
        if (!homes.contains(normalized)) {
            homes.add(normalized);
        }
    }

    static void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
