package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Assumptions;
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

    @Test
    void testClassesDirectoryPrefersOut() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-out");
        try {
            Path cls = root.resolve("out").resolve("lide").resolve("LideApp.class");
            Files.createDirectories(cls.getParent());
            Files.writeString(cls, "out");
            Path ladle = root.resolve("build").resolve("classes").resolve("lide").resolve("LideApp.class");
            Files.createDirectories(ladle.getParent());
            Files.writeString(ladle, "ladle");
            assertEquals(root.resolve("out"), AppPackager.classesDirectory(root), "prefers out");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testClassesDirectoryFallsBackToLadle() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-ladle");
        try {
            Path ladle = root.resolve("build").resolve("classes").resolve("lide").resolve("LideApp.class");
            Files.createDirectories(ladle.getParent());
            Files.writeString(ladle, "ladle");
            assertEquals(root.resolve("build").resolve("classes"), AppPackager.classesDirectory(root), "ladle classes");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testClassesDirectoryMissing() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-missing");
        try {
            assertTrue(AppPackager.classesDirectory(root) == null, "null when missing");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testCreateJarWritesMainClassAndEntries() throws Exception {
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
            assertTrue(Files.size(jar) > 0, "jar exists");
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
                assertTrue(manifest != null, "manifest");
                String text = new String(zip.getInputStream(manifest).readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.contains("Main-Class: lide.LideApp"), "main class");
                assertTrue(zip.getEntry("lide/LideApp.class") != null, "class entry");
                assertTrue(zip.getEntry("lide/icons/lide-16.png") != null, "icon entry");
            }
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testCommandForWindowsUsesIcoAndSkipsMacFlags() throws Exception {
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
            assertTrue(command.contains("app-image"), "type");
            assertTrue(command.contains("Lide"), "name");
            assertTrue(command.contains("lide.LideApp"), "main class");
            assertTrue(command.contains("lide.jar"), "main jar");
            assertTrue(command.contains(icon.toAbsolutePath().normalize().toString()), "icon");
            assertTrue(!command.contains("--mac-package-identifier"), "no mac identifier");
            assertEquals("--icon", command.get(command.indexOf(icon.toAbsolutePath().normalize().toString()) - 1), "icon flag before path");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testCommandForMacUsesIcnsAndIdentifier() throws Exception {
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
            assertTrue(command.contains(icon.toAbsolutePath().normalize().toString()), "icns");
            assertTrue(command.contains("--mac-package-identifier"), "identifier flag");
            assertTrue(command.contains("lide.Lide"), "identifier");
            assertTrue(command.contains("public.app-category.developer-tools"), "category");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testCommandForOmitsMissingIcon() throws Exception {
        Path dir = Files.createTempDirectory("lide-pack-no-icon");
        try {
            List<String> command = AppPackager.commandFor(
                    dir.resolve("jpackage"),
                    dir.resolve("input"),
                    dir.resolve("dist"),
                    dir.resolve("missing.ico"),
                    AppPackager.Platform.LINUX);
            assertTrue(!command.contains("--icon"), "no icon flag");
            assertTrue(!command.contains("--mac-package-identifier"), "no mac flags on linux");
        } finally {
            deleteRecursive(dir);
        }
    }

    @Test
    void testOutputPath() throws Exception {
        Path dest = Path.of("dist");
        assertEquals(dest.resolve("Lide"), AppPackager.Platform.WINDOWS.outputPath(dest), "windows");
        assertEquals(dest.resolve("Lide"), AppPackager.Platform.LINUX.outputPath(dest), "linux");
        assertEquals(dest.resolve("Lide.app"), AppPackager.Platform.MACOS.outputPath(dest), "mac");
        assertEquals("lide.ico", AppPackager.Platform.WINDOWS.iconFileName(), "windows icon");
        assertEquals("lide.icns", AppPackager.Platform.MACOS.iconFileName(), "mac icon");
        assertEquals("lide.png", AppPackager.Platform.LINUX.iconFileName(), "linux icon");
    }

    @Test
    void testPackageAppRequiresClasses() throws Exception {
        Path root = Files.createTempDirectory("lide-pack-require");
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> AppPackager.packageApp(root, AppPackager.Platform.WINDOWS));
            assertTrue(ex.getMessage().contains("compiled classes"), "mentions build");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testJpackageExecutableWhenJdkPresent() throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home", ""));
        String exe = ScriptCommand.isWindows() ? "jpackage.exe" : "jpackage";
        boolean present = Files.isRegularFile(javaHome.resolve("bin").resolve(exe));
        if (!present) {
            Path parent = javaHome.getParent();
            present = parent != null && Files.isRegularFile(parent.resolve("bin").resolve(exe));
        }
        Assumptions.assumeTrue(present, "jpackage is not on this JDK");
        Path found = AppPackager.jpackageExecutable();
        assertTrue(found != null && Files.isRegularFile(found), "found jpackage");
    }

    @Test
    void testCurrentPlatformMatchesOs() throws Exception {
        if (ScriptCommand.isWindows()) {
            assertEquals(AppPackager.Platform.WINDOWS, AppPackager.Platform.current(), "windows current");
            assertTrue(!ScriptCommand.isMac(), "not mac");
        } else if (ScriptCommand.isMac()) {
            assertEquals(AppPackager.Platform.MACOS, AppPackager.Platform.current(), "mac current");
        } else {
            assertEquals(AppPackager.Platform.LINUX, AppPackager.Platform.current(), "linux current");
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

}
