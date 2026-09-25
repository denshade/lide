package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for copying a Ladle distribution into a project.
 */
public final class LadleInstallerTest {

    @Test
    void testIsDistribution() throws Exception {
        Path dist = fakeDistribution();
        Path other = Files.createTempDirectory("lide-not-ladle");
        try {
            assertTrue(LadleInstaller.isDistribution(dist), "dist");
            assertTrue(!LadleInstaller.isDistribution(other), "other");
            assertTrue(!LadleInstaller.isDistribution(null), "null");
        } finally {
            deleteRecursive(dist);
            deleteRecursive(other);
        }
    }

    @Test
    void testFindDistributionSkipsTarget() throws Exception {
        Path dist = fakeDistribution();
        try {
            Path found = LadleInstaller.findDistribution(dist, List.of(dist));
            assertTrue(found == null, "skips installing from itself");
        } finally {
            deleteRecursive(dist);
        }
    }

    @Test
    void testFindDistributionFindsSibling() throws Exception {
        Path parent = Files.createTempDirectory("lide-git");
        Path project = Files.createDirectories(parent.resolve("app"));
        Path ladle = fakeDistribution(parent.resolve("ladle"));
        try {
            Path found = LadleInstaller.findDistribution(project, List.of(project));
            assertTrue(found != null, "found sibling");
            assertEquals(ladle.toAbsolutePath().normalize(), found, "sibling path");
        } finally {
            deleteRecursive(parent);
        }
    }

    @Test
    void testInstallCopiesJarScriptsAndIni() throws Exception {
        Path dist = fakeDistribution();
        Path project = Files.createTempDirectory("lide-install-target");
        try {
            LadleInstaller.Result result = LadleInstaller.install(dist, project);
            assertTrue(Files.isRegularFile(project.resolve("lib").resolve("ladle.jar")), "jar copied");
            assertEquals("jar-bytes", Files.readString(project.resolve("lib").resolve("ladle.jar")), "jar bytes");
            assertTrue(Files.isRegularFile(project.resolve("bin").resolve("ladle.cmd")), "cmd copied");
            assertTrue(Files.isRegularFile(project.resolve("bin").resolve("ladle")), "unix copied");
            assertTrue(result.wroteIni(), "wrote ini");
            assertTrue(Files.isRegularFile(project.resolve("build.ini")), "ini exists");
            assertTrue(LadleCommand.isAvailable(project), "available");
            assertEquals(2, result.scripts().size(), "script count");
        } finally {
            deleteRecursive(dist);
            deleteRecursive(project);
        }
    }

    @Test
    void testInstallDoesNotOverwriteIni() throws Exception {
        Path dist = fakeDistribution();
        Path project = Files.createTempDirectory("lide-install-keep-ini");
        try {
            Files.writeString(project.resolve("build.ini"), "# keep me\n");
            LadleInstaller.Result result = LadleInstaller.install(dist, project);
            assertTrue(!result.wroteIni(), "did not write ini");
            assertEquals("# keep me\n", Files.readString(project.resolve("build.ini")), "ini preserved");
        } finally {
            deleteRecursive(dist);
            deleteRecursive(project);
        }
    }

    @Test
    void testDefaultBuildIniUsesProjectName() throws Exception {
        String ini = LadleInstaller.defaultBuildIni(Path.of("C:/work/myapp"));
        assertTrue(ini.contains("name = myapp"), "jar name");
        assertTrue(ini.contains("paths = src"), "sources");
        assertTrue(ini.contains("sources = test"), "test");
        assertTrue(!ini.contains("path = $JAVA_HOME"), "no JAVA_HOME placeholder");
        assertTrue(ini.contains("path = "), "has javac path");
        assertTrue(ini.contains("main-class = your.package.Main"), "mentions main-class");
        assertTrue(ini.contains("# main-class ="), "main-class is commented");
    }

    @Test
    void testInstallRejectsMissingJar() throws Exception {
        Path empty = Files.createTempDirectory("lide-empty-dist");
        Path project = Files.createTempDirectory("lide-empty-target");
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> LadleInstaller.install(empty, project));
        assertTrue(ex.getMessage().contains("ladle.jar"), "mentions jar");
        } finally {
            deleteRecursive(empty);
            deleteRecursive(project);
        }
    }

    private static Path fakeDistribution() throws Exception {
        return fakeDistribution(Files.createTempDirectory("lide-ladle-dist"));
    }

    private static Path fakeDistribution(Path root) throws Exception {
        Files.createDirectories(root.resolve("lib"));
        Files.createDirectories(root.resolve("bin"));
        Files.writeString(root.resolve("lib").resolve("ladle.jar"), "jar-bytes");
        Files.writeString(root.resolve("bin").resolve("ladle.cmd"), "@echo off\n");
        Files.writeString(root.resolve("bin").resolve("ladle"), "#!/bin/sh\n");
        return root.toAbsolutePath().normalize();
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
