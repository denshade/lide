package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The fat JAR must ship a spec-compliant manifest so {@code java -jar} can start Lide.
 */
public final class ReleaseManifestTest {

    @Test
    void testCommittedManifestDeclaresMainClass() throws Exception {
        Path manifest = Path.of("manifest", "MANIFEST.MF");
        assertTrue(Files.isRegularFile(manifest), "committed manifest");
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Manifest-Version: 1.0"), "manifest version first");
        assertTrue(text.contains("Main-Class: lide.LideApp"), "main class");
        assertTrue(text.endsWith("\n"), "ends with newline");
    }

    @Test
    void testCreateJarManifestIsRunnable() throws Exception {
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
                assertTrue(entry != null, "jar manifest");
                String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(text.contains("Manifest-Version: 1.0"), "jar manifest version");
                assertTrue(text.contains("Main-Class: lide.LideApp"), "jar main class");
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

}
