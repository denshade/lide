package lide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Builds a runnable fat JAR: application classes, classpath icons, and extra
 * JARs from {@code lib/} except {@code ladle.jar}.
 */
public final class FatJar {
    static final String MAIN_CLASS = "lide.LideApp";
    static final String JAR_NAME = "lide.jar";

    private FatJar() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path jar = build(root);
        System.out.println("Created " + jar);
        System.out.println("Run with: java -jar " + jar);
    }

    public static Path build(Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path classes = classesDirectory(root);
        if (classes == null) {
            throw new IllegalStateException(
                    "No compiled classes found. Run build.bat or build.sh first.");
        }
        copyClasspathIcons(root, classes);
        Path jar = outputPath(root);
        write(classes, extraJars(root), jar);
        return jar;
    }

    static Path outputPath(Path projectRoot) {
        return projectRoot.resolve("dist").resolve(JAR_NAME);
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

    static List<Path> extraJars(Path projectRoot) throws IOException {
        Path lib = projectRoot.resolve(LadleCommand.JAR_DIR);
        if (!Files.isDirectory(lib)) {
            return List.of();
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(lib, "*.jar")) {
            for (Path jar : stream) {
                Path name = jar.getFileName();
                if (name == null || LadleCommand.JAR_NAME.equals(name.toString())) {
                    continue;
                }
                jars.add(jar.toAbsolutePath().normalize());
            }
        }
        jars.sort(Path::compareTo);
        return jars;
    }

    static void write(Path classesDir, List<Path> extraJars, Path jarFile) throws IOException {
        Path parent = jarFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Set<String> added = new LinkedHashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarFile))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(("Manifest-Version: 1.0\r\n"
                    + "Main-Class: " + MAIN_CLASS + "\r\n"
                    + "Created-By: Lide FatJar\r\n"
                    + "\r\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            added.add("META-INF/MANIFEST.MF");
            addDirectory(zip, classesDir, added);
            if (extraJars != null) {
                for (Path extra : extraJars) {
                    if (extra != null && Files.isRegularFile(extra)) {
                        addJar(zip, extra, added);
                    }
                }
            }
        }
    }

    private static void addDirectory(ZipOutputStream zip, Path classesDir, Set<String> added)
            throws IOException {
        try (var walk = Files.walk(classesDir)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String entry = classesDir.relativize(file).toString().replace('\\', '/');
                if (isApplicationTestClass(entry) || skipMergedEntry(entry) || !added.add(entry)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entry));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private static void addJar(ZipOutputStream zip, Path jar, Set<String> added) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!added.add(name) || skipMergedEntry(name)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(name));
                in.transferTo(zip);
                zip.closeEntry();
            }
        }
    }

    static boolean skipMergedEntry(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String normalized = name.replace('\\', '/');
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("META-INF/MANIFEST.MF".equals(upper)) {
            return true;
        }
        if ("META-INF/INDEX.LIST".equals(upper)
                || "MODULE-INFO.CLASS".equals(upper)
                || upper.endsWith("/MODULE-INFO.CLASS")) {
            return true;
        }
        if (upper.startsWith("META-INF/") && (upper.endsWith(".SF")
                || upper.endsWith(".DSA")
                || upper.endsWith(".RSA")
                || upper.endsWith(".EC"))) {
            return true;
        }
        return upper.startsWith("META-INF/SIG-");
    }

    static boolean isApplicationTestClass(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String file = name.replace('\\', '/');
        int slash = file.lastIndexOf('/');
        if (slash >= 0) {
            file = file.substring(slash + 1);
        }
        return file.endsWith("Test.class") || file.contains("Test$");
    }
}
