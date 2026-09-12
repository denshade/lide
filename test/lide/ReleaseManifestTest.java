package lide;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The fat JAR must ship a spec-compliant manifest so {@code java -jar} can start Lide.
 */
public final class ReleaseManifestTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testCommittedManifestDeclaresMainClass();
        testCreateJarManifestIsRunnable();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testCommittedManifestDeclaresMainClass() throws Exception {
        Path manifest = Path.of("manifest", "MANIFEST.MF");
        assertTrue("committed manifest", Files.isRegularFile(manifest));
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        assertTrue("manifest version first", text.startsWith("Manifest-Version: 1.0"));
        assertTrue("main class", text.contains("Main-Class: lide.LideApp"));
        assertTrue("ends with newline", text.endsWith("\n"));
    }

    private static void testCreateJarManifestIsRunnable() throws Exception {
        Path dir = Files.createTempDirectory("lide-release-manifest");
        try {
            Path classes = dir.resolve("classes");
            Path pkg = classes.resolve("lide");
            Files.createDirectories(pkg);
            Files.writeString(pkg.resolve("LideApp.class"), "class-bytes");
            Path jar = dir.resolve("lide.jar");
            AppPackager.createJar(classes, jar);
            try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
                var entry = zip.getEntry("META-INF/MANIFEST.MF");
                assertTrue("jar manifest", entry != null);
                String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                assertTrue("jar manifest version", text.contains("Manifest-Version: 1.0"));
                assertTrue("jar main class", text.contains("Main-Class: lide.LideApp"));
            }
        } finally {
            deleteRecursive(dir);
        }
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (condition) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label);
    }

    private ReleaseManifestTest() {
    }
}
