package lide;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Tests for native application image packaging.
 */
public final class AppPackagerTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testClassesDirectoryPrefersOut();
        testClassesDirectoryFallsBackToLadle();
        testClassesDirectoryMissing();
        testCreateJarWritesMainClassAndEntries();
        testCommandForWindowsUsesIcoAndSkipsMacFlags();
        testCommandForMacUsesIcnsAndIdentifier();
        testCommandForOmitsMissingIcon();
        testOutputPath();
        testPackageAppRequiresClasses();
        testJpackageExecutableWhenJdkPresent();
        testCurrentPlatformMatchesOs();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testClassesDirectoryPrefersOut() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-out");
        try {
            Path cls = root.resolve("out").resolve("lide").resolve("LideApp.class");
            Files.createDirectories(cls.getParent());
            Files.writeString(cls, "out");
            Path ladle = root.resolve("build").resolve("classes").resolve("lide").resolve("LideApp.class");
            Files.createDirectories(ladle.getParent());
            Files.writeString(ladle, "ladle");
            assertEqual("prefers out", root.resolve("out"), AppPackager.classesDirectory(root));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testClassesDirectoryFallsBackToLadle() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-ladle");
        try {
            Path ladle = root.resolve("build").resolve("classes").resolve("lide").resolve("LideApp.class");
            Files.createDirectories(ladle.getParent());
            Files.writeString(ladle, "ladle");
            assertEqual("ladle classes",
                    root.resolve("build").resolve("classes"),
                    AppPackager.classesDirectory(root));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testClassesDirectoryMissing() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-missing");
        try {
            assertTrue("null when missing", AppPackager.classesDirectory(root) == null);
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testCreateJarWritesMainClassAndEntries() throws Exception {
        Path dir = Files.createTempDirectory("lide-pack-jar");
        try {
            Path classes = dir.resolve("classes");
            Path pkg = classes.resolve("lide");
            Files.createDirectories(pkg);
            Files.writeString(pkg.resolve("LideApp.class"), "class-bytes");
            Files.createDirectories(pkg.resolve("icons"));
            Files.writeString(pkg.resolve("icons").resolve("lide-16.png"), "png");
            Path jar = dir.resolve("lide.jar");
            AppPackager.createJar(classes, jar);
            assertTrue("jar exists", Files.size(jar) > 0);
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
                assertTrue("manifest", manifest != null);
                String text = new String(zip.getInputStream(manifest).readAllBytes(), StandardCharsets.UTF_8);
                assertTrue("main class", text.contains("Main-Class: lide.LideApp"));
                assertTrue("class entry", zip.getEntry("lide/LideApp.class") != null);
                assertTrue("icon entry", zip.getEntry("lide/icons/lide-16.png") != null);
            }
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testCommandForWindowsUsesIcoAndSkipsMacFlags() throws Exception {
        Path dir = Files.createTempDirectory("lide-pack-win-cmd");
        try {
            Path icon = dir.resolve("lide.ico");
            Files.writeString(icon, "ico");
            List<String> command = AppPackager.commandFor(
                    dir.resolve("jpackage.exe"),
                    dir.resolve("input"),
                    dir.resolve("dist"),
                    icon,
                    AppPackager.Platform.WINDOWS);
            assertTrue("type", command.contains("app-image"));
            assertTrue("name", command.contains("Lide"));
            assertTrue("main class", command.contains("lide.LideApp"));
            assertTrue("main jar", command.contains("lide.jar"));
            assertTrue("icon", command.contains(icon.toAbsolutePath().normalize().toString()));
            assertTrue("no mac identifier", !command.contains("--mac-package-identifier"));
            assertEqual("icon flag before path",
                    "--icon",
                    command.get(command.indexOf(icon.toAbsolutePath().normalize().toString()) - 1));
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testCommandForMacUsesIcnsAndIdentifier() throws Exception {
        Path dir = Files.createTempDirectory("lide-pack-mac-cmd");
        try {
            Path icon = dir.resolve("lide.icns");
            Files.writeString(icon, "icns");
            List<String> command = AppPackager.commandFor(
                    dir.resolve("jpackage"),
                    dir.resolve("input"),
                    dir.resolve("dist"),
                    icon,
                    AppPackager.Platform.MACOS);
            assertTrue("icns", command.contains(icon.toAbsolutePath().normalize().toString()));
            assertTrue("identifier flag", command.contains("--mac-package-identifier"));
            assertTrue("identifier", command.contains("lide.Lide"));
            assertTrue("category", command.contains("public.app-category.developer-tools"));
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testCommandForOmitsMissingIcon() throws Exception {
        Path dir = Files.createTempDirectory("lide-pack-no-icon");
        try {
            List<String> command = AppPackager.commandFor(
                    dir.resolve("jpackage"),
                    dir.resolve("input"),
                    dir.resolve("dist"),
                    dir.resolve("missing.ico"),
                    AppPackager.Platform.LINUX);
            assertTrue("no icon flag", !command.contains("--icon"));
            assertTrue("no mac flags on linux", !command.contains("--mac-package-identifier"));
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void testOutputPath() throws Exception {
        Path dest = Path.of("dist");
        assertEqual("windows", dest.resolve("Lide"), AppPackager.Platform.WINDOWS.outputPath(dest));
        assertEqual("linux", dest.resolve("Lide"), AppPackager.Platform.LINUX.outputPath(dest));
        assertEqual("mac", dest.resolve("Lide.app"), AppPackager.Platform.MACOS.outputPath(dest));
        assertEqual("windows icon", "lide.ico", AppPackager.Platform.WINDOWS.iconFileName());
        assertEqual("mac icon", "lide.icns", AppPackager.Platform.MACOS.iconFileName());
        assertEqual("linux icon", "lide.png", AppPackager.Platform.LINUX.iconFileName());
    }

    private static void testPackageAppRequiresClasses() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-require");
        try {
            AppPackager.packageApp(root, AppPackager.Platform.WINDOWS);
            failed++;
            System.err.println("FAIL expected IllegalStateException for missing classes");
        } catch (IllegalStateException ex) {
            passed++;
            assertTrue("mentions build", ex.getMessage().contains("compiled classes"));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testJpackageExecutableWhenJdkPresent() {
        Path javaHome = Path.of(System.getProperty("java.home", ""));
        String exe = ScriptCommand.isWindows() ? "jpackage.exe" : "jpackage";
        boolean present = Files.isRegularFile(javaHome.resolve("bin").resolve(exe));
        if (!present) {
            Path parent = javaHome.getParent();
            present = parent != null && Files.isRegularFile(parent.resolve("bin").resolve(exe));
        }
        if (present) {
            Path found = AppPackager.jpackageExecutable();
            assertTrue("found jpackage", found != null && Files.isRegularFile(found));
        } else {
            passed++;
        }
    }

    private static void testCurrentPlatformMatchesOs() {
        if (ScriptCommand.isWindows()) {
            assertEqual("windows current", AppPackager.Platform.WINDOWS, AppPackager.Platform.current());
            assertTrue("not mac", !ScriptCommand.isMac());
        } else if (ScriptCommand.isMac()) {
            assertEqual("mac current", AppPackager.Platform.MACOS, AppPackager.Platform.current());
        } else {
            assertEqual("linux current", AppPackager.Platform.LINUX, AppPackager.Platform.current());
        }
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

    private AppPackagerTest() {
    }
}
